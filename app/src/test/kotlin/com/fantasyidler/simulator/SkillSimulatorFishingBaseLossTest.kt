package com.fantasyidler.simulator

import com.fantasyidler.data.json.FishData
import com.fantasyidler.data.json.GatheringSkillData
import com.fantasyidler.data.json.SkillDropEntry
import com.fantasyidler.data.json.XpRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * RED para problema 1: pesca pierde el pez base cuando la tabla acierta.
 *
 * Con rodEfficiency=1 cada frame produce fishQty=1. Si la tabla tier acierta,
 * el pez base debe seguir estando (base + bonus, no bonus en vez de base).
 */
class SkillSimulatorFishingBaseLossTest {

    private fun fish(xp: Int) = FishData(
        displayName = "Trout",
        levelRequired = 1,
        xpPerCatch = xp,
        timePerCatch = 1,
    )

    private fun fishingSkillWithGuaranteedBonus(): GatheringSkillData =
        GatheringSkillData(
            name = "fishing",
            displayName = "Fishing",
            description = "",
            xpRanges = mapOf("1" to XpRange(1, 1)),
            dropTables = mapOf(
                "1" to listOf(SkillDropEntry(item = "bonus_item", chance = 1.0)),
            ),
        )

    @Test
    fun `fishing always grants base fish even when drop table hits`() {
        val result = SkillSimulator.simulateFishing(
            fishKey = "trout",
            fishData = fish(12),
            startXp = 0,
            fishingSkillData = fishingSkillWithGuaranteedBonus(),
            random = Random(1234),
        )

        assertEquals(60, result.frames.size)

        // Cada frame debe contener al menos 1 trucha base (fishQty=1 con rod=1.0).
        assertTrue(
            "pez base perdido cuando la tabla acierta",
            result.frames.all { (it.items["trout"] ?: 0) >= 1 },
        )

        // Suma total: 60 truchas base en 60 frames.
        val totalTrout = result.frames.sumOf { it.items["trout"] ?: 0 }
        assertEquals(60, totalTrout)
    }
}
