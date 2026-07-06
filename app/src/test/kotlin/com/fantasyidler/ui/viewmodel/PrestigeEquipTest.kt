package com.fantasyidler.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [InventoryViewModel.UiState.effectiveSkillLevel], which treats
 * any prestiged combat skill as level 99 for gear equip checks.
 *
 * Bug fix: after prestiging a combat skill (e.g. Strength 99 → 1 with
 * 1 prestige star), gear that required a high level should still be
 * equippable because prestiging proves mastery.
 */
class PrestigeEquipTest {

    // ------------------------------------------------------------------
    // No prestige — effective level equals base level
    // ------------------------------------------------------------------

    @Test
    fun `effectiveSkillLevel returns base level when no prestige entry`() {
        val state = InventoryViewModel.UiState(
            skillLevels = mapOf("strength" to 50, "attack" to 30),
            skillPrestige = emptyMap(),
        )
        assertEquals(50, state.effectiveSkillLevel("strength"))
        assertEquals(30, state.effectiveSkillLevel("attack"))
    }

    @Test
    fun `effectiveSkillLevel returns 1 for missing skill`() {
        val state = InventoryViewModel.UiState(
            skillLevels = emptyMap(),
            skillPrestige = emptyMap(),
        )
        assertEquals(1, state.effectiveSkillLevel("nonexistent"))
    }

    @Test
    fun `effectiveSkillLevel returns base level when prestige is 0`() {
        val state = InventoryViewModel.UiState(
            skillLevels = mapOf("strength" to 50),
            skillPrestige = mapOf("strength" to 0),
        )
        assertEquals(50, state.effectiveSkillLevel("strength"))
    }

    // ------------------------------------------------------------------
    // Prestige > 0 — effective level is 99 (max)
    // ------------------------------------------------------------------

    @Test
    fun `effectiveSkillLevel returns 99 for prestige 1 with base level 1`() {
        val state = InventoryViewModel.UiState(
            skillLevels = mapOf("strength" to 1),
            skillPrestige = mapOf("strength" to 1),
        )
        assertEquals(99, state.effectiveSkillLevel("strength"))
    }

    @Test
    fun `effectiveSkillLevel returns 99 for prestige 1 with high base level`() {
        val state = InventoryViewModel.UiState(
            skillLevels = mapOf("strength" to 90),
            skillPrestige = mapOf("strength" to 1),
        )
        assertEquals(99, state.effectiveSkillLevel("strength"))
    }

    @Test
    fun `effectiveSkillLevel returns 99 for prestige 2`() {
        val state = InventoryViewModel.UiState(
            skillLevels = mapOf("strength" to 1),
            skillPrestige = mapOf("strength" to 2),
        )
        assertEquals(99, state.effectiveSkillLevel("strength"))
    }

    @Test
    fun `effectiveSkillLevel returns 99 for prestige 3 (max)`() {
        val state = InventoryViewModel.UiState(
            skillLevels = mapOf("strength" to 1),
            skillPrestige = mapOf("strength" to 3),
        )
        assertEquals(99, state.effectiveSkillLevel("strength"))
    }

    // ------------------------------------------------------------------
    // Mixed skills with different prestige levels
    // ------------------------------------------------------------------

    @Test
    fun `effectiveSkillLevel handles mixed prestige levels`() {
        val state = InventoryViewModel.UiState(
            skillLevels = mapOf(
                "strength" to 1,
                "attack" to 30,
                "magic" to 1,
            ),
            skillPrestige = mapOf(
                "strength" to 1,
                "attack" to 0,
                "magic" to 2,
            ),
        )
        assertEquals(99, state.effectiveSkillLevel("strength"))
        assertEquals(30, state.effectiveSkillLevel("attack"))
        assertEquals(99, state.effectiveSkillLevel("magic"))
    }

    // ------------------------------------------------------------------
    // Edge cases
    // ------------------------------------------------------------------

    @Test
    fun `effectiveSkillLevel handles missing prestige entry for skill`() {
        val state = InventoryViewModel.UiState(
            skillLevels = mapOf("strength" to 50),
            skillPrestige = mapOf("attack" to 1),
        )
        assertEquals(50, state.effectiveSkillLevel("strength"))
    }

    @Test
    fun `effectiveSkillLevel handles missing level entry for skill with prestige`() {
        val state = InventoryViewModel.UiState(
            skillLevels = mapOf("attack" to 30),
            skillPrestige = mapOf("strength" to 1),
        )
        assertEquals(99, state.effectiveSkillLevel("strength"))
        assertEquals(30, state.effectiveSkillLevel("attack"))
    }

    @Test
    fun `effectiveSkillLevel returns 1 when both level and prestige are missing`() {
        val state = InventoryViewModel.UiState(
            skillLevels = emptyMap(),
            skillPrestige = emptyMap(),
        )
        assertEquals(1, state.effectiveSkillLevel("unknown"))
    }

    // ------------------------------------------------------------------
    // Gathering/crafting skills
    // ------------------------------------------------------------------

    @Test
    fun `effectiveSkillLevel for gathering skills without prestige`() {
        val state = InventoryViewModel.UiState(
            skillLevels = mapOf("mining" to 60, "woodcutting" to 50),
            skillPrestige = emptyMap(),
        )
        assertEquals(60, state.effectiveSkillLevel("mining"))
        assertEquals(50, state.effectiveSkillLevel("woodcutting"))
    }

    @Test
    fun `effectiveSkillLevel for gathering skills with prestige`() {
        val state = InventoryViewModel.UiState(
            skillLevels = mapOf("mining" to 1),
            skillPrestige = mapOf("mining" to 1),
        )
        assertEquals(99, state.effectiveSkillLevel("mining"))
    }

    // ------------------------------------------------------------------
    // Gear equip requirement scenarios
    // ------------------------------------------------------------------

    @Test
    fun `prestige allows equipping Abyssal Whip after level reset`() {
        val state = InventoryViewModel.UiState(
            skillLevels = mapOf("strength" to 1),
            skillPrestige = mapOf("strength" to 1),
        )
        assertEquals(99, state.effectiveSkillLevel("strength"))
    }

    @Test
    fun `prestige allows equipping any gear regardless of base level`() {
        val state = InventoryViewModel.UiState(
            skillLevels = mapOf("strength" to 1),
            skillPrestige = mapOf("strength" to 1),
        )
        assertEquals(99, state.effectiveSkillLevel("strength"))
    }

    @Test
    fun `high prestige allows equipping highest-level gear`() {
        val state = InventoryViewModel.UiState(
            skillLevels = mapOf("strength" to 1),
            skillPrestige = mapOf("strength" to 3),
        )
        assertEquals(99, state.effectiveSkillLevel("strength"))
    }

    @Test
    fun `non-prestiged skill still uses base level`() {
        val state = InventoryViewModel.UiState(
            skillLevels = mapOf("strength" to 45),
            skillPrestige = emptyMap(),
        )
        assertEquals(45, state.effectiveSkillLevel("strength"))
    }
}
