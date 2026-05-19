package com.fantasyidler.ui.components.foundation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.ui.motion.pressScale
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/** Visual variants for [ChunkyButton]. */
enum class ChunkyButtonVariant { Primary, Secondary, Ghost, Destructive }

/**
 * Slot-API chunky button — gold-bordered tappable surface with press-scale,
 * leading icon slot, label, optional trailing slot. Use this in place of
 * [androidx.compose.material3.Button] / OutlinedButton.
 */
@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ChunkyButtonVariant = ChunkyButtonVariant.Primary,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tokens = LocalFantasyTokens.current
    val interactionSource = remember { MutableInteractionSource() }

    val container: Color
    val onContainer: Color
    val borderColor: Color
    when (variant) {
        ChunkyButtonVariant.Primary -> {
            container   = tokens.colors.primary
            onContainer = tokens.colors.onPrimary
            borderColor = tokens.colors.primary
        }
        ChunkyButtonVariant.Secondary -> {
            container   = Color.Transparent
            onContainer = tokens.colors.primary
            borderColor = tokens.colors.primary
        }
        ChunkyButtonVariant.Ghost -> {
            container   = Color.Transparent
            onContainer = tokens.colors.onSurface
            borderColor = Color.Transparent
        }
        ChunkyButtonVariant.Destructive -> {
            container   = tokens.colors.error.copy(alpha = 0.15f)
            onContainer = tokens.colors.error
            borderColor = tokens.colors.error
        }
    }

    Surface(
        shape  = tokens.shapes.button,
        color  = container,
        border = if (borderColor == Color.Transparent) null
                 else BorderStroke(tokens.shapes.hairlineStroke, borderColor),
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.m),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m + tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(tokens.spacing.m))
            }
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text       = text,
                    style      = tokens.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = onContainer,
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(tokens.spacing.m))
                trailing()
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewChunkyButtonPrimary() {
    FantasyPreviewSurface {
        ChunkyButton(text = "Confirm", onClick = {}, variant = ChunkyButtonVariant.Primary)
    }
}

@PreviewLightDark
@Composable
private fun PreviewChunkyButtonSecondary() {
    FantasyPreviewSurface {
        ChunkyButton(text = "Cancel", onClick = {}, variant = ChunkyButtonVariant.Secondary)
    }
}

@PreviewLightDark
@Composable
private fun PreviewChunkyButtonGhost() {
    FantasyPreviewSurface {
        ChunkyButton(text = "Maybe later", onClick = {}, variant = ChunkyButtonVariant.Ghost)
    }
}

@PreviewLightDark
@Composable
private fun PreviewChunkyButtonDestructive() {
    FantasyPreviewSurface {
        ChunkyButton(text = "Delete save", onClick = {}, variant = ChunkyButtonVariant.Destructive)
    }
}
