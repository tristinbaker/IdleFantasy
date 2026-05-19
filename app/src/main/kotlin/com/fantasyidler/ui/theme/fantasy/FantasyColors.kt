package com.fantasyidler.ui.theme.fantasy

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.fantasyidler.ui.theme.BrownContainer
import com.fantasyidler.ui.theme.BrownContainerLight
import com.fantasyidler.ui.theme.BrownSecondary
import com.fantasyidler.ui.theme.DarkBackground
import com.fantasyidler.ui.theme.DarkSurface
import com.fantasyidler.ui.theme.DarkSurfaceHigh
import com.fantasyidler.ui.theme.DarkSurfaceVariant
import com.fantasyidler.ui.theme.DarkText
import com.fantasyidler.ui.theme.DarkTextMuted
import com.fantasyidler.ui.theme.ErrorRed
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.theme.LightBackground
import com.fantasyidler.ui.theme.LightSurface
import com.fantasyidler.ui.theme.LightSurfaceVariant
import com.fantasyidler.ui.theme.ParchmentText
import com.fantasyidler.ui.theme.ParchmentTextMuted
import com.fantasyidler.ui.theme.SuccessGreen
import com.fantasyidler.ui.theme.TierAdamant
import com.fantasyidler.ui.theme.TierBronze
import com.fantasyidler.ui.theme.TierDragon
import com.fantasyidler.ui.theme.TierIron
import com.fantasyidler.ui.theme.TierMithril
import com.fantasyidler.ui.theme.TierRune
import com.fantasyidler.ui.theme.TierSteel
import com.fantasyidler.ui.theme.WarningAmber

/**
 * Semantic color tokens for the FantasyTheme. Wraps the raw palette in [Color.kt]
 * so foundation primitives never reach for raw `GoldPrimary` / `DarkSurface`
 * names — they consume tokens.colors.primary, tokens.colors.surface, etc.
 */
@Immutable
data class FantasyColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceHigh: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val border: Color,
    val tierBronze: Color,
    val tierIron: Color,
    val tierSteel: Color,
    val tierMithril: Color,
    val tierAdamant: Color,
    val tierRune: Color,
    val tierDragon: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val isDark: Boolean,
)

/** Look up a tier's brand color by tier index (0 = bronze, 6 = dragon). */
fun FantasyColors.tier(index: Int): Color = when (index) {
    0 -> tierBronze
    1 -> tierIron
    2 -> tierSteel
    3 -> tierMithril
    4 -> tierAdamant
    5 -> tierRune
    6 -> tierDragon
    else -> primary
}

/** Dark variant — the default in-game palette (parchment-on-midnight-blue). */
val DarkFantasyColors: FantasyColors = FantasyColors(
    background        = DarkBackground,
    surface           = DarkSurface,
    surfaceVariant    = DarkSurfaceVariant,
    surfaceHigh       = DarkSurfaceHigh,
    onSurface         = ParchmentText,
    onSurfaceMuted    = ParchmentTextMuted,
    primary           = GoldPrimary,
    onPrimary         = DarkBackground,
    secondary         = BrownSecondary,
    secondaryContainer = BrownContainer,
    border            = GoldPrimary,
    tierBronze        = TierBronze,
    tierIron          = TierIron,
    tierSteel         = TierSteel,
    tierMithril       = TierMithril,
    tierAdamant       = TierAdamant,
    tierRune          = TierRune,
    tierDragon        = TierDragon,
    success           = SuccessGreen,
    warning           = WarningAmber,
    error             = ErrorRed,
    isDark            = true,
)

/** Light variant — warm-parchment alternative. */
val LightFantasyColors: FantasyColors = FantasyColors(
    background        = LightBackground,
    surface           = LightSurface,
    surfaceVariant    = LightSurfaceVariant,
    surfaceHigh       = LightSurface,
    onSurface         = DarkText,
    onSurfaceMuted    = DarkTextMuted,
    primary           = GoldPrimary,
    onPrimary         = DarkText,
    secondary         = BrownSecondary,
    secondaryContainer = BrownContainerLight,
    border            = GoldPrimary,
    tierBronze        = TierBronze,
    tierIron          = TierIron,
    tierSteel         = TierSteel,
    tierMithril       = TierMithril,
    tierAdamant       = TierAdamant,
    tierRune          = TierRune,
    tierDragon        = TierDragon,
    success           = SuccessGreen,
    warning           = WarningAmber,
    error             = ErrorRed,
    isDark            = false,
)
