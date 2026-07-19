package com.fantasyidler.util

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.fantasyidler.R

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
        context.stringByName("item_${key}_name")
            ?: context.stringByName("crop_${key}_name")
            ?: key.toTitleCase()

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

    fun skillingDungeonName(context: Context, key: String, fallback: String): String =
        context.stringByName("skilling_dungeon_${key}_name") ?: fallback

    fun skillingDungeonDesc(context: Context, key: String, fallback: String): String =
        context.stringByName("skilling_dungeon_${key}_desc") ?: fallback

    fun enemyName(context: Context, key: String): String =
        context.stringByName("enemy_${key}_name") ?: key.toTitleCase()

    fun bossName(context: Context, key: String): String =
        context.stringByName("boss_${key}_name") ?: key.toTitleCase()

    fun bossDesc(context: Context, key: String): String =
        context.stringByName("boss_${key}_desc") ?: ""

    fun slotName(context: Context, slot: String): String =
        context.stringByName("equip_slot_$slot") ?: slot.toTitleCase()

    fun questName(context: Context, key: String, fallback: String = key.toTitleCase()): String =
        context.stringByName("quest_${key}_name") ?: fallback

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

    fun tradeRouteName(context: Context, id: String, fallback: String = id.toTitleCase()): String =
        context.stringByName("trade_route_${id}_name") ?: fallback

    fun tradeRouteDesc(context: Context, id: String, fallback: String = ""): String =
        context.stringByName("trade_route_${id}_desc") ?: fallback

    fun craftingCategory(context: Context, raw: String): String {
        val resId = context.resources.getIdentifier(
            "crafting_cat_${raw.lowercase()}", "string", context.packageName)
        return if (resId != 0) context.getString(resId) else raw
    }

    fun craftingTier(context: Context, raw: String): String {
        val resId = context.resources.getIdentifier(
            "crafting_tier_${raw.lowercase()}", "string", context.packageName)
        return if (resId != 0) context.getString(resId) else raw
    }

    fun boneName(context: Context, key: String): String =
        context.stringByName("bone_${key}_name") ?: key.toTitleCase()

    fun agilityCourse(context: Context, key: String): String =
        context.stringByName("agility_${key}_name") ?: key.toTitleCase()

    fun agilityCourseDesc(context: Context, key: String): String =
        context.stringByName("agility_${key}_desc") ?: ""

    fun thievingNpcName(context: Context, key: String): String =
        context.stringByName("thieving_npc_${key}_name") ?: key.toTitleCase()

    fun seasonalEventName(context: Context, id: String, fallback: String): String =
        context.stringByName("seasonal_event_${id}_name") ?: fallback

    fun seasonalEventBanner(context: Context, id: String, fallback: String): String =
        context.stringByName("seasonal_event_${id}_banner") ?: fallback

    fun seasonalBountyName(context: Context, id: String, fallback: String): String =
        context.stringByName("seasonal_bounty_${id}_name") ?: fallback

    fun seasonalBountyHint(context: Context, id: String, fallback: String): String =
        context.stringByName("seasonal_bounty_${id}_hint") ?: fallback

    fun seasonalMinigameName(context: Context, id: String, fallback: String): String =
        context.stringByName("seasonal_minigame_${id}_name") ?: fallback

    fun seasonalRewardDesc(context: Context, eventId: String, tokens: Int, fallback: String): String =
        context.stringByName("seasonal_reward_${eventId}_${tokens}_desc") ?: fallback

    fun skillEmoji(key: String): String = when (key) {
        "mining"      -> "⛏️"
        "fishing"     -> "🎣"
        "woodcutting" -> "🪓"
        "farming"     -> "🌱"
        "firemaking"  -> "🔥"
        "agility"     -> "🏃"
        "smithing"    -> "🔨"
        "cooking"     -> "🍳"
        "fletching"   -> "🪶"
        "crafting"    -> "💍"
        "runecrafting"-> "🔮"
        "attack"      -> "⚔️"
        "strength"    -> "💪"
        "defense"     -> "🛡️"
        "ranged"      -> "🎯"
        "magic"       -> "🪄"
        "hitpoints"   -> "❤️"
        "prayer"      -> "🙏"
        "mercantile"  -> "💰"
        "slayer"      -> "💀"
        "herblore"     -> "🌿"
        "construction" -> "🏗️"
        "thieving"     -> "🥷"
        "combat"       -> "⚔️"
        "carnival"     -> "🎪"
        "tower"        -> "🗼"
        else           -> "🎮"
    }

    /** Drawable icon for a skill, from Shikashi's Fantasy Icons Pack. Null if the skill (or non-skill key like "boss"/"carnival") has no icon; fall back to [skillEmoji]. */
    fun skillIconRes(key: String): Int? = when (key) {
        "mining"       -> R.drawable.skill_mining
        "fishing"      -> R.drawable.skill_fishing
        "woodcutting"  -> R.drawable.skill_woodcutting
        "farming"      -> R.drawable.skill_farming
        "firemaking"   -> R.drawable.skill_firemaking
        "agility"      -> R.drawable.skill_agility
        "smithing"     -> R.drawable.skill_smithing
        "cooking"      -> R.drawable.skill_cooking
        "fletching"    -> R.drawable.skill_fletching
        "crafting"     -> R.drawable.skill_crafting
        "runecrafting" -> R.drawable.skill_runecrafting
        "attack"       -> R.drawable.skill_attack
        "strength"     -> R.drawable.skill_strength
        "defense"      -> R.drawable.skill_defense
        "ranged"       -> R.drawable.skill_ranged
        "magic"        -> R.drawable.skill_magic
        "hitpoints"    -> R.drawable.skill_hitpoints
        "prayer"       -> R.drawable.skill_prayer
        "mercantile"   -> R.drawable.skill_mercantile
        "slayer"       -> R.drawable.skill_slayer
        "herblore"     -> R.drawable.skill_herblore
        "construction" -> R.drawable.skill_construction
        "thieving"     -> R.drawable.skill_thieving
        "combat"       -> R.drawable.skill_combat
        "expedition"   -> R.drawable.skill_expedition
        else           -> null
    }
}

// ---------------------------------------------------------------------------
// Returns a context whose locale matches the app's in-app language setting.
// Use this instead of a raw ApplicationContext when looking up game strings
// from non-Activity code (ViewModels, notification receivers, etc.).
// ---------------------------------------------------------------------------

fun Context.withAppLocale(): Context {
    val locales = AppCompatDelegate.getApplicationLocales()
    if (locales.isEmpty) return this
    val locale = locales[0] ?: return this
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    return createConfigurationContext(config)
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
// Resolves a drawable resource by name at runtime (e.g. seasonal banner icons,
// whose resource name is stored as a plain string in JSON/save data).
// Returns null rather than throwing if the identifier does not exist.
// ---------------------------------------------------------------------------

fun Context.drawableByName(name: String): Int? {
    val id = resources.getIdentifier(name, "drawable", packageName)
    return if (id != 0) id else null
}

// ---------------------------------------------------------------------------
// Shared title-case fallback
// ---------------------------------------------------------------------------

fun String.toTitleCase(): String =
    replace('_', ' ')
        .split(' ')
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
