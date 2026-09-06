package breathy.com.ui.auth

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import breathy.com.BreathyApplication
import breathy.com.data.repository.AuthRepository
import breathy.com.ui.theme.AccentPrimary
import breathy.com.ui.theme.AccentPurple
import breathy.com.ui.theme.AccentSecondary
import breathy.com.ui.theme.DarkBotanical
import breathy.com.ui.theme.DeepForest
import breathy.com.ui.theme.MediumSage
import breathy.com.ui.theme.NaturalYellow
import breathy.com.utils.s

import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

// ═══════════════════════════════════════════════════════════════════════════════
//  UI State & Navigation Events
// ═══════════════════════════════════════════════════════════════════════════════

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isSignUpMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val passwordResetSent: Boolean = false,
    val navigationEvent: AuthNavigationEvent? = null
)

sealed class AuthNavigationEvent {
    data object NavigateToHome : AuthNavigationEvent()
    data object NavigateToOnboarding : AuthNavigationEvent()
    /** One-time age completion for accounts created before age was required. */
    data object NavigateToAgeCompletion : AuthNavigationEvent()
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val onboardingLocalStore: breathy.com.utils.OnboardingLocalStore
) : ViewModel() {

    companion object {
        private const val TAG = "AuthViewModel"
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** Tracks whether we've already auto-navigated to prevent re-triggering. */
    private var hasAutoNavigated = false

    init {
        // Auto-navigate already-authenticated users on app reopen.
        // This is the fix for the "instant crash on reopen" — previously,
        // returning users saw the Auth screen instead of being routed to
        // Home or Onboarding, causing them to get stuck or re-onboard.
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                Timber.d("$TAG: Auth state changed — user=%s", user?.uid ?: "null")
                if (user != null && !hasAutoNavigated && _uiState.value.navigationEvent == null) {
                    hasAutoNavigated = true
                    checkUserProfileAndNavigate(user.uid)
                }
            }
        }
    }

    fun onEmailChanged(email: String) {
        _uiState.update {
            it.copy(
                email = email,
                errorMessage = null,
                emailError = null
            )
        }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update {
            it.copy(
                password = password,
                errorMessage = null,
                passwordError = null
            )
        }
    }

    fun onConfirmPasswordChanged(confirmPassword: String) {
        _uiState.update {
            it.copy(
                confirmPassword = confirmPassword,
                errorMessage = null,
                confirmPasswordError = null
            )
        }
    }

    fun toggleSignUpMode() {
        _uiState.update {
            it.copy(
                isSignUpMode = !it.isSignUpMode,
                errorMessage = null,
                emailError = null,
                passwordError = null,
                confirmPasswordError = null,
                passwordResetSent = false,
                confirmPassword = "",
                isConfirmPasswordVisible = false
            )
        }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
    }

    fun signIn() {
        val state = _uiState.value
        if (!validateInputs(state, isSignUp = false)) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = authRepository.signInWithEmail(state.email.trim(), state.password)
            _uiState.update { it.copy(isLoading = false) }

            result
                .onSuccess { user -> checkUserProfileAndNavigate(user.uid) }
                .onFailure { e ->
                    val message = mapAuthError(e)
                    _uiState.update { it.copy(errorMessage = message) }
                }
        }
    }

    fun signUp() {
        val state = _uiState.value
        if (!validateInputs(state, isSignUp = true)) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = authRepository.signUpWithEmail(
                state.email.trim(),
                state.password,
                "" // nickname is collected during onboarding
            )
            _uiState.update { it.copy(isLoading = false) }

            result
                .onSuccess { user -> checkUserProfileAndNavigate(user.uid) }
                .onFailure { e ->
                    val message = mapAuthError(e)
                    _uiState.update { it.copy(errorMessage = message) }
                }
        }
    }

    fun signInWithGoogle(idToken: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(idToken)
            _uiState.update { it.copy(isLoading = false) }

            result
                .onSuccess { user -> checkUserProfileAndNavigate(user.uid) }
                .onFailure { e ->
                    val message = mapAuthError(e)
                    _uiState.update { it.copy(errorMessage = message) }
                }
        }
    }

    /**
     * v1.0.17 — Surface a sign-in failure that happened OUTSIDE this
     * ViewModel (e.g., Google returned no ID token / ApiException in
     * MainActivity). Guarantees the user always sees what went wrong.
     */
    fun setExternalError(message: String) {
        _uiState.update { it.copy(isLoading = false, errorMessage = message) }
    }

    fun sendPasswordReset() {
        val state = _uiState.value
        if (state.email.isBlank()) {
            _uiState.update {
                it.copy(
                    emailError = s("Enter your email to reset your password.", "أدخل بريدك الإلكتروني لإعادة تعيين كلمة المرور."),
                    errorMessage = s("Please enter your email address to reset your password.", "يُرجى إدخال بريدك الإلكتروني لإعادة تعيين كلمة المرور.")
                )
            }
            return
        }

        if (!EMAIL_REGEX.matches(state.email.trim())) {
            _uiState.update {
                it.copy(
                    emailError = s("Enter a valid email address.", "أدخل عنوان بريد إلكتروني صالحاً."),
                    errorMessage = s("Please enter a valid email address.", "يُرجى إدخال عنوان بريد إلكتروني صالح.")
                )
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = authRepository.sendPasswordReset(state.email.trim())
            _uiState.update { it.copy(isLoading = false) }

            result
                .onSuccess {
                    _uiState.update { it.copy(passwordResetSent = true) }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(errorMessage = s("Failed to send reset email. Please check your email address.", "تعذّر إرسال بريد إعادة التعيين. يُرجى التحقق من بريدك الإلكتروني."))
                    }
                }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissPasswordResetSent() {
        _uiState.update { it.copy(passwordResetSent = false) }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigationEvent = null) }
    }

    /**
     * Decide where a signed-in account goes: Home (profile complete), Age
     * completion, or Onboarding.
     *
     * v1.0.8 FIX — the old logic asked the SERVER for the profile and fell
     * back to Onboarding on ANY read failure/timeout, so a returning user on
     * a slow network was thrown back into account setup on every cold start
     * ("close the app and rejoin → asks me to create the account again").
     * The decision now runs on three layers, offline-proof first:
     *
     *  1. LOCAL FLAG (OnboardingLocalStore, disk-persistent): written the
     *     moment onboarding completes → straight to Home, zero network.
     *  2. FIRESTORE (server → cache): for accounts created before the flag
     *     existed, and to detect the sparse pre-onboarding document.
     *  3. PENDING QUEUE: if Firestore says sparse but a local pending
     *     profile exists, the write never landed — retry it now and go Home.
     *     Only a truly sparse account with NO local state goes to Onboarding.
     */
    private fun checkUserProfileAndNavigate(userId: String) {
        // ── Layer 1: local completion flag — instant, offline-proof ────────
        if (onboardingLocalStore.isCompleted(userId)) {
            Timber.i("$TAG: uid=%s has LOCAL onboarding flag — navigating to Home (no network needed)", userId)
            _uiState.update { it.copy(isLoading = false) }
            retryPendingProfileUpload(userId)
            _uiState.update {
                it.copy(navigationEvent = AuthNavigationEvent.NavigateToHome)
            }
            return
        }

        viewModelScope.launch {
            try {
                // Try SERVER source first, fall back to CACHE if rules deny access.
                // Use a timeout so the user isn't stuck loading forever.
                var document = withTimeoutOrNull(8_000L) {
                    try {
                        firestore.collection("users").document(userId)
                            .get(com.google.firebase.firestore.Source.SERVER)
                            .await()
                    } catch (e: Exception) {
                        Timber.w(e, "$TAG: Server read failed for uid=%s — trying cache", userId)
                        try {
                            firestore.collection("users").document(userId)
                                .get(com.google.firebase.firestore.Source.CACHE)
                                .await()
                        } catch (cacheEx: Exception) {
                            Timber.w(cacheEx, "$TAG: Cache read also failed for uid=%s", userId)
                            null
                        }
                    }
                }

                _uiState.update { it.copy(isLoading = false) }

                // v1.0.9 FIX — a TIMEOUT (or total read failure) must NEVER be
                // treated as "new account". withTimeoutOrNull(8s) returns NULL
                // without throwing, which used to fall straight into the
                // "needs onboarding" branch below and re-onboarded an account
                // that is fully registered on the server. From this version,
                // a missing document behaves exactly like the catch path:
                // any local onboarding state (flag OR pending write) wins.
                if (document == null || !document.exists()) {
                    val hasLocal = onboardingLocalStore.isCompleted(userId) ||
                            onboardingLocalStore.readPendingProfile(userId) != null
                    if (hasLocal) {
                        Timber.w("$TAG: uid=%s server read failed/timed out but LOCAL onboarding state exists — going Home", userId)
                        retryPendingProfileUpload(userId)
                        _uiState.update {
                            it.copy(navigationEvent = AuthNavigationEvent.NavigateToHome)
                        }
                        return@launch
                    }
                }

                val profileComplete = document != null && document.exists()
                    && document.contains("quitDate")
                    && document.contains("quitType")
                    && document.contains("nickname")
                    && (document.getString("nickname")?.isNotBlank() == true)

                when {
                    // ── Firestore confirms onboarding done ─────────────────
                    profileComplete -> {
                        // Backfill the local flag so future launches skip the network
                        onboardingLocalStore.markCompleted(userId)
                        retryPendingProfileUpload(userId)

                        // Profile complete except AGE? Existing accounts created
                        // before the age requirement get a ONE-TIME completion
                        // step (never an infinite loop — once saved, the check passes).
                        val hasAge = (document.getLong("age") ?: 0L) > 0
                        if (!hasAge) {
                            Timber.i("$TAG: User uid=%s missing age — navigating to age completion", userId)
                            _uiState.update {
                                it.copy(navigationEvent = AuthNavigationEvent.NavigateToAgeCompletion)
                            }
                        } else {
                            Timber.i("$TAG: User uid=%s has completed onboarding — navigating to Home", userId)
                            _uiState.update {
                                it.copy(navigationEvent = AuthNavigationEvent.NavigateToHome)
                            }
                        }
                    }

                    // ── Sparse doc, but a pending local write exists: the
                    //    original save never landed — retry it and go Home ─
                    onboardingLocalStore.readPendingProfile(userId) != null -> {
                        Timber.i("$TAG: uid=%s sparse on server but LOCAL pending profile exists — retrying upload, navigating to Home", userId)
                        retryPendingProfileUpload(userId)
                        onboardingLocalStore.markCompleted(userId)
                        _uiState.update {
                            it.copy(navigationEvent = AuthNavigationEvent.NavigateToHome)
                        }
                    }

                    // ── Truly new / incomplete account ─────────────────────
                    else -> {
                        Timber.i("$TAG: User uid=%s needs onboarding — navigating to Onboarding", userId)
                        _uiState.update {
                            it.copy(navigationEvent = AuthNavigationEvent.NavigateToOnboarding)
                        }
                    }
                }
            } catch (e: Exception) {
                // Firestore read failed entirely (offline, no cache). A user
                // with ANY local onboarding state must NEVER be re-onboarded.
                val hasLocal = onboardingLocalStore.isCompleted(userId) ||
                        onboardingLocalStore.readPendingProfile(userId) != null
                Timber.e(e, "$TAG: Profile read failed for uid=%s — local state=%s", userId, hasLocal)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        navigationEvent = if (hasLocal) AuthNavigationEvent.NavigateToHome
                        else AuthNavigationEvent.NavigateToOnboarding
                    )
                }
            }
        }
    }

    /**
     * If onboarding's Firestore write failed earlier (rules propagation,
     * offline), the full payload was queued locally. Retry it in the
     * background until it lands, then clear the queue. Fire-and-forget —
     * never blocks navigation.
     */
    private fun retryPendingProfileUpload(userId: String) {
        val pending = onboardingLocalStore.readPendingProfile(userId) ?: return
        viewModelScope.launch {
            try {
                val (userMap, publicMap) = onboardingLocalStore.buildFirestoreMaps(pending)
                repeat(3) { attempt ->
                    try {
                        withTimeoutOrNull(10_000L) {
                            // v1.0.19 NICKNAME FIX — never move the users/{uid}
                            // identity anchors (createdAt/email) on UPDATE; the v8
                            // rules reject such writes, which used to make this
                            // retry fail forever and leave the stale sign-up
                            // nickname (e-mail prefix) in place.
                            val userRef = firestore.collection("users").document(userId)
                            val existing = try {
                                userRef.get().await()
                            } catch (e: Exception) { null }
                            val effectiveUserMap = if (existing?.exists() == true) {
                                userMap.toMutableMap().apply {
                                    remove("createdAt")
                                    existing.getString("email")?.let { this["email"] = it }
                                }
                            } else userMap
                            val batch = firestore.batch()
                            batch.set(userRef, effectiveUserMap)
                            batch.set(
                                firestore.collection("publicProfiles").document(userId),
                                publicMap,
                                com.google.firebase.firestore.SetOptions.merge()
                            )
                            batch.commit().await()
                        } ?: throw java.util.concurrent.TimeoutException()
                        Timber.i("$TAG: pending onboarding profile uploaded for uid=%s (attempt %d)", userId, attempt + 1)
                        onboardingLocalStore.clearPendingProfile(userId)
                        return@launch
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "$TAG: pending upload attempt %d failed for uid=%s", attempt + 1, userId)
                    }
                    kotlinx.coroutines.delay(5_000L * (attempt + 1))
                }
                Timber.w("$TAG: pending profile upload still failing for uid=%s — will retry next launch", userId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "$TAG: pending profile retry crashed for uid=%s", userId)
            }
        }
    }

    private fun validateInputs(state: AuthUiState, isSignUp: Boolean): Boolean {
        var valid = true

        if (state.email.isBlank()) {
            _uiState.update { it.copy(emailError = s("Email is required.", "البريد الإلكتروني مطلوب.")) }
            valid = false
        } else if (!EMAIL_REGEX.matches(state.email.trim())) {
            _uiState.update { it.copy(emailError = s("Enter a valid email address.", "أدخل عنوان بريد إلكتروني صالحاً.")) }
            valid = false
        }

        if (state.password.isBlank()) {
            _uiState.update { it.copy(passwordError = s("Password is required.", "كلمة المرور مطلوبة.")) }
            valid = false
        } else if (state.password.length < 6) {
            _uiState.update { it.copy(passwordError = s("Password must be at least 6 characters.", "يجب أن تتكون كلمة المرور من 6 أحرف على الأقل.")) }
            valid = false
        }

        if (isSignUp) {
            if (state.confirmPassword.isBlank()) {
                _uiState.update { it.copy(confirmPasswordError = s("Please confirm your password.", "يُرجى تأكيد كلمة المرور.")) }
                valid = false
            } else if (state.password != state.confirmPassword) {
                _uiState.update { it.copy(confirmPasswordError = s("Passwords do not match.", "كلمتا المرور غير متطابقتين.")) }
                valid = false
            }
        }

        return valid
    }

    private fun mapAuthError(exception: Throwable): String {
        return when (exception) {
            is FirebaseAuthInvalidUserException ->
                s("No account found with this email. Please sign up first.", "لا يوجد حساب بهذا البريد الإلكتروني. يُرجى إنشاء حساب أولاً.")
            is FirebaseAuthInvalidCredentialsException ->
                s("Invalid email or password. Please try again.", "البريد الإلكتروني أو كلمة المرور غير صحيحة. حاول مرة أخرى.")
            is FirebaseAuthWeakPasswordException ->
                s("Password is too weak. Use at least 6 characters with a mix of letters and numbers.", "كلمة المرور ضعيفة جداً. استخدم 6 أحرف على الأقل مع مزيج من الحروف والأرقام.")
            is FirebaseAuthUserCollisionException ->
                s("An account with this email already exists. Please sign in instead.", "يوجد حساب بهذا البريد الإلكتروني بالفعل. يُرجى تسجيل الدخول بدلاً من ذلك.")
            is IllegalArgumentException ->
                exception.message ?: s("Invalid input. Please check your details.", "بيانات غير صحيحة. يُرجى التحقق من المدخلات.")
            else -> {
                Timber.e(exception, "$TAG: Unhandled auth error")
                exception.localizedMessage ?: s("Authentication failed. Please try again.", "فشلت المصادقة. حاول مرة أخرى.")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel Factory — manual DI replacing @HiltViewModel
// ═══════════════════════════════════════════════════════════════════════════════

class AuthViewModelFactory(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val onboardingLocalStore: breathy.com.utils.OnboardingLocalStore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return AuthViewModel(authRepository, firestore, onboardingLocalStore) as T
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Auth Screen — Main Composable
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Authentication screen supporting email/password sign-in & sign-up with
 * Google Sign-In option.
 *
 * @param onNavigateToHome       Callback invoked when a returning user signs in.
 * @param onNavigateToOnboarding Callback invoked when a new user signs up.
 * @param onGoogleSignInRequest  Callback invoked to trigger Google Sign-In
 *                               from the hosting Activity/Fragment. The Activity
 *                               should launch the Google Sign-In intent and pass
 *                               the resulting idToken to [AuthViewModel.signInWithGoogle].
 */
@Composable
fun AuthScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToAgeCompletion: () -> Unit = {},
    onGoogleSignInRequest: () -> Unit = {},
    googleIdToken: String? = null,
    onGoogleTokenConsumed: () -> Unit = {},
    googleSignInError: String? = null,
    onGoogleErrorConsumed: () -> Unit = {},
    viewModel: AuthViewModel = run {
        val context = LocalContext.current
        val appModule = (context.applicationContext as BreathyApplication).appModule
        viewModel(factory = AuthViewModelFactory(appModule.authRepository, appModule.firestore, appModule.onboardingLocalStore))
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // ── Handle Google Sign-In token from Activity ────────────────────────────
    LaunchedEffect(googleIdToken) {
        if (googleIdToken != null) {
            viewModel.signInWithGoogle(googleIdToken)
            onGoogleTokenConsumed()
        }
    }

    // v1.0.17 — Surface Google Sign-In failures from the Activity. The picker
    // may complete yet the token exchange fail (signing-certificate mismatch,
    // network...). Those failures used to be log-only — the screen appeared
    // frozen. Now they become a visible, friendly error.
    LaunchedEffect(googleSignInError) {
        if (googleSignInError != null) {
            viewModel.setExternalError(googleSignInError)
            onGoogleErrorConsumed()
        }
    }

    // ── Navigation events ────────────────────────────────────────────────────
    LaunchedEffect(uiState.navigationEvent) {
        when (uiState.navigationEvent) {
            is AuthNavigationEvent.NavigateToHome -> {
                onNavigateToHome()
                viewModel.onNavigationHandled()
            }
            is AuthNavigationEvent.NavigateToOnboarding -> {
                onNavigateToOnboarding()
                viewModel.onNavigationHandled()
            }
            is AuthNavigationEvent.NavigateToAgeCompletion -> {
                onNavigateToAgeCompletion()
                viewModel.onNavigationHandled()
            }
            null -> { /* No navigation */ }
        }
    }

    // ── Snackbar for error messages ───────────────────────────────────────────
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.dismissError()
        }
    }

    // ── Snackbar for password reset confirmation ─────────────────────────────
    LaunchedEffect(uiState.passwordResetSent) {
        if (uiState.passwordResetSent) {
            snackbarHostState.showSnackbar(
                message = s("Password reset email sent! Check your inbox.", "تم إرسال بريد إعادة تعيين كلمة المرور! تحقّق من صندوق الوارد."),
                duration = SnackbarDuration.Long
            )
            viewModel.dismissPasswordResetSent()
        }
    }

    // ── Cleanup on dispose ───────────────────────────────────────────────────
    DisposableEffect(Unit) {
        onDispose {
            // Keyboard is auto-dismissed; no listeners to clean up
            // since AuthRepository manages its own auth-state listener.
            Timber.d("AuthScreen disposed")
        }
    }

    // ── Layout ───────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.background),
                    start = Offset(0f, 0f),
                    end = Offset(0f, 2000f)
                )
            )
    ) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.error,
                shape = RoundedCornerShape(12.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(top = 60.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AuthWelcomeHeader(isSignUpMode = uiState.isSignUpMode)

            Spacer(modifier = Modifier.height(36.dp))

            // ── Email field ───────────────────────────────────────────────
            EmailField(
                email = uiState.email,
                onEmailChanged = viewModel::onEmailChanged,
                error = uiState.emailError,
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Password field ────────────────────────────────────────────
            PasswordField(
                password = uiState.password,
                onPasswordChanged = viewModel::onPasswordChanged,
                isPasswordVisible = uiState.isPasswordVisible,
                onToggleVisibility = viewModel::togglePasswordVisibility,
                label = s("Password", "كلمة المرور"),
                error = uiState.passwordError,
                imeAction = if (uiState.isSignUpMode) ImeAction.Next else ImeAction.Done,
                onImeAction = {
                    if (uiState.isSignUpMode) {
                        focusManager.moveFocus(FocusDirection.Down)
                    } else {
                        keyboardController?.hide()
                        viewModel.signIn()
                    }
                }
            )

            // ── Confirm password (sign-up only) ───────────────────────────
            AnimatedVisibility(
                visible = uiState.isSignUpMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    PasswordField(
                        password = uiState.confirmPassword,
                        onPasswordChanged = viewModel::onConfirmPasswordChanged,
                        isPasswordVisible = uiState.isConfirmPasswordVisible,
                        onToggleVisibility = viewModel::toggleConfirmPasswordVisibility,
                        label = s("Confirm Password", "تأكيد كلمة المرور"),
                        error = uiState.confirmPasswordError,
                        imeAction = ImeAction.Done,
                        onImeAction = {
                            keyboardController?.hide()
                            viewModel.signUp()
                        }
                    )
                }
            }

            // ── Forgot password (sign-in only) ────────────────────────────
            if (!uiState.isSignUpMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = viewModel::sendPasswordReset) {
                        Text(
                            text = s("Forgot Password?", "نسيت كلمة المرور؟"),
                            color = AccentPurple,
                            fontSize = 13.sp,
                            textDecoration = TextDecoration.Underline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Primary action button ─────────────────────────────────────
            GradientButton(
                text = if (uiState.isSignUpMode) s("Sign Up", "إنشاء حساب") else s("Sign In", "تسجيل الدخول"),
                isLoading = uiState.isLoading,
                onClick = if (uiState.isSignUpMode) viewModel::signUp else viewModel::signIn,
                contentDescription = if (uiState.isSignUpMode) "Sign up button" else "Sign in button"
            )

            Spacer(modifier = Modifier.height(24.dp))

            OrDivider()

            Spacer(modifier = Modifier.height(24.dp))

            // ── Google Sign-In button ─────────────────────────────────────
            GoogleSignInButton(
                onClick = onGoogleSignInRequest,
                isLoading = uiState.isLoading
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Toggle sign-in / sign-up ──────────────────────────────────
            SignUpToggle(
                isSignUpMode = uiState.isSignUpMode,
                onToggle = viewModel::toggleSignUpMode
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Welcome Header — typography-first Breathy Nature login header
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Typography-first welcome header for the auth screen (v1.0.11 rev 6).
 * Replaces the old generic animated lung/blob illustration with a polished,
 * calm Breathy Nature composition: a refined static breath-ring mark with a
 * subtle alpha-only "breathing" glow, brand wordmark, and a clear headline
 * directly above the email field. No floating shapes, no resizing elements.
 */
@Composable
private fun AuthWelcomeHeader(isSignUpMode: Boolean) {
    // Alpha-only breathing accent (no scale/shape change — the mark itself
    // never resizes; only the outer ring's opacity gently breathes).
    val ringAlpha by rememberInfiniteTransition(label = "breath")
        .animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breathRingAlpha"
        )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // ── Breath mark: two thin concentric rings + a small gold dot ────
        Box(
            modifier = Modifier.size(72.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer ring — soft sage, gently breathing (alpha only)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer { alpha = ringAlpha }
                    .border(width = 1.5.dp, color = MediumSage, shape = CircleShape)
            )
            // Inner ring — deep forest green (steady anchor)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .border(width = 2.dp, color = DeepForest.copy(alpha = 0.85f), shape = CircleShape)
            )
            // Gold "morning" dot resting on the outer ring (recovery light)
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .align(Alignment.TopCenter)
                    .background(NaturalYellow, CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Brand wordmark ────────────────────────────────────────────────
        Text(
            text = s("B R E A T H Y", "B R E A T H Y"),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 3.sp,
            color = MediumSage
        )

        Spacer(modifier = Modifier.height(10.dp))

        // ── Headline (mode-aware) ─────────────────────────────────────────
        Text(
            text = if (isSignUpMode) {
                s("CREATE YOUR ACCOUNT", "أنشئ حسابك")
            } else {
                s("WELCOME BACK", "مرحباً بعودتك")
            },
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = DarkBotanical
        )

        Spacer(modifier = Modifier.height(8.dp))

        // ── Supporting line — calm, intentional ──────────────────────────
        Text(
            text = s(
                "Breathe in. Breathe out. Your smoke-free journey continues.",
                "تنفّس بهدوء. رحلتك دون تدخين مستمرة."
            ),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = DeepForest.copy(alpha = 0.65f)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Email Field
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EmailField(
    email: String,
    onEmailChanged: (String) -> Unit,
    error: String?,
    imeAction: ImeAction,
    onImeAction: () -> Unit
) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(s("Email", "البريد الإلكتروني"), color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = "Email input icon",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        isError = error != null,
        supportingText = error?.let {
            {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentPrimary,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedLabelColor = AccentPrimary,
            errorLabelColor = MaterialTheme.colorScheme.error,
            cursorColor = AccentPrimary,
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            errorTextColor = MaterialTheme.colorScheme.onBackground
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Password Field
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PasswordField(
    password: String,
    onPasswordChanged: (String) -> Unit,
    isPasswordVisible: Boolean,
    onToggleVisibility: () -> Unit,
    label: String,
    error: String?,
    imeAction: ImeAction,
    onImeAction: () -> Unit
) {
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "$label input icon",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            IconButton(
                onClick = onToggleVisibility,
                content = {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        },
        isError = error != null,
        supportingText = error?.let {
            {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }
        },
        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentPrimary,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedLabelColor = AccentPrimary,
            errorLabelColor = MaterialTheme.colorScheme.error,
            cursorColor = AccentPrimary,
            focusedTextColor = MaterialTheme.colorScheme.onBackground,
            unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
            errorTextColor = MaterialTheme.colorScheme.onBackground
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Gradient CTA Button
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun GradientButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    contentDescription: String = text
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp)),
        colors = ButtonDefaults.buttonColors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent
        ),
        enabled = !isLoading,
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(AccentPrimary, AccentSecondary)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.background,
                    strokeWidth = 2.5.dp
                )
            } else {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.background,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  "or" Divider
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        HorizontalDivider(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            thickness = 1.dp
        )
        Text(
            text = s("or", "أو"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        HorizontalDivider(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            thickness = 1.dp
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Google Sign-In Button
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun GoogleSignInButton(
    onClick: () -> Unit,
    isLoading: Boolean
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        enabled = !isLoading,
        shape = RoundedCornerShape(14.dp),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = Brush.horizontalGradient(
                colors = listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
            )
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
        )
    ) {
        // Google "G" letter as a simple text representation.
        // Replace with R.drawable.ic_google once the vector asset is added.
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = AccentSecondary,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                text = "G",
                color = AccentSecondary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = s("Continue with Google", "المتابعة عبر Google"),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Sign In / Sign Up Toggle
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SignUpToggle(
    isSignUpMode: Boolean,
    onToggle: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isSignUpMode) s("Already have an account? ", "لديك حساب بالفعل؟ ") else s("Don't have an account? ", "ليس لديك حساب؟ "),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Text(
            text = if (isSignUpMode) s("Sign In", "تسجيل الدخول") else s("Sign Up", "إنشاء حساب"),
            color = AccentPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable { onToggle() }
        )
    }
}
