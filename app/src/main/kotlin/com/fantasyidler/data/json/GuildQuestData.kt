package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GuildQuestData(
    val id: String,
    val guild: String,
    @SerialName("guild_level_required") val guildLevelRequired: Int,
    val name: String,
    val type: String,
    val target: String,
    val amount: Int,
    val description: String,
    val rewards: GuildQuestRewards,
)

@Serializable
data class GuildQuestRewards(
    val coins: Int = 0,
    val xp: Int = 0,
    @SerialName("xp_skill") val xpSkill: String = "",
    val items: Map<String, Int> = emptyMap(),
    val reputation: Int = 0,
)
