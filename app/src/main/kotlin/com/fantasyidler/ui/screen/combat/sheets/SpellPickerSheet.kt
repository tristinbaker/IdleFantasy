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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.SpellData
import com.fantasyidler.ui.components.foundation.ChunkySheet
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.util.GameStrings

/**
 * Embeddable spell-picker block — lists every spell the player meets the
 * magic-level requirement for, optionally filtered to spells the player can
 * currently cast (rune budget honoured). Used inside [DungeonInfoSheet] when
 * the equipped weapon's combat style is "magic".
 */
@Composable
fun SpellPickerSection(
    availableSpells: List<SpellData>,
    selectedSpell: SpellData?,
    equippedWeapon: EquipmentData?,
    inventory: Map<String, Int>,
    onSpellSelected: (SpellData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens  = LocalFantasyTokens.current
    val context = LocalContext.current

    var onlyCastable by remember { mutableStateOf(false) }
    val displaySpells = remember(availableSpells, onlyCastable, equippedWeapon, inventory) {
        if (onlyCastable) {
            availableSpells.filter { spell ->
                equippedWeapon?.infiniteRunes == spell.runeType ||
                    (inventory[spell.runeType] ?: 0) >= spell.runeCost
            }
        } else {
            availableSpells
        }
    }

    Column(modifier = modifier) {
        Text(
            text  = stringResource(R.string.label_spell),
            style = tokens.typography.labelSmall,
            color = tokens.colors.onSurfaceMuted,
        )
        Spacer(Modifier.height(tokens.spacing.s))

        if (availableSpells.isEmpty()) {
            Text(
                text  = stringResource(R.string.combat_no_spells),
                style = tokens.typography.bodyMedium,
                color = tokens.colors.onSurfaceMuted,
            )
        } else {
            Row(modifier = Modifier.padding(bottom = tokens.spacing.s)) {
                FilterChip(
                    selected = onlyCastable,
                    onClick  = { onlyCastable = !onlyCastable },
                    label    = { Text(stringResource(R.string.combat_only_castable)) },
                )
            }
            displaySpells.forEach { spell ->
                SpellPickerRow(
                    spell           = spell,
                    isSelected      = selectedSpell?.name == spell.name,
                    runeDisplayName = GameStrings.itemName(context, spell.runeType),
                    onSelect        = { onSpellSelected(spell) },
                )
            }
        }
    }
}

@Composable
private fun SpellPickerRow(
    spell: SpellData,
    isSelected: Boolean,
    runeDisplayName: String,
    onSelect: () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.l) // 48dp
            .clickable(onClickLabel = spell.displayName, onClick = onSelect)
            .padding(vertical = tokens.spacing.s)
            .semantics {
                role     = Role.RadioButton
                selected = isSelected
                contentDescription = spell.displayName
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text       = spell.displayName,
                style      = tokens.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color      = if (isSelected) tokens.colors.primary else tokens.colors.onSurface,
            )
            Text(
                text  = "${spell.runeCost}× $runeDisplayName  •  ${stringResource(R.string.combat_max_hit)} ${spell.maxHit}",
                style = tokens.typography.bodyMedium,
                color = tokens.colors.onSurfaceMuted,
            )
        }
        if (isSelected) {
            Text(
                text       = "✓",
                style      = tokens.typography.bodyLarge,
                color      = tokens.colors.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Standalone spell-picker [ChunkySheet] wrapper. The current orchestrator
 * embeds [SpellPickerSection] inline; this wrapper exists so callers can
 * promote the picker into its own modal without rewriting the body.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpellPickerSheet(
    availableSpells: List<SpellData>,
    selectedSpell: SpellData?,
    equippedWeapon: EquipmentData?,
    inventory: Map<String, Int>,
    onSpellSelected: (SpellData) -> Unit,
    onDismiss: () -> Unit,
) {
    ChunkySheet(onDismissRequest = onDismiss) {
        SpellPickerSection(
            availableSpells  = availableSpells,
            selectedSpell    = selectedSpell,
            equippedWeapon   = equippedWeapon,
            inventory        = inventory,
            onSpellSelected  = onSpellSelected,
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewSpellPickerSectionEmpty() {
    FantasyPreviewSurface {
        SpellPickerSection(
            availableSpells  = emptyList(),
            selectedSpell    = null,
            equippedWeapon   = null,
            inventory        = emptyMap(),
            onSpellSelected  = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewSpellPickerSectionPopulated() {
    val wind  = SpellData(name = "wind_strike",  displayName = "Wind Strike",  runeType = "air_rune",   magicLevelRequired = 1,  maxHit = 2, runeCost = 1)
    val water = SpellData(name = "water_strike", displayName = "Water Strike", runeType = "water_rune", magicLevelRequired = 5,  maxHit = 4, runeCost = 1)
    val fire  = SpellData(name = "fire_strike",  displayName = "Fire Strike",  runeType = "fire_rune",  magicLevelRequired = 13, maxHit = 8, runeCost = 3)
    FantasyPreviewSurface {
        SpellPickerSection(
            availableSpells  = listOf(wind, water, fire),
            selectedSpell    = water,
            equippedWeapon   = null,
            inventory        = mapOf("air_rune" to 50, "water_rune" to 10),
            onSpellSelected  = {},
        )
    }
}
