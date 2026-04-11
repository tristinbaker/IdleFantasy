package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpellData(
    /** Injected from the JSON key after loading — not present in the JSON itself. */
    val name: String = "",
    @SerialName("display_name")          val displayName: String,
    @SerialName("rune_type")             val runeType: String,
    @SerialName("magic_level_required")  val magicLevelRequired: Int,
    @SerialName("max_hit")               val maxHit: Int,
    @SerialName("rune_cost")             val runeCost: Int,
    val description: String = "",
)
