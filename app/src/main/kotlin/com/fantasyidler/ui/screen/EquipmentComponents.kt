package com.fantasyidler.ui.screen

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.simulator.HeirloomStats
import com.fantasyidler.util.GameStrings

// ---------------------------------------------------------------------------
// Equipment tab
// ---------------------------------------------------------------------------

@Composable
internal fun EquipmentTab(
    equipped: Map<String, String?>,
    allEquipment: Map<String, EquipmentData>,
    heirloomXp: Map<String, Long>,
    context: Context,
    onSlotTap: (String) -> Unit,
    onUnequip: (String) -> Unit,
    onEquipBestTools: () -> Unit = {},
    onNavigateToCombat: () -> Unit = {},
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            OutlinedButton(
                onClick  = onNavigateToCombat,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(stringResource(R.string.profile_view_combat_gear))
            }
        }
        item { SlotSectionHeader(stringResource(R.string.profile_gathering_tools)) }
        item {
            Button(
                onClick  = onEquipBestTools,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.profile_equip_best_tools))
            }
        }
        items(EquipSlot.TOOL_SLOTS) { slot ->
            EquipSlotRow(
                slotName  = GameStrings.slotName(context, slot),
                itemKey   = equipped[slot],
                equipment = allEquipment[equipped[slot]],
                heirloomXp = heirloomXp,
                onTap     = { onSlotTap(slot) },
                onUnequip = { onUnequip(slot) },
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
internal fun FoodRow(
    itemKey: String,
    qty: Int,
    healValue: Int,
    isEquipped: Boolean,
    context: Context,
    onEquip: () -> Unit,
    onUnequip: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text       = GameStrings.itemName(context, itemKey),
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text  = stringResource(R.string.profile_food_desc, qty, healValue),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isEquipped) {
            TextButton(onClick = onUnequip) {
                Text(stringResource(R.string.btn_unequip), color = MaterialTheme.colorScheme.error)
            }
        } else {
            TextButton(onClick = onEquip) {
                Text(stringResource(R.string.btn_equip), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
internal fun SlotSectionHeader(title: String) {
    Column {
        HorizontalDivider()
        Spacer(Modifier.height(4.dp))
        Text(
            text     = title.uppercase(),
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun EquipSlotRow(
    slotName: String,
    itemKey: String?,
    xpLabel: String? = null,
    equipment: EquipmentData? = null,
    heirloomXp: Map<String, Long>? = null,
    onTap: () -> Unit,
    onUnequip: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text  = slotName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(100.dp),
        )
        if (itemKey != null) {
            val baseName = GameStrings.itemName(context, itemKey)
            val displayName = if (xpLabel != null) "$baseName ($xpLabel)" else baseName
            Column(Modifier.weight(1f)) {
                Text(
                    text       = displayName,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    softWrap   = false,
                    modifier   = Modifier.horizontalScroll(rememberScrollState()),
                )
                if (equipment != null) {
                    val detail = buildEquipDetail(equipment, context, showReq = false, heirloomXp = heirloomXp)
                    if (detail.isNotEmpty()) {
                        Text(
                            text  = detail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(onClick = onUnequip) {
                Icon(
                    imageVector        = Icons.Filled.Clear,
                    contentDescription = "Unequip",
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                text     = stringResource(R.string.label_none),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(48.dp))
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ---------------------------------------------------------------------------
// Equip picker sheet
// ---------------------------------------------------------------------------

@Composable
internal fun EquipPickerSheet(
    slot: String,
    candidates: List<EquipmentData>,
    context: Context,
    heirloomXp: Map<String, Long>? = null,
    onEquip: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
    ) {
        item {
            Text(
                text     = stringResource(R.string.profile_choose_slot, GameStrings.slotName(context, slot)),
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider()
        }

        if (candidates.isEmpty()) {
            item {
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = stringResource(R.string.profile_no_items),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(
                candidates.sortedWith(
                    compareBy(
                        {
                            it.attackBonus + it.strengthBonus + it.defenseBonus +
                                (it.rangedAttackBonus ?: 0) + (it.rangedStrengthBonus ?: 0) +
                                (it.magicAttackBonus ?: 0) + (it.magicDamageBonus ?: 0) +
                                ((it.miningEfficiency ?: it.woodcuttingEfficiency ?: it.fishingEfficiency ?:
                                    it.farmingEfficiency ?: it.smithingEfficiency ?: it.firemakingEfficiency ?:
                                    it.agilityEfficiency ?: it.cookingEfficiency ?: it.thievingEfficiency ?:
                                    0f) * 100).toInt()
                        },
                        { it.name },
                    )
                )
            ) { item ->
                val xpLabel = weaponXpLabel(item.combatStyle, context).takeIf { item.slot == EquipSlot.WEAPON || EquipSlot.combatStyleForSlot(item.slot) != null }
                val displayName = buildString {
                    append(GameStrings.itemName(context, item.name))
                    if (xpLabel != null) append(" ($xpLabel)")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEquip(item.name) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    // weight(1f) keeps the Equip label at intrinsic width; without it a long
                    // localised detail line starved the label into a one-letter-per-line
                    // sliver that stretched the row several hundred px tall (issue #1765).
                    Column(Modifier.weight(1f)) {
                        Text(
                            displayName,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        val detail = buildEquipDetail(item, context, heirloomXp = heirloomXp)
                        if (detail.isNotEmpty()) {
                            Text(
                                text  = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = stringResource(R.string.btn_equip),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

internal fun weaponXpLabel(combatStyle: String?, context: Context): String? = when (combatStyle) {
    "attack"   -> context.getString(R.string.profile_stat_atk)
    "strength" -> context.getString(R.string.profile_stat_str)
    "ranged"   -> context.getString(R.string.profile_stat_ranged)
    "magic"    -> context.getString(R.string.profile_stat_magic)
    else       -> null
}

private val COMBAT_CAPE_SKILLS = setOf(
    "attack", "strength", "defense", "ranged", "magic", "hp",
    "warriors", "archers", "mages",
)

internal fun buildEquipDetail(
    item: EquipmentData,
    context: Context,
    showReq: Boolean = true,
    /** Pass the player's heirloom XP map to prefix heirloom items with their current level; null hides it. */
    heirloomXp: Map<String, Long>? = null,
): String {
    val parts = mutableListOf<String>()
    if (item.heirloomSkill != null && heirloomXp != null) {
        parts.add(context.getString(R.string.heirloom_level_label, HeirloomStats.level(heirloomXp[item.name] ?: 0L)))
    }
    val partsBeforeEfficiency = parts.size
    item.miningEfficiency?.let      { parts.add("${context.getString(R.string.profile_stat_mining)} ×${"%.2f".format(it)}") }
    item.woodcuttingEfficiency?.let { parts.add("${context.getString(R.string.profile_stat_wc)} ×${"%.2f".format(it)}") }
    item.fishingEfficiency?.let     { parts.add("${context.getString(R.string.profile_stat_fishing)} ×${"%.2f".format(it)}") }
    item.farmingEfficiency?.let     { parts.add("${context.getString(R.string.profile_stat_farming)} ×${"%.2f".format(it)}") }
    item.smithingEfficiency?.let    { parts.add("${context.getString(R.string.profile_stat_smithing)} ×${"%.2f".format(it)}") }
    item.firemakingEfficiency?.let  { parts.add("${context.getString(R.string.profile_stat_firemaking)} ×${"%.2f".format(it)}") }
    item.agilityEfficiency?.let     { parts.add("${context.getString(R.string.profile_stat_agility)} ×${"%.2f".format(it)}") }
    item.cookingEfficiency?.let     { parts.add("${context.getString(R.string.profile_stat_cooking)} ×${"%.2f".format(it)}") }
    item.thievingEfficiency?.let    { parts.add("${context.getString(R.string.profile_stat_thieving)} ×${"%.2f".format(it)}") }
    // Tools with efficiency stats hide their melee bonuses; keyed off the efficiency
    // block alone so the heirloom level label doesn't suppress them (issue #1594).
    if (parts.size == partsBeforeEfficiency) {
        if (item.attackBonus   != 0) parts.add("${context.getString(R.string.profile_stat_atk)} +${item.attackBonus}")
        if (item.strengthBonus != 0) parts.add("${context.getString(R.string.profile_stat_str)} +${item.strengthBonus}")
        if (item.defenseBonus  != 0) parts.add("${context.getString(R.string.profile_stat_def)} +${item.defenseBonus}")
    }
    if ((item.rangedAttackBonus   ?: 0) != 0) parts.add("${context.getString(R.string.profile_stat_ranged)} ${context.getString(R.string.profile_stat_atk)} +${item.rangedAttackBonus}")
    if ((item.rangedStrengthBonus ?: 0) != 0) parts.add("${context.getString(R.string.profile_stat_ranged)} ${context.getString(R.string.profile_stat_str)} +${item.rangedStrengthBonus}")
    if ((item.magicAttackBonus    ?: 0) != 0) parts.add("${context.getString(R.string.profile_stat_magic)} ${context.getString(R.string.profile_stat_atk)} +${item.magicAttackBonus}")
    if ((item.magicDamageBonus    ?: 0) != 0) parts.add("${context.getString(R.string.profile_stat_magic)} Dmg +${item.magicDamageBonus}")
    item.attackSpeed?.let { parts.add("${context.getString(R.string.armory_stat_attack_speed)} ${"%.1f".format(it)}s") }
    if (item.capeBonus != 0f) {
        val capeLabelRes = when {
            item.capeSkill in COMBAT_CAPE_SKILLS || item.capeSkill == "agility" -> R.string.armory_stat_cape
            item.capeSkill == "prayer" -> R.string.armory_stat_cape_boost
            else -> R.string.armory_stat_cape_yield
        }
        parts.add("${context.getString(capeLabelRes)} +${(item.capeBonus * 100).toInt()}%")
    }
    if (showReq) {
        for ((skill, lvl) in item.requirements) {
            parts.add("${context.getString(R.string.profile_req_lv)} $lvl ${GameStrings.skillName(context, skill)}")
        }
    }
    return parts.joinToString("  •  ")
}
