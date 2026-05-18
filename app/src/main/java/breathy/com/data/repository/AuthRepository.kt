package breathy.com.data.repository

import breathy.com.data.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Authentication repository — handles Firebase Auth sign-in, sign-up,
 * Google sign-in, password reset, sign-out, and auth-state observation.
 *
 * All suspend functions enforce a 30-second network timeout and return
 * Kotlin [Result] so callers branch on success/failure without relying
 * on exceptions for control flow.
 *
 * @param auth          Firebase Auth instance
 * @param firestore     Firestore instance (used to seed user docs on sign-up)
 */
class AuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    companion object {
        private const val TAG = "AuthRepository"
        private const val NETWORK_TIMEOUT_MS = 30_000L
        private const val USERS_COLLECTION = "users"
        private const val PUBLIC_PROFILES_COLLECTION = "publicProfiles"
        private const val SUBSCRIPTIONS_COLLECTION = "subscriptions"
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Auth state observation
    // ═══════════════════════════════════════════════════════════════════════════

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)

    /** Self-unregistering auth-state listener that keeps [_currentUser] hot. */
    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _currentUser.value = firebaseAuth.currentUser
    }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    /** Hot state flow of the currently signed-in [FirebaseUser] (or null). */
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    /** Cold flow that emits on every auth-state change; auto-unregisters on collection cancellation. */
    val authStateFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Email / password authentication
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Sign in with email and password.
     * @return Result.success with the signed-in [FirebaseUser], or Result.failure with a descriptive error.
     */
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val user = withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                result.user ?: throw IllegalStateException("Sign in failed: user is null")
            } ?: throw IllegalStateException("Sign in timed out after 30 seconds")
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthInvalidUserException) {
            Timber.e(e, "Email sign-in failed: account not found for %s", email)
            Result.failure(Exception("No account found with this email. Please sign up first."))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Timber.e(e, "Email sign-in failed: wrong credentials for %s", email)
            Result.failure(Exception("Invalid email or password. Please try again."))
        } catch (e: Exception) {
            Timber.e(e, "Email sign-in failed for %s", email)
            Result.failure(e.userFriendlyMessage("Sign in failed"))
        }
    }

    /**
     * Create a new account with email, password, and optional nickname.
     * Also creates the initial Firestore user document and sends a verification email.
     */
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        nickname: String = ""
    ): Result<FirebaseUser> {
        return try {
            val user = withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                val firebaseUser = result.user
                    ?: throw IllegalStateException("Sign up failed: user is null")

                // Create initial user document in Firestore (best-effort)
                // NOTE: We intentionally do NOT write quitDate/quitType here.
                // The onboarding screen will add those fields when the user
                // completes setup. AuthScreen.checkUserProfileAndNavigate()
                // checks for quitDate+quitType+nickname to decide whether
                // the user has completed onboarding.
                try {
                    val newUser = User(
                        email = email,
                        nickname = nickname.ifBlank { firebaseUser.displayName ?: "" },
                        createdAt = com.google.firebase.Timestamp.now()
                    )
                    // Build a sparse map WITHOUT quitDate/quitType so the
                    // auth check knows this user hasn't completed onboarding
                    val sparseMap = mutableMapOf<String, Any?>(
                        "email" to newUser.email,
                        "nickname" to newUser.nickname,
                        "createdAt" to newUser.createdAt,
                        "xp" to 0,
                        "coins" to 0
                    )
                    // Use toFirestoreMap() for explicit field mapping
                    // to avoid enum-serialization issues with Firestore's POJO converter
                    firestore.collection(USERS_COLLECTION)
                        .document(firebaseUser.uid)
                        .set(sparseMap)
                        .await()

                    // Create initial public profile (without quitDate)
                    val publicProfile = mapOf(
                        "nickname" to (nickname.ifBlank { firebaseUser.displayName ?: "" }),
                        "photoURL" to (firebaseUser.photoUrl?.toString()),
                        "daysSmokeFree" to 0,
                        "xp" to 0
                    )
                    firestore.collection(PUBLIC_PROFILES_COLLECTION)
                        .document(firebaseUser.uid)
                        .set(publicProfile)
                        .await()
                } catch (e: Exception) {
                    // Firestore write failed (e.g. rules not deployed yet).
                    // Auth account was created — don't block sign-up.
                    // The onboarding screen will create the document later.
                    Timber.w(e, "Firestore write failed during sign-up for %s — continuing", email)
                }

                // Send email verification (best-effort, don't block sign-up on failure)
                try {
                    firebaseUser.sendEmailVerification().await()
                } catch (e: Exception) {
                    Timber.w(e, "Failed to send verification email for %s", email)
                }

                firebaseUser
            } ?: throw IllegalStateException("Sign up timed out after 30 seconds")
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthWeakPasswordException) {
            Timber.e(e, "Sign-up failed: weak password for %s", email)
            Result.failure(Exception("Password is too weak. Please use at least 6 characters."))
        } catch (e: FirebaseAuthUserCollisionException) {
            Timber.e(e, "Sign-up failed: email already in use %s", email)
            Result.failure(Exception("An account with this email already exists. Please sign in."))
        } catch (e: Exception) {
            Timber.e(e, "Email sign-up failed for %s", email)
            Result.failure(e.userFriendlyMessage("Sign up failed"))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Google sign-in
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Sign in (or link) with a Google ID token obtained from [GoogleSignInClient].
     * If this is a new user, a Firestore user document is created automatically.
     */
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val user = withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                val firebaseUser = result.user
                    ?: throw IllegalStateException("Google sign-in failed: user is null")

                // If this is a new user, create a sparse Firestore document (best-effort)
                // NOTE: We intentionally do NOT write quitDate/quitType here.
                // The onboarding screen will add those fields when the user
                // completes setup. See signUpWithEmail() for same pattern.
                if (result.additionalUserInfo?.isNewUser == true) {
                    try {
                        // Sparse map WITHOUT quitDate/quitType
                        val sparseMap = mutableMapOf<String, Any?>(
                            "email" to (firebaseUser.email ?: ""),
                            "nickname" to (firebaseUser.displayName ?: ""),
                            "photoURL" to (firebaseUser.photoUrl?.toString()),
                            "createdAt" to com.google.firebase.Timestamp.now(),
                            "xp" to 0,
                            "coins" to 0
                        )
                        firestore.collection(USERS_COLLECTION)
                            .document(firebaseUser.uid)
                            .set(sparseMap)
                            .await()

                        val publicProfile = mapOf(
                            "nickname" to (firebaseUser.displayName ?: ""),
                            "photoURL" to (firebaseUser.photoUrl?.toString()),
                            "daysSmokeFree" to 0,
                            "xp" to 0
                        )
                        firestore.collection(PUBLIC_PROFILES_COLLECTION)
                            .document(firebaseUser.uid)
                            .set(publicProfile)
                            .await()
                    } catch (e: Exception) {
                        // Firestore write failed (e.g. rules not deployed yet).
                        // Auth account was created — don't block sign-in.
                        Timber.w(e, "Firestore write failed during Google sign-in — continuing")
                    }
                }
                firebaseUser
            } ?: throw IllegalStateException("Google sign-in timed out after 30 seconds")
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Google sign-in failed")
            Result.failure(e.userFriendlyMessage("Google sign-in failed"))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Password reset
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Send a password-reset email to the given address.
     * Always returns success even if the email doesn't exist (Firebase security best practice),
     * but logs the real error for debugging.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                auth.sendPasswordResetEmail(email).await()
                Unit
            } ?: throw IllegalStateException("Password reset timed out after 30 seconds")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthInvalidUserException) {
            // Don't reveal whether the email exists; return success
            Timber.w(e, "Password reset requested for non-existent email: %s", email)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Password reset failed for %s", email)
            Result.failure(e.userFriendlyMessage("Failed to send reset email"))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Current user / sign-out / account deletion
    // ═══════════════════════════════════════════════════════════════════════════

    /** Return the currently signed-in user synchronously. */
    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    /**
     * Sign the user out.
     * Clears the FCM token from Firestore first to prevent stale notifications.
     */
    suspend fun signOut() {
        try {
            val uid = auth.currentUser?.uid
            if (uid != null) {
                withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                    firestore.collection(USERS_COLLECTION).document(uid)
                        .update("fcmToken", FieldValue.delete())
                        .await()
                        Unit
                }
            }
            auth.signOut()
            Timber.i("User signed out successfully")
        } catch (e: Exception) {
            Timber.e(e, "Sign-out cleanup failed, proceeding with local sign-out")
            auth.signOut()
        }
    }

    /**
     * Delete the user's account and all associated Firestore data.
     * This is a best-effort cleanup — Auth deletion is attempted last so
     * the user stays authenticated while Firestore data is being removed.
     */
    suspend fun deleteAccount(): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: return Result.failure(
                IllegalStateException("No authenticated user to delete")
            )

            withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                // Delete user data from Firestore
                val batch = firestore.batch()
                batch.delete(firestore.collection(USERS_COLLECTION).document(uid))
                batch.delete(firestore.collection(PUBLIC_PROFILES_COLLECTION).document(uid))
                batch.delete(firestore.collection(SUBSCRIPTIONS_COLLECTION).document(uid))
                batch.commit().await()
                Unit

                // Delete Firebase Auth account
                auth.currentUser?.delete()?.await()
                Unit
            } ?: throw IllegalStateException("Account deletion timed out after 30 seconds")

            Timber.i("Account deleted successfully for uid=%s", uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Account deletion failed")
            Result.failure(e.userFriendlyMessage("Failed to delete account"))
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Listener cleanup
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Remove the internal auth-state listener.
     * Call this when the repository is no longer needed (e.g., in Application.onTerminate
     * or a lifecycle-aware component's onDestroy).
     */
    fun cleanup() {
        auth.removeAuthStateListener(authStateListener)
        Timber.d("AuthRepository cleaned up")
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Map unknown exceptions to user-friendly messages. */
    private fun Exception.userFriendlyMessage(fallback: String): Exception {
        return Exception(
            when {
                message?.contains("network", ignoreCase = true) == true ->
                    "Network error. Please check your connection and try again."
                message?.contains("timeout", ignoreCase = true) == true ->
                    "Request timed out. Please try again."
                else -> fallback
            }
        )
    }
}
