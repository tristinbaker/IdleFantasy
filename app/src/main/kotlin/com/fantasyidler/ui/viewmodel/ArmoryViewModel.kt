package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.repository.CarnivalRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import javax.inject.Inject

enum class ArmoryFilter { ALL, WEAPONS, ARMOR, ACCESSORIES, TOOLS }

data class ArmoryEntry(
    val key: String,
    val item: EquipmentData,
    val owned: Boolean,
    val source: String,
)

data class ArmoryUiState(
    val entries: List<ArmoryEntry> = emptyList(),
    val filter: ArmoryFilter = ArmoryFilter.ALL,
    val totalOwned: Int = 0,
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
)

@HiltViewModel
class ArmoryViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val gameData: GameDataRepository,
    private val carnivalRepo: CarnivalRepository,
    private val json: Json,
) : ViewModel() {

    private val _filter = MutableStateFlow(ArmoryFilter.ALL)

    private val sourceMap: Map<String, String> by lazy { buildSourceMap() }

    val uiState: StateFlow<ArmoryUiState> = combine(
        playerRepo.playerFlow,
        _filter,
    ) { player, filter ->
        if (player == null) return@combine ArmoryUiState(filter = filter)

        val inventory: Map<String, Int> = json.decodeFromString(player.inventory)
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
        val equippedValues = equipped.values.filterNotNull().toSet()

        val allEntries = gameData.equipment.map { (key, item) ->
            ArmoryEntry(
                key    = key,
                item   = item,
                owned  = (inventory[key] ?: 0) > 0 || key in equippedValues,
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

        ArmoryUiState(
            entries    = filtered,
            filter     = filter,
            totalOwned = allEntries.count { it.owned },
            totalCount = allEntries.size,
            isLoading  = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArmoryUiState())

    fun setFilter(filter: ArmoryFilter) { _filter.value = filter }

    private fun buildSourceMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()

        carnivalRepo.prizes.keys.forEach { key ->
            map[key] = "Carnival Prize Shop"
        }
        gameData.smithingRecipes.keys.forEach { key ->
            if (key !in map) map[key] = "Smithing"
        }
        gameData.craftingRecipes.keys.forEach { key ->
            if (key !in map) map[key] = "Crafting"
        }
        gameData.fletchingRecipes.values.forEach { recipe ->
            val key = recipe.itemName
            if (key !in map) map[key] = "Fletching"
        }
        gameData.enemies.forEach { (_, enemy) ->
            enemy.alwaysDrops.forEach { drop ->
                if (drop.item !in map) map[drop.item] = "Dropped by ${enemy.displayName} (Always)"
            }
            enemy.dropTable.forEach { drop ->
                if (drop.item !in map) map[drop.item] = "Dropped by ${enemy.displayName} (${formatChancePct(drop.chance)})"
            }
        }
        gameData.bosses.forEach { (_, boss) ->
            boss.rareDrops.forEach { drop ->
                if (drop.item !in map) map[drop.item] = "Dropped by ${boss.displayName} (${formatChancePct(drop.chance)})"
            }
            boss.commonLoot.items.keys.forEach { key ->
                if (key !in map) map[key] = "Dropped by ${boss.displayName}"
            }
        }
        gameData.marketplace.forEach { (_, category) ->
            category.items.keys.forEach { key -> if (key !in map) map[key] = "Shop" }
        }

        return map
    }

    companion object {
        val ARMOR_SLOTS     = setOf("head", "body", "legs", "boots", "shield")
        val ACCESSORY_SLOTS = setOf("ring", "necklace", "cape")
        val TOOL_SLOTS      = setOf("pickaxe", "axe", "fishing_rod", "hoe")

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
