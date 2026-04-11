package com.fantasyidler.util

import android.content.Context

/**
 * Central resolver for game-content display strings.
 *
 * All user-visible game content names and descriptions live in the strings_*.xml resource
 * files so Weblate can manage translations. JSON data files contain only internal snake_case
 * keys — they are never displayed directly. This object converts a key to its localised name.
 *
 * Naming convention in XML: {domain}_{key}_{role}
 *   e.g. item_iron_ore_name, skill_mining_desc, dungeon_dark_cave_name
 *
 * If a string resource is missing (e.g. a new item added before its translation is merged),
 * the key is title-cased as a readable fallback rather than crashing or showing a blank.
 */
object GameStrings {

    fun itemName(context: Context, key: String): String =
        context.stringByName("item_${key}_name") ?: key.toTitleCase()

    fun itemDesc(context: Context, key: String): String =
        context.stringByName("item_${key}_desc") ?: ""

    fun skillName(context: Context, key: String): String =
        context.stringByName("skill_${key}_name") ?: key.toTitleCase()

    fun skillDesc(context: Context, key: String): String =
        context.stringByName("skill_${key}_desc") ?: ""

    fun dungeonName(context: Context, key: String): String =
        context.stringByName("dungeon_${key}_name") ?: key.toTitleCase()

    fun dungeonDesc(context: Context, key: String): String =
        context.stringByName("dungeon_${key}_desc") ?: ""

    fun enemyName(context: Context, key: String): String =
        context.stringByName("enemy_${key}_name") ?: key.toTitleCase()

    fun bossName(context: Context, key: String): String =
        context.stringByName("boss_${key}_name") ?: key.toTitleCase()

    fun questName(context: Context, key: String): String =
        context.stringByName("quest_${key}_name") ?: key.toTitleCase()

    fun questDesc(context: Context, key: String): String =
        context.stringByName("quest_${key}_desc") ?: ""

    fun questObjective(context: Context, key: String): String =
        context.stringByName("quest_${key}_objective") ?: ""

    fun petName(context: Context, key: String): String =
        context.stringByName("pet_${key}_name") ?: key.toTitleCase()

    fun petDesc(context: Context, key: String): String =
        context.stringByName("pet_${key}_desc") ?: ""

    fun spellName(context: Context, key: String): String =
        context.stringByName("spell_${key}_name") ?: key.toTitleCase()

    fun cropName(context: Context, key: String): String =
        context.stringByName("crop_${key}_name") ?: key.toTitleCase()

    fun boneName(context: Context, key: String): String =
        context.stringByName("bone_${key}_name") ?: key.toTitleCase()

    fun agilityCourse(context: Context, key: String): String =
        context.stringByName("agility_${key}_name") ?: key.toTitleCase()
}

// ---------------------------------------------------------------------------
// Extension used internally — resolves a string resource by name at runtime.
// Returns null rather than throwing if the identifier does not exist.
// ---------------------------------------------------------------------------

fun Context.stringByName(name: String): String? {
    val id = resources.getIdentifier(name, "string", packageName)
    return if (id != 0) getString(id) else null
}

// ---------------------------------------------------------------------------
// Shared title-case fallback
// ---------------------------------------------------------------------------

fun String.toTitleCase(): String =
    replace('_', ' ')
        .split(' ')
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
