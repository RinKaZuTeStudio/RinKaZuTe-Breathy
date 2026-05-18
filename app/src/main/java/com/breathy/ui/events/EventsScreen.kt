@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class)

package com.breathy.ui.events


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
import com.breathy.BreathyApplication
import com.breathy.data.models.Event
import com.breathy.data.models.EventParticipant
import com.breathy.data.repository.EventRepository
import com.breathy.ui.theme.AccentPrimary
import com.breathy.ui.theme.themeBgPrimary
import com.breathy.ui.theme.themeBgSurface
import com.breathy.ui.theme.themeBgSurfaceVariant
import com.breathy.ui.theme.themeTextPrimary
import com.breathy.ui.theme.themeTextSecondary
import com.breathy.ui.theme.themeTextDisabled
import com.breathy.ui.theme.AccentPurple
import com.breathy.ui.theme.themeBgPrimary
import com.breathy.ui.theme.themeBgSurface
import com.breathy.ui.theme.themeBgSurfaceVariant
import com.breathy.ui.theme.themeTextPrimary
import com.breathy.ui.theme.themeTextSecondary
import com.breathy.ui.theme.themeTextDisabled
import com.breathy.ui.theme.AccentSecondary
import com.breathy.ui.theme.themeBgPrimary
import com.breathy.ui.theme.themeBgSurface
import com.breathy.ui.theme.themeBgSurfaceVariant
import com.breathy.ui.theme.themeTextPrimary
import com.breathy.ui.theme.themeTextSecondary
import com.breathy.ui.theme.themeTextDisabled

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
    val joiningEventId: String? = null
)

data class EventWithStatus(
    val event: Event,
    val isJoined: Boolean = false,
    val participantCount: Int = 0,
    val isCompleted: Boolean = false
)

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

class EventsViewModel(
    private val eventRepository: EventRepository,
    private val auth: FirebaseAuth
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
    }

    fun loadEvents() {
        val uid = currentUserId ?: run {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Not authenticated") }
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
                            isCompleted = isCompleted
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
                                errorMessage = e.localizedMessage ?: "Failed to load events"
                            )
                        }
                    }
                }
            )
        }
    }

    fun joinEvent(eventId: String) {
        val uid = currentUserId ?: return

        _uiState.update { it.copy(joiningEventId = eventId) }

        viewModelScope.launch {
            eventRepository.joinEvent(eventId).fold(
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
                        _uiState.update { it.copy(joiningEventId = null) }
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
    private val auth: FirebaseAuth
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventsViewModel::class.java)) {
            return EventsViewModel(eventRepository, auth) as T
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
                auth = app.appModule.firebaseAuth
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
                        text = "Events & Challenges",
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
                            items(
                                items = uiState.events,
                                key = { it.event.id }
                            ) { eventWithStatus ->
                                EventCard(
                                    eventWithStatus = eventWithStatus,
                                    isJoining = uiState.joiningEventId == eventWithStatus.event.id,
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

// ═══════════════════════════════════════════════════════════════════════════════
//  Event Card
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EventCard(
    eventWithStatus: EventWithStatus,
    isJoining: Boolean,
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

                // Completed badge
                if (eventWithStatus.isCompleted) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AccentPrimary.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Text(
                            text = "\u2705 Completed",
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
                        text = "${event.dailyRequired}x daily",
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
                                text = "1st: $firstPrize",
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
                        text = "Finished",
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
                            text = "\u2705 Joined",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = AccentPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                } else if (isCurrentlyActive) {
                    Button(
                        onClick = onJoin,
                        enabled = !isJoining,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentPrimary,
                            contentColor = themeBgPrimary,
                            disabledContainerColor = AccentPrimary.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.semantics {
                            contentDescription = "Join ${event.title}"
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
                            text = if (isJoining) "Joining..." else "Join",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Text(
                        text = "Coming Soon",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = themeTextDisabled
                        )
                    )
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
                text = "Loading events...",
                style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary)
            )
        }
    }
}

@Composable
private fun EventsEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "\uD83C\uDFC5",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Active Events",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = themeTextPrimary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Check back soon for new challenges and events!",
                style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary),
                textAlign = TextAlign.Center
            )
        }
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
                text = "Something went wrong",
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
                    text = "Try Again",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    color = themeBgPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
