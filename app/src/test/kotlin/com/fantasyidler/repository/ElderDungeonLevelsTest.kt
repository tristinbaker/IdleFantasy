package com.fantasyidler.repository

import com.fantasyidler.data.model.Skills
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Replication for #1928: after dying on the Elder Isle, the next queued
 * dungeon fight runs on mainland combat stats instead of isle stats.
 *
 * The boss branch maps to isle levels when `isElder` (v1.15.5 fix); the
 * dungeon branch passes mainland `levels` straight into `simulateDungeon`.
 */
class ElderDungeonLevelsTest {

    private val mainland = mapOf(
        Skills.ATTACK to 99,
        Skills.STRENGTH to 99,
        Skills.DEFENSE to 99,
        Skills.HITPOINTS to 99,
        Skills.RANGED to 99,
        Skills.MAGIC to 99,
    )
    private val isle = mapOf(
        Skills.ATTACK to 1,
        Skills.STRENGTH to 2,
        Skills.DEFENSE to 3,
        Skills.HITPOINTS to 10,
        Skills.RANGED to 1,
        Skills.MAGIC to 1,
    )

    @Test
    fun `isle dungeon runs on isle levels`() {
        val resolved = combatLevelsFor(isElder = true, levels = mainland, elderLevels = isle)
        // Buggy resolution keeps mainland 99s.
        assertEquals(1, resolved[Skills.ATTACK])
        assertEquals(2, resolved[Skills.STRENGTH])
        assertEquals(3, resolved[Skills.DEFENSE])
        assertEquals(10, resolved[Skills.HITPOINTS])
        assertEquals(1, resolved[Skills.RANGED])
        assertEquals(1, resolved[Skills.MAGIC])
    }

    @Test
    fun `mainland dungeon keeps mainland levels`() {
        val resolved = combatLevelsFor(isElder = false, levels = mainland, elderLevels = isle)
        assertEquals(99, resolved[Skills.ATTACK])
        assertEquals(99, resolved[Skills.DEFENSE])
    }

    @Test
    fun `missing isle level falls back to 1`() {
        val resolved = combatLevelsFor(isElder = true, levels = mainland, elderLevels = emptyMap())
        assertEquals(1, resolved[Skills.ATTACK])
    }
}
