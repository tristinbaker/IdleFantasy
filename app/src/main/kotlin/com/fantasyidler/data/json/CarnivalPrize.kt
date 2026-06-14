package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CarnivalPrize(
    val key: String,
    @SerialName("display_name") val displayName: String,
    val description: String,
    @SerialName("ticket_cost") val ticketCost: Int,
    val type: String,                          // "equipment", "pet", "xp_lamp"
    @SerialName("xp_amount") val xpAmount: Long = 0L,
    val requirements: Map<String, Int> = emptyMap(),
)
