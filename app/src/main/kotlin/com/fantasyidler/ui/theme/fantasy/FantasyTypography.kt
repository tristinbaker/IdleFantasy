package com.fantasyidler.ui.theme.fantasy

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Seven-step type ramp covering the screens. Display slots use the pixel
 * face; body slots use the legible sans-serif face. Both currently resolve
 * via [FontFamily] substitutes (system monospace + sans-serif) — see commit
 * footer for the substitution; once `fantasy_display.ttf` and
 * `fantasy_body_*.otf` are bundled, swap [pixelDisplayFamily] /
 * [legibleBodyFamily] to point at them.
 */
@Immutable
data class FantasyTypography(
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val headlineLarge: TextStyle,
    val titleLarge: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val labelSmall: TextStyle,
)

/**
 * The pixel display face. Substitute: [FontFamily.Monospace]. When
 * `R.font.fantasy_display` is bundled, change this to
 * `FontFamily(Font(R.font.fantasy_display))`.
 */
val pixelDisplayFamily: FontFamily = FontFamily.Monospace

/**
 * The legible body face. Substitute: [FontFamily.SansSerif]. When the
 * Atkinson Hyperlegible files are bundled, change this to
 * `FontFamily(Font(R.font.fantasy_body_regular), Font(R.font.fantasy_body_bold, FontWeight.Bold))`.
 */
val legibleBodyFamily: FontFamily = FontFamily.SansSerif

/** Default typography — the seven-step ramp. */
val DefaultFantasyTypography: FantasyTypography = FantasyTypography(
    displayLarge = TextStyle(
        fontFamily = pixelDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 40.sp,
        letterSpacing = 0.5.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = pixelDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 30.sp,
        letterSpacing = 0.5.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = pixelDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 24.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = pixelDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 18.sp,
        letterSpacing = 0.3.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = legibleBodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = legibleBodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = pixelDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize   = 11.sp,
        letterSpacing = 0.8.sp,
    ),
)
