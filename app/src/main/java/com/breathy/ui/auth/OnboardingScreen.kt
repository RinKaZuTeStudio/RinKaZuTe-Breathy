package com.breathy.ui.auth

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.breathy.BreathyApplication
import com.breathy.data.models.PublicProfile
import com.breathy.data.models.QuitType
import com.breathy.data.models.User
import com.breathy.ui.theme.AccentPrimary
import com.breathy.ui.theme.AccentPurple
import com.breathy.ui.theme.AccentSecondary
import com.breathy.ui.theme.BgPrimary
import com.breathy.ui.theme.BgSurface
import com.breathy.ui.theme.BgSurfaceVariant
import com.breathy.ui.theme.SemanticError
import com.breathy.ui.theme.TextPrimary
import com.breathy.ui.theme.TextSecondary
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════════════════
//  UI State
// ═══════════════════════════════════════════════════════════════════════════════

data class OnboardingUiState(
    val currentStep: Int = 0,
    val quitDate: Long = System.currentTimeMillis(),
    val quitType: QuitType = QuitType.INSTANT,
    val cigarettesPerDay: Int = 10,
    val pricePerPack: Double = 8.0,
    val cigarettesPerPack: Int = 20,
    val nickname: String = "",
    val photoUri: Uri? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isComplete: Boolean = false,
    val nicknameError: String? = null,
    val dailySavings: Double = 0.0,
    val monthlySavings: Double = 0.0,
    val yearlySavings: Double = 0.0
) {
    val canProceed: Boolean
        get() = when (currentStep) {
            0 -> true // quitDate always has a default
            1 -> true // quitType always has a default
            2 -> cigarettesPerDay > 0 && pricePerPack > 0.0 && cigarettesPerPack > 0
            3 -> nickname.isNotBlank() && nickname.length >= 2
            else -> false
        }

    val totalSteps: Int get() = 4
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

class OnboardingViewModel(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val cloudinaryUploader: com.breathy.utils.CloudinaryUploader
) : ViewModel() {

    companion object {
        private const val TAG = "OnboardingViewModel"
        private const val USERS_COLLECTION = "users"
        private const val PUBLIC_PROFILES_COLLECTION = "publicProfiles"
        private const val FIRESTORE_WRITE_TIMEOUT_MS = 10_000L
        private const val FALLBACK_NAVIGATION_DELAY_MS = 12_000L
    }

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /** Track the current save job so we can cancel it if needed. */
    private var saveJob: Job? = null

    init {
        recalculateSavings()
    }

    fun onQuitDateSelected(date: Long) {
        _uiState.update { it.copy(quitDate = date, errorMessage = null) }
    }

    fun onQuitTypeChanged(quitType: QuitType) {
        _uiState.update { it.copy(quitType = quitType, errorMessage = null) }
    }

    fun onCigarettesPerDayChanged(count: Int) {
        _uiState.update {
            it.copy(
                cigarettesPerDay = count.coerceAtLeast(1),
                errorMessage = null
            )
        }
        recalculateSavings()
    }

    fun onPricePerPackChanged(price: Double) {
        _uiState.update {
            it.copy(
                pricePerPack = price.coerceAtLeast(0.0),
                errorMessage = null
            )
        }
        recalculateSavings()
    }

    fun onCigarettesPerPackChanged(count: Int) {
        _uiState.update {
            it.copy(
                cigarettesPerPack = count.coerceAtLeast(1),
                errorMessage = null
            )
        }
        recalculateSavings()
    }

    fun onNicknameChanged(nickname: String) {
        _uiState.update {
            it.copy(
                nickname = nickname,
                nicknameError = null,
                errorMessage = null
            )
        }
    }

    fun onPhotoUriChanged(uri: Uri?) {
        _uiState.update { it.copy(photoUri = uri, errorMessage = null) }
    }

    fun onNextStep() {
        val currentStep = _uiState.value.currentStep
        if (currentStep < _uiState.value.totalSteps - 1 && _uiState.value.canProceed) {
            val nextStep = currentStep + 1
            _uiState.update { it.copy(currentStep = nextStep, errorMessage = null) }
        }
    }

    fun onPreviousStep() {
        val currentStep = _uiState.value.currentStep
        if (currentStep > 0) {
            _uiState.update { it.copy(currentStep = currentStep - 1, errorMessage = null) }
        }
    }

    fun onSkip() {
        // Skip to the last step (profile step) or mark as complete
        _uiState.update { it.copy(currentStep = _uiState.value.totalSteps - 1, errorMessage = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Save the onboarding profile data to Firestore.
     * Creates both the private User document and the public PublicProfile document
     * in an atomic write batch.
     *
     * The write is best-effort: if Firestore is unavailable (e.g. rules not
     * published yet, network timeout, permission denied), the user still
     * navigates to the home screen. A safety-net timer guarantees navigation
     * even if the coroutine hangs.
     */
    fun saveProfile() {
        val state = _uiState.value
        val currentUser = firebaseAuth.currentUser

        // Guard: already saving? Prevent double-tap
        if (state.isLoading) {
            Timber.w("$TAG: saveProfile called while already loading — ignoring")
            return
        }

        // Cancel any previous save job (shouldn't happen, but defensive)
        saveJob?.cancel()

        if (currentUser == null) {
            _uiState.update {
                it.copy(errorMessage = "You must be signed in to complete onboarding.")
            }
            return
        }

        // Validate nickname one more time
        if (state.nickname.isBlank() || state.nickname.length < 2) {
            _uiState.update {
                it.copy(
                    nicknameError = "Nickname must be at least 2 characters.",
                    errorMessage = "Please enter a valid nickname."
                )
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null, nicknameError = null) }

        // ── Safety-net: guarantee navigation even if the coroutine hangs ──────
        // This prevents the user from being stuck on loading forever.
        val safetyNetJob = viewModelScope.launch {
            delay(FALLBACK_NAVIGATION_DELAY_MS)
            if (_uiState.value.isLoading && !_uiState.value.isComplete) {
                Timber.w("$TAG: Safety-net triggered — forcing navigation to home")
                _uiState.update { it.copy(isLoading = false, isComplete = true) }
            }
        }

        saveJob = viewModelScope.launch {
            try {
                val userId = currentUser.uid
                val quitTimestamp = Timestamp(Date(state.quitDate))

                // Upload photo to Cloudinary first, then save the remote URL
                // This ensures the URL persists across app restarts
                val resolvedPhotoUrl: String? = try {
                    if (state.photoUri != null) {
                        Timber.d("$TAG: Uploading profile photo to Cloudinary...")
                        val uploadResult = cloudinaryUploader.uploadProfileImageFromUri(
                            imageUri = state.photoUri,
                            userId = userId
                        )
                        uploadResult?.secureUrl.also {
                            if (it != null) {
                                Timber.i("$TAG: Profile photo uploaded: %s", it)
                            } else {
                                Timber.w("$TAG: Cloudinary upload returned null — falling back to Google photo")
                            }
                        }
                    } else {
                        // No local photo selected — use Google account photo if available
                        currentUser.photoUrl?.toString()
                    }
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: Failed to upload photo to Cloudinary — using null")
                    currentUser.photoUrl?.toString()
                }

                // Build the typed User model — matching the Firestore schema
                val userProfile = User(
                    email = currentUser.email ?: "",
                    nickname = state.nickname.trim(),
                    quitDate = quitTimestamp,
                    quitType = state.quitType,
                    cigarettesPerDay = state.cigarettesPerDay,
                    pricePerPack = state.pricePerPack,
                    cigarettesPerPack = state.cigarettesPerPack,
                    photoURL = resolvedPhotoUrl,
                    createdAt = Timestamp.now()
                )

                val publicProfile = PublicProfile(
                    nickname = state.nickname.trim(),
                    photoURL = resolvedPhotoUrl,
                    daysSmokeFree = 0,
                    xp = 0,
                    quitDate = quitTimestamp
                )

                // Use a write batch for atomicity (best-effort with timeout)
                // Use toFirestoreMap() for explicit field mapping to avoid
                // enum-serialization issues with Firestore's POJO converter.
                saveProfileToFirestore(userProfile, publicProfile, userId)

                // Cancel safety-net since we completed normally
                safetyNetJob.cancel()

                // Always mark as complete and navigate to home
                _uiState.update { it.copy(isLoading = false, isComplete = true) }
            } catch (e: CancellationException) {
                // Don't treat cancellation as an error, but reset loading state
                safetyNetJob.cancel()
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Unexpected error during onboarding")
                // Cancel safety-net and still navigate — don't block the user
                safetyNetJob.cancel()
                _uiState.update { it.copy(isLoading = false, isComplete = true) }
            }
        }
    }

    /**
     * Best-effort Firestore write with its own timeout.
     * Catches ALL exceptions (including FirebaseFirestoreException with
     * permission-denied) and never re-throws — the caller always navigates.
     *
     * If the initial write fails, schedules a background retry after 5 seconds
     * so the data is eventually persisted even if rules take time to propagate.
     */
    private suspend fun saveProfileToFirestore(
        userProfile: User,
        publicProfile: PublicProfile,
        userId: String
    ) {
        val success = tryWriteToFirestore(userProfile, publicProfile, userId)
        if (!success) {
            // Schedule a background retry after a delay — rules may not have propagated yet
            viewModelScope.launch {
                repeat(3) { attempt ->
                    delay(5_000L * (attempt + 1)) // 5s, 10s, 15s
                    Timber.d("$TAG: Retrying Firestore write (attempt %d) for uid=%s", attempt + 1, userId)
                    if (tryWriteToFirestore(userProfile, publicProfile, userId)) {
                        Timber.i("$TAG: Retry succeeded for uid=%s on attempt %d", userId, attempt + 1)
                        return@launch
                    }
                }
                Timber.e("$TAG: All retries exhausted for uid=%s — data may be missing from Firestore", userId)
            }
        }
    }

    /**
     * Attempt a single Firestore write. Returns true on success, false on failure.
     * Never throws — all exceptions are caught and logged.
     */
    private suspend fun tryWriteToFirestore(
        userProfile: User,
        publicProfile: PublicProfile,
        userId: String
    ): Boolean {
        return try {
            val userMap = userProfile.toFirestoreMap()
            val publicMap = mapOf<String, Any?>(
                "nickname" to publicProfile.nickname,
                "photoURL" to publicProfile.photoURL,
                "daysSmokeFree" to publicProfile.daysSmokeFree,
                "xp" to publicProfile.xp,
                "quitDate" to publicProfile.quitDate
            )
            val batch = firestore.batch()
            batch.set(firestore.collection(USERS_COLLECTION).document(userId), userMap)
            batch.set(
                firestore.collection(PUBLIC_PROFILES_COLLECTION).document(userId),
                publicMap
            )
            val result = withTimeoutOrNull(FIRESTORE_WRITE_TIMEOUT_MS) {
                batch.commit().await()
                true
            }
            if (result == null) {
                Timber.w("$TAG: Firestore write timed out during onboarding — continuing")
                false
            } else {
                Timber.i("$TAG: Onboarding profile saved for uid=%s", userId)
                true
            }
        } catch (e: CancellationException) {
            throw e // Propagate cancellation
        } catch (e: Exception) {
            // Firestore write failed (e.g. rules not deployed yet, permission denied).
            // Don't block the user — they can still use the app.
            Timber.w(e, "$TAG: Firestore write failed during onboarding — continuing")
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Cancel any in-flight save job when the ViewModel is destroyed
        saveJob?.cancel()
        Timber.d("$TAG: OnboardingViewModel cleared")
    }

    private fun recalculateSavings() {
        val state = _uiState.value
        if (state.cigarettesPerPack > 0 && state.pricePerPack > 0) {
            val pricePerCigarette = state.pricePerPack / state.cigarettesPerPack
            val dailySavings = pricePerCigarette * state.cigarettesPerDay
            val monthlySavings = dailySavings * 30
            val yearlySavings = dailySavings * 365

            _uiState.update {
                it.copy(
                    dailySavings = (Math.round(dailySavings * 100.0) / 100.0),
                    monthlySavings = (Math.round(monthlySavings * 100.0) / 100.0),
                    yearlySavings = (Math.round(yearlySavings * 100.0) / 100.0)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel Factory — manual DI replacing @HiltViewModel
// ═══════════════════════════════════════════════════════════════════════════════

class OnboardingViewModelFactory(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
    private val cloudinaryUploader: com.breathy.utils.CloudinaryUploader
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return OnboardingViewModel(firestore, firebaseAuth, cloudinaryUploader) as T
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Date Picker Helper
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Show a native Android [DatePickerDialog] and invoke [onDateSelected] with
 * the chosen date as epoch millis.
 */
internal fun showDatePicker(
    context: android.content.Context,
    currentMillis: Long,
    onDateSelected: (Long) -> Unit
) {
    val calendar = Calendar.getInstance().apply { timeInMillis = currentMillis }
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    DatePickerDialog(
        context,
        { _, selectedYear, selectedMonth, selectedDay ->
            val selected = Calendar.getInstance().apply {
                set(selectedYear, selectedMonth, selectedDay, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            onDateSelected(selected.timeInMillis)
        },
        year,
        month,
        day
    ).apply {
        // Allow selecting today and past dates (quit date can be in the past)
        datePicker.maxDate = System.currentTimeMillis()
    }.show()
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Onboarding Screen — Main Composable
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * 4-step onboarding flow:
 * - Step 1: Welcome + set quit date
 * - Step 2: Choose quit type (instant/gradual) with cards
 * - Step 3: Smoking habits (cigarettes/day, price/pack, cigs/pack)
 * - Step 4: Set nickname + optional profile photo
 *
 * All data is saved to Firestore atomically on completion.
 *
 * @param onNavigateToHome Callback invoked after profile is saved successfully.
 */
@Composable
fun OnboardingScreen(
    onNavigateToHome: () -> Unit,
    viewModel: OnboardingViewModel = run {
        val context = LocalContext.current
        val appModule = (context.applicationContext as BreathyApplication).appModule
        viewModel(factory = OnboardingViewModelFactory(appModule.firestore, appModule.firebaseAuth, appModule.cloudinaryUploader))
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { uiState.totalSteps })
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // ── Navigate to home on completion ───────────────────────────────────────
    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) {
            onNavigateToHome()
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

    // ── Sync pager with ViewModel step ───────────────────────────────────────
    LaunchedEffect(uiState.currentStep) {
        if (pagerState.currentPage != uiState.currentStep) {
            pagerState.animateScrollToPage(uiState.currentStep)
        }
    }

    // ── Cleanup on dispose ───────────────────────────────────────────────────
    DisposableEffect(Unit) {
        onDispose {
            Timber.d("OnboardingScreen disposed")
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
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ── Progress indicator ────────────────────────────────────────
            LinearProgressIndicator(
                progress = { (uiState.currentStep + 1).toFloat() / uiState.totalSteps },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AccentPrimary,
                trackColor = BgSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Step indicator text
            Text(
                text = "Step ${uiState.currentStep + 1} of ${uiState.totalSteps}",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Horizontal pager with smooth transitions ──────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                userScrollEnabled = false
            ) { page ->
                when (page) {
                    0 -> WelcomeStep(
                        quitDate = uiState.quitDate,
                        onQuitDateSelected = viewModel::onQuitDateSelected
                    )
                    1 -> QuitTypeStep(
                        quitType = uiState.quitType,
                        onQuitTypeChanged = viewModel::onQuitTypeChanged
                    )
                    2 -> SmokingHabitsStep(
                        cigarettesPerDay = uiState.cigarettesPerDay,
                        pricePerPack = uiState.pricePerPack,
                        cigarettesPerPack = uiState.cigarettesPerPack,
                        dailySavings = uiState.dailySavings,
                        monthlySavings = uiState.monthlySavings,
                        yearlySavings = uiState.yearlySavings,
                        onCigarettesPerDayChanged = viewModel::onCigarettesPerDayChanged,
                        onPricePerPackChanged = viewModel::onPricePerPackChanged,
                        onCigarettesPerPackChanged = viewModel::onCigarettesPerPackChanged
                    )
                    3 -> ProfileStep(
                        nickname = uiState.nickname,
                        nicknameError = uiState.nicknameError,
                        photoUri = uiState.photoUri,
                        onNicknameChanged = viewModel::onNicknameChanged,
                        onPhotoUriChanged = viewModel::onPhotoUriChanged
                    )
                }
            }

            // ── Page indicator dots ───────────────────────────────────────
            PageIndicator(
                totalPages = uiState.totalSteps,
                currentPage = uiState.currentStep
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Navigation buttons ────────────────────────────────────────
            OnboardingNavigation(
                currentStep = uiState.currentStep,
                totalSteps = uiState.totalSteps,
                canProceed = uiState.canProceed,
                isLoading = uiState.isLoading,
                onNextClick = viewModel::onNextStep,
                onBackClick = viewModel::onPreviousStep,
                onSkipClick = viewModel::onSkip,
                onLetsGoClick = viewModel::saveProfile
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Step 1: Welcome + Quit Date
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun WelcomeStep(
    quitDate: Long,
    onQuitDateSelected: (Long) -> Unit
) {
    val context = LocalContext.current
    val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val infiniteTransition = rememberInfiniteTransition(label = "welcomeGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Welcome illustration with animated glow
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .graphicsLayer { alpha = glowAlpha }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(AccentPrimary.copy(alpha = 0.3f), Color.Transparent)
                        ),
                        CircleShape
                    )
            )
            Icon(
                imageVector = Icons.Default.LocalFireDepartment,
                contentDescription = "Welcome — fire icon representing your determination",
                tint = AccentPrimary,
                modifier = Modifier.size(72.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Your Journey Starts Here",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Every breath is a step toward freedom.\nLet's set your quit date.",
            fontSize = 15.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = "When did you quit (or plan to quit)?",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Date picker field
        OutlinedTextField(
            value = dateFormatter.format(Date(quitDate)),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showDatePicker(context, quitDate, onQuitDateSelected)
                },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = "Calendar icon for quit date",
                    tint = AccentSecondary
                )
            },
            trailingIcon = {
                TextButton(
                    onClick = { showDatePicker(context, quitDate, onQuitDateSelected) }
                ) {
                    Text(
                        text = "Change",
                        color = AccentPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentSecondary,
                unfocusedBorderColor = BgSurfaceVariant,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Helpful note
        Text(
            text = "You can set today's date if you're just starting,\nor a past date if you've already quit.",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    AccentPrimary.copy(alpha = 0.06f),
                    RoundedCornerShape(10.dp)
                )
                .padding(14.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Step 2: Quit Type — Instant vs Gradual with cards
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun QuitTypeStep(
    quitType: QuitType,
    onQuitTypeChanged: (QuitType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "How Are You Quitting?",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Choose the approach that works best for you.\nYou can change this later.",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // ── Instant quit card ────────────────────────────────────────────
        QuitTypeCard(
            title = "Instant Quit",
            subtitle = "Cold Turkey",
            description = "Stop smoking immediately. Strong and decisive \u2014 we'll help you power through cravings with breathing exercises and distractions.",
            icon = Icons.Default.Speed,
            isSelected = quitType == QuitType.INSTANT,
            accentColor = AccentPrimary,
            onClick = { onQuitTypeChanged(QuitType.INSTANT) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Gradual quit card ────────────────────────────────────────────
        QuitTypeCard(
            title = "Gradual Reduction",
            subtitle = "Step by Step",
            description = "Reduce your daily intake over time. We'll create a tapering schedule to help you ease into a smoke-free life at your own pace.",
            icon = Icons.Default.TrendingDown,
            isSelected = quitType == QuitType.GRADUAL,
            accentColor = AccentSecondary,
            onClick = { onQuitTypeChanged(QuitType.GRADUAL) }
        )
    }
}

@Composable
private fun QuitTypeCard(
    title: String,
    subtitle: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) accentColor else BgSurfaceVariant
    val backgroundColor = if (isSelected) accentColor.copy(alpha = 0.08f) else BgSurfaceVariant.copy(alpha = 0.5f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "$title icon",
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) accentColor else TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = description,
            fontSize = 13.sp,
            color = TextSecondary,
            lineHeight = 18.sp
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Step 3: Smoking Habits & Savings Preview
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SmokingHabitsStep(
    cigarettesPerDay: Int,
    pricePerPack: Double,
    cigarettesPerPack: Int,
    dailySavings: Double,
    monthlySavings: Double,
    yearlySavings: Double,
    onCigarettesPerDayChanged: (Int) -> Unit,
    onPricePerPackChanged: (Double) -> Unit,
    onCigarettesPerPackChanged: (Int) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Your Smoking Habits",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This helps us calculate your savings\nand track your progress.",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // ── Cigarettes per day stepper ───────────────────────────────────
        Text(
            text = "Cigarettes per day",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        StepperControl(
            value = cigarettesPerDay,
            onValueChange = onCigarettesPerDayChanged,
            minValue = 1,
            maxValue = 60
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Price per pack ───────────────────────────────────────────────
        Text(
            text = "Price per pack",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        var priceText by remember {
            mutableStateOf(if (pricePerPack > 0) String.format("%.2f", pricePerPack) else "")
        }

        OutlinedTextField(
            value = priceText,
            onValueChange = { newText ->
                val filtered = newText.filter { it.isDigit() || it == '.' }
                if (filtered.count { it == '.' } <= 1) {
                    priceText = filtered
                    val parsed = filtered.toDoubleOrNull()
                    if (parsed != null) {
                        onPricePerPackChanged(parsed)
                    } else if (filtered.isEmpty()) {
                        onPricePerPackChanged(0.0)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            prefix = {
                Text("$", color = AccentPrimary, fontWeight = FontWeight.Bold)
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentPrimary,
                unfocusedBorderColor = BgSurfaceVariant,
                cursorColor = AccentPrimary,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Cigarettes per pack ──────────────────────────────────────────
        Text(
            text = "Cigarettes per pack",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        var packText by remember {
            mutableStateOf(if (cigarettesPerPack > 0) cigarettesPerPack.toString() else "")
        }

        OutlinedTextField(
            value = packText,
            onValueChange = { newText ->
                val filtered = newText.filter { it.isDigit() }
                packText = filtered
                val parsed = filtered.toIntOrNull()
                if (parsed != null && parsed > 0) {
                    onCigarettesPerPackChanged(parsed)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            suffix = {
                Text("cigs", color = TextSecondary, fontSize = 13.sp)
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { keyboardController?.hide() }
            ),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentPrimary,
                unfocusedBorderColor = BgSurfaceVariant,
                cursorColor = AccentPrimary,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // ── Savings preview card ─────────────────────────────────────────
        if (dailySavings > 0) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BgSurfaceVariant)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Your Potential Savings",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SavingsItem(label = "Daily", value = "$${String.format("%.2f", dailySavings)}")
                    SavingsItem(label = "Monthly", value = "$${String.format("%.2f", monthlySavings)}")
                    SavingsItem(label = "Yearly", value = "$${String.format("%.0f", yearlySavings)}")
                }
            }
        }
    }
}

@Composable
private fun StepperControl(
    value: Int,
    onValueChange: (Int) -> Unit,
    minValue: Int,
    maxValue: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgSurfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = { if (value > minValue) onValueChange(value - 1) },
            enabled = value > minValue
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrease value",
                tint = if (value > minValue) AccentPrimary else TextSecondary.copy(alpha = 0.3f)
            )
        }

        Text(
            text = value.toString(),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        IconButton(
            onClick = { if (value < maxValue) onValueChange(value + 1) },
            enabled = value < maxValue
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase value",
                tint = if (value < maxValue) AccentPrimary else TextSecondary.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun SavingsItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = AccentPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextSecondary
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Step 4: Profile — Nickname + Optional Photo
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ProfileStep(
    nickname: String,
    nicknameError: String?,
    photoUri: Uri?,
    onNicknameChanged: (String) -> Unit,
    onPhotoUriChanged: (Uri?) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val infiniteTransition = rememberInfiniteTransition(label = "profileGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "profileGlow"
    )

    // Image picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        onPhotoUriChanged(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Profile photo with glow
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .clickable { photoPickerLauncher.launch("image/*") }
        ) {
            // Glow
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer { alpha = glowAlpha }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(AccentPurple.copy(alpha = 0.3f), Color.Transparent)
                        ),
                        CircleShape
                    )
            )

            // Photo or placeholder
            if (photoUri != null) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = "Profile photo",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .border(2.dp, AccentPrimary, CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(BgSurfaceVariant)
                        .border(
                            2.dp,
                            AccentPrimary.copy(alpha = 0.4f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Default profile icon",
                            tint = TextSecondary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Add Photo",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Set Your Profile",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Choose a nickname for the community.\nYou can add a photo too!",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // ── Nickname field ───────────────────────────────────────────────
        Text(
            text = "Nickname",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = onNicknameChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "e.g. BreathyBree",
                    color = TextSecondary.copy(alpha = 0.5f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Nickname input icon",
                    tint = TextSecondary
                )
            },
            isError = nicknameError != null,
            supportingText = nicknameError?.let {
                {
                    Text(
                        text = it,
                        color = SemanticError,
                        fontSize = 12.sp
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { keyboardController?.hide() }
            ),
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

        Spacer(modifier = Modifier.height(16.dp))

        // Photo change button
        OutlinedButton(
            onClick = { photoPickerLauncher.launch("image/*") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = AccentPrimary
            )
        ) {
            Icon(
                imageVector = if (photoUri != null) Icons.Default.Check else Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (photoUri != null) "Photo selected \u2714" else "Add profile photo (optional)",
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Motivational card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AccentPurple.copy(alpha = 0.08f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Almost there!",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AccentPurple
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your nickname will be visible to other community members. You can change it anytime in your profile settings.",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Page Indicator — Animated stepper dots
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PageIndicator(totalPages: Int, currentPage: Int) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalPages) { index ->
            val isSelected = index == currentPage
            val width by animateDpAsState(
                targetValue = if (isSelected) 24.dp else 8.dp,
                animationSpec = tween(durationMillis = 300),
                label = "dotWidth$index"
            )
            Box(
                modifier = Modifier
                    .size(width, 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isSelected) AccentPrimary else BgSurfaceVariant
                    )
            )
            if (index < totalPages - 1) {
                Spacer(modifier = Modifier.width(6.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Navigation Buttons — Back / Skip / Next / Let's Go
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun OnboardingNavigation(
    currentStep: Int,
    totalSteps: Int,
    canProceed: Boolean,
    isLoading: Boolean,
    onNextClick: () -> Unit,
    onBackClick: () -> Unit,
    onSkipClick: () -> Unit,
    onLetsGoClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Back button ──────────────────────────────────────────────────
        if (currentStep > 0) {
            OutlinedButton(
                onClick = onBackClick,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextSecondary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Go back to previous step",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Back", fontSize = 14.sp)
            }
        } else {
            // Spacer to maintain layout on first step
            Spacer(modifier = Modifier.width(1.dp))
        }

        // ── Skip button (visible on steps 0-2) ──────────────────────────
        if (currentStep < totalSteps - 1) {
            TextButton(
                onClick = onSkipClick,
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = "Skip",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        }

        // ── Next / Let's Go button ───────────────────────────────────────
        if (currentStep < totalSteps - 1) {
            Button(
                onClick = onNextClick,
                enabled = canProceed && !isLoading,
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPrimary,
                    disabledContainerColor = AccentPrimary.copy(alpha = 0.3f),
                    contentColor = BgPrimary
                )
            ) {
                Text("Next", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Go to next step",
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            // "Let's Go!" gradient CTA button
            Button(
                onClick = onLetsGoClick,
                enabled = canProceed && !isLoading,
                modifier = Modifier
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    contentColor = BgPrimary
                ),
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
                            text = "Let's Go!",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = BgPrimary
                        )
                    }
                }
            }
        }
    }
}
