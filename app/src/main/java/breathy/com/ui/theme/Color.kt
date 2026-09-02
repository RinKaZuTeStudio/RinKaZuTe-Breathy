package breathy.com.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════════
//  SAGE NATURE + BOTANICAL WELLNESS + PREMIUM MINIMALISM palette
//
//  Visual balance targets (across every screen):
//    60–70% white / warm white
//    20–25% light sage / green
//     5–10% deep green + natural accents
//
//  Symbol names are intentionally UNCHANGED from the previous design system so
//  every existing screen keeps compiling — the values carry the new identity.
//  The app is intentionally light-first: a calm, clean, fresh, premium
//  wellness product. (Dark mode renders the same light botanical scheme.)
// ═══════════════════════════════════════════════════════════════════════════════

// ── Core Sage Naturue Values ────────────────────────────────────────────────

val PureWhite = Color(0xFFFFFFFF)
val WarmWhite = Color(0xFFF8FAF6)
val VeryLightSage = Color(0xFFEAF3E5)
val SoftSage = Color(0xFFD5E6CC)
val MediumSage = Color(0xFFAFC8A3)
val NaturalGreen = Color(0xFF6F9B5E)
val DeepForest = Color(0xFF285C3A)
val DarkBotanical = Color(0xFF183D28)
val SoftSand = Color(0xFFE8DFC9)
val WarmEarth = Color(0xFFA9825B)
val SoftSky = Color(0xFFDCECF0)
val NaturalYellow = Color(0xFFF1D98A)

// ── Background Colors ────────────────────────────────────────────────────────
// Light botanical depth system: warm white canvas → pure white surfaces →
// very light sage tinted variants.

/** Full-screen background, behind all surfaces — warm white. */
val BgPrimary = WarmWhite

/** Card backgrounds, bottom sheets, dialog surfaces — pure white. */
val BgSurface = PureWhite

/** Tinted variant — very light sage. */
val BgSurfaceVariant = VeryLightSage

/** Elevated surface — pure white with stronger elevation shadows. */
val BgSurfaceElevated = PureWhite

val SurfaceColor = BgSurface
val SurfaceVariant = BgSurfaceVariant

// ── Accent Colors ───────────────────────────────────────────────────────────

/** Primary accent — natural green (CTAs, actions, progress). */
val AccentPrimary = NaturalGreen

/** Pressed state — slightly deeper natural green. */
val AccentPrimaryPressed = Color(0xFF5D874E)

/** Secondary accent — warm earth for supportive highlights. */
val AccentSecondary = WarmEarth

/** Informational accent — muted sky blue-green. */
val AccentInfo = Color(0xFF6FA6B5)

/** Warning accent — natural yellow. */
val AccentWarning = NaturalYellow

// ── Gold currency tokens (in-app Gold identity, derived from Natural Yellow) ──
/** Soft gold wash for Gold balance pills and reward highlights. */
val GoldSoft = Color(0xFFFAF0D3)
/** Deep gold for Gold amounts and icons on soft backgrounds. */
val GoldDeep = Color(0xFF9A7B2E)

/** "Purple" slot is remapped to DEEP FOREST GREEN — the premium identity. */
val AccentPurple = DeepForest

/** Orange slot — warm earth (used sparingly for streaks/energy). */
val AccentOrange = WarmEarth

/** Pink slot — muted terracotta for likes/hearts. */
val AccentPink = Color(0xFFC97F6D)

// Legacy aliases (kept so existing references compile)
val NeonGreen = AccentPrimary
val NeonBlue = AccentInfo
val NeonPurple = AccentPurple
val NeonOrange = AccentOrange
val NeonPink = AccentPink

// ── Text Colors ─────────────────────────────────────────────────────────────

/** Primary text — dark botanical (high contrast on warm white). */
val TextPrimary = DarkBotanical

/** Secondary text — sage gray-green. */
val TextSecondary = Color(0xFF5C6B5E)

/** Disabled text. */
val TextDisabled = Color(0xFF9AA79B)

/** Text on colored (green) surfaces — pure white. */
val TextInverse = PureWhite

// ── Semantic Colors ─────────────────────────────────────────────────────────

/** Error — muted natural red (calm, non-alarming). */
val SemanticError = Color(0xFFC0574F)

val SemanticSuccess = AccentPrimary

val SemanticWarning = AccentWarning

// ── Achievement Metals ──────────────────────────────────────────────────────

val AchievementGold = Color(0xFFD9B45B)
val AchievementSilver = Color(0xFFAEB6B2)
val AchievementBronze = Color(0xFFB98A5E)

// ── Outlines & Overlays ─────────────────────────────────────────────────────

/** Hairline borders on white cards. */
val OutlineColor = SoftSage

val OutlineVariantColor = VeryLightSage

/** Subtle accent outline (formerly neon). */
val OutlineNeon = AccentPrimary.copy(alpha = 0.25f)

val ScrimColor = Color(0x66183D28)

val OverlayDark = Color(0xCC183D28)

val Transparent = Color(0x00000000)

// ── Light-Scheme Tokens (mirror the same sage identity) ─────────────────────

val LightBgPrimary = WarmWhite
val LightBgSurface = PureWhite
val LightBgSurfaceVariant = VeryLightSage
val LightBgSurfaceElevated = PureWhite
val LightTextPrimary = DarkBotanical
val LightTextSecondary = Color(0xFF5C6B5E)
val LightTextDisabled = Color(0xFF9AA79B)
val LightTextInverse = PureWhite
val LightAccentPrimary = NaturalGreen
val LightAccentPrimaryContainer = VeryLightSage
val LightOnPrimaryContainer = DarkBotanical
val LightOutlineColor = SoftSage
val LightOutlineVariantColor = VeryLightSage
val LightScrimColor = ScrimColor

// ═══════════════════════════════════════════════════════════════════════════════
//  BreathyPalette — named design tokens for new/updated components
// ═══════════════════════════════════════════════════════════════════════════════

object BreathyPalette {
    val pureWhite = PureWhite
    val warmWhite = WarmWhite
    val veryLightSage = VeryLightSage
    val softSage = SoftSage
    val mediumSage = MediumSage
    val naturalGreen = NaturalGreen
    val deepForest = DeepForest
    val darkBotanical = DarkBotanical
    val softSand = SoftSand
    val warmEarth = WarmEarth
    val softSky = SoftSky
    val naturalYellow = NaturalYellow
    val textPrimary = TextPrimary
    val textSecondary = TextSecondary
    val textDisabled = TextDisabled
    val textInverse = TextInverse
    val error = SemanticError

    /** Soft radial gradient behind default avatars. */
    val defaultAvatarGradient = listOf(SoftSage.copy(alpha = 0.55f), VeryLightSage)
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Theme-aware Color Accessors
//  These read from MaterialTheme.colorScheme so they automatically switch
//  between dark and light mode. Screens should prefer these over the
//  hardcoded constants above.
// ═══════════════════════════════════════════════════════════════════════════════

/** Theme-aware background color. Replaces hardcoded BgPrimary. */
val themeBgPrimary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background

/** Theme-aware surface color. Replaces hardcoded BgSurface. */
val themeBgSurface: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface

/** Theme-aware surface variant color. Replaces hardcoded BgSurfaceVariant. */
val themeBgSurfaceVariant: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant

/** Theme-aware elevated surface color. Replaces hardcoded BgSurfaceElevated. */
val themeBgSurfaceElevated: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHigh

/** Theme-aware primary text color. Replaces hardcoded TextPrimary. */
val themeTextPrimary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onBackground

/** Theme-aware secondary text color. Replaces hardcoded TextSecondary. */
val themeTextSecondary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** Theme-aware disabled text color. Replaces hardcoded TextDisabled. */
val themeTextDisabled: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f)

/** Theme-aware inverse text color. Replaces hardcoded TextInverse. */
val themeTextInverse: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.inverseSurface

/** Theme-aware outline color. Replaces hardcoded OutlineColor. */
val themeOutlineColor: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outline

/** Theme-aware outline variant color. Replaces hardcoded OutlineVariantColor. */
val themeOutlineVariantColor: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.outlineVariant

/** Theme-aware primary accent color. Replaces hardcoded AccentPrimary. */
val themeAccentPrimary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

/** Theme-aware primary accent color at container opacity for backgrounds. */
val themeAccentPrimaryMuted: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer

/** Theme-aware premium accent (deep forest green). Replaces hardcoded AccentPurple. */
val themeAccentPurple: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary

/** Theme-aware error color. Replaces hardcoded SemanticError. */
val themeErrorColor: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error
