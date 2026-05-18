@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.breathy.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breathy.data.models.CopingMethod
import com.breathy.ui.theme.AccentPrimary
import com.breathy.ui.theme.AccentOrange
import com.breathy.ui.theme.AccentPurple
import com.breathy.ui.theme.AccentSecondary
import com.breathy.ui.theme.themeBgPrimary
import com.breathy.ui.theme.themeBgSurface
import com.breathy.ui.theme.themeBgSurfaceVariant
import com.breathy.ui.theme.themeTextPrimary
import com.breathy.ui.theme.themeTextSecondary
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════════════════════════
//  CravingBottomSheet — Craving coping bottom sheet with 3 methods
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Bottom sheet offering 3 coping methods for fighting cravings.
 *
 * @param lastCravingTimeAgo   Human-readable time since last craving.
 * @param onBreathingExercise  Callback to start the breathing exercise.
 * @param onMiniGame           Callback to start the tap game.
 * @param onAICoach            Callback to navigate to AI coach chat.
 * @param onLogCraving         Callback to log a craving with method and success.
 * @param onDismiss            Callback when the sheet is dismissed.
 */
@Composable
fun CravingBottomSheet(
    lastCravingTimeAgo: String?,
    onBreathingExercise: () -> Unit,
    onMiniGame: () -> Unit,
    onAICoach: () -> Unit,
    onLogCraving: (method: CopingMethod, success: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    var showFeedback by remember { mutableStateOf(false) }
    var selectedMethod by remember { mutableStateOf<CopingMethod?>(null) }
    var showConfetti by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = themethemeBgSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
            ) {
                Canvas(modifier = Modifier.size(40.dp, 4.dp)) {
                    drawRoundRect(
                        color = Color(0xFF606080),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                    )
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Confetti overlay
            if (showConfetti) {
                KonfettiOverlay(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
                LaunchedEffect(Unit) {
                    delay(2500)
                    showConfetti = false
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "Stay Strong! You've got this 💪",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = themethemeTextPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Time since last craving
                if (lastCravingTimeAgo != null) {
                    Text(
                        text = lastCravingTimeAgo,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = AccentSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                } else {
                    Text(
                        text = "Your first craving — you can do this!",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = themethemeTextSecondary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Choose a coping method to fight this craving",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = themethemeTextSecondary
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Coping method cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CopingMethodCard(
                        title = "Breathe",
                        description = "4-7-8 breathing to calm cravings",
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Air,
                                contentDescription = "Breathing",
                                tint = AccentPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        accentColor = AccentPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedMethod = CopingMethod.BREATHING
                            onBreathingExercise()
                        }
                    )

                    CopingMethodCard(
                        title = "Distract",
                        description = "Tap as fast as you can for 30s",
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Gamepad,
                                contentDescription = "Mini Game",
                                tint = AccentPurple,
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        accentColor = AccentPurple,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedMethod = CopingMethod.GAME
                            onMiniGame()
                        }
                    )

                    CopingMethodCard(
                        title = "Talk",
                        description = "Chat with your AI coach",
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = "AI Coach",
                                tint = AccentOrange,
                                modifier = Modifier.size(32.dp)
                            )
                        },
                        accentColor = AccentOrange,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedMethod = CopingMethod.AI
                            onAICoach()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Feedback section — success/failure recording
                AnimatedVisibility(
                    visible = showFeedback,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 })
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Did it help?",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = themethemeTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = {
                                    selectedMethod?.let { onLogCraving(it, true) }
                                    showConfetti = true
                                    showFeedback = false
                                    // Dismiss after confetti
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentPrimary,
                                    contentColor = themeBgPrimary
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text(
                                    text = "Yes, I'm good 👍",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    selectedMethod?.let { onLogCraving(it, false) }
                                    showFeedback = false
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = AccentSecondary
                                ),
                                border = BorderStroke(1.dp, AccentSecondary.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text(
                                    text = "Not really 😔",
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                if (!showFeedback) {
                    androidx.compose.material3.TextButton(
                        onClick = { showFeedback = true },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = "I already tried something",
                            color = themethemeTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Coping Method Card
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CopingMethodCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Subtle pulse to invite tapping
    val infiniteTransition = rememberInfiniteTransition(label = "card_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "card_glow"
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = themethemeBgSurfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(48.dp)) {
                    drawCircle(color = accentColor.copy(alpha = glowAlpha))
                }
                icon()
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = themethemeTextPrimary,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = themethemeTextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                ),
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tap to start",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  KonfettiOverlay — Custom confetti animation (no external library needed)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Simple Canvas-based confetti animation.
 * Renders colorful particles that fall and rotate, simulating celebration.
 */
@Composable
fun KonfettiOverlay(
    modifier: Modifier = Modifier
) {
    val confettiColors = listOf(
        Color(0xFF00E676), // Green
        Color(0xFF448AFF), // Blue
        Color(0xFFB388FF), // Purple
        Color(0xFFFF9100), // Orange
        Color(0xFFFF4081), // Pink
        Color(0xFFFFD740), // Yellow
    )

    // Generate confetti particles
    val particles = remember {
        List(60) { i ->
            ConfettiParticle(
                x = Random.nextFloat(),
                y = Random.nextFloat() * -1f, // Start above viewport
                color = confettiColors[i % confettiColors.size],
                speed = 0.5f + Random.nextFloat() * 1.5f,
                angle = Random.nextFloat() * 360f,
                rotationSpeed = 1f + Random.nextFloat() * 3f,
                width = 4.dp.value + Random.nextFloat() * 4.dp.value,
                height = 8.dp.value + Random.nextFloat() * 8.dp.value,
                drift = -0.3f + Random.nextFloat() * 0.6f // Horizontal drift
            )
        }
    }

    var elapsed by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            elapsed += 0.016f // ~60fps
            delay(16)
        }
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

                // Calculate rotated rectangle corners
                val radians = Math.toRadians(rotation.toDouble()).toFloat()
                val cosA = cos(radians)
                val sinA = sin(radians)

                drawRect(
                    color = particle.color,
                    topLeft = Offset(px - widthPx / 2, py - heightPx / 2),
                    size = androidx.compose.ui.geometry.Size(widthPx, heightPx),
                    alpha = 1f - (currentY / 1.5f).coerceIn(0f, 1f) * 0.5f
                )
            }
        }
    }
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
