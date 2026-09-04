package breathy.com.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * v1.0.9 PREMIUM PERK — Neon Glow Text / Glowing Text Animation.
 *
 * Every verified Breathy Premium subscriber's name glows across the app
 * (leaderboard, community, friends, profile). The effect is a soft neon
 * halo that breathes around the glyphs: a colored shadow copy pulses
 * underneath the crisp white foreground text.
 *
 * Signature Breathy neon color: mint-teal `#3BF5B0` — reads premium against
 * the Nature light palette and matches the app's botanical green identity.
 */
object PremiumGlow {
    /** The neon color chosen for Breathy Premium glowing names. */
    val NEON_MINT = Color(0xFF3BF5B0)
}

@Composable
fun PremiumGlowText(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TextStyle = TextStyle.Default,
    color: Color = Color.White,
    glowColor: Color = PremiumGlow.NEON_MINT,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis
) {
    if (!enabled) {
        Text(
            text = text,
            modifier = modifier,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow
        )
        return
    }

    val transition = rememberInfiniteTransition(label = "premium_glow")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    Box(modifier = modifier) {
        // ── Glow layer: neon copy with a breathing blurred shadow ──────────
        Text(
            text = text,
            style = style.copy(
                color = glowColor,
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = glowColor.copy(alpha = 0.9f * pulse),
                    offset = androidx.compose.ui.geometry.Offset.Zero,
                    blurRadius = 18f * pulse
                )
            ),
            maxLines = maxLines,
            overflow = overflow,
            modifier = Modifier.matchParentSize()
        )
        // Extra soft halo pass for a richer neon bloom.
        Text(
            text = text,
            style = style.copy(
                color = glowColor.copy(alpha = 0.55f * pulse),
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = glowColor.copy(alpha = 0.5f * pulse),
                    offset = androidx.compose.ui.geometry.Offset.Zero,
                    blurRadius = 30f * pulse
                )
            ),
            maxLines = maxLines,
            overflow = overflow,
            modifier = Modifier.matchParentSize()
        )
        // ── Crisp foreground ───────────────────────────────────────────────
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}
