package com.fantasyidler.repository

import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.Skills
import com.fantasyidler.simulator.SkillSimulator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Issue #2034: "Queue ends in" on Elder Isle must not use mainland agility /
 * Chronos Spire / tools. Per-task cards already show the isle-neutral
 * estimatedDurationMs; the total recomputed in HomeViewModel did not.
 *
 * Keyed on action.isElderSession (stamped at enqueue), NOT the live
 * flags.onElderIsle — same rule as queuedGatheringBoosts (#1993).
 */
class IsleQueueEndTimeTest {

    private val mainlandFastMs = SkillSimulator.sessionDurationMs(
        agilityLevel = 99, floorReductionMin = 10.0, chronosMultiplier = 0.5f,
    )
    private val isleSlowMs = SkillSimulator.elderSessionDurationMs(1)

    private fun action(
        skill: String,
        isElder: Boolean,
        qty: Int = 0,
        repeat: Int = 1,
        estimatedMs: Long = 0L,
    ) = QueuedAction(
        skillName = skill,
        activityKey = "k",
        skillDisplayName = "d",
        qty = qty,
        estimatedDurationMs = estimatedMs,
        repeatCount = repeat,
        isElderSession = isElder,
    )

    @Test
    fun `isle entry ignores mainland-shortened base`() {
        val got = queuedQueueEntryDurationMs(
            action = action(Skills.MINING, isElder = true),
            mainlandSessionMs = mainlandFastMs,
            isleSessionMs = isleSlowMs,
        )
        assertEquals(isleSlowMs, got)
    }

    @Test
    fun `mainland entry passes mainland base through`() {
        val got = queuedQueueEntryDurationMs(
            action = action(Skills.MINING, isElder = false),
            mainlandSessionMs = mainlandFastMs,
            isleSessionMs = isleSlowMs,
        )
        assertEquals(mainlandFastMs, got)
    }

    @Test
    fun `mixed queue sums per-entry bases`() {
        val isle = action(Skills.AGILITY, isElder = true)
        val main = action(Skills.AGILITY, isElder = false)
        val total = listOf(isle, main).sumOf {
            queuedQueueEntryDurationMs(it, mainlandFastMs, isleSlowMs)
        }
        assertEquals(isleSlowMs + mainlandFastMs, total)
    }

    @Test
    fun `isle qty entry neutralises tool efficiency`() {
        val qty = 10
        val gotIsle = queuedQueueEntryDurationMs(
            action = action(Skills.SMITHING, isElder = true, qty = qty),
            mainlandSessionMs = mainlandFastMs,
            isleSessionMs = isleSlowMs,
            mainlandCraftEff = 2.0f,
        )
        assertEquals(qty.toLong() * (isleSlowMs / 60), gotIsle)

        val gotMain = queuedQueueEntryDurationMs(
            action = action(Skills.SMITHING, isElder = false, qty = qty),
            mainlandSessionMs = mainlandFastMs,
            isleSessionMs = isleSlowMs,
            mainlandCraftEff = 2.0f,
        )
        assertEquals(qty.toLong() * ((mainlandFastMs / 60 / 2.0f).toLong()), gotMain)
    }

    @Test
    fun `boss uses stored estimate regardless of isle flag`() {
        val est = 7_000L
        val got = queuedQueueEntryDurationMs(
            action = action("boss", isElder = true, estimatedMs = est, repeat = 3),
            mainlandSessionMs = mainlandFastMs,
            isleSessionMs = isleSlowMs,
        )
        assertEquals(est * 3, got)
    }
}
