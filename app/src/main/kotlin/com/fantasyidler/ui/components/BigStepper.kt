package com.fantasyidler.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fantasyidler.ui.motion.pressScale
import com.fantasyidler.ui.theme.GoldPrimary

/**
 * Big tactile stepper for transactional sheets: craft qty, plant qty, shop
 * buy/sell qty. Replaces the cramped IconButton + OutlinedTextField pattern
 * that lived in CraftScreen / ShopScreen / PlantSheet.
 *
 * Layout: [ − ] [ qty (big, centered) ] [ + ]  with an optional "MAX" pill.
 *
 * Press-scaled buttons; disabled state goes alpha 0.4. The qty display itself
 * is read-only — long-press +/− to fast-step is left for a follow-up.
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
        Spacer(Modifier.width(12.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .defaultMinSize(minWidth = 88.dp)
                .size(width = 88.dp, height = 48.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        StepperButton(
            icon = Icons.Filled.Add,
            contentDescription = "Increase",
            enabled = canIncrement,
            onClick = { onValueChange((value + step).coerceAtMost(maxValue)) },
        )
        if (onMax != null) {
            Spacer(Modifier.width(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = GoldPrimary.copy(alpha = 0.18f),
                modifier = Modifier.size(width = 56.dp, height = 48.dp),
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
                        text = "MAX",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) GoldPrimary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.size(48.dp),
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
                tint = if (enabled) GoldPrimary
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
