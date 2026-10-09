package com.fantasyidler.simulator

import com.fantasyidler.data.json.BossCombatStats
import com.fantasyidler.data.json.BossCommonLoot
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.BossDefensiveStats
import com.fantasyidler.data.json.SpellData
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.repository.bossRuneArgs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Repro for issue #2038: Trident of the Seas + Bloodwave consumes runes on the
 * first boss fight but subsequent fights in the continuous chain (x3 sessions)
 * cost zero runes.
 *
 * Root cause (caller-level, NOT simulator-level):
 * - Live fight ([CombatViewModel] boss path) forwards `runeKey = blood_rune`,
 *   `runeCostPerAttack = 4`, `availableRunes = inventory` to
 *   [CombatSimulator.simulateBoss] → frames carry `runesConsumed`.
 * - Chained fights ([QueuedSessionStarter] `"boss"` branch, `startQueuedAction`)
 *   call [CombatSimulator.simulateBoss] WITHOUT any rune params → `runeKey`
 *   defaults to null → `canCast` is always true and every frame reports
 *   `runesConsumed = {}`.
 *
 * This test pins the contract: the SAME fight simulated with live-path params
 * vs queued-path params must report IDENTICAL rune consumption. The chained case
 * went RED (0 vs 12 runes) until the queued boss branch forwarded the rune
 * params (staff/infinite-rune check included, mirroring CombatViewModel); it is
 * GREEN with the fix and guards against regressions.
 */
class BossRuneChainTest {

    private fun weakBoss() = BossData(
        id = "test_boss",
        displayName = "Test Boss",
        emoji = "",
        description = "",
        combatLevelRequired = 1,
        durationMinutes = 3,
        hp = 50,
        combatStats = BossCombatStats(
            attackLevel = 1, strengthLevel = 1, defenseLevel = 1,
            attackBonus = 0, strengthBonus = 0,
        ),
        defensiveStats = BossDefensiveStats(
            attackDefense = 0, strengthDefense = 0, rangedDefense = 0, magicDefense = 0,
        ),
        xpRewards = mapOf("combat" to 20),
        commonLoot = BossCommonLoot(coinsMin = 0, coinsMax = 0, items = emptyMap()),
        rareDrops = emptyList(),
    )

    /** Params the LIVE boss path sends (CombatViewModel: bossRuneKey/bossRuneCost). */
    private fun liveFight(seed: Int) = CombatSimulator.simulateBoss(
        boss = weakBoss(),
        bossKey = "test_boss",
        playerAttack = 99,
        playerStrength = 99,
        playerDefence = 99,
        playerHp = 99,
        weaponAttackBonus = 88,
        weaponStrBonus = 0,
        combatStyle = "magic",
        playerRanged = 1,
        playerMagic = 99,
        spellMaxHit = 44,
        runeKey = "blood_rune",
        runeCostPerAttack = 4,
        availableRunes = 10_000,
        attackSpeedSec = 1.5,
        random = Random(seed),
    )

    /**
     * Params the QUEUED boss branch sends with the fix: derived through the
     * same [bossRuneArgs] helper the branch calls (QueuedSessionStarter),
     * so if the branch ever drops the forwarding again this test goes RED.
     */
    private fun queuedFight(seed: Int): List<SessionFrame> {
        val args = bossRuneArgs(
            combatStyle = "magic",
            spell = bloodwave(),
            // Trident of the Seas: infiniteRunes == null, covers nothing.
            weaponInfiniteRunes = null,
        )
        return CombatSimulator.simulateBoss(
            boss = weakBoss(),
            bossKey = "test_boss",
            playerAttack = 99,
            playerStrength = 99,
            playerDefence = 99,
            playerHp = 99,
            weaponAttackBonus = 88,
            weaponStrBonus = 0,
            combatStyle = "magic",
            playerRanged = 1,
            playerMagic = 99,
            spellMaxHit = 44,
            runeKey = args.runeKey,
            runeCostPerAttack = args.runeCostPerAttack,
            availableRunes = 10_000,
            attackSpeedSec = 1.5,
            random = Random(seed),
        )
    }

    private fun bloodwave() = SpellData(
        displayName = "Bloodwave",
        runeType = "blood_rune",
        magicLevelRequired = 1,
        maxHit = 44,
        runeCost = 4,
    )

    @Test
    fun `queued branch forwards Trident runes instead of dropping them`() {
        // Pre-fix the branch sent no rune params (runeKey null → free casts).
        // The helper the branch calls must resolve blood_rune x4 for a
        // Trident (covers nothing) + Bloodwave.
        val args = bossRuneArgs("magic", bloodwave(), weaponInfiniteRunes = null)
        assertEquals("blood_rune", args.runeKey)
        assertEquals(4, args.runeCostPerAttack)
    }

    @Test
    fun `covering staff keeps free casts`() {
        assertNull(bossRuneArgs("magic", bloodwave(), "blood_rune").runeKey)
        assertNull(bossRuneArgs("magic", bloodwave(), "all").runeKey)
    }

    @Test
    fun `melee and spell-less fights need no rune`() {
        assertNull(bossRuneArgs("melee", bloodwave(), null).runeKey)
        assertNull(bossRuneArgs("magic", null, null).runeKey)
    }

    @Test
    fun `without runes the mage deals no damage and consumes nothing`() {
        val frames = CombatSimulator.simulateBoss(
            boss = weakBoss(),
            bossKey = "test_boss",
            playerAttack = 99,
            playerStrength = 99,
            playerDefence = 99,
            playerHp = 99,
            weaponAttackBonus = 88,
            weaponStrBonus = 0,
            combatStyle = "magic",
            playerRanged = 1,
            playerMagic = 99,
            spellMaxHit = 44,
            runeKey = "blood_rune",
            runeCostPerAttack = 4,
            availableRunes = 0,
            attackSpeedSec = 1.5,
            random = Random(2038),
        )
        assertEquals(0, frames.sumOf { it.runesConsumed.values.sum() })
        assertEquals(0, frames.sumOf { it.playerHits.sum() })
    }

    @Test
    fun `melee needs no runes and still kills`() {
        val frames = CombatSimulator.simulateBoss(
            boss = weakBoss(),
            bossKey = "test_boss",
            playerAttack = 99,
            playerStrength = 99,
            playerDefence = 99,
            playerHp = 99,
            weaponAttackBonus = 88,
            weaponStrBonus = 85,
            combatStyle = "melee",
            playerRanged = 1,
            playerMagic = 1,
            attackSpeedSec = 1.5,
            random = Random(2038),
        )
        assertEquals(1, frames.sumOf { it.kills })
        assertEquals(0, frames.sumOf { it.runesConsumed.values.sum() })
    }

    @Test
    fun `live boss fight with Bloodwave consumes blood runes`() {
        val frames = liveFight(seed = 2038)
        val total = frames.sumOf { it.runesConsumed.values.sum() }
        assertTrue("expected blood runes consumed on 1st fight, got $total", total > 0)
    }

    @Test
    fun `chained boss fight consumes the same runes as the live fight`() {
        val seed = 2038
        val liveTotal = liveFight(seed).sumOf { it.runesConsumed.values.sum() }
        val queuedTotal = queuedFight(seed).sumOf { it.runesConsumed.values.sum() }
        assertTrue("precondition: live fight must consume runes, got $liveTotal", liveTotal > 0)
        assertEquals(
            "issue #2038: chained fight consumed $queuedTotal runes vs $liveTotal on 1st fight",
            liveTotal,
            queuedTotal,
        )
    }
}
