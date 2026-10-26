package com.fantasyidler.simulator

import com.fantasyidler.data.json.BossCombatStats
import com.fantasyidler.data.json.BossCommonLoot
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.BossDefensiveStats
import com.fantasyidler.data.json.BossLootRange
import com.fantasyidler.data.json.BossPet
import com.fantasyidler.data.json.BossRareDrop
import kotlin.random.Random
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Boss death frames must mark died=true, like simulateDungeon already does
 * (died = diedThisMinute). Readers (death badges, run history, survival
 * stats) depend on the flag; without it a boss death is indistinguishable
 * from a plain loss.
 */
class BossDeathDiedFlagTest {

    private fun hollowKing() = BossData(
        id = "the_hollow_king",
        displayName = "The Hollow King",
        emoji = "🎃",
        description = "test stand-in with production stats",
        combatLevelRequired = 70,
        durationMinutes = 55,
        hp = 2600,
        combatStats = BossCombatStats(
            attackLevel = 66,
            strengthLevel = 62,
            defenseLevel = 64,
            attackBonus = 50,
            strengthBonus = 85,
        ),
        defensiveStats = BossDefensiveStats(
            attackDefense = 85,
            strengthDefense = 85,
            rangedDefense = 50,
            magicDefense = 110,
        ),
        xpRewards = mapOf("attack" to 1600, "hitpoints" to 1250),
        commonLoot = BossCommonLoot(
            coinsMin = 20000,
            coinsMax = 40000,
            items = mapOf("death_rune" to BossLootRange(120, 240)),
        ),
        rareDrops = listOf(BossRareDrop("race_change_token", 0.004)),
        pet = BossPet("lantern_wisp", "Lantern Wisp", "🎃", 0.01),
    )

    @Test
    fun `death marks the died flag`() {
        for (seed in 1L..50L) {
            val frames = CombatSimulator.simulateBoss(
                boss = hollowKing(),
                bossKey = "the_hollow_king",
                playerAttack = 1,
                playerStrength = 1,
                playerDefence = 1,
                playerHp = 30,
                weaponAttackBonus = 0,
                weaponStrBonus = 0,
                combatStyle = "melee",
                equippedFood = emptyMap(),
                foodHealValues = mapOf("shark" to 20),
                random = Random(seed),
            )
            val last = frames.last()
            if (last.kills == 0 && last.killsByEnemy.isEmpty()) {
                assertTrue(
                    "death frame must set died=true (kills=${last.kills} hpAfter=${last.hpAfter})",
                    frames.any { it.died },
                )
                return
            }
        }
        throw AssertionError("no loss found in 50 seeds to check the died flag")
    }
}
