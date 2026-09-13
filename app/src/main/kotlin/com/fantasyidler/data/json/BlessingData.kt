package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class BlessingType { XP, DEFENSE, COINS }

/** A church blessing (assets/data/blessings.json). */
@Serializable
data class BlessingData(
    val key: String,
    @SerialName("prayer_level_required") val prayerLevelRequired: Int,
    val type: BlessingType,
    /** ×1.10 for XP, 3 for DEF, 0.10 for COINS. */
    val magnitude: Float,
)
