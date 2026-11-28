package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Pet Arena content (data/pet_arena.json): wild zones, their creatures, the trainer ladder and treats. */
@Serializable
data class PetArenaData(
    val creatures: Map<String, ArenaCreature> = emptyMap(),
    val zones: List<ArenaZone> = emptyList(),
    /** In ladder order: each trainer awards the next badge. */
    val trainers: List<ArenaTrainer> = emptyList(),
    /** Treat item key → pet XP per treat fed. */
    val treats: Map<String, Long> = emptyMap(),
)

@Serializable
data class ArenaCreature(
    val id: String,
    val emoji: String,
    @SerialName("battle_type")  val battleType: String,
    @SerialName("base_attack")  val baseAttack: Int,
    @SerialName("base_defense") val baseDefense: Int,
    @SerialName("base_hp")      val baseHp: Int,
)

@Serializable
data class ArenaZone(
    val id: String,
    val emoji: String,
    @SerialName("level_min")       val levelMin: Int,
    @SerialName("level_max")       val levelMax: Int,
    @SerialName("badges_required") val badgesRequired: Int = 0,
    val creatures: List<String>,
    /** Rolled once per battle won. */
    val loot: List<ArenaLoot> = emptyList(),
)

@Serializable
data class ArenaLoot(
    val item: String,
    val chance: Double,
    @SerialName("quantity_min") val quantityMin: Int = 1,
    @SerialName("quantity_max") val quantityMax: Int = 1,
)

@Serializable
data class ArenaTrainer(
    val id: String,
    val emoji: String,
    @SerialName("badge_emoji")  val badgeEmoji: String,
    /** Every creature on the trainer's team fights at this level. */
    val level: Int,
    val team: List<String>,
    /** One-time reward for the first win. */
    @SerialName("reward_coins") val rewardCoins: Long = 0L,
    @SerialName("reward_items") val rewardItems: Map<String, Int> = emptyMap(),
)
