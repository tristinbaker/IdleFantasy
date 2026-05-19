package com.fantasyidler.ui.theme.fantasy

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 8-pixel-grid spacing scale plus a canonical screen gutter. */
@Immutable
data class FantasySpacing(
    val xs: Dp   = 2.dp,
    val s: Dp    = 4.dp,
    val m: Dp    = 8.dp,
    val l: Dp    = 16.dp,
    val xl: Dp   = 24.dp,
    val xxl: Dp  = 32.dp,
    val gutter: Dp = 16.dp,
)

/** Default spacing instance — pinned to the 8-pixel grid. */
val DefaultFantasySpacing: FantasySpacing = FantasySpacing()
