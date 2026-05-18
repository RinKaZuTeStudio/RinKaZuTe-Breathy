package breathy.com.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import breathy.com.R

// ── Font Families ──────────────────────────────────────────────────────────
// Custom fonts (Montserrat, Inter, Space Mono) require .ttf files in
// res/font/. Until font resource files are added, we use the platform
// default font families as fallbacks.
//
// To activate custom fonts:
//   1. Place .ttf files in app/src/main/res/font/
//   2. Replace the FontFamily.SansSerif / FontFamily.Monospace assignments
//      below with the commented-out Font(R.font.xxx) definitions.

val MontserratFontFamily = FontFamily.SansSerif
// val MontserratFontFamily = FontFamily(
//     Font(R.font.montserrat_bold, FontWeight.Bold),
//     Font(R.font.montserrat_semibold, FontWeight.SemiBold),
//     Font(R.font.montserrat_medium, FontWeight.Medium),
//     Font(R.font.montserrat_regular, FontWeight.Normal)
// )

val InterFontFamily = FontFamily.SansSerif
// val InterFontFamily = FontFamily(
//     Font(R.font.inter_bold, FontWeight.Bold),
//     Font(R.font.inter_semibold, FontWeight.SemiBold),
//     Font(R.font.inter_medium, FontWeight.Medium),
//     Font(R.font.inter_regular, FontWeight.Normal),
//     Font(R.font.inter_light, FontWeight.Light)
// )

val SpaceMonoFontFamily = FontFamily.Monospace
// val SpaceMonoFontFamily = FontFamily(
//     Font(R.font.space_mono_bold, FontWeight.Bold),
//     Font(R.font.space_mono_regular, FontWeight.Normal)
// )

// ── Typography Scale ───────────────────────────────────────────────────────
// Matches the UI/UX spec exactly:
//   Headlines → Montserrat Bold (geometric, confident)
//   Body → Inter Regular (optimized for screen, exceptional legibility)
//   Stats → Space Mono Bold (monospaced for counter stability)
//
// Line heights: 1.33× for headlines, 1.5× for body text
// Letter spacing: tightens for large headlines, opens for small captions

object BreathyTypography {
    // ── Headlines (Montserrat Bold) ────────────────────────────────────────

    /** Screen titles, onboarding step titles. 24sp / 32sp line height. */
    val headlineLarge = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.5).sp
    )

    /** Section headers, card titles. 20sp / 28sp line height. */
    val headlineMedium = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.25).sp
    )

    /** Sub-sections, bottom sheet headers. 18sp / 24sp line height. */
    val headlineSmall = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    )

    // ── Body (Inter Regular) ───────────────────────────────────────────────

    /** Primary body text, story content. 16sp / 24sp line height. */
    val bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    )

    /** Secondary text, list items, captions. 14sp / 20sp line height. */
    val bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    )

    // ── Captions (Inter Light) ─────────────────────────────────────────────

    /** Timestamps, helper text, chip labels. 12sp / 16sp line height. */
    val captionLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    )

    /** Legal text, fine print, badge labels. 10sp / 14sp line height. */
    val captionSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )

    // ── Stat Numbers (Space Mono Bold) ─────────────────────────────────────

    /** Hero numbers (days smoke-free). 48sp / 56sp line height. */
    val statHero = TextStyle(
        fontFamily = SpaceMonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-1).sp
    )

    /** Card stat numbers (money, cigarettes). 32sp / 40sp line height. */
    val statCard = TextStyle(
        fontFamily = SpaceMonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    )

    // ── Additional Utility Styles ──────────────────────────────────────────

    /** Button text — Montserrat SemiBold for clear CTA labels. */
    val button = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    )

    /** Small button text — for compact CTAs. */
    val buttonSmall = TextStyle(
        fontFamily = MontserratFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    )

    /** Overline text — category labels above sections. */
    val overline = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.5.sp
    )

    /** Tab label text — navigation bar and tab row labels. */
    val tabLabel = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
}
