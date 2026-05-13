package com.fantasyidler.simulator

import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.Skills
import kotlin.math.max
import kotlin.random.Random

/**
 * Pre-simulates all 60 frames of a dungeon combat session.
 *
 * Uses a tick-by-tick simulation (25 ticks/frame at 2.4 s each). Each tick both
 * the player and enemy attack; food is eaten immediately after enemy damage if a
 * full-heal fits. Per-tick damage values are stored in [SessionFrame.playerHits]
 * and [SessionFrame.enemyHits] so the UI can animate live HP changes.
 */
object CombatSimulator {

    fun simulateDungeon(
        dungeon: DungeonData,
        enemies: Map<String, EnemyData>,
        playerAttack: Int,
        playerStrength: Int,
        playerDefence: Int,
        playerHp: Int = 10,
        weaponAttackBonus: Int = 0,
        weaponStrengthBonus: Int = 0,
        combatStyle: String = "melee",
        playerRanged: Int = 1,
        playerMagic: Int = 1,
        arrowStrengthBonus: Int = 0,
        spellMaxHit: Int = 0,
        agilityLevel: Int = 1,
        petBoostPct: Int = 0,
        equippedFood: Map<String, Int> = emptyMap(),
        foodHealValues: Map<String, Int> = emptyMap(),
    ): SkillSimulator.Result {
        val frames = mutableListOf<SessionFrame>()

        val spawnPool = dungeon.enemySpawns.flatMap { spawn ->
            List(spawn.weight) { spawn.enemy }
        }.ifEmpty { return SkillSimulator.Result(emptyList(), SkillSimulator.sessionDurationMs(agilityLevel)) }

        val maxHp = playerHp * 10
        var currentHp = maxHp

        val foodSupply = equippedFood.toMutableMap()
        val foodOrder: List<String> = foodHealValues.entries
            .filter { (k, _) -> k in foodSupply }
            .sortedByDescending { it.value }
            .map { it.key }

        var runningTotal = 0L

        for (minute in 1..60) {
            val frameItems     = mutableMapOf<String, Int>()
            val frameXpBySkill = mutableMapOf<String, Long>()
            var frameXp        = 0L
            val frameFood      = mutableMapOf<String, Int>()

            val enemyKey = spawnPool[Random.nextInt(spawnPool.size)]
            val enemy    = enemies[enemyKey] ?: continue

            // --- Player combat stats for this enemy ---
            val playerMaxHit: Int
            val playerEffAtk: Int
            val enemyDefStat: Int

            when (combatStyle) {
                "ranged" -> {
                    val effStr   = playerRanged + arrowStrengthBonus
                    playerMaxHit = max(1, 1 + effStr * (arrowStrengthBonus + 64) / 640)
                    playerEffAtk = playerRanged + weaponAttackBonus
                    enemyDefStat = enemy.defensiveStats.rangedDefense
                }
                "magic" -> {
                    playerMaxHit = spellMaxHit.coerceAtLeast(1)
                    playerEffAtk = playerMagic + weaponAttackBonus
                    enemyDefStat = enemy.defensiveStats.magicDefense
                }
                else -> {
                    val effStr   = playerStrength + weaponStrengthBonus
                    playerMaxHit = max(1, 1 + effStr * (weaponStrengthBonus + 64) / 640)
                    playerEffAtk = playerAttack + weaponAttackBonus
                    enemyDefStat = enemy.defensiveStats.attackDefense
                }
            }

            val playerHitChance = when {
                playerEffAtk > enemyDefStat ->
                    1.0 - enemyDefStat / (2.0 * playerEffAtk.coerceAtLeast(1))
                else ->
                    playerEffAtk / (2.0 * enemyDefStat.coerceAtLeast(1))
            }.coerceIn(0.15, 0.95)

            // --- Enemy combat stats ---
            val enemyEffStr    = enemy.combatStats.strengthLevel + enemy.combatStats.strengthBonus
            val enemyMaxHit    = max(1, 1 + enemyEffStr * (enemy.combatStats.strengthBonus + 64) / 640)
            val enemyEffAtk    = enemy.combatStats.attackLevel + enemy.combatStats.attackBonus
            val enemyHitChance = when {
                enemyEffAtk > playerDefence ->
                    1.0 - playerDefence / (2.0 * enemyEffAtk.coerceAtLeast(1))
                else ->
                    enemyEffAtk / (2.0 * playerDefence.coerceAtLeast(1))
            }.coerceIn(0.10, 0.95)

            // --- Tick-by-tick combat loop ---
            var enemyHp = enemy.hp
            var kills = 0
            val framePlayerHits = mutableListOf<Int>()
            val frameEnemyHits  = mutableListOf<Int>()

            repeat(TICKS_PER_FRAME) {
                // Player attacks
                val pDmg = if (Random.nextDouble() < playerHitChance) Random.nextInt(0, playerMaxHit + 1) else 0
                framePlayerHits += pDmg
                enemyHp -= pDmg
                if (enemyHp <= 0) {
                    kills++
                    for (drop in enemy.alwaysDrops) {
                        frameItems[drop.item] = (frameItems[drop.item] ?: 0) + drop.quantity
                    }
                    for (drop in enemy.dropTable) {
                        if (Random.nextDouble() < drop.chance) {
                            val qty = if (drop.quantityMin >= drop.quantityMax) drop.quantityMin
                                      else Random.nextInt(drop.quantityMin, drop.quantityMax + 1)
                            frameItems[drop.item] = (frameItems[drop.item] ?: 0) + qty
                        }
                    }
                    val baseXp = (enemy.xpDrops["combat"] ?: 0).toLong()
                    val xp     = if (petBoostPct > 0) (baseXp * (1.0 + petBoostPct / 100.0)).toLong() else baseXp
                    for ((skill, skillXp) in distributeXp(xp, combatStyle)) {
                        frameXpBySkill[skill] = (frameXpBySkill[skill] ?: 0L) + skillXp
                    }
                    frameXp += xp
                    enemyHp  = enemy.hp
                }

                // Enemy attacks
                val eDmg = if (Random.nextDouble() < enemyHitChance) Random.nextInt(0, enemyMaxHit + 1) else 0
                frameEnemyHits += eDmg
                currentHp      -= eDmg

                // Eat food immediately if a full-heal fits (no waste)
                var ate = true
                while (ate) {
                    ate = false
                    for (foodKey in foodOrder) {
                        val qty  = foodSupply[foodKey] ?: 0
                        if (qty <= 0) continue
                        val heal = foodHealValues[foodKey] ?: continue
                        if (currentHp + heal <= maxHp) {
                            currentHp          += heal
                            foodSupply[foodKey] = qty - 1
                            frameFood[foodKey]  = (frameFood[foodKey] ?: 0) + 1
                            ate = true
                            break
                        }
                    }
                }
            }

            val diedThisMinute = currentHp <= 0

            frames.add(
                SessionFrame(
                    minute       = minute,
                    xpGain       = frameXp.toInt(),
                    xpBefore     = runningTotal,
                    xpAfter      = runningTotal + frameXp,
                    levelBefore  = 0,
                    levelAfter   = 0,
                    items        = frameItems,
                    xpBySkill    = frameXpBySkill,
                    kills        = kills,
                    killsByEnemy = if (kills > 0) mapOf(enemyKey to kills) else emptyMap(),
                    died         = diedThisMinute,
                    foodConsumed = frameFood,
                    enemyKey     = enemyKey,
                    hpAfter      = currentHp.coerceAtLeast(0),
                    playerHits   = framePlayerHits,
                    enemyHits    = frameEnemyHits,
                )
            )
            runningTotal += frameXp

            if (diedThisMinute) break
        }

        val fullDurationMs = SkillSimulator.sessionDurationMs(agilityLevel)
        return SkillSimulator.Result(frames, fullDurationMs)
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private fun distributeXp(totalXp: Long, style: String): Map<String, Long> {
        val hp   = (totalXp * 0.15).toLong()
        val def  = (totalXp * 0.15).toLong()
        val main = totalXp - hp - def
        val mainSkill = when (style) {
            "strength" -> Skills.STRENGTH
            "ranged"   -> Skills.RANGED
            "magic"    -> Skills.MAGIC
            else       -> Skills.ATTACK
        }
        return mapOf(
            mainSkill        to main,
            Skills.HITPOINTS to hp,
            Skills.DEFENSE   to def,
        )
    }

    /** Ticks per 60-second frame (one attack every 2.4 s). */
    const val TICKS_PER_FRAME = 25

    /** Weapon attack speed in seconds. */
    private const val ATTACK_SPEED_SEC = 2.4

    enum class SurvivalRating { LIKELY, RISKY, UNLIKELY }

    fun estimateSurvival(
        dungeon: DungeonData,
        enemies: Map<String, EnemyData>,
        playerDefence: Int,
        playerHp: Int,
        totalFoodHeal: Int,
    ): SurvivalRating {
        if (dungeon.enemySpawns.isEmpty()) return SurvivalRating.LIKELY
        val playerHpPool = (playerHp * 10) + totalFoodHeal
        val totalWeight  = dungeon.enemySpawns.sumOf { it.weight }.coerceAtLeast(1)
        var weightedDPM  = 0.0

        for (spawn in dungeon.enemySpawns) {
            val enemy = enemies[spawn.enemy] ?: continue
            val weight      = spawn.weight.toDouble() / totalWeight
            val enemyEffStr = enemy.combatStats.strengthLevel + enemy.combatStats.strengthBonus
            val enemyMaxHit = max(1, 1 + enemyEffStr * (enemy.combatStats.strengthBonus + 64) / 640)
            val enemyEffAtk = enemy.combatStats.attackLevel + enemy.combatStats.attackBonus
            val enemyHit    = when {
                enemyEffAtk > playerDefence -> 1.0 - playerDefence / (2.0 * enemyEffAtk.coerceAtLeast(1))
                else                        -> enemyEffAtk / (2.0 * playerDefence.coerceAtLeast(1))
            }.coerceIn(0.10, 0.95)
            weightedDPM += weight * ((enemyMaxHit / 2.0) * enemyHit / ATTACK_SPEED_SEC * 60.0)
        }

        val totalDamage   = weightedDPM * 60.0
        val survivalRatio = playerHpPool.toDouble() / totalDamage.coerceAtLeast(1.0)
        return when {
            survivalRatio >= 1.2 -> SurvivalRating.LIKELY
            survivalRatio >= 0.6 -> SurvivalRating.RISKY
            else                 -> SurvivalRating.UNLIKELY
        }
    }
}
