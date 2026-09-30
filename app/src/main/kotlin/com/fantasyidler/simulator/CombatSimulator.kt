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
        floorReductionMin: Double = 0.0,
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
        eatThresholdPct: Int = 50,
        foodEatOrder: String = "descending",
        chronosMultiplier: Float = 1.0f,
        doubleHitChance: Double = 0.0,
        secondChance: Boolean = false,
        random: Random = Random.Default,
    ): SkillSimulator.Result {
        val speed = attackSpeedSec.coerceIn(1.2, BASE_ATTACK_SPEED_SEC)
        val ticksPerFrame = playerTicksPerFrame(speed)
        val eatThresholdFraction = eatThresholdPct.coerceIn(0, 100) / 100.0
        val effAttack   = playerAttack   + (potionBonuses["attack"]   ?: 0)
        val effStrength = playerStrength + (potionBonuses["strength"] ?: 0)
        val effDefence  = playerDefence  + (potionBonuses["defense"]  ?: 0) + blessingDefBonus
        val effRanged   = playerRanged   + (potionBonuses["ranged"]   ?: 0)
        val effMagic    = playerMagic    + (potionBonuses["magic"]    ?: 0)
        val statsAtStart = mapOf(
            "atk" to when (combatStyle) { "ranged" -> effRanged; "magic" -> effMagic; else -> effAttack } + weaponAttackBonus,
            "str" to when (combatStyle) {
                "ranged" -> effRanged + rangedGearStrengthBonus
                "magic"  -> spellMaxHit
                else     -> effStrength + weaponStrengthBonus
            },
            "def" to effDefence,
            "atk_potion" to when (combatStyle) { "ranged" -> potionBonuses["ranged"] ?: 0; "magic" -> potionBonuses["magic"] ?: 0; else -> potionBonuses["attack"] ?: 0 },
            "str_potion" to when (combatStyle) { "ranged" -> potionBonuses["ranged"] ?: 0; "magic" -> 0; else -> potionBonuses["strength"] ?: 0 },
            "def_potion" to (potionBonuses["defense"] ?: 0),
        )

        val frames = mutableListOf<SessionFrame>()

        val spawnPool = dungeon.enemySpawns.flatMap { spawn ->
            List(spawn.weight) { spawn.enemy }
        }.ifEmpty { return SkillSimulator.Result(emptyList(), SkillSimulator.sessionDurationMs(agilityLevel, floorReductionMin, chronosMultiplier)) }

        val maxHp = playerHp * 10
        var currentHp = maxHp

        val foodSupply = equippedFood.toMutableMap()
        val foodOrder: List<String> = foodHealValues.entries
            .filter { (k, _) -> k in foodSupply }
            .sortedBy {
                when (foodEatOrder) {
                    "descending" -> -it.value
                    "ascending" -> it.value
                    "least_quantity" -> equippedFood[it.key] ?: 0
                    else -> 0
                }
            }
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

            var enemyKey = carryoverEnemyKey ?: spawnPool[rnd.nextInt(spawnPool.size)]
            carryoverEnemyKey = null
            var enemy    = enemies[enemyKey] ?: continue
            val frameStartEnemyKey = enemyKey
            val frameKillsByEnemy  = mutableMapOf<String, Int>()
            val frameSpawnsAfterKills = mutableListOf<String>()

            // --- Per-enemy combat stats, recomputed on every spawn: each kill rolls a fresh
            // enemy type mid-frame so spawns follow their weights instead of locking one type
            // per frame and chaining it via carryover (issue #1557) ---
            // Ranged max hit is recomputed per shot below (not here), since it depends on
            // whichever arrow tier is actually being fired that tick (issue #1018).
            var playerMaxHit    = 0
            var playerHitChance = 0.0
            var enemyMaxHit     = 0
            var enemyHitChance  = 0.0

            fun refreshCombatStats() {
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
                playerHitChance = when {
                    playerEffAtk > enemyDefStat ->
                        1.0 - enemyDefStat / (2.0 * playerEffAtk.coerceAtLeast(1))
                    else ->
                        playerEffAtk / (2.0 * enemyDefStat.coerceAtLeast(1))
                }.coerceIn(0.15, 0.95)

                val enemyEffStr = enemy.combatStats.strengthLevel + enemy.combatStats.strengthBonus
                enemyMaxHit     = if (enemyEffStr == 0) 0 else max(0, 1 + enemyEffStr * (enemy.combatStats.strengthBonus + 64) / 640)
                val enemyEffAtk = enemy.combatStats.attackLevel + enemy.combatStats.attackBonus
                enemyHitChance  = when {
                    enemyEffAtk > effDefence ->
                        1.0 - effDefence / (2.0 * enemyEffAtk.coerceAtLeast(1))
                    else ->
                        enemyEffAtk / (2.0 * effDefence.coerceAtLeast(1))
                }.coerceIn(0.10, 0.95)
            }
            refreshCombatStats()

            // --- Tick-by-tick combat loop ---
            val savedCarryoverHp = carryoverEnemyHp.also { carryoverEnemyHp = 0 }
            var enemyHp = if (savedCarryoverHp > 0) savedCarryoverHp else enemy.hp
            var kills = 0
            val framePlayerHits  = mutableListOf<Int>()
            val frameEnemyHits   = mutableListOf<Int>()
            val framePlayerHeals = mutableListOf<Int>()
            val frameDoubleHitTicks = mutableListOf<Int>()

            for (tick in 0 until ticksPerFrame) {
                // Player attacks (ranged is capped by arrow supply)
                val pDmg = when (combatStyle) {
                    "ranged" -> {
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
                    "magic" -> {
                        val canCast = runeKey == null || runesLeft >= runeCostPerAttack
                        if (canCast) {
                            if (runeKey != null) { runesLeft -= runeCostPerAttack; frameRunesUsed++ }
                            if (rnd.nextDouble() < playerHitChance) rnd.nextInt(0, playerMaxHit + 1) else 0
                        } else 0
                    }
                    else -> {
                        var dmg = if (rnd.nextDouble() < playerHitChance) rnd.nextInt(0, playerMaxHit + 1)
                                  else if (secondChance && rnd.nextDouble() < playerHitChance) rnd.nextInt(0, playerMaxHit + 1)
                                  else 0
                        // Double Hit only strikes a still-living enemy (no overkill carry).
                        if (doubleHitChance > 0 && enemyHp - dmg > 0 &&
                            rnd.nextDouble() < doubleHitChance && rnd.nextDouble() < playerHitChance
                        ) {
                            dmg += rnd.nextInt(0, playerMaxHit + 1)
                            frameDoubleHitTicks += tick
                        }
                        dmg
                    }
                }
                framePlayerHits += pDmg
                enemyHp -= pDmg
                if (enemyHp <= 0) {
                    kills++
                    frameKillsByEnemy[enemyKey] = (frameKillsByEnemy[enemyKey] ?: 0) + 1
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
                    enemyKey = spawnPool[rnd.nextInt(spawnPool.size)]
                    enemy    = enemies[enemyKey] ?: enemy
                    frameSpawnsAfterKills += enemyKey
                    refreshCombatStats()
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

                // Dead characters stop fighting (issue #935). Checked before the eating
                // phase so food cannot revive a player already at 0 HP (issue #1412).
                // Safe zones clamp HP back to 1 after the frame, so death never ends those.
                if (currentHp <= 0 && !dungeon.safeZone) {
                    framePlayerHeals += 0
                    break
                }

                // Eats food based on the selected order, and move to next in list
                // when the current selected one runs out, up to 300 items total.
                val hpBeforeEating = currentHp
                var ate = true
                while (ate && totalFoodEaten < 300) {
                    ate = false
                    val foodKey = foodOrder.firstOrNull { (foodSupply[it] ?: 0) > 0 } ?: break
                    val heal = foodHealValues[foodKey] ?: break
                    if (currentHp <= enemyMaxHit || currentHp <= maxHp * eatThresholdFraction) {
                        currentHp            = minOf(maxHp, currentHp + heal)
                        foodSupply[foodKey]  = (foodSupply[foodKey] ?: 0) - 1
                        frameFood[foodKey]   = (frameFood[foodKey] ?: 0) + 1
                        totalFoodEaten++
                        ate = true
                    }
                }
                framePlayerHeals += currentHp - hpBeforeEating
            }

            // Carry only a genuinely in-progress fight into the next frame; an untouched
            // fresh spawn re-rolls there instead (same weighted distribution either way).
            val fightInProgress = enemyHp in 1 until enemy.hp
            carryoverEnemyKey = if (fightInProgress) enemyKey else null
            carryoverEnemyHp  = if (fightInProgress) enemyHp  else 0

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
                    killsByEnemy = frameKillsByEnemy.toMap(),
                    died           = diedThisMinute,
                    foodConsumed   = frameFood,
                    arrowsConsumed = frameArrows,
                    runesConsumed  = if (runeKey != null && frameRunesUsed > 0) mapOf(runeKey to frameRunesUsed * runeCostPerAttack) else emptyMap(),
                    enemyKey       = frameStartEnemyKey,
                    spawnsAfterKills = frameSpawnsAfterKills,
                    hpAfter      = currentHp.coerceAtLeast(0),
                    playerHits   = framePlayerHits,
                    doubleHitTicks = frameDoubleHitTicks,
                    enemyHits    = frameEnemyHits,
                    playerHeals  = framePlayerHeals,
                    maxHp        = maxHp,
                    foodAtStart  = if (frames.isEmpty()) equippedFood else emptyMap(),
                    statsAtStart = if (frames.isEmpty()) statsAtStart else emptyMap(),
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

        val fullDurationMs = SkillSimulator.sessionDurationMs(agilityLevel, floorReductionMin, chronosMultiplier)
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
     * A hired mercenary fighting alongside the player in a raid.
     * [effAttack] is the style level plus attack bonus; [hpLevel] scales x10 like the player's.
     */
    data class MercCombatant(
        val id: String,
        val style: String,
        val effAttack: Int,
        val maxHit: Int,
        val defense: Int,
        val hpLevel: Int,
    )

    /**
     * Tick-by-tick boss simulation. Returns one [SessionFrame] per simulated minute
     * (up to [BossData.durationMinutes] frames). Each frame has [SessionFrame.playerHits]
     * and [SessionFrame.enemyHits] populated for live combat-log animation. Loot and XP
     * are attached to the final frame on a win.
     *
     * Raids: [mercenaries] fight as full combatants. They attack on the standard 2.4s
     * cadence, the boss picks a uniformly random living party member per attack, and a
     * merc at 0 HP is out for the rest of the fight. Player death still ends the raid.
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
        eatThresholdPct: Int = 50,
        foodEatOrder: String = "descending",
        doubleHitChance: Double = 0.0,
        secondChance: Boolean = false,
        mercenaries: List<MercCombatant> = emptyList(),
        /** Rare-drop item keys that must not roll (heirlooms the player already owns). */
        blockedRareDrops: Set<String> = emptySet(),
        potionAttackBonus: Int = 0,
        potionStrengthBonus: Int = 0,
        potionDefenseBonus: Int = 0,
        random: Random = Random.Default,
    ): List<SessionFrame> {
        val speed = attackSpeedSec.coerceIn(1.2, BASE_ATTACK_SPEED_SEC)
        val ticksPerFrame = playerTicksPerFrame(speed)
        val eatThresholdFraction = eatThresholdPct.coerceIn(0, 100) / 100.0
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

        // Raid-tier enrage: below a full party (player + MercenaryRepository.MAX_PARTY mercs)
        // the boss hits proportionally harder and more often, so a maxed solo player cannot
        // out-heal it with the 300-food cap (issue #1578). A full party fights it unscaled.
        val partyScale = if (boss.raid) RAID_FULL_PARTY.toDouble() / (1 + mercenaries.size) else 1.0
        val bossEffStr = boss.combatStats.strengthLevel + boss.combatStats.strengthBonus
        val bossMax    = if (bossEffStr == 0) 0 else (max(0, 1 + bossEffStr * (boss.combatStats.strengthBonus + 64) / 640) * partyScale).roundToInt()
        val bossEffAtk = boss.combatStats.attackLevel + boss.combatStats.attackBonus
        val effPlayerDefence = playerDefence + blessingDefBonus
        val statsAtStart = mapOf(
            "atk" to effAtk,
            "str" to when (combatStyle) {
                "ranged" -> playerRanged + rangedGearStrengthBonus
                "magic"  -> spellMaxHit
                else     -> playerStrength + weaponStrBonus
            },
            "def" to effPlayerDefence,
            "atk_potion" to potionAttackBonus,
            "str_potion" to potionStrengthBonus,
            "def_potion" to potionDefenseBonus,
        )
        val bossHitChance = (when {
            bossEffAtk > effPlayerDefence -> 1.0 - effPlayerDefence / (2.0 * bossEffAtk.coerceAtLeast(1))
            else                          -> bossEffAtk / (2.0 * effPlayerDefence.coerceAtLeast(1))
        } * partyScale).coerceIn(0.10, 0.95)

        val maxHp         = playerHp * 10
        var currentHp     = maxHp
        var currentBossHp = boss.hp
        val maxFrames     = boss.durationMinutes
        val frames        = mutableListOf<SessionFrame>()
        var won           = false

        val foodSupply = equippedFood.toMutableMap()
        val foodOrder: List<String> = foodHealValues.entries
            .filter { (k, _) -> k in foodSupply }
            .sortedBy {
                when (foodEatOrder) {
                    "descending" -> -it.value
                    "ascending" -> it.value
                    "least_quantity" -> equippedFood[it.key] ?: 0
                    else -> 0
                }
            }
            .map { it.key }
        var totalFoodEaten = 0
        var bossClock = 0.0
        var mercClock = 0.0

        val mercHitChance = mercenaries.map { m ->
            val def = when (m.style) {
                "ranged" -> boss.defensiveStats.rangedDefense
                "magic"  -> boss.defensiveStats.magicDefense
                else     -> boss.defensiveStats.attackDefense
            }
            when {
                m.effAttack > def -> 1.0 - def / (2.0 * m.effAttack.coerceAtLeast(1))
                else              -> m.effAttack / (2.0 * def.coerceAtLeast(1))
            }.coerceIn(0.10, 0.95)
        }
        val bossHitChanceVsMerc = mercenaries.map { m ->
            (when {
                bossEffAtk > m.defense -> 1.0 - m.defense / (2.0 * bossEffAtk.coerceAtLeast(1))
                else                   -> bossEffAtk / (2.0 * m.defense.coerceAtLeast(1))
            } * partyScale).coerceIn(0.10, 0.95)
        }
        val mercHp = IntArray(mercenaries.size) { mercenaries[it].hpLevel * 10 }

        val rnd = random

        outer@ while (frames.size < maxFrames) {
            val pHits       = mutableListOf<Int>()
            val eHits       = mutableListOf<Int>()
            val aHits       = mutableListOf<Int>()
            val pHeals      = mutableListOf<Int>()
            val pDoubleHitTicks = mutableListOf<Int>()
            val frameFood   = mutableMapOf<String, Int>()
            val frameArrows = mutableMapOf<String, Int>()
            var frameRunesUsed = 0

            for (tick in 0 until ticksPerFrame) {
                val pDmg = when (combatStyle) {
                    "ranged" -> {
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
                    "magic" -> {
                        val canCast = runeKey == null || runesLeft >= runeCostPerAttack
                        if (canCast) {
                            if (runeKey != null) { runesLeft -= runeCostPerAttack; frameRunesUsed++ }
                            if (rnd.nextDouble() < playerHitChance) rnd.nextInt(0, playerMax + 1) else 0
                        } else 0
                    }
                    else -> {
                        var dmg = if (rnd.nextDouble() < playerHitChance) rnd.nextInt(0, playerMax + 1)
                                  else if (secondChance && rnd.nextDouble() < playerHitChance) rnd.nextInt(0, playerMax + 1)
                                  else 0
                        // Double Hit only strikes a still-living boss (no overkill carry).
                        if (doubleHitChance > 0 && currentBossHp - dmg > 0 &&
                            rnd.nextDouble() < doubleHitChance && rnd.nextDouble() < playerHitChance
                        ) {
                            dmg += rnd.nextInt(0, playerMax + 1)
                            pDoubleHitTicks += tick
                        }
                        dmg
                    }
                }
                currentBossHp -= pDmg
                pHits.add(pDmg)

                // Mercenaries attack on the standard 2.4s cadence, independent of player speed.
                if (mercenaries.isNotEmpty()) {
                    mercClock += speed
                    var aDmg = 0
                    while (mercClock >= BASE_ATTACK_SPEED_SEC - 1e-9) {
                        mercClock -= BASE_ATTACK_SPEED_SEC
                        for (i in mercenaries.indices) {
                            if (mercHp[i] <= 0) continue
                            if (rnd.nextDouble() < mercHitChance[i]) aDmg += rnd.nextInt(0, mercenaries[i].maxHit + 1)
                        }
                    }
                    currentBossHp -= aDmg
                    aHits.add(aDmg)
                }

                if (currentBossHp <= 0) {
                    won = true
                    frames.add(SessionFrame(
                        minute = frames.size, xpGain = 0, xpBefore = 0L, xpAfter = 0L,
                        levelBefore = 0, levelAfter = 0,
                        kills = 1, enemyKey = bossKey,
                        playerHits = pHits, doubleHitTicks = pDoubleHitTicks, enemyHits = eHits, playerHeals = pHeals, hpAfter = currentHp,
                        foodConsumed  = frameFood,
                        arrowsConsumed = frameArrows,
                        runesConsumed  = if (runeKey != null && frameRunesUsed > 0) mapOf(runeKey to frameRunesUsed * runeCostPerAttack) else emptyMap(),
                        maxHp          = maxHp,
                        foodAtStart    = if (frames.isEmpty()) equippedFood else emptyMap(),
                        statsAtStart   = if (frames.isEmpty()) statsAtStart else emptyMap(),
                        allyHits       = aHits,
                        alliesDown     = mercHp.count { it <= 0 },
                        allyHpAfter    = mercHp.toList(),
                    ))
                    break@outer
                }

                // Boss attacks on a fixed 2.4s cadence, independent of player speed.
                // In a raid each attack targets a uniformly random living party member.
                bossClock += speed
                var bDmg = 0
                while (bossClock >= BASE_ATTACK_SPEED_SEC - 1e-9) {
                    bossClock -= BASE_ATTACK_SPEED_SEC
                    if (mercenaries.isEmpty()) {
                        if (rnd.nextDouble() < bossHitChance) bDmg += rnd.nextInt(0, bossMax + 1)
                    } else {
                        val livingMercs = mercenaries.indices.filter { mercHp[it] > 0 }
                        val target = rnd.nextInt(1 + livingMercs.size)
                        if (target == 0) {
                            if (rnd.nextDouble() < bossHitChance) bDmg += rnd.nextInt(0, bossMax + 1)
                        } else {
                            val mi = livingMercs[target - 1]
                            if (rnd.nextDouble() < bossHitChanceVsMerc[mi]) {
                                mercHp[mi] = (mercHp[mi] - rnd.nextInt(0, bossMax + 1)).coerceAtLeast(0)
                            }
                        }
                    }
                }
                currentHp = (currentHp - bDmg).coerceAtLeast(0)
                eHits.add(bDmg)

                // Death is checked before the eating phase so food cannot revive a player
                // already at 0 HP (issue #1412).
                if (currentHp <= 0) {
                    pHeals.add(0)
                    frames.add(SessionFrame(
                        minute = frames.size, xpGain = 0, xpBefore = 0L, xpAfter = 0L,
                        levelBefore = 0, levelAfter = 0,
                        kills = 0, enemyKey = bossKey,
                        playerHits = pHits, doubleHitTicks = pDoubleHitTicks, enemyHits = eHits, playerHeals = pHeals, hpAfter = 0,
                        foodConsumed  = frameFood,
                        arrowsConsumed = frameArrows,
                        runesConsumed  = if (runeKey != null && frameRunesUsed > 0) mapOf(runeKey to frameRunesUsed * runeCostPerAttack) else emptyMap(),
                        maxHp          = maxHp,
                        foodAtStart    = if (frames.isEmpty()) equippedFood else emptyMap(),
                        statsAtStart   = if (frames.isEmpty()) statsAtStart else emptyMap(),
                        allyHits       = aHits,
                        alliesDown     = mercHp.count { it <= 0 },
                        allyHpAfter    = mercHp.toList(),
                    ))
                    break@outer
                }

                // Eats food based on the selected order, and move to next in list
                // when the current selected one runs out, up to 300 items total.
                val hpBeforeEating = currentHp
                var ate = true
                while (ate && totalFoodEaten < 300) {
                    ate = false
                    val foodKey = foodOrder.firstOrNull { (foodSupply[it] ?: 0) > 0 } ?: break
                    val heal = foodHealValues[foodKey] ?: break
                    if (currentHp <= bossMax || currentHp <= maxHp * eatThresholdFraction) {
                        currentHp            = minOf(maxHp, currentHp + heal)
                        foodSupply[foodKey]  = (foodSupply[foodKey] ?: 0) - 1
                        frameFood[foodKey]   = (frameFood[foodKey] ?: 0) + 1
                        totalFoodEaten++
                        ate = true
                    }
                }
                pHeals.add(currentHp - hpBeforeEating)
            }

            if (frames.size < maxFrames && currentHp > 0 && currentBossHp > 0) {
                frames.add(SessionFrame(
                    minute = frames.size, xpGain = 0, xpBefore = 0L, xpAfter = 0L,
                    levelBefore = 0, levelAfter = 0,
                    kills = 0, enemyKey = bossKey,
                    playerHits = pHits, doubleHitTicks = pDoubleHitTicks, enemyHits = eHits, playerHeals = pHeals, hpAfter = currentHp,
                    foodConsumed  = frameFood,
                    arrowsConsumed = frameArrows,
                    runesConsumed  = if (runeKey != null && frameRunesUsed > 0) mapOf(runeKey to frameRunesUsed * runeCostPerAttack) else emptyMap(),
                    maxHp          = maxHp,
                    foodAtStart    = if (frames.isEmpty()) equippedFood else emptyMap(),
                    statsAtStart   = if (frames.isEmpty()) statsAtStart else emptyMap(),
                    allyHits       = aHits,
                    alliesDown     = mercHp.count { it <= 0 },
                    allyHpAfter    = mercHp.toList(),
                ))
            }
        }

        // DPS fallback if the frame cap was hit with neither side dead.
        if (frames.isEmpty() || (frames.last().kills == 0 && currentBossHp > 0 && currentHp > 0)) {
            val mercDps = mercenaries.indices.sumOf { i ->
                (mercenaries[i].maxHit / 2.0) * mercHitChance[i] / BASE_ATTACK_SPEED_SEC
            }
            val partyHp   = maxHp + mercenaries.sumOf { it.hpLevel * 10 }
            val playerDps = (playerMax / 2.0) * playerHitChance / speed + mercDps
            val bossDps   = (bossMax / 2.0) * bossHitChance / BASE_ATTACK_SPEED_SEC
            won = if (playerDps > 0 && bossDps > 0) {
                (boss.hp / playerDps) <= (partyHp / bossDps)
            } else playerDps >= bossDps
            val stub = SessionFrame(
                minute = frames.size, xpGain = 0, xpBefore = 0L, xpAfter = 0L,
                levelBefore = 0, levelAfter = 0,
                kills = if (won) 1 else 0, enemyKey = bossKey, hpAfter = if (won) 1 else 0,
                maxHp = maxHp,
                foodAtStart = if (frames.size <= 1) equippedFood else emptyMap(),
                statsAtStart = if (frames.size <= 1) statsAtStart else emptyMap(),
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
                if (rare.item !in blockedRareDrops && rnd.nextDouble() < rare.chance)
                    items[rare.item] = (items[rare.item] ?: 0) + 1
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
            kills        = if (won) 1 else 0,
            killsByEnemy = if (won) mapOf(bossKey to 1) else emptyMap(),
            combatStyle  = combatStyle,
        )

        return frames
    }

    /** Ticks per 60-second frame at the base attack speed (one attack every 2.4 s). */
    const val TICKS_PER_FRAME = 25

    /** Default/enemy attack speed in seconds; player weapons may attack faster via their attackSpeed field. */
    const val BASE_ATTACK_SPEED_SEC = 2.4

    /** Full raid party size (player + MercenaryRepository.MAX_PARTY mercenaries); raid bosses scale up against smaller parties. */
    const val RAID_FULL_PARTY = 4

    /** Number of player attack ticks in a 60-second frame at the given attack speed. */
    fun playerTicksPerFrame(attackSpeedSec: Double): Int = (60.0 / attackSpeedSec).roundToInt()

    /**
     * True ticks-per-frame of a simulated session, derived from its largest frame. A fight
     * that ended mid-minute leaves a partial final frame; pacing playback (or alarms) by
     * that frame's own tick count would stretch its few hits across the whole minute
     * (issue #935). Floored at [TICKS_PER_FRAME] for fights shorter than one full frame.
     */
    fun fullFrameTicks(frames: List<SessionFrame>): Int =
        (frames.maxOfOrNull { maxOf(it.playerHits.size, it.enemyHits.size) } ?: 0)
            .coerceAtLeast(TICKS_PER_FRAME)

    /**
     * Wall-clock offset at which a boss fight is actually decided (boss or player dead),
     * plus a 2 s buffer so the final blow stays on screen briefly. Null when the fight
     * ran the full duration, meaning the session should end at its normal endsAt.
     */
    fun bossEndAlarmOffsetMs(frames: List<SessionFrame>, durationMinutes: Int, perFrameMs: Long): Long? {
        if (frames.size >= durationMinutes) return null
        val lastTicks   = frames.lastOrNull()?.let { maxOf(it.playerHits.size, it.enemyHits.size) } ?: 0
        val tickMs      = (perFrameMs / fullFrameTicks(frames)).coerceAtLeast(1L)
        val lastFrameMs = if (lastTicks > 0) (lastTicks * tickMs).coerceAtMost(perFrameMs) else perFrameMs
        return (frames.size - 1).coerceAtLeast(0) * perFrameMs + lastFrameMs + 2_000L
    }

    /**
     * Wall-clock offset at which the player died mid-session (dungeon/tower), plus a 2 s
     * buffer, so the session ends at the death tick instead of the end of that minute.
     * Null when the player survived.
     */
    fun deathAlarmOffsetMs(frames: List<SessionFrame>, perFrameMs: Long): Long? {
        val deathFrameIdx = frames.indexOfFirst { it.died }
        if (deathFrameIdx < 0) return null
        val deathFrame = frames[deathFrameIdx]
        val deathTicks = maxOf(deathFrame.playerHits.size, deathFrame.enemyHits.size)
        val tickMs     = (perFrameMs / fullFrameTicks(frames)).coerceAtLeast(1L)
        val deathMs    = if (deathTicks > 0) (deathTicks * tickMs).coerceAtMost(perFrameMs) else perFrameMs
        return deathFrameIdx * perFrameMs + deathMs + 2_000L
    }

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
