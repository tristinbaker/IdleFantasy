package com.fantasyidler.ui.theme.fantasy

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The top-level immutable bundle of every FantasyTheme design token. Foundation
 * primitives consume [LocalFantasyTokens]`.current.<group>.<token>` and never
 * reach for raw palette values or .dp literals.
 */
@Immutable
data class FantasyTokens(
    val colors: FantasyColors,
    val typography: FantasyTypography,
    val shapes: FantasyShapes,
    val spacing: FantasySpacing,
    val elevation: FantasyElevation,
    val motion: FantasyMotionTokens,
)

/**
 * Indirection layer so the foundation primitives can reach FantasyMotion via
 * tokens without taking a direct dependency on the `ui.motion` package every
 * time. The single instance points at the canonical [com.fantasyidler.ui.motion.FantasyMotion]
 * object — there is no per-theme override of motion.
 */
@Immutable
object FantasyMotionTokens {
    val snappy = com.fantasyidler.ui.motion.FantasyMotion.Snappy
    val smooth = com.fantasyidler.ui.motion.FantasyMotion.Smooth
    val bouncy = com.fantasyidler.ui.motion.FantasyMotion.Bouncy
    val idle = com.fantasyidler.ui.motion.FantasyMotion.Idle
}

/** Default dark-theme tokens — used as the [staticCompositionLocalOf] fallback. */
val DefaultDarkTokens: FantasyTokens = FantasyTokens(
    colors     = DarkFantasyColors,
    typography = DefaultFantasyTypography,
    shapes     = DefaultFantasyShapes,
    spacing    = DefaultFantasySpacing,
    elevation  = DefaultFantasyElevation,
    motion     = FantasyMotionTokens,
)

/** Default light-theme tokens. */
val DefaultLightTokens: FantasyTokens = FantasyTokens(
    colors     = LightFantasyColors,
    typography = DefaultFantasyTypography,
    shapes     = DefaultFantasyShapes,
    spacing    = DefaultFantasySpacing,
    elevation  = DefaultFantasyElevation,
    motion     = FantasyMotionTokens,
)

/** CompositionLocal handed out by [FantasyTheme]. */
val LocalFantasyTokens: ProvidableCompositionLocal<FantasyTokens> =
    staticCompositionLocalOf { DefaultDarkTokens }
