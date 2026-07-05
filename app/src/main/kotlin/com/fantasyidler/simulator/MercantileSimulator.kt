package com.fantasyidler.simulator

import com.fantasyidler.data.json.CoinRange
import com.fantasyidler.data.json.TradeRouteData
import com.fantasyidler.data.json.XpRange
import com.fantasyidler.data.model.SessionFrame
import kotlin.random.Random

object MercantileSimulator {

    data class Result(
        val frames: List<SessionFrame>,
        val durationMs: Long,
    )

    fun simulate(
        route: TradeRouteData,
        startXp: Long,
        agilityLevel: Int = 1,
        agilityPrestige: Int = 0,
        petDropKey: String? = null,
        petDropChance: Double = 0.0,
        random: Random = Random.Default,
    ): Result {
        var currentXp = startXp
        val frames = mutableListOf<SessionFrame>()

        val xpRange   = rangeForLevel(XpTable.levelForXp(startXp),   route.xpRanges.mapValues { XpRange(it.value.min, it.value.max) })
        val coinRange = rangeForCoin(XpTable.levelForXp(startXp), route.coinRanges)

        val totalCoins = random.nextInt(coinRange.min * 60, coinRange.max * 60 + 1)
        val baseShare = totalCoins / 60
        val remainder = totalCoins % 60

        for (minute in 1..60) {
            val xpBefore   = currentXp
            val levelBefore = XpTable.levelForXp(currentXp)
            val xpGain     = random.nextInt(xpRange.min, xpRange.max + 1)
            currentXp += xpGain
            val levelAfter = XpTable.levelForXp(currentXp)

            val coinReturn = baseShare + if (minute <= remainder) 1 else 0
            val items = mutableMapOf("_coins" to coinReturn)
            if (petDropKey != null && petDropChance > 0.0 && random.nextDouble() < petDropChance) {
                items[petDropKey] = 1
            }

            frames += SessionFrame(
                minute      = minute,
                xpGain      = xpGain,
                xpBefore    = xpBefore,
                xpAfter     = currentXp,
                levelBefore = levelBefore,
                levelAfter  = levelAfter,
                leveledUp   = levelAfter > levelBefore,
                items       = items,
            )
        }

        return Result(
            frames    = frames,
            durationMs = SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige),
        )
    }

    private fun rangeForLevel(level: Int, ranges: Map<String, XpRange>): XpRange {
        val sorted = ranges.keys.mapNotNull { it.toIntOrNull() }.sorted()
        val key = sorted.lastOrNull { it <= level } ?: sorted.firstOrNull() ?: return XpRange(1, 1)
        return ranges[key.toString()] ?: XpRange(1, 1)
    }

    private fun rangeForCoin(level: Int, ranges: Map<String, CoinRange>): CoinRange {
        val sorted = ranges.keys.mapNotNull { it.toIntOrNull() }.sorted()
        val key = sorted.lastOrNull { it <= level } ?: sorted.firstOrNull() ?: return CoinRange(0, 0)
        return ranges[key.toString()] ?: CoinRange(0, 0)
    }
}
