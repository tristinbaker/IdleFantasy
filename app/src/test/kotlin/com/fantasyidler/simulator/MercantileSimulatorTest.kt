package com.fantasyidler.simulator

import com.fantasyidler.data.json.CoinRange
import com.fantasyidler.data.json.TradeRouteData
import com.fantasyidler.data.json.XpRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Outcome tests for [MercantileSimulator] using the injected `random` seam. */
class MercantileSimulatorTest {

    private fun route(xp: XpRange, coins: CoinRange) = TradeRouteData(
        id = "silk", displayName = "Silk Road", description = "", levelRequired = 1, coinCost = 0,
        xpRanges = mapOf("1" to xp),
        coinRanges = mapOf("1" to coins),
    )

    @Test
    fun `fixed XP and coin ranges yield exact totals over 60 frames`() {
        val r = MercantileSimulator.simulate(route(XpRange(4, 4), CoinRange(20, 20)), startXp = 0, random = Random(11))
        assertEquals(60, r.frames.size)
        assertTrue(r.frames.all { it.xpGain == 4 })
        assertEquals(240L, r.frames.sumOf { it.xpGain.toLong() })
        assertTrue(r.frames.all { (it.items["_coins"] ?: 0) == 20 })
    }

    @Test
    fun `wide ranges stay within bounds and seeded runs reproduce`() {
        val cfg = route(XpRange(2, 9), CoinRange(5, 50))
        val a = MercantileSimulator.simulate(cfg, startXp = 0, random = Random(123))
        val b = MercantileSimulator.simulate(cfg, startXp = 0, random = Random(123))
        assertEquals(a.frames, b.frames)
        assertTrue(a.frames.all { it.xpGain in 2..9 })
        assertTrue(a.frames.all { (it.items["_coins"] ?: 0) in 5..50 })
    }
}
