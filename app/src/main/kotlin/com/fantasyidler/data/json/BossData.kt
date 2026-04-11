package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BossData(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val emoji: String,
    val description: String,
    @SerialName("combat_level_required") val combatLevelRequired: Int,
    @SerialName("duration_minutes") val durationMinutes: Int,
    val hp: Int,
    @SerialName("combat_stats") val combatStats: BossCombatStats,
    @SerialName("defensive_stats") val defensiveStats: BossDefensiveStats,
    @SerialName("xp_rewards") val xpRewards: Map<String, Int>,
    @SerialName("common_loot") val commonLoot: BossCommonLoot,
    @SerialName("rare_drops") val rareDrops: List<BossRareDrop>,
    val pet: BossPet? = null,
)

@Serializable
data class BossCombatStats(
    @SerialName("attack_level")  val attackLevel:   Int = 1,
    @SerialName("strength_level") val strengthLevel: Int = 1,
    @SerialName("defense_level") val defenseLevel:  Int = 1,
    @SerialName("attack_bonus")  val attackBonus:   Int = 0,
    @SerialName("strength_bonus") val strengthBonus: Int = 0,
)

@Serializable
data class BossDefensiveStats(
    @SerialName("attack_defense")  val attackDefense:  Int = 0,
    @SerialName("strength_defense") val strengthDefense: Int = 0,
)

@Serializable
data class BossCommonLoot(
    @SerialName("coins_min") val coinsMin: Int,
    @SerialName("coins_max") val coinsMax: Int,
    val items: Map<String, BossLootRange>,
)

@Serializable
data class BossLootRange(
    val min: Int,
    val max: Int,
)

@Serializable
data class BossRareDrop(
    val item: String,
    val chance: Double,
)

@Serializable
data class BossPet(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val emoji: String,
    val chance: Double,
)
