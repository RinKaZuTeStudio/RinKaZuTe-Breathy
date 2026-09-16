package breathy.com.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import breathy.com.ui.theme.DeepForest
import breathy.com.ui.theme.MediumSage
import breathy.com.ui.theme.NaturalGreen
import breathy.com.ui.theme.NaturalYellow
import breathy.com.ui.theme.SoftSage
import breathy.com.ui.theme.SoftSky
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════════════════════════
//  Breathy motion system
//
//  One shared easing/animation vocabulary so every screen moves the same calm,
//  deliberate way. Built around the app's own identity: the breath.
//    • BreathEasing   — slow, symmetrical in/out (a full breath cycle)
//    • EmphasisEasing — decelerating snap (taps, highlights)
// ═══════════════════════════════════════════════════════════════════════════════

/** Meditative, symmetrical ease — mimics an unhurried breath cycle. */
val BreathEasing: Easing = CubicBezierEasing(0.45f, 0.05f, 0.55f, 0.95f)

/** Decelerating snap for taps and emphasis. */
val EmphasisEasing: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Google brand blue for the sign-in glyph. */
private val GoogleBlue = Color(0xFF4285F4)

// ── Entrance ───────────────────────────────────────────────────────────────────

/**
 * Staggered fade + rise entrance. Elements appear in sequence, each lifting
 * [initialOffsetDp] into place while fading in. Purely visual — layout is
 * unaffected, so scroll and focus behave exactly as before.
 */
@Composable
fun Modifier.entrance(
    index: Int = 0,
    durationMillis: Int = 560,
    stepDelayMillis: Long = 70L,
    startDelayMillis: Long = 0L,
    initialOffsetDp: Float = 22f
): Modifier {
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(startDelayMillis + index * stepDelayMillis)
        played = true
    }
    val progress by animateFloatAsState(
        targetValue = if (played) 1f else 0f,
        animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
        label = "entranceProgress"
    )
    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * initialOffsetDp * density
    }
}

// ── Press feedback ─────────────────────────────────────────────────────────────

/**
 * Springy scale-down while the element is held. [detectTapGestures] with an
 * [onPress] handler does not consume the gesture, so the underlying click
 * still fires normally.
 */
@Composable
fun Modifier.pressScale(
    pressedScale: Float = 0.97f,
    enabled: Boolean = true
): Modifier {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pressScale"
    )
    return this
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                }
            )
        }
        .graphicsLayer { scaleX = scale; scaleY = scale }
}

// ── Error shake ────────────────────────────────────────────────────────────────

/**
 * Horizontal nudge whenever [trigger] changes to a non-null value (e.g. a
 * field error). Re-triggering the same error shakes again.
 */
@Composable
fun Modifier.shakeOnTrigger(
    trigger: Any?,
    amplitude: Float = 9f,
    durationMillis: Int = 420
): Modifier {
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
        animatable.snapTo(0f)
        animatable.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = durationMillis
                0f at 0 with FastOutSlowInEasing
                -amplitude at (durationMillis * 0.14f).toInt()
                (amplitude * 0.72f) at (durationMillis * 0.28f).toInt()
                (-amplitude * 0.48f) at (durationMillis * 0.42f).toInt()
                (amplitude * 0.28f) at (durationMillis * 0.56f).toInt()
                (-amplitude * 0.14f) at (durationMillis * 0.70f).toInt()
                0f at durationMillis
            }
        )
    }
    return graphicsLayer { translationX = animatable.value * density }
}

// ── Idle breathing scale ───────────────────────────────────────────────────────

/** Continuous slow scale oscillation — the app-wide "alive" idle motion. */
@Composable
fun Modifier.breathingScale(
    minScale: Float = 0.94f,
    maxScale: Float = 1.0f,
    cycleMillis: Int = 4400,
    delayMillis: Int = 0
): Modifier {
    val transition = rememberInfiniteTransition(label = "breathingScale")
    val scale by transition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = cycleMillis, delayMillis = delayMillis, easing = BreathEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingScaleValue"
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

// ── Shimmer sweep ──────────────────────────────────────────────────────────────

/** A soft highlight band sweeping across the element (CTA polish). */
@Composable
fun Modifier.shimmerSweep(
    active: Boolean,
    periodMillis: Int = 2400,
    bandWidth: Float = 0.34f,
    highlightAlpha: Float = 0.30f
): Modifier {
    if (!active) return this
    val transition = rememberInfiniteTransition(label = "shimmerSweep")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )
    return drawWithContent {
        drawContent()
        val width = this.size.width
        val center = (progress * 1.35f - 0.2f) * width
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = highlightAlpha),
                    Color.Transparent
                ),
                start = Offset(center - bandWidth * width, 0f),
                end = Offset(center + bandWidth * width, size.height)
            )
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  BreathingOrb — the brand mark. Three concentric elements breathe around a
//  green core; the gold "morning" dot rides the outer ring.
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun BreathingOrb(
    modifier: Modifier = Modifier,
    dotColor: Color = NaturalYellow
) {
    val transition = rememberInfiniteTransition(label = "orb")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4600, easing = BreathEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbBreath"
    )

    val outerScale = lerp(0.90f, 1.06f, breath)
    val midScale = lerp(1.02f, 0.93f, breath)          // counter-phase
    val coreScale = lerp(0.92f, 1.0f, breath)
    val glowAlpha = lerp(0.30f, 0.62f, breath)

    Box(modifier.size(132.dp), contentAlignment = Alignment.Center) {
        // Ambient glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = glowAlpha }
                .blur(34.dp)
                .background(Brush.radialGradient(listOf(SoftSage.copy(alpha = 0.7f), Color.Transparent)))
        )

        // Outer ring — carries the gold dot so it rides the breath
        Box(
            modifier = Modifier.size(104.dp).graphicsLayer {
                scaleX = outerScale; scaleY = outerScale
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(width = 1.5.dp, color = MediumSage, shape = CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = (-4.5).dp)
                    .background(dotColor, CircleShape)
            )
        }

        // Mid ring — counter-phase
        Box(
            modifier = Modifier
                .size(70.dp)
                .graphicsLayer { scaleX = midScale; scaleY = midScale }
                .clip(CircleShape)
                .border(width = 2.dp, color = DeepForest.copy(alpha = 0.85f), shape = CircleShape)
        )

        // Core
        Box(
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer { scaleX = coreScale; scaleY = coreScale }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(NaturalGreen, DeepForest)))
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  BreathingBackdrop — three soft, drifting colour tints for depth. Uses only
//  palette colours, so the identity stays exactly the same.
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun BreathingBackdrop(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "backdrop")
    val driftA by transition.animateFloat(
        0.94f, 1.06f,
        infiniteRepeatable(tween(7000, easing = BreathEasing), RepeatMode.Reverse),
        label = "driftA"
    )
    val driftB by transition.animateFloat(
        1.05f, 0.95f,
        infiniteRepeatable(tween(8400, easing = BreathEasing), RepeatMode.Reverse),
        label = "driftB"
    )
    val driftC by transition.animateFloat(
        0.97f, 1.04f,
        infiniteRepeatable(tween(6200, 400, BreathEasing), RepeatMode.Reverse),
        label = "driftC"
    )

    Box(modifier.fillMaxSize()) {
        Tint(
            alignment = Alignment.TopStart,
            offsetX = (-48).dp, offsetY = (-64).dp,
            size = 240.dp, scale = driftA, blurRadius = 56.dp,
            brush = Brush.radialGradient(listOf(SoftSage.copy(alpha = 0.55f), Color.Transparent))
        )
        Tint(
            alignment = Alignment.TopEnd,
            offsetX = 32.dp, offsetY = (-24).dp,
            size = 200.dp, scale = driftB, blurRadius = 60.dp,
            brush = Brush.radialGradient(listOf(SoftSky.copy(alpha = 0.6f), Color.Transparent))
        )
        Tint(
            alignment = Alignment.BottomCenter,
            offsetX = (-20).dp, offsetY = 80.dp,
            size = 260.dp, scale = driftC, blurRadius = 64.dp,
            brush = Brush.radialGradient(listOf(SoftSage.copy(alpha = 0.35f), Color.Transparent))
        )
    }
}

@Composable
private fun Tint(
    alignment: Alignment,
    offsetX: Dp,
    offsetY: Dp,
    size: Dp,
    scale: Float,
    blurRadius: Dp,
    brush: Brush
) {
    Box(
        modifier = Modifier
            .align(alignment)
            .offset(x = offsetX, y = offsetY)
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = 0.55f }
            .blur(blurRadius)
            .background(brush)
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
//  GoogleG — canvas-drawn "G" glyph in Google blue (no vector asset required).
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun GoogleG(modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val center = Offset(w / 2f, w / 2f)
        val radius = w / 2f - w * 0.115f
        val stroke = w * 0.205f

        drawArc(
            color = GoogleBlue,
            startAngle = 34f,
            sweepAngle = 292f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(
            color = GoogleBlue,
            start = Offset(center.x + radius * 0.96f, center.y),
            end = Offset(center.x - radius * 0.22f, center.y),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
