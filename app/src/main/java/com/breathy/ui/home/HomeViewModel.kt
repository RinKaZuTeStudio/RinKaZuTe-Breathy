package com.breathy.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.breathy.data.models.Achievement
import com.breathy.data.models.CopingMethod
import com.breathy.data.models.HealthMilestone
import com.breathy.data.models.User
import com.breathy.data.repository.RewardRepository
import com.breathy.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.DecimalFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════════════════════════════════
//  UI State
// ═══════════════════════════════════════════════════════════════════════════════

data class HomeUiState(
    val isLoading: Boolean = true,
    val nickname: String = "",
    val photoURL: String? = null,
    val daysSmokeFree: Int = 0,
    val moneySaved: Double = 0.0,
    val cigarettesAvoided: Int = 0,
    val lifeRegainedMinutes: Int = 0,
    val xp: Int = 0,
    val coins: Int = 0,
    val level: Int = 1,
    val xpForNextLevel: Int = 100,
    val levelProgress: Float = 0f,
    val healthMilestones: List<Pair<HealthMilestone, Boolean>> = emptyList(),
    val dailyRewardClaimed: Boolean = true,
    val dailyRewardCoins: Int = 0,
    val showConfetti: Boolean = false,
    val newAchievements: List<Achievement> = emptyList(),
    val lastCravingTimeAgo: String? = null,
    val errorMessage: String? = null,
    val cravingLogged: Boolean = false,
    val dailyRewardJustClaimed: Int = 0,
    /** Seconds until next daily reward is available (0 = available now). */
    val dailyRewardCountdownSeconds: Long = 0L,
    /** Total successful craving resistances. */
    val successfulCravings: Int = 0
)

sealed class HomeSingleEvent {
    data class ShowAchievementUnlock(val achievement: Achievement) : HomeSingleEvent()
    data class ShowDailyReward(val coins: Int) : HomeSingleEvent()
    data class ShowCravingXP(val xp: Int) : HomeSingleEvent()
    data class ShowError(val message: String) : HomeSingleEvent()
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

class HomeViewModel(
    private val userRepository: UserRepository,
    private val rewardRepository: RewardRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val userId: String?
        get() = auth.currentUser?.uid

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HomeSingleEvent>()
    val events: SharedFlow<HomeSingleEvent> = _events.asSharedFlow()

    private val moneyFormat = DecimalFormat("$#,##0.00")

    init {
        loadUserData()
    }

    // ── Data Loading ──────────────────────────────────────────────────────

    private fun loadUserData() {
        val uid = userId ?: run {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Not authenticated") }
            return
        }

        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            try {
                userRepository.observeUser(uid)
                    .debounce(300L) // Prevent rapid-fire updates from Firestore
                    .collect { user ->
                        try {
                            processUserUpdate(user, uid)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Individual emission processing failed — log but keep
                            // the flow alive so future emissions can still update UI
                            Timber.e(e, "$TAG: Error processing user data emission")
                            // Ensure loading is turned off even on error
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    nickname = it.nickname.ifBlank { "Quitter" }
                                )
                            }
                        }
                    }
            } catch (e: CancellationException) {
                // Don't treat cancellation as an error
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to load user data")
                // Show a user-friendly error with fallback data instead of crashing
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        nickname = it.nickname.ifBlank { "Quitter" },
                        errorMessage = "Could not load your data. Pull down to retry."
                    )
                }
            }
        }

        loadLastCravingTime(uid)
    }

    /**
     * Process a user data update from Firestore safely.
     * All calculations are wrapped in try-catch to prevent any single
     * calculation from crashing the entire app.
     */
    private suspend fun processUserUpdate(user: User, uid: String) {
        val daysSmokeFree = try { user.daysSmokeFree } catch (_: Exception) { 0 }
        val moneySaved = try { user.moneySaved() } catch (_: Exception) { 0.0 }
        val cigarettesAvoided = try { user.cigarettesAvoided() } catch (_: Exception) { 0 }
        val lifeRegained = cigarettesAvoided * 11 // 11 minutes per cigarette

        val milestones = try {
            userRepository.getCurrentMilestones(user.quitDate.toDate())
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to get health milestones")
            emptyList()
        }

        val level = try { rewardRepository.calculateLevel(user.xp) } catch (_: Exception) { 1 }
        val xpForNextLevel = try { rewardRepository.getXPForNextLevel(user.xp) } catch (_: Exception) { 100 }
        val levelProgress = try { rewardRepository.getLevelProgress(user.xp) } catch (_: Exception) { 0f }

        val lastClaim = user.lastDailyClaim?.toDate()
        val now = Calendar.getInstance()
        val dailyClaimed = lastClaim?.let {
            val cal = Calendar.getInstance().apply { time = it }
            cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        } ?: false

        // Calculate countdown to next midnight (when reward resets)
        val countdownSeconds = if (dailyClaimed) {
            val nextMidnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            (nextMidnight.timeInMillis - System.currentTimeMillis()) / 1000
        } else 0L

        val successfulCravings = try {
            userRepository.getSuccessfulCravingCount(uid)
        } catch (_: Exception) { 0 }

        _uiState.update { state ->
            state.copy(
                isLoading = false,
                nickname = user.nickname.ifBlank { "Quitter" },
                photoURL = user.photoURL,
                daysSmokeFree = daysSmokeFree,
                moneySaved = moneySaved,
                cigarettesAvoided = cigarettesAvoided,
                lifeRegainedMinutes = lifeRegained,
                xp = user.xp,
                coins = user.coins,
                level = level,
                xpForNextLevel = xpForNextLevel,
                levelProgress = levelProgress,
                healthMilestones = milestones,
                dailyRewardClaimed = dailyClaimed,
                dailyRewardCountdownSeconds = countdownSeconds.coerceAtLeast(0),
                successfulCravings = successfulCravings,
                errorMessage = null
            )
        }

        // Check for achievements on data change (best-effort, don't crash)
        try {
            checkAchievements()
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Achievement check failed — non-critical")
        }
    }

    private fun loadLastCravingTime(uid: String) {
        viewModelScope.launch {
            try {
                val lastTime = userRepository.getLastCravingTime(uid)
                val timeAgoText = lastTime?.let { formatTimeAgo(it) }
                _uiState.update { it.copy(lastCravingTimeAgo = timeAgoText) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load last craving time")
            }
        }
    }

    private fun formatTimeAgo(date: Date): String {
        val diffMillis = System.currentTimeMillis() - date.time
        if (diffMillis < 0) return "just now"

        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
        val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
        val days = TimeUnit.MILLISECONDS.toDays(diffMillis)

        return when {
            days > 0 -> "${days}d ${hours % 24}h since last craving"
            hours > 0 -> "${hours}h ${minutes % 60}m since last craving"
            minutes > 0 -> "${minutes}m since last craving"
            else -> "just now"
        }
    }

    // ── Achievement Checking ──────────────────────────────────────────────

    private suspend fun checkAchievements() {
        val uid = userId ?: return
        try {
            val result = rewardRepository.checkAndUnlockAchievement(uid)
            result.onSuccess { newAchievements ->
                if (newAchievements.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            newAchievements = newAchievements,
                            showConfetti = true
                        )
                    }
                    for (achievement in newAchievements) {
                        _events.emit(HomeSingleEvent.ShowAchievementUnlock(achievement))
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check achievements")
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────

    fun claimDailyReward() {
        val uid = userId ?: return
        viewModelScope.launch {
            try {
                val result = userRepository.claimDailyReward(uid)
                result.onSuccess { coinsAwarded ->
                    _uiState.update {
                        it.copy(
                            dailyRewardClaimed = true,
                            dailyRewardJustClaimed = coinsAwarded,
                            showConfetti = true
                        )
                    }
                    _events.emit(HomeSingleEvent.ShowDailyReward(coinsAwarded))
                }.onFailure { e ->
                    _events.emit(HomeSingleEvent.ShowError(e.message ?: "Failed to claim reward"))
                }
            } catch (e: Exception) {
                _events.emit(HomeSingleEvent.ShowError(e.message ?: "Failed to claim reward"))
            }
        }
    }

    fun logCraving(copingMethod: CopingMethod, success: Boolean) {
        val uid = userId ?: return
        viewModelScope.launch {
            try {
                val result = userRepository.logCraving(uid, copingMethod, success)
                result.onSuccess {
                    val xpAwarded = if (success) {
                        RewardRepository.XP_CRAVING_RESISTED
                    } else {
                        RewardRepository.XP_CRAVING_RESISTED / 2
                    }
                    userRepository.addXp(uid, xpAwarded)
                    _uiState.update { it.copy(cravingLogged = true) }
                    _events.emit(HomeSingleEvent.ShowCravingXP(xpAwarded))

                    // Refresh last craving time
                    loadLastCravingTime(uid)
                }.onFailure { e ->
                    _events.emit(HomeSingleEvent.ShowError(e.message ?: "Failed to log craving"))
                }
            } catch (e: Exception) {
                _events.emit(HomeSingleEvent.ShowError(e.message ?: "Failed to log craving"))
            }
        }
    }

    fun dismissConfetti() {
        _uiState.update { it.copy(showConfetti = false) }
    }

    fun clearNewAchievements() {
        _uiState.update { it.copy(newAchievements = emptyList()) }
    }

    fun clearDailyRewardClaimed() {
        _uiState.update { it.copy(dailyRewardJustClaimed = 0) }
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        loadUserData()
    }

    // ── Formatters ────────────────────────────────────────────────────────

    fun formatLifeRegained(minutes: Int): String {
        val days = minutes / (24 * 60)
        val hours = (minutes % (24 * 60)) / 60
        val mins = minutes % 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${mins}m"
            else -> "${mins}m"
        }
    }

    fun formatMoneySaved(amount: Double): String {
        return moneyFormat.format(amount)
    }

    fun formatCountdown(seconds: Long): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%dh %02dm", h, m)
        else String.format(Locale.US, "%dm %02ds", m, s)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel Factory — manual DI (not Hilt)
// ═══════════════════════════════════════════════════════════════════════════════

class HomeViewModelFactory(
    private val userRepository: UserRepository,
    private val rewardRepository: RewardRepository,
    private val auth: FirebaseAuth
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(userRepository, rewardRepository, auth) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
