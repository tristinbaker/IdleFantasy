package com.fantasyidler.ui.screen.combat.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.R
import com.fantasyidler.ui.components.EmptyState
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant
import com.fantasyidler.ui.components.foundation.ChunkySheet
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.util.GameStrings

/**
 * Read-only food roster sheet — surfaces what the player currently has
 * equipped and how much HP each unit heals, alongside the in-session consumed
 * count. Picking/equipping food itself happens in the inventory screen; this
 * sheet just lets the player audit what their character will eat mid-fight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodPickerSheet(
    equippedFood: Map<String, Int>,
    foodHealValues: Map<String, Int>,
    foodConsumed: Map<String, Int>,
    onDismiss: () -> Unit,
) {
    ChunkySheet(onDismissRequest = onDismiss) {
        FoodPickerContent(
            equippedFood   = equippedFood,
            foodHealValues = foodHealValues,
            foodConsumed   = foodConsumed,
            onDismiss      = onDismiss,
        )
    }
}

@Composable
private fun FoodPickerContent(
    equippedFood: Map<String, Int>,
    foodHealValues: Map<String, Int>,
    foodConsumed: Map<String, Int>,
    onDismiss: () -> Unit,
) {
    val tokens  = LocalFantasyTokens.current
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text       = stringResource(R.string.label_food),
            style      = tokens.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = tokens.colors.onSurface,
        )
        Spacer(Modifier.height(tokens.spacing.l))

        if (equippedFood.isEmpty()) {
            EmptyState(
                title       = stringResource(R.string.combat_no_food_equipped_title),
                description = stringResource(R.string.combat_no_food_equipped_description),
            )
        } else {
            equippedFood.forEach { (key, startQty) ->
                val remaining = (startQty - (foodConsumed[key] ?: 0)).coerceAtLeast(0)
                val heal      = foodHealValues[key] ?: 0
                val rowName   = GameStrings.itemName(context, key)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.l) // 48dp
                        .padding(vertical = tokens.spacing.s),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text       = rowName,
                            style      = tokens.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (remaining > 0) tokens.colors.onSurface
                                         else tokens.colors.onSurfaceMuted.copy(alpha = 0.6f),
                        )
                        Text(
                            text  = stringResource(R.string.combat_heals_hp, heal),
                            style = tokens.typography.bodyMedium,
                            color = tokens.colors.onSurfaceMuted,
                        )
                    }
                    Text(
                        text       = "×$remaining",
                        style      = tokens.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color      = if (remaining > 0) tokens.colors.primary
                                     else tokens.colors.onSurfaceMuted.copy(alpha = 0.6f),
                    )
                }
            }
        }

        Spacer(Modifier.height(tokens.spacing.l))
        ChunkyButton(
            text     = stringResource(R.string.btn_close),
            onClick  = onDismiss,
            variant  = ChunkyButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewFoodPickerSheetPopulated() {
    FantasyPreviewSurface {
        FoodPickerContent(
            equippedFood   = mapOf("shark" to 8, "lobster" to 4),
            foodHealValues = mapOf("shark" to 20, "lobster" to 12),
            foodConsumed   = mapOf("shark" to 3),
            onDismiss      = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewFoodPickerSheetEmpty() {
    FantasyPreviewSurface {
        FoodPickerContent(
            equippedFood   = emptyMap(),
            foodHealValues = emptyMap(),
            foodConsumed   = emptyMap(),
            onDismiss      = {},
        )
    }
}
