package com.fantasyidler.simulator

import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.Skills
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Pre-simulates all 60 frames of a dungeon combat session.
 *
 * Uses a tick-by-tick simulation. The player attacks once per tick at their weapon's
 * attack speed (default 2.4 s, faster for some ranged/magic weapons); the enemy attacks
 * on its own fixed 2.4 s cadence via an accumulator, so a faster player weapon raises the
 * player's attack count without changing incoming damage. Food is eaten immediately after
 * enemy damage if a full-heal fits. Per-tick damage values are stored in
 * [SessionFrame.playerHits] and [SessionFrame.enemyHits] so the UI can animate live HP changes.
 */
object CombatSimulator {

    fun simulateDungeon(
        dungeon: DungeonData,
        enemies: Map<String, EnemyData>,
        playerAttack: Int,
        playerStrength: Int,
        playerDefence: Int,
        blessingDefBonus: Int = 0,
        playerHp: Int = 10,
        weaponAttackBonus: Int = 0,
        weaponStrengthBonus: Int = 0,
        combatStyle: String = "melee",
        playerRanged: Int = 1,
        playerMagic: Int = 1,
        rangedGearStrengthBonus: Int = 0,
        spellMaxHit: Int = 0,
        agilityLevel: Int = 1,
        agilityPrestige: Int = 0,
        petBoostPct: Int = 0,
        equippedFood: Map<String, Int> = emptyMap(),
        foodHealValues: Map<String, Int> = emptyMap(),
        potionBonuses: Map<String, Int> = emptyMap(),
        availableArrows: Map<String, Int> = emptyMap(),
        arrowStrengthBonuses: Map<String, Int> = emptyMap(),
        runeKey: String? = null,
        runeCostPerAttack: Int = 1,
        availableRunes: Int = Int.MAX_VALUE,
        attackSpeedSec: Double = BASE_ATTACK_SPEED_SEC,
        random: Random = Random.Default,
    ): SkillSimulator.Result {
        val speed = attackSpeedSec.coerceIn(1.2, BASE_ATTACK_SPEED_SEC)
        val ticksPerFrame = playerTicksPerFrame(speed)
        val effAttack   = playerAttack   + (potionBonuses["attack"]   ?: 0)
        val effStrength = playerStrength + (potionBonuses["strength"] ?: 0)
        val effDefence  = playerDefence  + (potionBonuses["defense"]  ?: 0) + blessingDefBonus
        val effRanged   = playerRanged   + (potionBonuses["ranged"]   ?: 0)
        val effMagic    = playerMagic    + (potionBonuses["magic"]    ?: 0)

        val frames = mutableListOf<SessionFrame>()

        val spawnPool = dungeon.enemySpawns.flatMap { spawn ->
            List(spawn.weight) { spawn.enemy }
        }.ifEmpty { return SkillSimulator.Result(emptyList(), SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige)) }

        val maxHp = playerHp * 10
        var currentHp = maxHp

        val foodSupply = equippedFood.toMutableMap()
        val foodOrder: List<String> = foodHealValues.entries
            .filter { (k, _) -> k in foodSupply }
            .sortedByDescending { it.value }
            .map { it.key }
        var totalFoodEaten = 0

        val arrowTiers   = availableArrows.entries.map { it.key to it.value }.toMutableList()
        var arrowTierIdx = 0
        var arrowsLeft   = arrowTiers.getOrNull(0)?.second ?: if (combatStyle == "ranged") 0 else Int.MAX_VALUE
        var runesLeft    = availableRunes

        var runningTotal = 0L
        var carryoverEnemyKey: String? = null
        var carryoverEnemyHp = 0
        var enemyClock = 0.0

        val rnd = random

        for (minute in 1..60) {
            val frameItems     = mutableMapOf<String, Int>()
            val frameXpBySkill = mutableMapOf<String, Long>()
            var frameXp        = 0L
            val frameFood   = mutableMapOf<String, Int>()
            val frameArrows = mutableMapOf<String, Int>()
            var frameRunesUsed = 0

            val enemyKey = carryoverEnemyKey ?: spawnPool[rnd.nextInt(spawnPool.size)]
            carryoverEnemyKey = null
            val enemy    = enemies[enemyKey] ?: continue

            // --- Player combat stats for this enemy ---
            // Ranged max hit is recomputed per shot below (not here), since it depends on
            // whichever arrow tier is actually being fired that tick (issue #1018).
            var playerMaxHit: Int
            val playerEffAtk: Int
            val enemyDefStat: Int

            when (combatStyle) {
                "ranged" -> {
                    playerMaxHit = rangedMaxHit(effRanged, rangedGearStrengthBonus, 0)
                    playerEffAtk = effRanged + weaponAttackBonus
                    enemyDefStat = enemy.defensiveStats.rangedDefense
                }
                "magic" -> {
                    playerMaxHit = spellMaxHit.coerceAtLeast(1)
                    playerEffAtk = effMagic + weaponAttackBonus
                    enemyDefStat = enemy.defensiveStats.magicDefense
                }
                else -> {
                    val effStr   = effStrength + weaponStrengthBonus
                    playerMaxHit = max(1, 1 + effStr * (weaponStrengthBonus + 64) / 640)
                    playerEffAtk = effAttack + weaponAttackBonus
                    enemyDefStat = if (combatStyle == "strength") enemy.defensiveStats.strengthDefense
                                   else enemy.defensiveStats.attackDefense
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
            val enemyMaxHit    = if (enemyEffStr == 0) 0 else max(0, 1 + enemyEffStr * (enemy.combatStats.strengthBonus + 64) / 640)
            val enemyEffAtk    = enemy.combatStats.attackLevel + enemy.combatStats.attackBonus
            val enemyHitChance = when {
                enemyEffAtk > effDefence ->
                    1.0 - effDefence / (2.0 * enemyEffAtk.coerceAtLeast(1))
                else ->
                    enemyEffAtk / (2.0 * effDefence.coerceAtLeast(1))
            }.coerceIn(0.10, 0.95)

            // --- Tick-by-tick combat loop ---
            val savedCarryoverHp = carryoverEnemyHp.also { carryoverEnemyHp = 0 }
            var enemyHp = if (savedCarryoverHp > 0) savedCarryoverHp else enemy.hp
            var kills = 0
            val framePlayerHits = mutableListOf<Int>()
            val frameEnemyHits  = mutableListOf<Int>()

            repeat(ticksPerFrame) {
                // Player attacks (ranged is capped by arrow supply)
                val pDmg = when {
                    combatStyle == "ranged" -> {
                        while (arrowsLeft == 0 && arrowTierIdx + 1 < arrowTiers.size) {
                            arrowTierIdx++
                            arrowsLeft = arrowTiers[arrowTierIdx].second
                        }
                        if (arrowsLeft > 0) {
                            val key = arrowTiers[arrowTierIdx].first
                            arrowsLeft--
                            frameArrows[key] = (frameArrows[key] ?: 0) + 1
                            playerMaxHit = rangedMaxHit(effRanged, rangedGearStrengthBonus, arrowStrengthBonuses[key] ?: 0)
                            if (rnd.nextDouble() < playerHitChance) rnd.nextInt(0, playerMaxHit + 1) else 0
                        } else 0
                    }
                    combatStyle == "magic" -> {
                        val canCast = runeKey == null || runesLeft >= runeCostPerAttack
                        if (canCast) {
                            if (runeKey != null) { runesLeft -= runeCostPerAttack; frameRunesUsed++ }
                            if (rnd.nextDouble() < playerHitChance) rnd.nextInt(0, playerMaxHit + 1) else 0
                        } else 0
                    }
                    else -> if (rnd.nextDouble() < playerHitChance) rnd.nextInt(0, playerMaxHit + 1) else 0
                }
                framePlayerHits += pDmg
                enemyHp -= pDmg
                if (enemyHp <= 0) {
                    kills++
                    for (drop in enemy.alwaysDrops) {
                        frameItems[drop.item] = (frameItems[drop.item] ?: 0) + drop.quantity
                    }
                    for (drop in enemy.dropTable) {
                        if (rnd.nextDouble() < drop.chance) {
                            val qty = if (drop.quantityMin >= drop.quantityMax) drop.quantityMin
                                      else rnd.nextInt(drop.quantityMin, drop.quantityMax + 1)
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

                // Enemy attacks on a fixed 2.4s cadence, independent of player speed
                enemyClock += speed
                var eDmg = 0
                while (enemyClock >= BASE_ATTACK_SPEED_SEC - 1e-9) {
                    enemyClock -= BASE_ATTACK_SPEED_SEC
                    if (rnd.nextDouble() < enemyHitChance) eDmg += rnd.nextInt(0, enemyMaxHit + 1)
                }
                frameEnemyHits += eDmg
                currentHp      -= eDmg

                // Always eat the best-tier food still in stock first; only fall back to a
                // weaker tier once the best one runs out, up to 200 items total.
                var ate = true
                while (ate && totalFoodEaten < 300) {
                    ate = false
                    val foodKey = foodOrder.firstOrNull { (foodSupply[it] ?: 0) > 0 } ?: break
                    val heal = foodHealValues[foodKey] ?: break
                    if (currentHp + heal <= maxHp || currentHp <= enemyMaxHit || currentHp <= maxHp * 0.5) {
                        currentHp            = minOf(maxHp, currentHp + heal)
                        foodSupply[foodKey]  = (foodSupply[foodKey] ?: 0) - 1
                        frameFood[foodKey]   = (frameFood[foodKey] ?: 0) + 1
                        totalFoodEaten++
                        ate = true
                    }
                }
            }

            // Carry partial-damage enemy into next frame if still alive.
            // A kill resets enemyHp to enemy.hp, so guard against carrying over
            // a freshly-reset (full-HP) enemy — that would lock the session onto
            // one enemy type for all 60 frames.
            val freshlyKilled = kills > 0 && enemyHp == enemy.hp
            carryoverEnemyKey = if (enemyHp > 0 && !freshlyKilled) enemyKey else null
            carryoverEnemyHp  = if (enemyHp > 0 && !freshlyKilled) enemyHp  else 0

            if (dungeon.safeZone) currentHp = currentHp.coerceAtLeast(1)
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
                    died           = diedThisMinute,
                    foodConsumed   = frameFood,
                    arrowsConsumed = frameArrows,
                    runesConsumed  = if (runeKey != null && frameRunesUsed > 0) mapOf(runeKey to frameRunesUsed * runeCostPerAttack) else emptyMap(),
                    enemyKey       = enemyKey,
                    hpAfter      = currentHp.coerceAtLeast(0),
                    playerHits   = framePlayerHits,
                    enemyHits    = frameEnemyHits,
                )
            )
            runningTotal += frameXp

            if (diedThisMinute) break
        }

        // Roll dungeon rare drops once per completed run (not per kill).
        if (frames.isNotEmpty() && !frames.last().died && dungeon.rareDrops.isNotEmpty()) {
            val lastFrame = frames.last()
            val rareItems = lastFrame.items.toMutableMap()
            for (rare in dungeon.rareDrops) {
                if (rnd.nextDouble() < rare.chance) {
                    rareItems[rare.item] = (rareItems[rare.item] ?: 0) + 1
                }
            }
            if (rareItems != lastFrame.items) {
                frames[frames.lastIndex] = lastFrame.copy(items = rareItems)
            }
        }

        val fullDurationMs = SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige)
        return SkillSimulator.Result(frames, fullDurationMs)
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /** Ranged max hit for the arrow currently being fired — [arrowStrengthBonus] varies per shot, [gearStrengthBonus] doesn't. */
    private fun rangedMaxHit(effRanged: Int, gearStrengthBonus: Int, arrowStrengthBonus: Int): Int {
        val strBonus = gearStrengthBonus + arrowStrengthBonus
        val effStr   = effRanged + strBonus
        return max(1, 1 + effStr * (strBonus + 64) / 640)
    }

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

    /**
     * Tick-by-tick boss simulation. Returns one [SessionFrame] per simulated minute
     * (up to [BossData.durationMinutes] frames). Each frame has [SessionFrame.playerHits]
     * and [SessionFrame.enemyHits] populated for live combat-log animation. Loot and XP
     * are attached to the final frame on a win.
     */
    fun simulateBoss(
        boss: BossData,
        bossKey: String,
        playerAttack: Int,
        playerStrength: Int,
        playerDefence: Int,
        playerHp: Int,
        weaponAttackBonus: Int,
        weaponStrBonus: Int,
        combatStyle: String = "melee",
        playerRanged: Int = 1,
        playerMagic: Int = 1,
        rangedGearStrengthBonus: Int = 0,
        spellMaxHit: Int = 0,
        availableArrows: Map<String, Int> = emptyMap(),
        arrowStrengthBonuses: Map<String, Int> = emptyMap(),
        equippedFood: Map<String, Int> = emptyMap(),
        foodHealValues: Map<String, Int> = emptyMap(),
        blessingDefBonus: Int = 0,
        runeKey: String? = null,
        runeCostPerAttack: Int = 1,
        availableRunes: Int = Int.MAX_VALUE,
        attackSpeedSec: Double = BASE_ATTACK_SPEED_SEC,
        random: Random = Random.Default,
    ): List<SessionFrame> {
        val speed = attackSpeedSec.coerceIn(1.2, BASE_ATTACK_SPEED_SEC)
        val ticksPerFrame = playerTicksPerFrame(speed)
        // Ranged max hit is recomputed per shot below (not here), since it depends on
        // whichever arrow tier is actually being fired that tick (issue #1018).
        var playerMax: Int
        val effAtk: Int
        val bossDefence: Int
        when (combatStyle) {
            "ranged" -> {
                playerMax  = rangedMaxHit(playerRanged, rangedGearStrengthBonus, 0)
                effAtk     = playerRanged + weaponAttackBonus
                bossDefence = boss.defensiveStats.rangedDefense
            }
            "magic" -> {
                playerMax  = spellMaxHit.coerceAtLeast(1)
                effAtk     = playerMagic + weaponAttackBonus
                bossDefence = boss.defensiveStats.magicDefense
            }
            else -> {
                val effStr = playerStrength + weaponStrBonus
                playerMax  = max(1, 1 + effStr * (weaponStrBonus + 64) / 640)
                effAtk     = playerAttack + weaponAttackBonus
                bossDefence = if (combatStyle == "strength") boss.defensiveStats.strengthDefense
                              else boss.defensiveStats.attackDefense
            }
        }
        val arrowTiers   = availableArrows.entries.map { it.key to it.value }.toMutableList()
        var arrowTierIdx = 0
        var arrowsLeft   = arrowTiers.getOrNull(0)?.second ?: if (combatStyle == "ranged") 0 else Int.MAX_VALUE
        var runesLeft    = availableRunes
        val playerHitChance = when {
            effAtk > bossDefence -> 1.0 - bossDefence / (2.0 * effAtk.coerceAtLeast(1))
            else                 -> effAtk / (2.0 * bossDefence.coerceAtLeast(1))
        }.coerceIn(0.10, 0.95)

        val bossEffStr = boss.combatStats.strengthLevel + boss.combatStats.strengthBonus
        val bossMax    = if (bossEffStr == 0) 0 else max(0, 1 + bossEffStr * (boss.combatStats.strengthBonus + 64) / 640)
        val bossEffAtk = boss.combatStats.attackLevel + boss.combatStats.attackBonus
        val effPlayerDefence = playerDefence + blessingDefBonus
        val bossHitChance = when {
            bossEffAtk > effPlayerDefence -> 1.0 - effPlayerDefence / (2.0 * bossEffAtk.coerceAtLeast(1))
            else                          -> bossEffAtk / (2.0 * effPlayerDefence.coerceAtLeast(1))
        }.coerceIn(0.10, 0.95)

        val maxHp         = playerHp * 10
        var currentHp     = maxHp
        var currentBossHp = boss.hp
        val maxFrames     = boss.durationMinutes
        val frames        = mutableListOf<SessionFrame>()
        var won           = false

        val foodSupply = equippedFood.toMutableMap()
        val foodOrder: List<String> = foodHealValues.entries
            .filter { (k, _) -> k in foodSupply }
            .sortedByDescending { it.value }
            .map { it.key }
        var totalFoodEaten = 0
        var bossClock = 0.0

        val rnd = random

        outer@ while (frames.size < maxFrames) {
            val pHits       = mutableListOf<Int>()
            val eHits       = mutableListOf<Int>()
            val frameFood   = mutableMapOf<String, Int>()
            val frameArrows = mutableMapOf<String, Int>()
            var frameRunesUsed = 0

            for (tick in 0 until ticksPerFrame) {
                val pDmg = when {
                    combatStyle == "ranged" -> {
                        while (arrowsLeft == 0 && arrowTierIdx + 1 < arrowTiers.size) {
                            arrowTierIdx++
                            arrowsLeft = arrowTiers[arrowTierIdx].second
                        }
                        if (arrowsLeft > 0) {
                            val key = arrowTiers[arrowTierIdx].first
                            arrowsLeft--
                            frameArrows[key] = (frameArrows[key] ?: 0) + 1
                            playerMax = rangedMaxHit(playerRanged, rangedGearStrengthBonus, arrowStrengthBonuses[key] ?: 0)
                            if (rnd.nextDouble() < playerHitChance) rnd.nextInt(0, playerMax + 1) else 0
                        } else 0
                    }
                    combatStyle == "magic" -> {
                        val canCast = runeKey == null || runesLeft >= runeCostPerAttack
                        if (canCast) {
                            if (runeKey != null) { runesLeft -= runeCostPerAttack; frameRunesUsed++ }
                            if (rnd.nextDouble() < playerHitChance) rnd.nextInt(0, playerMax + 1) else 0
                        } else 0
                    }
                    else -> if (rnd.nextDouble() < playerHitChance) rnd.nextInt(0, playerMax + 1) else 0
                }
                currentBossHp -= pDmg
                pHits.add(pDmg)

                if (currentBossHp <= 0) {
                    won = true
                    frames.add(SessionFrame(
                        minute = frames.size, xpGain = 0, xpBefore = 0L, xpAfter = 0L,
                        levelBefore = 0, levelAfter = 0,
                        kills = 1, enemyKey = bossKey,
                        playerHits = pHits, enemyHits = eHits, hpAfter = currentHp,
                        foodConsumed  = frameFood,
                        arrowsConsumed = frameArrows,
                        runesConsumed  = if (runeKey != null && frameRunesUsed > 0) mapOf(runeKey to frameRunesUsed * runeCostPerAttack) else emptyMap(),
                    ))
                    break@outer
                }

                // Boss attacks on a fixed 2.4s cadence, independent of player speed
                bossClock += speed
                var bDmg = 0
                while (bossClock >= BASE_ATTACK_SPEED_SEC - 1e-9) {
                    bossClock -= BASE_ATTACK_SPEED_SEC
                    if (rnd.nextDouble() < bossHitChance) bDmg += rnd.nextInt(0, bossMax + 1)
                }
                currentHp = (currentHp - bDmg).coerceAtLeast(0)
                eHits.add(bDmg)

                // Always eat the best-tier food still in stock first; only fall back to a
                // weaker tier once the best one runs out, up to 200 items total.
                var ate = true
                while (ate && totalFoodEaten < 300) {
                    ate = false
                    val foodKey = foodOrder.firstOrNull { (foodSupply[it] ?: 0) > 0 } ?: break
                    val heal = foodHealValues[foodKey] ?: break
                    if (currentHp + heal <= maxHp || currentHp <= bossMax || currentHp <= maxHp * 0.5) {
                        currentHp            = minOf(maxHp, currentHp + heal)
                        foodSupply[foodKey]  = (foodSupply[foodKey] ?: 0) - 1
                        frameFood[foodKey]   = (frameFood[foodKey] ?: 0) + 1
                        totalFoodEaten++
                        ate = true
                    }
                }

                if (currentHp <= 0) {
                    frames.add(SessionFrame(
                        minute = frames.size, xpGain = 0, xpBefore = 0L, xpAfter = 0L,
                        levelBefore = 0, levelAfter = 0,
                        kills = 0, enemyKey = bossKey,
                        playerHits = pHits, enemyHits = eHits, hpAfter = 0,
                        foodConsumed  = frameFood,
                        arrowsConsumed = frameArrows,
                        runesConsumed  = if (runeKey != null && frameRunesUsed > 0) mapOf(runeKey to frameRunesUsed * runeCostPerAttack) else emptyMap(),
                    ))
                    break@outer
                }
            }

            if (frames.size < maxFrames && currentHp > 0 && currentBossHp > 0) {
                frames.add(SessionFrame(
                    minute = frames.size, xpGain = 0, xpBefore = 0L, xpAfter = 0L,
                    levelBefore = 0, levelAfter = 0,
                    kills = 0, enemyKey = bossKey,
                    playerHits = pHits, enemyHits = eHits, hpAfter = currentHp,
                    foodConsumed  = frameFood,
                    arrowsConsumed = frameArrows,
                    runesConsumed  = if (runeKey != null && frameRunesUsed > 0) mapOf(runeKey to frameRunesUsed * runeCostPerAttack) else emptyMap(),
                ))
            }
        }

        // DPS fallback if the frame cap was hit with neither side dead.
        if (frames.isEmpty() || (frames.last().kills == 0 && currentBossHp > 0 && currentHp > 0)) {
            val playerDps = (playerMax / 2.0) * playerHitChance / speed
            val bossDps   = (bossMax / 2.0) * bossHitChance / BASE_ATTACK_SPEED_SEC
            won = if (playerDps > 0 && bossDps > 0) {
                (boss.hp / playerDps) <= (maxHp / bossDps)
            } else playerDps >= bossDps
            val stub = SessionFrame(
                minute = frames.size, xpGain = 0, xpBefore = 0L, xpAfter = 0L,
                levelBefore = 0, levelAfter = 0,
                kills = if (won) 1 else 0, enemyKey = bossKey, hpAfter = if (won) 1 else 0,
            )
            if (frames.isEmpty()) frames.add(stub) else frames[frames.lastIndex] = stub
        }

        // Attach loot and XP to the final frame.
        val items     = mutableMapOf<String, Int>()
        val xpBySkill = mutableMapOf<String, Long>()
        if (won) {
            items["coins"] = rnd.nextInt(boss.commonLoot.coinsMin, boss.commonLoot.coinsMax + 1)
            for ((item, range) in boss.commonLoot.items) {
                items[item] = if (range.min >= range.max) range.min
                              else rnd.nextInt(range.min, range.max + 1)
            }
            for (rare in boss.rareDrops)
                if (rnd.nextDouble() < rare.chance) items[rare.item] = (items[rare.item] ?: 0) + 1
            boss.pet?.let { pet -> if (rnd.nextDouble() < pet.chance) items[pet.id] = 1 }
            for ((skill, xp) in boss.xpRewards) xpBySkill[skill] = xp.toLong()
        }

        if (!won) {
            for ((skill, xp) in boss.xpRewards) xpBySkill[skill] = maxOf(1L, (xp * 0.1).toLong())
        }

        val totalXp = xpBySkill.values.sum()
        val last = frames.last()
        frames[frames.lastIndex] = last.copy(
            xpGain       = totalXp.toInt(),
            xpAfter      = totalXp,
            items        = items,
            xpBySkill    = xpBySkill,
            killsByEnemy = if (won) mapOf(bossKey to 1) else emptyMap(),
            combatStyle  = combatStyle,
        )

        return frames
    }

    /** Ticks per 60-second frame at the base attack speed (one attack every 2.4 s). */
    const val TICKS_PER_FRAME = 25

    /** Default/enemy attack speed in seconds; player weapons may attack faster via their attackSpeed field. */
    const val BASE_ATTACK_SPEED_SEC = 2.4

    /** Number of player attack ticks in a 60-second frame at the given attack speed. */
    fun playerTicksPerFrame(attackSpeedSec: Double): Int = (60.0 / attackSpeedSec).roundToInt()

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
            val enemyMaxHit = if (enemyEffStr == 0) 0 else max(0, 1 + enemyEffStr * (enemy.combatStats.strengthBonus + 64) / 640)
            val enemyEffAtk = enemy.combatStats.attackLevel + enemy.combatStats.attackBonus
            val enemyHit    = when {
                enemyEffAtk > playerDefence -> 1.0 - playerDefence / (2.0 * enemyEffAtk.coerceAtLeast(1))
                else                        -> enemyEffAtk / (2.0 * playerDefence.coerceAtLeast(1))
            }.coerceIn(0.10, 0.95)
            weightedDPM += weight * ((enemyMaxHit / 2.0) * enemyHit / BASE_ATTACK_SPEED_SEC * 60.0)
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
