package com.breathy.data.repository

import android.net.Uri
import com.breathy.data.models.CopingMethod
import com.breathy.data.models.HealthMilestone
import com.breathy.data.models.PublicProfile
import com.breathy.data.models.QuitStats
import com.breathy.data.models.QuitType
import com.breathy.data.models.User
import com.breathy.utils.CloudinaryUploader
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * User repository — manages Firestore CRUD for [User] and [PublicProfile] documents,
 * XP/coin transactions with Firestore transactions, daily rewards, craving logs,
 * and real-time user observation.
 *
 * All network operations enforce a 30-second timeout.
 * All mutable operations use [Result] for error handling.
 */
class UserRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val cloudinaryUploader: CloudinaryUploader
) {

    companion object {
        private const val TAG = "UserRepository"
        private const val NETWORK_TIMEOUT_MS = 30_000L
        private const val USERS_COLLECTION = "users"
        private const val PUBLIC_PROFILES_COLLECTION = "publicProfiles"
        private const val CRAVING_LOGS_COLLECTION = "craving_logs"

        /** Daily reward: random coins between 10–50, plus 5 XP. */
        const val DAILY_REWARD_XP = 5
        const val DAILY_REWARD_COINS_MIN = 10
        const val DAILY_REWARD_COINS_MAX = 50
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Real-time current user tracking
    // ═══════════════════════════════════════════════════════════════════════════

    private val _currentUser = MutableStateFlow<User?>(null)
    private var userListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            if (uid != null) {
                listenToUserDocument(uid)
            } else {
                userListenerRegistration?.remove()
                userListenerRegistration = null
                _currentUser.value = null
            }
        }
    }

    private fun listenToUserDocument(uid: String) {
        // Remove previous listener before attaching a new one
        userListenerRegistration?.remove()
        userListenerRegistration = firestore.collection(USERS_COLLECTION).document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "Error listening to user document: %s", uid)
                    // Don't clear the user on error — keep the last known value or fallback
                    if (_currentUser.value == null) {
                        _currentUser.value = createFallbackUser()
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    try {
                        _currentUser.value = User.fromFirestoreMap(snapshot.data ?: emptyMap())
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to parse user document for %s", uid)
                        if (_currentUser.value == null) {
                            _currentUser.value = createFallbackUser()
                        }
                    }
                } else {
                    // Document doesn't exist — keep fallback instead of null
                    if (_currentUser.value == null) {
                        _currentUser.value = createFallbackUser()
                    }
                }
            }
    }

    /** Hot flow of the currently signed-in user's [User] object (or null). */
    val currentUser: Flow<User?> = _currentUser.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════════
    //  Single-read operations
    // ═══════════════════════════════════════════════════════════════════════════

    /** Fetch a user by ID. Uses server source to ensure fresh data, falls back to cache. */
    suspend fun getUser(userId: String): Result<User> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val document = try {
                firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .get(Source.SERVER)
                    .await()
            } catch (e: Exception) {
                Timber.w(e, "Server read failed for user %s — trying cache", userId)
                try {
                    firestore.collection(USERS_COLLECTION)
                        .document(userId)
                        .get(Source.CACHE)
                        .await()
                } catch (cacheEx: Exception) {
                    Timber.w(cacheEx, "Cache read also failed for user %s", userId)
                    null
                }
            }
            if (document == null || !document.exists()) throw NoSuchElementException("User not found: $userId")
            User.fromFirestoreMap(document.data ?: emptyMap())
        } ?: throw IllegalStateException("Get user timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get user: %s", userId)
    }

    /** Create a new user document at users/{userId}. */
    suspend fun createUser(user: User): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            firestore.collection(USERS_COLLECTION).document(uid).set(user).await(); Unit
            Unit
        } ?: throw IllegalStateException("Create user timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to create user")
    }

    /** Overwrite the user document at users/{userId} with the given [user]. */
    suspend fun updateUser(user: User): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated")
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            firestore.collection(USERS_COLLECTION).document(uid).set(user).await(); Unit
            Unit
        } ?: throw IllegalStateException("Update user timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to update user")
    }

    /** Upload a new avatar photo and update both user and publicProfile documents. */
    suspend fun updatePhoto(userId: String, photoUri: Uri): Result<String> = runCatching {
        val uploadResult = cloudinaryUploader.uploadProfileImageFromUri(
            imageUri = photoUri,
            userId = userId
        ) ?: throw IllegalStateException("Failed to upload photo to Cloudinary")

        val urlString = uploadResult.secureUrl

        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val batch = firestore.batch()
            batch.update(
                firestore.collection(USERS_COLLECTION).document(userId),
                "photoURL", urlString
            )
            batch.update(
                firestore.collection(PUBLIC_PROFILES_COLLECTION).document(userId),
                "photoURL", urlString
            )
            batch.commit().await()
            urlString
        } ?: throw IllegalStateException("Firestore update timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to update photo for user: %s", userId)
    }

    /** Fetch a public profile by user ID. */
    suspend fun getPublicProfile(userId: String): Result<PublicProfile> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val document = firestore.collection(PUBLIC_PROFILES_COLLECTION)
                .document(userId)
                .get(Source.SERVER)
                .await()
            if (!document.exists()) throw NoSuchElementException("Public profile not found: $userId")
            PublicProfile.fromFirestoreMap(userId, document.data ?: emptyMap())
        } ?: throw IllegalStateException("Get public profile timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get public profile: %s", userId)
    }

    /** Calculate quit stats from the user data. */
    suspend fun getQuitStats(userId: String): Result<QuitStats> = runCatching {
        val user = getUser(userId).getOrThrow()
        QuitStats.fromUser(user)
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to get quit stats for user: %s", userId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Field-level updates
    // ═══════════════════════════════════════════════════════════════════════════

    /** Update specific fields on the user document. */
    suspend fun updateUserFields(userId: String, updates: Map<String, Any>): Result<Unit> =
        runCatching {
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                firestore.collection(USERS_COLLECTION).document(userId)
                    .update(updates).await()
                Unit
            } ?: throw IllegalStateException("Update user fields timed out after 30 seconds")
        }.onFailure { e ->
            if (e !is CancellationException) Timber.e(e, "Failed to update user fields: %s", userId)
        }

    /** Update specific fields on the public profile document. */
    suspend fun updatePublicProfileFields(
        userId: String,
        updates: Map<String, Any>
    ): Result<Unit> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            firestore.collection(PUBLIC_PROFILES_COLLECTION).document(userId)
                .update(updates).await()
            Unit
        } ?: throw IllegalStateException("Update public profile timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to update public profile fields: %s", userId)
    }

    /** Update the FCM token for push notifications. */
    suspend fun updateFcmToken(userId: String, token: String): Result<Unit> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            firestore.collection(USERS_COLLECTION).document(userId)
                .update("fcmToken", token).await()
            Unit
        } ?: throw IllegalStateException("Update FCM token timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to update FCM token for user: %s", userId)
    }

    /** Replace the entire public profile document. */
    suspend fun updatePublicProfile(userId: String, profile: PublicProfile): Result<Unit> =
        runCatching {
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                firestore.collection(PUBLIC_PROFILES_COLLECTION).document(userId)
                    .set(profile).await()
                Unit
            } ?: throw IllegalStateException("Update public profile timed out after 30 seconds")
        }.onFailure { e ->
            if (e !is CancellationException) Timber.e(e, "Failed to update public profile: %s", userId)
        }

    /**
     * Update the daysSmokeFree denormalized field on the public profile.
     * Called whenever the user's days smoke-free might have changed (app open, midnight, etc.).
     */
    suspend fun updatePublicProfileDaysSmokeFree(userId: String): Result<Unit> = runCatching {
        val user = getUser(userId).getOrThrow()
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            firestore.collection(PUBLIC_PROFILES_COLLECTION).document(userId)
                .update(
                    mapOf(
                        "daysSmokeFree" to user.daysSmokeFree,
                        "xp" to user.xp,
                        "nickname" to user.nickname,
                        "photoURL" to (user.photoURL ?: FieldValue.delete()),
                        "location" to (user.location ?: FieldValue.delete())
                    )
                ).await()
            Unit
        } ?: throw IllegalStateException("Update daysSmokeFree timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to update daysSmokeFree for: %s", userId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  XP and currency — all use Firestore transactions for consistency
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Add XP to the user using a Firestore transaction.
     * @return the new total XP after the addition.
     */
    suspend fun addXp(userId: String, amount: Int): Result<Int> = runCatching {
        if (amount < 0) throw IllegalArgumentException("XP amount must be non-negative")
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val userRef = firestore.collection(USERS_COLLECTION).document(userId)
            val profileRef = firestore.collection(PUBLIC_PROFILES_COLLECTION).document(userId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val currentXp = (snapshot.getLong("xp") ?: 0L).toInt()
                val newXp = currentXp + amount
                transaction.update(userRef, "xp", newXp)
                transaction.update(profileRef, "xp", newXp)
                newXp
            }.await()
        } ?: throw IllegalStateException("Add XP timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to add XP for user: %s", userId)
    }

    /**
     * Add coins to the user using a Firestore transaction.
     * @return the new total coins after the addition.
     */
    suspend fun addCoins(userId: String, amount: Int): Result<Int> = runCatching {
        if (amount < 0) throw IllegalArgumentException("Coin amount must be non-negative")
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val userRef = firestore.collection(USERS_COLLECTION).document(userId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val currentCoins = (snapshot.getLong("coins") ?: 0L).toInt()
                val newCoins = currentCoins + amount
                transaction.update(userRef, "coins", newCoins)
                newCoins
            }.await()
        } ?: throw IllegalStateException("Add coins timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to add coins for user: %s", userId)
    }

    /**
     * Update both XP and coins in a single transaction.
     * @return Pair(newXp, newCoins)
     */
    suspend fun updateXpAndCoins(
        userId: String,
        xpDelta: Int,
        coinDelta: Int
    ): Result<Pair<Int, Int>> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val userRef = firestore.collection(USERS_COLLECTION).document(userId)
            val profileRef = firestore.collection(PUBLIC_PROFILES_COLLECTION).document(userId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val currentXp = (snapshot.getLong("xp") ?: 0L).toInt()
                val currentCoins = (snapshot.getLong("coins") ?: 0L).toInt()
                val newXp = currentXp + xpDelta
                val newCoins = currentCoins + coinDelta
                transaction.update(userRef, mapOf("xp" to newXp, "coins" to newCoins))
                transaction.update(profileRef, "xp", newXp)
                Pair(newXp, newCoins)
            }.await()
        } ?: throw IllegalStateException("Update XP/coins timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to update XP/coins for user: %s", userId)
    }

    /**
     * Claim the daily reward. Uses a Firestore transaction to ensure atomicity.
     * @return the number of coins awarded (random between 10–50).
     * @throws IllegalStateException if the reward was already claimed today.
     */
    suspend fun claimDailyReward(userId: String): Result<Int> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val userRef = firestore.collection(USERS_COLLECTION).document(userId)
            val profileRef = firestore.collection(PUBLIC_PROFILES_COLLECTION).document(userId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val lastClaimTs = snapshot.getTimestamp("lastDailyClaim")
                val lastClaim = lastClaimTs?.toDate()
                val now = Calendar.getInstance()

                // Check if already claimed today
                if (lastClaim != null) {
                    val lastClaimCal = Calendar.getInstance().apply { time = lastClaim }
                    val sameDay = lastClaimCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                            lastClaimCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
                    if (sameDay) {
                        throw IllegalStateException("Daily reward already claimed today")
                    }
                }

                val currentCoins = (snapshot.getLong("coins") ?: 0L).toInt()
                val currentXp = (snapshot.getLong("xp") ?: 0L).toInt()
                val reward = (DAILY_REWARD_COINS_MIN..DAILY_REWARD_COINS_MAX).random()
                val newCoins = currentCoins + reward
                val newXp = currentXp + DAILY_REWARD_XP

                transaction.update(userRef, mapOf(
                    "coins" to newCoins,
                    "xp" to newXp,
                    "lastDailyClaim" to Timestamp.now()
                ))
                transaction.update(profileRef, "xp", newXp)

                reward
            }.await()
        } ?: throw IllegalStateException("Claim daily reward timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to claim daily reward for user: %s", userId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Achievements & likes
    // ═══════════════════════════════════════════════════════════════════════════

    /** Add an achievement ID to the user's achievements array. */
    suspend fun addAchievement(userId: String, achievementId: String): Result<Unit> =
        runCatching {
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                firestore.collection(USERS_COLLECTION).document(userId)
                    .update("achievements", FieldValue.arrayUnion(achievementId))
                    .await()
                Unit
            } ?: throw IllegalStateException("Add achievement timed out after 30 seconds")
        }.onFailure { e ->
            if (e !is CancellationException) Timber.e(e, "Failed to add achievement %s for user: %s", achievementId, userId)
        }

    /** Record that the user liked a story. */
    suspend fun addGivenLike(userId: String, storyId: String): Result<Unit> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            firestore.collection(USERS_COLLECTION).document(userId)
                .update("givenLikes", FieldValue.arrayUnion(storyId))
                .await()
            Unit
        } ?: throw IllegalStateException("Add given like timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to add given like for user: %s", userId)
    }

    /** Remove a recorded like from the user. */
    suspend fun removeGivenLike(userId: String, storyId: String): Result<Unit> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            firestore.collection(USERS_COLLECTION).document(userId)
                .update("givenLikes", FieldValue.arrayRemove(storyId))
                .await()
            Unit
        } ?: throw IllegalStateException("Remove given like timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to remove given like for user: %s", userId)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Craving logs
    // ═══════════════════════════════════════════════════════════════════════════

    /** Log a craving event. */
    suspend fun logCraving(
        userId: String,
        copingMethod: CopingMethod,
        success: Boolean
    ): Result<Unit> = runCatching {
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            val cravingLog = hashMapOf(
                "userId" to userId,
                "timestamp" to Timestamp.now(),
                "copingMethod" to copingMethod.value,
                "success" to success
            )
            firestore.collection(CRAVING_LOGS_COLLECTION).add(cravingLog).await()
            Unit
        } ?: throw IllegalStateException("Log craving timed out after 30 seconds")
    }.onFailure { e ->
        if (e !is CancellationException) Timber.e(e, "Failed to log craving for user: %s", userId)
    }

    /** Get the timestamp of the most recent craving log for a user. */
    suspend fun getLastCravingTime(userId: String): Date? {
        return try {
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                val snapshot = firestore.collection(CRAVING_LOGS_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()
                if (snapshot.isEmpty) null
                else snapshot.documents.first().getTimestamp("timestamp")?.toDate()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get last craving time for user: %s", userId)
            null
        }
    }

    /** Count the number of successful craving resistances for a user. */
    suspend fun getSuccessfulCravingCount(userId: String): Int {
        return try {
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                val snapshot = firestore.collection(CRAVING_LOGS_COLLECTION)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("success", true)
                    .get()
                    .await()
                snapshot.size()
            } ?: 0
        } catch (e: Exception) {
            Timber.e(e, "Failed to get successful craving count for user: %s", userId)
            0
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Derived calculations
    // ═══════════════════════════════════════════════════════════════════════════

    /** Calculate days smoke-free from the quit date. */
    fun calculateDaysSmokeFree(quitDate: Date): Int {
        val diffMillis = System.currentTimeMillis() - quitDate.time
        return if (diffMillis < 0) 0
        else TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
    }

    /** Calculate total money saved. */
    fun calculateMoneySaved(
        quitDate: Date,
        cigarettesPerDay: Int,
        pricePerPack: Double,
        cigarettesPerPack: Int
    ): Double {
        if (cigarettesPerPack <= 0) return 0.0
        val days = calculateDaysSmokeFree(quitDate)
        val costPerCigarette = pricePerPack / cigarettesPerPack
        return cigarettesPerDay * costPerCigarette * days
    }

    /** Calculate total cigarettes avoided. */
    fun calculateCigarettesAvoided(quitDate: Date, cigarettesPerDay: Int): Int {
        val days = calculateDaysSmokeFree(quitDate)
        return cigarettesPerDay * days
    }

    /** Calculate life regained in minutes (11 minutes per cigarette avoided). */
    fun calculateLifeRegained(quitDate: Date, cigarettesPerDay: Int): Int {
        val cigarettesAvoided = calculateCigarettesAvoided(quitDate, cigarettesPerDay)
        return cigarettesAvoided * 11
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Health milestones
    // ═══════════════════════════════════════════════════════════════════════════

    /** Get all health milestone definitions. */
    fun getHealthMilestones(): List<HealthMilestone> = HealthMilestone.ALL

    /** Get health milestones with achievement status. */
    fun getCurrentMilestones(quitDate: Date): List<Pair<HealthMilestone, Boolean>> {
        val elapsedMillis = System.currentTimeMillis() - quitDate.time
        val elapsedMinutes = if (elapsedMillis < 0) 0L
        else TimeUnit.MILLISECONDS.toMinutes(elapsedMillis)
        return HealthMilestone.ALL.map { milestone ->
            milestone to milestone.isAchieved(elapsedMinutes)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Nickname uniqueness check
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if a nickname is available (not taken by another user).
     * Queries the publicProfiles collection for an exact nickname match.
     * @param nickname The nickname to check.
     * @param excludeUserId Optional user ID to exclude (for when editing own nickname).
     * @return true if the nickname is available, false if already taken.
     */
    suspend fun isNicknameAvailable(nickname: String, excludeUserId: String? = null): Boolean {
        return try {
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                val snapshot = firestore.collection(PUBLIC_PROFILES_COLLECTION)
                    .whereEqualTo("nickname", nickname)
                    .limit(2)
                    .get()
                    .await()
                // If excludeUserId is provided, filter out that user's own document
                val otherUsers = snapshot.documents.filter { doc ->
                    doc.id != excludeUserId
                }
                otherUsers.isEmpty()
            } ?: true // On timeout, allow the nickname (best-effort)
        } catch (e: Exception) {
            Timber.e(e, "Failed to check nickname availability for: %s", nickname)
            true // On error, allow the nickname (best-effort)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Search users
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Search public profiles by nickname prefix.
     * Firestore doesn't natively support "contains" queries, so we use
     * prefix matching: startAt(query) + endAt(query + '~').
     */
    suspend fun searchUsers(query: String, limit: Int = 20): Result<List<PublicProfile>> =
        runCatching {
            if (query.isBlank()) return Result.success(emptyList())
            withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                val snapshot = firestore.collection(PUBLIC_PROFILES_COLLECTION)
                    .orderBy("nickname")
                    .startAt(query)
                    .endAt(query + "\uf8ff")
                    .limit(limit.toLong())
                    .get()
                    .await()
                snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { PublicProfile.fromFirestoreMap(doc.id, it) }
                }
            } ?: throw IllegalStateException("Search users timed out after 30 seconds")
        }.onFailure { e ->
            if (e !is CancellationException) Timber.e(e, "Failed to search users: %s", query)
        }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Real-time observers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Observe a single user document in real-time. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeUser(userId: String): Flow<User> = callbackFlow {
        val registration = firestore.collection(USERS_COLLECTION).document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Timber.e(error, "observeUser error for %s — emitting fallback user", userId)
                    // Don't close the flow on error — emit a fallback User so the UI
                    // can still render instead of crashing. The listener stays active
                    // and will retry on the next Firestore sync.
                    trySend(createFallbackUser())
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    try {
                        trySend(User.fromFirestoreMap(snapshot.data ?: emptyMap()))
                    } catch (e: Exception) {
                        Timber.e(e, "observeUser: Failed to parse user document for %s — emitting fallback", userId)
                        trySend(createFallbackUser())
                    }
                } else {
                    // Document doesn't exist yet (e.g. Firestore rules blocked the read
                    // or onboarding hasn't completed). Emit a fallback user.
                    Timber.w("observeUser: No document for %s — emitting fallback user", userId)
                    trySend(createFallbackUser())
                }
            }
        awaitClose { registration.remove() }
    }

    /** Create a safe fallback User for when Firestore data is unavailable. */
    private fun createFallbackUser(): User = User(
        nickname = "Quitter",
        email = "",
        quitDate = null,
        quitType = QuitType.INSTANT,
        cigarettesPerDay = 0,
        pricePerPack = 0.0,
        cigarettesPerPack = 20,
        xp = 0,
        coins = 0
    )

    /** Observe public profiles ordered by XP (leaderboard). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observePublicProfilesOrderedByXp(limit: Int): Flow<List<Pair<String, PublicProfile>>> =
        callbackFlow {
            val registration = firestore.collection(PUBLIC_PROFILES_COLLECTION)
                .orderBy("xp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Timber.e(error, "observePublicProfilesOrderedByXp error")
                        // Don't close — emit empty list as fallback
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val profiles = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { Pair(doc.id, PublicProfile.fromFirestoreMap(doc.id, it)) }
                        }
                        trySend(profiles)
                    }
                }
            awaitClose { registration.remove() }
        }

    /** Observe public profiles ordered by days smoke-free (leaderboard). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observePublicProfilesOrderedByDaysSmokeFree(limit: Int): Flow<List<PublicProfile>> =
        callbackFlow {
            val registration = firestore.collection(PUBLIC_PROFILES_COLLECTION)
                .orderBy("daysSmokeFree", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Timber.e(error, "observePublicProfilesOrderedByDaysSmokeFree error")
                        // Don't close — emit empty list as fallback
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val profiles = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { PublicProfile.fromFirestoreMap(doc.id, it) }
                        }
                        trySend(profiles)
                    }
                }
            awaitClose { registration.remove() }
        }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Listener cleanup
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Remove the internal user document listener.
     * Call when the repository is no longer needed.
     */
    fun cleanup() {
        userListenerRegistration?.remove()
        userListenerRegistration = null
        Timber.d("UserRepository cleaned up")
    }
}
