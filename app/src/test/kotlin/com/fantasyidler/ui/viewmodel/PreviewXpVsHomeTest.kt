package com.fantasyidler.ui.viewmodel

import com.fantasyidler.repository.predictionXpMult
import com.fantasyidler.simulator.SkillSimulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Issue #1981: sheet preview XP vs Home card with prestige XP active.
 *
 * Scenario (from /tmp/repro_1981.py): coal 50 XP/action, tool efficiency 1.85,
 * purchased 2x boost, 1.15 church blessing, +5% prestige xp_pct.
 *
 * Mirrors (post-fix production logic):
 * - preview mult: SkillsUiState.xpMultBySkill via BoostRepository.xpMultiplier =
 *   2x * blessing * prestige (same chain as Home card and payout).
 * - queued estimates bake prestigeMult and store xpQueueMult * prestigeMult
 *   (thieving pattern), so the Home live rescale is identity.
 */
class PreviewXpVsHomeTest {

    private val xpPerOre = 50
    private val toolEff = 1.85f
    private val boost2x = 2.0
    private val blessing = 1.15
    private val prestigePct = 5 // boostRepo.prestigeXpPct(...) Int

    /** Sheet preview mult (per-skill full chain via `xpMultBySkill`). */
    private fun previewMult(): Double = boost2x * blessing * (1.0 + prestigePct / 100.0)

    /** Home card / payout mult (BoostRepository.xpMultiplier per skill). */
    private fun homeMult(): Double = boost2x * blessing * (1.0 + prestigePct / 100.0)

    @Test
    fun `gathering preview matches home payout with prestige xp active`() {
        val preview = SkillSimulator.estimateGatheringXp(xpPerOre, toolEff * previewMult().toFloat())
        val perMinBase = (xpPerOre * toolEff).toInt()
        val home = (perMinBase * 60L * homeMult()).toLong()
        assertEquals("preview mult must equal home mult", homeMult(), previewMult(), 0.0)
        assertTrue("preview $preview within 1% of home $home", abs(preview - home) <= home * 0.01)
    }

    @Test
    fun `queued estimate stores the full mult so Home rescale is identity`() {
        // SkillsViewModel queue estimate vs HomeViewModel live rescale denominator.
        val stored = predictionXpMult(false, false, boost2x * blessing) * (1.0 + prestigePct / 100.0)
        val liveFull = predictionXpMult(false, false, homeMult())
        assertEquals("stored mult must equal live full mult", liveFull, stored, 0.0)
    }

    @Test
    fun `isle and ironman predictions stay at base rates`() {
        assertEquals(1.0, predictionXpMult(false, true, homeMult()), 0.0)
        assertEquals(1.0, predictionXpMult(true, false, homeMult()), 0.0)
        assertEquals(homeMult(), predictionXpMult(false, false, homeMult()), 0.0)
    }
}
