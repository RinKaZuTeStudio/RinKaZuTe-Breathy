package com.breathy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ── Background Colors ────────────────────────────────────────────────────────
// Three-tier depth system: deepest → surface → elevated
// Creates perceptible depth through luminance stepping rather than shadows.

/** Full-screen background, behind all surfaces. */
val BgPrimary = Color(0xFF0D1117)

/** Card backgrounds, bottom sheets, dialog surfaces. */
val BgSurface = Color(0xFF161B22)

/** Elevated sub-surfaces: input fields, nested cards, dividers. */
val BgSurfaceVariant = Color(0xFF1C2128)

/** Highest elevation surface — modal overlays, dropdown menus. */
val BgSurfaceElevated = Color(0xFF21262D)

// Backward-compat aliases (previous Color.kt used different hex values)
val SurfaceColor = BgSurface
val SurfaceVariant = BgSurfaceVariant

// ── Neon Accent Colors ───────────────────────────────────────────────────────
// Each neon accent is assigned a specific semantic role:
//   Green → progress/success   Red → warnings/cravings   Blue → info/links
//   Yellow → caution           Purple → premium/XP        Orange → cravings/urgency
//   Pink → social/likes

/** Primary Neon Green — progress bars, success states, CTA buttons. */
val AccentPrimary = Color(0xFF00E676)

/** Primary pressed state — 10% lighter. */
val AccentPrimaryPressed = Color(0xFF33EB91)

/** Secondary Neon Red — warnings, cravings, urgency indicators. */
val AccentSecondary = Color(0xFFFF6B6B)

/** Info Blue — links, secondary actions, informational badges. */
val AccentInfo = Color(0xFF4FC3F7)

/** Warning Yellow — caution banners, rate-limit notices. */
val AccentWarning = Color(0xFFFFD54F)

/** Purple — premium features, achievement badges, XP. */
val AccentPurple = Color(0xFFAB47BC)

/** Orange — cravings, urgency, warnings. */
val AccentOrange = Color(0xFFFF9100)

/** Pink — likes, hearts, social affirmation. */
val AccentPink = Color(0xFFFF4081)

// Legacy aliases from previous Color.kt
val NeonGreen = AccentPrimary
val NeonBlue = AccentInfo
val NeonPurple = AccentPurple
val NeonOrange = AccentOrange
val NeonPink = AccentPink

// ── Text Colors ──────────────────────────────────────────────────────────────
// Three-level emphasis model matching Material Design opacity approach
// but using fixed hex values for consistency across surfaces.

/** Headlines, body text, high-emphasis labels. */
val TextPrimary = Color(0xFFE6EDF3)

/** Subtitles, helper text, metadata, timestamps. */
val TextSecondary = Color(0xFF8B949E)

/** Disabled states, placeholder text, inactive tabs. */
val TextDisabled = Color(0xFF484F58)

/** Inverse text — for use on light/accent backgrounds. */
val TextInverse = Color(0xFF0D1117)

// ── Semantic Colors ──────────────────────────────────────────────────────────

/** Error states, form validation failures, destructive actions. */
val SemanticError = Color(0xFFFF5252)

/** Success toasts, completed milestones (same as AccentPrimary). */
val SemanticSuccess = AccentPrimary

/** Warning banners, rate-limit notices, cautionary prompts. */
val SemanticWarning = AccentWarning

// ── Achievement Colors ───────────────────────────────────────────────────────

/** Gold — top rank, premium achievements. */
val AchievementGold = Color(0xFFFFD700)

/** Silver — second rank. */
val AchievementSilver = Color(0xFFC0C0C0)

/** Bronze — third rank. */
val AchievementBronze = Color(0xFFCD7F32)

// ── Outline & Divider Colors ─────────────────────────────────────────────────

/** Card outlines, border strokes. */
val OutlineColor = Color(0xFF21262D)

/** Subtle dividers, hairlines. */
val OutlineVariantColor = Color(0xFF1C2128)

/** Neon border — thin accent-primary stroke for cards (30% opacity). */
val OutlineNeon = Color(0x4D00E676)

// ── Scrim & Overlay ──────────────────────────────────────────────────────────

/** Modal scrim — 60% opacity primary background. */
val ScrimColor = Color(0x990D1117)

/** Dark overlay for image readability (80% opacity). */
val OverlayDark = Color(0xCC0D1117)

// ── Utility ──────────────────────────────────────────────────────────────────

/** Transparent color for no-fill cases. */
val Transparent = Color(0x00000000)

// ── Light Mode Colors ──────────────────────────────────────────────────────────

/** Full-screen background, behind all surfaces (light mode). */
val LightBgPrimary = Color(0xFFFFFFFF)

/** Card backgrounds, bottom sheets, dialog surfaces (light mode). */
val LightBgSurface = Color(0xFFF5F5F5)

/** Elevated sub-surfaces (light mode). */
val LightBgSurfaceVariant = Color(0xFFEEEEEE)

/** Highest elevation surface (light mode). */
val LightBgSurfaceElevated = Color(0xFFE0E0E0)

/** Headlines, body text (light mode). */
val LightTextPrimary = Color(0xFF1A1A1A)

/** Subtitles, helper text (light mode). */
val LightTextSecondary = Color(0xFF666666)

/** Disabled states (light mode). */
val LightTextDisabled = Color(0xFF9E9E9E)

/** Inverse text — for use on dark/accent backgrounds (light mode). */
val LightTextInverse = Color(0xFFFFFFFF)

/** Primary accent for light mode — slightly darker green for contrast. */
val LightAccentPrimary = Color(0xFF00C853)

/** Primary container for light mode — light green surface for primary-themed containers. */
val LightAccentPrimaryContainer = Color(0xFFC8E6C9)

/** On primary container for light mode — dark green text on primary container surfaces. */
val LightOnPrimaryContainer = Color(0xFF1B5E20)

/** Card outlines (light mode). */
val LightOutlineColor = Color(0xFFE0E0E0)

/** Subtle dividers (light mode). */
val LightOutlineVariantColor = Color(0xFFEEEEEE)

/** Modal scrim (light mode). */
val LightScrimColor = Color(0x99FFFFFF)

// ═══════════════════════════════════════════════════════════════════════════════
//  Theme-aware Color Accessors
//  These read from MaterialTheme.colorScheme so they automatically switch
//  between dark and light mode. Screens should prefer these over the
//  hardcoded dark-mode constants above.
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

/** Theme-aware primary accent color. Replaces hardcoded AccentPrimary.
 *  Uses darker green (#00C853) in light mode for better contrast on white. */
val themeAccentPrimary: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

/** Theme-aware primary accent color at 10% opacity for backgrounds. */
val themeAccentPrimaryMuted: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer

/** Theme-aware purple accent. Replaces hardcoded AccentPurple. */
val themeAccentPurple: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.tertiary

/** Theme-aware error color. Replaces hardcoded SemanticError. */
val themeErrorColor: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error
