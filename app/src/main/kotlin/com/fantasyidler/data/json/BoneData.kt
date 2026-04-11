package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BoneData(
    val name: String,
    @SerialName("display_name") val displayName: String,
    val description: String,
    @SerialName("xp_per_bone") val xpPerBone: Double,
    @SerialName("time_per_bone") val timePerBone: Int = 60,
)
