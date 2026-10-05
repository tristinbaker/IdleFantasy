package com.fantasyidler.simulator

import com.fantasyidler.data.json.AgilityCourseData
import com.fantasyidler.data.json.FishData
import com.fantasyidler.data.json.GatheringSkillData
import com.fantasyidler.data.json.GemData
import com.fantasyidler.data.json.OreData
import com.fantasyidler.data.json.TreeData
import com.fantasyidler.data.model.SessionFrame
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Pre-simulates all 60 frames of a gathering skill session.
 *
 * All three gathering simulators (mining, woodcutting, fishing) follow the
 * same pattern from the Python source:
 *   1. For each of 60 minutes, determine XP gain and item drops.
 *   2. Track level-ups as they happen within the session.
 *   3. Apply the agility reduction to compute the actual wall-clock duration.
 *
 * The caller is responsible for serialising [Result.frames] to JSON and passing
 * it to [SessionRepository.startSession].
 */
object SkillSimulator {

    data class Result(
        val frames: List<SessionFrame>,
        /** Wall-clock duration in milliseconds, reduced by agility bonus. */
        val durationMs: Long,
    )

    // ------------------------------------------------------------------
    // Mining — picks a specific ore; bonus gem drops rolled independently
    // ------------------------------------------------------------------

    /**
     * @param oreKey       asset key, e.g. "iron_ore"
     * @param oreData      loaded ore definition
     * @param gems         loaded gems.json (all gems, for bonus drop rolls)
     * @param startXp      player's current mining XP
     * @param agilityLevel player's agility level (for duration reduction)
     * @param petBoostPct  XP boost percent from an equipped mining pet (0 = none)
     * @param toolEfficiency  pickaxe multiplier: 1.0 = base, 1.5 = +50% ores/min
     * @param gemChanceMult multiplier on gem drop rates (mining Gem Hunter prestige nodes)
     */
    fun simulateMining(
        oreKey: String,
        oreData: OreData,
        gems: Map<String, GemData>,
        startXp: Long,
        agilityLevel: Int = 1,
        floorReductionMin: Double = 0.0,
        petBoostPct: Int = 0,
        toolEfficiency: Float = 1.0f,
        petDropKey: String? = null,
        petDropChance: Double = 0.0,
        chronosMultiplier: Float = 1.0f,
        gemChanceMult: Double = 1.0,
        random: Random = Random.Default,
    ): Result {
        var currentXp = startXp
        val frames = mutableListOf<SessionFrame>()
        var oreAccumulator = 0f

        for (minute in 1..60) {
            val xpBefore = currentXp
            val levelBefore = XpTable.levelForXp(currentXp)

            val baseXp = (oreData.xpPerOre * toolEfficiency).toInt()
            val xpGain = applyPetBoost(baseXp, petBoostPct)

            currentXp += xpGain
            val levelAfter = XpTable.levelForXp(currentXp)

            oreAccumulator += toolEfficiency
            val oreQty = oreAccumulator.toInt().coerceAtLeast(1)
            oreAccumulator -= oreQty
            val items = mutableMapOf(oreKey to oreQty)

            // Bonus gem rolls — one independent roll per ore mined per gem type
            for (i in 0 until oreQty) {
                for ((gemKey, gemData) in gems) {
                    if (random.nextDouble() < gemData.dropRate * gemChanceMult) {
                        items[gemKey] = (items[gemKey] ?: 0) + 1
                    }
                }
            }
            if (petDropKey != null && petDropChance > 0.0 && random.nextDouble() < petDropChance) {
                items[petDropKey] = 1
            }

            frames.add(
                SessionFrame(
                    minute = minute,
                    xpGain = xpGain,
                    xpBefore = xpBefore,
                    xpAfter = currentXp,
                    levelBefore = levelBefore,
                    levelAfter = levelAfter,
                    items = items,
                    leveledUp = levelAfter > levelBefore,
                )
            )
        }

        return Result(frames, sessionDurationMs(agilityLevel, floorReductionMin, chronosMultiplier))
    }

    // ------------------------------------------------------------------
    // Woodcutting — picks a specific tree; always produces that tree's log
    // ------------------------------------------------------------------

    /**
     * @param treeData     loaded tree definition
     * @param startXp      player's current woodcutting XP
     * @param toolEfficiency  axe multiplier: 1.0 = base, 1.5 = +50% logs/min
     */
    fun simulateWoodcutting(
        treeData: TreeData,
        startXp: Long,
        agilityLevel: Int = 1,
        floorReductionMin: Double = 0.0,
        petBoostPct: Int = 0,
        toolEfficiency: Float = 1.0f,
        petDropKey: String? = null,
        petDropChance: Double = 0.0,
        chronosMultiplier: Float = 1.0f,
        random: Random = Random.Default,
    ): Result {
        var currentXp = startXp
        val frames = mutableListOf<SessionFrame>()
        var logAccumulator = 0f

        for (minute in 1..60) {
            val xpBefore = currentXp
            val levelBefore = XpTable.levelForXp(currentXp)

            val baseXp = (treeData.xpPerLog * toolEfficiency).toInt()
            val xpGain = applyPetBoost(baseXp, petBoostPct)

            currentXp += xpGain
            val levelAfter = XpTable.levelForXp(currentXp)

            logAccumulator += toolEfficiency
            val logQty = logAccumulator.toInt().coerceAtLeast(1)
            logAccumulator -= logQty
            val items = mutableMapOf(treeData.logName to logQty)
            if (petDropKey != null && petDropChance > 0.0 && random.nextDouble() < petDropChance) {
                items[petDropKey] = 1
            }

            frames.add(
                SessionFrame(
                    minute = minute,
                    xpGain = xpGain,
                    xpBefore = xpBefore,
                    xpAfter = currentXp,
                    levelBefore = levelBefore,
                    levelAfter = levelAfter,
                    items = items,
                    leveledUp = levelAfter > levelBefore,
                )
            )
        }

        return Result(frames, sessionDurationMs(agilityLevel, floorReductionMin, chronosMultiplier))
    }

    // ------------------------------------------------------------------
    // Fishing — picks a specific fish target (mirrors mining)
    // ------------------------------------------------------------------

    fun simulateFishing(
        fishKey: String,
        fishData: FishData,
        startXp: Long,
        agilityLevel: Int = 1,
        floorReductionMin: Double = 0.0,
        petBoostPct: Int = 0,
        rodEfficiency: Float = 1.0f,
        petDropKey: String? = null,
        petDropChance: Double = 0.0,
        fishingSkillData: GatheringSkillData? = null,
        chronosMultiplier: Float = 1.0f,
        random: Random = Random.Default,
    ): Result {
        var currentXp = startXp
        val frames = mutableListOf<SessionFrame>()
        var fishAccumulator = 0f

        for (minute in 1..60) {
            val xpBefore    = currentXp
            val levelBefore = XpTable.levelForXp(currentXp)

            val baseXp = (fishData.xpPerCatch * rodEfficiency).toInt()
            val xpGain = applyPetBoost(baseXp, petBoostPct)

            currentXp += xpGain
            val levelAfter = XpTable.levelForXp(currentXp)

            fishAccumulator += rodEfficiency
            val fishQty = fishAccumulator.toInt().coerceAtLeast(1)
            fishAccumulator -= fishQty
            val items = mutableMapOf(fishKey to fishQty)
            if (fishingSkillData != null && random.nextDouble() > 0.8) {
                val dropTable = getTierData(fishingSkillData.dropTables, levelBefore)
                for (entry in dropTable) {
                    if (random.nextDouble() < entry.chance) {
                        items[entry.item] = (items[entry.item] ?: 0) + 1
                    }
                }
            }
            if (petDropKey != null && petDropChance > 0.0 && random.nextDouble() < petDropChance) {
                items[petDropKey] = 1
            }

            frames.add(
                SessionFrame(
                    minute      = minute,
                    xpGain      = xpGain,
                    xpBefore    = xpBefore,
                    xpAfter     = currentXp,
                    levelBefore = levelBefore,
                    levelAfter  = levelAfter,
                    items       = items,
                    leveledUp   = levelAfter > levelBefore,
                )
            )
        }

        return Result(frames, sessionDurationMs(agilityLevel, floorReductionMin, chronosMultiplier))
    }

    // ------------------------------------------------------------------
    // Generic gathering — uses xp_ranges + drop_tables from skill JSON
    // ------------------------------------------------------------------
    // Note: Function has been deprecated and is only used by tests - should likely be removed

    /**
     * @param skillData    loaded gathering skill data with xp_ranges + drop_tables
     * @param startXp      player's current XP in this skill
     * @param toolEfficiency  rod multiplier for fishing: multiplies XP gain
     */
    fun simulateGathering(
        skillData: GatheringSkillData,
        startXp: Long,
        agilityLevel: Int = 1,
        floorReductionMin: Double = 0.0,
        petBoostPct: Int = 0,
        toolEfficiency: Float = 1.0f,
        petDropKey: String? = null,
        petDropChance: Double = 0.0,
        forcedDropPerFrame: String? = null,
        chronosMultiplier: Float = 1.0f,
        random: Random = Random.Default,
    ): Result {
        var currentXp = startXp
        val frames = mutableListOf<SessionFrame>()

        for (minute in 1..60) {
            val xpBefore = currentXp
            val levelBefore = XpTable.levelForXp(currentXp)

            val xpRange = getTierData(skillData.xpRanges, levelBefore)
            val baseXp = (random.nextInt(xpRange.min, xpRange.max + 1) * toolEfficiency).toInt()
            val xpGain = applyPetBoost(baseXp, petBoostPct)

            currentXp += xpGain
            val levelAfter = XpTable.levelForXp(currentXp)

            // Roll drops from level-appropriate table, using level BEFORE xp gain
            val dropTable = if (skillData.dropTables.isEmpty()) emptyList()
                            else getTierData(skillData.dropTables, levelBefore)
            val items = mutableMapOf<String, Int>()
            for (entry in dropTable) {
                if (random.nextDouble() < entry.chance) {
                    items[entry.item] = (items[entry.item] ?: 0) + 1
                }
            }
            if (forcedDropPerFrame != null) {
                items[forcedDropPerFrame] = (items[forcedDropPerFrame] ?: 0) + 1
            }
            if (petDropKey != null && petDropChance > 0.0 && random.nextDouble() < petDropChance) {
                items[petDropKey] = 1
            }

            frames.add(
                SessionFrame(
                    minute = minute,
                    xpGain = xpGain,
                    xpBefore = xpBefore,
                    xpAfter = currentXp,
                    levelBefore = levelBefore,
                    levelAfter = levelAfter,
                    items = items,
                    leveledUp = levelAfter > levelBefore,
                )
            )
        }

        return Result(frames, sessionDurationMs(agilityLevel, floorReductionMin, chronosMultiplier))
    }

    // ------------------------------------------------------------------
    // Agility — course-based, success/fail per lap
    // ------------------------------------------------------------------

    /**
     * Simulates a 60-minute agility session on [courseData].
     *
     * Each minute the player attempts [LAPS_PER_MINUTE] laps.
     * Success probability scales from ~60% at the minimum required level
     * up to ~95% as the player's level overtakes the course requirement.
     *
     * Failed laps grant no XP and are tracked via [SessionFrame.success].
     *
     * @param toolEfficiency  grappling hook multiplier: 1.0 = base, 1.5 = +50% laps/min
     */
    fun simulateAgility(
        courseData: AgilityCourseData,
        startXp: Long,
        agilityLevel: Int = 1,
        floorReductionMin: Double = 0.0,
        petBoostPct: Int = 0,
        toolEfficiency: Float = 1.0f,
        petDropKey: String? = null,
        petDropChance: Double = 0.0,
        chronosMultiplier: Float = 1.0f,
        random: Random = Random.Default,
    ): Result {
        var currentXp = startXp
        val frames = mutableListOf<SessionFrame>()

        val successRate = (0.80 + (agilityLevel - courseData.levelRequired) * 0.02)
            .coerceAtMost(0.95)
        val lapsPerMinute = (LAPS_PER_MINUTE * toolEfficiency).roundToInt().coerceAtLeast(1)

        for (minute in 1..60) {
            val xpBefore    = currentXp
            val levelBefore = XpTable.levelForXp(currentXp)

            val successfulLaps = (0 until lapsPerMinute).count { random.nextDouble() < successRate }
            val baseXp = successfulLaps * courseData.xpPerSuccess
            val xpGain = applyPetBoost(baseXp, petBoostPct)

            currentXp += xpGain
            val levelAfter = XpTable.levelForXp(currentXp)

            val items = mutableMapOf<String, Int>()
            if (petDropKey != null && petDropChance > 0.0 && random.nextDouble() < petDropChance) {
                items[petDropKey] = 1
            }

            frames.add(
                SessionFrame(
                    minute      = minute,
                    xpGain      = xpGain,
                    xpBefore    = xpBefore,
                    xpAfter     = currentXp,
                    levelBefore = levelBefore,
                    levelAfter  = levelAfter,
                    items       = items,
                    leveledUp   = levelAfter > levelBefore,
                    success     = successfulLaps > 0,
                )
            )
        }

        return Result(frames, sessionDurationMs(agilityLevel, floorReductionMin, chronosMultiplier))
    }

    // ------------------------------------------------------------------
    // Session XP estimation — used for pre-session preview in the UI
    // ------------------------------------------------------------------

    /** Estimated total XP for a 60-frame gathering session (mining/woodcutting/fishing). */
    fun estimateGatheringXp(xpPerAction: Int, efficiency: Float = 1f): Long =
        (xpPerAction * efficiency).toLong() * 60L

    /** Estimated total item yield for a 60-frame gathering session. */
    fun estimateGatheringQty(efficiency: Float = 1f): Int =
        max(1, efficiency.roundToInt()) * 60

    /** Estimated total XP for a 60-frame agility session. */
    fun estimateAgilityXp(xpPerSuccess: Int, levelRequired: Int, currentAgilityLevel: Int, toolEfficiency: Float = 1f): Long {
        val successRate = (0.80 + (currentAgilityLevel - levelRequired) * 0.02).coerceAtMost(0.95)
        val lapsPerMinute = (LAPS_PER_MINUTE * toolEfficiency).roundToInt().coerceAtLeast(1)
        return (lapsPerMinute * successRate * xpPerSuccess).toLong() * 60L
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private const val LAPS_PER_MINUTE = 2

    /**
     * Returns the duration of a session in milliseconds, applying the agility
     * speed bonus. Sessions scale linearly from 60 min at level 1 to 40 min at level 99.
     * [floorReductionMin] (agility Endurance prestige nodes) lowers the level-99
     * floor by up to that many minutes, scaled by level like the base bonus.
     *
     * Examples (floorReductionMin=0):
     *   level  1 → 60 min
     *   level 25 → 55 min
     *   level 50 → 50 min
     *   level 75 → 45 min
     *   level 99 → 40 min
     */
    fun sessionDurationMs(agilityLevel: Int, floorReductionMin: Double = 0.0, chronosMultiplier: Float = 1.0f): Long {
        val fraction = (agilityLevel - 1).coerceIn(0, 98) / 98.0
        val maxReduction = 20.0 + floorReductionMin.coerceIn(0.0, 10.0)
        val minutes = (60.0 - maxReduction * fraction) * chronosMultiplier.coerceIn(0.5f, 1.0f)
        // Millisecond precision: rounding to whole minutes swallowed the 2% Chronos Spire
        // reduction entirely at many agility levels (issue #1486).
        return (minutes * 60_000.0).roundToLong().coerceAtLeast(60_000L)
    }

    /**
     * Elder Isle session duration: 60 min at elder-agility 1, scaling linearly to 45 min at
     * elder-agility 99. Mainland agility, prestige floor, and Chronos Spire are all ignored
     * — the isle explicitly walls off mainland duration bonuses so isle time-to-reward stays
     * predictable and elder Agility is the only lever.
     */
    fun elderSessionDurationMs(elderAgilityLevel: Int): Long {
        val fraction = (elderAgilityLevel - 1).coerceIn(0, 98) / 98.0
        val minutes = 60.0 - 15.0 * fraction
        return (minutes * 60_000.0).roundToLong().coerceAtLeast(45L * 60_000L)
    }

    /**
     * Finds the tier entry whose key (as an Int) is the highest value ≤ [currentLevel].
     * Keys are the string-encoded level thresholds used in xp_ranges / drop_tables.
     */
    private fun <T> getTierData(tiers: Map<String, T>, currentLevel: Int): T {
        val sorted = tiers.keys.mapNotNull { it.toIntOrNull() }.sorted()
        val threshold = sorted.lastOrNull { it <= currentLevel } ?: sorted.first()
        return tiers[threshold.toString()]!!
    }

    /** Apply a pet XP boost percentage to a raw XP gain value. */
    private fun applyPetBoost(baseXp: Int, petBoostPct: Int): Int =
        if (petBoostPct > 0) (baseXp * (1.0 + petBoostPct / 100.0)).toInt() else baseXp
}
