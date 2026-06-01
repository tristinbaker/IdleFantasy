package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SlayerTaskData(
    @SerialName("min_kills")    val minKills: Int,
    @SerialName("max_kills")    val maxKills: Int,
    @SerialName("slayer_level") val slayerLevel: Int,
    @SerialName("xp_per_kill")  val xpPerKill: Int,
)
