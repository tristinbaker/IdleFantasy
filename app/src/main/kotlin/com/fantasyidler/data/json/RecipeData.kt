package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Smithing recipe — smelting bars or forging items. */
@Serializable
data class SmithingRecipe(
    val type: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("level_required") val levelRequired: Int,
    val materials: Map<String, Int>,
    @SerialName("output_quantity") val outputQuantity: Int,
    @SerialName("xp_per_item") val xpPerItem: Double,
    @SerialName("time_per_item") val timePerItem: Int,
)

/** Cooking recipe — raw → cooked transformation. */
@Serializable
data class CookingRecipe(
    @SerialName("raw_item") val rawItem: String,
    @SerialName("cooked_item") val cookedItem: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("level_required") val levelRequired: Int,
    @SerialName("xp_per_item") val xpPerItem: Double,
    @SerialName("healing_value") val healingValue: Int,
    @SerialName("time_per_item") val timePerItem: Int,
)

/** Fletching recipe — bows, arrows, and components. */
@Serializable
data class FletchingRecipe(
    @SerialName("item_name") val itemName: String,
    @SerialName("display_name") val displayName: String,
    val type: String,
    @SerialName("level_required") val levelRequired: Int,
    @SerialName("xp_per_item") val xpPerItem: Double,
    val materials: Map<String, Int>,
    @SerialName("output_quantity") val outputQuantity: Int,
    @SerialName("time_per_batch") val timePerBatch: Int,
    val damage: Int? = null,
    @SerialName("attack_bonus")   val attackBonus:   Int? = null,
    @SerialName("strength_bonus") val strengthBonus: Int? = null,
    val requirements: Map<String, Int> = emptyMap(),
)

/** Crafting recipe — jewellery and other items from precious materials. */
@Serializable
data class CraftingRecipe(
    val type: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("level_required") val levelRequired: Int,
    val materials: Map<String, Int>,
    @SerialName("output_quantity") val outputQuantity: Int,
    @SerialName("xp_per_item") val xpPerItem: Double,
    @SerialName("time_per_item") val timePerItem: Int,
)
