package com.fantasyidler.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.ArmoryEntry
import com.fantasyidler.ui.viewmodel.ArmoryFilter
import com.fantasyidler.ui.viewmodel.ArmorySort
import com.fantasyidler.ui.viewmodel.ArmoryViewModel
import com.fantasyidler.ui.theme.ScaledSheetContent
import com.fantasyidler.util.GameStrings

private val COMBAT_CAPE_SKILLS = setOf(
    "attack", "strength", "defense", "ranged", "magic", "hp",
    "warriors", "archers", "mages",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArmoryTab(viewModel: ArmoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var selectedEntry by remember { mutableStateOf<ArmoryEntry?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(Modifier.fillMaxSize()) {
        if (!state.isLoading && state.totalCount > 0) {
            CollectionProgressBar(
                obtained = state.totalOwned,
                total    = state.totalCount,
                label    = stringResource(R.string.armory_progress_bar),
            )
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ArmoryFilter.entries.forEach { f ->
                FilterChip(
                    selected = state.filter == f,
                    onClick  = { viewModel.setFilter(f) },
                    label    = { Text(filterLabel(f)) },
                )
            }
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ArmorySort.entries.forEach { s ->
                FilterChip(
                    selected = state.sort == s,
                    onClick  = { viewModel.setSort(s) },
                    label    = { Text(sortLabel(s)) },
                )
            }
        }

        val grouped = buildSlotGroups(state.entries)
        LazyColumn(Modifier.fillMaxSize()) {
            grouped.forEach { (groupName, entries) ->
                item(key = "header_$groupName") {
                    Text(
                        text     = groupName,
                        style    = MaterialTheme.typography.labelMedium,
                        color    = GoldPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    HorizontalDivider()
                }
                items(entries, key = { it.key }) { entry ->
                    ArmoryRow(entry = entry, onClick = { selectedEntry = entry })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    selectedEntry?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { selectedEntry = null },
            sheetState       = sheetState,
        ) {
            ScaledSheetContent {
            ArmoryDetailContent(entry = entry)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Row
// ---------------------------------------------------------------------------

@Composable
private fun ArmoryRow(entry: ArmoryEntry, onClick: () -> Unit) {
    val context = LocalContext.current
    val textColor = if (entry.owned)
        MaterialTheme.colorScheme.onSurface
    else
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val subColor = if (entry.owned)
        MaterialTheme.colorScheme.onSurfaceVariant
    else
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)

    //val statSummary = armoryStatSummary(entry.item)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val name = GameStrings.itemName(context, entry.item.name)
            Text(
                text  = name,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
            val details = buildEquipDetail(entry.item, context, false)
            if (details.isNotBlank()) {
                Text(
                    text  = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = subColor,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Detail sheet
// ---------------------------------------------------------------------------

@Composable
private fun ArmoryDetailContent(entry: ArmoryEntry) {
    val context = LocalContext.current
    val item     = entry.item
    val dimmed   = !entry.owned
    val statRows = armoryStatRows(item)
    val reqRows  = item.requirements.map { (skill, lvl) ->
        skill.replaceFirstChar { it.uppercase() } to "Lv. $lvl"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        item {
            val name = GameStrings.itemName(context, item.name)
            Text(
                text       = name,
                style      = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text  = slotLabel(item.slot),
                style = MaterialTheme.typography.labelMedium,
                color = GoldPrimary,
            )
            Spacer(Modifier.height(16.dp))
        }

        item {
            ArmorySectionHeader(stringResource(R.string.label_stats))
            Spacer(Modifier.height(4.dp))
            if (statRows.isNotEmpty()) {
                ArmoryStatTable(rows = statRows, dimmed = dimmed)
            } else {
                Text(
                    text  = stringResource(R.string.armory_no_stats),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        if (reqRows.isNotEmpty()) {
            item {
                ArmorySectionHeader(stringResource(R.string.label_requirements))
                Spacer(Modifier.height(4.dp))
                ArmoryStatTable(rows = reqRows, dimmed = false)
                Spacer(Modifier.height(16.dp))
            }
        }

        item {
            ArmorySectionHeader(stringResource(R.string.armory_how_to_obtain))
            Spacer(Modifier.height(4.dp))
            Text(
                text  = entry.source,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Shared table composable
// ---------------------------------------------------------------------------

@Composable
private fun ArmoryStatTable(rows: List<Pair<String, String>>, dimmed: Boolean) {
    val valueColor = if (dimmed)
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
    else
        MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.let {
        if (dimmed) it.copy(alpha = 0.45f) else it
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    ) {
        rows.forEachIndexed { i, (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text  = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = labelColor,
                )
                Text(
                    text       = value,
                    style      = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color      = valueColor,
                    textAlign  = TextAlign.End,
                )
            }
            if (i < rows.size - 1) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Section header
// ---------------------------------------------------------------------------

@Composable
private fun ArmorySectionHeader(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color      = GoldPrimary,
    )
}

// ---------------------------------------------------------------------------
// Data helpers
// ---------------------------------------------------------------------------

private fun armoryStatSummary(item: EquipmentData): String {
    val parts = mutableListOf<String>()
    item.miningEfficiency?.let      { parts.add("×${"%.2f".format(it)}") }
    item.woodcuttingEfficiency?.let { parts.add("×${"%.2f".format(it)}") }
    item.fishingEfficiency?.let     { parts.add("×${"%.2f".format(it)}") }
    item.farmingEfficiency?.let     { parts.add("+${"%.0f".format(it * 100)}% yield") }
    if (parts.isEmpty()) {
        if (item.attackBonus   != 0) parts.add("Atk +${item.attackBonus}")
        if (item.strengthBonus != 0) parts.add("Str +${item.strengthBonus}")
        if (item.defenseBonus  != 0) parts.add("Def +${item.defenseBonus}")
        if ((item.rangedAttackBonus  ?: 0) != 0) parts.add("Rng +${item.rangedAttackBonus}")
        if ((item.magicAttackBonus   ?: 0) != 0) parts.add("Mag +${item.magicAttackBonus}")
        if (item.capeBonus != 0f) {
            val label = if (item.capeSkill in COMBAT_CAPE_SKILLS) "XP" else "yield"
            parts.add("+${(item.capeBonus * 100).toInt()}% $label")
        }
    }
    return parts.joinToString("  •  ")
}

@Composable
private fun armoryStatRows(item: EquipmentData): List<Pair<String, String>> {
    val rows = mutableListOf<Pair<String, String>>()
    item.miningEfficiency?.let      { rows.add(stringResource(R.string.armory_stat_mining) to "×${"%.2f".format(it)}") }
    item.woodcuttingEfficiency?.let { rows.add(stringResource(R.string.armory_stat_woodcutting) to "×${"%.2f".format(it)}") }
    item.fishingEfficiency?.let     { rows.add(stringResource(R.string.armory_stat_fishing) to "×${"%.2f".format(it)}") }
    item.farmingEfficiency?.let     { rows.add(stringResource(R.string.armory_stat_farming) to "×${"%.2f".format(1f + it)}") }
    item.smithingEfficiency?.let    { rows.add(stringResource(R.string.armory_stat_smithing) to "×${"%.2f".format(it)}") }
    item.firemakingEfficiency?.let  { rows.add(stringResource(R.string.armory_stat_firemaking) to "×${"%.2f".format(it)}") }
    item.agilityEfficiency?.let     { rows.add(stringResource(R.string.armory_stat_agility) to "×${"%.2f".format(it)}") }
    item.cookingEfficiency?.let     { rows.add(stringResource(R.string.armory_stat_cooking) to "×${"%.2f".format(it)}") }
    item.thievingEfficiency?.let    { rows.add(stringResource(R.string.armory_stat_thieving) to "×${"%.2f".format(it)}") }
    if (rows.isEmpty()) {
        if (item.attackBonus   != 0) rows.add(stringResource(R.string.armory_stat_attack) to "+${item.attackBonus}")
        if (item.strengthBonus != 0) rows.add(stringResource(R.string.armory_stat_strength) to "+${item.strengthBonus}")
        if (item.defenseBonus  != 0) rows.add(stringResource(R.string.armory_stat_defense) to "+${item.defenseBonus}")
        if ((item.rangedAttackBonus  ?: 0) != 0) rows.add(stringResource(R.string.armory_stat_ranged_atk) to "+${item.rangedAttackBonus}")
        if ((item.rangedStrengthBonus?: 0) != 0) rows.add(stringResource(R.string.armory_stat_ranged_str) to "+${item.rangedStrengthBonus}")
        if ((item.magicAttackBonus   ?: 0) != 0) rows.add(stringResource(R.string.armory_stat_magic_atk) to "+${item.magicAttackBonus}")
        if ((item.magicDamageBonus   ?: 0) != 0) rows.add(stringResource(R.string.armory_stat_magic_dmg) to "+${item.magicDamageBonus}")
        item.attackSpeed?.let { rows.add(stringResource(R.string.armory_stat_attack_speed) to "%.1fs".format(it)) }
        if (item.capeBonus != 0f) {
            val label = when {
                item.capeSkill in COMBAT_CAPE_SKILLS || item.capeSkill == "agility" -> stringResource(R.string.armory_stat_cape)
                item.capeSkill == "prayer" -> stringResource(R.string.armory_stat_cape_boost)
                else -> stringResource(R.string.armory_stat_cape_yield)
            }
            rows.add(label to "+${(item.capeBonus * 100).toInt()}%")
        }
    }
    return rows
}

@Composable
private fun buildSlotGroups(entries: List<ArmoryEntry>): List<Pair<String, List<ArmoryEntry>>> {
    val grouped = linkedMapOf<String, MutableList<ArmoryEntry>>()
    entries.forEach { entry ->
        val group = slotLabel(entry.item.slot)
        grouped.getOrPut(group) { mutableListOf() }.add(entry)
    }
    return grouped.map { it.key to it.value }
}

@Composable
private fun slotLabel(slot: String): String = when (slot) {
    "weapon"      -> stringResource(R.string.profile_weapons)
    "head"        -> stringResource(R.string.equip_slot_head)
    "body"        -> stringResource(R.string.equip_slot_body)
    "legs"        -> stringResource(R.string.equip_slot_legs)
    "boots"       -> stringResource(R.string.equip_slot_boots)
    "shield"      -> stringResource(R.string.equip_slot_shield)
    "cape"        -> stringResource(R.string.equip_slot_cape)
    "necklace"    -> stringResource(R.string.equip_slot_necklace)
    "ring"        -> stringResource(R.string.equip_slot_ring)
    "pickaxe"     -> stringResource(R.string.equip_slot_pickaxe)
    "axe"         -> stringResource(R.string.equip_slot_axe)
    "fishing_rod" -> stringResource(R.string.equip_slot_fishing_rod)
    "hoe"         -> stringResource(R.string.equip_slot_hoe)
    "frying_pan"  -> stringResource(R.string.equip_slot_frying_pan)
    "grappling_hook" -> stringResource(R.string.equip_slot_grappling_hook)
    "hammer"      -> stringResource(R.string.equip_slot_hammer)
    "tinderbox"   -> stringResource(R.string.equip_slot_tinderbox)
    "lockpick"    -> stringResource(R.string.equip_slot_lockpick)
    else          -> slot.replaceFirstChar { it.uppercase() }
}

@Composable
private fun CollectionProgressBar(obtained: Int, total: Int, label: String) {
    val fraction = if (total > 0) obtained.toFloat() / total else 0f
    val pct = (fraction * 100).toInt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text  = "$obtained / $total $label",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text  = "$pct%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = GoldPrimary,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color    = GoldPrimary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun filterLabel(filter: ArmoryFilter): String = when (filter) {
    ArmoryFilter.ALL         -> stringResource(R.string.armory_filter_all)
    ArmoryFilter.WEAPONS     -> stringResource(R.string.armory_filter_weapons)
    ArmoryFilter.ARMOR       -> stringResource(R.string.armory_filter_armor)
    ArmoryFilter.ACCESSORIES -> stringResource(R.string.armory_filter_accessories)
    ArmoryFilter.TOOLS       -> stringResource(R.string.armory_filter_tools)
}

@Composable
private fun sortLabel(sort: ArmorySort): String = when (sort) {
    ArmorySort.DEFAULT     -> stringResource(R.string.armory_sort_default)
    ArmorySort.ATTACK      -> stringResource(R.string.armory_sort_attack)
    ArmorySort.STRENGTH    -> stringResource(R.string.armory_sort_strength)
    ArmorySort.DEFENSE     -> stringResource(R.string.armory_sort_defense)
    ArmorySort.REQUIREMENT -> stringResource(R.string.armory_sort_requirement)
}
