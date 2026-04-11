package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QuestData(
    val id: String,
    val name: String,
    val skill: String,
    val tier: Int,
    @SerialName("requires_previous") val requiresPrevious: String? = null,
    val type: String,
    val target: String,
    val amount: Int,
    val description: String,
    val rewards: QuestRewards,
)

@Serializable
data class QuestRewards(
    val coins: Int = 0,
    val xp: Int = 0,
    val items: Map<String, Int> = emptyMap(),
)
