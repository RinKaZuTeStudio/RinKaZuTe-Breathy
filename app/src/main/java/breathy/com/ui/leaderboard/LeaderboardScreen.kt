@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class)

package breathy.com.ui.leaderboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import breathy.com.ui.theme.DeepForest
import breathy.com.ui.theme.DarkBotanical
import breathy.com.ui.theme.SoftSage
import breathy.com.ui.theme.VeryLightSage
import breathy.com.ui.theme.SoftSand
import breathy.com.ui.theme.PureWhite
import breathy.com.ui.theme.NaturalYellow
import breathy.com.ui.theme.GoldDeep
import breathy.com.ui.theme.AchievementGold
import breathy.com.ui.theme.AchievementSilver
import breathy.com.ui.theme.AchievementBronze
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import breathy.com.ui.components.NetworkImage
import breathy.com.BreathyApplication
import breathy.com.data.models.PublicProfile
import breathy.com.data.repository.EventRepository
import breathy.com.data.repository.UserRepository
import breathy.com.utils.s
import breathy.com.ui.theme.AccentPrimary
import breathy.com.ui.theme.AccentPurple
import breathy.com.ui.theme.AccentSecondary
import breathy.com.ui.theme.themeBgPrimary
import breathy.com.ui.theme.themeBgSurface
import breathy.com.ui.theme.themeBgSurfaceVariant
import breathy.com.ui.theme.themeTextPrimary
import breathy.com.ui.theme.themeTextSecondary
import breathy.com.ui.theme.themeTextDisabled

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.text.font.FontFamily

// ═══════════════════════════════════════════════════════════════════════════════
//  UI State
// ═══════════════════════════════════════════════════════════════════════════════

data class LeaderboardUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val entries: List<LeaderboardEntry> = emptyList(),
    val currentUserEntry: LeaderboardEntry? = null,
    val currentUserRank: Int = 0,
    val selectedPeriod: LeaderboardPeriod = LeaderboardPeriod.ALL_TIME,
    val errorMessage: String? = null,
    /** REAL member count — server-side aggregate of actual created accounts. */
    val memberCount: Int = 0,
    /** v1.0.11 — one-shot prize-payout notice (cleared after showing). */
    val rewardNotice: String? = null
)

data class LeaderboardEntry(
    val userId: String = "",
    val nickname: String = "",
    val photoURL: String? = null,
    /** XP for the SELECTED period (lifetime / this week / this month). */
    val xp: Int = 0,
    val daysSmokeFree: Int = 0,
    val rank: Int = 0,
    val avatarFrame: breathy.com.data.models.AvatarFrame = breathy.com.data.models.AvatarFrame.NONE,
    val isPremium: Boolean = false,
    val level: Int = 1,
    /** v1.0.9 unified profile picture id — renders on every row. */
    val profilePicture: String? = null
)

// v1.0.9: LeaderboardPeriod moved to breathy.com.data.models (shared with UserRepository).
typealias LeaderboardPeriod = breathy.com.data.models.LeaderboardPeriod

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

class LeaderboardViewModel(
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth,
    /** v1.0.11 — settles the weekly/monthly rollover and rank payouts. */
    private val leaderboardRepository: breathy.com.data.repository.LeaderboardRepository? = null
) : ViewModel() {

    companion object {
        private const val TAG = "LeaderboardViewModel"
        private const val LEADERBOARD_LIMIT = 50

        /**
         * SAFE INITIAL (PRE-LAUNCH) LEADERBOARD RESET — one-time, development-stage.
         *
         * The launch build ships with this fixed cutoff timestamp. Any public
         * profile whose last activity (`updatedAt`) predates the cutoff is
         * treated as test/demo data and is NOT shown on the leaderboard — so
         * the board starts with ZERO users until real users join and use the
         * app. This is NOT a recurring deletion mechanism:
         * - nothing is ever deleted (user data integrity is preserved);
         * - the filter is a fixed constant baked into this release;
         * - any user whose profile is updated after the cutoff (XP change,
         *   avatar change, etc.) appears on the leaderboard normally;
         * - no account deletion, no "wipe all users" logic, ever.
         */
        private val LEADERBOARD_RESET_CUTOFF: Long = java.util.Calendar.getInstance(
            java.util.TimeZone.getTimeZone("UTC")
        ).apply {
            clear()
            set(2026, java.util.Calendar.SEPTEMBER, 1, 0, 0, 0)
        }.timeInMillis
    }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    /** Active period collection — cancelled & restarted on period switch. */
    private var periodJob: kotlinx.coroutines.Job? = null

    init {
        loadLeaderboard()
    }

    fun loadLeaderboard() {
        val uid = currentUserId ?: run {
            _uiState.update { it.copy(isLoading = false, errorMessage = s("Not authenticated", "غير مسجل الدخول")) }
            return
        }

        // v1.0.11 — settle the period rollover + payouts in the background.
        // Idempotent: finalizing is transaction-guarded and prizes are
        // dedup-keyed, so repeated loads never double-pay.
        val settlePeriod = _uiState.value.selectedPeriod
        if (settlePeriod != breathy.com.data.models.LeaderboardPeriod.ALL_TIME && leaderboardRepository != null) {
            viewModelScope.launch {
                val result = leaderboardRepository.finalizeAndClaim(settlePeriod) ?: return@launch
                if (result.goldAwarded > 0) {
                    val label = if (settlePeriod == breathy.com.data.models.LeaderboardPeriod.WEEKLY)
                        s("weekly", "الأسبوعي") else s("monthly", "الشهري")
                    _uiState.update {
                        it.copy(
                            rewardNotice = s(
                                "🏆 +%1\$d Gold — rank #%3\$d in the %2\$s leaderboard!",
                                "🏆 +%1\$d ذهب — المركز #%3\$d في قائمة %2\$s!"
                            ).format(result.goldAwarded, label, result.myRank ?: 0)
                        )
                    }
                }
            }
        }

        periodJob?.cancel()
        periodJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                // v1.0.9 — REAL period-aware query: Weekly / Monthly rank by the
                // auto-resetting weeklyXp / monthlyXp mirrors; All-Time by xp.
                val period = _uiState.value.selectedPeriod
                val flow = userRepository.observePublicProfilesForPeriod(LEADERBOARD_LIMIT, period)
                val currentWeekKey = UserRepository.weeklyXpPeriodKey()
                val currentMonthKey = UserRepository.monthlyXpPeriodKey()

                flow.collect { profilePairs ->
                    // Apply the one-time initial reset filter: only profiles
                    // active after the cutoff count as real leaderboard users.
                    // Weekly/Monthly additionally drop stale-period profiles.
                    val realProfiles = profilePairs.filter { (_, profile) ->
                        val lastActivity = profile.updatedAt?.toDate()?.time ?: 0L
                        val activeAfterCutoff = lastActivity >= LEADERBOARD_RESET_CUTOFF
                        val inCurrentPeriod = when (period) {
                            breathy.com.data.models.LeaderboardPeriod.WEEKLY ->
                                profile.weeklyXpPeriod == currentWeekKey
                            breathy.com.data.models.LeaderboardPeriod.MONTHLY ->
                                profile.monthlyXpPeriod == currentMonthKey
                            breathy.com.data.models.LeaderboardPeriod.ALL_TIME -> true
                        }
                        activeAfterCutoff && inCurrentPeriod
                    }
                    val entries = realProfiles.mapIndexed { index, pair ->
                        val (userId, profile) = pair
                        LeaderboardEntry(
                            userId = userId,
                            nickname = profile.nickname,
                            photoURL = profile.photoURL,
                            xp = when (period) {
                                breathy.com.data.models.LeaderboardPeriod.WEEKLY -> profile.weeklyXp
                                breathy.com.data.models.LeaderboardPeriod.MONTHLY -> profile.monthlyXp
                                breathy.com.data.models.LeaderboardPeriod.ALL_TIME -> profile.xp
                            },
                            daysSmokeFree = profile.daysSmokeFree,
                            rank = index + 1,
                            avatarFrame = breathy.com.data.models.AvatarFrame.fromId(profile.avatarFrame),
                            isPremium = profile.premium,
                            level = breathy.com.data.models.User.computeLevel(profile.xp),
                            profilePicture = profile.profilePicture
                        )
                    }

                    val currentUserEntry = entries.find { it.userId == uid }

                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            entries = entries,
                            currentUserEntry = currentUserEntry,
                            errorMessage = null,
                            currentUserRank = currentUserEntry?.rank ?: state.currentUserRank
                        )
                    }

                    // REAL member count — aggregate COUNT over actual accounts
                    // with the same one-time reset filter. Never invented.
                    try {
                        val count = userRepository.countLeaderboardMembers(LEADERBOARD_RESET_CUTOFF)
                            .getOrDefault(0)
                        _uiState.update { it.copy(memberCount = count) }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to count leaderboard members")
                    }

                    // Fetch current user's own rank if not found in entries
                    if (currentUserEntry == null) {
                        fetchCurrentUserRank(uid, realProfiles)
                    }
                }
            } catch (e: CancellationException) {
                // Ignore
            } catch (e: Exception) {
                Timber.e(e, "Failed to load leaderboard")
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = s("Failed to load leaderboard: ${e.message}", "فشل تحميل قائمة المتصدرين: ${e.message}"))
                }
            }
        }
    }

    private suspend fun fetchCurrentUserRank(uid: String, profilePairs: List<Pair<String, PublicProfile>>) {
        try {
            val userProfile = userRepository.getPublicProfile(uid).getOrNull() ?: return
            // The user's own entry follows the same initial-reset rule.
            val lastActivity = userProfile.updatedAt?.toDate()?.time ?: 0L
            if (lastActivity < LEADERBOARD_RESET_CUTOFF) return
            // Match by document ID (= userId) instead of nickname to avoid duplicate-nickname issues
            val listRank = profilePairs.indexOfFirst { it.first == uid } + 1
            // TRUE global rank: when outside the loaded page, count everyone with
            // more XP (server-side COUNT) instead of guessing page size + 1.
            val trueRank = if (listRank > 0) listRank else {
                val higher = userRepository.countProfilesWithHigherXp(
                    xp = userProfile.xp.toLong(),
                    cutoffMillis = LEADERBOARD_RESET_CUTOFF
                ).getOrDefault(0L).toInt()
                higher + 1
            }
            val entry = LeaderboardEntry(
                userId = uid,
                nickname = userProfile.nickname,
                photoURL = userProfile.photoURL,
                xp = when (_uiState.value.selectedPeriod) {
                    breathy.com.data.models.LeaderboardPeriod.WEEKLY -> userProfile.weeklyXp
                    breathy.com.data.models.LeaderboardPeriod.MONTHLY -> userProfile.monthlyXp
                    breathy.com.data.models.LeaderboardPeriod.ALL_TIME -> userProfile.xp
                },
                daysSmokeFree = userProfile.daysSmokeFree,
                rank = trueRank,
                avatarFrame = breathy.com.data.models.AvatarFrame.fromId(userProfile.avatarFrame),
                isPremium = userProfile.premium,
                level = breathy.com.data.models.User.computeLevel(userProfile.xp),
                profilePicture = userProfile.profilePicture
            )
            _uiState.update {
                it.copy(currentUserEntry = entry, currentUserRank = entry.rank)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch current user rank")
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            delay(500) // minimum refresh time for UX
            loadLeaderboard()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun selectPeriod(period: LeaderboardPeriod) {
        if (_uiState.value.selectedPeriod == period) return
        _uiState.update { it.copy(selectedPeriod = period) }
        // v1.0.9 — restart the live query for the new period (Weekly ranks by
        // weeklyXp, Monthly by monthlyXp, All-Time by lifetime xp).
        loadLeaderboard()
    }

    /** v1.0.11 — clear the prize notice after it has been shown. */
    fun clearRewardNotice() {
        _uiState.update { it.copy(rewardNotice = null) }
    }
}

class LeaderboardViewModelFactory(
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth,
    private val leaderboardRepository: breathy.com.data.repository.LeaderboardRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LeaderboardViewModel::class.java)) {
            return LeaderboardViewModel(userRepository, auth, leaderboardRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

// Slightly darker, subtle Breathy background used ONLY to distinguish the
// current user's leaderboard row (identical layout — no shadow/glow/border).
// A soft sage-tinted step darker than the white row surface.
val CurrentUserRowBackground = androidx.compose.ui.graphics.Color(0xFFEFF3ED)

// ═══════════════════════════════════════════════════════════════════════════════
//  LeaderboardScreen — Global XP leaderboard
//
//  Professional, visual leaderboard experience:
//  ─ Botanical header with the REAL member count (server-side aggregate)
//  ─ YOUR POSITION / YOUR SCORE hero panel (always visible)
//  ─ Drawn top-3 podium with medal artwork and pedestal blocks
//  ─ Strong-hierarchy ranked cards for positions 4+
//  ─ Current user identified by Firebase Auth UID; their row uses the
//    SAME layout as every other row — the ONLY difference is a slightly
//    darker, subtle Breathy background (no shadow/glow/border effects)
//  Data is calculated from REAL accounts only — zero fake entries.
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun LeaderboardScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
    viewModel: LeaderboardViewModel = run {
        val context = LocalContext.current
        val app = context.applicationContext as BreathyApplication
        ViewModelProvider(
            androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!,
            LeaderboardViewModelFactory(
                userRepository = app.appModule.userRepository,
                auth = app.appModule.firebaseAuth,
                leaderboardRepository = app.appModule.leaderboardRepository
            )
        )[LeaderboardViewModel::class.java]
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    // Staggered entrance
    var headerVisible by remember { mutableStateOf(false) }
    var podiumVisible by remember { mutableStateOf(false) }
    var listVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
        headerVisible = true
        delay(180)
        podiumVisible = true
        delay(220)
        listVisible = true
    }

    DisposableEffect(Unit) {
        Timber.d("LeaderboardScreen: composed")
        onDispose { Timber.d("LeaderboardScreen: disposed") }
    }

    // v1.0.11 — show the leaderboard prize payout notice exactly once.
    val toastContext = LocalContext.current
    LaunchedEffect(uiState.rewardNotice) {
        uiState.rewardNotice?.let { notice ->
            android.widget.Toast.makeText(toastContext, notice, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearRewardNotice()
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            viewModel.refresh()
            scope.launch {
                delay(1000)
                isRefreshing = false
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = s("Leaderboard", "المتصدرون"),
                            fontWeight = FontWeight.Bold,
                            color = themeTextPrimary
                        )
                        // REAL member count — calculated from actual accounts.
                        if (uiState.memberCount > 0) {
                            Text(
                                text = s("%,d member%s", "%,d عضو").format(
                                    uiState.memberCount,
                                    if (uiState.memberCount == 1) "" else "s"
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = themeTextSecondary
                                )
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            tint = themeTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = themeTextPrimary
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pullRefresh(pullRefreshState)
        ) {
            when {
                uiState.isLoading && uiState.entries.isEmpty() -> {
                    LoadingState()
                }
                uiState.errorMessage != null && uiState.entries.isEmpty() -> {
                    ErrorState(
                        message = uiState.errorMessage!!,
                        onRetry = { viewModel.loadLeaderboard() }
                    )
                }
                uiState.entries.isEmpty() && !uiState.isLoading -> {
                    // Polished empty state — real users only, never fake entries.
                    breathy.com.ui.components.BreathyEmptyState(
                        title = s("Your community is just getting started", "مجتمعك بدأ للتو"),
                        subtitle = s("Be one of the first to make your mark. " +
                            "Earn XP to appear on the board.", "كن من أوائل من يتركون أثرهم. " +
                            "اجمع نقاط الخبرة لتظهر في القائمة."),
                        icon = "\uD83C\uDF3F"
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = if (uiState.currentUserEntry != null &&
                                uiState.entries.none { it.userId == uiState.currentUserEntry?.userId }
                            ) 108.dp else 20.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ── YOUR POSITION / YOUR SCORE hero panel ──────────
                        item(key = "period_tabs") {
                            PeriodTabsRow(
                                selected = uiState.selectedPeriod,
                                onSelect = { viewModel.selectPeriod(it) }
                            )
                        }

                        // ── v1.0.11 PRIZE TABLE for the selected period ────
                        if (uiState.selectedPeriod != breathy.com.data.models.LeaderboardPeriod.ALL_TIME) {
                            item(key = "prize_table") {
                                LeaderboardPrizeCard(period = uiState.selectedPeriod)
                            }
                        }

                        item(key = "your_position") {
                            AnimatedVisibility(
                                visible = headerVisible,
                                enter = fadeIn(tween(400)) + slideInVertically(
                                    initialOffsetY = { it / 6 },
                                    animationSpec = tween(400)
                                )
                            ) {
                                YourPositionPanel(
                                    entry = uiState.currentUserEntry,
                                    fallbackRank = if (uiState.currentUserRank > 0)
                                        uiState.currentUserRank else uiState.memberCount,
                                    period = uiState.selectedPeriod,
                                    onPeriodSelected = { viewModel.selectPeriod(it) }
                                )
                            }
                        }

                        // ── Top 3 Podium ───────────────────────────────────
                        if (uiState.entries.isNotEmpty()) {
                            item(key = "podium") {
                                AnimatedVisibility(
                                    visible = podiumVisible,
                                    enter = fadeIn(tween(500)) + slideInVertically(
                                        initialOffsetY = { it / 4 },
                                        animationSpec = tween(500)
                                    )
                                ) {
                                    PodiumSection(
                                        entries = uiState.entries.take(3),
                                        onProfileClick = onNavigateToProfile
                                    )
                                }
                            }
                        }

                        // ── Positions 4+ ───────────────────────────────────
                        if (uiState.entries.size > 3) {
                            item(key = "others_header") {
                                Text(
                                    text = when (uiState.selectedPeriod) {
                                        breathy.com.data.models.LeaderboardPeriod.WEEKLY -> s("WEEKLY RANKINGS", "ترتيب أسبوعي")
                                        breathy.com.data.models.LeaderboardPeriod.MONTHLY -> s("MONTHLY RANKINGS", "ترتيب شهري")
                                        breathy.com.data.models.LeaderboardPeriod.ALL_TIME -> s("ALL RANKINGS", "كل الترتيبات")
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = themeTextSecondary,
                                        letterSpacing = 2.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                                )
                            }
                            itemsIndexed(
                                items = uiState.entries.subList(3, uiState.entries.size),
                                key = { _, entry -> entry.rank }
                            ) { _, entry ->
                                AnimatedVisibility(
                                    visible = listVisible,
                                    enter = fadeIn(tween(300))
                                ) {
                                    LeaderboardRow(
                                        entry = entry,
                                        isCurrentUser = entry.userId == uiState.currentUserEntry?.userId,
                                        onProfileClick = onNavigateToProfile
                                    )
                                }
                            }
                        } else if (uiState.entries.size in 1..3) {
                            item(key = "podium_hint") {
                                Text(
                                    text = s("More ranks join as the community grows", "المزيد من المتصدرين مع نمو المجتمع"),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = themeTextSecondary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // ── Current User Fixed Bottom Bar ──────────────────────
                    val currentUserInVisibleList = uiState.currentUserEntry?.let { cur ->
                        uiState.entries.any { it.userId == cur.userId }
                    } == true

                    if (uiState.currentUserEntry != null && !currentUserInVisibleList) {
                        CurrentUserBottomBar(
                            entry = uiState.currentUserEntry!!,
                            visible = listVisible
                        )
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                contentColor = AccentPrimary,
                backgroundColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  YOUR POSITION / YOUR SCORE hero panel
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun YourPositionPanel(
    entry: LeaderboardEntry?,
    fallbackRank: Int,
    period: LeaderboardPeriod,
    onPeriodSelected: (LeaderboardPeriod) -> Unit
) {
    Column {
        // NOTE: Weekly/Monthly chips removed — the leaderboard is a single
        // honest ALL-TIME ranking (spec section 13: no misleading UI).
        // Time-windowed rankings would require per-period XP fields.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = entry?.let {
                        s("Your position ${it.rank}, score ${it.xp} XP", "مركزك ${it.rank}، بنتيجة ${it.xp} خبرة")
                    } ?: s("You are not ranked yet", "لست مصنفاً بعد")
                },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        brush = Brush.linearGradient(
                            listOf(DeepForest, DarkBotanical)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                if (entry != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = s("YOUR POSITION", "ترتيبك"),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = SoftSand,
                                    letterSpacing = 2.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "#${entry.rank}",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PureWhite
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                breathy.com.ui.components.RankBadge(
                                    rankTier = breathy.com.data.models.RankTier.forLevel(entry.level),
                                    level = entry.level
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(PureWhite.copy(alpha = 0.12f))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = s("YOUR SCORE", "نقاطك"),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = SoftSand,
                                        letterSpacing = 1.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "${entry.xp}",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = NaturalYellow
                                    )
                                )
                                Text(
                                    text = s("XP", "نقاط خبرة"),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = VeryLightSage
                                    )
                                )
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = s("YOU'RE NOT RANKED YET", "لست ضمن الترتيب بعد"),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = SoftSand,
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = s("Earn XP by staying smoke-free and completing achievements to join the board.", "اجمع نقاط الخبرة بالبقاء بعيدًا عن التدخين وإكمال الإنجازات للانضمام إلى القائمة."),
                            style = MaterialTheme.typography.bodySmall.copy(color = VeryLightSage)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Podium Section — drawn pedestals + medal artwork for the top 3
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PodiumSection(
    entries: List<LeaderboardEntry>,
    onProfileClick: (String) -> Unit
) {
    if (entries.isEmpty()) return

    val first = entries.getOrNull(0)
    val second = entries.getOrNull(1)
    val third = entries.getOrNull(2)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = VeryLightSage),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(VeryLightSage, SoftSage.copy(alpha = 0.45f))
                    )
                )
                .padding(horizontal = 10.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                // Silver — 2nd place (left)
                PodiumColumn(
                    entry = second,
                    place = 2,
                    avatarSize = 54.dp,
                    pedestalHeight = 64.dp,
                    onProfileClick = onProfileClick,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Gold — 1st place (center)
                PodiumColumn(
                    entry = first,
                    place = 1,
                    avatarSize = 72.dp,
                    pedestalHeight = 92.dp,
                    onProfileClick = onProfileClick,
                    modifier = Modifier.weight(1.15f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Bronze — 3rd place (right)
                PodiumColumn(
                    entry = third,
                    place = 3,
                    avatarSize = 50.dp,
                    pedestalHeight = 48.dp,
                    onProfileClick = onProfileClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PodiumColumn(
    entry: LeaderboardEntry?,
    place: Int,
    avatarSize: Dp,
    pedestalHeight: Dp,
    onProfileClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val medalColor = when (place) {
        1 -> AchievementGold
        2 -> AchievementSilver
        else -> AchievementBronze
    }
    val medalEmoji = when (place) {
        1 -> "\uD83E\uDD47"
        2 -> "\uD83E\uDD48"
        else -> "\uD83E\uDD49"
    }
    val pedestalBrush = when (place) {
        1 -> Brush.verticalGradient(listOf(Color(0xFFE9CB6F), Color(0xFFC9A44A)))
        2 -> Brush.verticalGradient(listOf(Color(0xFFC7CFCA), Color(0xFF9FAAA4)))
        else -> Brush.verticalGradient(listOf(Color(0xFFD3A379), Color(0xFFA97747)))
    }

    if (entry == null) {
        // Graceful half-populated podium (1–2 real members only)
        Box(modifier = modifier.height(190.dp))
        return
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Medal + crown for the champion
        Box(contentAlignment = Alignment.Center) {
            if (place == 1) {
                Text(text = "👑", fontSize = 18.sp, modifier = Modifier.offset(y = (-2).dp))
            }
            Text(
                text = medalEmoji,
                fontSize = if (place == 1) 24.sp else 20.sp,
                modifier = Modifier.padding(top = if (place == 1) 14.dp else 0.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Avatar with the user's REAL equipped frame
        breathy.com.ui.components.BreathyAvatar(
            photoURL = entry.photoURL,
            frame = entry.avatarFrame,
            rankTier = breathy.com.data.models.RankTier.forLevel(entry.level),
            size = avatarSize,
            contentDescription = "${entry.nickname}'s avatar",
            animated = false,
            profilePictureId = entry.profilePicture,
            isPremiumUser = entry.isPremium
        )

        Spacer(modifier = Modifier.height(6.dp))

        // v1.0.9 — premium subscribers' names glow neon across the app.
        breathy.com.ui.components.PremiumGlowText(
            text = entry.nickname,
            enabled = entry.isPremium,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = DarkBotanical,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = s("%d XP", "%d نقاط خبرة").format(entry.xp),
            style = MaterialTheme.typography.labelSmall.copy(
                color = GoldDeep,
                fontWeight = FontWeight.ExtraBold
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Drawn pedestal block
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pedestalHeight)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(pedestalBrush)
                .semantics {
                    contentDescription = "Rank ${entry.rank}: ${entry.nickname}, ${entry.xp} XP"
                    role = androidx.compose.ui.semantics.Role.Button
                }
                .clickable { onProfileClick(entry.userId) },
            contentAlignment = Alignment.Center
        ) {
            // Decorative shine line on the podium top edge
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter)
            ) {
                drawRect(
                    color = PureWhite.copy(alpha = 0.55f),
                    size = size
                )
            }
            Text(
                text = "$place",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = DarkBotanical.copy(alpha = 0.65f)
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Leaderboard Row — Individual entry in the scrollable list
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LeaderboardRow(
    entry: LeaderboardEntry,
    isCurrentUser: Boolean,
    onProfileClick: (String) -> Unit
) {
    // CURRENT-USER HIGHLIGHT (spec section 10):
    // The row layout, size, spacing, avatar size, rank layout and borders are
    // IDENTICAL to every other row. The ONLY visual difference is a slightly
    // darker, subtle Breathy background — no shadow, no elevation, no glow,
    // no thick border, no floating-card effect.
    val cardColor = if (isCurrentUser) {
        CurrentUserRowBackground   // slightly darker Breathy background
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Rank ${entry.rank}: ${entry.nickname}, ${entry.xp} XP"
                role = androidx.compose.ui.semantics.Role.Button
            },
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SoftSage.copy(alpha = 0.5f)),
        onClick = { onProfileClick(entry.userId) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rank number in a soft circle — identical for every row
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(VeryLightSage),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${entry.rank}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeTextSecondary
                    )
                )
            }

            // Avatar with persisted frame
            breathy.com.ui.components.BreathyAvatar(
                photoURL = entry.photoURL,
                frame = entry.avatarFrame,
                rankTier = breathy.com.data.models.RankTier.forLevel(entry.level),
                size = 44.dp,
                contentDescription = "${entry.nickname}'s avatar",
                animated = false,
                profilePictureId = entry.profilePicture,
                isPremiumUser = entry.isPremium
            )

            // Nickname and days
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // v1.0.9 — premium subscribers' names glow neon.
                    breathy.com.ui.components.PremiumGlowText(
                        text = entry.nickname,
                        enabled = entry.isPremium,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = themeTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCurrentUser) {
                        // Subtle, integrated YOU indicator (spec: keep it small
                        // and inside the row; identity comes from the darker bg).
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentPrimary.copy(alpha = 0.14f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = s("YOU", "أنت"),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = DeepForest,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 8.sp,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                    }
                    if (entry.isPremium) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "✦",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = DeepForest,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                Text(
                    text = s("%d days smoke-free", "%d يوم بدون تدخين").format(entry.daysSmokeFree),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = themeTextSecondary,
                        fontSize = 11.sp
                    )
                )
            }

            // XP pill — identical styling for every row
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(VeryLightSage)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = s("%d XP", "%d نقاط خبرة").format(entry.xp),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentSecondary
                    )
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Current User Bottom Bar — YOUR POSITION + YOUR SCORE, fixed at bottom
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CurrentUserBottomBar(
    entry: LeaderboardEntry,
    visible: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(300)
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                breathy.com.ui.components.BreathyAvatar(
                    photoURL = entry.photoURL,
                    frame = entry.avatarFrame,
                    rankTier = breathy.com.data.models.RankTier.forLevel(entry.level),
                    size = 46.dp,
                    contentDescription = "Your avatar",
                    animated = false,
                    profilePictureId = entry.profilePicture,
                    isPremiumUser = entry.isPremium
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s("YOUR POSITION", "ترتيبك"),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = themeTextSecondary,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "#${entry.rank}",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DeepForest
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = s("You", "أنت"),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = AccentPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(VeryLightSage)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = s("YOUR SCORE", "نقاطك"),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = themeTextSecondary,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        )
                        Text(
                            text = s("%d XP", "%d نقاط خبرة").format(entry.xp),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeepForest
                            )
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Loading / Error States
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = AccentPrimary,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = s("Loading leaderboard...", "جارٍ تحميل قائمة المتصدرين..."),
                style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary)
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "\u26A0\uFE0F",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = s("Something went wrong", "حدث خطأ ما"),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = themeTextPrimary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                onClick = onRetry,
                colors = CardDefaults.cardColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = s("Try Again", "حاول مجددًا"),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.background,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  v1.0.11 — Prize table card for the selected period.
//  Weekly : ranks 1–3 → 1000 Gold · 4–10 → 500 · 11–50 → 100
//  Monthly: ranks 1–3 → 5000 Gold · 4–10 → 3000 · 11–50 → 500
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LeaderboardPrizeCard(
    period: breathy.com.data.models.LeaderboardPeriod,
    modifier: Modifier = Modifier
) {
    val isWeekly = period == breathy.com.data.models.LeaderboardPeriod.WEEKLY
    val top = if (isWeekly) 1000 else 5000
    val mid = if (isWeekly) 500 else 3000
    val rest = if (isWeekly) 100 else 500
    val title = if (isWeekly)
        s("Weekly prizes — resets every week", "جوائز أسبوعية — تُصفّر كل أسبوع")
    else
        s("Monthly prizes — resets every month", "جوائز شهرية — تُصفّر كل شهر")

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = AccentPrimary.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = themeTextPrimary
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            PrizeRow(emoji = "🥇", text = s("Top 3", "المراكز الثلاثة الأولى"), gold = top)
            PrizeRow(emoji = "🏅", text = s("Places 4 – 10", "المراكز 4 – 10"), gold = mid)
            PrizeRow(emoji = "🎁", text = s("Places 11 – 50", "المراكز 11 – 50"), gold = rest)
        }
    }
}

@Composable
private fun PrizeRow(emoji: String, text: String, gold: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 13.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(color = themeTextSecondary),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = s("+%,d Gold", "+%,d ذهب").format(gold),
            style = MaterialTheme.typography.labelMedium.copy(
                color = GoldDeep,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  v1.0.9 — Period tabs: WEEKLY · MONTHLY · ALL TIME
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PeriodTabsRow(
    selected: breathy.com.data.models.LeaderboardPeriod,
    onSelect: (breathy.com.data.models.LeaderboardPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        breathy.com.data.models.LeaderboardPeriod.entries.forEach { period ->
            val isSelected = period == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) AccentPrimary else Color.Transparent
                    )
                    .clickable { onSelect(period) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (period) {
                        breathy.com.data.models.LeaderboardPeriod.WEEKLY -> s("Weekly", "أسبوعي")
                        breathy.com.data.models.LeaderboardPeriod.MONTHLY -> s("Monthly", "شهري")
                        breathy.com.data.models.LeaderboardPeriod.ALL_TIME -> s("All-Time", "كل الأوقات")
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else themeTextSecondary
                )
            }
        }
    }
}
