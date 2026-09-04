@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class)

package breathy.com.ui.events


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import breathy.com.R
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import breathy.com.BreathyApplication
import breathy.com.utils.s
import breathy.com.data.models.Event
import breathy.com.data.models.EventParticipant
import breathy.com.data.repository.EventRepository
import breathy.com.ui.theme.GoldDeep
import breathy.com.ui.theme.NaturalYellow
import breathy.com.ui.theme.DeepForest
import breathy.com.ui.theme.SoftSage
import breathy.com.ui.theme.PureWhite
import breathy.com.ui.theme.VeryLightSage
import breathy.com.ui.theme.AccentPrimary
import breathy.com.ui.theme.themeBgPrimary
import breathy.com.ui.theme.themeBgSurface
import breathy.com.ui.theme.themeBgSurfaceVariant
import breathy.com.ui.theme.themeTextPrimary
import breathy.com.ui.theme.themeTextSecondary
import breathy.com.ui.theme.themeTextDisabled
import breathy.com.ui.theme.AccentPurple
import breathy.com.ui.theme.themeBgPrimary
import breathy.com.ui.theme.themeBgSurface
import breathy.com.ui.theme.themeBgSurfaceVariant
import breathy.com.ui.theme.themeTextPrimary
import breathy.com.ui.theme.themeTextSecondary
import breathy.com.ui.theme.themeTextDisabled
import breathy.com.ui.theme.AccentSecondary
import breathy.com.ui.theme.themeBgPrimary
import breathy.com.ui.theme.themeBgSurface
import breathy.com.ui.theme.themeBgSurfaceVariant
import breathy.com.ui.theme.themeTextPrimary
import breathy.com.ui.theme.themeTextSecondary
import breathy.com.ui.theme.themeTextDisabled

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
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

// ═══════════════════════════════════════════════════════════════════════════════
//  UI State
// ═══════════════════════════════════════════════════════════════════════════════

data class EventsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val events: List<EventWithStatus> = emptyList(),
    val errorMessage: String? = null,
    val joiningEventId: String? = null,
    /** Verified premium entitlement — gates premium-only events. */
    val isPremium: Boolean = false,
    /** Real-time Gold balance — powers the 500-Gold entry gate. */
    val goldBalance: Int = 0
)

data class EventWithStatus(
    val event: Event,
    val isJoined: Boolean = false,
    val participantCount: Int = 0,
    val isCompleted: Boolean = false,
    /** True when the event is premium-only and the user is NOT premium. */
    val isLockedByPremium: Boolean = false
)

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

class EventsViewModel(
    private val eventRepository: EventRepository,
    private val auth: FirebaseAuth,
    private val premiumRepository: breathy.com.data.repository.PremiumRepository? = null,
    private val goldRepository: breathy.com.data.repository.GoldRepository? = null
) : ViewModel() {

    companion object {
        private const val TAG = "EventsViewModel"
    }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    init {
        loadEvents()
        observePremium()
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

    /** Mirror the app-wide verified premium entitlement into UI state. */
    private fun observePremium() {
        premiumRepository?.let { repo ->
            viewModelScope.launch {
                repo.state.collect { premium ->
                    _uiState.update { it.copy(isPremium = premium.isPremium) }
                }
            }
        }
    }

    fun loadEvents() {
        val uid = currentUserId ?: run {
            _uiState.update { it.copy(isLoading = false, errorMessage = s("Not authenticated", "غير مسجل الدخول")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            eventRepository.getActiveEvents().fold(
                onSuccess = { events ->
                    val eventsWithStatus = events.map { event ->
                        val participant = eventRepository.getParticipant(event.id, uid)
                            .getOrNull()
                        val isJoined = participant != null
                        val isCompleted = participant?.completed == true

                        EventWithStatus(
                            event = event,
                            isJoined = isJoined,
                            participantCount = 0, // Would need a count query
                            isCompleted = isCompleted,
                            isLockedByPremium = event.isPremiumOnly &&
                                !(premiumRepository?.isPremium() ?: false)
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            events = eventsWithStatus,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to load events")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = e.localizedMessage ?: s("Failed to load events", "تعذر تحميل الفعاليات")
                            )
                        }
                    }
                }
            )
        }
    }

    fun joinEvent(eventId: String) {
        val uid = currentUserId ?: return

        // Premium gate: non-premium users cannot enter premium-only events.
        val target = _uiState.value.events.firstOrNull { it.event.id == eventId }
        if (target?.isLockedByPremium == true) {
            _uiState.update {
                it.copy(errorMessage = s(
                    "This is an exclusive Premium event. Upgrade to Breathy Premium to join.",
                    "هذه فعالية حصرية لمشتركي بريميوم. قم بالترقية إلى Breathy بريميوم للانضمام."
                ))
            }
            return
        }

        _uiState.update { it.copy(joiningEventId = eventId) }

        viewModelScope.launch {
            // v1.0.9 premium perk: verified subscribers join any event FREE.
            val isPremium = premiumRepository?.isPremium() ?: false
            eventRepository.joinEvent(eventId, isPremium = isPremium).fold(
                onSuccess = { participant ->
                    _uiState.update { state ->
                        val updatedEvents = state.events.map { eventWithStatus ->
                            if (eventWithStatus.event.id == eventId) {
                                eventWithStatus.copy(isJoined = true)
                            } else eventWithStatus
                        }
                        state.copy(
                            events = updatedEvents,
                            joiningEventId = null
                        )
                    }
                },
                onFailure = { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Failed to join event: %s", eventId)
                        val message = when (e) {
                            is breathy.com.data.repository.InsufficientGoldException ->
                                s(
                                    "Not enough Gold — entry costs 500 Gold, you have ${e.available}.",
                                    "رصيد الذهب غير كافٍ — رسوم الدخول 500 ذهب، ولديك ${e.available}."
                                )
                            is IllegalStateException -> e.message ?: s("Failed to join event", "تعذر الانضمام إلى الفعالية")
                            else -> e.localizedMessage ?: s("Failed to join event", "تعذر الانضمام إلى الفعالية")
                        }
                        _uiState.update { it.copy(joiningEventId = null, errorMessage = message) }
                    }
                }
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            loadEvents()
            delay(500)
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }
}

class EventsViewModelFactory(
    private val eventRepository: EventRepository,
    private val auth: FirebaseAuth,
    private val premiumRepository: breathy.com.data.repository.PremiumRepository? = null,
    private val goldRepository: breathy.com.data.repository.GoldRepository? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventsViewModel::class.java)) {
            return EventsViewModel(eventRepository, auth, premiumRepository, goldRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  EventsScreen — List of active events/challenges
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun EventsScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToEventDetail: (String) -> Unit = {},
    viewModel: EventsViewModel = run {
        val context = LocalContext.current
        val app = context.applicationContext as BreathyApplication
        ViewModelProvider(
            androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner.current!!,
            EventsViewModelFactory(
                eventRepository = app.appModule.eventRepository,
                auth = app.appModule.firebaseAuth,
                premiumRepository = app.appModule.premiumRepository,
                goldRepository = app.appModule.goldRepository
            )
        )[EventsViewModel::class.java]
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        contentVisible = true
    }

    // NOTE: demo event auto-creation was REMOVED. Events are admin-managed
    // (Firestore rules: write only for admins). With no active events the
    // screen shows the polished Coming Soon state.

    DisposableEffect(Unit) {
        Timber.d("EventsScreen: composed")
        onDispose { Timber.d("EventsScreen: disposed") }
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
                    Text(
                        text = s("Events & Challenges", "الفعاليات والتحديات"),
                        fontWeight = FontWeight.Bold,
                        color = themeTextPrimary
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pullRefresh(pullRefreshState)
        ) {
            when {
                uiState.isLoading && uiState.events.isEmpty() -> {
                    EventsLoadingState()
                }
                uiState.errorMessage != null && uiState.events.isEmpty() -> {
                    EventsErrorState(
                        message = uiState.errorMessage!!,
                        onRetry = { viewModel.loadEvents() }
                    )
                }
                uiState.events.isEmpty() && !uiState.isLoading -> {
                    EventsEmptyState()
                }
                else -> {
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(400)) + slideInVertically(
                            initialOffsetY = { it / 4 },
                            animationSpec = tween(400)
                        )
                    ) {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Canonical featured event hero (same artwork as Home — spec §21)
                            item(key = "featured_hero") {
                                EventHeroBanner(
                                    goldBalance = uiState.goldBalance,
                                    onOpenEvent = { onNavigateToEventDetail(breathy.com.data.repository.EventRepository.FEATURED_EVENT_ID) }
                                )
                            }
                            items(
                                items = uiState.events,
                                key = { it.event.id }
                            ) { eventWithStatus ->
                                EventCard(
                                    eventWithStatus = eventWithStatus,
                                    isJoining = uiState.joiningEventId == eventWithStatus.event.id,
                                    goldBalance = uiState.goldBalance,
                                    entryFee = 500,
                                    isPremium = uiState.isPremium,
                                    onJoin = { viewModel.joinEvent(eventWithStatus.event.id) },
                                    onClick = { onNavigateToEventDetail(eventWithStatus.event.id) }
                                )
                            }
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                contentColor = AccentPrimary,
                backgroundColor = themeBgSurface
            )
        }
    }
}

/**
 * Canonical featured event hero — the SAME artwork as the Home featured card
 * (spec section 21). Coming Soon badge + title + description + reward preview.
 */
@Composable
private fun EventHeroBanner(
    goldBalance: Int,
    onOpenEvent: () -> Unit = {}
) {
    Card(
        onClick = onOpenEvent,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, SoftSage.copy(alpha = 0.6f))
    ) {
        Column {
            Box {
                Image(
                    painter = painterResource(R.drawable.event_pushup_hero),
                    contentDescription = "Push-Up Challenge event artwork",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                    contentScale = ContentScale.Crop
                )
                Card(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = DeepForest)
                ) {
                    Text(
                        text = s("COMING SOON", "قريبًا"),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = NaturalYellow,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = s("Push-Up Challenge", "تحدي تمارين الضغط"),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = themeTextPrimary
                    )
                )
                Text(
                    text = s(
                        "Compete with the community. Daily push-up check-ins, live event leaderboard, exclusive rewards.",
                        "نافس المجتمع. تسجيل حضور يومي لتمارين الضغط، ولوحة صدارة مباشرة للفعالية، ومكافآت حصرية."
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(color = themeTextSecondary)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = VeryLightSage)
                    ) {
                        Text(
                            text = s("🪙 Entry 500 Gold", "🪙 رسوم الدخول 500 ذهب"),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = GoldDeep,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = VeryLightSage)
                    ) {
                        Text(
                            text = s("Your balance: %,d", "رصيدك: %,d").format(goldBalance),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = themeTextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Event Card
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EventCard(
    eventWithStatus: EventWithStatus,
    isJoining: Boolean,
    goldBalance: Int,
    entryFee: Int,
    isPremium: Boolean = false,
    onJoin: () -> Unit,
    onClick: () -> Unit
) {
    val event = eventWithStatus.event
    val dateFormatter = SimpleDateFormat("MMM dd", Locale.getDefault())

    val isCurrentlyActive = event.isCurrentlyActive()
    val isEnded = System.currentTimeMillis() > event.endDate.toDate().time

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Event: ${event.title}. ${event.description}"
                role = Role.Button
            },
        colors = CardDefaults.cardColors(
            containerColor = if (eventWithStatus.isCompleted) {
                AccentPrimary.copy(alpha = 0.06f)
            } else if (isCurrentlyActive) {
                themeBgSurface
            } else {
                themeBgSurface.copy(alpha = 0.7f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
        border = if (eventWithStatus.isJoined && !eventWithStatus.isCompleted) {
            BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.3f))
        } else if (eventWithStatus.isCompleted) {
            BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.5f))
        } else null,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // ── Title Row ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = if (eventWithStatus.isCompleted) AccentPrimary else AccentPurple,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = themeTextPrimary,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Premium-exclusive badge
                if (event.isPremiumOnly && !eventWithStatus.isCompleted) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = breathy.com.ui.theme.DeepForest
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text = s("✦ Premium", "✦ بريميوم"),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = breathy.com.ui.theme.SoftSand,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Completed badge
                if (eventWithStatus.isCompleted) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AccentPrimary.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text = s("\u2705 Completed", "\u2705 مكتمل"),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AccentPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Description ──────────────────────────────────────────────
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = themeTextSecondary
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Countdown to start if event hasn't started yet ───────────
            val startMillis = event.startDate.toDate().time
            val nowMillis = System.currentTimeMillis()
            if (nowMillis < startMillis) {
                var countdownSeconds by remember { mutableStateOf((startMillis - nowMillis) / 1000) }
                LaunchedEffect(countdownSeconds) {
                    if (countdownSeconds > 0) {
                        delay(1000)
                        countdownSeconds = (countdownSeconds - 1).coerceAtLeast(0)
                    }
                }

                val days = countdownSeconds / 86400
                val hours = (countdownSeconds % 86400) / 3600
                val minutes = (countdownSeconds % 3600) / 60

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AccentPrimary.copy(alpha = 0.08f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = s("Starts in ${days}d ${hours}h ${minutes}m", "يبدأ بعد ${days}ي ${hours}س ${minutes}د"),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = AccentPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Meta Row: dates, participants ────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Date range
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = themeTextDisabled,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${dateFormatter.format(event.startDate.toDate())} - ${dateFormatter.format(event.endDate.toDate())}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = themeTextDisabled,
                            fontSize = 11.sp
                        )
                    )
                }

                // Daily requirement
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = null,
                        tint = themeTextDisabled,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = s("${event.dailyRequired}x daily", "${event.dailyRequired}x يوميًا"),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = themeTextDisabled,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Bottom Row: prize + action ──────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Prize info
                if (event.prizes.isNotEmpty()) {
                    val firstPrize = event.prizes["1st"] ?: event.prizes.entries.firstOrNull()?.value
                    if (firstPrize != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "\uD83C\uDFC6",
                                fontSize = 14.sp
                            )
                            Text(
                                text = s("1st: $firstPrize", "المركز الأول: $firstPrize"),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = AccentPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Join button or Joined indicator
                if (eventWithStatus.isCompleted) {
                    // Already completed
                    Text(
                        text = s("Finished", "انتهت"),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = themeTextDisabled,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                } else if (eventWithStatus.isJoined) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AccentPrimary.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text = s("\u2705 Joined", "\u2705 انضممت"),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = AccentPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                } else if (isCurrentlyActive || (event.active && System.currentTimeMillis() < event.startDate.toDate().time)) {
                    // v1.0.9: Premium subscribers join FREE — no balance gate.
                    val canAfford = isPremium || goldBalance >= entryFee
                    Column(horizontalAlignment = Alignment.End) {
                        Button(
                            onClick = onJoin,
                            enabled = !isJoining && canAfford,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentPrimary,
                                contentColor = themeBgPrimary,
                                disabledContainerColor = AccentPrimary.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.semantics {
                                contentDescription = if (isPremium) "Join ${event.title} free (Premium)"
                                                     else "Join ${event.title} for $entryFee Gold"
                                role = Role.Button
                            }
                        ) {
                            if (isJoining) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = themeBgPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = when {
                                    isJoining -> s("Joining...", "جارٍ الانضمام...")
                                    isPremium -> s("Join · FREE 👑", "انضم · مجانًا 👑")
                                    else -> s("Join · $entryFee \uD83E\uDE99", "انضم · $entryFee \uD83E\uDE99")
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        if (!canAfford) {
                            Text(
                                text = s(
                                    "Need ${(entryFee - goldBalance).coerceAtLeast(0)} more \uD83E\uDE99",
                                    "تحتاج إلى ${(entryFee - goldBalance).coerceAtLeast(0)} \uD83E\uDE99 إضافية"
                                ),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = themeTextSecondary,
                                    fontSize = 10.sp
                                ),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        } else if (isPremium) {
                            Text(
                                text = s("Premium perk — free entry", "ميزة بريميوم — دخول مجاني"),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = AccentPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else {
                    // Event not yet started — show countdown
                    val startMillis = event.startDate.toDate().time
                    val nowMillis = System.currentTimeMillis()
                    if (nowMillis < startMillis) {
                        var cardCountdownSeconds by remember { mutableStateOf((startMillis - nowMillis) / 1000) }
                        LaunchedEffect(cardCountdownSeconds) {
                            if (cardCountdownSeconds > 0) {
                                delay(1000)
                                cardCountdownSeconds = (cardCountdownSeconds - 1).coerceAtLeast(0)
                            }
                        }
                        val days = cardCountdownSeconds / 86400
                        val hours = (cardCountdownSeconds % 86400) / 3600
                        val minutes = (cardCountdownSeconds % 3600) / 60
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AccentPrimary.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Text(
                                text = s("${days}d ${hours}h ${minutes}m", "${days}ي ${hours}س ${minutes}د"),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = AccentPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    } else {
                        Text(
                            text = s("Coming Soon", "قريبًا"),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = themeTextDisabled
                            )
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Loading / Empty / Error States
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EventsLoadingState() {
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
                text = s("Loading events...", "جارٍ تحميل الفعاليات..."),
                style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary)
            )
        }
    }
}

@Composable
private fun EventsEmptyState(featured: Boolean = true) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        breathy.com.ui.components.BreathyEmptyState(
            icon = "\uD83C\uDFCB\uFE0F",
            title = s("Exclusive events are coming soon", "فعاليات حصرية قريبًا"),
            subtitle = s(
                "Compete, complete challenges, and earn rewards. " +
                    "Premium members get first access to every event.",
                "نافس وأكمل التحديات واحصل على مكافآت. يحصل أعضاء بريميوم على أولوية الوصول إلى كل فعالية."
            )
        )
    }
}

@Composable
private fun EventsErrorState(
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
                    color = themeBgPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
