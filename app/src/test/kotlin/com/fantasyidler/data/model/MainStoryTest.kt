package com.fantasyidler.data.model

import com.fantasyidler.data.model.MainStory.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure progression rules for the optional main story (v2.0.0). */
class MainStoryTest {

    private val fresh = MainStory.State(started = false, chapter = 0, counters = emptyMap(), delivered = emptyList())

    private fun begun(): MainStory.State = MainStory.begin(fresh, emptyMap()).state

    private fun indexOf(id: String) = MainStory.CHAPTERS.indexOfFirst { it.id == id }

    /** Runs the Landfall objectives so the story sits at the first dungeon chapter. */
    private fun throughLandfall(): MainStory.State {
        var s = begun()
        s = MainStory.onEvent(s, Event.SessionCollected(Skills.MINING, "copper_ore", mapOf("copper_ore" to 10)), emptyMap()).state
        s = MainStory.onEvent(s, Event.SessionCollected(Skills.COOKING, "shrimp", mapOf("shrimp" to 5)), emptyMap()).state
        s = MainStory.onEvent(s, Event.SessionCollected(Skills.SMITHING, "bronze_bar", mapOf("bronze_bar" to 5)), emptyMap()).state
        s = MainStory.onEvent(s, Event.DungeonCleared("farm"), emptyMap()).state
        return s
    }

    @Test
    fun `nothing advances before the story begins`() {
        val out = MainStory.onEvent(fresh, Event.DungeonCleared("farm"), emptyMap())
        assertFalse(out.changed)
        assertEquals(fresh, out.state)
    }

    @Test
    fun `begin delivers the prologue and opens Landfall`() {
        val out = MainStory.begin(fresh, emptyMap())
        assertTrue(out.state.started)
        assertEquals(MainStory.LANDFALL_INDEX, out.state.chapter)
        assertEquals(listOf("intro", "landing_welcome"), out.newNotes)
        // Beginning twice is a no-op.
        assertFalse(MainStory.begin(out.state, emptyMap()).changed)
    }

    @Test
    fun `selection notes fire once on session start`() {
        val s = begun()
        val first = MainStory.onEvent(s, Event.SessionStarted(Skills.FISHING, "raw_shrimp"), emptyMap())
        assertEquals(listOf("landing_gather_start"), first.newNotes)
        val again = MainStory.onEvent(first.state, Event.SessionStarted(Skills.MINING, "copper_ore"), emptyMap())
        assertTrue(again.newNotes.isEmpty())
        val cooking = MainStory.onEvent(first.state, Event.SessionStarted(Skills.COOKING, "shrimp"), emptyMap())
        assertEquals(listOf("landing_cook_start"), cooking.newNotes)
    }

    @Test
    fun `Landfall needs all four objectives and grants coins and the Duster title`() {
        var s = begun()
        s = MainStory.onEvent(s, Event.DungeonCleared("farm"), emptyMap()).state
        assertEquals(MainStory.LANDFALL_INDEX, s.chapter)
        s = MainStory.onEvent(s, Event.SessionCollected(Skills.MINING, "copper_ore", emptyMap()), emptyMap()).state
        s = MainStory.onEvent(s, Event.SessionCollected(Skills.COOKING, "shrimp", emptyMap()), emptyMap()).state
        val last = MainStory.onEvent(s, Event.SessionCollected(Skills.SMITHING, "bronze_bar", emptyMap()), emptyMap())
        assertEquals(indexOf("imp_cavern"), last.state.chapter)
        assertEquals(1_000L, last.coins)
        assertEquals(setOf(MainStory.DUSTER_TITLE), last.titles)
        assertTrue("imp_prompt" in last.newNotes)
    }

    @Test
    fun `clears of a later dungeon do not count early`() {
        val s = throughLandfall()
        val out = MainStory.onEvent(s, Event.DungeonCleared("goblin_cave"), emptyMap())
        assertEquals(indexOf("imp_cavern"), out.state.chapter)
        assertFalse(out.changed)
    }

    @Test
    fun `a dungeon clear completes its chapter in order`() {
        val s = throughLandfall()
        val out = MainStory.onEvent(s, Event.DungeonCleared("imp_cavern"), emptyMap())
        assertEquals(indexOf("goblin_cave"), out.state.chapter)
        assertEquals(listOf("imp_done", "goblin_prompt"), out.newNotes)
        assertEquals(1_000L, out.coins)
    }

    @Test
    fun `Orc Stronghold delivers both completion notes in order`() {
        val s = fresh.copy(started = true, chapter = indexOf("orc_stronghold"))
        val out = MainStory.onEvent(s, Event.DungeonCleared("orc_stronghold"), emptyMap())
        assertEquals(listOf("orc_done", "orc_return"), out.newNotes)
    }

    @Test
    fun `Infernal Stronghold needs the reagents, uses them up and grants the Eye of Chaos`() {
        val s = fresh.copy(started = true, chapter = indexOf("infernal_stronghold"))
        val without = MainStory.onEvent(s, Event.DungeonCleared("infernal_stronghold"), emptyMap())
        assertEquals(indexOf("infernal_stronghold"), without.state.chapter)

        val inv = mapOf(MainStory.MOUNTAIN_HEART to 1, MainStory.DRAGON_MOLT to 1)
        val with = MainStory.onEvent(s, Event.DungeonCleared("infernal_stronghold"), inv)
        assertEquals(indexOf("ancient_temple"), with.state.chapter)
        assertEquals(mapOf(MainStory.MOUNTAIN_HEART to 1, MainStory.DRAGON_MOLT to 1), with.consumeItems)
        assertEquals(mapOf(MainStory.EYE_OF_CHAOS to 1), with.grantItems)
    }

    @Test
    fun `a lost keepsake comes back from its source dungeon`() {
        val s = fresh.copy(started = true, chapter = indexOf("ancient_temple"))
        val out = MainStory.onEvent(s, Event.DungeonCleared("bandit_camp"), mapOf(MainStory.EYE_OF_CHAOS to 1))
        assertEquals(mapOf(MainStory.OBSIDIAN_KEY to 1), out.grantItems)
        assertEquals(indexOf("ancient_temple"), out.state.chapter)
    }

    @Test
    fun `clearing the Ancient Temple opens the Kur'zul boss chapter`() {
        val s = fresh.copy(started = true, chapter = indexOf("ancient_temple"))
        val inv = mapOf(MainStory.OBSIDIAN_KEY to 1, MainStory.EYE_OF_CHAOS to 1)
        assertFalse(MainStory.storyBossUnlocked(s.started, s.chapter))
        val out = MainStory.onEvent(s, Event.DungeonCleared("ancient_temple"), inv)
        assertEquals(MainStory.KURZUL_INDEX, out.state.chapter)
        assertEquals(listOf("temple_done"), out.newNotes)
        assertEquals(200_000L, out.coins)
        assertTrue(out.titles.isEmpty())
        assertTrue(MainStory.storyBossUnlocked(out.state.started, out.state.chapter))
    }

    @Test
    fun `a Kur'zul kill before his chapter does not count`() {
        val s = fresh.copy(started = true, chapter = indexOf("ancient_temple"))
        val out = MainStory.onEvent(s, Event.BossDefeated(MainStory.KURZUL_BOSS), emptyMap())
        assertFalse(out.changed)
        assertEquals(indexOf("ancient_temple"), out.state.chapter)
    }

    @Test
    fun `defeating Kur'zul ends the story with the final title`() {
        val s = fresh.copy(started = true, chapter = MainStory.KURZUL_INDEX)
        assertFalse(MainStory.onEvent(s, Event.BossDefeated("demon_lord"), emptyMap()).changed)
        val out = MainStory.onEvent(s, Event.BossDefeated(MainStory.KURZUL_BOSS), emptyMap())
        assertEquals(MainStory.CHAPTERS.size, out.state.chapter)
        assertEquals(setOf(MainStory.BANE_TITLE), out.titles)
        assertEquals(listOf("kurzul_done"), out.newNotes)
        // Nothing happens after the end, and the boss stays available.
        assertFalse(MainStory.onEvent(out.state, Event.BossDefeated(MainStory.KURZUL_BOSS), emptyMap()).changed)
        assertTrue(MainStory.storyBossUnlocked(true, out.state.chapter))
        assertEquals(setOf(MainStory.DUSTER_TITLE, MainStory.BANE_TITLE), MainStory.titlesEarned(true, out.state.chapter))
    }

    @Test
    fun `every note id is unique and every chapter has a note`() {
        val ids = MainStory.CHAPTERS.flatMap { c -> c.beats.map { it.note.id } }
        assertEquals(ids.size, ids.toSet().size)
        MainStory.CHAPTERS.forEach { c -> assertTrue(c.id, c.beats.isNotEmpty()) }
    }
}
