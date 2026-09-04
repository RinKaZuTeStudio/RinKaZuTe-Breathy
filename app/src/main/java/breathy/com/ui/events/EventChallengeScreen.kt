package breathy.com.ui.events

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import breathy.com.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import breathy.com.ui.theme.GoldDeep
import breathy.com.ui.theme.NaturalYellow
import breathy.com.ui.theme.SoftSand
import breathy.com.ui.theme.DeepForest
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import breathy.com.ui.components.NetworkImage
import breathy.com.BreathyApplication
import breathy.com.utils.s
import breathy.com.data.models.Event
import breathy.com.data.models.EventParticipant
import breathy.com.data.repository.EventRepository
import breathy.com.ui.theme.SoftSage
import breathy.com.ui.theme.DarkBotanical
import breathy.com.ui.theme.PureWhite
import breathy.com.ui.theme.VeryLightSage
import breathy.com.ui.theme.AccentPrimary
import breathy.com.ui.theme.AccentPurple
import breathy.com.ui.theme.AccentSecondary
import breathy.com.ui.theme.themeBgPrimary
import breathy.com.ui.theme.themeBgSurface
import breathy.com.ui.theme.themeBgSurfaceVariant
import breathy.com.ui.theme.themeTextDisabled
import breathy.com.ui.theme.themeTextPrimary
import breathy.com.ui.theme.themeTextSecondary
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.compose.ui.text.font.FontFamily

// ═══════════════════════════════════════════════════════════════════════════════
//  UI State
// ═══════════════════════════════════════════════════════════════════════════════

data class EventChallengeUiState(
    val isLoading: Boolean = true,
    val event: Event? = null,
    val participant: EventParticipant? = null,
    val isJoined: Boolean = false,
    val leaderboard: List<EventRepository.EventLeaderboardEntry> = emptyList(),
    val currentDayNumber: Int = 1,
    val canCheckinToday: Boolean = false,
    val errorMessage: String? = null,
    val countdownSeconds: Long = 0L,
    val isPushupChallenge: Boolean = false,
    /**
     * True when the live event document is not available yet and the screen is
     * rendering the canonical Coming Soon configuration — full structure
     * (artwork / rewards / rules / entry info) with joining disabled.
     */
    val isComingSoonPreview: Boolean = false,
    /** Real-time Gold balance — powers the 500-Gold entry gate. */
    val goldBalance: Int = 0
)

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

class EventChallengeViewModel(
    private val eventRepository: EventRepository,
    private val auth: FirebaseAuth,
    private val eventId: String,
    private val goldRepository: breathy.com.data.repository.GoldRepository? = null,
    private val premiumRepository: breathy.com.data.repository.PremiumRepository? = null
) : ViewModel() {

    companion object {
        private const val TAG = "EventChallengeViewModel"
    }

    /** Exposed so the UI can highlight the current user's row / position. */
    val currentUserId: String?
        get() = auth.currentUser?.uid

    private val _uiState = MutableStateFlow(EventChallengeUiState())
    val uiState: StateFlow<EventChallengeUiState> = _uiState.asStateFlow()

    init {
        loadEventData()
        observeGoldBalance()
    }

    /** Stream the real Gold balance so the entry gate is always accurate. */
    private fun observeGoldBalance() {
        goldRepository?.let { repo ->
            viewModelScope.launch {
                repo.balanceFlow().collect { balance ->
                    _uiState.update { it.copy(goldBalance = balance) }
                }
            }
        }
    }

    fun loadEventData() {
        val uid = currentUserId ?: run {
            _uiState.update { it.copy(isLoading = false, errorMessage = s("Not authenticated", "غير مسجل الدخول")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                // Load event details — robust fallback chain (spec section 21):
                // 1. The requested document, 2. any live pushup_challenge event,
                // 3. the canonical Coming Soon configuration (full structure,
                //    joining disabled — never fake live data).
                val requestedResult = eventRepository.getEvent(eventId)
                val resolved = requestedResult.recoverCatching {
                    eventRepository.findFeaturedPushupEvent().getOrThrow()
                }.recoverCatching {
                    eventRepository.canonicalFeaturedEvent()
                }
                val event = resolved.getOrNull() ?: run {
                    _uiState.update { it.copy(isLoading = false, errorMessage = s("Event not found", "الفعالية غير موجودة")) }
                    return@launch
                }
                // Coming Soon preview only when NO live document was resolved AND
                // the event isn't currently running.
                val comingSoonPreview = requestedResult.isFailure && !event.isCurrentlyActive()

                // Load participant info
                val participant = if (comingSoonPreview) null
                    else eventRepository.getParticipant(eventId, uid).getOrNull()
                val isJoined = participant != null

                // Calculate current day number and check-in availability
                val startMillis = event.startDate.toDate().time
                val nowMillis = System.currentTimeMillis()
                val dayNumber = ((nowMillis - startMillis) / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(1)
                    .coerceAtMost(event.totalDays())

                val canCheckin = isJoined &&
                    event.isCurrentlyActive() &&
                    participant?.completed == false

                // Calculate countdown to event end
                val endMillis = event.endDate.toDate().time
                val countdownSeconds = ((endMillis - nowMillis) / 1000).coerceAtLeast(0)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        event = event,
                        participant = participant,
                        isJoined = isJoined,
                        currentDayNumber = dayNumber,
                        canCheckinToday = canCheckin,
                        countdownSeconds = countdownSeconds,
                        errorMessage = null,
                        isPushupChallenge = event.isPushupChallenge(),
                        isComingSoonPreview = comingSoonPreview
                    )
                }
            } catch (e: CancellationException) {
                // Ignore
            } catch (e: Exception) {
                Timber.e(e, "Failed to load event data")
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = s("Failed to load event: ${e.message}", "تعذر تحميل الفعالية: ${e.message}"))
                }
            }
        }
    }

    fun loadLeaderboard() {
        viewModelScope.launch {
            val event = _uiState.value.event
            val leaderboardResult = if (event != null && event.isPushupChallenge()) {
                eventRepository.getPushupLeaderboard(eventId)
            } else {
                eventRepository.getEventLeaderboard(eventId)
            }
            leaderboardResult.fold(
                onSuccess = { entries ->
                    _uiState.update { it.copy(leaderboard = entries) }
                },
                onFailure = { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to load event leaderboard")
                    }
                }
            )
        }
    }

    fun joinEvent() {
        val uid = currentUserId ?: return
        viewModelScope.launch {
            // v1.0.9 premium perk: verified subscribers join any event FREE.
            val isPremium = premiumRepository?.isPremium() ?: false
            eventRepository.joinEvent(eventId, isPremium = isPremium).fold(
                onSuccess = { participant ->
                    _uiState.update {
                        it.copy(
                            isJoined = true,
                            participant = participant,
                            canCheckinToday = _uiState.value.event?.isCurrentlyActive() == true
                        )
                    }
                },
                onFailure = { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to join event")
                        val message = when (e) {
                            is breathy.com.data.repository.InsufficientGoldException ->
                                s(
                                    "Not enough Gold — entry costs 500 Gold, you have ${e.available}.",
                                    "رصيد الذهب غير كافٍ — رسوم الدخول 500 ذهب، ولديك ${e.available}."
                                )
                            is IllegalStateException -> e.message ?: s("Failed to join event", "تعذر الانضمام إلى الفعالية")
                            else -> e.localizedMessage ?: s("Failed to join event", "تعذر الانضمام إلى الفعالية")
                        }
                        _uiState.update { it.copy(errorMessage = message) }
                    }
                }
            )
        }
    }

    fun refresh() {
        loadEventData()
    }
}

class EventChallengeViewModelFactory(
    private val eventRepository: EventRepository,
    private val auth: FirebaseAuth,
    private val eventId: String,
    private val goldRepository: breathy.com.data.repository.GoldRepository? = null,
    private val premiumRepository: breathy.com.data.repository.PremiumRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventChallengeViewModel::class.java)) {
            return EventChallengeViewModel(eventRepository, auth, eventId, goldRepository, premiumRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  EventChallengeScreen — Event detail page
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventChallengeScreen(
    eventId: String,
    onNavigateBack: () -> Unit = {},
    onNavigateToCheckin: (String) -> Unit = {},
    onNavigateToPushupCounter: (String) -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
    viewModel: EventChallengeViewModel = run {
        val context = LocalContext.current
        val app = context.applicationContext as BreathyApplication
        ViewModelProvider(
            androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!,
            EventChallengeViewModelFactory(
                eventRepository = app.appModule.eventRepository,
                auth = app.appModule.firebaseAuth,
                eventId = eventId,
                goldRepository = app.appModule.goldRepository,
                premiumRepository = app.appModule.premiumRepository
            )
        )[EventChallengeViewModel::class.java]
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(s("Details", "التفاصيل"), s("Leaderboard", "لوحة الصدارة"))

    // Countdown timer
    var countdownSeconds by remember { mutableStateOf(uiState.countdownSeconds) }
    LaunchedEffect(uiState.countdownSeconds) {
        countdownSeconds = uiState.countdownSeconds
    }
    LaunchedEffect(countdownSeconds) {
        if (countdownSeconds > 0) {
            delay(1000)
            countdownSeconds = (countdownSeconds - 1).coerceAtLeast(0)
        }
    }

    // Load leaderboard when leaderboard tab selected
    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex == 1) {
            viewModel.loadLeaderboard()
        }
    }

    DisposableEffect(Unit) {
        Timber.d("EventChallengeScreen: composed")
        onDispose { Timber.d("EventChallengeScreen: disposed") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.event?.title ?: s("Event", "الفعالية"),
                        fontWeight = FontWeight.Bold,
                        color = themeTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                    containerColor = themeBgPrimary,
                    titleContentColor = themeTextPrimary
                )
            )
        },
        containerColor = themeBgPrimary
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = AccentPrimary,
                        strokeWidth = 3.dp
                    )
                }
            }
            uiState.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.errorMessage!!,
                            style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            onClick = { viewModel.refresh() },
                            colors = CardDefaults.cardColors(containerColor = AccentPrimary),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(
                                text = s("Retry", "إعادة المحاولة"),
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                                color = themeBgPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            uiState.event != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // ── Tab Row ────────────────────────────────────────────
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = themeBgPrimary,
                        contentColor = AccentPrimary,
                        indicator = { tabPositions ->
                            TabRowDefaults.Indicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                height = 2.dp,
                                color = AccentPrimary
                            )
                        }
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = {
                                    Text(
                                        text = title,
                                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedTabIndex == index) AccentPrimary else themeTextSecondary
                                    )
                                }
                            )
                        }
                    }

                    // ── Tab Content ────────────────────────────────────────
                    when (selectedTabIndex) {
                        0 -> DetailsTab(
                            uiState = uiState,
                            countdownSeconds = countdownSeconds,
                            onCheckin = {
                                val event = uiState.event
                                if (event != null && event.isPushupChallenge()) {
                                    onNavigateToPushupCounter(eventId)
                                } else {
                                    onNavigateToCheckin(eventId)
                                }
                            },
                            onJoin = { viewModel.joinEvent() }
                        )
                        1 -> LeaderboardTab(
                            leaderboard = uiState.leaderboard,
                            currentUserId = viewModel.currentUserId,
                            isLoading = uiState.isLoading,
                            onProfileClick = onNavigateToProfile
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Details Tab
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DetailsTab(
    uiState: EventChallengeUiState,
    countdownSeconds: Long,
    onCheckin: () -> Unit,
    onJoin: () -> Unit
) {
    val event = uiState.event ?: return
    val participant = uiState.participant

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Canonical Event Hero (same artwork as Home/Events — spec §21) ──
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.event_pushup_hero),
                    contentDescription = "Push-Up Challenge event artwork",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // ── Coming Soon banner (full structure, no live event yet) ──────
        if (uiState.isComingSoonPreview) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = VeryLightSage
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = s("COMING SOON", "قريبًا"),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.5.sp,
                                    color = DeepForest
                                )
                            )
                            Text(
                                text = s(
                                    "The next challenge hasn't opened yet. Review the rewards, rules and entry details below so you're ready when it goes live.",
                                    "لم يبدأ التحدي القادم بعد. اطلع على المكافآت والقواعد وتفاصيل الدخول أدناه لتكون جاهزًا عند انطلاقه."
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = themeTextSecondary
                                )
                            )
                        }
                    }
                }
            }
        }

        // ── Event Info Card ─────────────────────────────────────────────
        item {
            EventInfoCard(event = event)
        }

        // ── Countdown to start if event hasn't started yet ──────────────
        val startMillis = event.startDate.toDate().time
        val nowMillis = System.currentTimeMillis()
        if (nowMillis < startMillis) {
            item {
                EventStartCountdownCard(startMillis = startMillis)
            }
        }

        // ── Countdown to End ────────────────────────────────────────────
        if (countdownSeconds > 0 && event.isCurrentlyActive()) {
            item {
                CountdownCard(countdownSeconds = countdownSeconds)
            }
        }

        // ── Progress Stats (if joined) ──────────────────────────────────
        if (participant != null) {
            item {
                ProgressStatsCard(
                    participant = participant,
                    totalDays = event.totalDays(),
                    dailyRequired = event.dailyRequired,
                    currentDayNumber = uiState.currentDayNumber
                )
            }
        }

        // ── Prize Breakdown ─────────────────────────────────────────────
        if (event.prizes.isNotEmpty()) {
            item {
                PrizeBreakdownCard(prizes = event.prizes)
            }
        }

        // ── Rewards info (spec section 25) ─────────────────────────────
        item {
            EventRewardsInfoCard()
        }

        // ── Rules & Terms (spec section 26) ────────────────────────────
        item {
            EventRulesCard(event = event)
        }

        // ── Check-in Button (if joined and active) ─────────────────────
        if (uiState.isJoined && event.isCurrentlyActive() && participant?.completed != true) {
            item {
                if (uiState.isPushupChallenge) {
                    PushupCheckinButton(
                        onClick = onCheckin,
                        targetPushups = event.targetPushups,
                        currentPushups = participant?.totalPushups ?: 0
                    )
                } else {
                    CheckinButton(
                        onClick = onCheckin,
                        currentDayNumber = uiState.currentDayNumber
                    )
                }
            }
        }

        // ── Join Button (if not joined and event is active or upcoming) ──
        if (!uiState.isJoined && participant?.completed != true) {
            item {
                val eventOpen = event.isCurrentlyActive() ||
                    (event.active && nowMillis < startMillis)
                JoinEventButton(
                    goldBalance = uiState.goldBalance,
                    entryFee = 500,
                    eventOpen = eventOpen,
                    onClick = onJoin
                )
            }
        }

        // ── Completion Badge ────────────────────────────────────────────
        if (participant?.completed == true) {
            item {
                CompletionBadgeCard()
            }
        }

        // Bottom spacing
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Leaderboard Tab
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LeaderboardTab(
    leaderboard: List<EventRepository.EventLeaderboardEntry>,
    currentUserId: String?,
    isLoading: Boolean = false,
    onProfileClick: (String) -> Unit
) {
    if (leaderboard.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = AccentPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = s("Loading leaderboard...", "جارٍ تحميل لوحة الصدارة..."),
                        style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary)
                    )
                } else {
                    // Polished empty state — only real participants appear (spec section 30)
                    Text(text = "🏅", fontSize = 44.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = s("NO PARTICIPANTS YET", "لا يوجد مشاركون بعد"),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp,
                            color = themeTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = s(
                            "The leaderboard fills in as participants join and their check-ins are approved. Join the challenge and claim the top spot.",
                            "تمتلئ لوحة الصدارة كلما انضم المشاركون وتم قبول تسجيلات حضورهم. انضم إلى التحدي واحتل المركز الأول."
                        ),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = themeTextSecondary
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    } else {
        val currentUserEntry = leaderboard.firstOrNull { it.participant.userId == currentUserId }
        val currentUserInList = currentUserEntry != null && currentUserEntry.rank <= leaderboard.size

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 12.dp,
                bottom = if (currentUserEntry != null && currentUserEntry.rank > 3) 96.dp else 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── YOUR POSITION / YOUR SCORE hero panel ───────────────────
            currentUserEntry?.let { me ->
                item(key = "event_your_position") {
                    EventYourPositionPanel(
                        rank = me.rank,
                        score = if (me.participant.totalPushups > 0)
                            me.participant.totalPushups else me.participant.totalApprovedDays,
                        scoreUnit = if (me.participant.totalPushups > 0) s("push-ups", "تمارين ضغط") else s("days", "أيام")
                    )
                }
            }

            // ── Top 3 podium ────────────────────────────────────────────
            if (leaderboard.size >= 3) {
                item(key = "event_podium") {
                    EventPodium(
                        entries = leaderboard.take(3),
                        onProfileClick = onProfileClick
                    )
                }
            }

            // ── Ranked participants 4+ (top 3 keep their row too when < 3 entries) ─
            val rankedRest = if (leaderboard.size >= 3) leaderboard.drop(3) else leaderboard
            items(
                items = rankedRest,
                key = { it.participant.userId }
            ) { entry ->
                EventLeaderboardRow(
                    entry = entry,
                    isCurrentUser = entry.participant.userId == currentUserId,
                    onProfileClick = onProfileClick
                )
            }
        }
    }
}

/** YOUR POSITION #X / YOUR SCORE X — event-scoped (spec section 30). */
@Composable
private fun EventYourPositionPanel(
    rank: Int,
    score: Int,
    scoreUnit: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(DeepForest, DarkBotanical)))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = s("YOUR POSITION", "مركزك"),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = SoftSand,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = PureWhite
                    )
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(PureWhite.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = s("YOUR SCORE", "نتيجتك"),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SoftSand,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    )
                    Text(
                        text = "$score $scoreUnit",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = NaturalYellow
                        )
                    )
                }
            }
        }
    }
}

/** Drawn podium for the event's top 3 (spec section 30). */
@Composable
private fun EventPodium(
    entries: List<EventRepository.EventLeaderboardEntry>,
    onProfileClick: (String) -> Unit
) {
    if (entries.size < 3) return
    val first = entries[0]
    val second = entries[1]
    val third = entries[2]

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = VeryLightSage),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            EventPodiumColumn(second, 2, 50.dp, 56.dp, onProfileClick, Modifier.weight(1f))
            Spacer(modifier = Modifier.width(6.dp))
            EventPodiumColumn(first, 1, 64.dp, 84.dp, onProfileClick, Modifier.weight(1.15f))
            Spacer(modifier = Modifier.width(6.dp))
            EventPodiumColumn(third, 3, 46.dp, 44.dp, onProfileClick, Modifier.weight(1f))
        }
    }
}

@Composable
private fun EventPodiumColumn(
    entry: EventRepository.EventLeaderboardEntry,
    place: Int,
    avatarSize: androidx.compose.ui.unit.Dp,
    pedestalHeight: androidx.compose.ui.unit.Dp,
    onProfileClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = entry.publicProfile
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

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = medalEmoji, fontSize = 20.sp)
        Spacer(modifier = Modifier.height(4.dp))
        breathy.com.ui.components.BreathyAvatar(
            photoURL = profile?.photoURL,
            frame = breathy.com.data.models.AvatarFrame.fromId(profile?.avatarFrame),
            rankTier = profile?.let {
                breathy.com.data.models.RankTier.forLevel(
                    breathy.com.data.models.User.computeLevel(it.xp)
                )
            },
            size = avatarSize,
            contentDescription = "${profile?.nickname ?: "Participant"}'s avatar",
            profilePictureId = profile?.profilePicture,
            isPremiumUser = profile?.premium == true
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = profile?.nickname ?: s("Unknown", "غير معروف"),
            style = MaterialTheme.typography.labelSmall.copy(
                color = DarkBotanical,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (entry.participant.totalPushups > 0)
                s("${entry.participant.totalPushups} push-ups", "${entry.participant.totalPushups} تمارين ضغط")
            else s("${entry.participant.totalApprovedDays} days", "${entry.participant.totalApprovedDays} أيام"),
            style = MaterialTheme.typography.labelSmall.copy(
                color = GoldDeep,
                fontWeight = FontWeight.ExtraBold
            ),
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pedestalHeight)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(pedestalBrush)
                .clickable { onProfileClick(entry.participant.userId) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$place",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = DarkBotanical.copy(alpha = 0.65f)
                )
            )
        }
    }
}

@Composable
private fun EventLeaderboardRow(
    entry: EventRepository.EventLeaderboardEntry,
    isCurrentUser: Boolean,
    onProfileClick: (String) -> Unit
) {
    val profile = entry.publicProfile
    val medalEmoji = when (entry.rank) {
        1 -> "\uD83E\uDD47"
        2 -> "\uD83E\uDD48"
        3 -> "\uD83E\uDD49"
        else -> ""
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Rank ${entry.rank}: ${profile?.nickname ?: "Unknown"}, ${entry.participant.totalApprovedDays} days approved"
                role = Role.Button
            },
        // CURRENT-USER HIGHLIGHT (spec sections 10/12): every row shares the
        // same layout, size, elevation (0) and border. The current user —
        // resolved by Firebase Auth UID — differs ONLY by the same subtle
        // darker Breathy background used on the main leaderboard.
        // No shadow, no floating effect, no glow.
        colors = CardDefaults.cardColors(
            containerColor = when {
                isCurrentUser -> breathy.com.ui.leaderboard.CurrentUserRowBackground
                entry.rank <= 3 -> AccentPrimary.copy(alpha = 0.06f)
                else -> themeBgSurface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            when {
                entry.rank <= 3 -> AccentPrimary.copy(alpha = 0.2f)
                else -> SoftSage.copy(alpha = 0.5f)
            }
        ),
        onClick = { onProfileClick(entry.participant.userId) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rank
            Text(
                text = if (medalEmoji.isNotEmpty()) medalEmoji else "${entry.rank}",
                style = if (medalEmoji.isEmpty()) {
                    TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeTextSecondary
                    )
                } else {
                    TextStyle(fontSize = 18.sp)
                },
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.Center
            )

            // Avatar with the participant's REAL equipped frame (spec §30/§40)
            breathy.com.ui.components.BreathyAvatar(
                photoURL = profile?.photoURL,
                frame = breathy.com.data.models.AvatarFrame.fromId(profile?.avatarFrame),
                rankTier = profile?.let {
                    breathy.com.data.models.RankTier.forLevel(
                        breathy.com.data.models.User.computeLevel(it.xp)
                    )
                },
                size = 40.dp,
                contentDescription = "${profile?.nickname ?: "Participant"}'s avatar",
                profilePictureId = profile?.profilePicture,
                isPremiumUser = profile?.premium == true,
                animated = false
            )

            // Name and streak
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile?.nickname ?: s("Unknown", "غير معروف"),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = themeTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (entry.participant.totalPushups > 0) {
                        s("\uD83D\uDCAA ${entry.participant.totalPushups} pushups", "\uD83D\uDCAA ${entry.participant.totalPushups} تمارين ضغط")
                    } else {
                        s("\uD83D\uDD25 ${entry.participant.currentStreak} day streak", "\uD83D\uDD25 سلسلة ${entry.participant.currentStreak} يوم")
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = AccentPrimary,
                        fontSize = 11.sp
                    )
                )
            }

            // Score
            Text(
                text = if (entry.participant.totalPushups > 0) {
                    "${entry.participant.totalPushups}"
                } else {
                    "${entry.participant.totalApprovedDays}d"
                },
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentSecondary
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Sub-Components
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EventStartCountdownCard(startMillis: Long) {
    val nowMillis = System.currentTimeMillis()
    var startCountdownSeconds by remember { mutableStateOf(((startMillis - nowMillis) / 1000).coerceAtLeast(0)) }
    LaunchedEffect(startCountdownSeconds) {
        if (startCountdownSeconds > 0) {
            delay(1000)
            startCountdownSeconds = (startCountdownSeconds - 1).coerceAtLeast(0)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AccentSecondary.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AccentSecondary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = s("Event Starts In", "تبدأ الفعالية بعد"),
                style = MaterialTheme.typography.labelMedium.copy(
                    color = themeTextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            val days = startCountdownSeconds / 86400
            val hours = (startCountdownSeconds % 86400) / 3600
            val minutes = (startCountdownSeconds % 3600) / 60
            val seconds = startCountdownSeconds % 60
            Text(
                text = s("${days}d ${hours}h ${minutes}m ${seconds}s", "${days}ي ${hours}س ${minutes}د ${seconds}ث"),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentSecondary
                )
            )
        }
    }
}

@Composable
private fun EventInfoCard(event: Event) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = themeBgSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = s("About This Challenge", "عن هذا التحدي"),
                style = MaterialTheme.typography.titleMedium.copy(
                    color = themeTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = event.description,
                style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Date range
            val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Timer,
                    contentDescription = null,
                    tint = themeTextDisabled,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${dateFormatter.format(event.startDate.toDate())} - ${dateFormatter.format(event.endDate.toDate())}",
                    style = MaterialTheme.typography.labelMedium.copy(color = themeTextSecondary)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Daily requirement
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = null,
                    tint = themeTextDisabled,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = s("Daily requirement: ${event.dailyRequired}x per day", "المطلوب يوميًا: ${event.dailyRequired}x في اليوم"),
                    style = MaterialTheme.typography.labelMedium.copy(color = themeTextSecondary)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Total days
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = themeTextDisabled,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = s("Duration: ${event.totalDays()} days", "المدة: ${event.totalDays()} يومًا"),
                    style = MaterialTheme.typography.labelMedium.copy(color = themeTextSecondary)
                )
            }
        }
    }
}

@Composable
private fun CountdownCard(countdownSeconds: Long) {
    val days = countdownSeconds / 86400
    val hours = (countdownSeconds % 86400) / 3600
    val minutes = (countdownSeconds % 3600) / 60
    val seconds = countdownSeconds % 60

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AccentPrimary.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = s("Time Remaining", "الوقت المتبقي"),
                style = MaterialTheme.typography.labelMedium.copy(
                    color = themeTextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CountdownUnit(value = days, label = s("Days", "أيام"))
                Text(
                    text = ":",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPrimary
                    )
                )
                CountdownUnit(value = hours, label = s("Hrs", "ساعات"))
                Text(
                    text = ":",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPrimary
                    )
                )
                CountdownUnit(value = minutes, label = s("Min", "دقائق"))
                Text(
                    text = ":",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPrimary
                    )
                )
                CountdownUnit(value = seconds, label = s("Sec", "ثوانٍ"))
            }
        }
    }
}

@Composable
private fun CountdownUnit(value: Long, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = String.format(Locale.US, "%02d", value),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AccentPrimary
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = themeTextSecondary,
                fontSize = 10.sp
            )
        )
    }
}

@Composable
private fun ProgressStatsCard(
    participant: EventParticipant,
    totalDays: Int,
    dailyRequired: Int,
    currentDayNumber: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = themeBgSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = s("Your Progress", "تقدمك"),
                style = MaterialTheme.typography.titleMedium.copy(
                    color = themeTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Current streak
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "\uD83D\uDD25",
                        fontSize = 20.sp
                    )
                    Text(
                        text = "${participant.currentStreak}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentPrimary
                        )
                    )
                    Text(
                        text = s("Day Streak", "سلسلة أيام"),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = themeTextSecondary,
                            fontSize = 11.sp
                        )
                    )
                }

                // Total approved
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "\u2705",
                        fontSize = 20.sp
                    )
                    Text(
                        text = "${participant.totalApprovedDays}",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentSecondary
                        )
                    )
                    Text(
                        text = s("Days Approved", "أيام مقبولة"),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = themeTextSecondary,
                            fontSize = 11.sp
                        )
                    )
                }

                // Current day
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "\uD83D\uDCC5",
                        fontSize = 20.sp
                    )
                    Text(
                        text = "$currentDayNumber",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentPurple
                        )
                    )
                    Text(
                        text = s("of $totalDays Days", "من أصل $totalDays يومًا"),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = themeTextSecondary,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            val progress = if (totalDays > 0) {
                (participant.totalApprovedDays.toFloat() / totalDays).coerceIn(0f, 1f)
            } else 0f

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = AccentPrimary,
                trackColor = themeBgSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = s("${(progress * 100).toInt()}% complete", "اكتمل ${(progress * 100).toInt()}%"),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = themeTextSecondary,
                    fontSize = 11.sp
                ),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun PrizeBreakdownCard(prizes: Map<String, String>) {
    // Group prizes by value (prize amount) to show tiers
    val prizeGroups = prizes.entries.groupBy { it.value }
    val sortedPrizeValues = prizeGroups.keys.sortedByDescending { it.removePrefix("$").toIntOrNull() ?: 0 }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = themeBgSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = s("\uD83C\uDFC6 Prizes", "\uD83C\uDFC6 الجوائز"),
                style = MaterialTheme.typography.titleMedium.copy(
                    color = themeTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Show grouped prize tiers
            sortedPrizeValues.forEachIndexed { tierIndex, prizeValue ->
                val ranks = prizeGroups[prizeValue] ?: emptyList()
                val sortedRanks = ranks.sortedBy { it.key }

                val tierColor = when (tierIndex) {
                    0 -> AccentPrimary
                    1 -> AccentSecondary
                    else -> AccentPurple
                }
                val tierIcon = when (tierIndex) {
                    0 -> "\uD83E\uDD47" // Gold
                    1 -> "\uD83E\uDD48" // Silver
                    else -> "\uD83E\uDD49" // Bronze
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = tierColor.copy(alpha = 0.06f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, tierColor.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = tierIcon,
                            fontSize = 22.sp,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sortedRanks.joinToString(", ") { it.key },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = themeTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = if (sortedRanks.size > 1) s("${sortedRanks.size} winners", "${sortedRanks.size} فائزين")
                                       else s("${sortedRanks.size} winner", "فائز واحد"),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = themeTextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tierColor.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Text(
                                text = prizeValue,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tierColor
                                )
                            )
                        }
                    }
                }

                if (tierIndex < sortedPrizeValues.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun CheckinButton(
    onClick: () -> Unit,
    currentDayNumber: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "checkin_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "checkin_pulse_scale"
    )

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(pulseScale)
            .semantics {
                contentDescription = "Check in for day $currentDayNumber"
                role = Role.Button
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentPrimary,
            contentColor = themeBgPrimary
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Videocam,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = s("Check In - Day $currentDayNumber", "تسجيل الحضور - اليوم $currentDayNumber"),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun PushupCheckinButton(
    onClick: () -> Unit,
    targetPushups: Int,
    currentPushups: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pushup_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pushup_pulse_scale"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Pushup progress
        if (targetPushups > 0) {
            LinearProgressIndicator(
                progress = { (currentPushups.toFloat() / targetPushups).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = AccentPrimary,
                trackColor = themeBgSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = s("$currentPushups / $targetPushups pushups", "$currentPushups / $targetPushups تمارين ضغط"),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = AccentPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                ),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .scale(pulseScale)
                .semantics {
                    contentDescription = "Start pushup counting with AI"
                    role = Role.Button
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentPrimary,
                contentColor = themeBgPrimary
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.FitnessCenter,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = s("Do Pushups", "قم بتمارين الضغط"),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun JoinEventButton(
    goldBalance: Int,
    entryFee: Int,
    eventOpen: Boolean = true,
    onClick: () -> Unit
) {
    val canAfford = goldBalance >= entryFee
    val canJoin = canAfford && eventOpen
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Entry fee + balance summary (spec section 24)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = VeryLightSage),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = s("ENTRY FEE", "رسوم الدخول"),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = themeTextSecondary,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = s("$entryFee Gold", "$entryFee ذهب"),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = themeTextPrimary
                        )
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = s("YOUR BALANCE", "رصيدك"),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = themeTextSecondary,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = s("%,d Gold", "%,d ذهب").format(goldBalance),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (canAfford) themeTextPrimary else MaterialTheme.colorScheme.error
                        )
                    )
                }
            }
        }

        Button(
            onClick = onClick,
            enabled = canJoin,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics {
                    contentDescription = when {
                        !eventOpen -> s("Entry opens when the event starts", "يفتح الاشتراك عند بداية الفعالية")
                        canJoin -> s("Join this event for $entryFee Gold", "اشترك في هذه الفعالية مقابل $entryFee ذهب")
                        else -> s("Need $entryFee Gold to join — your balance is $goldBalance", "تحتاج $entryFee ذهب للاشتراك — رصيدك $goldBalance")
                    }
                    role = Role.Button
                },
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentPrimary,
                contentColor = themeBgPrimary,
                disabledContainerColor = AccentPrimary.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    !eventOpen -> s("Entry Opens Soon", "يفتح الدخول قريبًا")
                    canAfford -> s("Enter Challenge · $entryFee Gold", "ادخل التحدي · $entryFee ذهب")
                    else -> s("Need ${(entryFee - goldBalance).coerceAtLeast(0)} more Gold", "تحتاج إلى ${(entryFee - goldBalance).coerceAtLeast(0)} ذهب إضافي")
                },
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        if (!eventOpen) {
            Text(
                text = s(
                    "COMING SOON — entry opens when the event starts. Your Gold is only charged when you join an open event.",
                    "قريبًا — يفتح الدخول عند بدء الفعالية. لا يُخصم الذهب إلا عند الانضمام إلى فعالية مفتوحة."
                ),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = themeTextSecondary
                ),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun CompletionBadgeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AccentPrimary.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = AccentPrimary,
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(
                    text = s("Challenge Completed!", "تم إكمال التحدي!"),
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = AccentPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = s("Congratulations on finishing this challenge!", "مبروك على إكمالك هذا التحدي!"),
                    style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary)
                )
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
//  Rewards information (spec section 25) — clear, honest reward conditions
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EventRewardsInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SoftSage.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = s("REWARDS", "المكافآت"),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = themeTextSecondary,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            val rewardLines = listOf(
                s("🏆 Reward type: PayPal gift cards + Gold + the exclusive Event Avatar Frame + Champion Badge.",
                  "🏆 نوع المكافأة: بطاقات هدايا PayPal + ذهب + إطار الصورة الحصري للفعالية + شارة البطل."),
                s("🥇 1st place: $50 PayPal Gift Card + 10,000 Gold + Event Avatar Frame + Champion Badge.",
                  "🥇 المركز الأول: بطاقة هدايا PayPal بقيمة 50$ + 10,000 ذهب + إطار الصورة الخاص بالفعالية + شارة البطل."),
                s("🥈 2nd place: $50 PayPal Gift Card + 6,000 Gold + Event Avatar Frame.",
                  "🥈 المركز الثاني: بطاقة هدايا PayPal بقيمة 50$ + 6,000 ذهب + إطار الصورة الخاص بالفعالية."),
                s("🥉 3rd place: $50 PayPal Gift Card + 3,000 Gold + Event Avatar Frame.",
                  "🥉 المركز الثالث: بطاقة هدايا PayPal بقيمة 50$ + 3,000 ذهب + إطار الصورة الخاص بالفعالية."),
                s("🏅 4th–5th place: $30 PayPal Gift Card + 1,500 Gold each.",
                  "🏅 المركز الرابع–الخامس: بطاقة هدايا PayPal بقيمة 30$ + 1,500 ذهب لكل منهما."),
                s("⭐ 6th–10th place: $15 PayPal Gift Card + 1,000 Gold each.",
                  "⭐ المركز السادس–العاشر: بطاقة هدايا PayPal بقيمة 15$ + 1,000 ذهب لكل منهم."),
                s("💳 Payout: the monetary prize is a PayPal gift card, delivered to the PayPal email saved in Settings → Payment / Payout Setup.",
                  "💳 الصرف: الجائزة المالية هي بطاقة هدايا PayPal، تُرسل إلى بريد PayPal المحفوظ في الإعدادات ← الدفع / إعداد الصرف."),
                s("👥 Winners: the top performers on the event leaderboard.",
                  "👥 الفائزون: الأعلى أداءً في لوحة صدارة الفعالية."),
                s("✅ Requirement: approved daily check-ins during the event window determine your score.",
                  "✅ الشرط: تسجيلات الحضور اليومية المقبولة خلال فترة الفعالية هي التي تحدد نتيجتك."),
                s("⏱️ Distribution: rewards are granted after the event ends and all check-ins are reviewed.",
                  "⏱️ التوزيع: تُمنح المكافآت بعد انتهاء الفعالية ومراجعة جميع تسجيلات الحضور."),
                s("🎯 Eligibility: one entry per account; entry fee (500 Gold) is paid once at join.",
                  "🎯 الأهلية: مشاركة واحدة لكل حساب؛ وتُدفع رسوم الدخول (500 ذهب) مرة واحدة عند الانضمام."),
                s("⚖️ Ties are broken by the earliest date the top score was reached.",
                  "⚖️ في حالة التعادل، تُفضّل النتيجة التي تحققت في تاريخ أبكر."),
                s("🛡️ Verified cheating, automated check-ins, or manipulated videos lead to disqualification and forfeited rewards.",
                  "🛡️ الغش المؤكد أو تسجيلات الحضور الآلية أو مقاطع الفيديو المعدّلة يؤدي إلى الاستبعاد ومصادرة المكافآت.")
            )
            rewardLines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall.copy(color = themeTextSecondary)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Payout status (spec section 21) — honest, never claims a
            // prize was paid before it was actually delivered.
            HorizontalDivider(color = SoftSage.copy(alpha = 0.5f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = s("PAYOUT STATUS", "حالة الصرف"),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = themeTextSecondary,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NaturalYellow.copy(alpha = 0.18f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = s("PENDING PAYOUT", "الصرف قيد الانتظار"),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = AccentSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = s(
                        "All prizes are pending until the event is finalized and " +
                            "check-in reviews complete. Gift cards are then delivered to " +
                            "the winner's saved PayPal email.",
                        "جميع الجوائز معلّقة حتى إقفال الفعالية واكتمال مراجعة تسجيلات الحضور. بعد ذلك تُسلّم بطاقات الهدايا إلى بريد PayPal المحفوظ للفائز."
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(color = themeTextSecondary),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Rules & Terms (spec section 26) — required acceptance-grade clarity
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EventRulesCard(event: Event) {
    val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val rules = listOf(
        s("👤 Eligibility: any Breathy account in good standing. Minimum age: 13 (or the minimum digital-consent age in your country).",
          "👤 الأهلية: أي حساب Breathy بحالة سليمة. الحد الأدنى للعمر: 13 عامًا (أو الحد الأدنى لعمر الموافقة الرقمية في بلدك)."),
        s("📅 Event dates: ${dateFormatter.format(event.startDate.toDate())} → ${dateFormatter.format(event.endDate.toDate())}.",
          "📅 تواريخ الفعالية: ${dateFormatter.format(event.startDate.toDate())} ← ${dateFormatter.format(event.endDate.toDate())}."),
        s("🪙 Entry: 500 Gold, charged once when you join. Entry is not refundable after the event starts.",
          "🪙 الدخول: 500 ذهب، تُخصم مرة واحدة عند الانضمام. رسوم الدخول غير قابلة للاسترداد بعد بدء الفعالية."),
        s("📈 Scoring: each approved daily check-in (verified push-up video) adds to your approved days and event score.",
          "📈 النقاط: كل تسجيل حضور يومي مقبول (فيديو تمارين ضغط موثّق) يضيف إلى أيامك المقبولة ونتيجتك في الفعالية."),
        s("🏅 Leaderboard: ranked by approved days; ties broken by earliest achievement of the score.",
          "🏅 لوحة الصدارة: الترتيب حسب الأيام المقبولة؛ ويُحسم التعادل لمن حقق النتيجة أولًا."),
        s("🛡️ Anti-cheat: submissions are human-reviewed. Automated, reused, or manipulated content is rejected.",
          "🛡️ مكافحة الغش: تخضع جميع المشاركات لمراجعة بشرية. يُرفض المحتوى الآلي أو المُعاد استخدامه أو المعدّل."),
        s("🚫 Disqualification: cheating, multiple accounts, or abusive behavior. Forfeits all event rewards.",
          "🚫 الاستبعاد: الغش أو استخدام حسابات متعددة أو السلوك المسيء. يؤدي إلى مصادرة جميع مكافآت الفعالية."),
        s("🎁 Rewards: distributed to eligible winners within days after review completes — PayPal gift cards (1st–3rd: $50, 4th–5th: $30, 6th–10th: $15) plus Gold, the Event Avatar Frame, and the Champion Badge. Gift cards are delivered to the winner's saved PayPal email (Settings → Payment / Payout Setup).",
          "🎁 المكافآت: تُوزّع على الفائزين المؤهلين خلال أيام بعد اكتمال المراجعة — بطاقات هدايا PayPal (الأول–الثالث: 50$، الرابع–الخامس: 30$، السادس–العاشر: 15$) إضافة إلى الذهب وإطار الصورة الخاص بالفعالية وشارة البطل. تُسلّم بطاقات الهدايا إلى بريد PayPal المحفوظ للفائز (الإعدادات ← الدفع / إعداد الصرف)."),
        s("🤝 Prohibited: harassment, inappropriate content, or any attempt to game the challenge.",
          "🤝 الممنوعات: التحرش أو المحتوى غير اللائق أو أي محاولة للتحايل على التحدي."),
        s("🔒 Privacy: check-in videos are reviewed by moderators for verification and are not shared publicly without your action. You keep ownership of your content.",
          "🔒 الخصوصية: يراجع المشرفون فيديوهات تسجيل الحضور للتحقق فقط، ولا تُنشر علنًا دون إجراء منك. تبقى ملكيتك لمحتواك محفوظة.")
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = VeryLightSage),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = s("RULES & TERMS", "القواعد والشروط"),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = DarkBotanical,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            rules.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall.copy(color = themeTextSecondary)
                )
            }
            Text(
                text = s("Joining the event means you accept these rules.", "انضمامك إلى الفعالية يعني موافقتك على هذه القواعد."),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = DarkBotanical,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}
