package com.fantasyidler.ui.screen.shop

import java.time.LocalDate
import kotlin.random.Random

/**
 * Daily-rotating shop discounts. Same seed = same picks for everyone on a given
 * day (and the same player launching twice on the same day sees the same set).
 *
 * Behaviour:
 * - Picks [MIN_PICKS]..[MAX_PICKS] eligible items.
 * - Each pick gets a percentage discount rolled uniformly in [MIN_OFF_PCT, MAX_OFF_PCT].
 * - The effective price is `originalPrice * (100 - percentOff) / 100`, floored to a
 *   minimum of 1 so nothing becomes free.
 *
 * Pure / stateless on purpose — fully unit-testable.
 */
object DailySaleRoller {

    data class Sale(val percentOff: Int, val originalPrice: Int, val salePrice: Int)

    /**
     * @param itemKeys     ordered list of sale-eligible item keys (usually `buyEntries.map { it.key }`
     *                     after filtering out the XP boost / specials).
     * @param priceLookup  maps a key to its original (non-discounted) buy price.
     * @param day          override for tests; defaults to `LocalDate.now()`.
     */
    fun roll(
        itemKeys: List<String>,
        priceLookup: (String) -> Int,
        day: LocalDate = LocalDate.now(),
    ): Map<String, Sale> {
        if (itemKeys.isEmpty()) return emptyMap()

        val rng = Random(day.toEpochDay())
        val picksCount = (MIN_PICKS..MAX_PICKS).random(rng).coerceAtMost(itemKeys.size)

        val shuffled = itemKeys.shuffled(rng).take(picksCount)
        return shuffled.associateWith { key ->
            val originalPrice = priceLookup(key)
            val percentOff = rng.nextInt(MIN_OFF_PCT, MAX_OFF_PCT + 1)
            val salePrice = (originalPrice * (100 - percentOff) / 100).coerceAtLeast(1)
            Sale(percentOff = percentOff, originalPrice = originalPrice, salePrice = salePrice)
        }
    }

    const val MIN_PICKS = 3
    const val MAX_PICKS = 5
    const val MIN_OFF_PCT = 15
    const val MAX_OFF_PCT = 35
}
