package com.fantasyidler.ui.screen.profile

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.ui.viewmodel.InventoryCategory
import com.fantasyidler.util.GameStrings


@Composable
internal fun InventoryTab(
    inventory: Map<String, Int>,
    context: Context,
    categoryFor: (String) -> InventoryCategory,
    onOpenTreasure: (Boolean) -> Unit,
    onDebugAddItem: () -> Unit,
) {
    var sortAlpha by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<InventoryCategory?>(null) }
    var query by remember { mutableStateOf("") }
    var treasureDialogQty by remember { mutableStateOf<Int?>(null) }

    treasureDialogQty?.let { qty ->
        AlertDialog(
            onDismissRequest = { treasureDialogQty = null },
            title = { Text(GameStrings.itemName(context, PlayerRepository.ANCIENT_TREASURE_KEY)) },
            text = { Text(stringResource(R.string.treasure_open_message)) },
            confirmButton = {
                TextButton(onClick = { onOpenTreasure(true); treasureDialogQty = null }) {
                    Text(
                        if (qty > 1) stringResource(R.string.treasure_open_all, qty)
                        else stringResource(R.string.treasure_open)
                    )
                }
            },
            dismissButton = if (qty > 1) {
                {
                    TextButton(onClick = { onOpenTreasure(false); treasureDialogQty = null }) {
                        Text(stringResource(R.string.treasure_open_one))
                    }
                }
            } else null,
        )
    }

    val allGroups: List<Pair<InventoryCategory, List<Map.Entry<String, Int>>>> =
        remember(inventory, sortAlpha, query) {
            val grouped = inventory.entries
                .filter {
                    query.isBlank() || GameStrings.itemName(context, it.key)
                        .contains(query.trim(), ignoreCase = true)
                }
                .groupBy { categoryFor(it.key) }
            InventoryCategory.values().mapNotNull { cat ->
                val items = grouped[cat] ?: return@mapNotNull null
                val sorted = if (sortAlpha)
                    items.sortedBy { GameStrings.itemName(context, it.key) }
                else
                    items.sortedByDescending { it.value }
                cat to sorted
            }
        }

    val groups = remember(allGroups, selectedCategory) {
        if (selectedCategory == null) allGroups
        else allGroups.filter { (cat, _) -> cat == selectedCategory }
    }

    // Search and filters live inside the list so they scroll away with it, leaving the
    // whole tab for items on small screens (issue #2146).
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (BuildConfig.DEBUG) {
            item(key = "inventory_debug") {
                TextButton(onClick = onDebugAddItem) {
                    Text("[Debug] Modify Item Count")
                }
            }
        }
        item(key = "inventory_search") {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.shop_search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item(key = "inventory_sort") {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !sortAlpha,
                    onClick = { sortAlpha = false },
                    label = { Text(stringResource(R.string.inventory_sort_quantity)) },
                )
                FilterChip(
                    selected = sortAlpha,
                    onClick = { sortAlpha = true },
                    label = { Text(stringResource(R.string.inventory_sort_az)) },
                )
            }
        }
        item(key = "inventory_categories") {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text(stringResource(R.string.inventory_cat_filter_all)) },
                )
                allGroups.forEach { (cat, _) ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                        label = { Text(categoryLabel(cat)) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        if (groups.isEmpty()) {
            item(key = "inventory_empty") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.label_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            for ((cat, catItems) in groups) {
                item(key = cat.name) {
                    Text(
                        text = categoryLabel(cat),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                itemsIndexed(catItems, key = { _, cat -> cat.key }) { index, entry ->
                    InventoryRow(
                        name = GameStrings.itemName(context, entry.key),
                        qty = entry.value,
                        onClick = if (entry.key == PlayerRepository.ANCIENT_TREASURE_KEY) {
                            { treasureDialogQty = entry.value }
                        } else null,
                    )
                    if (index < catItems.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun categoryLabel(cat: InventoryCategory): String = stringResource(
    when (cat) {
        InventoryCategory.WEAPONS -> R.string.inventory_cat_weapons
        InventoryCategory.ARMOUR -> R.string.inventory_cat_armour
        InventoryCategory.TOOLS -> R.string.inventory_cat_tools
        InventoryCategory.FOOD -> R.string.inventory_cat_food
        InventoryCategory.RAW_FOOD -> R.string.inventory_cat_raw_food
        InventoryCategory.POTIONS -> R.string.inventory_cat_potions
        InventoryCategory.AMMUNITION -> R.string.inventory_cat_ammunition
        InventoryCategory.ORES -> R.string.inventory_cat_ores
        InventoryCategory.CONSTRUCTION -> R.string.inventory_cat_construction
        InventoryCategory.SEEDS -> R.string.inventory_cat_seeds
        InventoryCategory.MATERIALS -> R.string.inventory_cat_materials
        InventoryCategory.OTHER -> R.string.inventory_cat_other
    }
)

@Composable
private fun InventoryRow(name: String, qty: Int, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.Companion)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "×$qty",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}