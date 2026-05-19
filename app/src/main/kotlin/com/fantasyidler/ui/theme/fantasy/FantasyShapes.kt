package com.fantasyidler.ui.theme.fantasy

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shape tokens. The default [card] matches the current ChunkyCard radius so
 * the spine swap is visually a no-op; sheets use top-rounded corners so they
 * read as anchored to the bottom edge.
 */
@Immutable
data class FantasyShapes(
    val card: Shape          = RoundedCornerShape(16.dp),
    val sheet: Shape         = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
    val button: Shape        = RoundedCornerShape(12.dp),
    val chip: Shape          = RoundedCornerShape(8.dp),
    val badge: Shape         = CircleShape,
    val hairlineStroke: Dp   = 1.dp,
    val borderStroke: Dp     = 2.dp,
)

/** Default shapes — the chunky pixel-art set. */
val DefaultFantasyShapes: FantasyShapes = FantasyShapes()

/**
 * Draws a single-color border around the receiver. The chunky look uses
 * 1.dp on small surfaces and 2.dp on hero-scale ones; pick the width per
 * usage rather than baking it into the token.
 */
fun Modifier.chunkyBorder(
    width: Dp = 1.dp,
    color: Color,
    shape: Shape = RoundedCornerShape(CornerSize(0.dp)),
): Modifier = this.border(BorderStroke(width, color), shape)

/** Composable-scope overload that pulls the default border color from tokens. */
@Composable
fun Modifier.chunkyBorder(
    width: Dp = 1.dp,
    shape: Shape = RoundedCornerShape(CornerSize(0.dp)),
): Modifier = this.chunkyBorder(width = width, color = LocalFantasyTokens.current.colors.border, shape = shape)
