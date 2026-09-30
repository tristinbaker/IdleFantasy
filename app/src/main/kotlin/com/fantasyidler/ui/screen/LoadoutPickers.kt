package com.fantasyidler.ui.screen

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import com.fantasyidler.R
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.SpellData
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.toTitleCase

// ---------------------------------------------------------------------------
// Arrow/spell pickers for the active combat style, shown inline in the Combat
// Gear tab (CombatScreen.kt) right below its style-only weapon row — never in
// a separate sheet, so switching style and editing its loadout happen in place.
// ---------------------------------------------------------------------------

internal val ARROW_TIERS = listOf(
    "runite_arrow", "adamantite_arrow", "mithril_arrow",
    "steel_arrow", "iron_arrow", "bronze_arrow",
)

internal val ARROW_STRENGTH_BONUS = mapOf(
    "bronze_arrow"     to 7,
    "iron_arrow"       to 10,
    "steel_arrow"      to 16,
    "mithril_arrow"    to 22,
    "adamantite_arrow" to 31,
    "runite_arrow"     to 49,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArrowLoadoutPicker(
    selectedArrowKey: String?,
    inventory: Map<String, Int>,
    context: Context,
    onArrowSelected: (String?) -> Unit,
) {
    val availableArrows = ARROW_TIERS.filter { (inventory[it] ?: 0) > 0 }
    val arrowOptions = listOf(null) + availableArrows
    var expanded by remember { mutableStateOf(false) }
    Text(
        text     = stringResource(R.string.combat_label_arrow),
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value         = if (selectedArrowKey == null) stringResource(R.string.combat_arrow_auto)
                             else GameStrings.itemName(context, selectedArrowKey),
            onValueChange = {},
            readOnly      = true,
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors        = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            singleLine    = true,
            modifier      = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
        ) {
            arrowOptions.forEach { key ->
                DropdownMenuItem(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    text = {
                        if (key == null) {
                            Text(stringResource(R.string.combat_arrow_auto))
                        } else {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                val qty = inventory[key] ?: 0
                                Text(
                                    text  = "${GameStrings.itemName(context, key)} ($qty)",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text  = stringResource(
                                        R.string.combat_arrow_ranged_str,
                                        ARROW_STRENGTH_BONUS[key] ?: 0,
                                    ),
                                    style     = MaterialTheme.typography.bodySmall,
                                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    },
                    onClick = { onArrowSelected(key); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpellLoadoutPicker(
    selectedSpell: SpellData?,
    availableSpells: List<SpellData>,
    magicLevel: Int,
    inventory: Map<String, Int>,
    equippedWeapon: EquipmentData?,
    context: Context,
    onSpellSelected: (SpellData?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        text     = stringResource(R.string.label_spell),
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
    if (availableSpells.isEmpty()) {
        Text(
            text  = stringResource(R.string.combat_no_spells),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value         = selectedSpell?.let { GameStrings.spellName(context, it.name) } ?: "",
            onValueChange = {},
            readOnly      = true,
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors        = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            singleLine    = true,
            modifier      = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
        ) {
            availableSpells.forEach { spell ->
                val locked   = spell.magicLevelRequired > magicLevel
                val infinite = equippedWeapon?.infiniteRunes == "all" || equippedWeapon?.infiniteRunes == spell.runeType
                val held     = inventory[spell.runeType] ?: 0
                val runeName = GameStrings.itemName(context, spell.runeType)
                DropdownMenuItem(
                    enabled        = !locked,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    text = {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.Top,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text  = GameStrings.spellName(context, spell.name),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text  = "${spell.runeCost}× $runeName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text  = if (infinite) {
                                        stringResource(R.string.combat_infinite_runes, runeName)
                                    } else {
                                        stringResource(R.string.combat_you_have_runes, held, runeName)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (infinite || held >= spell.runeCost) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.error,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text  = "${stringResource(R.string.combat_max_hit)} ${spell.maxHit}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text      = if (locked) {
                                        stringResource(R.string.combat_spell_locked, spell.magicLevelRequired)
                                    } else {
                                        stringResource(R.string.combat_spell_level_met, spell.magicLevelRequired)
                                    },
                                    style     = MaterialTheme.typography.labelSmall,
                                    color     = if (locked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    },
                    onClick = { onSpellSelected(spell); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FoodOrderPicker(
    foodEatOrder: String,
    onFoodOrderChanged: (String) -> Unit,
) {
    val options = mapOf(
        "descending" to R.string.food_order_descending,
        "ascending" to R.string.food_order_ascending,
        "least_quantity" to R.string.food_order_least_quantity,
    )
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value         = stringResource(options[foodEatOrder] ?: R.string.food_order_descending),
            onValueChange = {},
            readOnly      = true,
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors        = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            singleLine    = true,
            modifier      = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (order, label) ->
                DropdownMenuItem(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    text = { Text(stringResource(label)) },
                    onClick = { onFoodOrderChanged(order); expanded = false },
                )
            }
        }
    }
}

/** Shared potion picker for Boss/Dungeon/Tower start sheets — a plain dropdown, no in-place preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PotionLoadoutPicker(
    availablePotions: Map<String, Int>,
    potionEffects: Map<String, Map<String, Int>>,
    selectedPotionKey: String?,
    context: Context,
    onPotionSelected: (String?) -> Unit,
) {
    if (availablePotions.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Text(
        text     = stringResource(R.string.label_potion),
        style    = MaterialTheme.typography.labelMedium,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
    val potionOptions = listOf(null) + availablePotions.keys.toList()
    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value         = if (selectedPotionKey == null) stringResource(R.string.combat_no_potion)
                             else GameStrings.itemName(context, selectedPotionKey),
            onValueChange = {},
            readOnly      = true,
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors        = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            singleLine    = true,
            modifier      = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
        ) {
            potionOptions.forEach { key ->
                DropdownMenuItem(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    text = {
                        Column {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text  = if (key == null) stringResource(R.string.combat_no_potion)
                                             else GameStrings.itemName(context, key),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (key != null) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text  = "×${availablePotions[key]}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (key != null) {
                                val effectStr = potionEffects[key]?.entries
                                    ?.joinToString(", ") { (stat, bonus) -> "+$bonus ${stat.toTitleCase()}" }
                                if (effectStr != null) {
                                    Text(
                                        text  = effectStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    },
                    onClick = { onPotionSelected(key); expanded = false },
                )
            }
        }
    }
}
