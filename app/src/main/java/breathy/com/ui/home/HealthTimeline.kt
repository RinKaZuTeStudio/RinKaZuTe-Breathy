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

    val currentIndex = milestones.indexOfFirst { !it.second }

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

        // ── RECOVERY PATH (grouped journey, not a flat checklist) ───────
        JourneySectionHeader(achievedCount = milestones.count { it.second }, total = milestones.size)

        // Chapter the milestones into the botanical stages so the path reads
        // as a journey through chapters — Seed → Sprout → Leaf → Plant → …
        val stageGroups = remember(milestones) { groupMilestonesByStage(milestones) }

        Column(modifier = Modifier.fillMaxWidth()) {
            stageGroups.forEachIndexed { groupIndex, group ->
                val groupVisible = visibleCount >= group.firstMilestoneIndex + 1
                AnimatedVisibility(
                    visible = groupVisible,
                    enter = fadeIn(animationSpec = tween(300))
                ) {
                    Column {
                        StageChapterHeader(
                            stage = group.stage,
                            isCurrentStage = daysSmokeFree <= group.stage.maxDay &&
                                (groupIndex == 0 || daysSmokeFree > stageGroups[groupIndex - 1].stage.maxDay)
                        )
                        Column(modifier = Modifier.fillMaxWidth()) {
                            group.items.forEachIndexed { itemIndex, (milestone, isAchieved) ->
                                val flatIndex = group.firstMilestoneIndex + itemIndex
                                AnimatedVisibility(
                                    visible = flatIndex < visibleCount,
                                    enter = fadeIn(animationSpec = tween(300)) +
                                            slideInVertically(
                                                animationSpec = tween(300),
                                                initialOffsetY = { it / 8 }
                                            )
                                ) {
                                    JourneyMilestoneCard(
                                        milestone = milestone,
                                        isAchieved = isAchieved,
                                        isCurrent = flatIndex == currentIndex,
                                        isLast = flatIndex == milestones.lastIndex
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  Journey chapters — milestones grouped by botanical stage
// ═══════════════════════════════════════════════════════════════════════════

private data class JourneyStageGroup(
    val stage: RecoveryStage,
    val items: List<Pair<HealthMilestone, Boolean>>,
    val firstMilestoneIndex: Int
)

private fun groupMilestonesByStage(
    milestones: List<Pair<HealthMilestone, Boolean>>
): List<JourneyStageGroup> {
    val groups = mutableListOf<JourneyStageGroup>()
    milestones.forEachIndexed { index, pair ->
        val day = (pair.first.minutesAfterQuit / 1440.0).toInt()
        val stage = stageForDay(day)
        val last = groups.lastOrNull()
        if (last != null && last.stage == stage) {
            groups[groups.lastIndex] = last.copy(items = last.items + pair)
        } else {
            groups.add(JourneyStageGroup(stage, listOf(pair), index))
        }
    }
    return groups
}

/** Chapter header — the name of the current chapter of the journey. */
@Composable
private fun StageChapterHeader(stage: RecoveryStage, isCurrentStage: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = stage.emoji, fontSize = 14.sp)
        Text(
            text = stage.label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (isCurrentStage) DeepForest else themeTextSecondary,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            )
        )
        if (isCurrentStage) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DeepForest)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "CURRENT CHAPTER",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = NaturalYellow,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                )
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
private fun JourneySectionHeader(achievedCount: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "RECOVERY PATH",
            style = MaterialTheme.typography.labelSmall.copy(
                color = themeTextSecondary,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(start = 4.dp)
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "$achievedCount of $total reached",
            style = MaterialTheme.typography.labelSmall.copy(
                color = NaturalGreen,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(end = 4.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Journey milestone card — three clearly DIFFERENT states:
//  REACHED (soft sage, glow, check) / YOU ARE HERE (hero gradient card) /
//  UPCOMING (quiet, dimmed). No two adjacent cards look the same.
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun JourneyMilestoneCard(
    milestone: HealthMilestone,
    isAchieved: Boolean,
    isCurrent: Boolean,
    isLast: Boolean
) {
    val timeLabel = milestone.timeLabel()
    val surfaceVariantColor = themeBgSurfaceVariant

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

    // Pulse glow for the current milestone
    val infiniteTransition = rememberInfiniteTransition(label = "journey_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "journey_glow_alpha"
    )

    Row(modifier = Modifier.fillMaxWidth()) {
        // ── Journey rail: node + connecting vine line ───────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(48.dp)
        ) {
            Box(
                modifier = Modifier.size(38.dp),
                contentAlignment = Alignment.Center
            ) {
                // Glow halo (reached or current)
                Canvas(modifier = Modifier.size(38.dp)) {
                    val haloColor = when {
                        isCurrent -> NaturalYellow
                        isAchieved -> NaturalGreen
                        else -> surfaceVariantColor
                    }
                    drawCircle(
                        color = haloColor,
                        radius = size.minDimension / 2f,
                        alpha = if (isCurrent) glowAlpha * 0.5f else if (isAchieved) glowAlpha * 0.35f else 0f
                    )
                }
                // Node circle
                Box(
                    modifier = Modifier
                        .size(
                            when {
                                isCurrent -> 30.dp
                                isAchieved -> 26.dp
                                else -> 20.dp
                            }
                        )
                        .scale(checkScale)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCurrent -> DeepForest
                                isAchieved -> NaturalGreen
                                else -> surfaceVariantColor
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isCurrent -> Text(text = milestone.icon, fontSize = 13.sp)
                        isAchieved -> Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Reached",
                            tint = PureWhite,
                            modifier = Modifier.size(15.dp)
                        )
                        else -> Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Upcoming",
                            tint = themeTextDisabled,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
            if (!isLast) {
                Canvas(modifier = Modifier.width(3.dp).height(30.dp)) {
                    drawLine(
                        color = if (isAchieved) NaturalGreen else surfaceVariantColor,
                        start = Offset(size.width / 2f, 0f),
                        end = Offset(size.width / 2f, size.height),
                        strokeWidth = size.width
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // ── Card, per state ─────────────────────────────────────────────
        when {
            // ── YOU ARE HERE — hero card, most prominent ──────────────────
            isCurrent -> {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                brush = Brush.linearGradient(listOf(DeepForest, DarkBotanical)),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "YOU ARE HERE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = NaturalYellow,
                                        letterSpacing = 2.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "at $timeLabel",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = VeryLightSage,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = milestone.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = PureWhite,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = milestone.description,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = VeryLightSage
                                ),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // ── REACHED — soft sage card with glow + check ────────────────
            isAchieved -> {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = VeryLightSage),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = milestone.icon, fontSize = 17.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = milestone.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = DarkBotanical,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NaturalGreen.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "REACHED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = NaturalGreen,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = milestone.description,
                            style = MaterialTheme.typography.bodySmall.copy(color = themeTextSecondary),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // ── UPCOMING — quiet, dimmed, clearly not yet ─────────────────
            else -> {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 12.dp)
                        .alpha(0.85f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, SoftSage.copy(alpha = 0.55f)
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = milestone.icon,
                                fontSize = 17.sp,
                                modifier = Modifier.alpha(0.6f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = milestone.title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = themeTextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = timeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = themeTextDisabled,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = milestone.description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = themeTextDisabled
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
