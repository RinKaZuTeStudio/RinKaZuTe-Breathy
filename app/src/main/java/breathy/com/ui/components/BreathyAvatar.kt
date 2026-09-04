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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.imageResource
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
 * [AvatarFrame]. Frame artwork is the OFFICIAL "Avatar Borders Collection"
 * (res/drawable-nodpi/frame_*.png) — 4K-designed wreaths, crests and rings.
 *
 * EVERY border has its own signature animation (v1.0.7):
 * - CLASSIC      → soft halo breathing
 * - NATURE       → gentle botanical sway (rotates ±2.4° like leaves in wind)
 * - LEAF         → living scale breathing + green aura
 * - BRONZE       → warm amber aura pulse + bronze shine sweep
 * - SILVER       → cool metal shine sweep + pale aura
 * - GOLD         → golden shine sweep, warm aura, orbiting gold sparks
 * - ACHIEVEMENT  → celebratory star pop + sparkle
 * - EVENT        → counter-rotating gold/red festive arcs + sparks
 * - RANK         → violet energy sweep + aura
 * - PREMIUM      → the full treatment: gold+red counter shimmer, aura, sparks
 *
 * Pass [animated] = false in dense lists (leaderboard) to keep scrolling
 * buttery — the artwork stays, the motion pauses.
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
    animated: Boolean = true,
    /** v1.0.9 unified profile picture id (users.profilePicture). Null = Day One default. */
    profilePictureId: String? = null,
    /** v1.0.9 whether THIS avatar's owner has verified Premium (drives the animated avatar). */
    isPremiumUser: Boolean = false
) {
    val effectiveFrame = frame ?: AvatarFrame.NONE
    val effectivePicture = breathy.com.data.models.ProfilePicture.fromId(profilePictureId)
    // Premium ANIMATED avatar: white flash every 5s alternates the two artworks.
    val useAnimatedAvatar = animated && isPremiumUser && effectivePicture.animated

    // Official frame artwork (Avatar Borders Collection sprite sheet)
    val art: ImageBitmap? = when (effectiveFrame) {
        AvatarFrame.NONE -> ImageBitmap.imageResource(breathy.com.R.drawable.frame_classic)
        AvatarFrame.NATURE -> ImageBitmap.imageResource(breathy.com.R.drawable.frame_nature)
        AvatarFrame.LEAF -> ImageBitmap.imageResource(breathy.com.R.drawable.frame_leaf)
        AvatarFrame.BRONZE -> ImageBitmap.imageResource(breathy.com.R.drawable.frame_bronze)
        AvatarFrame.SILVER -> ImageBitmap.imageResource(breathy.com.R.drawable.frame_silver)
        AvatarFrame.GOLD -> ImageBitmap.imageResource(breathy.com.R.drawable.frame_gold)
        AvatarFrame.ACHIEVEMENT -> ImageBitmap.imageResource(breathy.com.R.drawable.frame_achievement)
        AvatarFrame.EVENT -> ImageBitmap.imageResource(breathy.com.R.drawable.frame_event)
        AvatarFrame.RANK -> ImageBitmap.imageResource(breathy.com.R.drawable.frame_rank)
        AvatarFrame.PREMIUM -> ImageBitmap.imageResource(breathy.com.R.drawable.frame_premium)
    }

    // ── Animation phase (only for RARE and above) ──────────────────────────
    val phase: Float = if (animated) {
        val transition = rememberInfiniteTransition(label = "frame_art_${effectiveFrame.id}")
        val duration = when (effectiveFrame.rarity) {
            FrameRarity.COMMON -> 5200
            FrameRarity.UNCOMMON -> 5200
            FrameRarity.RARE -> 4200
            FrameRarity.EPIC -> 3800
            FrameRarity.LEGENDARY -> 3400
            FrameRarity.PREMIUM -> 3200
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
        // ── Aura layer (UNDER the photo) — soft colored breathing glow ────
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawFrameAura(effectiveFrame, phase)
        }

        // ── Inner avatar circle FIRST — the frame renders IN FRONT of it ──
        // v1.0.10 SIZE FIX — the artwork circle now matches EACH frame's
        // actual inner opening (measured from the official border PNGs'
        // inscribed transparent circle, with a small tuck under the wreath
        // edge). Previously a fixed 0.66 fraction made the artwork spill
        // OUTSIDE decorative frames (nature/event/premium/rank/…) so the
        // square card background showed around the wreath — the "distorted"
        // look. Square art into a square circle via Crop = zero distortion.
        Box(
            modifier = Modifier
                .size(size * frameHoleFraction(effectiveFrame))
                .clip(CircleShape)
                .background(BreathyPalette.veryLightSage)
        ) {
            if (useAnimatedAvatar) {
                // v1.0.10 Premium animated avatar — FINALLY FREE with the
                // white flash sweep every 5 seconds (single artwork).
                AnimatedPremiumAvatar(
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // v1.0.9 UNIFIED PROFILE PICTURE — the official collection artwork
                // is the avatar EVERYWHERE; Day One is the automatic default.
                androidx.compose.foundation.Image(
                    bitmap = ImageBitmap.imageResource(profilePictureRes(effectivePicture)),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.Medium
                )
            }
        }

        // ── FOREGROUND overlay: frame artwork IN FRONT of the photo ────────
        // Drawn LAST so the wreath/ring overlaps the avatar edge — the
        // character sits INSIDE the border (transparent hole shows the face).
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawFrameArtwork(effectiveFrame, rankTier, ringWidth.toPx(), phase, art)
        }
    }
}

/**
 * v1.0.9 — drawable resource for a unified profile picture (Day One default).
 */
private fun profilePictureRes(picture: breathy.com.data.models.ProfilePicture): Int = when (picture) {
    breathy.com.data.models.ProfilePicture.DAY1 -> breathy.com.R.drawable.pic_day1
    breathy.com.data.models.ProfilePicture.SEVEN_DAYS -> breathy.com.R.drawable.pic_7days
    breathy.com.data.models.ProfilePicture.THIRTY_DAYS -> breathy.com.R.drawable.pic_30days
    breathy.com.data.models.ProfilePicture.NINETY_DAYS -> breathy.com.R.drawable.pic_90days
    breathy.com.data.models.ProfilePicture.SUNRISE -> breathy.com.R.drawable.pic_sunrise
    breathy.com.data.models.ProfilePicture.DONT_SMOKE -> breathy.com.R.drawable.pic_dontsmoke
    breathy.com.data.models.ProfilePicture.FORREST -> breathy.com.R.drawable.pic_forrest
    breathy.com.data.models.ProfilePicture.FRESH_BREATH -> breathy.com.R.drawable.pic_freshbreath
    breathy.com.data.models.ProfilePicture.GOOD_FROM_BAD -> breathy.com.R.drawable.pic_goodfrombad
    breathy.com.data.models.ProfilePicture.HEALTH_HEART -> breathy.com.R.drawable.pic_healthheart
    breathy.com.data.models.ProfilePicture.HEALTH_LUNGS -> breathy.com.R.drawable.pic_healthlungs
    breathy.com.data.models.ProfilePicture.HEALTHY_FUTURE -> breathy.com.R.drawable.pic_healthyfuture
    breathy.com.data.models.ProfilePicture.FINALLY_FREE -> breathy.com.R.drawable.pic_finallyfree
}

/**
 * v1.0.10 — fraction of the avatar canvas that the artwork circle occupies,
 * matched to each official border artwork's real inner opening (the largest
 * transparent circle centered in the frame PNG, plus ~12% tuck so the art
 * edge hides beneath the wreath instead of floating with a gap). Measured
 * offline from the shipped 512×512 frame PNGs — values are static because
 * the frame collection is bundled with the app.
 */
private fun frameHoleFraction(frame: breathy.com.data.models.AvatarFrame): Float = when (frame) {
    breathy.com.data.models.AvatarFrame.NONE -> 0.551f        // classic thin ring
    breathy.com.data.models.AvatarFrame.NATURE -> 0.494f      // leaf wreath
    breathy.com.data.models.AvatarFrame.LEAF -> 0.509f        // laurel
    breathy.com.data.models.AvatarFrame.BRONZE -> 0.499f      // medal ring
    breathy.com.data.models.AvatarFrame.SILVER -> 0.529f      // medal ring
    breathy.com.data.models.AvatarFrame.GOLD -> 0.507f        // star medal
    breathy.com.data.models.AvatarFrame.ACHIEVEMENT -> 0.341f // gem wreath
    breathy.com.data.models.AvatarFrame.EVENT -> 0.416f       // radiant wreath
    breathy.com.data.models.AvatarFrame.PREMIUM -> 0.341f     // ornate gem wreath
    breathy.com.data.models.AvatarFrame.RANK -> 0.346f        // crystal wreath
}

/**
 * v1.0.10 PREMIUM ANIMATED AVATAR — FINALLY FREE. Not a heavy animation:
 * every FIVE seconds a white flash sweeps in over the artwork and sweeps
 * away again. (v1.0.9's two-artwork swap is gone — freefromthechain was
 * removed from the collection; the flash stays as the signature motion.)
 */
@Composable
private fun AnimatedPremiumAvatar(
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    var flashAlpha by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5_000L)
            // Flash in (350ms)
            androidx.compose.animation.core.animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.tween(350)
            ) { value, _ -> flashAlpha = value }
            // Flash away (350ms)
            androidx.compose.animation.core.animate(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.tween(350)
            ) { value, _ -> flashAlpha = value }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(
            bitmap = ImageBitmap.imageResource(breathy.com.R.drawable.pic_finallyfree),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Medium
        )
        if (flashAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha))
            )
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
            text = tier.displayLabel(),
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
    phase: Float,
    art: ImageBitmap?
) {
    if (art == null) {
        drawLegacyFrameArtwork(frame, rankTier, ringWidthPx, phase)
        return
    }
    drawOfficialFrameArt(frame, ringWidthPx, phase, art)
}

/** Pre-v1.0.7 programmatic frames — kept as a defensive fallback. */
private fun DrawScope.drawLegacyFrameArtwork(
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

// ═══════════════════════════════════════════════════════════════════════════════
//  Official frame art (Avatar Borders Collection) + signature animations
// ═══════════════════════════════════════════════════════════════════════════════

/** Aura/glow color per frame — matched to each wreath's palette. */
private val FrameAuraColors = mapOf(
    AvatarFrame.NONE to Color(0xFF9BA88F),
    AvatarFrame.NATURE to Color(0xFF8B9E6A),
    AvatarFrame.LEAF to Color(0xFF6FA84E),
    AvatarFrame.BRONZE to Color(0xFFC89058),
    AvatarFrame.SILVER to Color(0xFFCBD5DF),
    AvatarFrame.GOLD to Color(0xFFF0B93B),
    AvatarFrame.ACHIEVEMENT to Color(0xFF5FA84E),
    AvatarFrame.EVENT to Color(0xFFF0B93B),
    AvatarFrame.RANK to Color(0xFF9B6BE0),
    AvatarFrame.PREMIUM to Color(0xFFE86A5B)
)

/**
 * Soft colored aura glow drawn UNDER the photo — the breathing backdrop that
 * makes the foreground frame art pop. Runs on its own canvas layer.
 */
private fun DrawScope.drawFrameAura(frame: AvatarFrame, phase: Float) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val ringRadius = size.minDimension / 2f * 0.86f
    val aura = FrameAuraColors[frame] ?: Color(0xFF9BA88F)
    val auraMax = when (frame) {
        AvatarFrame.NONE -> 0.14f
        AvatarFrame.NATURE, AvatarFrame.LEAF -> 0.20f
        AvatarFrame.BRONZE, AvatarFrame.SILVER -> 0.24f
        AvatarFrame.RANK, AvatarFrame.ACHIEVEMENT -> 0.26f
        else -> 0.32f // GOLD / EVENT / PREMIUM
    }
    drawGlow(center, ringRadius * 1.12f, aura, phase, maxAlpha = auraMax)
}

/**
 * Draw the official frame artwork with its SIGNATURE animation:
 * motion of the art itself (sway / breathe / pop) and light effects over it
 * (shine sweeps, sparkles). Rendered as the FOREGROUND overlay — in front
 * of the avatar photo.
 */
private fun DrawScope.drawOfficialFrameArt(
    frame: AvatarFrame,
    ringWidthPx: Float,
    phase: Float,
    art: ImageBitmap
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val ringRadius = size.minDimension / 2f * 0.86f
    val tau = (2.0 * PI).toFloat()
    val pulse = sin(phase * tau)

    // ── artwork with per-frame motion ────────────────────────────────────
    when (frame) {
        // Nature: gentle botanical sway, like leaves in a breeze
        AvatarFrame.NATURE -> rotate(degrees = 2.4f * pulse, pivot = center) {
            drawArtScaled(art)
        }
        // Leaf: living scale breathing
        AvatarFrame.LEAF -> {
            val s = 1f + 0.018f * pulse
            withTransform({ scale(s, s, center) }) { drawArtScaled(art) }
        }
        // Achievement: celebratory star pop once per cycle
        AvatarFrame.ACHIEVEMENT -> {
            val pop = maxOf(0f, sin(phase * tau))
            val s = 1f + 0.035f * pop * pop * pop
            withTransform({ scale(s, s, center) }) { drawArtScaled(art) }
        }
        else -> drawArtScaled(art)
    }

    // ── signature light effects (over the artwork) ───────────────────────
    when (frame) {
        AvatarFrame.BRONZE -> shineArc(
            center, ringRadius, ringWidthPx, phase,
            Color(0xFFE8C79A), alpha = 0.55f
        )
        AvatarFrame.SILVER -> shineArc(
            center, ringRadius, ringWidthPx, phase,
            Color(0xFFFFFFFF), alpha = 0.75f, sweep = 60f
        )
        AvatarFrame.GOLD -> {
            shineArc(center, ringRadius, ringWidthPx, phase, Color(0xFFFFE08A), alpha = 0.85f, sweep = 75f)
            drawSparkles(center, ringRadius, phase, 3, Color(0xFFFFD65E))
        }
        AvatarFrame.ACHIEVEMENT -> drawSparkles(
            center, ringRadius, phase, 1, Color(0xFFFFE08A), big = true
        )
        AvatarFrame.EVENT -> {
            shineArc(center, ringRadius, ringWidthPx, phase, Color(0xFFFFE08A), alpha = 0.8f, sweep = 70f)
            shineArc(center, ringRadius, ringWidthPx, phase, Color(0xFFFF7A66), alpha = 0.7f, sweep = 55f, offset = 180f, dir = -1f)
            drawSparkles(center, ringRadius, phase, 4, Color(0xFFFFE08A))
        }
        AvatarFrame.RANK -> {
            shineArc(center, ringRadius, ringWidthPx, phase, Color(0xFFCBA8FF), alpha = 0.8f, sweep = 85f)
            drawSparkles(center, ringRadius, phase, 2, Color(0xFFD9BCFF))
        }
        AvatarFrame.PREMIUM -> {
            shineArc(center, ringRadius, ringWidthPx, phase, Color(0xFFFFE08A), alpha = 0.85f, sweep = 70f)
            shineArc(center, ringRadius, ringWidthPx, phase, Color(0xFFFF6F61), alpha = 0.75f, sweep = 50f, offset = 140f, dir = -1f)
            drawSparkles(center, ringRadius, phase, 4, Color(0xFFFFE08A))
        }
        else -> {}
    }
}

/** Draw the artwork bitmap scaled to fill the canvas. */
private fun DrawScope.drawArtScaled(art: ImageBitmap) {
    drawImage(
        image = art,
        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
        srcSize = androidx.compose.ui.unit.IntSize(art.width, art.height),
        dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
        dstSize = androidx.compose.ui.unit.IntSize(
            size.width.toInt().coerceAtLeast(1),
            size.height.toInt().coerceAtLeast(1)
        ),
        filterQuality = FilterQuality.High
    )
}

/** A soft light arc sweeping around the ring — the frame's "shine". */
private fun DrawScope.shineArc(
    center: Offset,
    radius: Float,
    widthPx: Float,
    phase: Float,
    color: Color,
    alpha: Float,
    sweep: Float = 70f,
    offset: Float = 0f,
    dir: Float = 1f
) {
    val start = -90f + dir * phase * 360f + offset
    rotate(degrees = start, pivot = center) {
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(Color.Transparent, color.copy(alpha = alpha), Color.Transparent),
                center = center
            ),
            startAngle = -sweep / 2f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = (widthPx * 1.4f).coerceAtLeast(2f), cap = StrokeCap.Round)
        )
    }
}

/** Small twinkling glints orbiting the ring. */
private fun DrawScope.drawSparkles(
    center: Offset,
    radius: Float,
    phase: Float,
    count: Int,
    color: Color,
    dir: Float = 1f,
    big: Boolean = false
) {
    val baseR = size.minDimension * if (big) 0.026f else 0.016f
    repeat(count) { i ->
        val ang = (dir * phase * 360f) + i * (360f / count) - 90f
        val rad = ang * PI.toFloat() / 180f
        val pos = center + Offset(cos(rad), sin(rad)) * radius
        val tw = 0.5f + 0.5f * sin((phase * 2f + i) * PI.toFloat()).toFloat()
        drawCircle(
            color = color.copy(alpha = 0.25f + 0.75f * tw),
            radius = baseR * (0.7f + 0.7f * tw),
            center = pos
        )
        val arm = baseR * 1.7f * (0.6f + 0.6f * tw)
        drawLine(
            color = color.copy(alpha = 0.45f * tw),
            start = pos - Offset(arm, 0f),
            end = pos + Offset(arm, 0f),
            strokeWidth = 1.1f
        )
        drawLine(
            color = color.copy(alpha = 0.45f * tw),
            start = pos - Offset(0f, arm),
            end = pos + Offset(0f, arm),
            strokeWidth = 1.1f
        )
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

// ═══════════════════════════════════════════════════════════════════════════════
//  FramedStoryAvatar — community feed avatar with the author's REAL equipped
//  frame (spec section 40). Observes the author's public profile live, so a
//  frame change propagates to the feed without restarting the app.
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun FramedStoryAvatar(
    userId: String,
    photoURL: String?,
    nickname: String,
    size: androidx.compose.ui.unit.Dp,
    userRepository: breathy.com.data.repository.UserRepository,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    animated: Boolean = false
) {
    val profile by remember(userId) {
        userRepository.observePublicProfile(userId)
    }.collectAsState(initial = null)

    BreathyAvatar(
        photoURL = photoURL ?: profile?.photoURL,
        frame = profile?.let { AvatarFrame.fromId(it.avatarFrame) },
        rankTier = profile?.let { RankTier.forLevel(breathy.com.data.models.User.computeLevel(it.xp)) },
        size = size,
        modifier = modifier,
        contentDescription = contentDescription ?: "$nickname's avatar",
        animated = animated,
        profilePictureId = profile?.profilePicture,
        isPremiumUser = profile?.premium == true
    )
}
