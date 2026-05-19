package com.fantasyidler.ui.theme.fantasy

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Drop-shadow offsets in whole pixels, used via [androidx.compose.ui.draw.shadow]
 * rather than Material elevation tints — chunky pixel art demands hard offsets,
 * not blurred fills.
 */
@Immutable
data class FantasyElevation(
    val card: Dp  = 2.dp,
    val hero: Dp  = 4.dp,
    val sheet: Dp = 8.dp,
)

/** Default elevation instance. */
val DefaultFantasyElevation: FantasyElevation = FantasyElevation()
