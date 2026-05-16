package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HerbloreRecipe(
    @SerialName("display_name")    val displayName:    String,
    @SerialName("level_required")  val levelRequired:  Int,
    val materials:                 Map<String, Int>,
    @SerialName("output_quantity") val outputQuantity: Int = 1,
    @SerialName("xp_per_item")    val xpPerItem:      Double,
    @SerialName("time_per_item")  val timePerItem:    Int = 120,
    val effects:                   Map<String, Int>,
)
