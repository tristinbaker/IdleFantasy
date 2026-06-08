package com.fantasyidler.simulator

import com.fantasyidler.data.json.SkillDropEntry
import com.fantasyidler.data.json.SkillingDungeonData
import com.fantasyidler.data.json.XpRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Outcome tests for [SkillingDungeonSimulator] using the injected `random` seam. */
class SkillingDungeonSimulatorTest {

    private fun dungeon(
        xp: XpRange,
        drops: List<SkillDropEntry> = emptyList(),
        noteChance: Double = 0.0,
    ) = SkillingDungeonData(
        name = "caves", displayName = "Crystal Caves", description = "", skill = "mining", levelRequired = 1,
        xpRanges = mapOf("1" to xp),
        dropTables = if (drops.isEmpty()) emptyMap() else mapOf("1" to drops),
        noteChancePerFrame = noteChance,
        noteTexts = listOf("a", "b", "c", "d", "e"),
        unlockDungeon = "deeper_caves",
        unlockMessage = "Unlocked!",
    )

    @Test
    fun `fixed XP range gives exact totals over 60 frames`() {
        val r = SkillingDungeonSimulator.simulate("caves", dungeon(XpRange(6, 6)), startXp = 0, random = Random(8))
        assertEquals(60, r.frames.size)
        assertTrue(r.frames.all { it.xpGain == 6 })
        assertEquals(360L, r.frames.sumOf { it.xpGain.toLong() })
    }

    @Test
    fun `note chance of 1 drops a note every frame and 0 drops none`() {
        val always = SkillingDungeonSimulator.simulate(
            "caves", dungeon(XpRange(6, 6), noteChance = 1.0), startXp = 0, random = Random(8),
        )
        assertTrue(always.frames.all { (it.items["note_caves"] ?: 0) == 1 })

        val never = SkillingDungeonSimulator.simulate(
            "caves", dungeon(XpRange(6, 6), noteChance = 0.0), startXp = 0, random = Random(8),
        )
        assertTrue(never.frames.none { it.items.containsKey("note_caves") })
    }

    @Test
    fun `guaranteed drop appears every frame and seeded runs reproduce`() {
        val cfg = dungeon(XpRange(4, 10), drops = listOf(SkillDropEntry(item = "shard", chance = 1.0)))
        val a = SkillingDungeonSimulator.simulate("caves", cfg, startXp = 0, random = Random(55))
        val b = SkillingDungeonSimulator.simulate("caves", cfg, startXp = 0, random = Random(55))
        assertEquals(a.frames, b.frames)
        assertTrue(a.frames.all { (it.items["shard"] ?: 0) == 1 })
        assertTrue(a.frames.all { it.xpGain in 4..10 })
    }
}
