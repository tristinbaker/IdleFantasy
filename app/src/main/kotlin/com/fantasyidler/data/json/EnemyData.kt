package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EnemyData(
    val name: String,
    @SerialName("display_name") val displayName: String,
    val hp: Int,
    @SerialName("combat_stats") val combatStats: EnemyCombatStats,
    @SerialName("defensive_stats") val defensiveStats: EnemyDefensiveStats,
    @SerialName("xp_drops") val xpDrops: Map<String, Int>,
    @SerialName("drop_table") val dropTable: List<DropEntry> = emptyList(),
    @SerialName("always_drops") val alwaysDrops: List<AlwaysDrop> = emptyList(),
)

@Serializable
data class EnemyCombatStats(
    @SerialName("attack_level") val attackLevel: Int,
    @SerialName("strength_level") val strengthLevel: Int,
    @SerialName("defense_level") val defenseLevel: Int,
    @SerialName("attack_bonus") val attackBonus: Int,
    @SerialName("strength_bonus") val strengthBonus: Int,
)

@Serializable
data class EnemyDefensiveStats(
    @SerialName("attack_defense") val attackDefense: Int,
    @SerialName("strength_defense") val strengthDefense: Int,
    @SerialName("ranged_defense") val rangedDefense: Int,
    @SerialName("magic_defense") val magicDefense: Int,
)

@Serializable
data class DropEntry(
    val item: String,
    val chance: Double,
    @SerialName("quantity_min") val quantityMin: Int,
    @SerialName("quantity_max") val quantityMax: Int,
)

@Serializable
data class AlwaysDrop(
    val item: String,
    val quantity: Int,
)
