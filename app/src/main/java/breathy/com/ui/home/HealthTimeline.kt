package breathy.com.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import breathy.com.data.models.HealthMilestone
import breathy.com.ui.theme.*
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════════════════════════
//  Recovery Journey — the rebuilt health timeline experience (spec section 34)
//
//  Structure: TODAY → CURRENT STAGE → NEXT MILESTONE → RECOVERY JOURNEY.
//  The subtle nature progression metaphor (Seed → Sprout → Leaf → Plant → Tree)
//  frames the SAME scientifically supported health milestones — no invented
//  medical facts, just a calmer way to present real recovery data.
// ═══════════════════════════════════════════════════════════════════════════════

/** A stage of the botanical recovery metaphor, mapped to real smoke-free days. */
private data class RecoveryStage(
    val label: String,
    val emoji: String,
    val maxDay: Int,          // inclusive upper bound of the stage
    val encouragement: String
)

private val RECOVERY_STAGES = listOf(
    RecoveryStage("Seed", "🌰", 1, "You planted the seed. The first hours are the hardest — and you are doing them."),
    RecoveryStage("Sprout", "🌱", 7, "Your first week of fresh air. Early healing is already underway."),
    RecoveryStage("Leaf", "🍃", 30, "New growth every day. Breathing, taste and smell are returning."),
    RecoveryStage("Plant", "🪴", 90, "Strong roots. Circulation and lung function keep improving."),
    RecoveryStage("Tree", "🌳", 365, "A full ring of seasons. Your heart is measurably safer."),
    RecoveryStage("Evergreen", "🌿", Int.MAX_VALUE, "A living testament. Your recovery keeps deepening year after year.")
)

private fun stageForDay(day: Int): RecoveryStage =
    RECOVERY_STAGES.firstOrNull { day <= it.maxDay } ?: RECOVERY_STAGES.last()

/**
 * Recovery Journey — displays TODAY, CURRENT STAGE, NEXT MILESTONE and the
 * full milestone path.
 *
 * @param milestones    milestone-achievement pairs from [UserRepository.getCurrentMilestones].
 * @param daysSmokeFree the user's real smoke-free day count (drives the stage).
 * @param modifier      Modifier for the container.
 */
@Composable
fun HealthTimeline(
    milestones: List<Pair<HealthMilestone, Boolean>>,
    daysSmokeFree: Int = 0,
    modifier: Modifier = Modifier
) {
    var visibleCount by remember { mutableIntStateOf(0) }

    // Staggered entrance animation
    LaunchedEffect(milestones) {
        visibleCount = 0
        milestones.forEachIndexed { index, _ ->
            delay(40L)
            visibleCount = index + 1
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── TODAY ───────────────────────────────────────────────────────
        TodayCard(
            daysSmokeFree = daysSmokeFree,
            milestones = milestones
        )

        // ── CURRENT STAGE ───────────────────────────────────────────────
        CurrentStageCard(daysSmokeFree = daysSmokeFree)

        // ── NEXT MILESTONE ──────────────────────────────────────────────
        milestones.firstOrNull { !it.second }?.let { (next, _) ->
            NextMilestoneCard(
                milestone = next,
                daysSmokeFree = daysSmokeFree
            )
        }

        // ── RECOVERY JOURNEY (full path) ────────────────────────────────
        JourneySectionHeader()
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
//  TODAY — hero summary of where the user stands right now
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TodayCard(
    daysSmokeFree: Int,
    milestones: List<Pair<HealthMilestone, Boolean>>
) {
    val achievedCount = milestones.count { it.second }
    val totalCount = milestones.size
    val overallProgress = if (totalCount > 0) achievedCount.toFloat() / totalCount else 0f
    val stage = stageForDay(daysSmokeFree)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.linearGradient(listOf(DeepForest, DarkBotanical)),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(18.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "TODAY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = SoftSand,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${(overallProgress * 100).toInt()}% recovered",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = NaturalYellow,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Day $daysSmokeFree smoke-free",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = PureWhite,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${stage.emoji} ${stage.label} stage · $achievedCount of $totalCount health milestones reached",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = VeryLightSage
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { overallProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = NaturalYellow,
                    trackColor = DeepForest
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  CURRENT STAGE — the nature metaphor stage with progress to the next
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CurrentStageCard(daysSmokeFree: Int) {
    val stageIndex = RECOVERY_STAGES.indexOfFirst { daysSmokeFree <= it.maxDay }
        .coerceAtLeast(0)
    val stage = RECOVERY_STAGES[stageIndex]
    val nextStage = RECOVERY_STAGES.getOrNull(stageIndex + 1)

    // Progress inside the stage: day span between this stage start and next start
    val stageStart = if (stageIndex == 0) 0 else RECOVERY_STAGES[stageIndex - 1].maxDay + 1
    val stageProgress = if (nextStage == null) 1f
    else {
        val span = (nextStage.maxDay - stageStart + 1).coerceAtLeast(1)
        ((daysSmokeFree - stageStart + 1).toFloat() / span).coerceIn(0f, 1f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = VeryLightSage),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Stage chips: 🌰 → 🌱 → 🍃 → 🪴 → 🌳 → 🌿
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RECOVERY_STAGES.forEachIndexed { i, s ->
                        val reached = i <= stageIndex
                        val isCurrent = i == stageIndex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isCurrent -> DeepForest
                                        reached -> MediumSage.copy(alpha = 0.55f)
                                        else -> PureWhite
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = s.emoji,
                                fontSize = 13.sp,
                                modifier = Modifier.alpha(if (reached) 1f else 0.45f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "CURRENT STAGE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = themeTextSecondary,
                        letterSpacing = 1.5.sp
                    )
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "${stage.emoji} ${stage.label}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = DarkBotanical
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stage.encouragement,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = themeTextSecondary
                )
            )
            if (nextStage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { stageProgress },
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = NaturalGreen,
                        trackColor = SoftSage.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Next: ${nextStage.emoji} ${nextStage.label}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = NaturalGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  NEXT MILESTONE — the next health win and how far away it is
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun NextMilestoneCard(
    milestone: HealthMilestone,
    daysSmokeFree: Int
) {
    val minutes = milestone.minutesAfterQuit
    val daysAway = (minutes / 1440.0) - daysSmokeFree
    val whenText = when {
        daysAway <= 1.0 -> "Arriving within hours"
        daysAway < 30 -> "In ${(kotlin.math.ceil(daysAway)).toInt()} days"
        else -> {
            val months = (daysAway / 30.4)
            if (months < 12) "In about ${kotlin.math.ceil(months).toInt()} months"
            else "In about ${(kotlin.math.ceil(months / 12.0)).toInt()} year(s)"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SoftSage.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(VeryLightSage),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = milestone.icon, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NEXT MILESTONE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = themeTextSecondary,
                            letterSpacing = 1.5.sp
                        )
                    )
                    Text(
                        text = milestone.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = themeTextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = whenText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = NaturalGreen,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.End
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = milestone.description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = themeTextSecondary
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun JourneySectionHeader() {
    Text(
        text = "RECOVERY JOURNEY",
        style = MaterialTheme.typography.labelSmall.copy(
            color = themeTextSecondary,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold
        ),
        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Individual timeline item — milestone card on the journey path
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TimelineItem(
    milestone: HealthMilestone,
    isAchieved: Boolean,
    isLast: Boolean
) {
    val accentColor = if (isAchieved) NaturalGreen else themeTextDisabled
    val textColor = if (isAchieved) themeTextPrimary else themeTextSecondary
    val surfaceVariantColor = themeBgSurfaceVariant
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
    val infiniteTransition = rememberInfiniteTransition(label = "milestone_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "milestone_glow_alpha"
    )
    val nodeAlpha = if (isAchieved) glowAlpha else 0.85f

    Row(modifier = Modifier.fillMaxWidth()) {
        // ── Timeline rail: node + connecting line ───────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(44.dp)
        ) {
            Box(
                modifier = Modifier.size(34.dp),
                contentAlignment = Alignment.Center
            ) {
                // Glow ring
                Canvas(modifier = Modifier.size(34.dp)) {
                    drawCircle(
                        color = if (isAchieved) accentColor else surfaceVariantColor,
                        radius = size.minDimension / 2f,
                        alpha = nodeAlpha * 0.35f
                    )
                }
                // Node circle
                Box(
                    modifier = Modifier
                        .size(if (isAchieved) 26.dp else 22.dp)
                        .scale(checkScale)
                        .clip(CircleShape)
                        .background(if (isAchieved) accentColor else surfaceVariantColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (isAchieved) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Achieved",
                            tint = PureWhite,
                            modifier = Modifier.size(15.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Upcoming",
                            tint = themeTextDisabled,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
            if (!isLast) {
                Canvas(modifier = Modifier.width(3.dp).height(28.dp)) {
                    drawLine(
                        color = if (isAchieved) accentColor else surfaceVariantColor,
                        start = Offset(size.width / 2f, 0f),
                        end = Offset(size.width / 2f, size.height),
                        strokeWidth = size.width
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // ── Milestone card ──────────────────────────────────────────────
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isAchieved) VeryLightSage else PureWhite
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = if (!isAchieved) androidx.compose.foundation.BorderStroke(
                1.dp, SoftSage.copy(alpha = 0.5f)
            ) else null
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = milestone.icon,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = milestone.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = if (isAchieved) DarkBotanical else themeTextPrimary,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isAchieved) NaturalGreen else themeTextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = milestone.description,
                    style = MaterialTheme.typography.bodySmall.copy(color = textColor),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
