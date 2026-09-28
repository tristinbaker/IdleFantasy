package com.fantasyidler.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Replication for #1941: the Collect dialog labels Pet coin bonuses as
 * blessings, and skill rows show no blessing context at all.
 *
 * `blessingCoinMult` folds the Church blessing AND the Golden Goose pet into
 * one multiplier, so `coinBlessingBonus` (rendered as "+X from blessing")
 * includes pet money. Skill rows only render tool/pet lines.
 */
class BonusAttributionTest {

    @Test
    fun `pet-only bonus is not attributed to blessing`() {
        // No blessing (1.0x) + Golden Goose pet (+10%): the blessing portion
        // must be 0 and the pet portion reported separately.
        val (blessing, pet) = splitCoinBonus(combined = 1000L, blessingMult = 1.0f, petMult = 1.1f)
        assertEquals("blessing portion with no blessing active", 0L, blessing)
        assertEquals("pet portion", 100L, pet)
    }

    @Test
    fun `blessing-only bonus carries no pet portion`() {
        // Split contract: with no pet, the entire bonus is the blessing's.
        val (blessing, pet) = splitCoinBonus(combined = 1000L, blessingMult = 1.1f, petMult = 1.0f)
        assertEquals(100L, blessing)
        assertEquals(0L, pet)
    }

    @Test
    fun `split preserves today's displayed total`() {
        // Totals must stay byte-identical for every combination.
        val cases = listOf(
            Triple(1000L, 1.0f, 1.0f),
            Triple(1000L, 1.0f, 1.1f),
            Triple(1000L, 1.5f, 1.0f),
            Triple(1000L, 1.5f, 1.25f),
            Triple(999L, 1.37f, 1.1f),
            Triple(0L, 1.5f, 1.1f),
        )
        for ((combined, blessing, pet) in cases) {
            val legacyTotal = (combined.toDouble() * (blessing * pet)).toLong()
            val (b, p) = splitCoinBonus(combined = combined, blessingMult = blessing, petMult = pet)
            assertEquals("total $combined $blessing $pet", legacyTotal, combined + b + p)
        }
    }

    @Test
    fun `isle and ironman run predictions at base blessing percent`() {
        assertEquals(0, blessingXpPercent(isIronman = false, isIsle = true, churchMult = 1.5f))
        assertEquals(0, blessingXpPercent(isIronman = true, isIsle = false, churchMult = 1.5f))
        assertEquals(50, blessingXpPercent(isIronman = false, isIsle = false, churchMult = 1.5f))
        // Float 1.05f truncates to 4 without rounding; display must read 5%.
        assertEquals(5, blessingXpPercent(isIronman = false, isIsle = false, churchMult = 1.05f))
    }
}
