package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GuildDailyTemplate(
    val id: String,
    val guild: String,
    @SerialName("guild_level_min") val guildLevelMin: Int,
    @SerialName("guild_level_max") val guildLevelMax: Int,
    val name: String,
    val type: String,
    val target: String,
    val amount: Int,
    val description: String,
    val rewards: GuildQuestRewards,
)
