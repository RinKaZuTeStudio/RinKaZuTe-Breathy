package breathy.com.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import breathy.com.data.models.AvatarFrame
import breathy.com.data.models.FrameRarity
import breathy.com.data.models.RankTier
import breathy.com.ui.theme.AchievementBronze
import breathy.com.ui.theme.AchievementGold
import breathy.com.ui.theme.AchievementSilver
import breathy.com.ui.theme.BreathyBorders
import breathy.com.ui.theme.BreathyGradients
import breathy.com.ui.theme.BreathyPalette
import breathy.com.ui.theme.DarkBotanical
import breathy.com.ui.theme.DeepForest
import breathy.com.ui.theme.GoldDeep
import breathy.com.ui.theme.MediumSage
import breathy.com.ui.theme.NaturalGreen
import breathy.com.ui.theme.NaturalYellow
import breathy.com.ui.theme.PureWhite
import breathy.com.ui.theme.SoftSand
import breathy.com.ui.theme.SoftSage
import breathy.com.ui.theme.VeryLightSage
import breathy.com.ui.theme.WarmEarth
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The single avatar renderer used across the WHOLE app — profile header,
 * leaderboard, community posts, event participants, friends, chat.
 *
 * Renders the user's photo (or a botanical default) inside the selected
 * [AvatarFrame]. Frames are REAL DESIGNED ARTWORK — layered botanical
 * compositions (branches, leaves, laurels, petals, crests, ornamental
 * metal) with unique silhouettes per frame — never a plain colored ring.
 *
 * Animation tiers (elegant, never casino-flashy):
 * - COMMON / UNCOMMON  → static artwork
 * - RARE               → soft shimmer sweep
 * - EPIC               → shimmer + gentle glow pulse
 * - LEGENDARY          → + orbiting botanical light particles
 * - PREMIUM            → the full treatment: glow, light sweep, particles
 *
 * When [frame] is [AvatarFrame.RANK], the artwork is derived from the
 * user's [RankTier] so the border always reflects real progression.
 */
@Composable
fun BreathyAvatar(
    photoURL: String?,
    frame: AvatarFrame?,
    rankTier: RankTier?,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Avatar",
    cacheBust: Long = 0,
    /** Set false for static contexts (dense lists) to save animation cost. */
    animated: Boolean = true
) {
    val effectiveFrame = frame ?: AvatarFrame.NONE

    // ── Animation phase (only for RARE and above) ──────────────────────────
    val isAnimated = animated && effectiveFrame.rarity.ordinal >= FrameRarity.RARE.ordinal
    val phase: Float = if (isAnimated) {
        val transition = rememberInfiniteTransition(label = "frame_art_${effectiveFrame.id}")
        val duration = when (effectiveFrame.rarity) {
            FrameRarity.RARE -> 6000
            FrameRarity.EPIC -> 4600
            FrameRarity.LEGENDARY -> 3800
            FrameRarity.PREMIUM -> 3200
            else -> 6000
        }
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(duration),
                repeatMode = RepeatMode.Restart
            ),
            label = "frame_phase"
        )
        p
    } else {
        0f
    }

    val ringWidth = ringWidthFor(effectiveFrame, size)

    val description = contentDescription
    val semanticsModifier = if (description != null) {
        Modifier.semantics {
            this[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription] =
                listOf(description)
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .then(semanticsModifier),
        contentAlignment = Alignment.Center
    ) {
        // ── Frame artwork layer — drawn botanical composition ──────────────
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawFrameArtwork(effectiveFrame, rankTier, ringWidth.toPx(), phase)
        }

        // ── Inner avatar circle, inset inside the artwork ──────────────────
        Box(
            modifier = Modifier
                .size(size - (ringWidth * 3.6f))
                .clip(CircleShape)
                .background(BreathyPalette.veryLightSage)
        ) {
            if (!photoURL.isNullOrBlank()) {
                NetworkImage(
                    model = photoURL,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    cacheBust = cacheBust
                )
            } else {
                // Botanical default avatar
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.radialGradient(BreathyPalette.defaultAvatarGradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = contentDescription,
                        tint = BreathyPalette.naturalGreen,
                        modifier = Modifier.fillMaxSize(0.55f)
                    )
                }
            }
        }
    }
}

/**
 * Compact rank badge — tier label + icon, used on the profile screen and
 * next to leaderboard rows. Presentation only; does not alter rank math.
 */
@Composable
fun RankBadge(rankTier: RankTier?, level: Int, modifier: Modifier = Modifier) {
    val tier = rankTier ?: RankTier.forLevel(level)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(BreathyPalette.veryLightSage)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = tier.icon, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(4.dp))
        Text(
            text = tier.label,
            style = MaterialTheme.typography.labelMedium,
            color = BreathyPalette.deepForest,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Lv $level",
            style = MaterialTheme.typography.labelSmall,
            color = BreathyPalette.textSecondary
        )
    }
}

/** Small "Premium" pill used wherever premium state is displayed. */
@Composable
fun PremiumBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Brush.horizontalGradient(BreathyGradients.premium))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "✦", style = MaterialTheme.typography.labelSmall, color = BreathyPalette.warmWhite)
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Premium",
            style = MaterialTheme.typography.labelMedium,
            color = BreathyPalette.warmWhite
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Frame artwork dispatcher — one unique designed composition per frame
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Draw the selected frame's artwork inside the canvas bounds.
 * [phase] (0f..1f) drives shimmer / glow / particle animation for high tiers.
 */
private fun DrawScope.drawFrameArtwork(
    frame: AvatarFrame,
    rankTier: RankTier?,
    ringWidthPx: Float,
    phase: Float
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension / 2f - ringWidthPx * 0.8f

    when (frame) {
        AvatarFrame.NONE -> drawClassicFrame(center, radius, ringWidthPx)

        AvatarFrame.NATURE -> drawNatureFrame(center, radius, ringWidthPx)

        AvatarFrame.LEAF -> drawLeafFrame(center, radius, ringWidthPx)

        AvatarFrame.BRONZE -> drawLaurelFrame(
            center, radius, ringWidthPx, phase,
            ringColor = Color(0xFFB98A5E), deepColor = Color(0xFF8A5F35),
            leafColor = Color(0xFFB98A5E), tipColor = Color(0xFFE0B98A),
            sparkle = false
        )

        AvatarFrame.SILVER -> drawLaurelFrame(
            center, radius, ringWidthPx, phase,
            ringColor = AchievementSilver, deepColor = Color(0xFF7F8B86),
            leafColor = AchievementSilver, tipColor = Color(0xFFE3E9E5),
            sparkle = true
        )

        AvatarFrame.GOLD -> drawGoldCrownFrame(center, radius, ringWidthPx, phase)

        AvatarFrame.ACHIEVEMENT -> drawAchievementFrame(center, radius, ringWidthPx, phase)

        AvatarFrame.EVENT -> drawEventFrame(center, radius, ringWidthPx, phase)

        AvatarFrame.RANK -> drawRankVineFrame(center, radius, ringWidthPx, rankTier)

        AvatarFrame.PREMIUM -> drawPremiumCrestFrame(center, radius, ringWidthPx, phase)
    }
}

/** Ring width scales gently with avatar size; richer frames get more artwork room. */
private fun ringWidthFor(frame: AvatarFrame, size: Dp): Dp {
    val base = when (frame) {
        AvatarFrame.PREMIUM -> 3.0f
        AvatarFrame.EVENT, AvatarFrame.GOLD -> 2.6f
        AvatarFrame.SILVER, AvatarFrame.BRONZE, AvatarFrame.RANK, AvatarFrame.ACHIEVEMENT -> 2.2f
        else -> 1.8f
    }
    return (size.value * 0.045f * base).coerceAtLeast(1.5f).dp
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Shared drawing primitives
// ═══════════════════════════════════════════════════════════════════════════════

/** A single botanical leaf with a subtle vein. */
private fun DrawScope.drawLeaf(
    base: Offset,
    angleDeg: Float,
    length: Float,
    width: Float,
    color: Color,
    veinColor: Color = color.copy(alpha = 0.55f)
) {
    val rad = angleDeg * PI.toFloat() / 180f
    val dir = Offset(cos(rad), sin(rad))
    val perp = Offset(-dir.y, dir.x)
    val tip = base + dir * length
    val c1 = base + dir * (length * 0.5f) + perp * width
    val c2 = base + dir * (length * 0.5f) - perp * width
    val path = Path().apply {
        moveTo(base.x, base.y)
        quadraticBezierTo(c1.x, c1.y, tip.x, tip.y)
        quadraticBezierTo(c2.x, c2.y, base.x, base.y)
        close()
    }
    drawPath(path, color)
    // central vein
    drawLine(
        color = veinColor,
        start = base,
        end = tip,
        strokeWidth = (length * 0.045f).coerceAtLeast(0.6f)
    )
}

/** A curved branch stroke used as the spine of wreaths and crests. */
private fun DrawScope.drawBranch(
    start: Offset,
    end: Offset,
    bow: Float,
    color: Color,
    strokeWidth: Float
) {
    val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
    val dir = end - start
    val perp = Offset(-dir.y, dir.x).let { if (it != Offset.Zero) it / it.getDistance() else it }
    val control = mid + perp * bow
    val path = Path().apply {
        moveTo(start.x, start.y)
        quadraticBezierTo(control.x, control.y, end.x, end.y)
    }
    drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
}

/** Soft radial glow behind the avatar (animated pulse via [phase]). */
private fun DrawScope.drawGlow(
    center: Offset,
    radius: Float,
    color: Color,
    phase: Float,
    maxAlpha: Float = 0.30f
) {
    val pulse = 0.55f + 0.45f * sin(phase * 2f * PI.toFloat())
    val alpha = maxAlpha * pulse
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

/** Light sweep travelling around the ring (elegant, slow). */
private fun DrawScope.drawShimmer(
    center: Offset,
    radius: Float,
    widthPx: Float,
    phase: Float,
    color: Color
) {
    val sweep = 80f
    val start = -90f + phase * 460f
    rotate(degrees = start, pivot = center) {
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(Color.Transparent, color.copy(alpha = 0.85f), Color.Transparent),
                center = center
            ),
            startAngle = -sweep / 2f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = widthPx * 1.15f, cap = StrokeCap.Round)
        )
    }
}

/** Small botanical light particles gently orbiting the frame. */
private fun DrawScope.drawParticles(
    center: Offset,
    radius: Float,
    phase: Float,
    count: Int,
    color: Color
) {
    val twoPi = 2f * PI.toFloat()
    for (i in 0 until count) {
        val a = phase * twoPi + i * twoPi / count
        val wobble = 1f + 0.04f * sin(phase * twoPi * 2f + i)
        val r = radius * wobble
        val p = center + Offset(cos(a) * r, sin(a) * r)
        val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(phase * twoPi * 3f + i * 1.7f))
        drawCircle(
            color = color.copy(alpha = 0.7f * twinkle),
            radius = radius * 0.028f,
            center = p
        )
    }
}

/** Tiny four-point sparkle (for silver/gold ornament). */
private fun DrawScope.drawSparkle(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - size)
        quadraticBezierTo(center.x, center.y, center.x + size, center.y)
        quadraticBezierTo(center.x, center.y, center.x, center.y + size)
        quadraticBezierTo(center.x, center.y, center.x - size, center.y)
        quadraticBezierTo(center.x, center.y, center.x, center.y - size)
        close()
    }
    drawPath(path, color)
}

private fun direction(angleDeg: Float, radius: Float) = Offset(
    cos(angleDeg * PI.toFloat() / 180f) * radius,
    sin(angleDeg * PI.toFloat() / 180f) * radius
)

// ═══════════════════════════════════════════════════════════════════════════════
//  Frame designs — each rarity has its own silhouette and ornament set
// ═══════════════════════════════════════════════════════════════════════════════

/** CLASSIC (Common) — clean minimal ring, quiet by design. */
private fun DrawScope.drawClassicFrame(center: Offset, radius: Float, w: Float) {
    drawCircle(
        brush = Brush.sweepGradient(listOf(SoftSage, MediumSage.copy(alpha = 0.8f), SoftSage)),
        radius = radius,
        center = center,
        style = Stroke(width = w, cap = StrokeCap.Butt)
    )
}

/** NATURE (Common) — soft sage ring with leaf sprigs at the crown. */
private fun DrawScope.drawNatureFrame(center: Offset, radius: Float, w: Float) {
    drawCircle(
        brush = Brush.sweepGradient(
            listOf(SoftSage, MediumSage, NaturalGreen.copy(alpha = 0.75f), MediumSage, SoftSage)
        ),
        radius = radius, center = center,
        style = Stroke(width = w, cap = StrokeCap.Butt)
    )
    // Three small sprigs around the crown
    val angles = listOf(-115f, -90f, -65f)
    angles.forEachIndexed { i, a ->
        val base = center + direction(a, radius - w * 0.2f)
        val angleOut = a + if (i == 1) 0f else (if (i == 0) -22f else 22f)
        drawLeaf(
            base = base, angleDeg = angleOut,
            length = radius * 0.20f, width = radius * 0.075f,
            color = NaturalGreen
        )
    }
}

/** LEAF (Uncommon) — botanical ring wrapped by alternating leaves. */
private fun DrawScope.drawLeafFrame(center: Offset, radius: Float, w: Float) {
    drawCircle(
        brush = Brush.sweepGradient(
            listOf(NaturalGreen, DeepForest, NaturalGreen)
        ),
        radius = radius, center = center,
        style = Stroke(width = w, cap = StrokeCap.Butt)
    )
    val count = 8
    for (i in 0 until count) {
        val a = -90f + i * (360f / count)
        val base = center + direction(a, radius)
        drawLeaf(
            base = base,
            angleDeg = a + (if (i % 2 == 0) 34f else -34f),
            length = radius * 0.22f,
            width = radius * 0.08f,
            color = if (i % 2 == 0) DeepForest else NaturalGreen
        )
    }
}

/**
 * BRONZE / SILVER (Rare) — metallic ring + laurel wreath (open at the crown)
 * + optional sparkles. Real laurel composition, not a color change.
 */
private fun DrawScope.drawLaurelFrame(
    center: Offset,
    radius: Float,
    w: Float,
    phase: Float,
    ringColor: Color,
    deepColor: Color,
    leafColor: Color,
    tipColor: Color,
    sparkle: Boolean
) {
    // Metallic base ring with inner accent line
    drawCircle(
        brush = Brush.sweepGradient(listOf(ringColor, tipColor.copy(alpha = 0.8f), ringColor)),
        radius = radius, center = center,
        style = Stroke(width = w, cap = StrokeCap.Butt)
    )
    drawCircle(
        color = deepColor.copy(alpha = 0.55f),
        radius = radius - w * 1.15f, center = center,
        style = Stroke(width = w * 0.35f)
    )

    // Laurel wreath: two branches rising from the bottom, leaves along the arc
    val leafCount = 6
    // Left branch: from bottom (90°) up to ~200°
    for (i in 0 until leafCount) {
        val a = 115f + i * 13f          // sweep up the left side
        val base = center + direction(a, radius + w * 0.15f)
        drawLeaf(base, a - 40f, radius * 0.17f, radius * 0.062f, leafColor, tipColor.copy(alpha = 0.5f))
    }
    // Right branch
    for (i in 0 until leafCount) {
        val a = 65f - i * 13f
        val base = center + direction(a, radius + w * 0.15f)
        drawLeaf(base, a + 40f, radius * 0.17f, radius * 0.062f, leafColor, tipColor.copy(alpha = 0.5f))
    }

    if (sparkle) {
        // Gentle shimmer + a few sparkles
        drawShimmer(center, radius, w, phase, PureWhite.copy(alpha = 0.55f))
        listOf(-90f, 30f, 150f).forEach { a ->
            drawSparkle(center + direction(a, radius + w * 1.6f), radius * 0.05f, tipColor.copy(alpha = 0.9f))
        }
    }
}

/** GOLD (Epic) — ornamental double ring, laurel, crown peaks and gold shimmer. */
private fun DrawScope.drawGoldCrownFrame(center: Offset, radius: Float, w: Float, phase: Float) {
    val gold = AchievementGold
    val goldDeep = GoldDeep

    // Outer soft glow (pulsing)
    drawGlow(center, radius * 1.45f, gold, phase, maxAlpha = 0.22f)

    // Double ring: broad gold + inner deep-gold line
    drawCircle(
        brush = Brush.sweepGradient(listOf(goldDeep, gold, SoftSand, gold, goldDeep)),
        radius = radius, center = center,
        style = Stroke(width = w, cap = StrokeCap.Butt)
    )
    drawCircle(
        color = goldDeep.copy(alpha = 0.7f),
        radius = radius - w * 1.2f, center = center,
        style = Stroke(width = w * 0.3f)
    )

    // Crown-like ornament: three peaks at the top
    listOf(-22f, 0f, 22f).forEachIndexed { i, off ->
        val a = -90f + off
        val base = center + direction(a, radius + w * 0.1f)
        drawLeaf(base, a, radius * (if (i == 1) 0.22f else 0.16f), radius * 0.06f, gold, goldDeep.copy(alpha = 0.6f))
    }
    // Crown band
    drawArc(
        color = goldDeep,
        startAngle = -125f, sweepAngle = 70f, useCenter = false,
        topLeft = Offset(center.x - radius - w * 0.5f, center.y - radius - w * 0.5f),
        size = Size((radius + w * 0.5f) * 2f, (radius + w * 0.5f) * 2f),
        style = Stroke(width = w * 0.8f, cap = StrokeCap.Round)
    )

    // Laurel leaves on both lower sides
    for (i in 0 until 5) {
        val aL = 112f + i * 14f
        drawLeaf(center + direction(aL, radius + w * 0.15f), aL - 40f, radius * 0.16f, radius * 0.06f, goldDeep, gold.copy(alpha = 0.6f))
        val aR = 68f - i * 14f
        drawLeaf(center + direction(aR, radius + w * 0.15f), aR + 40f, radius * 0.16f, radius * 0.06f, goldDeep, gold.copy(alpha = 0.6f))
    }

    // Dot ornaments at the lower crest
    listOf(90f).forEach { a ->
        drawCircle(goldDeep, radius * 0.045f, center + direction(a, radius + w * 1.2f))
    }

    drawShimmer(center, radius, w, phase, SoftSand)
}

/** ACHIEVEMENT (Rare) — laurel-less star crest: sparkles + ribbon foot. */
private fun DrawScope.drawAchievementFrame(center: Offset, radius: Float, w: Float, phase: Float) {
    drawCircle(
        brush = Brush.sweepGradient(listOf(NaturalGreen, DeepForest, NaturalGreen)),
        radius = radius, center = center,
        style = Stroke(width = w, cap = StrokeCap.Butt)
    )
    // Eight sparkle studs around the ring
    for (i in 0 until 8) {
        val a = -90f + i * 45f
        drawSparkle(center + direction(a, radius + w * 0.9f), radius * 0.055f, NaturalYellow)
    }
    // Ribbon banner at the bottom
    val ribbonY = center.y + radius * 0.86f
    val ribbonW = radius * 0.42f
    val ribbonH = radius * 0.16f
    val ribbon = Path().apply {
        moveTo(center.x - ribbonW, ribbonY)
        lineTo(center.x + ribbonW, ribbonY)
        lineTo(center.x + ribbonW * 0.78f, ribbonY + ribbonH)
        lineTo(center.x - ribbonW * 0.78f, ribbonY + ribbonH)
        close()
    }
    drawPath(ribbon, NaturalYellow)
    drawPath(ribbon, GoldDeep, style = Stroke(width = 1.2f))
    drawShimmer(center, radius, w, phase, PureWhite.copy(alpha = 0.4f))
}

/** EVENT (Legendary) — radiant botanical petals + gold core ring, animated. */
private fun DrawScope.drawEventFrame(center: Offset, radius: Float, w: Float, phase: Float) {
    drawGlow(center, radius * 1.5f, NaturalYellow, phase, maxAlpha = 0.28f)

    // Base ring
    drawCircle(
        brush = Brush.sweepGradient(listOf(DeepForest, NaturalGreen, DeepForest)),
        radius = radius, center = center,
        style = Stroke(width = w, cap = StrokeCap.Butt)
    )

    // Twelve petals radiating outward — unique sunburst silhouette
    val petalCount = 12
    for (i in 0 until petalCount) {
        val a = i * (360f / petalCount)
        val base = center + direction(a, radius)
        drawLeaf(
            base = base, angleDeg = a,
            length = radius * 0.20f, width = radius * 0.07f,
            color = if (i % 2 == 0) DeepForest else NaturalGreen,
            veinColor = NaturalYellow.copy(alpha = 0.7f)
        )
    }

    // Gold inner ring
    drawCircle(
        color = NaturalYellow.copy(alpha = 0.85f),
        radius = radius - w * 1.2f, center = center,
        style = Stroke(width = w * 0.35f)
    )

    drawShimmer(center, radius, w, phase, NaturalYellow)
    drawParticles(center, radius + w * 1.4f, phase, 8, NaturalYellow)
}

/** RANK (Epic) — living vine whose growth mirrors the user's real tier. */
private fun DrawScope.drawRankVineFrame(
    center: Offset,
    radius: Float,
    w: Float,
    rankTier: RankTier?
) {
    val tier = rankTier ?: RankTier.SEED
    val tierRing = when (tier) {
        RankTier.SEED -> listOf(SoftSage, MediumSage, SoftSage)
        RankTier.SPROUT -> listOf(SoftSage, MediumSage, SoftSage)
        RankTier.LEAF -> listOf(MediumSage, NaturalGreen, MediumSage)
        RankTier.PLANT -> listOf(NaturalGreen, DeepForest, NaturalGreen)
        RankTier.TREE -> listOf(DeepForest, DarkBotanical, DeepForest)
        RankTier.FOREST -> listOf(DarkBotanical, WarmEarth, DarkBotanical)
        RankTier.EVERGREEN -> listOf(DarkBotanical, SoftSand, NaturalYellow, DarkBotanical)
    }
    drawCircle(
        brush = Brush.sweepGradient(tierRing),
        radius = radius, center = center,
        style = Stroke(width = w, cap = StrokeCap.Butt)
    )

    // Vine arcs on both upper sides; leaf pairs grow with tier progress
    val pairs = (tier.ordinal + 1).coerceIn(1, 7)
    drawBranch(
        start = center + direction(160f, radius),
        end = center + direction(-110f, radius),
        bow = radius * 0.34f,
        color = DeepForest.copy(alpha = 0.85f),
        strokeWidth = w * 0.5f
    )
    drawBranch(
        start = center + direction(20f, radius),
        end = center + direction(-70f, radius),
        bow = -radius * 0.34f,
        color = DeepForest.copy(alpha = 0.85f),
        strokeWidth = w * 0.5f
    )
    for (i in 0 until pairs) {
        val t = i / 7f
        val aL = 150f - t * 250f
        val aR = 30f + t * 250f
        drawLeaf(center + direction(aL, radius), aL - 46f, radius * 0.13f, radius * 0.05f, NaturalGreen)
        drawLeaf(center + direction(aR, radius), aR + 46f, radius * 0.13f, radius * 0.05f, NaturalGreen)
    }
    // Tier bud at the crown
    drawSparkle(center + direction(-90f, radius + w * 0.8f), radius * 0.05f, tierRing.last())
}

/**
 * PREMIUM — the finest frame in the app: a deep forest botanical crest with
 * gold accents, layered fern artwork, elegant glow, light sweep and fine
 * particles. Refined and natural — never a plain gold circle.
 */
private fun DrawScope.drawPremiumCrestFrame(center: Offset, radius: Float, w: Float, phase: Float) {
    val gold = AchievementGold
    val goldDeep = GoldDeep

    // 1. Elegant breathing glow
    drawGlow(center, radius * 1.55f, NaturalYellow, phase, maxAlpha = 0.30f)

    // 2. Deep forest main ring
    drawCircle(
        brush = Brush.sweepGradient(
            listOf(DeepForest, DarkBotanical, DeepForest, DarkBotanical, DeepForest)
        ),
        radius = radius, center = center,
        style = Stroke(width = w, cap = StrokeCap.Butt)
    )

    // 3. Gold inner filigree line
    drawCircle(
        color = gold.copy(alpha = 0.85f),
        radius = radius - w * 1.25f, center = center,
        style = Stroke(width = w * 0.32f)
    )
    drawCircle(
        color = goldDeep.copy(alpha = 0.5f),
        radius = radius + w * 0.55f, center = center,
        style = Stroke(width = w * 0.22f)
    )

    // 4. Symmetric fern crests — left and right, rising from the foot
    fun fern(side: Float) {
        val startA = 122f * side        // foot side
        val endA = -128f * side         // crown side
        val start = center + direction(startA, radius)
        val end = center + direction(endA, radius)
        drawBranch(start, end, radius * 0.30f * side, DarkBotanical, w * 0.55f)
        // Fern leaflets along the branch
        for (i in 0 until 7) {
            val t = 0.12f + 0.8f * (i / 6f)
            val a = startA + (endA - startA) * t
            val base = center + direction(a, radius)
            val len = radius * (0.13f + 0.05f * (1f - t))
            drawLeaf(base, a + 42f * side, len, radius * 0.045f, DeepForest, goldDeep.copy(alpha = 0.45f))
            drawLeaf(base, a - 42f * side, len * 0.85f, radius * 0.04f, DeepForest, goldDeep.copy(alpha = 0.45f))
        }
        // Gold bud at the crest tip
        drawSparkle(center + direction(endA, radius + w * 0.7f), radius * 0.05f, gold)
    }
    fern(1f)
    fern(-1f)

    // 5. Gold petal accents at the four cardinal points
    listOf(-90f, 0f, 90f, 180f).forEach { a ->
        drawLeaf(
            base = center + direction(a, radius + w * 0.35f),
            angleDeg = a, length = radius * 0.12f, width = radius * 0.042f,
            color = gold, veinColor = goldDeep
        )
    }

    // 6. Fine light sweep + particles (the Premium treatment)
    drawShimmer(center, radius, w, phase, SoftSand)
    drawParticles(center, radius + w * 1.5f, phase, 10, NaturalYellow)
    drawParticles(center, radius + w * 1.9f, phase + 0.5f, 5, PureWhite.copy(alpha = 0.7f))
}
