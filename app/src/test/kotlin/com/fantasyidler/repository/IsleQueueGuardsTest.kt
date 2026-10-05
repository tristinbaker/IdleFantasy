package com.fantasyidler.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Problema 2: guards de isle incompletos en cola.
 * Con isElder=true + boosts mainland, minería/madera/pesca encoladas deben
 * resolver en base (pet 0, tool 1.0, gem 1.0, petDrop null/0.0) como el
 * arranque directo y la agilidad encolada — no con multis mainland.
 */
class IsleQueueGuardsTest {

    @Test
    fun `isle neutralises queued gathering boosts`() {
        val got = queuedGatheringBoosts(
            isElder = true,
            petBoostPct = 50,
            toolEfficiency = 1.5f,
            gemChanceMult = 2.0,
            petDropKey = "baby_dragon",
            petDropChance = 0.5,
        )

        assertEquals(0, got.petBoostPct)
        assertEquals(1.0f, got.toolEfficiency)
        assertEquals(1.0, got.gemChanceMult, 0.0)
        assertNull(got.petDropKey)
        assertEquals(0.0, got.petDropChance, 0.0)
    }

    @Test
    fun `mainland keeps queued gathering boosts`() {
        val got = queuedGatheringBoosts(
            isElder = false,
            petBoostPct = 50,
            toolEfficiency = 1.5f,
            gemChanceMult = 2.0,
            petDropKey = "baby_dragon",
            petDropChance = 0.5,
        )

        assertEquals(50, got.petBoostPct)
        assertEquals(1.5f, got.toolEfficiency)
        assertEquals(2.0, got.gemChanceMult, 0.0)
        assertEquals("baby_dragon", got.petDropKey)
        assertEquals(0.5, got.petDropChance, 0.0)
    }
}
