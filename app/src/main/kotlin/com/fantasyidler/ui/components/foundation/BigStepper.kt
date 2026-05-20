package com.fantasyidler.ui.components.foundation

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.Dp
import com.fantasyidler.ui.motion.pressScale
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/**
 * Big tactile stepper for transactional sheets: craft qty, plant qty, shop
 * buy/sell qty. Six-button layout:
 *   `[Min] [−10] [−]  [qty]  [+] [+10] [Max]`
 * Min/Max are hidden when [onMin]/[onMax] are null.
 */
@Composable
fun BigStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minValue: Int = 1,
    maxValue: Int = Int.MAX_VALUE,
    step: Int = 1,
    bigStep: Int = 10,
    onMax: (() -> Unit)? = null,
    onMin: (() -> Unit)? = null,
) {
    val tokens = LocalFantasyTokens.current
    val canDecrement = value > minValue
    val canIncrement = value < maxValue
    val buttonSize = tokens.spacing.xxl + tokens.spacing.m       // 40dp
    val displayW   = tokens.spacing.xxl + tokens.spacing.xl      // 56dp
    val gap        = tokens.spacing.s                            // 4dp

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onMin != null) {
            StepperButton(
                label = "Min",
                contentDescription = "Minimum",
                enabled = canDecrement,
                size = buttonSize,
                onClick = onMin,
            )
            Spacer(Modifier.width(gap))
        }
        StepperButton(
            label = "-$bigStep",
            contentDescription = "Decrease by $bigStep",
            enabled = canDecrement,
            size = buttonSize,
            onClick = { onValueChange((value - bigStep).coerceAtLeast(minValue)) },
        )
        Spacer(Modifier.width(gap))
        StepperButton(
            icon = Icons.Filled.Remove,
            contentDescription = "Decrease",
            enabled = canDecrement,
            size = buttonSize,
            onClick = { onValueChange((value - step).coerceAtLeast(minValue)) },
        )
        Spacer(Modifier.width(gap))
        Surface(
            shape = tokens.shapes.button,
            color = tokens.colors.surface,
            modifier = Modifier.size(width = displayW, height = buttonSize),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text       = value.toString(),
                    style      = tokens.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = tokens.colors.primary,
                    textAlign  = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.width(gap))
        StepperButton(
            icon = Icons.Filled.Add,
            contentDescription = "Increase",
            enabled = canIncrement,
            size = buttonSize,
            onClick = { onValueChange((value + step).coerceAtMost(maxValue)) },
        )
        Spacer(Modifier.width(gap))
        StepperButton(
            label = "+$bigStep",
            contentDescription = "Increase by $bigStep",
            enabled = canIncrement,
            size = buttonSize,
            onClick = { onValueChange((value + bigStep).coerceAtMost(maxValue)) },
        )
        if (onMax != null) {
            Spacer(Modifier.width(gap))
            StepperButton(
                label = "Max",
                contentDescription = "Maximum",
                enabled = canIncrement,
                size = buttonSize,
                onClick = onMax,
            )
        }
    }
}

@Composable
private fun StepperButton(
    contentDescription: String,
    enabled: Boolean,
    size: Dp,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    label: String? = null,
) {
    val tokens = LocalFantasyTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = tokens.shapes.button,
        color = if (enabled) tokens.colors.primary.copy(alpha = 0.18f)
                else tokens.colors.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.size(size),
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
            val activeColor   = tokens.colors.primary
            val disabledColor = tokens.colors.onSurfaceMuted.copy(alpha = 0.5f)
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (enabled) activeColor else disabledColor,
                    modifier = Modifier.size(tokens.spacing.xl - tokens.spacing.xs),
                )
            } else if (label != null) {
                Text(
                    text       = label,
                    style      = tokens.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color      = if (enabled) activeColor else disabledColor,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewBigStepper() {
    FantasyPreviewSurface {
        BigStepper(value = 5, onValueChange = {}, onMax = {}, onMin = {})
    }
}
