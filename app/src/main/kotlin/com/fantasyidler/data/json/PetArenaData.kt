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
    /** Treat item key → what feeding one does. */
    val treats: Map<String, ArenaTreat> = emptyMap(),
    /** The Ultimate Challenge: one enormous foe against an 8-pet team, after all 8 badges. */
    val ultimate: ArenaUltimate? = null,
)

@Serializable
data class ArenaUltimate(
    val id: String,
    val emoji: String,
    val level: Int,
    val attack: Int,
    val defense: Int,
    @SerialName("max_hp") val maxHp: Int,
    /** Its type cycles through these, one phase at a time. */
    val phases: List<String>,
    @SerialName("phase_turns") val phaseTurns: Int,
    @SerialName("attacks_per_turn") val attacksPerTurn: Int,
    /** Every this many turns it also strikes the whole team for [areaFactor] of a normal hit. */
    @SerialName("area_every") val areaEvery: Int,
    @SerialName("area_factor") val areaFactor: Double,
    /** From this turn on its damage is multiplied by [enrageMultiplier]: the fight is a damage race. */
    @SerialName("enrage_turn") val enrageTurn: Int,
    @SerialName("enrage_multiplier") val enrageMultiplier: Double,
    @SerialName("team_size") val teamSize: Int = 8,
    @SerialName("pet_drop") val petDrop: String,
    @SerialName("pet_chance") val petChance: Double,
    @SerialName("xp_each") val xpEach: Long = 0L,
    /** Rolled on every win, on top of the pet chance. */
    val loot: List<ArenaLoot> = emptyList(),
)

/** A cooked pet treat: fed between fights, it heals and makes the pet Well Fed for its next battle. */
@Serializable
data class ArenaTreat(
    /** Share of the pet's max HP restored per treat. */
    val heal: Double,
    /** Attack and defense bonus (%) for the pet's next battle session. */
    @SerialName("well_fed") val wellFed: Int,
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
    /** Wild creature strength here (null = PetBattle.WILD_STRENGTH); tuned per zone so each feels as risky. */
    val strength: Double? = null,
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
