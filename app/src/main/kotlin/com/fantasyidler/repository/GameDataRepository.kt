package com.fantasyidler.repository

import android.content.Context
import com.fantasyidler.data.json.BoneData
import com.fantasyidler.data.json.SlayerTaskData
import com.fantasyidler.data.json.CropData
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.CookingRecipe
import com.fantasyidler.data.json.CraftingRecipe
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.json.SkillingDungeonData
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.FletchingRecipe
import com.fantasyidler.data.json.AgilityCourseData
import com.fantasyidler.data.json.FishData
import com.fantasyidler.data.json.GatheringSkillData
import com.fantasyidler.data.json.GemData
import com.fantasyidler.data.json.HerbloreRecipe
import com.fantasyidler.data.json.LogData
import com.fantasyidler.data.json.MarketplaceJson
import com.fantasyidler.data.json.OreData
import com.fantasyidler.data.json.PetData
import com.fantasyidler.data.json.DailyQuestTemplate
import com.fantasyidler.data.json.WeeklyQuestTemplate
import com.fantasyidler.data.json.GuildDailyTemplate
import com.fantasyidler.data.json.GuildQuestData
import com.fantasyidler.data.json.QuestData
import com.fantasyidler.data.json.RuneData
import com.fantasyidler.data.json.SkillData
import com.fantasyidler.data.json.SmithingRecipe
import com.fantasyidler.data.json.ConstructionRecipe
import com.fantasyidler.data.json.ThievingNpcData
import com.fantasyidler.data.json.SpellData
import com.fantasyidler.data.json.TownBuildingData
import com.fantasyidler.data.json.TradeRouteData
import com.fantasyidler.data.json.TreeData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads and caches static game data from the JSON files in assets/data/.
 *
 * All loaders are lazy — data is parsed on first access and retained for
 * the lifetime of the singleton.  The raw [loadAsset] helper is also
 * available for one-off reads.
 *
 * Data flow:
 *   assets/data/[name].json  ->  GameDataRepository (in-memory cache)  ->  ViewModels
 */
@Singleton
class GameDataRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {

    // ------------------------------------------------------------------ helpers

    /** Read a raw asset file and return its text content. */
    fun loadAsset(path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    private inline fun <reified T> asset(path: String): T =
        json.decodeFromString<T>(loadAsset(path))

    // ------------------------------------------------------------------ slayer

    val slayerTasks: Map<String, SlayerTaskData> by lazy {
        asset("data/slayer_tasks.json")
    }

    // ------------------------------------------------------------------ enemies

    val enemies: Map<String, EnemyData> by lazy {
        asset("data/enemies.json")
    }

    /** Maps each enemy key to the dungeon display names it appears in. */
    val enemyLocations: Map<String, List<String>> by lazy {
        val map = mutableMapOf<String, MutableList<String>>()
        dungeons.values.forEach { dungeon ->
            dungeon.enemySpawns.forEach { spawn ->
                map.getOrPut(spawn.enemy) { mutableListOf() }.add(dungeon.displayName)
            }
        }
        map.mapValues { (_, v) -> v.sorted() }
    }

    // ------------------------------------------------------------------ dungeons

    /** All dungeon files in assets/data/dungeons/, keyed by dungeon name. */
    val dungeons: Map<String, DungeonData> by lazy {
        context.assets.list("data/dungeons")
            .orEmpty()
            .filter { it.endsWith(".json") }
            .associate { filename ->
                val data = asset<DungeonData>("data/dungeons/$filename")
                data.name to data
            }
    }

    /** All skilling dungeon files in assets/data/skilling_dungeons/, keyed by dungeon name. */
    val skillingDungeons: Map<String, SkillingDungeonData> by lazy {
        context.assets.list("data/skilling_dungeons")
            .orEmpty()
            .filter { it.endsWith(".json") }
            .associate { filename ->
                val data = asset<SkillingDungeonData>("data/skilling_dungeons/$filename")
                data.name to data
            }
    }

    /** Combat dungeon keys that are only accessible after completing an expedition (unlocked via skillingDungeons). */
    val expeditionLockedDungeons: Set<String> by lazy {
        skillingDungeons.values.mapNotNull { it.unlockDungeon }.toSet()
    }

    // ------------------------------------------------------------------ quests

    val quests: Map<String, QuestData> by lazy {
        asset("data/quests.json")
    }

    val dailyQuestPool: List<DailyQuestTemplate> by lazy {
        asset("data/daily_quests.json")
    }

    val weeklyQuestPool: List<WeeklyQuestTemplate> by lazy {
        asset("data/weekly_quests.json")
    }

    val guildQuests: Map<String, GuildQuestData> by lazy {
        asset("data/guild_quests.json")
    }

    val guildDailyPool: List<GuildDailyTemplate> by lazy {
        asset("data/guild_daily_quests.json")
    }

    // ------------------------------------------------------------------ skills

    /** All skill JSON files in assets/data/skills/, keyed by skill name. */
    val skills: Map<String, SkillData> by lazy {
        context.assets.list("data/skills")
            .orEmpty()
            .filter { it.endsWith(".json") }
            .associate { filename ->
                val data = asset<SkillData>("data/skills/$filename")
                data.name to data
            }
    }

    // ------------------------------------------------------------------ pets

    val pets: Map<String, PetData> by lazy {
        asset("data/pets.json")
    }

    // ------------------------------------------------------------------ bosses

    val bosses: Map<String, BossData> by lazy {
        asset("data/raid_bosses.json")
    }

    // ------------------------------------------------------------------ bones (prayer)

    val bones: Map<String, BoneData> by lazy {
        asset("data/bones.json")
    }

    val runes: Map<String, RuneData> by lazy {
        asset("data/runes.json")
    }

    // ------------------------------------------------------------------ equipment

    val equipment: Map<String, EquipmentData> by lazy {
        asset("data/equipment.json")
    }

    // ------------------------------------------------------------------ recipes

    val smithingRecipes: Map<String, SmithingRecipe> by lazy {
        asset("data/recipes/smithing.json")
    }

    val cookingRecipes: Map<String, CookingRecipe> by lazy {
        asset("data/recipes/cooking.json")
    }

    val fletchingRecipes: Map<String, FletchingRecipe> by lazy {
        asset("data/recipes/fletching.json")
    }

    val craftingRecipes: Map<String, CraftingRecipe> by lazy {
        asset("data/recipes/crafting.json")
    }

    val herbloreRecipes: Map<String, HerbloreRecipe> by lazy {
        asset("data/recipes/herblore.json")
    }

    val constructionRecipes: Map<String, ConstructionRecipe> by lazy {
        asset("data/recipes/construction.json")
    }

    val thievingNpcs: Map<String, ThievingNpcData> by lazy {
        asset<List<ThievingNpcData>>("data/thieving_npcs.json").associateBy { it.key }
    }

    /** Potion key → map of stat name → flat bonus value. Includes enhanced_* variants at 2× base. */
    val potionEffects: Map<String, Map<String, Int>> by lazy {
        val base = herbloreRecipes.mapValues { (_, recipe) -> recipe.effects }
        val enhanced = herbloreRecipes.mapValues { (_, recipe) ->
            recipe.effects.mapValues { (_, v) -> (v * 2.0).toInt().coerceAtLeast(v + 1) }
        }.mapKeys { (key, _) -> "enhanced_$key" }
        base + enhanced
    }

    // ------------------------------------------------------------------ marketplace

    val marketplace: MarketplaceJson by lazy {
        asset("data/marketplace.json")
    }

    // ------------------------------------------------------------------ building tiers

    val townBuildings: Map<String, TownBuildingData> by lazy {
        asset("data/buildings.json")
    }

    // ------------------------------------------------------------------ gathering activities

    /** All mineable ores, keyed by ore key (e.g. "iron_ore"). */
    val ores: Map<String, OreData> by lazy {
        asset("data/ores.json")
    }

    /** All choppable trees, keyed by tree key (e.g. "oak_tree"). */
    val trees: Map<String, TreeData> by lazy {
        asset("data/trees.json")
    }

    /** Gem bonus drop table for mining. */
    val gems: Map<String, GemData> by lazy {
        asset("data/gems.json")
    }

    /** All catchable fish, keyed by item key (e.g. "raw_shrimp"). */
    val fish: Map<String, FishData> by lazy {
        asset("data/fish.json")
    }

    val fishingSkillData: GatheringSkillData by lazy {
        asset("data/skills/fishing.json")
    }

    val firemakingSkillData: GatheringSkillData by lazy {
        asset("data/skills/firemaking.json")
    }

    val runecraftingSkillData: GatheringSkillData by lazy {
        asset("data/skills/runecrafting.json")
    }

    // ------------------------------------------------------------------ agility

    val agilityCourses: Map<String, AgilityCourseData> by lazy {
        asset("data/agility_courses.json")
    }

    // ------------------------------------------------------------------ logs

    val logs: Map<String, LogData> by lazy {
        asset("data/logs.json")
    }

    // ------------------------------------------------------------------ spells

    val spells: Map<String, SpellData> by lazy {
        val raw: Map<String, SpellData> = asset("data/spells.json")
        raw.mapValues { (key, spell) -> spell.copy(name = key) }
    }

    // ------------------------------------------------------------------ farming

    val crops: Map<String, CropData> by lazy {
        asset("data/crops.json")
    }

    // ------------------------------------------------------------------ food

    /** Maps cooked-item key → HP healed per eat (from cooking recipes). */
    val foodHealValues: Map<String, Int> by lazy {
        cookingRecipes.values.associate { it.cookedItem to it.healingValue }
    }

    // ------------------------------------------------------------------ trade routes (mercantile)

    val tradeRoutes: List<TradeRouteData> by lazy {
        context.assets.list("data/trade_routes")
            .orEmpty()
            .filter { it.endsWith(".json") }
            .map { filename -> asset<TradeRouteData>("data/trade_routes/$filename") }
            .sortedBy { it.levelRequired }
    }

    // ------------------------------------------------------------------ sell helpers

    /**
     * Set of all item keys that have a gameplay use (equipment, food, raw materials,
     * recipe inputs/outputs, bones, currencies, quest-collect targets).
     * Items NOT in this set are considered junk and can be sold in bulk.
     */
    val usefulItemKeys: Set<String> by lazy {
        buildSet {
            addAll(equipment.keys)
            cookingRecipes.values.forEach { r -> add(r.rawItem); add(r.cookedItem) }
            smithingRecipes.forEach { (k, r) -> add(k); addAll(r.materials.keys) }
            fletchingRecipes.forEach { (k, r) -> add(k); addAll(r.materials.keys) }
            craftingRecipes.forEach { (k, r) -> add(k); addAll(r.materials.keys) }
            constructionRecipes.forEach { (k, r) -> add(k); addAll(r.materials.keys) }
            addAll(ores.keys)
            addAll(gems.keys)
            addAll(logs.keys)
            addAll(bones.keys)
            add("coins")
            add("rune_essence")
            add("carnival_ticket")
            addAll(runes.keys)
            // Quest collect targets should not be auto-sold
            quests.values.filter { it.type == "collect" }.forEach { add(it.target) }
            // Herblore: protect both ingredients (including junk secondaries), potions, and enhanced variants
            herbloreRecipes.forEach { (key, r) -> add(key); add("enhanced_$key"); addAll(r.materials.keys) }
            // Farming: protect seeds and harvested produce (used in farming-guild daily requests)
            addAll(crops.values.map { it.seedName })
            addAll(crops.values.map { it.id })
            // Marketplace items not in equipment.json
            addAll(marketplace.values.flatMap { it.items.keys })
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Resolves an item key (e.g. "iron_ore", "oak_log") to a human-readable display name.
     * Checks all known item tables in order and falls back to title-casing the key.
     */
    fun itemDisplayName(key: String): String {
        ores[key]?.let { return it.displayName }
        gems[key]?.let { return it.displayName }
        logs[key]?.let { return it.displayName }
        runes[key]?.let { return it.displayName }
        smithingRecipes[key]?.let { return it.displayName }
        cookingRecipes.values.find { it.cookedItem == key }?.let { return it.displayName }
        fletchingRecipes[key]?.let { return it.displayName }
        craftingRecipes[key]?.let { return it.displayName }
        return key.split('_').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
}
