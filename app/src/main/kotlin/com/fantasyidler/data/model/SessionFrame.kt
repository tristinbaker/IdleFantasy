package com.fantasyidler.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One minute of a skill session (60 frames per session).
 * Matches the frame schema emitted by the IdleApes Python simulators.
 */
@Serializable
data class SessionFrame(
    val minute: Int,
    @SerialName("xp_gain")    val xpGain: Int,
    @SerialName("xp_before")  val xpBefore: Long,
    @SerialName("xp_after")   val xpAfter: Long,
    @SerialName("level_before") val levelBefore: Int,
    @SerialName("level_after")  val levelAfter: Int,
    /** Item key → quantity gained this minute */
    val items: Map<String, Int> = emptyMap(),
    @SerialName("leveled_up") val leveledUp: Boolean = false,
    /** Each entry is [newLevel, minuteItOccurred] */
    @SerialName("level_ups")  val levelUps: List<List<Int>> = emptyList(),
    /** Agility only — false when a lap was failed */
    val success: Boolean = true,
    /**
     * Combat only — per-skill XP breakdown for this frame.
     * Empty for gathering/crafting frames (those use xpGain + skillName instead).
     */
    @SerialName("xp_by_skill") val xpBySkill: Map<String, Long> = emptyMap(),
    /** Combat only — total kills this minute (0 for non-combat frames). */
    val kills: Int = 0,
    /** Combat only — enemy key → kills this minute. */
    @SerialName("kills_by_enemy") val killsByEnemy: Map<String, Int> = emptyMap(),
    /** Combat only — true when the player died (underleveled dungeon or boss loss). */
    val died: Boolean = false,
    /** Combat only — food items consumed this minute (item key → quantity eaten). */
    @SerialName("food_consumed") val foodConsumed: Map<String, Int> = emptyMap(),
)
