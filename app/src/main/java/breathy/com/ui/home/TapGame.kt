package breathy.com.ui.home

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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

/**
 * Simple tap game where the user must tap a button as fast as possible
 * for 30 seconds. Helps distract from cravings through rapid interaction.
 *
 * Bubbles appear and the user must tap them before they disappear.
 * After the game, the user reports whether the craving was defeated.
 *
 * @param onComplete Called with `true` if the craving was defeated, `false` otherwise.
 * @param onCancel   Called when the user cancels the game.
 */
@Composable
fun TapGame(
    onComplete: (success: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val totalDuration = 30 // seconds
    var timeLeft by remember { mutableIntStateOf(totalDuration) }
    var tapCount by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(true) }
    var isComplete by remember { mutableStateOf(false) }
    var isTapAnimating by remember { mutableStateOf(false) }

    val tapScale by animateFloatAsState(
        targetValue = if (isTapAnimating) 1.15f else 1f,
        animationSpec = tween(100),
        label = "tap_scale"
    )

    // Countdown timer
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect

        while (timeLeft > 0 && isPlaying) {
            delay(1000)
            timeLeft--
        }

        if (timeLeft <= 0 && isPlaying) {
            isPlaying = false
            isComplete = true
        }
    }

    // Reset tap animation
    LaunchedEffect(isTapAnimating) {
        if (isTapAnimating) {
            delay(100)
            isTapAnimating = false
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
            // Close button at top
            Box(modifier = Modifier.fillMaxSize()) {
                IconButton(
                    onClick = {
                        isPlaying = false
                        onCancel()
                    },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel tap game",
                        tint = themeTextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isComplete) {
                        // Game instructions
                        Text(
                            text = "Tap as fast as you can!",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = themeTextPrimary,
                                fontWeight = FontWeight.Bold
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Timer
                        Text(
                            text = timeLeft.toString(),
                            style = MaterialTheme.typography.displayLarge.copy(
                                color = if (timeLeft <= 5) Color(0xFFFF5252) else AccentSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 72.sp
                            )
                        )

                        Text(
                            text = "seconds left",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = themeTextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(40.dp))

                        // Tap target button
                        val infiniteTransition = rememberInfiniteTransition(label = "tap_glow")
                        val glowAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.1f,
                            targetValue = 0.3f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "tap_glow_alpha"
                        )

                        Button(
                            onClick = {
                                if (isPlaying) {
                                    tapCount++
                                    isTapAnimating = true
                                }
                            },
                            modifier = Modifier
                                .size(160.dp)
                                .scale(tapScale),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentPrimary
                            ),
                            shape = CircleShape,
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp
                            )
                        ) {
                            // Glow effect behind
                            Canvas(modifier = Modifier.size(160.dp)) {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            AccentPrimary.copy(alpha = glowAlpha),
                                            AccentPrimary.copy(alpha = 0f)
                                        ),
                                        center = center,
                                        radius = size.minDimension / 2
                                    ),
                                    radius = size.minDimension / 2
                                )
                            }

                            Text(
                                text = "TAP!",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = themeBgPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 28.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Current count
                        Text(
                            text = tapCount.toString(),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentPurple
                            )
                        )

                        Text(
                            text = "taps",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = themeTextSecondary
                            )
                        )
                    }

                    // Result screen
                    if (isComplete) {
                        Text(
                            text = "Time's up! 🎯",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = themeTextPrimary,
                                fontWeight = FontWeight.Bold
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Result card
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = themeBgSurface
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.width(280.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = tapCount.toString(),
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 64.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentPrimary
                                    )
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "taps in 30 seconds",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = themeTextSecondary
                                    )
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                val feedbackMessage = when {
                                    tapCount >= 150 -> "Incredible! That craving doesn't stand a chance! 💪"
                                    tapCount >= 100 -> "Nice! You're faster than the craving! 🔥"
                                    tapCount >= 50 -> "Good work! Keep that energy up! ⚡"
                                    else -> "Every tap counts! You've got this! 🌟"
                                }

                                Text(
                                    text = feedbackMessage,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = themeTextPrimary,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
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
                            text = "+5 XP for completing the game",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = AccentPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}
