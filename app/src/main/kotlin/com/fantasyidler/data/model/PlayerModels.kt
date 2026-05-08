package com.fantasyidler.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Deserialised sub-models stored inside Player's JSON columns
// ---------------------------------------------------------------------------

@Serializable
data class PlayerFlags(
    @SerialName("current_hp") val currentHp: Int = 10,
    @SerialName("equipped_food") val equippedFood: Map<String, Int> = emptyMap(),
    @SerialName("equipped_arrows") val equippedArrows: String? = null,
    @SerialName("equipped_runes") val equippedRunes: String? = null,
    @SerialName("active_spell") val activeSpell: String? = null,
    @SerialName("battery_prompt_shown") val batteryPromptShown: Boolean = false,
    /** Epoch ms when the 2× XP boost expires; 0 = not active. */
    @SerialName("xp_boost_expires_at") val xpBoostExpiresAt: Long = 0L,
    @SerialName("character_name") val characterName: String = "",
    @SerialName("character_gender") val characterGender: String = "",
    @SerialName("character_race") val characterRace: String = "",
    /** False until the player completes or skips the character setup prompt. */
    @SerialName("character_setup_done") val characterSetupDone: Boolean = false,
)

@Serializable
data class OwnedPet(
    val id: String,
    @SerialName("boost_percent") val boostPercent: Int = 0,
)

/** Portable save file written by export and read by import. */
@Serializable
data class PlayerExport(
    val skillLevels: String,
    val skillXp: String,
    val inventory: String,
    val equipped: String,
    val flags: String,
    val pets: String,
    val coins: Long,
    val questProgress: List<QuestProgress> = emptyList(),
)

// ---------------------------------------------------------------------------
// Equipment slot keys — match the Python equipped dict keys exactly
// ---------------------------------------------------------------------------

object EquipSlot {
    // Combat gear — slot keys match equipment.json "slot" field exactly
    const val WEAPON   = "weapon"
    const val HEAD     = "head"
    const val BODY     = "body"
    const val LEGS     = "legs"
    const val CAPE     = "cape"
    const val RING     = "ring"
    const val NECKLACE = "necklace"
    const val SHIELD   = "shield"

    // Gathering tools
    const val PICKAXE     = "pickaxe"
    const val AXE         = "axe"
    const val FISHING_ROD = "fishing_rod"

    val COMBAT_SLOTS = listOf(WEAPON, HEAD, BODY, LEGS, CAPE, RING, NECKLACE, SHIELD)
    val TOOL_SLOTS   = listOf(PICKAXE, AXE, FISHING_ROD)
    val ALL          = COMBAT_SLOTS + TOOL_SLOTS
}

// ---------------------------------------------------------------------------
// Canonical skill keys — must match keys in skill_levels / skill_xp JSON
// ---------------------------------------------------------------------------

object Skills {
    // Gathering
    const val MINING      = "mining"
    const val FISHING     = "fishing"
    const val WOODCUTTING = "woodcutting"
    const val FARMING     = "farming"
    const val FIREMAKING  = "firemaking"
    const val AGILITY     = "agility"

    // Crafting
    const val SMITHING     = "smithing"
    const val COOKING      = "cooking"
    const val FLETCHING    = "fletching"
    const val CRAFTING     = "crafting"
    const val RUNECRAFTING = "runecrafting"

    // Combat
    const val ATTACK    = "attack"
    const val STRENGTH  = "strength"
    const val DEFENSE   = "defense"
    const val RANGED    = "ranged"
    const val MAGIC     = "magic"
    const val HITPOINTS = "hitpoints"
    const val PRAYER    = "prayer"

    val GATHERING = listOf(MINING, FISHING, WOODCUTTING, FIREMAKING, AGILITY)
    val CRAFTING_SKILLS = listOf(SMITHING, COOKING, FLETCHING, CRAFTING, RUNECRAFTING)
    val COMBAT = listOf(ATTACK, STRENGTH, DEFENSE, RANGED, MAGIC, HITPOINTS, PRAYER)
    val ALL = GATHERING + CRAFTING_SKILLS + COMBAT

    val DEFAULT_LEVELS: Map<String, Int> = ALL.associateWith { 1 }
    val DEFAULT_XP: Map<String, Long> = ALL.associateWith { 0L }
}
