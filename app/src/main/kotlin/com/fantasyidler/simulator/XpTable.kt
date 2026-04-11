package com.fantasyidler.simulator

import kotlin.math.floor
import kotlin.math.pow

/**
 * Classic idle RPG XP progression table for levels 1–99.
 *
 * Formula: XP(n) = floor( (1/4) * Σ_{k=1}^{n-1} floor(k + 300 * 2^(k/7)) )
 *
 * Known anchors (verified against IdleApes xp_table.json):
 *   Level  1  →       0 XP
 *   Level  2  →      83 XP
 *   Level 50  → 101,333 XP
 *   Level 99  → 13,034,431 XP
 */
object XpTable {

    /**
     * xpRequirements[level] = total XP required to be AT that level.
     * Index 0 is unused. Valid range: 1–99.
     */
    val xpRequirements: LongArray = buildTable()

    private fun buildTable(): LongArray {
        val table = LongArray(100)          // table[0] unused, table[1..99] valid
        var accumulator = 0.0
        for (k in 1..98) {
            accumulator += floor(k + 300.0 * 2.0.pow(k / 7.0))
            table[k + 1] = (accumulator / 4).toLong()
        }
        return table
    }

    // ------------------------------------------------------------------

    /** XP threshold for [level]. Clamped to the valid 1–99 range. */
    fun xpForLevel(level: Int): Long =
        xpRequirements[level.coerceIn(1, 99)]

    /** Current level for [xp] total XP. Returns 1–99. */
    fun levelForXp(xp: Long): Int {
        var level = 1
        while (level < 99 && xp >= xpRequirements[level + 1]) level++
        return level
    }

    /** XP still needed to reach the next level. Returns 0 at level 99. */
    fun xpToNextLevel(currentXp: Long): Long {
        val level = levelForXp(currentXp)
        return if (level >= 99) 0L else xpRequirements[level + 1] - currentXp
    }

    /** XP at which the next level begins (useful for displaying the target). */
    fun nextLevelThreshold(currentXp: Long): Long {
        val level = levelForXp(currentXp)
        return if (level >= 99) xpRequirements[99] else xpRequirements[level + 1]
    }

    /**
     * Progress through the current level band as a fraction in [0.0, 1.0].
     * Returns 1.0 at level 99.
     */
    fun progressFraction(currentXp: Long): Float {
        val level = levelForXp(currentXp)
        if (level >= 99) return 1f
        val start = xpRequirements[level]
        val end   = xpRequirements[level + 1]
        return ((currentXp - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    }
}
