package com.fantasyidler.ui.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

/**
 * Scales the receiver to [pressedScale] while [interactionSource] is pressed, springing
 * back when released. Caller must pass the same [interactionSource] to whichever
 * clickable / Button / Surface owns the press, so they share state.
 *
 * Usage:
 * ```
 * val source = remember { MutableInteractionSource() }
 * Row(modifier = Modifier
 *     .pressScale(source)
 *     .clickable(interactionSource = source, indication = LocalIndication.current, onClick = ...))
 * ```
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.96f,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessMediumLow,
        ),
        label = "pressScale",
    )
    return this.scale(scale)
}
