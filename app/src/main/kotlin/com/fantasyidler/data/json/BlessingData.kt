package com.fantasyidler.data.json

enum class BlessingType { XP, DEFENSE, COINS }

data class BlessingData(
    val key: String,
    val prayerLevelRequired: Int,
    val type: BlessingType,
    /** ×1.10 for XP, 3 for DEF, 0.10 for COINS. */
    val magnitude: Float,
)
