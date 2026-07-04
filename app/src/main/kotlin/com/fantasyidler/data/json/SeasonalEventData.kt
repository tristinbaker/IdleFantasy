package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeasonalEventData(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("start_ms") val startMs: Long,
    @SerialName("end_ms") val endMs: Long,
    @SerialName("token_goal") val tokenGoal: Int,
    /** Subset of "bounty" | "expedition" | "boss" | "minigame". */
    val pillars: List<String>,
    @SerialName("bounty_tasks") val bountyTasks: List<SeasonalBountyTaskData> = emptyList(),
    @SerialName("expedition_dungeon_key") val expeditionDungeonKey: String? = null,
    @SerialName("boss_key") val bossKey: String? = null,
    val minigame: SeasonalMinigameConfig? = null,
    @SerialName("reward_tiers") val rewardTiers: List<SeasonalRewardTierData> = emptyList(),
    @SerialName("banner_text") val bannerText: String,
) {
    fun isActiveAt(nowMs: Long): Boolean = nowMs in startMs..endMs
}

/** A single Bounty Board task. [type] is "gather" | "craft" | "kill", matched the same way guild quests are. */
@Serializable
data class SeasonalBountyTaskData(
    val id: String,
    val type: String,
    val target: String,
    val amount: Int,
    @SerialName("display_name") val displayName: String,
    /** Short "where to do this" hint shown under the task, e.g. "Woodcutting" or "The Sunspire Expedition". */
    val hint: String,
)

/**
 * A whack-a-mole style reflex minigame: over [rounds] rounds, an ember lights up in a random
 * hole (of [holeCount]) for [visibleMs] and the player must tap it before it goes out.
 * Landing at least [hitsRequired] hits wins a token; falling short is a real failure.
 */
@Serializable
data class SeasonalMinigameConfig(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val rounds: Int,
    @SerialName("hole_count") val holeCount: Int,
    @SerialName("hits_required") val hitsRequired: Int,
    @SerialName("visible_ms") val visibleMs: Long,
    @SerialName("cooldown_ms") val cooldownMs: Long,
)

@Serializable
data class SeasonalRewardTierData(
    val tokens: Int,
    val description: String,
)
