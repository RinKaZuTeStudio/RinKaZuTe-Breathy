@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class)

package com.breathy.ui.home


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.breathy.BreathyApplication
import com.breathy.data.models.CopingMethod
import com.breathy.ui.theme.AccentOrange
import com.breathy.ui.theme.AccentPrimary
import com.breathy.ui.theme.AccentPurple
import com.breathy.ui.theme.BgPrimary
import com.breathy.ui.theme.BgSurface
import com.breathy.ui.theme.BgSurfaceVariant
import com.breathy.ui.theme.TextDisabled
import com.breathy.ui.theme.TextPrimary
import com.breathy.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.compose.material3.Icon

// ═══════════════════════════════════════════════════════════════════════════════
//  HomeScreen — Main dashboard composable
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToAICoach: () -> Unit = {},
    viewModel: HomeViewModel = run {
        val context = LocalContext.current
        val app = context.applicationContext as BreathyApplication
        viewModel(factory = HomeViewModelFactory(
            userRepository = app.appModule.userRepository,
            rewardRepository = app.appModule.rewardRepository,
            auth = app.appModule.firebaseAuth
        ))
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Overlay states
    var showCravingSheet by remember { mutableStateOf(false) }
    var showBreathingExercise by remember { mutableStateOf(false) }
    var showTapGame by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showCravingConfetti by remember { mutableStateOf(false) }

    // Daily reward countdown timer
    var countdownSeconds by remember { mutableIntStateOf(uiState.dailyRewardCountdownSeconds.toInt()) }
    LaunchedEffect(uiState.dailyRewardCountdownSeconds) {
        countdownSeconds = uiState.dailyRewardCountdownSeconds.toInt()
    }
    LaunchedEffect(countdownSeconds) {
        if (countdownSeconds > 0) {
            delay(1000)
            countdownSeconds = (countdownSeconds - 1).coerceAtLeast(0)
        }
    }

    // Handle single events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeSingleEvent.ShowDailyReward -> {
                    snackbarHostState.showSnackbar(
                        "🎉 Reward Claimed! +${event.coins} coins",
                        duration = SnackbarDuration.Short
                    )
                }
                is HomeSingleEvent.ShowCravingXP -> {
                    snackbarHostState.showSnackbar(
                        "💪 +${event.xp} XP earned!",
                        duration = SnackbarDuration.Short
                    )
                }
                is HomeSingleEvent.ShowAchievementUnlock -> {
                    snackbarHostState.showSnackbar(
                        "🏆 Achievement: ${event.achievement.title}!",
                        duration = SnackbarDuration.Long
                    )
                }
                is HomeSingleEvent.ShowError -> {
                    snackbarHostState.showSnackbar(
                        event.message,
                        duration = SnackbarDuration.Short
                    )
                }
            }
        }
    }

    // Lifecycle logging
    DisposableEffect(Unit) {
        Timber.d("HomeScreen: composed")
        onDispose { Timber.d("HomeScreen: disposed") }
    }

    // Pull-to-refresh
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

    // Staggered entrance animations
    var heroVisible by remember { mutableStateOf(false) }
    var statsVisible by remember { mutableStateOf(false) }
    var rewardVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        heroVisible = true
        delay(300)
        statsVisible = true
        delay(200)
        rewardVisible = true
    }

    // Animated days counter
    var displayDays by remember { mutableIntStateOf(0) }
    LaunchedEffect(uiState.daysSmokeFree) {
        val target = uiState.daysSmokeFree
        if (target <= displayDays) {
            displayDays = target
            return@LaunchedEffect
        }
        val steps = 30.coerceAtMost(target - displayDays)
        val increment = (target - displayDays) / steps.coerceAtLeast(1)
        repeat(steps) {
            displayDays = (displayDays + increment).coerceAtMost(target)
            delay(20)
        }
        displayDays = target
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = BgPrimary,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                CravingFAB(
                    onClick = { showCravingSheet = true }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // ── Top Bar with Greeting ───────────────────────────────
                    TopBar(
                        nickname = uiState.nickname,
                        photoURL = uiState.photoURL,
                        onAvatarClick = onNavigateToProfile,
                        onNotificationClick = onNavigateToNotifications
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Hero Stat Card (Days Smoke-Free) ────────────────────
                    AnimatedVisibility(
                        visible = heroVisible,
                        enter = scaleIn(
                            initialScale = 0.8f,
                            animationSpec = tween(500)
                        ) + fadeIn(animationSpec = tween(500))
                    ) {
                        HeroStatCard(
                            daysSmokeFree = displayDays,
                            level = uiState.level,
                            levelProgress = uiState.levelProgress,
                            xpForNextLevel = uiState.xpForNextLevel
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Stat Cards Row ─────────────────────────────────────
                    AnimatedVisibility(
                        visible = statsVisible,
                        enter = slideInVertically(
                            initialOffsetY = { it / 4 },
                            animationSpec = tween(400)
                        ) + fadeIn(animationSpec = tween(400))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MoneySavedCard(
                                moneySaved = uiState.moneySaved,
                                modifier = Modifier.weight(1f)
                            )
                            CigarettesAvoidedCard(
                                cigarettesAvoided = uiState.cigarettesAvoided,
                                modifier = Modifier.weight(1f)
                            )
                            LifeRegainedCard(
                                lifeRegainedText = viewModel.formatLifeRegained(uiState.lifeRegainedMinutes),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ── Daily Reward Banner ────────────────────────────────
                    AnimatedVisibility(
                        visible = rewardVisible,
                        enter = fadeIn(animationSpec = tween(300)) +
                                slideInVertically(
                                    initialOffsetY = { it / 4 },
                                    animationSpec = tween(300)
                                )
                    ) {
                        if (!uiState.dailyRewardClaimed) {
                            DailyRewardBanner(
                                onClaim = { viewModel.claimDailyReward() }
                            )
                        } else if (countdownSeconds > 0) {
                            DailyRewardCountdown(
                                countdownText = viewModel.formatCountdown(countdownSeconds.toLong())
                            )
                        }
                    }

                    // Confetti overlay for daily reward claim
                    if (uiState.dailyRewardJustClaimed > 0) {
                        ConfettiMessage(
                            coinsAwarded = uiState.dailyRewardJustClaimed,
                            onDismiss = { viewModel.clearDailyRewardClaimed() }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ── Craving SOS Quick Stats ────────────────────────────
                    if (uiState.successfulCravings > 0) {
                        CravingStreakCard(
                            successfulCount = uiState.successfulCravings,
                            lastCravingTimeAgo = uiState.lastCravingTimeAgo
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // ── Health Timeline ────────────────────────────────────
                    Text(
                        text = "Health Timeline",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    HealthTimeline(
                        milestones = uiState.healthMilestones,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(80.dp)) // Bottom padding for FAB
                }

                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    contentColor = AccentPrimary,
                    backgroundColor = BgSurface
                )
            }
        }

        // ── Craving Bottom Sheet ─────────────────────────────────────────
        if (showCravingSheet) {
            CravingBottomSheet(
                lastCravingTimeAgo = uiState.lastCravingTimeAgo,
                onBreathingExercise = {
                    showCravingSheet = false
                    showBreathingExercise = true
                },
                onMiniGame = {
                    showCravingSheet = false
                    showTapGame = true
                },
                onAICoach = {
                    showCravingSheet = false
                    onNavigateToAICoach()
                },
                onLogCraving = { method, success ->
                    viewModel.logCraving(method, success)
                    if (success) {
                        showCravingConfetti = true
                    }
                },
                onDismiss = {
                    showCravingSheet = false
                }
            )
        }

        // ── Breathing Exercise Overlay ───────────────────────────────────
        if (showBreathingExercise) {
            BreathingExercise(
                onComplete = { success ->
                    showBreathingExercise = false
                    viewModel.logCraving(CopingMethod.BREATHING, success)
                    if (success) showCravingConfetti = true
                },
                onCancel = {
                    showBreathingExercise = false
                }
            )
        }

        // ── Tap Game Overlay ─────────────────────────────────────────────
        if (showTapGame) {
            TapGame(
                onComplete = { success ->
                    showTapGame = false
                    viewModel.logCraving(CopingMethod.GAME, success)
                    if (success) showCravingConfetti = true
                },
                onCancel = {
                    showTapGame = false
                }
            )
        }

        // ── Craving Defeat Confetti ──────────────────────────────────────
        if (showCravingConfetti) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp),
                contentAlignment = Alignment.Center
            ) {
                KonfettiOverlay(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
                LaunchedEffect(Unit) {
                    delay(3000)
                    showCravingConfetti = false
                }
            }
        }

        // ── Loading State ────────────────────────────────────────────────
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Loading your progress...",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextSecondary
                        )
                    )
                }
            }
        }

        // ── Error State ──────────────────────────────────────────────────
        if (uiState.errorMessage != null && !uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextSecondary
                        ),
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
                            color = BgPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Top Bar — Greeting with nickname and avatar
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TopBar(
    nickname: String,
    photoURL: String?,
    onAvatarClick: () -> Unit,
    onNotificationClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar
            Card(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = CardDefaults.cardColors(containerColor = AccentPrimary.copy(alpha = 0.2f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                onClick = onAvatarClick
            ) {
                if (photoURL != null) {
                    AsyncImage(
                        model = photoURL,
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
                            text = nickname.take(1).uppercase(),
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = AccentPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // Greeting
            Column {
                Text(
                    text = getGreeting(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                )
                Text(
                    text = nickname.ifBlank { "there" },
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Notification bell
        Card(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = BgSurfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            onClick = onNotificationClick
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🔔",
                    fontSize = 18.sp
                )
            }
        }
    }
}

/** Return a time-of-day greeting. */
private fun getGreeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning ☀️"
        in 12..16 -> "Good afternoon 🌤️"
        in 17..20 -> "Good evening 🌅"
        else -> "Good night 🌙"
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Hero Stat Card — Large days smoke-free counter with XP/Level progress
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun HeroStatCard(
    daysSmokeFree: Int,
    level: Int,
    levelProgress: Float,
    xpForNextLevel: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        ) {
            // Glow effect behind the number
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            ) {
                val center = Offset(size.width / 2, size.height / 2 - 10.dp.toPx())
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AccentPrimary.copy(alpha = glowAlpha),
                            AccentPrimary.copy(alpha = 0f)
                        ),
                        center = center,
                        radius = 120.dp.toPx()
                    ),
                    radius = 120.dp.toPx(),
                    center = center
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Days Smoke-Free",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Large animated counter
                Text(
                    text = daysSmokeFree.toString(),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentPrimary,
                        shadow = Shadow(
                            color = AccentPrimary.copy(alpha = 0.4f),
                            offset = Offset(0f, 0f),
                            blurRadius = 16f
                        )
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // XP/Level progress bar
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Level $level",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AccentPurple,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                        Text(
                            text = "$xpForNextLevel XP to next",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextDisabled,
                                fontSize = 11.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { levelProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = AccentPrimary,
                        trackColor = BgSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Daily Reward Banner — Claimable reward with pulsing CTA
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DailyRewardBanner(
    onClaim: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "reward_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "reward_pulse_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pulseScale),
        colors = CardDefaults.cardColors(containerColor = AccentPrimary.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = onClaim
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Savings,
                    contentDescription = "Daily Reward",
                    tint = AccentPrimary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = "Claim your daily reward!",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = "Tap to earn coins + XP",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = AccentPrimary),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = "Claim",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = BgPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Daily Reward Countdown — Shows time until next claim available
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DailyRewardCountdown(
    countdownText: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "✅",
                fontSize = 20.sp
            )
            Column {
                Text(
                    text = "Daily reward claimed!",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = "Next reward in $countdownText",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Craving Streak Card — Shows craving defeat stats
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CravingStreakCard(
    successfulCount: Int,
    lastCravingTimeAgo: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🔥",
                    fontSize = 24.sp
                )
                Column {
                    Text(
                        text = "$successfulCount cravings defeated",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    if (lastCravingTimeAgo != null) {
                        Text(
                            text = lastCravingTimeAgo,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AccentPrimary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Craving FAB — Floating action button with glow
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CravingFAB(
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fab_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fab_pulse_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fab_glow_alpha"
    )

    Box(contentAlignment = Alignment.Center) {
        // Glow effect
        Canvas(modifier = Modifier.size(80.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AccentOrange.copy(alpha = glowAlpha),
                        AccentOrange.copy(alpha = 0f)
                    ),
                    center = center,
                    radius = size.minDimension / 2
                ),
                radius = size.minDimension / 2
            )
        }

        FloatingActionButton(
            onClick = onClick,
            modifier = Modifier.scale(pulseScale),
            containerColor = AccentOrange,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                hoveredElevation = 0.dp
            )
        ) {
            Text(
                text = "🔥",
                fontSize = 24.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Confetti Message — Shows reward amount with auto-dismiss
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ConfettiMessage(
    coinsAwarded: Int,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(3000)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = AccentPrimary.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Text(
                text = "🎉 Reward Claimed! +$coinsAwarded coins",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                style = MaterialTheme.typography.titleSmall.copy(
                    color = AccentPrimary,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
