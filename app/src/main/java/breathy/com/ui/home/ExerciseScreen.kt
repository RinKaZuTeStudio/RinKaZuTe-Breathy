package breathy.com.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import breathy.com.ui.theme.themeBgPrimary
import breathy.com.ui.theme.themeTextPrimary
import breathy.com.ui.theme.themeTextSecondary
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════════════════════════
//  ExerciseWorkout — Real guided workouts for the Craving coping exercises
//  Flow: 5s "Get Ready" countdown → guided workout (reps or hold) → Done 🎉
// ═══════════════════════════════════════════════════════════════════════════════

/** The three guided exercises offered in the Craving coping sheet. */
enum class ExerciseType(
    val title: String,
    val targetReps: Int?,
    val holdSeconds: Int?,
    val accent: Color,
    val prepInstruction: String,
    val workingInstruction: String
) {
    PUSHUPS(
        title = "Pushups",
        targetReps = 10,
        holdSeconds = null,
        accent = Color(0xFF00BCD4),
        prepInstruction = "Get into pushup position",
        workingInstruction = "Do one pushup, then tap +1"
    ),
    SQUATS(
        title = "Squats",
        targetReps = 15,
        holdSeconds = null,
        accent = Color(0xFFE91E63),
        prepInstruction = "Stand with feet shoulder-width apart",
        workingInstruction = "Do one squat, then tap +1"
    ),
    PLANK(
        title = "Plank",
        targetReps = null,
        holdSeconds = 30,
        accent = Color(0xFFFFC107),
        prepInstruction = "Get into plank position",
        workingInstruction = "Hold it! Don't give up 💪"
    )
}

private enum class WorkoutPhase { GET_READY, WORKING, DONE }

/**
 * Full-screen guided exercise with a 5-second "Get Ready" countdown
 * before the workout starts.
 *
 * - Rep exercises (Pushups 10 / Squats 15): the user taps "+1" after each rep.
 * - Hold exercise (Plank 30s): an automatic countdown timer runs.
 *
 * @param exercise   Which guided exercise to run.
 * @param onComplete Called with `true` when the workout is finished,
 *                   `false` when the user gives up mid-workout.
 * @param onCancel   Called when the user closes the screen before logging anything.
 */
@Composable
fun ExerciseWorkout(
    exercise: ExerciseType,
    onComplete: (success: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    var phase by remember { mutableStateOf(WorkoutPhase.GET_READY) }

    // Get-ready countdown (5 seconds)
    var countdown by remember { mutableIntStateOf(5) }
    var showGo by remember { mutableStateOf(false) }

    // Workout state
    var repsDone by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(exercise.holdSeconds ?: 0) }
    var progress by remember { mutableFloatStateOf(0f) }

    val target = exercise.targetReps ?: 0
    val totalHold = exercise.holdSeconds ?: 0
    val accent = exercise.accent

    // ── Get-ready countdown timer: 5 → 1, then "GO!", then start ────────
    LaunchedEffect(exercise) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        showGo = true
        delay(800)
        phase = WorkoutPhase.WORKING
    }

    // ── Plank hold timer (100ms ticks for a smooth progress ring) ───────
    LaunchedEffect(phase) {
        if (phase != WorkoutPhase.WORKING || totalHold <= 0) return@LaunchedEffect
        val totalSteps = totalHold * 10
        for (step in 0..totalSteps) {
            progress = step.toFloat() / totalSteps
            secondsLeft = (totalHold - (step / 10)).coerceAtLeast(0)
            if (step == totalSteps) {
                phase = WorkoutPhase.DONE
            }
            delay(100)
        }
    }

    // ── Auto-return shortly after completing ────────────────────────────
    LaunchedEffect(phase) {
        if (phase == WorkoutPhase.DONE) {
            delay(2000)
            onComplete(true)
        }
    }

    // Number pulse animation for the countdown / hold timer
    val infiniteTransition = rememberInfiniteTransition(label = "workout_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "countdown_pulse"
    )

    val ringProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "ring_progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        // Close button (top-right) — cancels without logging
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel exercise",
                tint = themeTextSecondary,
                modifier = Modifier.size(28.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (phase) {
                WorkoutPhase.GET_READY -> {
                    Text(
                        text = "Get Ready!",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = themeTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = exercise.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = exercise.prepInstruction,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = themeTextSecondary
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Giant 5 → 1 countdown (then GO!)
                    Text(
                        text = if (showGo) "GO! 🔥" else countdown.toString(),
                        style = MaterialTheme.typography.displayLarge.copy(
                            color = if (showGo) accent else themeTextPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = if (showGo) 72.sp else 96.sp
                        ),
                        modifier = Modifier.scale(pulseScale)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Starting in ${countdown.coerceAtLeast(0)}...",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = themeTextSecondary
                        )
                    )
                }

                WorkoutPhase.WORKING -> {
                    Text(
                        text = exercise.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = themeTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = exercise.workingInstruction,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = themeTextSecondary
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Progress ring with rep count / hold seconds in the middle
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(240.dp)
                    ) {
                        // Outer glow
                        Canvas(modifier = Modifier.size(240.dp)) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        accent.copy(alpha = 0.12f),
                                        accent.copy(alpha = 0f)
                                    ),
                                    center = center,
                                    radius = size.minDimension / 2
                                ),
                                radius = size.minDimension / 2,
                                center = center
                            )
                        }

                        // Track + progress arc
                        Canvas(modifier = Modifier.size(220.dp)) {
                            val stroke = 14.dp.toPx()
                            val radius = (size.minDimension - stroke) / 2
                            val topLeft = Offset(
                                (size.width - radius * 2) / 2,
                                (size.height - radius * 2) / 2
                            )
                            // Track
                            drawArc(
                                color = accent.copy(alpha = 0.15f),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                            // Progress
                            drawArc(
                                color = accent,
                                startAngle = -90f,
                                sweepAngle = (ringProgress.coerceIn(0f, 1f)) * 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }

                        // Center content
                        if (exercise.targetReps != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$repsDone",
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        color = themeTextPrimary,
                                        fontWeight = FontWeight.Black
                                    )
                                )
                                Text(
                                    text = "of $target reps",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = themeTextSecondary
                                    )
                                )
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${secondsLeft.coerceAtLeast(0)}s",
                                    style = MaterialTheme.typography.displayMedium.copy(
                                        color = themeTextPrimary,
                                        fontWeight = FontWeight.Black
                                    ),
                                    modifier = Modifier.scale(pulseScale)
                                )
                                Text(
                                    text = "hold it!",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = themeTextSecondary
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    if (exercise.targetReps != null) {
                        // Tap button for each rep
                        Button(
                            onClick = {
                                if (repsDone < target) {
                                    repsDone++
                                    if (repsDone >= target) {
                                        phase = WorkoutPhase.DONE
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FitnessCenter,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.size(10.dp))
                            Text(
                                text = "+1 Rep",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = { onComplete(false) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = themeTextSecondary
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = "Give up 😓",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                WorkoutPhase.DONE -> {
                    Text(
                        text = "Awesome! 🎉",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = themeTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (exercise.targetReps != null) {
                            "${exercise.title} — $target reps done!"
                        } else {
                            "${exercise.title} — $totalHold seconds held!"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    KonfettiOverlay(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }
        }
    }
}
