package breathy.com.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import breathy.com.ui.theme.BreathyPalette

/**
 * Polished, botanical empty state used across the app (events, leaderboard,
 * community, achievements). Renders a soft organic leaf/seed motif inside a
 * sage halo — intentional and premium, never "broken-looking".
 */
@Composable
fun BreathyEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: String = "\uD83C\uDF3F", // herb leaf
    illustrationSize: Dp = 96.dp,
    extraContent: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Botanical halo: concentric sage circles + glyph
        Box(
            modifier = Modifier.size(illustrationSize),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(illustrationSize)
                    .drawBehind {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        drawCircle(
                            color = BreathyPalette.softSage.copy(alpha = 0.45f),
                            radius = size.minDimension / 2f,
                            center = center
                        )
                        drawCircle(
                            color = BreathyPalette.mediumSage.copy(alpha = 0.5f),
                            radius = size.minDimension / 2.6f,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                    }
            )
            Box(
                modifier = Modifier
                    .size(illustrationSize * 0.62f)
                    .clip(CircleShape)
                    .background(BreathyPalette.pureWhite),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = BreathyPalette.textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = BreathyPalette.textSecondary,
            textAlign = TextAlign.Center
        )

        extraContent?.invoke()
    }
}

/** Standard spacing token shortcut for empty-state containers. */
val EmptyStateMinHeight: Dp = 320.dp

/** Reusable vertical filler for centered empty states inside scroll columns. */
@Composable
fun EmptyStateSpacer() = Spacer(Modifier.height(24.dp))

/** Small colored dot used by list meta rows. */
@Composable
fun StatusDot(color: Color) = Box(
    Modifier
        .size(8.dp)
        .clip(CircleShape)
        .background(color)
)
