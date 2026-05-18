package breathy.com.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Simple Canvas-based confetti animation for celebrations.
 * No external library needed - uses pure Compose Canvas.
 */
object ConfettiHelper {

    val PALETTE_PRIMARY = listOf(
        0xFF00E676.toInt(), // Neon Green
        0xFFFFD740.toInt(), // Gold
        0xFF448AFF.toInt()  // Neon Blue
    )

    val PALETTE_EXTENDED = listOf(
        0xFF00E676.toInt(), 0xFFFFD740.toInt(), 0xFF448AFF.toInt(),
        0xFFB388FF.toInt(), 0xFFFF4081.toInt(), 0xFFFF9100.toInt()
    )

    enum class Preset(
        val displayName: String,
        val description: String,
        val durationMs: Long,
        val colors: List<Int>
    ) {
        SMALL_BURST("Small Burst", "Quick celebration", 1500L, PALETTE_PRIMARY),
        BIG_CELEBRATION("Big Celebration", "Large celebration", 3000L, PALETTE_EXTENDED),
        MILESTONE("Milestone", "Milestone reached", 4000L, PALETTE_PRIMARY),
        ACHIEVEMENT("Achievement", "Achievement unlocked", 2500L, PALETTE_PRIMARY)
    }
}

/**
 * Canvas-based confetti overlay composable.
 */
@Composable
fun BreathyConfetti(
    preset: ConfettiHelper.Preset,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onFinished: (() -> Unit)? = null
) {
    if (!isActive) return

    val particles = remember {
        List(60) { i ->
            ConfettiParticle(
                x = Random.nextFloat(),
                y = Random.nextFloat() * -1f,
                color = Color(preset.colors[i % preset.colors.size]),
                speed = 0.5f + Random.nextFloat() * 1.5f,
                angle = Random.nextFloat() * 360f,
                rotationSpeed = 1f + Random.nextFloat() * 3f,
                width = 4f + Random.nextFloat() * 4f,
                height = 8f + Random.nextFloat() * 8f,
                drift = -0.3f + Random.nextFloat() * 0.6f
            )
        }
    }

    var elapsed by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (elapsed * 16f < preset.durationMs) {
            elapsed += 0.016f
            delay(16)
        }
        onFinished?.invoke()
    }

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        particles.forEach { particle ->
            val currentY = (particle.y + elapsed * particle.speed * 0.5f) % 1.5f
            val currentX = particle.x + sin(elapsed * 2f + particle.angle) * 0.02f + particle.drift * elapsed * 0.01f
            val rotation = (particle.angle + elapsed * particle.rotationSpeed * 60f) % 360f

            val px = (currentX * canvasWidth).coerceIn(0f, canvasWidth)
            val py = currentY * canvasHeight

            if (py in 0f..canvasHeight) {
                val widthPx = particle.width * density
                val heightPx = particle.height * density
                val alpha = 1f - (currentY / 1.5f).coerceIn(0f, 1f) * 0.5f

                rotate(rotation, Offset(px, py)) {
                    drawRect(
                        color = particle.color,
                        topLeft = Offset(px - widthPx / 2, py - heightPx / 2),
                        size = Size(widthPx, heightPx),
                        alpha = alpha
                    )
                }
            }
        }
    }
}

/**
 * Multi-preset confetti composable.
 */
@Composable
fun BreathyMultiConfetti(
    presets: List<ConfettiHelper.Preset>,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onFinished: (() -> Unit)? = null
) {
    BreathyConfetti(
        preset = presets.firstOrNull() ?: ConfettiHelper.Preset.SMALL_BURST,
        isActive = isActive,
        modifier = modifier,
        onFinished = onFinished
    )
}

private data class ConfettiParticle(
    val x: Float,
    val y: Float,
    val color: Color,
    val speed: Float,
    val angle: Float,
    val rotationSpeed: Float,
    val width: Float,
    val height: Float,
    val drift: Float
)
