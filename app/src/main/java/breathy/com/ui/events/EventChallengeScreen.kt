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
import androidx.compose.foundation.Canvas
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
import breathy.com.data.models.Event
import breathy.com.data.models.EventParticipant
import breathy.com.data.repository.EventRepository
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
    val isPushupChallenge: Boolean = false
)

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

class EventChallengeViewModel(
    private val eventRepository: EventRepository,
    private val auth: FirebaseAuth,
    private val eventId: String
) : ViewModel() {

    companion object {
        private const val TAG = "EventChallengeViewModel"
    }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    private val _uiState = MutableStateFlow(EventChallengeUiState())
    val uiState: StateFlow<EventChallengeUiState> = _uiState.asStateFlow()

    init {
        loadEventData()
    }

    fun loadEventData() {
        val uid = currentUserId ?: run {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Not authenticated") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                // Load event details
                val event = eventRepository.getEvent(eventId).getOrNull() ?: run {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Event not found") }
                    return@launch
                }

                // Load participant info
                val participant = eventRepository.getParticipant(eventId, uid).getOrNull()
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
                        isPushupChallenge = event.isPushupChallenge()
                    )
                }
            } catch (e: CancellationException) {
                // Ignore
            } catch (e: Exception) {
                Timber.e(e, "Failed to load event data")
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Failed to load event: ${e.message}")
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
            eventRepository.joinEvent(eventId).fold(
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
    private val eventId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventChallengeViewModel::class.java)) {
            return EventChallengeViewModel(eventRepository, auth, eventId) as T
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
                eventId = eventId
            )
        )[EventChallengeViewModel::class.java]
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Details", "Leaderboard")

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
                        text = uiState.event?.title ?: "Event",
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
                                text = "Retry",
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
        if (!uiState.isJoined && (event.isCurrentlyActive() || (event.active && nowMillis < startMillis))) {
            item {
                JoinEventButton(onClick = onJoin)
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
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = AccentPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Loading leaderboard...",
                    style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary)
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = leaderboard,
                key = { it.participant.userId }
            ) { entry ->
                EventLeaderboardRow(
                    entry = entry,
                    onProfileClick = onProfileClick
                )
            }
        }
    }
}

@Composable
private fun EventLeaderboardRow(
    entry: EventRepository.EventLeaderboardEntry,
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
        colors = CardDefaults.cardColors(
            containerColor = if (entry.rank <= 3) AccentPrimary.copy(alpha = 0.06f) else themeBgSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
        border = if (entry.rank <= 3) BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.2f)) else null,
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

            // Avatar
            Card(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = AccentPrimary.copy(alpha = 0.15f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                if (profile?.photoURL != null) {
                    NetworkImage(
                        model = profile.photoURL,
                        contentDescription = "${profile.nickname}'s avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (profile?.nickname ?: "?").take(1).uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = AccentPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // Name and streak
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile?.nickname ?: "Unknown",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = themeTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (entry.participant.totalPushups > 0) {
                        "\uD83D\uDCAA ${entry.participant.totalPushups} pushups"
                    } else {
                        "\uD83D\uDD25 ${entry.participant.currentStreak} day streak"
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
                text = "Event Starts In",
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
                text = "${days}d ${hours}h ${minutes}m ${seconds}s",
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
                text = "About This Challenge",
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
                    text = "Daily requirement: ${event.dailyRequired}x per day",
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
                    text = "Duration: ${event.totalDays()} days",
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
                text = "Time Remaining",
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
                CountdownUnit(value = days, label = "Days")
                Text(
                    text = ":",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPrimary
                    )
                )
                CountdownUnit(value = hours, label = "Hrs")
                Text(
                    text = ":",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPrimary
                    )
                )
                CountdownUnit(value = minutes, label = "Min")
                Text(
                    text = ":",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPrimary
                    )
                )
                CountdownUnit(value = seconds, label = "Sec")
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
                text = "Your Progress",
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
                        text = "Day Streak",
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
                        text = "Days Approved",
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
                        text = "of $totalDays Days",
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
                text = "${(progress * 100).toInt()}% complete",
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
                text = "\uD83C\uDFC6 Prizes",
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
                                text = "${sortedRanks.size} winner${if (sortedRanks.size > 1) "s" else ""}",
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
            text = "Check In - Day $currentDayNumber",
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
                text = "$currentPushups / $targetPushups pushups",
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
                text = "Do Pushups",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun JoinEventButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .semantics {
                contentDescription = "Join this event"
                role = Role.Button
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentPrimary,
            contentColor = themeBgPrimary
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
            text = "Join Challenge",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
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
                    text = "Challenge Completed!",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = AccentPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Congratulations on finishing this challenge!",
                    style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary)
                )
            }
        }
    }
}
