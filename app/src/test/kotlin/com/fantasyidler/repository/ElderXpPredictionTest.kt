package com.fantasyidler.repository

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Replication for #1930: on the Elder Isle with a Church XP buff, XP
 * predictions are inflated by mainland boosts the payout ignores.
 *
 * The guarded paths (`SkillsViewModel:246/:973`) force 1.0 on isle; the
 * firemaking/runecrafting/prayer/thieving enqueue estimates and the Home
 * previews/rescale/repeat do not — they must all resolve through
 * [predictionXpMult].
 */
class ElderXpPredictionTest {

    // Church blessing x1.5 stacked on the purchased 2x boost.
    private val mainlandMult = 3.0

    @Test
    fun `isle prediction runs at base rate`() {
        // Buggy resolution returns the inflated mainland chain.
        assertEquals(1.0, predictionXpMult(isIronman = false, isIsle = true, mainlandMult = mainlandMult), 0.0)
    }

    @Test
    fun `mainland prediction keeps mainland boosts`() {
        assertEquals(3.0, predictionXpMult(isIronman = false, isIsle = false, mainlandMult = mainlandMult), 0.0)
    }

    @Test
    fun `ironman prediction runs at base rate`() {
        assertEquals(1.0, predictionXpMult(isIronman = true, isIsle = false, mainlandMult = mainlandMult), 0.0)
    }

    @Test
    fun `ironman on isle runs at base rate`() {
        assertEquals(1.0, predictionXpMult(isIronman = true, isIsle = true, mainlandMult = mainlandMult), 0.0)
    }
}
