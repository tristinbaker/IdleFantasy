package com.fantasyidler.repository

import android.content.Context
import com.fantasyidler.data.json.BoneData
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.CookingRecipe
import com.fantasyidler.data.json.CraftingRecipe
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.FletchingRecipe
import com.fantasyidler.data.json.AgilityCourseData
import com.fantasyidler.data.json.GatheringSkillData
import com.fantasyidler.data.json.GemData
import com.fantasyidler.data.json.LogData
import com.fantasyidler.data.json.MarketplaceJson
import com.fantasyidler.data.json.OreData
import com.fantasyidler.data.json.PetData
import com.fantasyidler.data.json.QuestData
import com.fantasyidler.data.json.RuneData
import com.fantasyidler.data.json.SkillData
import com.fantasyidler.data.json.SmithingRecipe
import com.fantasyidler.data.json.SpellData
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

    // ------------------------------------------------------------------ enemies

    val enemies: Map<String, EnemyData> by lazy {
        asset("data/enemies.json")
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

    // ------------------------------------------------------------------ quests

    val quests: Map<String, QuestData> by lazy {
        asset("data/quests.json")
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

    // ------------------------------------------------------------------ marketplace

    val marketplace: MarketplaceJson by lazy {
        asset("data/marketplace.json")
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

    // ------------------------------------------------------------------ food

    /** Maps cooked-item key → HP healed per eat (from cooking recipes). */
    val foodHealValues: Map<String, Int> by lazy {
        cookingRecipes.values.associate { it.cookedItem to it.healingValue }
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
            addAll(ores.keys)
            addAll(gems.keys)
            addAll(logs.keys)
            addAll(bones.keys)
            add("coins")
            add("rune_essence")
            addAll(runes.keys)
            // Quest collect targets should not be auto-sold
            quests.values.filter { it.type == "collect" }.forEach { add(it.target) }
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
