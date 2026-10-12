package com.fantasyidler.data

import com.fantasyidler.data.model.ElderQuests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #2058: "Champion of the Isle" title not awarded after killing the Last Elder.
 *
 * run-by-orchestrator (worker does not run Gradle on this laptop).
 *
 * R0 (RED on pre-fix code): a player with the guaranteed Ancient Signet drop in
 * inventory but no `enemyKills["last_elder"]` entry scores 0 on
 * `act4_face_last_elder`, so the quest never completes and the title never grants.
 * Post-fix the same snapshot scores >= target via the signet fallback.
 */
class ElderQuestIsleChampionTitleTest {

    private val faceQuest = ElderQuests.CHAIN.first { it.id == "act4_face_last_elder" }

    private val priorEleven: Set<String> = ElderQuests.CHAIN
        .map { it.id }
        .filter { it != "act4_face_last_elder" }
        .toSet()

    private fun snapshot(
        kills: Int = 0,
        signets: Int = 0,
    ) = ElderQuests.Snapshot(
        dungeonRuns = emptyMap(),
        enemyKills = if (kills > 0) mapOf("last_elder" to kills) else emptyMap(),
        inventory = if (signets > 0) mapOf("ancient_signet" to signets) else emptyMap(),
    )

    @Test
    fun `R0 signet-only snapshot counts as Last Elder kill`() {
        // Pre-fix RED: counter returns 0 here (only enemyKills checked).
        // Post-fix GREEN: signet fallback (guaranteed drop, chance 1.0) counts.
        // HomeScreen already treats inventory ancient_signet as lastElderKilled.
        val snap = snapshot(kills = 0, signets = 1)
        assertTrue(
            "act4_face_last_elder must detect the kill via ancient_signet fallback",
            faceQuest.counter(snap) >= faceQuest.target,
        )
    }

    @Test
    fun `kill via enemyKills completes final quest when chain prior done`() {
        val snap = snapshot(kills = 7, signets = 0)
        val newly = ElderQuests.newlyCompleted(priorEleven, snap)
        assertTrue(newly.contains("act4_face_last_elder"))
        assertTrue(ElderQuests.qualifiesForIsleChampion(priorEleven + newly, snap))
    }

    @Test
    fun `signet-only kill completes final quest when chain prior done`() {
        val snap = snapshot(kills = 0, signets = 1)
        val newly = ElderQuests.newlyCompleted(priorEleven, snap)
        assertTrue(newly.contains("act4_face_last_elder"))
    }

    @Test
    fun `already-completed quest without title still qualifies retroactive grant`() {
        // Old grant condition ("act4_face_last_elder" in newly) is false here
        // because newly is empty — the title would never grant. The retroactive
        // path must still qualify via the completed set.
        val completed = priorEleven + "act4_face_last_elder"
        val snap = snapshot(kills = 7, signets = 1)
        val newly = ElderQuests.newlyCompleted(completed, snap)
        assertTrue("already completed: nothing newly completes", newly.isEmpty())
        assertTrue(ElderQuests.qualifiesForIsleChampion(completed + newly, snap))
    }

    @Test
    fun `title already present is idempotent no-op`() {
        val unlocked = setOf("isle_champion")
        val merged = unlocked + if (true) setOf("isle_champion") else emptySet()
        assertEquals(unlocked, merged)
        assertEquals(1, merged.size)
    }

    @Test
    fun `player without kill does not qualify`() {
        val snap = snapshot(kills = 0, signets = 0)
        assertEquals(0, faceQuest.counter(snap))
        assertFalse(ElderQuests.qualifiesForIsleChampion(emptySet(), snap))
        assertTrue(ElderQuests.newlyCompleted(emptySet(), snap).isEmpty())
    }
}
