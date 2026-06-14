package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CropData(
    val id: String,
    @SerialName("display_name")              val displayName: String,
    val emoji: String,
    @SerialName("seed_name")                 val seedName: String,
    @SerialName("growth_time_hours")         val growthTimeHours: Int,
    @SerialName("farming_level_required")    val levelRequired: Int,
    @SerialName("planting_xp")               val plantingXp: Int,
    @SerialName("harvest_xp")               val harvestXp: Int,
    @SerialName("yield_min")                 val yieldMin: Int,
    @SerialName("yield_max")                 val yieldMax: Int,
    @SerialName("seed_cost")                 val seedCost: Int = 0,
    /**
     * True for cover crops (clover, wildflower, moonblossom).
     * When harvested, these reset the patch's consecutive-same-crop streak to 0
     * and grant [soilRestoreXp] farming XP, but yield no harvestable produce.
     */
    @SerialName("is_cover_crop")             val isCoverCrop: Boolean = false,
    /**
     * Farming XP awarded when a cover crop is harvested (soil restored).
     * Ignored for regular crops.
     */
    @SerialName("soil_restore_xp")          val soilRestoreXp: Int = 0,
) {
    val growthTimeMs: Long get() = growthTimeHours.toLong() * 3_600_000L
}
