package com.fantasyidler.ui.screen.profile

import android.content.Context
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.fantasyidler.ui.viewmodel.profile.ArmoryEntry
import com.fantasyidler.ui.viewmodel.profile.ArmoryFilter
import com.fantasyidler.ui.viewmodel.profile.ArmorySort
import com.fantasyidler.ui.viewmodel.profile.ArmoryViewModel
import com.fantasyidler.ui.theme.ScaledSheetContent
import com.fantasyidler.simulator.HeirloomStats
import com.fantasyidler.simulator.XpTable
import com.fantasyidler.ui.components.CompletionProgressBar
import com.fantasyidler.ui.screen.buildEquipDetail
import com.fantasyidler.util.GameStrings

private val COMBAT_CAPE_SKILLS = setOf(
    "attack", "strength", "defense", "ranged", "magic", "hp",
    "warriors", "archers", "mages",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArmoryTab(viewModel: ArmoryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var selectedEntry by remember { mutableStateOf<ArmoryEntry?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val groupContext = LocalContext.current
    val grouped = remember(state.entries) { buildSlotGroups(groupContext, state.entries) }
    val listState = rememberLazyListState()
    // Progress and filters scroll away with the list so it gets the whole tab (issue #2146).
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item(key = "armory_header") {
            Column {
                if (!state.isLoading && state.totalCount > 0) {
                    CompletionProgressBar(
                        completed = state.totalOwned,
                        total = state.totalCount,
                        label = stringResource(R.string.armory_progress_bar)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
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
                        .padding(horizontal = 16.dp),
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
                Spacer(Modifier.height(4.dp))
            }
        }
        grouped.forEach { (groupName, entries) ->
            item(key = "header_$groupName") {
                Text(
                    text     = groupName,
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                HorizontalDivider()
            }
            items(entries, key = { "${groupName}_${it.key}" }) { entry ->
                ArmoryRow(entry = entry, onClick = { selectedEntry = entry })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    selectedEntry?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { selectedEntry = null },
            sheetState       = sheetState,
        ) {
            ScaledSheetContent {
            ArmoryDetailContent(entry = entry, heirloomXp = state.heirloomXp)
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
private fun ArmoryDetailContent(entry: ArmoryEntry, heirloomXp: Map<String, Long>) {
    val context = LocalContext.current
    val item     = entry.item
    val dimmed   = !entry.owned
    val statRows = armoryStatRows(item)
    val reqRows  = item.requirements.map { (skill, lvl) ->
        GameStrings.skillName(context, skill) to context.getString(R.string.armory_req_level, lvl)
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
                text  = GameStrings.slotName(context, item.slot),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
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

        if (item.heirloomSkill != null) {
            item {
                ArmorySectionHeader(stringResource(R.string.heirloom_section_title))
                Spacer(Modifier.height(4.dp))
                if (entry.owned) {
                    val xp  = heirloomXp[item.name] ?: 0L
                    val lvl = HeirloomStats.level(xp)
                    Text(
                        text       = stringResource(R.string.heirloom_level_label, lvl),
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { XpTable.progressFraction(xp) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text  = stringResource(R.string.heirloom_desc, GameStrings.skillName(context, item.heirloomSkill!!)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }
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
        color      = MaterialTheme.colorScheme.primary,
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
    item.farmingEfficiency?.let     { parts.add("×${"%.2f".format(it)}") }
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
    item.farmingEfficiency?.let     { rows.add(stringResource(R.string.armory_stat_farming) to "×${"%.2f".format(it)}") }
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

private fun buildSlotGroups(context: Context, entries: List<ArmoryEntry>): List<Pair<String, List<ArmoryEntry>>> {
    // Resolve each label once per distinct slot: a per-entry slotName lookup creates a
    // configuration context every call, which froze the armory list (issue #1710).
    val labels = entries.map { it.item.slot }.distinct().associateWith { GameStrings.slotName(context, it) }
    val grouped = linkedMapOf<String, MutableList<ArmoryEntry>>()
    entries.forEach { entry ->
        grouped.getOrPut(labels.getValue(entry.item.slot)) { mutableListOf() }.add(entry)
    }
    return grouped.map { it.key to it.value }
}

@Composable
private fun filterLabel(filter: ArmoryFilter): String = when (filter) {
    ArmoryFilter.ALL         -> stringResource(R.string.armory_filter_all)
    ArmoryFilter.MISSING     -> stringResource(R.string.armory_filter_missing)
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
