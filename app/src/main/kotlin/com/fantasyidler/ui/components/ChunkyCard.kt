package com.fantasyidler.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fantasyidler.ui.motion.pressScale
import com.fantasyidler.ui.theme.GoldPrimary

/**
 * The unified chunky card primitive — replaces ad-hoc Surface + RoundedCornerShape
 * + manual border + clickable scaffolding scattered across the screens. Every
 * list row, recipe, quest, dungeon, etc. should be wrapped in this so the
 * visual rhythm is enforced by the API rather than vigilance.
 *
 * @param highlight when true, paints a 1dp gold border + slightly warmer surface
 *                  tint — used for active sessions, claimable rewards,
 *                  recommended slots.
 * @param onClick   null = non-interactive card; non-null = press-scale + ripple.
 */
@Composable
fun ChunkyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlight: Boolean = false,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val tap = onClick?.takeIf { enabled }

    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val surfaceColor = if (highlight) GoldPrimary.copy(alpha = 0.10f).compositeOver(baseColor)
                       else baseColor
    val borderAlpha = when {
        highlight -> 0.42f
        else      -> 0.18f
    }

    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = surfaceColor,
        border   = BorderStroke(1.dp, GoldPrimary.copy(alpha = borderAlpha)),
        modifier = modifier
            .fillMaxWidth()
            .then(if (tap != null) Modifier.pressScale(interactionSource) else Modifier)
            .then(
                if (tap != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    enabled = enabled,
                    onClick = tap,
                ) else Modifier
            ),
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

/**
 * Pre-composite a translucent overlay onto an opaque base so the resulting
 * surface colour is opaque — avoids the alpha-on-surface flicker that
 * Material3 Surface tints can introduce in dark theme.
 */
private fun Color.compositeOver(base: Color): Color {
    val a = this.alpha
    return Color(
        red   = this.red   * a + base.red   * (1f - a),
        green = this.green * a + base.green * (1f - a),
        blue  = this.blue  * a + base.blue  * (1f - a),
        alpha = 1f,
    )
}
