package com.breathy.ui.home

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breathy.data.models.HealthMilestone
import com.breathy.ui.theme.AccentPrimary
import com.breathy.ui.theme.themeBgSurface
import com.breathy.ui.theme.themeBgSurfaceVariant
import com.breathy.ui.theme.themeTextDisabled
import com.breathy.ui.theme.themeTextPrimary
import com.breathy.ui.theme.themeTextSecondary
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════════════════════════
//  HealthTimeline — Timeline of health improvements after quitting
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Displays a vertical timeline of health milestones.
 *
 * @param milestones List of milestone-achievement pairs from [UserRepository.getCurrentMilestones].
 * @param modifier   Modifier for the timeline container.
 */
@Composable
fun HealthTimeline(
    milestones: List<Pair<HealthMilestone, Boolean>>,
    modifier: Modifier = Modifier
) {
    var visibleCount by remember { mutableIntStateOf(0) }

    // Staggered entrance animation
    LaunchedEffect(milestones) {
        visibleCount = 0
        milestones.forEachIndexed { index, _ ->
            delay(50L)
            visibleCount = index + 1
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Overall progress bar
        HealthTimelineProgressBar(milestones = milestones)

        Spacer(modifier = Modifier.height(16.dp))

        // Timeline items — use regular Column instead of LazyColumn
        // to avoid crash from nesting LazyColumn inside verticalScroll Column.
        // The milestones list is small (~11 items) so lazy loading is unnecessary.
        Column(modifier = Modifier.fillMaxWidth()) {
            milestones.forEachIndexed { index, (milestone, isAchieved) ->
                AnimatedVisibility(
                    visible = index < visibleCount,
                    enter = fadeIn(animationSpec = tween(300)) +
                            slideInVertically(
                                animationSpec = tween(300),
                                initialOffsetY = { it / 8 }
                            )
                ) {
                    TimelineItem(
                        milestone = milestone,
                        isAchieved = isAchieved,
                        isLast = index == milestones.lastIndex
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Progress bar showing overall timeline position
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun HealthTimelineProgressBar(
    milestones: List<Pair<HealthMilestone, Boolean>>
) {
    val achievedCount = milestones.count { it.second }
    val totalCount = milestones.size
    val progress = if (totalCount > 0) achievedCount.toFloat() / totalCount else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = themeBgSurface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$achievedCount of $totalCount milestones",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = themeTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = AccentPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = AccentPrimary,
                trackColor = themeBgSurfaceVariant,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Individual timeline item
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TimelineItem(
    milestone: HealthMilestone,
    isAchieved: Boolean,
    isLast: Boolean
) {
    val accentColor = if (isAchieved) AccentPrimary else themeTextDisabled
    val textColor = if (isAchieved) themeTextPrimary else themeTextSecondary
    val timeLabel = milestone.timeLabel()

    // Animated checkmark scale for achieved milestones
    var checkScale by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isAchieved) {
        if (isAchieved) {
            checkScale = 0f
            delay(100)
            checkScale = 1.2f
            delay(150)
            checkScale = 1f
        }
    }

    // Pulse glow for achieved items
    val infiniteTransition = rememberInfiniteTransition(label = "check_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "check_glow"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline left column: icon + connector
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(48.dp)
        ) {
            // Icon circle
            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background circle
                Canvas(modifier = Modifier.size(32.dp)) {
                    if (isAchieved) {
                        drawCircle(color = accentColor.copy(alpha = 0.15f))
                        drawCircle(
                            color = accentColor.copy(alpha = glowAlpha),
                            radius = size.minDimension / 2
                        )
                    } else {
                        drawCircle(color = themeBgSurfaceVariant)
                    }
                }

                // Icon
                if (isAchieved) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Achieved",
                        tint = AccentPrimary,
                        modifier = Modifier
                            .size(18.dp)
                            .scale(checkScale)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = themeTextDisabled,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Connector line
            if (!isLast) {
                Canvas(
                    modifier = Modifier
                        .width(2.dp)
                        .height(40.dp)
                ) {
                    drawLine(
                        color = if (isAchieved) AccentPrimary.copy(alpha = 0.4f) else themeBgSurfaceVariant,
                        start = Offset(size.width / 2, 0f),
                        end = Offset(size.width / 2, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }

        // Timeline right column: text content
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 4.dp, bottom = if (isLast) 0.dp else 16.dp)
        ) {
            Text(
                text = "After $timeLabel",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isAchieved) AccentPrimary else themeTextDisabled,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = milestone.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = textColor,
                    fontWeight = if (isAchieved) FontWeight.SemiBold else FontWeight.Normal
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isAchieved && milestone.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = milestone.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = themeTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (!isAchieved) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = milestone.description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = themeTextDisabled,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(0.6f)
                )
            }
        }
    }
}
