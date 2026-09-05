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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import breathy.com.ui.theme.AccentPrimary
import breathy.com.ui.theme.AccentPurple
import breathy.com.ui.theme.AccentSecondary
import breathy.com.ui.theme.themeBgSurface
import breathy.com.ui.theme.themeTextSecondary
import breathy.com.utils.s
import kotlinx.coroutines.delay
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════════════════
//  StatCard — Reusable stat card component with animated value counter
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * A compact stat card showing an icon, animated value, and label.
 *
 * v1.0.11 rev 7 — DASHBOARD ALIGNMENT PASS (presentation only; values,
 * icons, labels, colors and calculations are untouched):
 * - every card in the row stretches to the SAME height (the Home row wraps
 *   the three cards in an intrinsic-min-height Row and each card fills it),
 *   so top and bottom edges align and spacing stays even;
 * - the content uses FIXED slots (icon 24dp · value 28dp · label 28dp) and is
 *   vertically centered, so all three values sit on one line across the row;
 * - the value auto-fits (22sp shrinking to 13sp) so long values like
 *   "999d 23h" or "$123.45" never clip, wrap or overlap.
 *
 * @param value     The display string for the stat value (e.g. "$42.50", "120").
 * @param label     The label text below the value (e.g. "Saved", "Avoided").
 * @param icon      The icon composable to display above the value.
 * @param accentColor The neon accent color for the value and glow effects.
 * @param numericValue If provided, the card will animate counting from 0 to this value.
 * @param isAchieved Whether the stat has a non-zero value (triggers glow border).
 */
@Composable
fun StatCard(
    value: String,
    label: String,
    icon: @Composable () -> Unit,
    accentColor: Color,
    numericValue: Int = 0,
    isAchieved: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Animated scale on mount
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(400),
        label = "stat_scale"
    )

    // Animated value counter
    var displayValue by remember { mutableIntStateOf(0) }
    val shouldAnimate = numericValue > 0 && numericValue != displayValue

    LaunchedEffect(numericValue) {
        if (numericValue > 0) {
            val steps = 20.coerceAtMost(numericValue)
            val increment = numericValue / steps
            var current = 0
            repeat(steps) {
                current += increment
                if (current > numericValue) current = numericValue
                displayValue = current
                delay(30)
            }
            displayValue = numericValue
        } else {
            displayValue = 0
        }
    }

    // Glow pulsation for achieved cards
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // v1.0.11 rev 7 — auto-fit: start at the designed 22sp and shrink 1sp per
    // layout pass (down to 13sp) ONLY if the value would overflow its slot.
    var valueFontSize by remember(value) { mutableFloatStateOf(22f) }

    Card(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        colors = CardDefaults.cardColors(containerColor = themeBgSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icon — fixed slot so every card aligns
                Row(
                    modifier = Modifier.height(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    icon()
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Animated value display — fixed slot + auto-fit, single line
                val displayText = if (shouldAnimate && numericValue > 0) {
                    displayValue.toString()
                } else {
                    value
                }

                Box(
                    modifier = Modifier.height(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayText,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = valueFontSize.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            shadow = Shadow(
                                color = accentColor.copy(alpha = 0.4f),
                                offset = androidx.compose.ui.geometry.Offset(0f, 0f),
                                blurRadius = 8f
                            )
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        onTextLayout = { result ->
                            if (result.hasVisualOverflow && valueFontSize > 13f) {
                                valueFontSize -= 1f
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Label — fixed two-line slot keeps all three labels aligned
                Box(
                    modifier = Modifier.height(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = themeTextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Neon border glow overlay for achieved cards
            if (isAchieved) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawRoundRect(
                        color = accentColor.copy(alpha = glowAlpha),
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Pre-built stat card variants
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun MoneySavedCard(
    moneySaved: Double,
    modifier: Modifier = Modifier
) {
    val formatted = formatCurrency(moneySaved)
    val numericInt = moneySaved.toInt().coerceAtLeast(0)
    StatCard(
        value = formatted,
        label = s("Saved", "موفّر"),
        icon = {
            Icon(
                imageVector = Icons.Default.Savings,
                contentDescription = "Money Saved",
                tint = AccentPrimary,
                modifier = Modifier.size(24.dp)
            )
        },
        accentColor = AccentPrimary,
        numericValue = numericInt,
        isAchieved = moneySaved > 0,
        modifier = modifier
    )
}

@Composable
fun CigarettesAvoidedCard(
    cigarettesAvoided: Int,
    modifier: Modifier = Modifier
) {
    StatCard(
        value = cigarettesAvoided.toString(),
        label = s("Avoided", "تم تجنّبها"),
        icon = {
            Icon(
                imageVector = Icons.Default.MonitorHeart,
                contentDescription = "Cigarettes Avoided",
                tint = AccentSecondary,
                modifier = Modifier.size(24.dp)
            )
        },
        accentColor = AccentSecondary,
        numericValue = cigarettesAvoided,
        isAchieved = cigarettesAvoided > 0,
        modifier = modifier
    )
}

@Composable
fun LifeRegainedCard(
    lifeRegainedText: String,
    modifier: Modifier = Modifier
) {
    StatCard(
        value = lifeRegainedText,
        label = s("Life Regained", "عمر مستعاد"),
        icon = {
            Icon(
                imageVector = Icons.Default.Bloodtype,
                contentDescription = "Life Regained",
                tint = AccentPurple,
                modifier = Modifier.size(24.dp)
            )
        },
        accentColor = AccentPurple,
        isAchieved = lifeRegainedText != "0m",
        modifier = modifier
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Helpers
// ═══════════════════════════════════════════════════════════════════════════════

private fun formatCurrency(amount: Double): String {
    return if (amount >= 1000) {
        String.format(Locale.US, "$%.1fk", amount / 1000.0)
    } else {
        String.format(Locale.US, "$%.2f", amount)
    }
}
