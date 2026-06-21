package com.fantasyidler.simulator

import com.fantasyidler.data.json.AgilityCourseData
import com.fantasyidler.data.json.FishData
import com.fantasyidler.data.json.GatheringSkillData
import com.fantasyidler.data.json.GemData
import com.fantasyidler.data.json.OreData
import com.fantasyidler.data.json.SkillDropEntry
import com.fantasyidler.data.json.TreeData
import com.fantasyidler.data.json.XpRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Outcome tests for the RNG-driven [SkillSimulator] functions, made deterministic
 * via the injected `random` seam.
 *
 * Strategy: where a value has no randomness (fixed per-action XP) or the
 * randomness is pinned to a certain outcome (drop chance 0.0/1.0, single-value
 * XP range), assert exact values; for genuinely stochastic parts assert
 * reproducibility under a fixed seed plus range invariants.
 */
class SkillSimulatorRngTest {

    private fun ore(xp: Int) = OreData(displayName = "Iron", levelRequired = 1, xpPerOre = xp, timePerOre = 1)
    private fun tree(xp: Int) = TreeData(
        displayName = "Oak", logName = "oak_log", logDisplayName = "Oak log",
        levelRequired = 1, xpPerLog = xp, timePerLog = 1,
    )
    private fun fish(xp: Int) = FishData(displayName = "Trout", levelRequired = 1, xpPerCatch = xp, timePerCatch = 1)
    private fun gatheringSkill(range: XpRange, drops: List<SkillDropEntry> = emptyList()) =
        GatheringSkillData(
            name = "mining", displayName = "Mining", description = "",
            xpRanges = mapOf("1" to range),
            dropTables = if (drops.isEmpty()) emptyMap() else mapOf("1" to drops),
        )

    @Test
    fun `mining grants fixed XP per frame and is reproducible under a seed`() {
        val a = SkillSimulator.simulateMining("iron_ore", ore(10), emptyMap(), startXp = 0, random = Random(42))
        val b = SkillSimulator.simulateMining("iron_ore", ore(10), emptyMap(), startXp = 0, random = Random(42))

        assertEquals(60, a.frames.size)
        assertEquals(a.frames, b.frames)                       // same seed -> identical run
        assertTrue(a.frames.all { it.xpGain == 10 })           // no RNG in mining XP
        assertEquals(600L, a.frames.sumOf { it.xpGain.toLong() })
        assertTrue(a.frames.all { (it.items["iron_ore"] ?: 0) == 1 })
    }

    @Test
    fun `mining with fractional tool efficiency accumulates items correctly`() {
        val res = SkillSimulator.simulateMining(
            "iron_ore", ore(10), emptyMap(), startXp = 0,
            toolEfficiency = 1.25f, random = Random(42)
        )
        // Over 60 frames, efficiency 1.25 should yield exactly 75 ores.
        val totalOres = res.frames.sumOf { it.items["iron_ore"] ?: 0 }
        assertEquals(75, totalOres)

        // The pattern of item counts should alternatingly yield 1, 1, 1, 2.
        val quantities = res.frames.map { it.items["iron_ore"] ?: 0 }
        // Verify we only have 1s and 2s
        assertTrue(quantities.all { it in 1..2 })
        // Check the pattern for the first 8 frames: 1, 1, 1, 2, 1, 1, 1, 2
        assertEquals(listOf(1, 1, 1, 2, 1, 1, 1, 2), quantities.take(8))
    }


    @Test
    fun `gem drop chance of 1 always drops and 0 never drops regardless of seed`() {
        val always = GemData("Diamond", "", dropRate = 1.0, rarity = "rare")
        val never = GemData("Diamond", "", dropRate = 0.0, rarity = "rare")

        val withGem = SkillSimulator.simulateMining(
            "iron_ore", ore(10), mapOf("diamond" to always), startXp = 0, random = Random(7),
        )
        val withoutGem = SkillSimulator.simulateMining(
            "iron_ore", ore(10), mapOf("diamond" to never), startXp = 0, random = Random(7),
        )

        assertTrue(withGem.frames.all { (it.items["diamond"] ?: 0) == 1 })
        assertTrue(withoutGem.frames.none { it.items.containsKey("diamond") })
    }

    @Test
    fun `woodcutting and fishing produce their item every frame with fixed XP`() {
        val wc = SkillSimulator.simulateWoodcutting(tree(8), startXp = 0, random = Random(1))
        assertTrue(wc.frames.all { it.xpGain == 8 && (it.items["oak_log"] ?: 0) == 1 })

        // With no fishingSkillData, fishing has no RNG in its item output.
        val f = SkillSimulator.simulateFishing("trout", fish(12), startXp = 0, random = Random(1))
        assertTrue(f.frames.all { it.xpGain == 12 && (it.items["trout"] ?: 0) == 1 })
        assertEquals(720L, f.frames.sumOf { it.xpGain.toLong() })
    }

    @Test
    fun `gathering XP is exact for a single-value range and bounded for a wide range`() {
        // min == max -> XP is deterministic regardless of seed.
        val fixed = SkillSimulator.simulateGathering(gatheringSkill(XpRange(5, 5)), startXp = 0, random = Random(99))
        assertTrue(fixed.frames.all { it.xpGain == 5 })
        assertEquals(300L, fixed.frames.sumOf { it.xpGain.toLong() })

        // Wide range -> every roll must stay within [min, max].
        val wide = SkillSimulator.simulateGathering(gatheringSkill(XpRange(3, 7)), startXp = 0, random = Random(99))
        assertTrue(wide.frames.all { it.xpGain in 3..7 })
    }

    @Test
    fun `gathering drop chance of 1 always drops and seeded runs reproduce`() {
        val drops = listOf(SkillDropEntry(item = "nugget", chance = 1.0))
        val a = SkillSimulator.simulateGathering(gatheringSkill(XpRange(5, 5), drops), startXp = 0, random = Random(3))
        val b = SkillSimulator.simulateGathering(gatheringSkill(XpRange(5, 5), drops), startXp = 0, random = Random(3))
        assertEquals(a.frames, b.frames)
        assertTrue(a.frames.all { (it.items["nugget"] ?: 0) == 1 })
    }

    @Test
    fun `agility XP is always a bounded multiple of xp-per-success and reproducible`() {
        val course = AgilityCourseData(name = "gnome", displayName = "Gnome", levelRequired = 1, xpPerSuccess = 9)
        val a = SkillSimulator.simulateAgility(course, startXp = 0, agilityLevel = 50, random = Random(5))
        val b = SkillSimulator.simulateAgility(course, startXp = 0, agilityLevel = 50, random = Random(5))

        assertEquals(a.frames, b.frames)
        // At most LAPS_PER_MINUTE (2) successful laps per frame, each worth 9 XP.
        assertTrue(a.frames.all { it.xpGain % 9 == 0 && it.xpGain in 0..18 })
    }
}
