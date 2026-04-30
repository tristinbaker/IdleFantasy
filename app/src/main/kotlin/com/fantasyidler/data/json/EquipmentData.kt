package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EquipmentData(
    val name: String,
    @SerialName("display_name")           val displayName: String,
    val slot: String,
    @SerialName("combat_style")           val combatStyle: String? = null,
    val description: String = "",
    @SerialName("attack_bonus")           val attackBonus: Int = 0,
    @SerialName("strength_bonus")         val strengthBonus: Int = 0,
    @SerialName("defense_bonus")          val defenseBonus: Int = 0,
    val requirements: Map<String, Int> = emptyMap(),
    @SerialName("infinite_runes")          val infiniteRunes: String? = null,
    // Gathering tool efficiency fields (null for combat gear)
    @SerialName("mining_efficiency")      val miningEfficiency: Float? = null,
    @SerialName("woodcutting_efficiency") val woodcuttingEfficiency: Float? = null,
    @SerialName("fishing_efficiency")     val fishingEfficiency: Float? = null,
)
