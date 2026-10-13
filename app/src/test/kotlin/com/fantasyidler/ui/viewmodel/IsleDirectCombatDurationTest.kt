package com.fantasyidler.ui.viewmodel

import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.repository.queuedQueueEntryDurationMs
import com.fantasyidler.simulator.SkillSimulator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Issue #2066: Elder Isle combat started with an empty queue (direct path in
 * CombatViewModel) uses mainland agility / prestige floor / Chronos Spire for
 * its duration. The queued path (QueuedSessionStarter) correctly uses elder
 * Agility via elderSessionDurationMs.
 *
 * Direct dungeon/boss duration on the isle must equal elderSessionDurationMs
 * and ignore mainland bonuses. Mainland keeps sessionDurationMs.
 */
class IsleDirectCombatDurationTest {

    @Test
    fun `isle direct dungeon ignores mainland floor and chronos`() {
        val got = directDungeonDurationMs(
            isElder = true,
            elderAgility = 1,
            mainlandAgility = 99,
            floorReductionMin = 10.0,
            chronosMultiplier = 0.5f,
        )
        assertEquals(SkillSimulator.elderSessionDurationMs(1), got)
    }

    @Test
    fun `mainland direct dungeon keeps mainland calc`() {
        val got = directDungeonDurationMs(
            isElder = false,
            elderAgility = 1,
            mainlandAgility = 99,
            floorReductionMin = 10.0,
            chronosMultiplier = 0.5f,
        )
        assertEquals(SkillSimulator.sessionDurationMs(99, 10.0, 0.5f), got)
    }

    @Test
    fun `isle direct boss frame ignores mainland bonuses`() {
        val got = directBossFrameMs(
            isElder = true,
            elderAgility = 1,
            mainlandAgility = 99,
            floorReductionMin = 10.0,
            chronosMultiplier = 0.5f,
        )
        assertEquals(SkillSimulator.elderSessionDurationMs(1) / 60L, got)
    }

    @Test
    fun `mainland direct boss frame keeps mainland calc`() {
        val got = directBossFrameMs(
            isElder = false,
            elderAgility = 1,
            mainlandAgility = 99,
            floorReductionMin = 10.0,
            chronosMultiplier = 0.5f,
        )
        assertEquals(SkillSimulator.sessionDurationMs(99, 10.0, 0.5f) / 60L, got)
    }

    // Repeat snapshots (CombatViewModel dungeon/boss direct, repeatCount > 1) run
    // fights 2..N via startQueuedAction, which keys off action.isElderSession.
    // An unstamped snapshot (default false) would bill isle repeats at the
    // mainland base — the same root cause as #2066. These pin the requirement.
    @Test
    fun `isle repeat combat snapshot uses isle base`() {
        val mainlandBase = SkillSimulator.sessionDurationMs(99, 10.0, 0.5f)
        val isleBase = SkillSimulator.elderSessionDurationMs(1)
        val repeat = QueuedAction(
            skillName = "combat",
            activityKey = "k",
            skillDisplayName = "d",
            repeatCount = 3,
            isElderSession = true,
        )
        assertEquals(
            isleBase * 3,
            queuedQueueEntryDurationMs(repeat, mainlandBase, isleBase),
        )
    }

    @Test
    fun `unstamped repeat combat snapshot falls back to mainland base`() {
        val mainlandBase = SkillSimulator.sessionDurationMs(99, 10.0, 0.5f)
        val isleBase = SkillSimulator.elderSessionDurationMs(1)
        val repeat = QueuedAction(
            skillName = "combat",
            activityKey = "k",
            skillDisplayName = "d",
            repeatCount = 3,
            isElderSession = false,
        )
        assertEquals(
            mainlandBase * 3,
            queuedQueueEntryDurationMs(repeat, mainlandBase, isleBase),
        )
    }
}
