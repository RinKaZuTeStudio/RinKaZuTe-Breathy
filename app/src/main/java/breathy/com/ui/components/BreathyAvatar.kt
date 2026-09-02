package breathy.com.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import breathy.com.data.models.AvatarFrame
import breathy.com.data.models.RankTier
import breathy.com.ui.theme.BreathyBorders
import breathy.com.ui.theme.BreathyGradients
import breathy.com.ui.theme.BreathyPalette

/**
 * The single avatar renderer used across the WHOLE app — profile header,
 * leaderboard, community posts, event participants, friends, chat.
 *
 * Renders the user's photo (or a botanical default) inside the selected
 * [AvatarFrame] ring, guaranteeing that the persisted avatar + frame appear
 * consistently everywhere. When [frame] is [AvatarFrame.RANK], the ring is
 * derived from the user's [RankTier] so the border always reflects real
 * progression.
 */
@Composable
fun BreathyAvatar(
    photoURL: String?,
    frame: AvatarFrame?,
    rankTier: RankTier?,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Avatar",
    cacheBust: Long = 0
) {
    val ringBrush = frameBrush(frame, rankTier)
    val ringWidth = ringWidthFor(frame, size)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Frame ring — drawn slightly larger than the avatar itself
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawRing(ringBrush, ringWidth),
            contentAlignment = Alignment.Center
        ) {
            // Inner avatar circle, inset by the ring width
            Box(
                modifier = Modifier
                    .size(size - (ringWidth * 2.4f))
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
}

/** Ring width scales gently with avatar size; premium/event frames are slightly richer. */
private fun ringWidthFor(frame: AvatarFrame?, size: Dp): Dp {
    val base = when (frame) {
        AvatarFrame.PREMIUM, AvatarFrame.EVENT, AvatarFrame.GOLD -> 2.6f
        AvatarFrame.SILVER, AvatarFrame.BRONZE, AvatarFrame.ACHIEVEMENT -> 2.2f
        else -> 1.8f
    }
    return (size.value * 0.045f * base).coerceAtLeast(1.5f).dp
}

/** Gradient/donut colors for each frame, aligned with the Sage Nature palette. */
@Composable
private fun frameBrush(frame: AvatarFrame?, rankTier: RankTier?): Brush {
    val effectiveFrame = if (frame == AvatarFrame.RANK) {
        // Rank frame → ring follows the current tier colors
        null
    } else {
        frame
    }
    return when (effectiveFrame) {
        AvatarFrame.NATURE -> Brush.sweepGradient(
            listOf(BreathyPalette.softSage, BreathyPalette.mediumSage, BreathyPalette.softSage)
        )
        AvatarFrame.LEAF -> Brush.sweepGradient(
            listOf(BreathyPalette.naturalGreen, BreathyPalette.deepForest, BreathyPalette.naturalGreen)
        )
        AvatarFrame.BRONZE -> Brush.sweepGradient(
            listOf(BreathyPalette.warmEarth, Color(0xFFD9B48F), BreathyPalette.warmEarth)
        )
        AvatarFrame.SILVER -> Brush.sweepGradient(
            listOf(Color(0xFF9AA5A1), Color(0xFFD7DDD9), Color(0xFF9AA5A1))
        )
        AvatarFrame.GOLD, AvatarFrame.ACHIEVEMENT -> Brush.sweepGradient(
            listOf(Color(0xFFD9B45B), BreathyPalette.naturalYellow, Color(0xFFD9B45B))
        )
        AvatarFrame.PREMIUM -> Brush.sweepGradient(
            listOf(BreathyPalette.deepForest, BreathyPalette.softSand, BreathyPalette.deepForest)
        )
        AvatarFrame.EVENT -> Brush.sweepGradient(
            listOf(BreathyPalette.naturalGreen, BreathyPalette.naturalYellow, BreathyPalette.naturalGreen)
        )
        else -> rankTierBrush(rankTier)
    }
}

/** Rank-tier ring colors for [AvatarFrame.RANK] and null frames. */
@Composable
private fun rankTierBrush(rankTier: RankTier?): Brush = when (rankTier) {
    null, RankTier.SEED -> Brush.sweepGradient(listOf(BreathyPalette.softSage, BreathyPalette.softSage))
    RankTier.SPROUT -> Brush.sweepGradient(listOf(BreathyPalette.softSage, BreathyPalette.mediumSage, BreathyPalette.softSage))
    RankTier.LEAF -> Brush.sweepGradient(listOf(BreathyPalette.mediumSage, BreathyPalette.naturalGreen, BreathyPalette.mediumSage))
    RankTier.PLANT -> Brush.sweepGradient(listOf(BreathyPalette.naturalGreen, BreathyPalette.deepForest, BreathyPalette.naturalGreen))
    RankTier.TREE -> Brush.sweepGradient(listOf(BreathyPalette.deepForest, BreathyPalette.darkBotanical, BreathyPalette.deepForest))
    RankTier.FOREST -> Brush.sweepGradient(listOf(BreathyPalette.darkBotanical, BreathyPalette.warmEarth, BreathyPalette.darkBotanical))
    RankTier.EVERGREEN -> Brush.sweepGradient(
        listOf(BreathyPalette.darkBotanical, BreathyPalette.softSand, BreathyPalette.naturalYellow, BreathyPalette.darkBotanical)
    )
}

/** Draws a full donut ring behind the avatar content. */
private fun Modifier.drawRing(brush: Brush, width: Dp): Modifier = this.then(
    Modifier.drawBehind {
        val stroke = width.toPx()
        val radius = (size.minDimension - stroke) / 2f
        drawCircle(
            brush = brush,
            radius = radius,
            center = Offset(size.width / 2f, size.height / 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
    }
)

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
            .border(BreathyBorders.subtle, CircleShape)
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
            .background(Brush.linearGradient(BreathyGradients.premium))
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
