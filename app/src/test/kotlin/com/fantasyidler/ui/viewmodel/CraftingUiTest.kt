package com.fantasyidler.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests verifying that owned item quantities are correctly retrievable
 * and computed from inventory state for crafting skills UI.
 */
class CraftingUiTest {

    @Test
    fun `owned inventory count is retrieved correctly for recipes`() {
        val inventory = mapOf(
            "bronze_bar" to 15,
            "bronze_sword" to 3,
            "cooked_shrimp" to 42,
            "air_rune" to 100,
        )

        val state = CraftingUiState(inventory = inventory)

        assertEquals(3, state.inventory["bronze_sword"] ?: 0)
        assertEquals(42, state.inventory["cooked_shrimp"] ?: 0)
        assertEquals(0, state.inventory["iron_sword"] ?: 0)
    }

    @Test
    fun `maxCraftable calculates maximum quantity based on materials`() {
        val recipe = CraftableRecipe(
            key = "bronze_sword",
            displayName = "Bronze Sword",
            levelRequired = 1,
            materials = mapOf("bronze_bar" to 2),
            outputKey = "bronze_sword",
            outputQty = 1,
            xpPerItem = 10.0,
            skillName = "smithing",
        )

        val state = CraftingUiState(effectiveInventory = mapOf("bronze_bar" to 9))

        assertEquals(4, state.maxCraftable(recipe))
    }
}
