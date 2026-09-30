package com.fantasyidler.ui.viewmodel

import com.fantasyidler.util.withAppLocale

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.R
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.CarnivalRepository
import com.fantasyidler.repository.DailyQuestRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.WeeklyQuestRepository
import com.fantasyidler.repository.capeKeyForSkill
import com.fantasyidler.util.GameStrings
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

enum class ArmoryFilter { ALL, MISSING, WEAPONS, ARMOR, ACCESSORIES, TOOLS }
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
    /** Heirloom item key -> accumulated item XP, for the detail sheet. */
    val heirloomXp: Map<String, Long> = emptyMap(),
)

@HiltViewModel
class ArmoryViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val gameData: GameDataRepository,
    private val carnivalRepo: CarnivalRepository,
    private val dailyQuestRepo: DailyQuestRepository,
    private val weeklyQuestRepo: WeeklyQuestRepository,
    @ApplicationContext private val context: Context,
    private val json: Json,
) : ViewModel() {

    init {
        viewModelScope.launch { playerRepo.migrateSeenItems() }
    }

    private val _filter = MutableStateFlow(ArmoryFilter.ALL)
    private val _sort   = MutableStateFlow(ArmorySort.DEFAULT)

    private val sourceMap: Map<String, String> by lazy { buildSourceMap() }

    // Locale collation over translated names, not the English displayName (issue #1685).
    // Resolved and sorted once per ViewModel: doing this inside the combine lambda froze
    // the UI for seconds, because the comparator re-resolved every name (each lookup
    // creating a configuration context) on every comparison, on every emission (issue
    // #1710). Safe to cache: an in-app language change recreates the activity.
    private val sortedEquipment: List<Pair<String, EquipmentData>> by lazy {
        val ctx = context.withAppLocale()
        val collator = java.text.Collator.getInstance(ctx.resources.configuration.locales[0])
        val names = gameData.equipment.keys.associateWith { GameStrings.itemName(ctx, it) }
        gameData.equipment.entries
            .sortedWith(
                compareBy<Map.Entry<String, EquipmentData>> { slotSortOrder(it.value.slot) }
                    .thenBy(collator) { names.getValue(it.key) }
            )
            .map { it.key to it.value }
    }

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

        // Isle-only gear stays hidden from the armory (and out of the "N / M" total at the
        // top) until the isle is unlocked. Includes the tiered Elder armor set (Coastal,
        // Grove, Volcanic, Elder pieces + accessories) and the Ancient Signet.
        val hideElder = !flags.elderIsleUnlocked
        val allEntries = sortedEquipment
            .filterNot { (key, _) -> hideElder && key in ELDER_ISLE_ITEM_KEYS }
            .map { (key, item) ->
                ArmoryEntry(
                    key    = key,
                    item   = item,
                    owned  = (inventory[key] ?: 0) > 0 || key in equippedValues || key in flags.seenItemKeys,
                    source = sourceMap[key] ?: item.description.takeIf { it.isNotBlank() } ?: "Unknown source",
                )
            }

        val filtered = when (filter) {
            ArmoryFilter.ALL         -> allEntries
            ArmoryFilter.MISSING     -> allEntries.filter { !it.owned }
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
            heirloomXp = flags.heirloomXp,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArmoryUiState())

    fun setFilter(filter: ArmoryFilter) { _filter.value = filter }
    fun setSort(sort: ArmorySort) { _sort.value = sort }

    private data class RecipeGate(val label: String, val races: List<String>?)

    private val TIER_NUMERALS = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")

    private fun buildRecipeGates(): Map<String, RecipeGate> {
        val gates = mutableMapOf<String, RecipeGate>()
        gameData.prestigeTrees.forEach { (skill, tree) ->
            tree.paths.forEach { path ->
                path.nodes.forEachIndexed { idx, node ->
                    val unlock = node.unlock ?: return@forEachIndexed
                    val label = GameStrings.prestigePathName(context, skill, path.key) +
                        " " + (TIER_NUMERALS.getOrNull(idx) ?: (idx + 1).toString())
                    gates[unlock] = RecipeGate(label, node.races)
                }
            }
        }
        return gates
    }

    private fun buildSourceMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val gates = buildRecipeGates()

        // Recipes gated behind an unlock_recipe prestige node show the perk (and race lock)
        // needed to craft them instead of looking like ordinary recipes (issue #1631).
        fun craftSource(key: String, baseRes: Int): String {
            val base = context.withAppLocale().getString(baseRes)
            val gate = gates[key] ?: return base
            return if (gate.races.isNullOrEmpty())
                context.withAppLocale().getString(R.string.armory_source_prestige_locked_any, base, gate.label)
            else
                context.withAppLocale().getString(
                    R.string.armory_source_prestige_locked, base, gate.label,
                    GameStrings.raceNames(context, gate.races),
                )
        }

        carnivalRepo.prizes.keys.forEach { key ->
            map[key] = context.withAppLocale().getString(R.string.armory_source_carnival)
        }
        gameData.smithingRecipes.keys.forEach { key ->
            if (key !in map) map[key] = craftSource(key, R.string.armory_source_smithing)
        }
        gameData.craftingRecipes.keys.forEach { key ->
            if (key !in map) map[key] = craftSource(key, R.string.armory_source_crafting)
        }
        gameData.fletchingRecipes.values.forEach { recipe ->
            val key = recipe.itemName
            if (key !in map) map[key] = craftSource(key, R.string.armory_source_fletching)
        }
        // Bosses are checked before regular enemies so an item dropped by both (e.g. Ring of
        // Dragon's Might, dropped by both King Black Dragon and Abyssal Lord) resolves to the
        // more notable boss source rather than whichever happened to be registered first.
        gameData.bosses.forEach { (_, boss) ->
            boss.rareDrops.forEach { drop ->
                if (drop.item !in map) map[drop.item] = context.withAppLocale().getString(R.string.armory_source_drop_chance, GameStrings.bossName(context, boss.id), formatChancePct(drop.chance))
            }
            boss.commonLoot.items.keys.forEach { key ->
                if (key !in map) map[key] = context.withAppLocale().getString(R.string.armory_source_drop, GameStrings.bossName(context, boss.id))
            }
        }
        gameData.dungeons.forEach { (_, dungeon) ->
            dungeon.rareDrops.forEach { drop ->
                if (drop.item !in map) map[drop.item] = context.withAppLocale().getString(R.string.armory_source_drop_chance, GameStrings.dungeonName(context, dungeon.name), formatChancePct(drop.chance))
            }
        }
        gameData.enemies.forEach { (_, enemy) ->
            enemy.alwaysDrops.forEach { drop ->
                if (drop.item !in map) map[drop.item] = context.withAppLocale().getString(R.string.armory_source_drop_always, GameStrings.enemyName(context, enemy.name))
            }
            enemy.dropTable.forEach { drop ->
                if (drop.item !in map) map[drop.item] = context.withAppLocale().getString(R.string.armory_source_drop_chance, GameStrings.enemyName(context, enemy.name), formatChancePct(drop.chance))
            }
        }
        gameData.marketplace.values.filter { it.categoryName != ShopViewModel.CAPES_CATEGORY }.forEach { category ->
            category.items.keys.forEach { key -> if (key !in map) map[key] = context.withAppLocale().getString(R.string.armory_source_shop) }
        }
        dailyQuestRepo.dwarvenDropPool.forEach { key ->
            if (key !in map) map[key] = context.withAppLocale().getString(R.string.armory_source_daily_bonus)
        }
        weeklyQuestRepo.divineDropPool.forEach { key ->
            if (key !in map) map[key] = context.withAppLocale().getString(R.string.armory_source_weekly_bonus)
        }
        Skills.ALL.forEach { skill ->
            val capeKey = capeKeyForSkill(skill) ?: return@forEach
            if (capeKey in gameData.equipment && capeKey !in map) {
                map[capeKey] = context.withAppLocale().getString(R.string.armory_source_skill_cape, GameStrings.skillName(context, skill))
            }
        }

        return map
    }

    companion object {
        val ARMOR_SLOTS     = setOf("head", "body", "legs", "boots", "shield")
        val ACCESSORY_SLOTS = setOf("ring", "necklace", "cape")
        val TOOL_SLOTS      = setOf("pickaxe", "axe", "fishing_rod", "hoe", "hammer", "tinderbox", "grappling_hook", "frying_pan", "lockpick")

        /** Isle-only equipment (tiered armor + Ancient Signet). Hidden from the armory
         *  list AND excluded from the collection total until the isle is unlocked. */
        val ELDER_ISLE_ITEM_KEYS = setOf(
            "coastal_helm", "coastal_platebody", "coastal_platelegs", "coastal_boots",
            "grove_helm",   "grove_platebody",   "grove_platelegs",   "grove_boots",
            "volcanic_helm","volcanic_platebody","volcanic_platelegs","volcanic_boots",
            "elder_helm",   "elder_platebody",   "elder_platelegs",   "elder_boots",
            "elder_cape",   "elder_shield",      "elder_signet_ring", "elder_amulet",
            "ancient_signet",
        )

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
