package com.fantasyidler.ui.screen.combat.sheets

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.R
import com.fantasyidler.ui.components.foundation.ChunkySheet
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.util.GameStrings

/**
 * Embeddable potion-picker block — used inside [BossInfoSheet] and
 * [DungeonInfoSheet]. Renders an opening "Potion" header, then a list of
 * radio-style rows: "No potion" plus one row per owned potion key, each tall
 * enough to honour the 48dp tap-target minimum.
 *
 * The block keeps no internal state; selection is hoisted to the caller via
 * [onPotionSelected].
 */
@Composable
fun PotionPickerSection(
    availablePotions: Map<String, Int>,
    selectedPotionKey: String?,
    onPotionSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (availablePotions.isEmpty()) return
    val tokens   = LocalFantasyTokens.current
    val context  = LocalContext.current
    val noPotion = stringResource(R.string.combat_no_potion)

    Column(modifier = modifier) {
        Text(
            text  = stringResource(R.string.combat_potion_label),
            style = tokens.typography.labelSmall,
            color = tokens.colors.onSurfaceMuted,
        )
        Spacer(Modifier.height(tokens.spacing.s))

        val options: List<String?> = listOf(null) + availablePotions.keys.toList()
        options.forEach { key ->
            val isSelected = selectedPotionKey == key
            val rowLabel   = if (key == null) noPotion else GameStrings.itemName(context, key)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.l) // 48dp
                    .clickable(onClickLabel = rowLabel) { onPotionSelected(key) }
                    .padding(vertical = tokens.spacing.s)
                    .semantics {
                        role     = Role.RadioButton
                        selected = isSelected
                        contentDescription = rowLabel
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text       = rowLabel,
                    style      = tokens.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (isSelected) tokens.colors.primary else tokens.colors.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (key != null) {
                        Text(
                            text  = "×${availablePotions[key]}",
                            style = tokens.typography.bodyMedium,
                            color = tokens.colors.onSurfaceMuted,
                        )
                    }
                    if (isSelected) {
                        Spacer(Modifier.padding(start = tokens.spacing.s))
                        Text(
                            text       = "✓",
                            style      = tokens.typography.bodyLarge,
                            color      = tokens.colors.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Standalone potion-picker [ChunkySheet] wrapper — useful when the caller wants
 * a dedicated modal instead of embedding the section. The current orchestrator
 * uses [PotionPickerSection] inline; this wrapper is preserved so callers can
 * promote the picker without rewriting the body.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PotionPickerSheet(
    availablePotions: Map<String, Int>,
    selectedPotionKey: String?,
    onPotionSelected: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ChunkySheet(onDismissRequest = onDismiss) {
        PotionPickerSection(
            availablePotions  = availablePotions,
            selectedPotionKey = selectedPotionKey,
            onPotionSelected  = onPotionSelected,
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewPotionPickerSectionEmpty() {
    FantasyPreviewSurface {
        PotionPickerSection(
            availablePotions  = emptyMap(),
            selectedPotionKey = null,
            onPotionSelected  = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewPotionPickerSectionPopulated() {
    FantasyPreviewSurface {
        PotionPickerSection(
            availablePotions  = mapOf("attack_potion" to 3, "strength_potion" to 1),
            selectedPotionKey = "attack_potion",
            onPotionSelected  = {},
        )
    }
}
