package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PetData(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val emoji: String,
    val description: String,
    val source: String,
    @SerialName("effect_type") val effectType: String,
    @SerialName("boosted_skill") val boostedSkill: String,
    @SerialName("boost_percent") val boostPercent: Int,
)
