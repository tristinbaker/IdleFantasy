package com.fantasyidler.simulator

import com.fantasyidler.data.json.AgilityCourseData
import com.fantasyidler.data.json.GatheringSkillData
import com.fantasyidler.data.json.GemData
import com.fantasyidler.data.json.OreData
import com.fantasyidler.data.json.TreeData
import com.fantasyidler.data.model.SessionFrame
import kotlin.math.max
import kotlin.math.roundToInt
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
     */
    fun simulateMining(
        oreKey: String,
        oreData: OreData,
        gems: Map<String, GemData>,
        startXp: Long,
        agilityLevel: Int = 1,
        petBoostPct: Int = 0,
        toolEfficiency: Float = 1.0f,
        petDropKey: String? = null,
        petDropChance: Double = 0.0,
    ): Result {
        var currentXp = startXp
        val frames = mutableListOf<SessionFrame>()

        for (minute in 1..60) {
            val xpBefore = currentXp
            val levelBefore = XpTable.levelForXp(currentXp)

            val baseXp = (oreData.xpPerOre * toolEfficiency).toInt()
            val xpGain = applyPetBoost(baseXp, petBoostPct)

            currentXp += xpGain
            val levelAfter = XpTable.levelForXp(currentXp)

            // Ore quantity this minute
            val oreQty = max(1, toolEfficiency.roundToInt())
            val items = mutableMapOf(oreKey to oreQty)

            // Bonus gem rolls — one independent roll per ore mined per gem type
            for (i in 0 until oreQty) {
                for ((gemKey, gemData) in gems) {
                    if (Random.nextDouble() < gemData.dropRate) {
                        items[gemKey] = (items[gemKey] ?: 0) + 1
                    }
                }
            }
            if (petDropKey != null && petDropChance > 0.0 && Random.nextDouble() < petDropChance) {
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

        return Result(frames, sessionDurationMs(agilityLevel))
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
        petBoostPct: Int = 0,
        toolEfficiency: Float = 1.0f,
        petDropKey: String? = null,
        petDropChance: Double = 0.0,
    ): Result {
        var currentXp = startXp
        val frames = mutableListOf<SessionFrame>()

        for (minute in 1..60) {
            val xpBefore = currentXp
            val levelBefore = XpTable.levelForXp(currentXp)

            val baseXp = (treeData.xpPerLog * toolEfficiency).toInt()
            val xpGain = applyPetBoost(baseXp, petBoostPct)

            currentXp += xpGain
            val levelAfter = XpTable.levelForXp(currentXp)

            val logQty = max(1, toolEfficiency.roundToInt())
            val items = mutableMapOf(treeData.logName to logQty)
            if (petDropKey != null && petDropChance > 0.0 && Random.nextDouble() < petDropChance) {
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

        return Result(frames, sessionDurationMs(agilityLevel))
    }

    // ------------------------------------------------------------------
    // Generic gathering — uses xp_ranges + drop_tables from skill JSON
    // Used by Fishing (and any future skill that doesn't pick a specific target)
    // ------------------------------------------------------------------

    /**
     * @param skillData    loaded gathering skill data with xp_ranges + drop_tables
     * @param startXp      player's current XP in this skill
     * @param toolEfficiency  rod multiplier for fishing: multiplies XP gain
     */
    fun simulateGathering(
        skillData: GatheringSkillData,
        startXp: Long,
        agilityLevel: Int = 1,
        petBoostPct: Int = 0,
        toolEfficiency: Float = 1.0f,
        petDropKey: String? = null,
        petDropChance: Double = 0.0,
    ): Result {
        var currentXp = startXp
        val frames = mutableListOf<SessionFrame>()

        for (minute in 1..60) {
            val xpBefore = currentXp
            val levelBefore = XpTable.levelForXp(currentXp)

            val xpRange = getTierData(skillData.xpRanges, levelBefore)
            val baseXp = (Random.nextInt(xpRange.min, xpRange.max + 1) * toolEfficiency).toInt()
            val xpGain = applyPetBoost(baseXp, petBoostPct)

            currentXp += xpGain
            val levelAfter = XpTable.levelForXp(currentXp)

            // Roll drops from level-appropriate table, using level BEFORE xp gain
            val dropTable = if (skillData.dropTables.isEmpty()) emptyList()
                            else getTierData(skillData.dropTables, levelBefore)
            val items = mutableMapOf<String, Int>()
            for (entry in dropTable) {
                if (Random.nextDouble() < entry.chance) {
                    items[entry.item] = (items[entry.item] ?: 0) + 1
                }
            }
            if (petDropKey != null && petDropChance > 0.0 && Random.nextDouble() < petDropChance) {
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

        return Result(frames, sessionDurationMs(agilityLevel))
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
     */
    fun simulateAgility(
        courseData: AgilityCourseData,
        startXp: Long,
        agilityLevel: Int = 1,
        petBoostPct: Int = 0,
    ): Result {
        var currentXp = startXp
        val frames = mutableListOf<SessionFrame>()

        val successRate = (0.60 + (agilityLevel - courseData.levelRequired) * 0.02)
            .coerceIn(0.40, 0.95)

        for (minute in 1..60) {
            val xpBefore    = currentXp
            val levelBefore = XpTable.levelForXp(currentXp)

            val successfulLaps = (0 until LAPS_PER_MINUTE).count { Random.nextDouble() < successRate }
            val baseXp = successfulLaps * courseData.xpPerSuccess
            val xpGain = applyPetBoost(baseXp, petBoostPct)

            currentXp += xpGain
            val levelAfter = XpTable.levelForXp(currentXp)

            frames.add(
                SessionFrame(
                    minute      = minute,
                    xpGain      = xpGain,
                    xpBefore    = xpBefore,
                    xpAfter     = currentXp,
                    levelBefore = levelBefore,
                    levelAfter  = levelAfter,
                    leveledUp   = levelAfter > levelBefore,
                    success     = successfulLaps > 0,
                )
            )
        }

        return Result(frames, sessionDurationMs(agilityLevel))
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private const val LAPS_PER_MINUTE = 4

    /**
     * Returns the duration of a session in milliseconds, applying the agility
     * speed bonus: 5% reduction per 10 agility levels, capped at 45% (level 90+).
     *
     * Examples:
     *   level  1 → 60 min (0% reduction)
     *   level 10 → 57 min (5%)
     *   level 50 → 45 min (25%)
     *   level 99 → 33 min (45%)
     */
    fun sessionDurationMs(agilityLevel: Int): Long {
        val reductionPercent = ((agilityLevel / 10) * 5).coerceAtMost(45)
        val multiplier = (100 - reductionPercent) / 100.0
        val minutes = (60 * multiplier).toInt().coerceAtLeast(1)
        return minutes * 60_000L
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
