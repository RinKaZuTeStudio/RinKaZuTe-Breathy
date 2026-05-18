package breathy.com.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import breathy.com.ui.theme.AccentPrimary
import breathy.com.ui.theme.AccentPurple
import breathy.com.ui.theme.AccentSecondary
import breathy.com.ui.theme.themeBgPrimary
import breathy.com.ui.theme.themeBgSurface
import breathy.com.ui.theme.themeTextPrimary
import breathy.com.ui.theme.themeTextSecondary
import kotlinx.coroutines.delay

private enum class BreathingPhase {
    INHALE, HOLD, EXHALE, DONE
}

/**
 * Full-screen breathing exercise using the 4-7-8 technique.
 * The user inhales for 4s, holds for 7s, and exhales for 8s,
 * repeated for 3 cycles. After completion, the user reports
 * whether the craving was defeated.
 *
 * @param onComplete Called with `true` if the craving was defeated, `false` otherwise.
 * @param onCancel   Called when the user cancels the exercise.
 */
@Composable
fun BreathingExercise(
    onComplete: (success: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val totalCycles = 3
    var currentCycle by remember { mutableIntStateOf(1) }
    var currentPhase by remember { mutableStateOf(BreathingPhase.INHALE) }
    var phaseProgress by remember { mutableFloatStateOf(0f) }
    var phaseSecondsLeft by remember { mutableIntStateOf(4) }
    var isRunning by remember { mutableStateOf(true) }
    var isComplete by remember { mutableStateOf(false) }

    val circleRadius by animateFloatAsState(
        targetValue = when (currentPhase) {
            BreathingPhase.INHALE -> 120f
            BreathingPhase.HOLD -> 120f
            BreathingPhase.EXHALE -> 60f
            BreathingPhase.DONE -> 90f
        },
        animationSpec = tween(
            durationMillis = when (currentPhase) {
                BreathingPhase.INHALE -> 4000
                BreathingPhase.HOLD -> 700
                BreathingPhase.EXHALE -> 8000
                BreathingPhase.DONE -> 500
            },
            easing = LinearEasing
        ),
        label = "circle_radius"
    )

    val phaseColor = when (currentPhase) {
        BreathingPhase.INHALE -> AccentPrimary
        BreathingPhase.HOLD -> AccentSecondary
        BreathingPhase.EXHALE -> AccentPurple
        BreathingPhase.DONE -> AccentPrimary
    }

    val phaseDuration = when (currentPhase) {
        BreathingPhase.INHALE -> 4
        BreathingPhase.HOLD -> 7
        BreathingPhase.EXHALE -> 8
        BreathingPhase.DONE -> 0
    }

    val phaseText = when (currentPhase) {
        BreathingPhase.INHALE -> "Breathe In..."
        BreathingPhase.HOLD -> "Hold..."
        BreathingPhase.EXHALE -> "Breathe Out..."
        BreathingPhase.DONE -> "Complete!"
    }

    // Phase timer
    LaunchedEffect(currentPhase, currentCycle) {
        if (!isRunning || currentPhase == BreathingPhase.DONE) return@LaunchedEffect

        phaseSecondsLeft = phaseDuration
        phaseProgress = 0f

        val totalSteps = phaseDuration * 10 // 100ms steps
        for (step in 0..totalSteps) {
            if (!isRunning) break
            phaseProgress = step.toFloat() / totalSteps
            phaseSecondsLeft = phaseDuration - (step / 10)
            if (phaseSecondsLeft < 0) phaseSecondsLeft = 0
            delay(100)
        }

        // Move to next phase
        when (currentPhase) {
            BreathingPhase.INHALE -> {
                currentPhase = BreathingPhase.HOLD
            }
            BreathingPhase.HOLD -> {
                currentPhase = BreathingPhase.EXHALE
            }
            BreathingPhase.EXHALE -> {
                if (currentCycle < totalCycles) {
                    currentCycle++
                    currentPhase = BreathingPhase.INHALE
                } else {
                    currentPhase = BreathingPhase.DONE
                    isComplete = true
                }
            }
            BreathingPhase.DONE -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Close button
            Box(modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = {
                        isRunning = false
                        onCancel()
                    },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel breathing exercise",
                        tint = themeTextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Cycle indicator
                    Text(
                        text = "Cycle $currentCycle of $totalCycles",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = themeTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Animated breathing circle
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(280.dp)
                    ) {
                        // Outer glow
                        Canvas(modifier = Modifier.size(280.dp)) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        phaseColor.copy(alpha = 0.15f),
                                        phaseColor.copy(alpha = 0f)
                                    ),
                                    center = center,
                                    radius = size.minDimension / 2
                                ),
                                radius = size.minDimension / 2,
                                center = center
                            )
                        }

                        // Main circle
                        Canvas(modifier = Modifier.size(260.dp)) {
                            val radiusPx = circleRadius.dp.toPx()
                            val centerOffset = Offset(size.width / 2, size.height / 2)

                            // Filled circle
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        phaseColor.copy(alpha = 0.3f),
                                        phaseColor.copy(alpha = 0.1f)
                                    ),
                                    center = centerOffset,
                                    radius = radiusPx
                                ),
                                radius = radiusPx,
                                center = centerOffset
                            )

                            // Circle border
                            drawCircle(
                                color = phaseColor,
                                radius = radiusPx,
                                center = centerOffset,
                                style = Stroke(width = 3.dp.toPx())
                            )

                            // Progress arc
                            if (currentPhase != BreathingPhase.DONE) {
                                val sweepAngle = phaseProgress * 360f
                                drawArc(
                                    color = phaseColor.copy(alpha = 0.8f),
                                    startAngle = -90f,
                                    sweepAngle = sweepAngle,
                                    useCenter = false,
                                    topLeft = Offset(
                                        centerOffset.x - radiusPx - 8.dp.toPx(),
                                        centerOffset.y - radiusPx - 8.dp.toPx()
                                    ),
                                    size = Size(
                                        (radiusPx + 8.dp.toPx()) * 2,
                                        (radiusPx + 8.dp.toPx()) * 2
                                    ),
                                    style = Stroke(
                                        width = 4.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                )
                            }
                        }

                        // Phase text and countdown
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = phaseText,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = phaseColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                ),
                                textAlign = TextAlign.Center
                            )

                            if (currentPhase != BreathingPhase.DONE) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = phaseSecondsLeft.toString(),
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        color = themeTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 48.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Overall progress
                    val overallProgress = when {
                        currentPhase == BreathingPhase.DONE -> 1f
                        else -> {
                            val completedPhases = (currentCycle - 1) * 3 +
                                    when (currentPhase) {
                                        BreathingPhase.INHALE -> 0
                                        BreathingPhase.HOLD -> 1
                                        BreathingPhase.EXHALE -> 2
                                        BreathingPhase.DONE -> 3
                                    }
                            (completedPhases + phaseProgress) / (totalCycles * 3f)
                        }
                    }

                    LinearProgressIndicator(
                        progress = { overallProgress },
                        modifier = Modifier
                            .width(200.dp)
                            .height(4.dp),
                        color = phaseColor,
                        trackColor = phaseColor.copy(alpha = 0.2f),
                    )

                    // Complete state buttons
                    if (isComplete) {
                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = "Great job! You completed the exercise 🎉",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = themeTextPrimary,
                                fontWeight = FontWeight.Medium
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(
                                onClick = { onComplete(true) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentPrimary,
                                    contentColor = themeBgPrimary
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text(
                                    text = "I feel better 👍",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = { onComplete(false) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentSecondary.copy(alpha = 0.2f),
                                    contentColor = AccentSecondary
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text(
                                    text = "Still craving 😔",
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "+5 XP for completing the exercise",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AccentPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    if (!isComplete) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                isRunning = false
                                onCancel()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = themeTextSecondary
                            )
                        ) {
                            Text(text = "Cancel", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
