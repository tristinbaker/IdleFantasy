package com.fantasyidler.ui.screen.profile

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.LaunchedEffect
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
import com.fantasyidler.ui.viewmodel.profile.BestiaryEntry
import com.fantasyidler.ui.viewmodel.profile.BestiaryFilter
import com.fantasyidler.ui.viewmodel.profile.BestiarySort
import com.fantasyidler.ui.viewmodel.profile.BestiaryViewModel
import com.fantasyidler.ui.theme.ScaledSheetContent
import com.fantasyidler.util.GameStrings
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.ui.components.CompletionProgressBar
import com.fantasyidler.ui.screen.BossIcon
import com.fantasyidler.util.formatCoinsBrief
import java.text.Collator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BestiaryTab(viewModel: BestiaryViewModel = hiltViewModel()) {
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

        val enemiesListState = rememberLazyListState()
        val bossesListState = rememberLazyListState()
        LaunchedEffect(state.filter, state.sort) {
            enemiesListState.scrollToItem(0)
            bossesListState.scrollToItem(0)
        }
        val entries = if (selectedSubTab == 0) state.enemies else state.bosses
        BestiaryList(
            entries = entries,
            sort    = state.sort,
            listState = if (selectedSubTab == 0) enemiesListState else bossesListState,
            onEntryClick = { selectedEntry = it },
            // Progress and filters scroll away with the list so it gets the whole tab (issue #2146).
            header = {
                if (state.totalCount > 0) {
                    CompletionProgressBar(
                        completed = state.totalEncountered,
                        total = state.totalCount,
                        label = stringResource(R.string.bestiary_progress_bar)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BestiaryFilter.entries.forEach { f ->
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
                Spacer(Modifier.height(4.dp))
            },
        )
    }

    selectedEntry?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { selectedEntry = null },
            sheetState       = sheetState,
        ) {
            ScaledSheetContent {
            val context = LocalContext.current
            if (entry.enemy != null) {
                EnemyDetailContent(entry = entry, enemy = entry.enemy, context = context)
            } else if (entry.boss != null) {
                BossDetailContent(entry = entry, boss = entry.boss, context = context)
            }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// List
// ---------------------------------------------------------------------------

@Composable
private fun filterLabel(filter: BestiaryFilter): String = when (filter) {
    BestiaryFilter.ALL     -> stringResource(R.string.bestiary_filter_all)
    BestiaryFilter.MISSING -> stringResource(R.string.bestiary_filter_missing)
}

@Composable
private fun BestiaryList(
    entries: List<BestiaryEntry>,
    sort: BestiarySort,
    listState: LazyListState = rememberLazyListState(),
    onEntryClick: (BestiaryEntry) -> Unit,
    header: @Composable () -> Unit = {},
) {
    val otherLabel = stringResource(R.string.bestiary_location_other)
    val context = LocalContext.current
    val collator = remember(context) { Collator.getInstance(context.resources.configuration.locales[0]) }
    // Sort by the translated names the rows display, not the English keys (issue #1647).
    val sortedEntries = remember(entries, collator) {
        entries.sortedWith(compareBy(collator) { it.nameLoader(context, it.key) })
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item(key = "bestiary_header") { Column { header() } }
        if (sort == BestiarySort.BY_LOCATION) {
            val grouped = buildLocationGroups(sortedEntries, otherLabel, collator)
            grouped.forEach { (groupName, groupEntries) ->
                item(key = "header_$groupName") {
                    Text(
                        text     = groupName,
                        style    = MaterialTheme.typography.labelMedium,
                        color    = MaterialTheme.colorScheme.primary,
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
            items(sortedEntries, key = { it.key }) { entry ->
                BestiaryRow(entry = entry, onClick = { onEntryClick(entry) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        item(key = "bestiary_footer_spacer") { Spacer(Modifier.height(16.dp)) }
    }
}

private fun buildLocationGroups(
    entries: List<BestiaryEntry>,
    otherLabel: String,
    collator: Collator,
): List<Pair<String, List<BestiaryEntry>>> {
    val grouped = mutableMapOf<String, MutableList<BestiaryEntry>>()
    entries.forEach { entry ->
        val locs = entry.locations.ifEmpty { listOf(otherLabel) }
        locs.forEach { loc -> grouped.getOrPut(loc) { mutableListOf() }.add(entry) }
    }
    return grouped.entries
        .sortedWith(compareBy<Map.Entry<String, List<BestiaryEntry>>> { it.key == otherLabel }.thenBy(collator) { it.key })
        .map { it.key to it.value.toList() }
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
        if (entry.boss != null) {
            BossIcon(
                bossId        = entry.boss.id,
                modifier      = Modifier.size(40.dp),
                silhouette    = !entry.encountered,
                fallbackEmoji = entry.boss.emoji,
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text  = entry.nameLoader(LocalContext.current, entry.key),
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
                text  = stringResource(R.string.bestiary_kill_count, entry.killCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
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
    context: Context,
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
                    text       = entry.nameLoader(context, enemy.name),
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (entry.killCount > 0) {
                    Text(
                        text  = stringResource(R.string.bestiary_kill_count, entry.killCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
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
                add(stringResource(R.string.bestiary_stat_hp) to enemy.hp.toString())
                add(stringResource(R.string.bestiary_stat_attack) to enemy.combatStats.attackLevel.toString())
                add(stringResource(R.string.bestiary_stat_strength) to enemy.combatStats.strengthLevel.toString())
                add(stringResource(R.string.bestiary_stat_defense) to enemy.combatStats.defenseLevel.toString())
                add(stringResource(R.string.bestiary_stat_atk_defense) to enemy.defensiveStats.attackDefense.toString())
                add(stringResource(R.string.bestiary_stat_str_defense) to enemy.defensiveStats.strengthDefense.toString())
                add(stringResource(R.string.bestiary_stat_rng_defense) to enemy.defensiveStats.rangedDefense.toString())
                add(stringResource(R.string.bestiary_stat_mag_defense) to enemy.defensiveStats.magicDefense.toString())
                if (xp > 0) add(stringResource(R.string.bestiary_stat_xp) to xp.toString())
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
    context: Context,
) {
    LazyColumn(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                BossIcon(
                    bossId        = boss.id,
                    modifier      = Modifier.size(96.dp),
                    silhouette    = !entry.encountered,
                    fallbackEmoji = boss.emoji,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text       = GameStrings.bossName(context, boss.id),
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (entry.killCount > 0) {
                    Text(
                        text  = stringResource(R.string.bestiary_kill_count, entry.killCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
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
                add(stringResource(R.string.bestiary_stat_hp) to boss.hp.toString())
                add(stringResource(R.string.bestiary_stat_required_level) to boss.combatLevelRequired.toString())
                add(stringResource(R.string.bestiary_stat_duration) to stringResource(R.string.bestiary_stat_duration_min, boss.durationMinutes))
                add(stringResource(R.string.bestiary_stat_attack) to boss.combatStats.attackLevel.toString())
                add(stringResource(R.string.bestiary_stat_strength) to boss.combatStats.strengthLevel.toString())
                add(stringResource(R.string.bestiary_stat_defense) to boss.combatStats.defenseLevel.toString())
                add(stringResource(R.string.bestiary_stat_atk_defense) to boss.defensiveStats.attackDefense.toString())
                add(stringResource(R.string.bestiary_stat_str_defense) to boss.defensiveStats.strengthDefense.toString())
                add(stringResource(R.string.bestiary_stat_rng_defense) to boss.defensiveStats.rangedDefense.toString())
                add(stringResource(R.string.bestiary_stat_mag_defense) to boss.defensiveStats.magicDefense.toString())
                if (xp > 0) add(stringResource(R.string.bestiary_stat_xp_reward) to xp.toString())
            }
            BestiaryStatTable(statRows)
            Spacer(Modifier.height(16.dp))
        }

        item {
            BestiarySectionHeader(stringResource(R.string.bestiary_common_loot))
            Spacer(Modifier.height(4.dp))
            val coinRow = Triple(
                stringResource(R.string.label_coins),
                "${boss.commonLoot.coinsMin.toLong().formatCoinsBrief()}–${boss.commonLoot.coinsMax.toLong().formatCoinsBrief()}",
                null as String?,
            )
            val cappedCoinRow = Triple(
                stringResource(R.string.bestiary_coin_cap_label, PlayerRepository.BOSS_FULL_COIN_KILLS_PER_DAY),
                "${(boss.commonLoot.coinsMin * PlayerRepository.BOSS_COIN_SOFT_CAP_MULT).toLong().formatCoinsBrief()}–${(boss.commonLoot.coinsMax * PlayerRepository.BOSS_COIN_SOFT_CAP_MULT).toLong().formatCoinsBrief()}",
                null as String?,
            )
            val itemRows = boss.commonLoot.items.map { (itemKey, range) ->
                val qty = if (range.min == range.max) "×${range.min}" else "×${range.min}–${range.max}"
                Triple(GameStrings.itemName(context, itemKey), qty, null)
            }
            BestiaryDropTable(listOf(coinRow, cappedCoinRow) + itemRows)
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
                BestiaryDropTable(listOf(Triple("${pet.emoji} ${GameStrings.petName(context, pet.id)}", "", formatChance(pet.chance))))
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
                text     = stringResource(R.string.bestiary_loot_item_header),
                modifier = Modifier.weight(1f),
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.primary,
            )
            Text(
                text      = stringResource(R.string.bestiary_qty_header),
                modifier  = Modifier.width(80.dp),
                style     = MaterialTheme.typography.labelSmall,
                color     = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
            )
            if (hasChance) {
                Text(
                    text      = stringResource(R.string.bestiary_chance_header),
                    modifier  = Modifier.width(52.dp),
                    style     = MaterialTheme.typography.labelSmall,
                    color     = MaterialTheme.colorScheme.primary,
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
                    modifier  = Modifier.width(80.dp),
                    style     = MaterialTheme.typography.bodySmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines  = 1,
                    softWrap  = false,
                )
                if (hasChance) {
                    Text(
                        text      = chance ?: "",
                        modifier  = Modifier.width(52.dp),
                        style     = MaterialTheme.typography.bodySmall,
                        color     = if (chance != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
// Section header
// ---------------------------------------------------------------------------

@Composable
private fun BestiarySectionHeader(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.primary,
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
