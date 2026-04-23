package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RuneData(
    @SerialName("display_name")  val displayName: String,
    @SerialName("level_required") val levelRequired: Int,
    @SerialName("essence_cost")  val essenceCost: Int,
    @SerialName("xp_per_rune")   val xpPerRune: Double,
    @SerialName("time_per_rune") val timePerRune: Int,
    val description: String = "",
)
