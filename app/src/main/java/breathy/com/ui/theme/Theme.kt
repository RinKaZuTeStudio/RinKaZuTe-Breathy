package breathy.com.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Note: Color definitions live in Color.kt (same package).
// Typography definitions live in Type.kt (same package).
// Colors like BgPrimary, AccentPrimary, TextPrimary, etc. are
// accessible here without explicit import.

// ── Gradients ──────────────────────────────────────────────────────────────

/** Primary gradient: 45° natural green blend — CTA buttons, hero card accents. */
val GradientPrimary = Brush.linearGradient(
    colors = listOf(NaturalGreen, Color(0xFF5D874E)),
    start = androidx.compose.ui.geometry.Offset.Zero,
    end = androidx.compose.ui.geometry.Offset(1000f, 1000f) // ~45 degrees
)

/** Premium gradient: deep botanical green — premium cards, premium badge. */
val GradientPurple = Brush.linearGradient(
    colors = listOf(DeepForest, DarkBotanical)
)

/** Sage glow — radial gradient behind hero stats. */
val GradientGlowGreen = Brush.radialGradient(
    colors = listOf(VeryLightSage, Color.Transparent)
)

/** Warm sand glow — pulsing glow behind craving button. */
val GradientGlowOrange = Brush.radialGradient(
    colors = listOf(SoftSand.copy(alpha = 0.45f), Color.Transparent)
)

// ── Named gradient tokens for new components ──────────────────────────────

object BreathyGradients {
    /** Premium surfaces/badges: deep botanical green. */
    val premium = listOf(DeepForest, DarkBotanical)

    /** Fresh sage wash for section headers and soft hero areas. */
    val sageWash = listOf(VeryLightSage, WarmWhite)

    /** Gold-sand sheen for achievement highlights. */
    val goldSand = listOf(SoftSand, NaturalYellow)
}

// ── Custom Component Tokens ────────────────────────────────────────────────

object BreathyBorders {
    /** Subtle hairline border for white cards on the warm-white canvas. */
    val subtle: BorderStroke
        @Composable @ReadOnlyComposable get() = BorderStroke(1.dp, SoftSage.copy(alpha = 0.55f))

    /** Accent border for selected/highlighted elements. */
    val accent: BorderStroke
        @Composable @ReadOnlyComposable get() = BorderStroke(1.dp, NaturalGreen.copy(alpha = 0.5f))

    /** Premium border — deep forest. */
    val premium: BorderStroke
        @Composable @ReadOnlyComposable get() = BorderStroke(1.dp, DeepForest.copy(alpha = 0.4f))
}

object BreathyComponents {
    /** Soft sage border for cards (replaces the old neon stroke). */
    val neonBorder: BorderStroke
        @Composable @ReadOnlyComposable get() = BreathyBorders.subtle

    /** Deep-forest border for premium/achievement cards. */
    val neonBorderPurple: BorderStroke
        @Composable @ReadOnlyComposable get() = BreathyBorders.premium

    /** Sky-tinted border for informational cards. */
    val neonBorderBlue: BorderStroke
        @Composable @ReadOnlyComposable get() = BorderStroke(
            width = 1.dp,
            color = AccentInfo.copy(alpha = 0.3f)
        )

    /** Button gradient brush for primary CTA buttons. */
    val buttonGradient: Brush
        @Composable @ReadOnlyComposable get() = GradientPrimary

    /** Button gradient brush for premium/achievement contexts. */
    val buttonGradientPurple: Brush
        @Composable @ReadOnlyComposable get() = GradientPurple

    /** Card corner radius. */
    val cardCornerRadius = 20.dp

    /** Button corner radius (pill-shaped). */
    val buttonCornerRadius = 24.dp

    /** Input field corner radius. */
    val inputCornerRadius = 14.dp

    /** Chip corner radius (stadium shape). */
    val chipCornerRadius = 20.dp

    /** Bottom sheet corner radius (top corners). */
    val bottomSheetCornerRadius = 28.dp

    /** Dialog corner radius. */
    val dialogCornerRadius = 28.dp
}

// ── Spacing Tokens ─────────────────────────────────────────────────────────

object BreathySpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

// ── Composition Locals for custom theme values ─────────────────────────────

val LocalBreathyTypography = staticCompositionLocalOf { BreathyTypography }
val LocalBreathyComponents = staticCompositionLocalOf { BreathyComponents }
val LocalBreathySpacing = staticCompositionLocalOf { BreathySpacing }

// ── Theme preference ───────────────────────────────────────────────────────

enum class ThemeMode {
    SYSTEM,   // Follow system setting
    LIGHT,    // Always light
    DARK      // Always dark
}

val LocalThemeMode = staticCompositionLocalOf { ThemeMode.SYSTEM }

// ── Material 3 Color Schemes ───────────────────────────────────────────────
// The app is light-first by design (calm, clean, premium wellness).
// Both schemes render the Sage Nature identity; the "dark" scheme keeps
// deep-forest accents for users who explicitly choose dark mode, while
// retaining the same light botanical canvas so every screen stays coherent.

private val BreathyDarkColorScheme = darkColorScheme(
    primary = NaturalGreen,
    onPrimary = TextInverse,
    primaryContainer = VeryLightSage,
    onPrimaryContainer = DarkBotanical,

    secondary = WarmEarth,
    onSecondary = TextInverse,
    secondaryContainer = SoftSand.copy(alpha = 0.45f),
    onSecondaryContainer = DarkBotanical,

    tertiary = DeepForest,
    onTertiary = TextInverse,
    tertiaryContainer = VeryLightSage,
    onTertiaryContainer = DarkBotanical,

    background = WarmWhite,
    onBackground = TextPrimary,

    surface = PureWhite,
    onSurface = TextPrimary,

    surfaceVariant = VeryLightSage,
    onSurfaceVariant = TextSecondary,

    error = SemanticError,
    onError = TextInverse,
    errorContainer = SemanticError.copy(alpha = 0.12f),
    onErrorContainer = SemanticError,

    outline = OutlineColor,
    outlineVariant = OutlineVariantColor,

    inverseSurface = TextPrimary,
    inverseOnSurface = WarmWhite,
    inversePrimary = AccentPrimaryPressed,

    surfaceContainerLowest = WarmWhite,
    surfaceContainerLow = PureWhite,
    surfaceContainer = VeryLightSage,
    surfaceContainerHigh = PureWhite,
    surfaceContainerHighest = PureWhite,

    scrim = ScrimColor
)

// ── Material 3 Light Color Scheme ──────────────────────────────────────────

private val BreathyLightColorScheme = lightColorScheme(
    primary = NaturalGreen,
    onPrimary = TextInverse,
    primaryContainer = VeryLightSage,
    onPrimaryContainer = DarkBotanical,

    secondary = WarmEarth,
    onSecondary = TextInverse,
    secondaryContainer = SoftSand.copy(alpha = 0.45f),
    onSecondaryContainer = DarkBotanical,

    tertiary = DeepForest,
    onTertiary = TextInverse,
    tertiaryContainer = VeryLightSage,
    onTertiaryContainer = DarkBotanical,

    background = WarmWhite,
    onBackground = TextPrimary,

    surface = PureWhite,
    onSurface = TextPrimary,

    surfaceVariant = VeryLightSage,
    onSurfaceVariant = TextSecondary,

    error = SemanticError,
    onError = TextInverse,
    errorContainer = SemanticError.copy(alpha = 0.12f),
    onErrorContainer = SemanticError,

    outline = OutlineColor,
    outlineVariant = OutlineVariantColor,

    inverseSurface = TextPrimary,
    inverseOnSurface = WarmWhite,
    inversePrimary = AccentPrimaryPressed,

    surfaceContainerLowest = WarmWhite,
    surfaceContainerLow = PureWhite,
    surfaceContainer = VeryLightSage,
    surfaceContainerHigh = PureWhite,
    surfaceContainerHighest = PureWhite,

    scrim = ScrimColor
)

// ── Theme Composable ───────────────────────────────────────────────────────

@Composable
fun BreathyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDarkTheme) BreathyDarkColorScheme else BreathyLightColorScheme

    // Merge our custom typography with Material 3's typography
    val materialTypography = androidx.compose.material3.Typography(
        headlineLarge = BreathyTypography.headlineLarge,
        headlineMedium = BreathyTypography.headlineMedium,
        headlineSmall = BreathyTypography.headlineSmall,
        bodyLarge = BreathyTypography.bodyLarge,
        bodyMedium = BreathyTypography.bodyMedium,
        bodySmall = BreathyTypography.captionLarge,
        labelLarge = BreathyTypography.bodyMedium,
        labelMedium = BreathyTypography.captionLarge,
        labelSmall = BreathyTypography.captionSmall,
        titleLarge = BreathyTypography.headlineSmall,
        titleMedium = BreathyTypography.bodyLarge,
        titleSmall = BreathyTypography.bodyMedium
    )

    // Custom shapes matching the design system
    val shapes = androidx.compose.material3.Shapes(
        extraSmall = RoundedCornerShape(BreathyComponents.inputCornerRadius),
        small = RoundedCornerShape(BreathyComponents.inputCornerRadius),
        medium = RoundedCornerShape(BreathyComponents.cardCornerRadius),
        large = RoundedCornerShape(BreathyComponents.bottomSheetCornerRadius),
        extraLarge = RoundedCornerShape(BreathyComponents.dialogCornerRadius)
    )

    CompositionLocalProvider(
        LocalBreathyTypography provides BreathyTypography,
        LocalBreathyComponents provides BreathyComponents,
        LocalBreathySpacing provides BreathySpacing,
        LocalThemeMode provides themeMode
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = materialTypography,
            shapes = shapes,
            content = content
        )
    }
}

// ── Convenience Accessors ──────────────────────────────────────────────────

object BreathyTheme {
    val typography: BreathyTypography
        @Composable @ReadOnlyComposable get() = LocalBreathyTypography.current

    val components: BreathyComponents
        @Composable @ReadOnlyComposable get() = LocalBreathyComponents.current

    val spacing: BreathySpacing
        @Composable @ReadOnlyComposable get() = LocalBreathySpacing.current

    val themeMode: ThemeMode
        @Composable @ReadOnlyComposable get() = LocalThemeMode.current
}
