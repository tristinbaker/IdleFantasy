package com.fantasyidler.ui.viewmodel

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.R
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.repository.CarnivalRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

enum class ArmoryFilter { ALL, WEAPONS, ARMOR, ACCESSORIES, TOOLS }
enum class ArmorySort   { DEFAULT, ATTACK, STRENGTH, DEFENSE, REQUIREMENT }

data class ArmoryEntry(
    val key: String,
    val item: EquipmentData,
    val owned: Boolean,
    val source: String,
)

data class ArmoryUiState(
    val entries: List<ArmoryEntry> = emptyList(),
    val filter: ArmoryFilter = ArmoryFilter.ALL,
    val sort: ArmorySort = ArmorySort.DEFAULT,
    val totalOwned: Int = 0,
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
)

@HiltViewModel
class ArmoryViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val gameData: GameDataRepository,
    private val carnivalRepo: CarnivalRepository,
    @ApplicationContext private val context: Context,
    private val json: Json,
) : ViewModel() {

    init {
        viewModelScope.launch { playerRepo.migrateSeenItems() }
    }

    private val _filter = MutableStateFlow(ArmoryFilter.ALL)
    private val _sort   = MutableStateFlow(ArmorySort.DEFAULT)

    private val sourceMap: Map<String, String> by lazy { buildSourceMap() }

    val uiState: StateFlow<ArmoryUiState> = combine(
        playerRepo.playerFlow,
        _filter,
        _sort,
    ) { player, filter, sort ->
        if (player == null) return@combine ArmoryUiState(filter = filter, sort = sort)

        val inventory: Map<String, Int> = json.decodeFromString(player.inventory)
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
        val equippedValues = equipped.values.filterNotNull().toSet()
        val flags: PlayerFlags = json.decodeFromString(player.flags)

        val allEntries = gameData.equipment.map { (key, item) ->
            ArmoryEntry(
                key    = key,
                item   = item,
                owned  = (inventory[key] ?: 0) > 0 || key in equippedValues || key in flags.seenItemKeys,
                source = sourceMap[key] ?: item.description.takeIf { it.isNotBlank() } ?: "Unknown source",
            )
        }.sortedWith(compareBy({ slotSortOrder(it.item.slot) }, { it.item.displayName }))

        val filtered = when (filter) {
            ArmoryFilter.ALL         -> allEntries
            ArmoryFilter.WEAPONS     -> allEntries.filter { it.item.slot == "weapon" }
            ArmoryFilter.ARMOR       -> allEntries.filter { it.item.slot in ARMOR_SLOTS }
            ArmoryFilter.ACCESSORIES -> allEntries.filter { it.item.slot in ACCESSORY_SLOTS }
            ArmoryFilter.TOOLS       -> allEntries.filter { it.item.slot in TOOL_SLOTS }
        }

        val sorted = when (sort) {
            ArmorySort.DEFAULT     -> filtered
            ArmorySort.ATTACK      -> filtered.sortedByDescending { it.item.attackBonus }
            ArmorySort.STRENGTH    -> filtered.sortedByDescending { it.item.strengthBonus }
            ArmorySort.DEFENSE     -> filtered.sortedByDescending { it.item.defenseBonus }
            ArmorySort.REQUIREMENT -> filtered.sortedByDescending { it.item.requirements.values.maxOrNull() ?: 0 }
        }

        ArmoryUiState(
            entries    = sorted,
            filter     = filter,
            sort       = sort,
            totalOwned = allEntries.count { it.owned },
            totalCount = allEntries.size,
            isLoading  = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArmoryUiState())

    fun setFilter(filter: ArmoryFilter) { _filter.value = filter }
    fun setSort(sort: ArmorySort) { _sort.value = sort }

    private fun buildSourceMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()

        carnivalRepo.prizes.keys.forEach { key ->
            map[key] = context.getString(R.string.armory_source_carnival)
        }
        gameData.smithingRecipes.keys.forEach { key ->
            if (key !in map) map[key] = context.getString(R.string.armory_source_smithing)
        }
        gameData.craftingRecipes.keys.forEach { key ->
            if (key !in map) map[key] = context.getString(R.string.armory_source_crafting)
        }
        gameData.fletchingRecipes.values.forEach { recipe ->
            val key = recipe.itemName
            if (key !in map) map[key] = context.getString(R.string.armory_source_fletching)
        }
        gameData.enemies.forEach { (_, enemy) ->
            enemy.alwaysDrops.forEach { drop ->
                if (drop.item !in map) map[drop.item] = context.getString(R.string.armory_source_drop_always, enemy.displayName)
            }
            enemy.dropTable.forEach { drop ->
                if (drop.item !in map) map[drop.item] = context.getString(R.string.armory_source_drop_chance, enemy.displayName, formatChancePct(drop.chance))
            }
        }
        gameData.bosses.forEach { (_, boss) ->
            boss.rareDrops.forEach { drop ->
                if (drop.item !in map) map[drop.item] = context.getString(R.string.armory_source_drop_chance, boss.displayName, formatChancePct(drop.chance))
            }
            boss.commonLoot.items.keys.forEach { key ->
                if (key !in map) map[key] = context.getString(R.string.armory_source_drop, boss.displayName)
            }
        }
        gameData.marketplace.forEach { (_, category) ->
            category.items.keys.forEach { key -> if (key !in map) map[key] = context.getString(R.string.armory_source_shop) }
        }

        return map
    }

    companion object {
        val ARMOR_SLOTS     = setOf("head", "body", "legs", "boots", "shield")
        val ACCESSORY_SLOTS = setOf("ring", "necklace", "cape")
        val TOOL_SLOTS      = setOf("pickaxe", "axe", "fishing_rod", "hoe", "hammer", "tinderbox", "grappling_hook", "frying_pan")

        fun formatChancePct(chance: Double): String {
            if (chance >= 1.0) return "Always"
            val pct = chance * 100.0
            return when {
                pct >= 10.0 -> "${"%.1f".format(pct)}%"
                pct >= 1.0  -> "${"%.2f".format(pct)}%"
                pct >= 0.1  -> "${"%.3f".format(pct)}%"
                else        -> "<0.1%"
            }
        }

        fun slotSortOrder(slot: String): Int = when (slot) {
            "weapon"      -> 0
            "head"        -> 1
            "body"        -> 2
            "legs"        -> 3
            "boots"       -> 4
            "shield"      -> 5
            "cape"        -> 6
            "necklace"    -> 7
            "ring"        -> 8
            "pickaxe"     -> 9
            "axe"         -> 10
            "fishing_rod" -> 11
            "hoe"         -> 12
            else          -> 13
        }
    }
}
