package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TradeRouteData(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val description: String,
    @SerialName("level_required") val levelRequired: Int,
    @SerialName("coin_cost") val coinCost: Int,
    @SerialName("xp_ranges")   val xpRanges:   Map<String, XpRange>,
    @SerialName("coin_ranges") val coinRanges:  Map<String, CoinRange>,
)

@Serializable
data class CoinRange(val min: Int, val max: Int)

