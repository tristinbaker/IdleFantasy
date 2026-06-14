package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DungeonData(
    val name: String,
    @SerialName("display_name") val displayName: String,
    val description: String,
    @SerialName("recommended_level") val recommendedLevel: Int,
    @SerialName("encounter_rate") val encounterRate: Double,
    @SerialName("enemy_spawns") val enemySpawns: List<EnemySpawn>,
    /** When true this dungeon is only accessible via a lore unlock, not the normal level gate. */
    @SerialName("lore_unlock_only") val loreUnlockOnly: Boolean = false,
    /** Rolled once per completed dungeon run (not per kill). Uses same type as BossRareDrop. */
    @SerialName("rare_drops") val rareDrops: List<DungeonRareDrop> = emptyList(),
    /** When true the player cannot die here; HP is clamped to 1 minimum. */
    @SerialName("safe_zone") val safeZone: Boolean = false,
    /** Hint shown in the dungeon list when lore-locked; overrides the generic expedition hint string. */
    @SerialName("lore_hint") val loreHint: String? = null,
)

@Serializable
data class DungeonRareDrop(
    val item: String,
    val chance: Double,
)

@Serializable
data class EnemySpawn(
    val enemy: String,
    val weight: Int,
)
