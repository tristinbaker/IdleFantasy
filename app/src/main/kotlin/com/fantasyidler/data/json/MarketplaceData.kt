package com.fantasyidler.data.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/** Top-level structure: map of category key → category data. */
typealias MarketplaceJson = Map<String, MarketplaceCategory>

@Serializable
data class MarketplaceCategory(
    @SerialName("category_name") val categoryName: String,
    val description: String,
    val items: Map<String, MarketplaceItem>,
)

@Serializable
data class MarketplaceItem(
    @SerialName("display_name") val displayName: String,
    val description: String,
    val price: Int,
    /**
     * Stock is either the string "unlimited" or a numeric value.
     * Stored as [JsonPrimitive] so callers can check [isUnlimited].
     */
    val stock: JsonPrimitive,
) {
    val isUnlimited: Boolean get() = stock.isString && stock.content == "unlimited"
    val stockCount: Int? get() = if (isUnlimited) null else stock.content.toIntOrNull()
}
