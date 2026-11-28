package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PetData(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val emoji: String,
    val description: String,
    val source: String,
    @SerialName("effect_type") val effectType: String,
    @SerialName("boosted_skill") val boostedSkill: String,
    @SerialName("boost_percent") val boostPercent: Int,
    /** Pet Arena (2.1.0): "melee", "ranged" or "magic"; each beats the next (see PetBattle). */
    @SerialName("battle_type")  val battleType: String = "melee",
    /** "common" to "legendary"; harder-to-get pets fight with higher stats. */
    val rarity: String = "common",
    @SerialName("base_attack")  val baseAttack: Int = 64,
    @SerialName("base_defense") val baseDefense: Int = 64,
    @SerialName("base_hp")      val baseHp: Int = 64,
)
