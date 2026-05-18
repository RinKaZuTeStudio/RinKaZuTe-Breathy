@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class)

package com.breathy.ui.leaderboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.breathy.ui.components.NetworkImage
import com.breathy.BreathyApplication
import com.breathy.data.models.PublicProfile
import com.breathy.data.repository.EventRepository
import com.breathy.data.repository.UserRepository
import com.breathy.ui.theme.AccentPrimary
import com.breathy.ui.theme.AccentPurple
import com.breathy.ui.theme.AccentSecondary
import com.breathy.ui.theme.themeBgPrimary
import com.breathy.ui.theme.themeBgSurface
import com.breathy.ui.theme.themeBgSurfaceVariant
import com.breathy.ui.theme.themeTextPrimary
import com.breathy.ui.theme.themeTextSecondary
import com.breathy.ui.theme.themeTextDisabled

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
    val errorMessage: String? = null
)

data class LeaderboardEntry(
    val userId: String = "",
    val nickname: String = "",
    val photoURL: String? = null,
    val xp: Int = 0,
    val daysSmokeFree: Int = 0,
    val rank: Int = 0
)

enum class LeaderboardPeriod(val label: String) {
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    ALL_TIME("All Time")
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

class LeaderboardViewModel(
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    companion object {
        private const val TAG = "LeaderboardViewModel"
        private const val LEADERBOARD_LIMIT = 50
    }

    private val currentUserId: String?
        get() = auth.currentUser?.uid

    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    init {
        loadLeaderboard()
    }

    fun loadLeaderboard() {
        val uid = currentUserId ?: run {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Not authenticated") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                val flow = userRepository.observePublicProfilesOrderedByXp(LEADERBOARD_LIMIT)

                flow.collect { profilePairs ->
                    val entries = profilePairs.mapIndexed { index, pair ->
                        val (userId, profile) = pair
                        LeaderboardEntry(
                            userId = userId,
                            nickname = profile.nickname,
                            photoURL = profile.photoURL,
                            xp = profile.xp,
                            daysSmokeFree = profile.daysSmokeFree,
                            rank = index + 1
                        )
                    }

                    val currentUserEntry = entries.find { it.userId == uid }

                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            entries = entries,
                            currentUserEntry = currentUserEntry,
                            errorMessage = null
                        )
                    }

                    // Fetch current user's own rank if not found in entries
                    if (currentUserEntry == null) {
                        fetchCurrentUserRank(uid, profilePairs)
                    }
                }
            } catch (e: CancellationException) {
                // Ignore
            } catch (e: Exception) {
                Timber.e(e, "Failed to load leaderboard")
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Failed to load leaderboard: ${e.message}")
                }
            }
        }
    }

    private suspend fun fetchCurrentUserRank(uid: String, profilePairs: List<Pair<String, PublicProfile>>) {
        try {
            val userProfile = userRepository.getPublicProfile(uid).getOrNull() ?: return
            // Match by document ID (= userId) instead of nickname to avoid duplicate-nickname issues
            val rank = profilePairs.indexOfFirst { it.first == uid } + 1
            val entry = LeaderboardEntry(
                userId = uid,
                nickname = userProfile.nickname,
                photoURL = userProfile.photoURL,
                xp = userProfile.xp,
                daysSmokeFree = userProfile.daysSmokeFree,
                rank = if (rank > 0) rank else profilePairs.size + 1
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
        _uiState.update { it.copy(selectedPeriod = period) }
        // In production, this would filter the query by time range
        // For now, we reload the same data
        loadLeaderboard()
    }
}

class LeaderboardViewModelFactory(
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LeaderboardViewModel::class.java)) {
            return LeaderboardViewModel(userRepository, auth) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  LeaderboardScreen — Global XP leaderboard
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
                auth = app.appModule.firebaseAuth
            )
        )[LeaderboardViewModel::class.java]
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    // Staggered entrance
    var podiumVisible by remember { mutableStateOf(false) }
    var listVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        podiumVisible = true
        delay(300)
        listVisible = true
    }

    DisposableEffect(Unit) {
        Timber.d("LeaderboardScreen: composed")
        onDispose { Timber.d("LeaderboardScreen: disposed") }
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
                        text = "Leaderboard",
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
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // ── Period Filter Chips ─────────────────────────────
                        PeriodFilterChips(
                            selectedPeriod = uiState.selectedPeriod,
                            onPeriodSelected = { viewModel.selectPeriod(it) }
                        )

                        // ── Top 3 Podium ───────────────────────────────────
                        if (uiState.entries.size >= 3) {
                            AnimatedVisibility(
                                visible = podiumVisible,
                                enter = fadeIn(tween(500)) + slideInVertically(
                                    initialOffsetY = { it / 3 },
                                    animationSpec = tween(500)
                                )
                            ) {
                                PodiumSection(
                                    entries = uiState.entries,
                                    onProfileClick = onNavigateToProfile
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // ── Scrollable List ────────────────────────────────
                        AnimatedVisibility(
                            visible = listVisible,
                            enter = fadeIn(tween(400))
                        ) {
                            val topEntries = if (uiState.entries.size > 3) {
                                uiState.entries.subList(3, uiState.entries.size)
                            } else {
                                emptyList()
                            }

                            val currentInTopList = uiState.currentUserEntry?.let { currentUser ->
                                topEntries.any { it.userId == currentUser.userId }
                            } == true

                            LazyColumn(
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 4.dp,
                                    bottom = if (uiState.currentUserEntry != null && !currentInTopList) 80.dp else 16.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(
                                    items = topEntries,
                                    key = { _, entry -> entry.rank }
                                ) { index, entry ->
                                    LeaderboardRow(
                                        entry = entry,
                                        isCurrentUser = entry.userId == uiState.currentUserEntry?.userId,
                                        onProfileClick = onNavigateToProfile
                                    )
                                }

                                if (topEntries.isEmpty() && uiState.entries.size <= 3) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Top 3 are on the podium above!",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = themeTextSecondary
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Current User Fixed Bottom Bar ──────────────────────
                    val currentUserInTop3 = uiState.currentUserEntry?.let { cur ->
                        uiState.entries.take(3).any { it.userId == cur.userId }
                    } == true
                    val currentUserInList = uiState.currentUserEntry?.let { cur ->
                        uiState.entries.any { it.userId == cur.userId }
                    } == true

                    if (uiState.currentUserEntry != null && !currentUserInTop3 && uiState.entries.size > 3) {
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
//  Period Filter Chips
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PeriodFilterChips(
    selectedPeriod: LeaderboardPeriod,
    onPeriodSelected: (LeaderboardPeriod) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LeaderboardPeriod.entries.forEach { period ->
            FilterChip(
                selected = selectedPeriod == period,
                onClick = { onPeriodSelected(period) },
                label = {
                    Text(
                        text = period.label,
                        fontWeight = if (selectedPeriod == period) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentPrimary.copy(alpha = 0.2f),
                    selectedLabelColor = AccentPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = themeTextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = MaterialTheme.colorScheme.surfaceVariant,
                    selectedBorderColor = AccentPrimary.copy(alpha = 0.5f),
                    enabled = true,
                    selected = selectedPeriod == period
                ),
                modifier = Modifier.semantics {
                    contentDescription = "Filter by ${period.label}"
                    role = androidx.compose.ui.semantics.Role.Button
                }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Podium Section — Top 3 with gold/silver/bronze
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PodiumSection(
    entries: List<LeaderboardEntry>,
    onProfileClick: (String) -> Unit
) {
    if (entries.size < 3) return

    // Reorder: 2nd (left), 1st (center), 3rd (right)
    val first = entries[0]
    val second = entries[1]
    val third = entries[2]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            // Silver — 2nd place
            PodiumCard(
                entry = second,
                medalColor = Color(0xFFC0C0C0),
                medalEmoji = "\uD83E\uDD48",
                height = 140.dp,
                onProfileClick = onProfileClick
            )

            // Gold — 1st place
            PodiumCard(
                entry = first,
                medalColor = Color(0xFFFFD700),
                medalEmoji = "\uD83E\uDD47",
                height = 180.dp,
                onProfileClick = onProfileClick
            )

            // Bronze — 3rd place
            PodiumCard(
                entry = third,
                medalColor = Color(0xFFCD7F32),
                medalEmoji = "\uD83E\uDD49",
                height = 120.dp,
                onProfileClick = onProfileClick
            )
        }
    }
}

@Composable
private fun PodiumCard(
    entry: LeaderboardEntry,
    medalColor: Color,
    medalEmoji: String,
    height: androidx.compose.ui.unit.Dp,
    onProfileClick: (String) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "podium_glow_${entry.rank}")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_${entry.rank}"
    )

    Card(
        modifier = Modifier
            .width(100.dp)
            .height(height)
            .semantics {
                contentDescription = "Rank ${entry.rank}: ${entry.nickname}, ${entry.xp} XP"
                role = androidx.compose.ui.semantics.Role.Button
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        onClick = { onProfileClick(entry.userId) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Glow effect
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            medalColor.copy(alpha = glowAlpha),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2, size.height * 0.3f),
                        radius = size.minDimension * 0.6f
                    ),
                    radius = size.minDimension * 0.6f,
                    center = Offset(size.width / 2, size.height * 0.3f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Medal emoji
                Text(
                    text = medalEmoji,
                    fontSize = 20.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Avatar
                Card(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(
                        containerColor = medalColor.copy(alpha = 0.2f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    if (entry.photoURL != null) {
                        NetworkImage(
                            model = entry.photoURL,
                            contentDescription = "${entry.nickname}'s avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = entry.nickname.take(1).uppercase(),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = medalColor,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Nickname
                Text(
                    text = entry.nickname,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = themeTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // XP
                Text(
                    text = "${entry.xp} XP",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = AccentPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    fontFamily = FontFamily.Monospace
                )
            }
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
    val cardColor = if (isCurrentUser) {
        AccentPrimary.copy(alpha = 0.08f)
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
        shape = RoundedCornerShape(12.dp),
        border = if (isCurrentUser) {
            androidx.compose.foundation.BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.3f))
        } else null,
        onClick = { onProfileClick(entry.userId) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rank number
            Text(
                text = "${entry.rank}",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrentUser) AccentPrimary else themeTextSecondary
                ),
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.Center
            )

            // Avatar
            Card(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = AccentPrimary.copy(alpha = 0.15f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                if (entry.photoURL != null) {
                    NetworkImage(
                        model = entry.photoURL,
                        contentDescription = "${entry.nickname}'s avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = entry.nickname.take(1).uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = AccentPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // Nickname and days
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = entry.nickname,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (isCurrentUser) AccentPrimary else themeTextPrimary,
                        fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${entry.daysSmokeFree} days smoke-free",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = themeTextSecondary,
                        fontSize = 11.sp
                    )
                )
            }

            // XP
            Text(
                text = "${entry.xp} XP",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrentUser) AccentPrimary else AccentSecondary
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Current User Bottom Bar — Fixed at bottom if not in top rankings
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
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 8.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${entry.rank}",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPrimary
                    ),
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.Center
                )

                Card(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(
                        containerColor = AccentPrimary.copy(alpha = 0.2f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    if (entry.photoURL != null) {
                        NetworkImage(
                            model = entry.photoURL,
                            contentDescription = "Your avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = entry.nickname.take(1).uppercase(),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = AccentPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "You",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = AccentPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "${entry.daysSmokeFree} days smoke-free",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = themeTextSecondary,
                            fontSize = 11.sp
                        )
                    )
                }

                Text(
                    text = "${entry.xp} XP",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPrimary
                    )
                )
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
                text = "Loading leaderboard...",
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
                    color = MaterialTheme.colorScheme.background,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
