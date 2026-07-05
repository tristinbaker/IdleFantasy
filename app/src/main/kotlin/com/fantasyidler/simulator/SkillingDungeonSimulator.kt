package com.fantasyidler.simulator

import com.fantasyidler.data.json.SkillingDungeonData
import com.fantasyidler.data.json.XpRange
import com.fantasyidler.data.json.SkillDropEntry
import com.fantasyidler.data.model.SessionFrame
import kotlin.random.Random

/**
 * Pre-simulates all 60 frames of a skilling dungeon expedition.
 *
 * Follows the same pattern as [SkillSimulator.simulateGathering] but also rolls
 * for a lore note each frame. Note drops are stored in the frame's items map
 * under the key "note_<dungeonKey>" so HomeViewModel can detect and handle them
 * at collect time.
 */
object SkillingDungeonSimulator {

    fun simulate(
        dungeonKey: String,
        dungeon: SkillingDungeonData,
        startXp: Long,
        agilityLevel: Int = 1,
        agilityPrestige: Int = 0,
        toolEfficiency: Float = 1.0f,
        petBoostPct: Int = 0,
        random: Random = Random.Default,
    ): SkillSimulator.Result {
        var currentXp = startXp
        val frames = mutableListOf<SessionFrame>()
        val noteKey = "note_$dungeonKey"

        for (minute in 1..60) {
            val xpBefore = currentXp
            val levelBefore = XpTable.levelForXp(currentXp)

            val xpRange = getTierData(dungeon.xpRanges, levelBefore)
            val baseXp = (random.nextInt(xpRange.min, xpRange.max + 1) * toolEfficiency).toInt()
            val xpGain = if (petBoostPct > 0) (baseXp * (1.0 + petBoostPct / 100.0)).toInt() else baseXp

            currentXp += xpGain
            val levelAfter = XpTable.levelForXp(currentXp)

            val dropTable = if (dungeon.dropTables.isEmpty()) emptyList()
                            else getTierData(dungeon.dropTables, levelBefore)
            val items = mutableMapOf<String, Int>()
            for (entry in dropTable) {
                if (random.nextDouble() < entry.chance) {
                    items[entry.item] = (items[entry.item] ?: 0) + 1
                }
            }

            if (random.nextDouble() < dungeon.noteChancePerFrame) {
                items[noteKey] = (items[noteKey] ?: 0) + 1
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

        return SkillSimulator.Result(frames, SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige))
    }

    private fun <T> getTierData(tiers: Map<String, T>, currentLevel: Int): T {
        val sorted = tiers.keys.mapNotNull { it.toIntOrNull() }.sorted()
        val threshold = sorted.lastOrNull { it <= currentLevel } ?: sorted.first()
        return tiers[threshold.toString()]!!
    }
}
