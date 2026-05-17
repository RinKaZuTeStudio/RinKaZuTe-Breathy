package com.breathy.ui.auth

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import com.breathy.BreathyApplication
import com.breathy.data.repository.AuthRepository
import com.breathy.ui.theme.AccentPrimary
import com.breathy.ui.theme.AccentPurple
import com.breathy.ui.theme.AccentSecondary
import com.breathy.ui.theme.BgPrimary
import com.breathy.ui.theme.BgSurface
import com.breathy.ui.theme.BgSurfaceVariant
import com.breathy.ui.theme.SemanticError
import com.breathy.ui.theme.TextPrimary
import com.breathy.ui.theme.TextSecondary
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
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
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    companion object {
        private const val TAG = "AuthViewModel"
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        // Observe auth state for automatic navigation on returning users
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                Timber.d("$TAG: Auth state changed — user=%s", user?.uid ?: "null")
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

    fun sendPasswordReset() {
        val state = _uiState.value
        if (state.email.isBlank()) {
            _uiState.update {
                it.copy(
                    emailError = "Enter your email to reset your password.",
                    errorMessage = "Please enter your email address to reset your password."
                )
            }
            return
        }

        if (!EMAIL_REGEX.matches(state.email.trim())) {
            _uiState.update {
                it.copy(
                    emailError = "Enter a valid email address.",
                    errorMessage = "Please enter a valid email address."
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
                        it.copy(errorMessage = "Failed to send reset email. Please check your email address.")
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

    private fun checkUserProfileAndNavigate(userId: String) {
        viewModelScope.launch {
            try {
                // Use a timeout so the user isn't stuck loading forever
                val document = withTimeoutOrNull(8_000L) {
                    firestore.collection("users").document(userId).get().await()
                }

                _uiState.update { it.copy(isLoading = false) }

                if (document != null && document.exists()
                    && document.contains("quitDate")
                    && document.contains("quitType")
                    && document.contains("nickname")
                    && (document.getString("nickname")?.isNotBlank() == true)
                ) {
                    // User has completed onboarding (has quitDate + quitType + nickname)
                    _uiState.update {
                        it.copy(navigationEvent = AuthNavigationEvent.NavigateToHome)
                    }
                } else {
                    // New user or incomplete profile — go to onboarding
                    _uiState.update {
                        it.copy(navigationEvent = AuthNavigationEvent.NavigateToOnboarding)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to verify user profile for uid=%s — navigating to onboarding as fallback", userId)
                // Firestore read failed (likely rules not deployed), but auth succeeded.
                // Navigate to onboarding as a safe fallback instead of blocking the user.
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        navigationEvent = AuthNavigationEvent.NavigateToOnboarding
                    )
                }
            }
        }
    }

    private fun validateInputs(state: AuthUiState, isSignUp: Boolean): Boolean {
        var valid = true

        if (state.email.isBlank()) {
            _uiState.update { it.copy(emailError = "Email is required.") }
            valid = false
        } else if (!EMAIL_REGEX.matches(state.email.trim())) {
            _uiState.update { it.copy(emailError = "Enter a valid email address.") }
            valid = false
        }

        if (state.password.isBlank()) {
            _uiState.update { it.copy(passwordError = "Password is required.") }
            valid = false
        } else if (state.password.length < 6) {
            _uiState.update { it.copy(passwordError = "Password must be at least 6 characters.") }
            valid = false
        }

        if (isSignUp) {
            if (state.confirmPassword.isBlank()) {
                _uiState.update { it.copy(confirmPasswordError = "Please confirm your password.") }
                valid = false
            } else if (state.password != state.confirmPassword) {
                _uiState.update { it.copy(confirmPasswordError = "Passwords do not match.") }
                valid = false
            }
        }

        return valid
    }

    private fun mapAuthError(exception: Throwable): String {
        return when (exception) {
            is FirebaseAuthInvalidUserException ->
                "No account found with this email. Please sign up first."
            is FirebaseAuthInvalidCredentialsException ->
                "Invalid email or password. Please try again."
            is FirebaseAuthWeakPasswordException ->
                "Password is too weak. Use at least 6 characters with a mix of letters and numbers."
            is FirebaseAuthUserCollisionException ->
                "An account with this email already exists. Please sign in instead."
            is FirebaseAuthRecentLoginRequiredException ->
                "Please sign in again for security."
            is IllegalArgumentException ->
                exception.message ?: "Invalid input. Please check your details."
            else -> {
                Timber.e(exception, "$TAG: Unhandled auth error")
                exception.localizedMessage ?: "Authentication failed. Please try again."
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel Factory — manual DI replacing @HiltViewModel
// ═══════════════════════════════════════════════════════════════════════════════

class AuthViewModelFactory(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return AuthViewModel(authRepository, firestore) as T
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
    onGoogleSignInRequest: () -> Unit = {},
    googleIdToken: String? = null,
    onGoogleTokenConsumed: () -> Unit = {},
    viewModel: AuthViewModel = run {
        val context = LocalContext.current
        val appModule = (context.applicationContext as BreathyApplication).appModule
        viewModel(factory = AuthViewModelFactory(appModule.authRepository, appModule.firestore))
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
                message = "Password reset email sent! Check your inbox.",
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
                    colors = listOf(BgPrimary, BgSurface, BgPrimary),
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
                containerColor = BgSurfaceVariant,
                contentColor = SemanticError,
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
            BreathyLogo()

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Breathe Free",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (uiState.isSignUpMode) "Create your account" else "Welcome back",
                fontSize = 15.sp,
                color = TextSecondary
            )

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
                label = "Password",
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
                        label = "Confirm Password",
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
                            text = "Forgot Password?",
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
                text = if (uiState.isSignUpMode) "Sign Up" else "Sign In",
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
//  Breathy Logo — Animated breathing icon with neon glow
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun BreathyLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingGlow"
    )

    Box(
        contentAlignment = Alignment.Center
    ) {
        // Outer glow
        Box(
            modifier = Modifier
                .size(100.dp)
                .graphicsLayer {
                    scaleX = scale * 1.3f
                    scaleY = scale * 1.3f
                    alpha = glowAlpha
                }
                .blur(30.dp)
                .background(
                    AccentPrimary.copy(alpha = 0.4f),
                    CircleShape
                )
        )

        // Inner glow
        Box(
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = glowAlpha + 0.2f
                }
                .blur(15.dp)
                .background(
                    AccentPrimary.copy(alpha = 0.3f),
                    CircleShape
                )
        )

        // Breathing lung/air icon
        Icon(
            imageVector = Icons.Default.Air,
            contentDescription = "Breathy app logo — breathing air icon",
            tint = AccentPrimary,
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
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
        label = { Text("Email", color = TextSecondary) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = "Email input icon",
                tint = TextSecondary
            )
        },
        isError = error != null,
        supportingText = error?.let {
            {
                Text(
                    text = it,
                    color = SemanticError,
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
            unfocusedBorderColor = BgSurfaceVariant,
            errorBorderColor = SemanticError,
            focusedLabelColor = AccentPrimary,
            errorLabelColor = SemanticError,
            cursorColor = AccentPrimary,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            errorTextColor = TextPrimary
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
        label = { Text(label, color = TextSecondary) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "$label input icon",
                tint = TextSecondary
            )
        },
        trailingIcon = {
            IconButton(
                onClick = onToggleVisibility,
                content = {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                        tint = TextSecondary
                    )
                }
            )
        },
        isError = error != null,
        supportingText = error?.let {
            {
                Text(
                    text = it,
                    color = SemanticError,
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
            unfocusedBorderColor = BgSurfaceVariant,
            errorBorderColor = SemanticError,
            focusedLabelColor = AccentPrimary,
            errorLabelColor = SemanticError,
            cursorColor = AccentPrimary,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            errorTextColor = TextPrimary
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
                    color = BgPrimary,
                    strokeWidth = 2.5.dp
                )
            } else {
                Text(
                    text = text,
                    color = BgPrimary,
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
            color = BgSurfaceVariant,
            thickness = 1.dp
        )
        Text(
            text = "or",
            color = TextSecondary,
            fontSize = 13.sp
        )
        HorizontalDivider(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            color = BgSurfaceVariant,
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
                colors = listOf(BgSurfaceVariant, BgSurfaceVariant)
            )
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = BgSurface.copy(alpha = 0.6f),
            disabledContainerColor = BgSurface.copy(alpha = 0.3f)
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
            text = "Continue with Google",
            color = TextPrimary,
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
            text = if (isSignUpMode) "Already have an account? " else "Don't have an account? ",
            color = TextSecondary,
            fontSize = 14.sp
        )
        Text(
            text = if (isSignUpMode) "Sign In" else "Sign Up",
            color = AccentPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable { onToggle() }
        )
    }
}
