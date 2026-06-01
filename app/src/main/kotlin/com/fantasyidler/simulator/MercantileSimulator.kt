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
    ): Result {
        var currentXp = startXp
        val frames = mutableListOf<SessionFrame>()

        val xpRange   = rangeForLevel(XpTable.levelForXp(startXp),   route.xpRanges.mapValues { XpRange(it.value.min, it.value.max) })
        val coinRange = rangeForCoin(XpTable.levelForXp(startXp), route.coinRanges)

        for (minute in 1..60) {
            val xpBefore   = currentXp
            val levelBefore = XpTable.levelForXp(currentXp)
            val xpGain     = Random.nextInt(xpRange.min, xpRange.max + 1)
            currentXp += xpGain
            val levelAfter = XpTable.levelForXp(currentXp)

            val coinReturn = Random.nextInt(coinRange.min, coinRange.max + 1)

            frames += SessionFrame(
                minute      = minute,
                xpGain      = xpGain,
                xpBefore    = xpBefore,
                xpAfter     = currentXp,
                levelBefore = levelBefore,
                levelAfter  = levelAfter,
                leveledUp   = levelAfter > levelBefore,
                items       = mapOf("_coins" to coinReturn),
            )
        }

        return Result(
            frames    = frames,
            durationMs = SkillSimulator.sessionDurationMs(agilityLevel),
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
