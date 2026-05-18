@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package breathy.com.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import breathy.com.BreathyApplication
import breathy.com.data.models.Achievement
import breathy.com.data.repository.RewardRepository
import breathy.com.ui.theme.AccentPrimary
import breathy.com.ui.theme.AccentPurple
import breathy.com.ui.theme.themeBgPrimary
import breathy.com.ui.theme.themeBgSurface
import breathy.com.ui.theme.themeBgSurfaceVariant
import breathy.com.ui.theme.themeTextDisabled
import breathy.com.ui.theme.themeTextPrimary
import breathy.com.ui.theme.themeTextSecondary
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

// ═══════════════════════════════════════════════════════════════════════════════
//  AchievementsListScreen — Grid of all achievements with filters
// ═══════════════════════════════════════════════════════════════════════════════

enum class AchievementFilter(val label: String) {
    ALL("All"),
    UNLOCKED("Unlocked"),
    LOCKED("Locked")
}

@Composable
fun AchievementsListScreen(
    onBack: () -> Unit = {},
    viewModel: AchievementsListViewModel = run {
        val context = androidx.compose.ui.platform.LocalContext.current
        val app = context.applicationContext as BreathyApplication
        viewModel(factory = AchievementsListViewModelFactory(
            rewardRepository = app.appModule.rewardRepository,
            auth = app.appModule.firebaseAuth,
            firestore = app.appModule.firestore
        ))
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf(AchievementFilter.ALL) }
    var selectedAchievement by remember { mutableStateOf<Achievement?>(null) }

    DisposableEffect(Unit) {
        Timber.d("AchievementsListScreen: composed")
        onDispose { Timber.d("AchievementsListScreen: disposed") }
    }

    Scaffold(
        containerColor = themeBgPrimary,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Achievements",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = themeTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = themeTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeBgSurface,
                    titleContentColor = themeTextPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Stats Summary ─────────────────────────────────────────────
            val unlockedCount = uiState.achievements.count { it.unlocked }
            val totalCount = uiState.achievements.size
            val totalXpEarned = uiState.achievements.filter { it.unlocked }.sumOf { it.xpReward }

            AchievementStatsBar(
                unlockedCount = unlockedCount,
                totalCount = totalCount,
                totalXpEarned = totalXpEarned
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Filter Chips ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AchievementFilter.entries.forEach { filter ->
                    val count = when (filter) {
                        AchievementFilter.ALL -> totalCount
                        AchievementFilter.UNLOCKED -> unlockedCount
                        AchievementFilter.LOCKED -> totalCount - unlockedCount
                    }
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                text = "${filter.label} ($count)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (selectedFilter == filter) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = AccentPrimary,
                            containerColor = themeBgSurfaceVariant,
                            labelColor = themeTextSecondary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = themeBgSurfaceVariant,
                            selectedBorderColor = AccentPrimary,
                            enabled = true,
                            selected = selectedFilter == filter
                        ),
                        leadingIcon = if (selectedFilter == filter) {
                            {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = AccentPrimary
                                )
                            }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Achievements Grid ─────────────────────────────────────────
            val filteredAchievements = when (selectedFilter) {
                AchievementFilter.ALL -> uiState.achievements
                AchievementFilter.UNLOCKED -> uiState.achievements.filter { it.unlocked }
                AchievementFilter.LOCKED -> uiState.achievements.filter { !it.unlocked }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Loading achievements...",
                            style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary)
                        )
                    }
                }
            } else if (filteredAchievements.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when (selectedFilter) {
                                AchievementFilter.UNLOCKED -> "No achievements unlocked yet"
                                AchievementFilter.LOCKED -> "All achievements unlocked! 🎉"
                                else -> "No achievements found"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(color = themeTextSecondary),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (selectedFilter) {
                                AchievementFilter.UNLOCKED -> "Keep going to earn your first trophy!"
                                AchievementFilter.LOCKED -> "You're amazing!"
                                else -> ""
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = themeTextDisabled,
                                fontSize = 12.sp
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredAchievements, key = { it.id }) { achievement ->
                        AchievementGridCard(
                            achievement = achievement,
                            onClick = { selectedAchievement = achievement }
                        )
                    }
                }
            }
        }
    }

    // ── Achievement Detail Dialog ─────────────────────────────────────────
    selectedAchievement?.let { achievement ->
        AchievementDetailDialog(
            achievement = achievement,
            onDismiss = { selectedAchievement = null }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Stats Bar — Summary of unlocked/total + XP earned
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AchievementStatsBar(
    unlockedCount: Int,
    totalCount: Int,
    totalXpEarned: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = themeBgSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "$unlockedCount / $totalCount",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = AccentPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Achievements Unlocked",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = themeTextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "+$totalXpEarned XP",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = AccentPurple,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "From Achievements",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = themeTextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress bar
            val progress = if (totalCount > 0) unlockedCount.toFloat() / totalCount else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = AccentPrimary,
                trackColor = themeBgSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${(progress * 100).toInt()}% complete",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = themeTextDisabled,
                    fontSize = 11.sp
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Achievement Grid Card — Individual achievement in the grid
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AchievementGridCard(
    achievement: Achievement,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "achievement_glow_${achievement.id}")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_${achievement.id}"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (achievement.unlocked) {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                AccentPrimary.copy(alpha = 0.4f),
                                AccentPurple.copy(alpha = 0.4f)
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (achievement.unlocked) {
                themeBgSurface
            } else {
                themeBgSurface.copy(alpha = 0.6f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Box {
            // Glow effect for unlocked achievements
            if (achievement.unlocked) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                AccentPrimary.copy(alpha = glowAlpha),
                                AccentPrimary.copy(alpha = 0f)
                            ),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.minDimension / 2
                        ),
                        radius = size.minDimension / 2,
                        center = Offset(size.width / 2, size.height / 2)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Achievement icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (achievement.unlocked) {
                                AccentPrimary.copy(alpha = 0.15f)
                            } else {
                                themeBgSurfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = achievement.icon,
                        fontSize = 24.sp,
                        color = if (achievement.unlocked) Color.Unspecified else themeTextDisabled
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Achievement name
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (achievement.unlocked) themeTextPrimary else themeTextDisabled,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // XP reward
                Text(
                    text = "+${achievement.xpReward} XP",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (achievement.unlocked) AccentPurple else themeTextDisabled,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                )

                // Progress bar for partial achievements (for locked ones)
                if (!achievement.unlocked) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val progress = estimateAchievementProgress(achievement)
                    if (progress > 0f) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp)),
                            color = AccentPrimary.copy(alpha = 0.5f),
                            trackColor = themeBgSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = themeTextDisabled,
                                fontSize = 9.sp
                            )
                        )
                    } else {
                        // Locked indicator
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "🔒",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Estimate achievement progress based on the achievement ID.
 * In production, this would use actual user data to compute progress.
 */
private fun estimateAchievementProgress(achievement: Achievement): Float {
    return when (achievement.id) {
        "first_day" -> 0.5f // Would be based on actual days
        "one_week" -> 0.2f
        "one_month" -> 0.1f
        "three_months" -> 0.05f
        "six_months" -> 0.02f
        "one_year" -> 0.01f
        else -> 0f // No progress info for non-numeric achievements
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Achievement Detail Dialog — Shows full info on tap
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AchievementDetailDialog(
    achievement: Achievement,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (achievement.unlocked) AccentPrimary.copy(alpha = 0.15f) else themeBgSurfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = achievement.icon,
                        fontSize = 32.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = if (achievement.unlocked) themeTextPrimary else themeTextDisabled,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = themeTextSecondary
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Status badge
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (achievement.unlocked) {
                            AccentPrimary.copy(alpha = 0.15f)
                        } else {
                            themeBgSurfaceVariant
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (achievement.unlocked) "✅" else "🔒",
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (achievement.unlocked) "Unlocked" else "Locked",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (achievement.unlocked) AccentPrimary else themeTextDisabled,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // XP reward
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Reward:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = themeTextSecondary
                        )
                    )
                    Text(
                        text = "+${achievement.xpReward} XP",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = AccentPurple,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Close",
                    color = AccentPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        containerColor = themeBgSurface,
        titleContentColor = themeTextPrimary,
        textContentColor = themeTextSecondary
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ViewModel
// ═══════════════════════════════════════════════════════════════════════════════

data class AchievementsListUiState(
    val achievements: List<Achievement> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class AchievementsListViewModel(
    private val rewardRepository: RewardRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AchievementsListUiState())
    val uiState: StateFlow<AchievementsListUiState> = _uiState.asStateFlow()

    init {
        loadAchievements()
    }

    private fun loadAchievements() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                rewardRepository.observeUnlockedAchievements(userId).collect { unlocked ->
                    val allWithState = Achievement.ALL_DEFINITIONS.map { def ->
                        def.copy(unlocked = unlocked.any { it.id == def.id })
                    }
                    _uiState.update {
                        it.copy(
                            achievements = allWithState,
                            isLoading = false
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load achievements")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load achievements"
                    )
                }
            }
        }
    }
}

class AchievementsListViewModelFactory(
    private val rewardRepository: RewardRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AchievementsListViewModel(rewardRepository, auth, firestore) as T
    }
}
