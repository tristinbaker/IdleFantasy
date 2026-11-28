package com.fantasyidler.simulator

import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyData
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * The gear-restricted hidden achievements must stay beatable as combat gets retuned. Runs the
 * real shipped data with a maxed (99s, no prestige, no potions) player and best food:
 * Bronze Age (Kur'zul in full bronze, no mercenaries) and Naked and Afraid (a level 60+
 * dungeon with nothing equipped).
 */
class HiddenAchievementFeasibilityTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun dataFile(name: String): File =
        listOf("src/main/assets/data/$name", "app/src/main/assets/data/$name")
            .map(::File).firstOrNull { it.exists() }
            ?: error("Could not locate asset $name from ${File(".").absolutePath}")

    private val bosses: Map<String, BossData> by lazy {
        json.decodeFromString(dataFile("raid_bosses.json").readText())
    }
    private val enemies: Map<String, EnemyData> by lazy {
        json.decodeFromString(dataFile("enemies.json").readText())
    }

    private val sharks = mapOf("cooked_shark" to 300)
    private val sharkHeal = mapOf("cooked_shark" to 20)

    /** Full bronze: helm 6 + platebody 12 + platelegs 10 + kiteshield 9 + boots 5 defense, battleaxe 8/8. */
    private fun bronzeKurzul(seed: Int) = CombatSimulator.simulateBoss(
        boss = bosses.getValue("kurzul"), bossKey = "kurzul",
        playerAttack = 99, playerStrength = 99, playerDefence = 99 + 42, playerHp = 99,
        weaponAttackBonus = 8, weaponStrBonus = 8, combatStyle = "strength",
        equippedFood = sharks, foodHealValues = sharkHeal,
        random = Random(seed),
    )

    private fun nakedDungeon(key: String, seed: Int) = CombatSimulator.simulateDungeon(
        dungeon = json.decodeFromString<DungeonData>(dataFile("dungeons/$key.json").readText()),
        enemies = enemies,
        playerAttack = 99, playerStrength = 99, playerDefence = 99, playerHp = 99,
        equippedFood = sharks, foodHealValues = sharkHeal,
        random = Random(seed),
    )

    @Test
    fun `Kur'zul can be beaten in full bronze without mercenaries`() {
        val wins = (1..40).count { seed -> bronzeKurzul(seed).any { it.kills > 0 || it.killsByEnemy.isNotEmpty() } }
        assertTrue("expected some bronze wins against Kur'zul, got $wins/40", wins > 0)
    }

    @Test
    fun `a level 60+ dungeon can be survived with nothing equipped`() {
        val survived = (1..40).count { seed -> nakedDungeon("ancient_temple", seed).frames.none { it.died } }
        assertTrue("expected some naked Ancient Temple survivals, got $survived/40", survived > 0)
    }
}
