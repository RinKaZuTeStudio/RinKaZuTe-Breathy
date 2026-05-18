package com.breathy.ui.theme

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

/** Primary gradient: 45° green-to-blue — CTA buttons, hero card accents. */
val GradientPrimary = Brush.linearGradient(
    colors = listOf(AccentPrimary, AccentInfo),
    start = androidx.compose.ui.geometry.Offset.Zero,
    end = androidx.compose.ui.geometry.Offset(1000f, 1000f) // ~45 degrees
)

/** Purple gradient: 135° purple-to-blue — premium feature cards, achievement bg. */
val GradientPurple = Brush.linearGradient(
    colors = listOf(AccentPurple, AccentInfo)
)

/** Green glow — radial gradient behind hero stats. */
val GradientGlowGreen = Brush.radialGradient(
    colors = listOf(AccentPrimary.copy(alpha = 0.3f), Color.Transparent)
)

/** Orange glow — pulsing glow behind craving button. */
val GradientGlowOrange = Brush.radialGradient(
    colors = listOf(AccentOrange.copy(alpha = 0.25f), Color.Transparent)
)

// ── Custom Component Tokens ────────────────────────────────────────────────

object BreathyComponents {
    /** Neon border for cards — thin accent-primary stroke with subtle glow. */
    val neonBorder: BorderStroke
        @Composable @ReadOnlyComposable get() = BorderStroke(
            width = 1.dp,
            color = AccentPrimary.copy(alpha = 0.3f)
        )

    /** Neon border with purple accent for premium/achievement cards. */
    val neonBorderPurple: BorderStroke
        @Composable @ReadOnlyComposable get() = BorderStroke(
            width = 1.dp,
            color = AccentPurple.copy(alpha = 0.3f)
        )

    /** Neon border with blue accent for informational cards. */
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
    val cardCornerRadius = 16.dp

    /** Button corner radius (pill-shaped). */
    val buttonCornerRadius = 24.dp

    /** Input field corner radius. */
    val inputCornerRadius = 12.dp

    /** Chip corner radius (stadium shape). */
    val chipCornerRadius = 20.dp

    /** Bottom sheet corner radius (top corners). */
    val bottomSheetCornerRadius = 24.dp

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

// ── Material 3 Dark Color Scheme ───────────────────────────────────────────

private val BreathyDarkColorScheme = darkColorScheme(
    primary = AccentPrimary,
    onPrimary = TextInverse,
    primaryContainer = AccentPrimary.copy(alpha = 0.15f),
    onPrimaryContainer = AccentPrimary,

    secondary = AccentSecondary,
    onSecondary = TextInverse,
    secondaryContainer = AccentSecondary.copy(alpha = 0.15f),
    onSecondaryContainer = AccentSecondary,

    tertiary = AccentPurple,
    onTertiary = TextInverse,
    tertiaryContainer = AccentPurple.copy(alpha = 0.15f),
    onTertiaryContainer = AccentPurple,

    background = BgPrimary,
    onBackground = TextPrimary,

    surface = BgSurface,
    onSurface = TextPrimary,

    surfaceVariant = BgSurfaceVariant,
    onSurfaceVariant = TextSecondary,

    error = SemanticError,
    onError = TextPrimary,
    errorContainer = SemanticError.copy(alpha = 0.15f),
    onErrorContainer = SemanticError,

    outline = OutlineColor,
    outlineVariant = OutlineVariantColor,

    inverseSurface = TextPrimary,
    inverseOnSurface = BgPrimary,
    inversePrimary = AccentPrimaryPressed,

    surfaceContainerLowest = BgPrimary,
    surfaceContainerLow = BgSurface,
    surfaceContainer = BgSurfaceVariant,
    surfaceContainerHigh = BgSurfaceElevated,
    surfaceContainerHighest = BgSurfaceElevated,

    scrim = ScrimColor
)

// ── Material 3 Light Color Scheme ──────────────────────────────────────────

private val BreathyLightColorScheme = lightColorScheme(
    primary = LightAccentPrimary,
    onPrimary = LightTextInverse,
    primaryContainer = LightAccentPrimary.copy(alpha = 0.12f),
    onPrimaryContainer = LightAccentPrimary,

    secondary = AccentSecondary,
    onSecondary = LightTextInverse,
    secondaryContainer = AccentSecondary.copy(alpha = 0.12f),
    onSecondaryContainer = AccentSecondary,

    tertiary = AccentPurple,
    onTertiary = LightTextInverse,
    tertiaryContainer = AccentPurple.copy(alpha = 0.12f),
    onTertiaryContainer = AccentPurple,

    background = LightBgPrimary,
    onBackground = LightTextPrimary,

    surface = LightBgSurface,
    onSurface = LightTextPrimary,

    surfaceVariant = LightBgSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,

    error = SemanticError,
    onError = LightTextInverse,
    errorContainer = SemanticError.copy(alpha = 0.12f),
    onErrorContainer = SemanticError,

    outline = LightOutlineColor,
    outlineVariant = LightOutlineVariantColor,

    inverseSurface = LightTextPrimary,
    inverseOnSurface = LightBgPrimary,
    inversePrimary = AccentPrimary,

    surfaceContainerLowest = LightBgPrimary,
    surfaceContainerLow = LightBgSurface,
    surfaceContainer = LightBgSurfaceVariant,
    surfaceContainerHigh = LightBgSurfaceElevated,
    surfaceContainerHighest = LightBgSurfaceElevated,

    scrim = LightScrimColor
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
