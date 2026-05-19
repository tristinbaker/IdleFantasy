package com.fantasyidler.ui.components.foundation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.ui.motion.pressScale
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/**
 * Unified chunky card primitive — surface + gold border + optional press-scale
 * clickable. Every row, recipe, quest, dungeon, etc. wraps in this so visual
 * rhythm is enforced by the API.
 *
 * @param highlight when true, stronger gold border + warmer tint — used for
 *                  active sessions, claimable rewards, recommended slots.
 * @param onClick   null = non-interactive; non-null = press-scale + ripple.
 */
@Composable
fun ChunkyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlight: Boolean = false,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(LocalFantasyTokens.current.spacing.l),
    content: @Composable () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val tap = onClick?.takeIf { enabled }

    val baseColor = tokens.colors.surfaceVariant
    val surfaceColor = if (highlight) tokens.colors.primary.copy(alpha = 0.10f).compositeOver(baseColor)
                       else baseColor
    val borderAlpha = if (highlight) 0.42f else 0.18f

    Surface(
        shape    = tokens.shapes.card,
        color    = surfaceColor,
        border   = BorderStroke(tokens.shapes.hairlineStroke, tokens.colors.primary.copy(alpha = borderAlpha)),
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

/** Pre-composites a translucent overlay onto an opaque base so the result is opaque. */
internal fun Color.compositeOver(base: Color): Color {
    val a = this.alpha
    return Color(
        red   = this.red   * a + base.red   * (1f - a),
        green = this.green * a + base.green * (1f - a),
        blue  = this.blue  * a + base.blue  * (1f - a),
        alpha = 1f,
    )
}

@PreviewLightDark
@Composable
private fun PreviewChunkyCard() {
    FantasyPreviewSurface {
        ChunkyCard {
            Text("Chunky card sample")
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewChunkyCardHighlight() {
    FantasyPreviewSurface {
        ChunkyCard(highlight = true, onClick = {}) {
            Text("Highlighted, tappable")
        }
    }
}
