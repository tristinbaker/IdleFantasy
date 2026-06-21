package com.fantasyidler.simulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the deterministic (RNG-free) preview helpers of [SkillSimulator]:
 * `estimateGatheringXp`, `estimateAgilityXp`, and `sessionDurationMs`. The
 * RNG-driven `simulate*` functions are covered separately once a seedable
 * Random seam is introduced.
 */
class SkillSimulatorPureTest {

    @Test
    fun `estimateGatheringXp multiplies per-action XP by efficiency over 60 frames`() {
        assertEquals(600L, SkillSimulator.estimateGatheringXp(10, 1f))
        assertEquals(420L, SkillSimulator.estimateGatheringXp(7, 1f))
        assertEquals(900L, SkillSimulator.estimateGatheringXp(10, 1.5f))
        assertEquals(0L, SkillSimulator.estimateGatheringXp(0, 1f))
    }

    @Test
    fun `estimateGatheringQty estimates correct quantity with efficiency`() {
        assertEquals(60, SkillSimulator.estimateGatheringQty(1.0f))
        assertEquals(75, SkillSimulator.estimateGatheringQty(1.25f))
        assertEquals(90, SkillSimulator.estimateGatheringQty(1.5f))
        assertEquals(60, SkillSimulator.estimateGatheringQty(0.5f))
    }


    @Test
    fun `estimateAgilityXp grows with level then caps at the 0_95 success rate`() {
        // At the minimum level the success rate is the base 0.80.
        assertEquals(960L, SkillSimulator.estimateAgilityXp(10, 1, 1))
        // Far above the requirement the rate is clamped to 0.95.
        assertEquals(1140L, SkillSimulator.estimateAgilityXp(10, 1, 99))
        // The capped value should equal any level past the cap threshold.
        assertEquals(
            SkillSimulator.estimateAgilityXp(10, 1, 99),
            SkillSimulator.estimateAgilityXp(10, 1, 50),
        )
    }

    @Test
    fun `estimateAgilityXp is monotonically non-decreasing in current level`() {
        var previous = Long.MIN_VALUE
        for (level in 1..99) {
            val xp = SkillSimulator.estimateAgilityXp(10, 1, level)
            assertTrue("XP dropped at level $level", xp >= previous)
            previous = xp
        }
    }

    @Test
    fun `sessionDurationMs scales linearly from 60 to 40 minutes`() {
        assertEquals(60 * 60_000L, SkillSimulator.sessionDurationMs(1))
        assertEquals(55 * 60_000L, SkillSimulator.sessionDurationMs(25))
        assertEquals(50 * 60_000L, SkillSimulator.sessionDurationMs(50))
        assertEquals(45 * 60_000L, SkillSimulator.sessionDurationMs(75))
        assertEquals(40 * 60_000L, SkillSimulator.sessionDurationMs(99))
    }

    @Test
    fun `sessionDurationMs clamps out-of-range levels and never increases with level`() {
        assertEquals(SkillSimulator.sessionDurationMs(1), SkillSimulator.sessionDurationMs(0))
        assertEquals(SkillSimulator.sessionDurationMs(99), SkillSimulator.sessionDurationMs(200))
        var previous = Long.MAX_VALUE
        for (level in 1..99) {
            val ms = SkillSimulator.sessionDurationMs(level)
            assertTrue("duration increased at level $level", ms <= previous)
            previous = ms
        }
    }
}
