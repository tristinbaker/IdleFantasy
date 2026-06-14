package com.fantasyidler.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.BestiaryEntry
import com.fantasyidler.ui.viewmodel.BestiarySort
import com.fantasyidler.ui.viewmodel.BestiaryViewModel
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BestiaryTab(viewModel: BestiaryViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var selectedSubTab by remember { mutableIntStateOf(0) }
    var selectedEntry by remember { mutableStateOf<BestiaryEntry?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedSubTab) {
            Tab(
                selected = selectedSubTab == 0,
                onClick  = { selectedSubTab = 0 },
                text     = { Text(stringResource(R.string.label_enemies)) },
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick  = { selectedSubTab = 1 },
                text     = { Text(stringResource(R.string.label_bosses)) },
            )
        }

        val encounteredEnemies = state.enemies.count { it.encountered }
        val encounteredBosses  = state.bosses.count { it.encountered }
        val totalEncountered   = encounteredEnemies + encounteredBosses
        val totalAll           = state.enemies.size + state.bosses.size
        if (totalAll > 0) {
            BestiaryProgressBar(encountered = totalEncountered, total = totalAll)
        }

        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.sort == BestiarySort.ALPHABETICAL,
                onClick  = { viewModel.setSort(BestiarySort.ALPHABETICAL) },
                label    = { Text("A–Z") },
            )
            FilterChip(
                selected = state.sort == BestiarySort.BY_LOCATION,
                onClick  = { viewModel.setSort(BestiarySort.BY_LOCATION) },
                label    = { Text(stringResource(R.string.label_by_location)) },
            )
        }

        val entries = if (selectedSubTab == 0) state.enemies else state.bosses
        BestiaryList(
            entries = entries,
            sort    = state.sort,
            onEntryClick = { selectedEntry = it },
        )
    }

    selectedEntry?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { selectedEntry = null },
            sheetState       = sheetState,
        ) {
            val context = LocalContext.current
            if (entry.enemy != null) {
                EnemyDetailContent(entry = entry, enemy = entry.enemy, context = context)
            } else if (entry.boss != null) {
                BossDetailContent(entry = entry, boss = entry.boss, context = context)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------

@Composable
private fun BestiaryList(
    entries: List<BestiaryEntry>,
    sort: BestiarySort,
    onEntryClick: (BestiaryEntry) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (sort == BestiarySort.BY_LOCATION) {
            val grouped = buildLocationGroups(entries)
            grouped.forEach { (groupName, groupEntries) ->
                item(key = "header_$groupName") {
                    Text(
                        text     = groupName,
                        style    = MaterialTheme.typography.labelMedium,
                        color    = GoldPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    HorizontalDivider()
                }
                items(groupEntries, key = { "${groupName}_${it.key}" }) { entry ->
                    BestiaryRow(entry = entry, onClick = { onEntryClick(entry) })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        } else {
            items(entries, key = { it.key }) { entry ->
                BestiaryRow(entry = entry, onClick = { onEntryClick(entry) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

private fun buildLocationGroups(
    entries: List<BestiaryEntry>,
): List<Pair<String, List<BestiaryEntry>>> {
    val grouped = mutableMapOf<String, MutableList<BestiaryEntry>>()
    entries.forEach { entry ->
        val locs = entry.locations.ifEmpty { listOf("Other") }
        locs.forEach { loc -> grouped.getOrPut(loc) { mutableListOf() }.add(entry) }
    }
    return grouped.entries
        .sortedWith(compareBy({ it.key == "Other" }, { it.key }))
        .map { it.key to it.value.sortedBy { e -> e.displayName } }
}

@Composable
private fun BestiaryRow(entry: BestiaryEntry, onClick: () -> Unit) {
    val textColor = if (entry.encountered)
        MaterialTheme.colorScheme.onSurface
    else
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val subColor = if (entry.encountered)
        MaterialTheme.colorScheme.onSurfaceVariant
    else
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text  = entry.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
            if (entry.locations.isNotEmpty()) {
                Text(
                    text  = entry.locations.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = subColor,
                )
            }
        }
        if (entry.killCount > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text  = "${entry.killCount} kills",
                style = MaterialTheme.typography.labelSmall,
                color = GoldPrimary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Enemy detail sheet
// ---------------------------------------------------------------------------

@Composable
private fun EnemyDetailContent(
    entry: BestiaryEntry,
    enemy: EnemyData,
    context: android.content.Context,
) {
    LazyColumn(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text       = enemy.displayName,
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (entry.killCount > 0) {
                    Text(
                        text  = "${entry.killCount} kills",
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldPrimary,
                    )
                }
            }

            if (!entry.encountered) {
                Text(
                    text  = stringResource(R.string.bestiary_not_encountered),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                return@item
            }
            Spacer(Modifier.height(12.dp))
        }

        if (!entry.encountered) return@LazyColumn

        item {
            BestiarySectionHeader(stringResource(R.string.label_stats))
            Spacer(Modifier.height(4.dp))
            val xp = enemy.xpDrops.values.sum()
            val statRows = buildList {
                add("HP" to enemy.hp.toString())
                add("Attack" to enemy.combatStats.attackLevel.toString())
                add("Strength" to enemy.combatStats.strengthLevel.toString())
                add("Defense" to enemy.combatStats.defenseLevel.toString())
                add("Atk Defense" to enemy.defensiveStats.attackDefense.toString())
                add("Str Defense" to enemy.defensiveStats.strengthDefense.toString())
                add("Rng Defense" to enemy.defensiveStats.rangedDefense.toString())
                add("Mag Defense" to enemy.defensiveStats.magicDefense.toString())
                if (xp > 0) add("XP" to xp.toString())
            }
            BestiaryStatTable(statRows)
            Spacer(Modifier.height(16.dp))
        }

        if (enemy.alwaysDrops.isNotEmpty()) {
            item {
                BestiarySectionHeader(stringResource(R.string.bestiary_always_drops))
                Spacer(Modifier.height(4.dp))
                val rows = enemy.alwaysDrops.map { drop ->
                    Triple(GameStrings.itemName(context, drop.item), "×${drop.quantity}", null as String?)
                }
                BestiaryDropTable(rows)
                Spacer(Modifier.height(16.dp))
            }
        }

        if (enemy.dropTable.isNotEmpty()) {
            item {
                BestiarySectionHeader(stringResource(R.string.label_drops))
                Spacer(Modifier.height(4.dp))
                val rows = enemy.dropTable.map { drop ->
                    val qty = if (drop.quantityMin == drop.quantityMax) "×${drop.quantityMin}"
                              else "×${drop.quantityMin}–${drop.quantityMax}"
                    Triple(GameStrings.itemName(context, drop.item), qty, formatChance(drop.chance))
                }
                BestiaryDropTable(rows)
                Spacer(Modifier.height(16.dp))
            }
        }

        if (entry.locations.isNotEmpty()) {
            item {
                BestiarySectionHeader(stringResource(R.string.label_locations))
                Spacer(Modifier.height(4.dp))
                BestiaryStatTable(entry.locations.map { it to "" })
                Spacer(Modifier.height(24.dp))
            }
        } else {
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Boss detail sheet
// ---------------------------------------------------------------------------

@Composable
private fun BossDetailContent(
    entry: BestiaryEntry,
    boss: BossData,
    context: android.content.Context,
) {
    LazyColumn(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text       = "${boss.emoji}  ${boss.displayName}",
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (entry.killCount > 0) {
                    Text(
                        text  = "${entry.killCount} kills",
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldPrimary,
                    )
                }
            }

            if (!entry.encountered) {
                Text(
                    text  = stringResource(R.string.bestiary_not_encountered),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                return@item
            }
            Spacer(Modifier.height(12.dp))
        }

        if (!entry.encountered) return@LazyColumn

        item {
            BestiarySectionHeader(stringResource(R.string.label_stats))
            Spacer(Modifier.height(4.dp))
            val xp = boss.xpRewards.values.sum()
            val statRows = buildList {
                add("HP" to boss.hp.toString())
                add("Required Level" to boss.combatLevelRequired.toString())
                add("Duration" to "${boss.durationMinutes} min")
                add("Attack" to boss.combatStats.attackLevel.toString())
                add("Strength" to boss.combatStats.strengthLevel.toString())
                add("Defense" to boss.combatStats.defenseLevel.toString())
                add("Atk Defense" to boss.defensiveStats.attackDefense.toString())
                add("Str Defense" to boss.defensiveStats.strengthDefense.toString())
                add("Rng Defense" to boss.defensiveStats.rangedDefense.toString())
                add("Mag Defense" to boss.defensiveStats.magicDefense.toString())
                if (xp > 0) add("XP Reward" to xp.toString())
            }
            BestiaryStatTable(statRows)
            Spacer(Modifier.height(16.dp))
        }

        item {
            BestiarySectionHeader(stringResource(R.string.bestiary_common_loot))
            Spacer(Modifier.height(4.dp))
            val coinRow = Triple(
                stringResource(R.string.label_coins),
                "${boss.commonLoot.coinsMin.toLong().formatCoins()}–${boss.commonLoot.coinsMax.toLong().formatCoins()}",
                null as String?,
            )
            val itemRows = boss.commonLoot.items.map { (itemKey, range) ->
                val qty = if (range.min == range.max) "×${range.min}" else "×${range.min}–${range.max}"
                Triple(GameStrings.itemName(context, itemKey), qty, null)
            }
            BestiaryDropTable(listOf(coinRow) + itemRows)
            Spacer(Modifier.height(16.dp))
        }

        if (boss.rareDrops.isNotEmpty()) {
            item {
                BestiarySectionHeader(stringResource(R.string.bestiary_rare_drops))
                Spacer(Modifier.height(4.dp))
                val rows = boss.rareDrops.map { drop ->
                    Triple(GameStrings.itemName(context, drop.item), "", formatChance(drop.chance))
                }
                BestiaryDropTable(rows)
                Spacer(Modifier.height(16.dp))
            }
        }

        boss.pet?.let { pet ->
            item {
                BestiarySectionHeader(stringResource(R.string.bestiary_pet_drop))
                Spacer(Modifier.height(4.dp))
                BestiaryDropTable(listOf(Triple("${pet.emoji} ${pet.displayName}", "", formatChance(pet.chance))))
                Spacer(Modifier.height(16.dp))
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ---------------------------------------------------------------------------
// Shared table composables
// ---------------------------------------------------------------------------

@Composable
private fun BestiaryStatTable(rows: List<Pair<String, String>>) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = MaterialTheme.colorScheme.onSurface

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
                if (value.isNotEmpty()) {
                    Text(
                        text       = value,
                        style      = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color      = valueColor,
                        textAlign  = TextAlign.End,
                    )
                }
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

@Composable
private fun BestiaryDropTable(rows: List<Triple<String, String, String?>>) {
    val hasChance = rows.any { it.third != null }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text(
                text     = "Item",
                modifier = Modifier.weight(1f),
                style    = MaterialTheme.typography.labelSmall,
                color    = GoldPrimary,
            )
            Text(
                text      = "Qty",
                modifier  = Modifier.width(52.dp),
                style     = MaterialTheme.typography.labelSmall,
                color     = GoldPrimary,
                textAlign = TextAlign.End,
            )
            if (hasChance) {
                Text(
                    text      = "Chance",
                    modifier  = Modifier.width(52.dp),
                    style     = MaterialTheme.typography.labelSmall,
                    color     = GoldPrimary,
                    textAlign = TextAlign.End,
                )
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )

        // Data rows
        rows.forEachIndexed { i, (name, qty, chance) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = name,
                    modifier = Modifier.weight(1f),
                    style    = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text      = qty,
                    modifier  = Modifier.width(52.dp),
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
                if (hasChance) {
                    Text(
                        text      = chance ?: "",
                        modifier  = Modifier.width(52.dp),
                        style     = MaterialTheme.typography.bodySmall,
                        color     = if (chance != null) GoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            }
            if (i < rows.size - 1) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    modifier  = Modifier.padding(horizontal = 4.dp),
                    color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Progress bar
// ---------------------------------------------------------------------------

@Composable
private fun BestiaryProgressBar(encountered: Int, total: Int) {
    val fraction = if (total > 0) encountered.toFloat() / total else 0f
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
                text  = "$encountered / $total encountered",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text       = "$pct%",
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color      = GoldPrimary,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress  = { fraction },
            modifier  = Modifier.fillMaxWidth(),
            color     = GoldPrimary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Section header
// ---------------------------------------------------------------------------

@Composable
private fun BestiarySectionHeader(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color      = GoldPrimary,
    )
}

// ---------------------------------------------------------------------------
// Utility
// ---------------------------------------------------------------------------

private fun formatChance(chance: Double): String {
    if (chance >= 1.0) return "Always"
    val pct = chance * 100.0
    return when {
        pct >= 10.0  -> "${"%.1f".format(pct)}%"
        pct >= 1.0   -> "${"%.2f".format(pct)}%"
        pct >= 0.1   -> "${"%.3f".format(pct)}%"
        else         -> "<0.1%"
    }
}
