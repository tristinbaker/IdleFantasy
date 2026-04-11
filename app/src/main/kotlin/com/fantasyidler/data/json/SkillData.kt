package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SkillData(
    val name: String,
    @SerialName("display_name") val displayName: String,
    val description: String,
    @SerialName("max_level") val maxLevel: Int = 99,
)
