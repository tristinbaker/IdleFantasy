package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeeklyQuestTemplate(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val skill: String,
    val type: String,
    val target: String,
    val amount: Int,
    val description: String,
    @SerialName("level_required") val levelRequired: Int = 1,
)
