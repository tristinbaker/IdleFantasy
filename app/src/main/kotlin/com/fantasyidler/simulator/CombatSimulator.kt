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
 * The player is assumed to survive the full session (idle-game simplification).
 * XP is distributed per-skill and stored in each [SessionFrame.xpBySkill], so
 * the collect step can apply rewards to multiple combat skills at once.
 */
object CombatSimulator {

    /**
     * @param dungeon           the dungeon definition (spawn table, etc.)
     * @param enemies           full enemy map from GameDataRepository
     * @param playerAttack      player's attack level
     * @param playerStrength    player's strength level
     * @param playerDefence     player's defence level
     * @param playerHp          player's hitpoints level (unused in simulation for now)
     * @param weaponAttackBonus equipment attack bonus from equipped weapon
     * @param weaponStrengthBonus equipment strength bonus from equipped weapon
     * @param combatStyle       "melee" | "ranged" | "magic"
     * @param agilityLevel      for session duration reduction (same as gathering)
     * @param petBoostPct       XP boost % from an equipped combat pet (0 = none)
     */
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
    ): SkillSimulator.Result {
        val frames = mutableListOf<SessionFrame>()

        // Build a weighted spawn pool (repeat enemy key by its weight for O(1) pick)
        val spawnPool = dungeon.enemySpawns.flatMap { spawn ->
            List(spawn.weight) { spawn.enemy }
        }.ifEmpty { return SkillSimulator.Result(emptyList(), SkillSimulator.sessionDurationMs(agilityLevel)) }

        var runningTotal = 0L

        for (minute in 1..60) {
            val frameItems      = mutableMapOf<String, Int>()
            val frameXpBySkill  = mutableMapOf<String, Long>()
            var frameXp         = 0L

            // Pick a random enemy for this minute's encounters
            val enemyKey = spawnPool[Random.nextInt(spawnPool.size)]
            val enemy    = enemies[enemyKey] ?: continue

            val kills = killsThisMinute(
                playerAttack        = playerAttack,
                playerStrength      = playerStrength,
                playerRanged        = playerRanged,
                playerMagic         = playerMagic,
                weaponAttackBonus   = weaponAttackBonus,
                weaponStrengthBonus = weaponStrengthBonus,
                arrowStrengthBonus  = arrowStrengthBonus,
                spellMaxHit         = spellMaxHit,
                combatStyle         = combatStyle,
                enemy               = enemy,
            )

            repeat(kills) {
                // Always-drops (bones, etc.)
                for (drop in enemy.alwaysDrops) {
                    frameItems[drop.item] = (frameItems[drop.item] ?: 0) + drop.quantity
                }
                // Chance-based drops
                for (drop in enemy.dropTable) {
                    if (Random.nextDouble() < drop.chance) {
                        val qty = if (drop.quantityMin >= drop.quantityMax) drop.quantityMin
                                  else Random.nextInt(drop.quantityMin, drop.quantityMax + 1)
                        frameItems[drop.item] = (frameItems[drop.item] ?: 0) + qty
                    }
                }
                // XP — apply pet boost then distribute across skills
                val baseXp = (enemy.xpDrops["combat"] ?: 0).toLong()
                val xp     = if (petBoostPct > 0) (baseXp * (1.0 + petBoostPct / 100.0)).toLong()
                             else baseXp
                for ((skill, skillXp) in distributeXp(xp, combatStyle)) {
                    frameXpBySkill[skill] = (frameXpBySkill[skill] ?: 0L) + skillXp
                }
                frameXp += xp
            }

            frames.add(
                SessionFrame(
                    minute        = minute,
                    xpGain        = frameXp.toInt(),
                    xpBefore      = runningTotal,
                    xpAfter       = runningTotal + frameXp,
                    levelBefore   = 0,
                    levelAfter    = 0,
                    items         = frameItems,
                    xpBySkill     = frameXpBySkill,
                    kills         = kills,
                    killsByEnemy  = if (kills > 0) mapOf(enemyKey to kills) else emptyMap(),
                )
            )
            runningTotal += frameXp
        }

        return SkillSimulator.Result(frames, SkillSimulator.sessionDurationMs(agilityLevel))
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Estimates how many kills the player gets per minute against [enemy].
     *
     * Formula (OSRS-inspired, simplified for idle):
     *   maxHit    = 1 + effectiveStrength * (weaponStrBonus + 64) / 640
     *   hitChance = clamp using attack vs enemy defence rolls
     *   dps       = avgHit / ATTACK_SPEED_SEC
     *   kpm       = dps * 60 / enemy.hp
     */
    private fun killsThisMinute(
        playerAttack: Int,
        playerStrength: Int,
        playerRanged: Int,
        playerMagic: Int,
        weaponAttackBonus: Int,
        weaponStrengthBonus: Int,
        arrowStrengthBonus: Int,
        spellMaxHit: Int,
        combatStyle: String,
        enemy: EnemyData,
    ): Int {
        val maxHit: Int
        val effectiveAttack: Int
        val enemyDefence: Int

        when (combatStyle) {
            "ranged" -> {
                val effStr = playerRanged + arrowStrengthBonus
                maxHit          = max(1, 1 + effStr * (arrowStrengthBonus + 64) / 640)
                effectiveAttack = playerRanged + weaponAttackBonus
                enemyDefence    = enemy.defensiveStats.rangedDefense
            }
            "magic" -> {
                maxHit          = spellMaxHit.coerceAtLeast(1)
                effectiveAttack = playerMagic + weaponAttackBonus
                enemyDefence    = enemy.defensiveStats.magicDefense
            }
            else -> { // melee
                val effStr      = playerStrength + weaponStrengthBonus
                maxHit          = max(1, 1 + effStr * (weaponStrengthBonus + 64) / 640)
                effectiveAttack = playerAttack + weaponAttackBonus
                enemyDefence    = enemy.defensiveStats.attackDefense
            }
        }

        val hitChance = when {
            effectiveAttack > enemyDefence ->
                1.0 - enemyDefence / (2.0 * effectiveAttack.coerceAtLeast(1))
            else ->
                effectiveAttack / (2.0 * enemyDefence.coerceAtLeast(1))
        }.coerceIn(0.15, 0.95)

        val avgDamage = (maxHit / 2.0) * hitChance
        val dps       = avgDamage / ATTACK_SPEED_SEC
        val kpm       = dps * 60.0 / enemy.hp.coerceAtLeast(1)

        // Stochastic rounding: floor + fractional probability of +1
        val base = kpm.toInt().coerceAtLeast(0)
        return base + if (Random.nextDouble() < (kpm - base)) 1 else 0
    }

    /**
     * Distributes total XP across skills based on combat style.
     *
     * Attack:   70% attack   · 15% hitpoints · 15% defense
     * Strength: 70% strength · 15% hitpoints · 15% defense
     * Ranged:   70% ranged   · 15% hitpoints · 15% defense
     * Magic:    70% magic    · 15% hitpoints · 15% defense
     *
     * Defense and HP always gain at the same passive rate regardless of style.
     */
    private fun distributeXp(totalXp: Long, style: String): Map<String, Long> {
        val hp  = (totalXp * 0.15).toLong()
        val def = (totalXp * 0.15).toLong()
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

    /** Weapon attack speed in seconds (standard 4-tick OSRS weapon). */
    private const val ATTACK_SPEED_SEC = 2.4
}
