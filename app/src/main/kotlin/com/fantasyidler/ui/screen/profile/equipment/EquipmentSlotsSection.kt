package com.fantasyidler.ui.screen.profile.equipment

import android.content.Context
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.R
import com.fantasyidler.data.json.CookingRecipe
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.ui.components.SectionHeader
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.ui.viewmodel.slotDisplayName
import com.fantasyidler.util.GameStrings
import kotlin.math.roundToInt

/**
 * Profile → Equipment tab. "Equip best gear" hero CTA at the top, then
 * grouped sections for combat slots, gathering tool slots, and dungeon food.
 * Every interactive row meets the 48dp tap-target threshold.
 */
@Composable
fun EquipmentSlotsSection(
    equipped: Map<String, String?>,
    inventory: Map<String, Int>,
    equippedFood: Map<String, Int>,
    foodHealValues: Map<String, Int>,
    cookingRecipes: Map<String, CookingRecipe>,
    allEquipment: Map<String, EquipmentData>,
    context: Context,
    onSlotTap: (String) -> Unit,
    onUnequip: (String) -> Unit,
    onEquipBest: () -> Unit,
    onEquipFood: (String) -> Unit,
    onUnequipFood: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFantasyTokens.current
    val cookedItemKeys = remember(cookingRecipes) {
        cookingRecipes.values.map { it.cookedItem }.toSet()
    }
    val foodInInventory = remember(inventory, cookedItemKeys) {
        inventory.filterKeys { it in cookedItemKeys }.entries.toList()
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item("hero_equip_best") {
            ChunkyButton(
                text     = stringResource(R.string.profile_equip_best),
                onClick  = onEquipBest,
                variant  = ChunkyButtonVariant.Primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m + tokens.spacing.s),
            )
        }
        item("hdr_combat") { SectionHeader(stringResource(R.string.profile_combat_gear)) }
        items(EquipSlot.COMBAT_SLOTS) { slot ->
            val xpLabel = if (slot == EquipSlot.WEAPON)
                weaponXpLabel(allEquipment[equipped[slot]]?.combatStyle, context)
            else null
            EquipSlotRow(
                slotName  = slotDisplayName(slot),
                itemKey   = equipped[slot],
                xpLabel   = xpLabel,
                onTap     = { onSlotTap(slot) },
                onUnequip = { onUnequip(slot) },
            )
        }
        item("hdr_tools") { SectionHeader(stringResource(R.string.profile_gathering_tools)) }
        items(EquipSlot.TOOL_SLOTS) { slot ->
            EquipSlotRow(
                slotName  = slotDisplayName(slot),
                itemKey   = equipped[slot],
                onTap     = { onSlotTap(slot) },
                onUnequip = { onUnequip(slot) },
            )
        }
        item("hdr_food") { SectionHeader(stringResource(R.string.profile_food_dungeon)) }
        if (foodInInventory.isEmpty()) {
            item("food_empty") {
                Text(
                    text     = stringResource(R.string.profile_no_food),
                    style    = tokens.typography.bodyMedium,
                    color    = tokens.colors.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m),
                )
            }
        } else {
            items(foodInInventory, key = { "food_${it.key}" }) { (key, qty) ->
                FoodRow(
                    itemKey    = key,
                    qty        = qty,
                    healValue  = foodHealValues[key] ?: 0,
                    isEquipped = key in equippedFood,
                    context    = context,
                    onEquip    = { onEquipFood(key) },
                    onUnequip  = { onUnequipFood(key) },
                )
            }
        }
        item { Spacer(Modifier.height(tokens.spacing.l)) }
    }
}

/**
 * Sheet body for "choose which item to put in this slot". Lazy-list of the
 * eligible inventory items sorted by their minimum requirement, falling back
 * to a friendly empty state when there's nothing wearable.
 */
@Composable
fun EquipPickerSheet(
    slot: String,
    candidates: List<EquipmentData>,
    context: Context,
    onEquip: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = tokens.spacing.xxl),
    ) {
        item {
            Text(
                text     = stringResource(R.string.profile_choose_slot, slotDisplayName(slot)),
                style    = tokens.typography.titleLarge,
                color    = tokens.colors.onSurface,
                modifier = Modifier.padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m + tokens.spacing.s),
            )
            HorizontalDivider(color = tokens.colors.border.copy(alpha = 0.18f))
        }

        if (candidates.isEmpty()) {
            item("empty") {
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .padding(tokens.spacing.xxl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = stringResource(R.string.profile_no_items),
                        style = tokens.typography.bodyMedium,
                        color = tokens.colors.onSurfaceMuted,
                    )
                }
            }
        } else {
            items(
                candidates.sortedWith(
                    compareBy({ it.requirements.values.maxOrNull() ?: 0 }, { it.name })
                ),
                key = { it.name },
            ) { item ->
                EquipPickerRow(
                    item    = item,
                    context = context,
                    onEquip = onEquip,
                )
            }
        }
    }
}

@Composable
private fun EquipPickerRow(
    item: EquipmentData,
    context: Context,
    onEquip: (String) -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    val xpLabel = weaponXpLabel(item.combatStyle, context).takeIf { item.slot == EquipSlot.WEAPON }
    val displayName = buildString {
        append(GameStrings.itemName(context, item.name))
        if (xpLabel != null) append(" ($xpLabel)")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.l)
            .clickable { onEquip(item.name) }
            .padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m + tokens.spacing.s),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = displayName,
                style = tokens.typography.bodyLarge,
                color = tokens.colors.onSurface,
            )
            val detail = buildEquipDetail(item, context)
            if (detail.isNotEmpty()) {
                Text(
                    text  = detail,
                    style = tokens.typography.bodyMedium,
                    color = tokens.colors.onSurfaceMuted,
                )
            }
        }
        Text(
            text  = stringResource(R.string.btn_equip),
            style = tokens.typography.labelSmall,
            color = tokens.colors.primary,
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = tokens.spacing.l),
        color    = tokens.colors.border.copy(alpha = 0.12f),
    )
}

@Composable
private fun EquipSlotRow(
    slotName: String,
    itemKey: String?,
    xpLabel: String? = null,
    onTap: () -> Unit,
    onUnequip: () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.l)
            .clickable(
                interactionSource = interactionSource,
                indication        = LocalIndication.current,
                onClick           = onTap,
            )
            .padding(start = tokens.spacing.l, end = tokens.spacing.s, top = tokens.spacing.s, bottom = tokens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = slotName,
            style    = tokens.typography.labelSmall,
            color    = tokens.colors.onSurfaceMuted,
            modifier = Modifier.width(tokens.spacing.xxl * 3 + tokens.spacing.s),
        )
        if (itemKey != null) {
            val baseName = itemKey.replace('_', ' ').split(' ')
                .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
            val displayName = if (xpLabel != null) "$baseName ($xpLabel)" else baseName
            Text(
                text       = displayName,
                style      = tokens.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = tokens.colors.onSurface,
                modifier   = Modifier.weight(1f),
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick  = onUnequip,
                modifier = Modifier.size(tokens.spacing.xxl + tokens.spacing.l),
            ) {
                Icon(
                    imageVector        = Icons.Filled.Clear,
                    contentDescription = stringResource(R.string.cd_unequip_slot, slotName),
                    tint               = tokens.colors.onSurfaceMuted,
                )
            }
        } else {
            Text(
                text     = stringResource(R.string.label_none),
                style    = tokens.typography.bodyLarge,
                color    = tokens.colors.onSurfaceMuted,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(tokens.spacing.xxl + tokens.spacing.l))
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = tokens.spacing.l),
        color    = tokens.colors.border.copy(alpha = 0.12f),
    )
}

@Composable
private fun FoodRow(
    itemKey: String,
    qty: Int,
    healValue: Int,
    isEquipped: Boolean,
    context: Context,
    onEquip: () -> Unit,
    onUnequip: () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.l)
            .padding(start = tokens.spacing.l, end = tokens.spacing.s, top = tokens.spacing.s, bottom = tokens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = GameStrings.itemName(context, itemKey),
                style      = tokens.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = tokens.colors.onSurface,
            )
            Text(
                text  = stringResource(R.string.profile_food_desc, qty, healValue),
                style = tokens.typography.bodyMedium,
                color = tokens.colors.onSurfaceMuted,
            )
        }
        if (isEquipped) {
            TextButton(onClick = onUnequip) {
                Text(
                    text  = stringResource(R.string.btn_unequip),
                    style = tokens.typography.labelSmall,
                    color = tokens.colors.error,
                )
            }
        } else {
            TextButton(onClick = onEquip) {
                Text(
                    text  = stringResource(R.string.btn_equip),
                    style = tokens.typography.labelSmall,
                    color = tokens.colors.primary,
                )
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = tokens.spacing.l),
        color    = tokens.colors.border.copy(alpha = 0.12f),
    )
}

private fun weaponXpLabel(combatStyle: String?, context: Context): String? = when (combatStyle) {
    "attack"   -> context.getString(R.string.profile_stat_atk)
    "strength" -> context.getString(R.string.profile_stat_str)
    "ranged"   -> context.getString(R.string.profile_stat_ranged)
    "magic"    -> context.getString(R.string.profile_stat_magic)
    else       -> null
}

private fun buildEquipDetail(item: EquipmentData, context: Context): String {
    val parts = mutableListOf<String>()
    item.miningEfficiency?.let      { parts.add("${context.getString(R.string.profile_stat_mining)} ×${"%.2f".format(it)}") }
    item.woodcuttingEfficiency?.let { parts.add("${context.getString(R.string.profile_stat_wc)} ×${"%.2f".format(it)}") }
    item.fishingEfficiency?.let     { parts.add("${context.getString(R.string.profile_stat_fishing)} ×${"%.2f".format(it)}") }
    item.farmingEfficiency?.let     { parts.add("${context.getString(R.string.profile_stat_farming)} +${(it * 100).roundToInt()}%") }
    if (parts.isEmpty()) {
        if (item.attackBonus   != 0) parts.add("${context.getString(R.string.profile_stat_atk)} +${item.attackBonus}")
        if (item.strengthBonus != 0) parts.add("${context.getString(R.string.profile_stat_str)} +${item.strengthBonus}")
        if (item.defenseBonus  != 0) parts.add("${context.getString(R.string.profile_stat_def)} +${item.defenseBonus}")
    }
    val req = item.requirements.entries.firstOrNull()
    if (req != null) parts.add("${context.getString(R.string.profile_req_lv)}${req.value} ${req.key}")
    return parts.joinToString("  •  ")
}

@PreviewLightDark
@Composable
private fun PreviewEquipmentSlotsSection() {
    FantasyPreviewSurface {
        EquipmentSlotsSection(
            equipped       = mapOf(
                EquipSlot.WEAPON to "iron_sword",
                EquipSlot.HEAD   to null,
                EquipSlot.BODY   to "bronze_platebody",
            ),
            inventory      = emptyMap(),
            equippedFood   = emptyMap(),
            foodHealValues = emptyMap(),
            cookingRecipes = emptyMap(),
            allEquipment   = emptyMap(),
            context        = LocalContext.current,
            onSlotTap      = {},
            onUnequip      = {},
            onEquipBest    = {},
            onEquipFood    = {},
            onUnequipFood  = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewEquipPickerSheetEmpty() {
    FantasyPreviewSurface {
        EquipPickerSheet(
            slot       = EquipSlot.WEAPON,
            candidates = emptyList(),
            context    = LocalContext.current,
            onEquip    = {},
            onDismiss  = {},
        )
    }
}
