package com.fantasyidler.ui.components.foundation

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.ui.motion.pressScale
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/**
 * Big tactile stepper for transactional sheets: craft qty, plant qty, shop
 * buy/sell qty. Layout is `[ − ] [ qty ] [ + ]` with optional MAX pill.
 */
@Composable
fun BigStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minValue: Int = 1,
    maxValue: Int = Int.MAX_VALUE,
    step: Int = 1,
    onMax: (() -> Unit)? = null,
) {
    val tokens = LocalFantasyTokens.current
    val canDecrement = value > minValue
    val canIncrement = value < maxValue

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(
            icon = Icons.Filled.Remove,
            contentDescription = "Decrease",
            enabled = canDecrement,
            onClick = { onValueChange((value - step).coerceAtLeast(minValue)) },
        )
        Spacer(Modifier.width(tokens.spacing.m + tokens.spacing.s))
        Surface(
            shape = tokens.shapes.button,
            color = tokens.colors.surface,
            modifier = Modifier
                .defaultMinSize(minWidth = tokens.spacing.xxl + tokens.spacing.xxl + tokens.spacing.xxl - tokens.spacing.m)
                .size(
                    width  = tokens.spacing.xxl + tokens.spacing.xxl + tokens.spacing.xxl - tokens.spacing.m,
                    height = tokens.spacing.xxl + tokens.spacing.l,
                ),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text       = value.toString(),
                    style      = tokens.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color      = tokens.colors.primary,
                    textAlign  = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.width(tokens.spacing.m + tokens.spacing.s))
        StepperButton(
            icon = Icons.Filled.Add,
            contentDescription = "Increase",
            enabled = canIncrement,
            onClick = { onValueChange((value + step).coerceAtMost(maxValue)) },
        )
        if (onMax != null) {
            Spacer(Modifier.width(tokens.spacing.m + tokens.spacing.s))
            Surface(
                shape = tokens.shapes.button,
                color = tokens.colors.primary.copy(alpha = 0.18f),
                modifier = Modifier.size(
                    width  = tokens.spacing.xxl + tokens.spacing.xl,
                    height = tokens.spacing.xxl + tokens.spacing.l,
                ),
            ) {
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .pressScale(interactionSource)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = onMax,
                        ),
                ) {
                    Text(
                        text       = "MAX",
                        style      = tokens.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color      = tokens.colors.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = tokens.shapes.button,
        color = if (enabled) tokens.colors.primary.copy(alpha = 0.18f)
                else tokens.colors.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.size(tokens.spacing.xxl + tokens.spacing.l),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .pressScale(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    enabled = enabled,
                    onClick = onClick,
                ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tokens.colors.primary
                       else tokens.colors.onSurfaceMuted.copy(alpha = 0.5f),
                modifier = Modifier.size(tokens.spacing.xl - tokens.spacing.xs),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewBigStepper() {
    FantasyPreviewSurface {
        BigStepper(value = 5, onValueChange = {}, onMax = {})
    }
}
