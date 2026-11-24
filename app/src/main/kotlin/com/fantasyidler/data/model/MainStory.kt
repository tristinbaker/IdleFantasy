package com.fantasyidler.data.model

import androidx.annotation.StringRes
import com.fantasyidler.R

/**
 * The optional mainland main story (v2.0.0): Thaddius Cantor, the Duster's outpost, and the
 * necromancer Kur'zul, told across the twelve story dungeons. Pure data plus pure progression
 * logic; [com.fantasyidler.repository.StoryRepository] persists the results into PlayerFlags.
 *
 * Nothing here gates gameplay. Objectives only count while their chapter is active, so a
 * veteran who begins the story still plays it out one dungeon at a time.
 */
object MainStory {

    enum class Speaker { NARRATOR, THADDIUS }

    data class Page(@StringRes val text: Int, val speaker: Speaker = Speaker.NARRATOR)

    data class Note(val id: String, val pages: List<Page>)

    sealed interface Event {
        /** A player session (not a town worker, not Elder Isle) was started. */
        data class SessionStarted(val skill: String, val activityKey: String) : Event
        /** A player gathering or crafting session was collected. */
        data class SessionCollected(val skill: String, val activityKey: String, val items: Map<String, Int>) : Event
        /** A player dungeon session was collected without dying. */
        data class DungeonCleared(val dungeonKey: String) : Event
        /** A player boss session was collected as a win. */
        data class BossDefeated(val bossId: String) : Event
    }

    sealed interface Trigger {
        data object ChapterStart : Trigger
        class SessionStart(val matches: (Event.SessionStarted) -> Boolean) : Trigger
        data class ObjectiveDone(val objectiveId: String) : Trigger
        data object ChapterComplete : Trigger
    }

    class Beat(val note: Note, val trigger: Trigger)

    class Objective(
        val id: String,
        @StringRes val label: Int,
        /** Formats [label] with this dungeon's name, for "Clear %1$s" objectives. */
        val dungeonArg: String? = null,
        val counts: (Event) -> Boolean,
    )

    class Chapter(
        val id: String,
        /** Null = the chapter is named after [dungeonKey]. */
        @StringRes val titleRes: Int?,
        val dungeonKey: String?,
        val objectives: List<Objective>,
        val beats: List<Beat>,
        val rewardCoins: Long = 0L,
        val rewardTitle: String? = null,
        /** Keepsake granted when the chapter completes. */
        val grantsItem: String? = null,
        /** Objectives only count while every one of these is in the inventory. */
        val requiresItems: List<String> = emptyList(),
        /** Keepsakes used up when the chapter completes. */
        val consumesItems: List<String> = emptyList(),
    )

    const val DUSTER_TITLE = "duster"
    /** The story's finale boss; listed only once the story reaches his chapter. */
    const val KURZUL_BOSS  = "kurzul"
    const val BANE_TITLE   = "bane_of_kurzul"

    const val OBSIDIAN_KEY   = "keepsake_obsidian_key"
    const val MOUNTAIN_HEART = "keepsake_mountain_heart"
    const val DRAGON_MOLT    = "keepsake_dragon_molt"
    const val EYE_OF_CHAOS   = "keepsake_eye_of_chaos"

    /** Story keepsakes can never be sold or swept up by Sell Junk. */
    val KEEPSAKES: Set<String> = setOf(OBSIDIAN_KEY, MOUNTAIN_HEART, DRAGON_MOLT, EYE_OF_CHAOS)

    private val GATHER_SKILLS = setOf(Skills.MINING, Skills.FISHING, Skills.WOODCUTTING)
    private val CRAFT_SKILLS  = setOf(Skills.SMITHING, Skills.FLETCHING, Skills.CRAFTING, Skills.RUNECRAFTING, Skills.HERBLORE, Skills.CONSTRUCTION)
    private const val COMBAT_SKILL = "combat"

    private fun note(id: String, vararg pages: Page) = Note(id, pages.toList())
    private fun n(@StringRes text: Int) = Page(text)
    private fun t(@StringRes text: Int) = Page(text, Speaker.THADDIUS)

    private fun startsDungeon(key: String) = Trigger.SessionStart { it.skill == COMBAT_SKILL && it.activityKey == key }

    private fun clearObjective(key: String) = Objective(
        id         = "clear",
        label      = R.string.story_obj_clear,
        dungeonArg = key,
        counts     = { it is Event.DungeonCleared && it.dungeonKey == key },
    )

    private fun dungeonChapter(
        key: String,
        coins: Long,
        start: Note? = null,
        opening: Note? = null,
        complete: List<Note>,
        rewardTitle: String? = null,
        grantsItem: String? = null,
        requiresItems: List<String> = emptyList(),
        consumesItems: List<String> = emptyList(),
    ) = Chapter(
        id            = key,
        titleRes      = null,
        dungeonKey    = key,
        objectives    = listOf(clearObjective(key)),
        beats         = buildList {
            opening?.let { add(Beat(it, Trigger.ChapterStart)) }
            start?.let { add(Beat(it, startsDungeon(key))) }
            complete.forEach { add(Beat(it, Trigger.ChapterComplete)) }
        },
        rewardCoins   = coins,
        rewardTitle   = rewardTitle,
        grantsItem    = grantsItem,
        requiresItems = requiresItems,
        consumesItems = consumesItems,
    )

    val CHAPTERS: List<Chapter> = listOf(
        Chapter(
            id         = "prologue",
            titleRes   = R.string.story_chapter_prologue,
            dungeonKey = null,
            objectives = emptyList(),
            beats      = listOf(Beat(note("intro", n(R.string.story_intro_1), n(R.string.story_intro_2), n(R.string.story_intro_3)), Trigger.ChapterStart)),
        ),
        Chapter(
            id         = "landfall",
            titleRes   = R.string.story_chapter_landfall,
            dungeonKey = null,
            objectives = listOf(
                Objective("gather", R.string.story_obj_gather) { it is Event.SessionCollected && it.skill in GATHER_SKILLS },
                Objective("cook_shrimp", R.string.story_obj_cook_shrimp) {
                    it is Event.SessionCollected && it.skill == Skills.COOKING && (it.activityKey == "shrimp" || (it.items["shrimp"] ?: 0) > 0)
                },
                Objective("smelt_bronze", R.string.story_obj_smelt_bronze) {
                    it is Event.SessionCollected && it.skill == Skills.SMITHING && (it.activityKey == "bronze_bar" || (it.items["bronze_bar"] ?: 0) > 0)
                },
                clearObjective("farm"),
            ),
            beats = listOf(
                Beat(note("landing_welcome", t(R.string.story_landing_welcome_1)), Trigger.ChapterStart),
                Beat(note("landing_gather_start", t(R.string.story_landing_gather_start_1)), Trigger.SessionStart { it.skill in GATHER_SKILLS }),
                Beat(note("landing_gather_done", t(R.string.story_landing_gather_done_1)), Trigger.ObjectiveDone("gather")),
                Beat(note("landing_craft_start", t(R.string.story_landing_craft_start_1)), Trigger.SessionStart { it.skill in CRAFT_SKILLS }),
                Beat(note("landing_cook_start", t(R.string.story_landing_cook_start_1)), Trigger.SessionStart { it.skill == Skills.COOKING }),
                Beat(note("landing_farm_start", t(R.string.story_landing_farm_start_1)), startsDungeon("farm")),
                Beat(note("landing_farm_done", n(R.string.story_landing_farm_done_1), t(R.string.story_landing_farm_done_2)), Trigger.ObjectiveDone("clear")),
            ),
            rewardCoins = 1_000L,
            rewardTitle = DUSTER_TITLE,
        ),
        dungeonChapter("imp_cavern", 1_000L,
            opening  = note("imp_prompt", n(R.string.story_imp_prompt_1)),
            start    = note("imp_start", t(R.string.story_imp_start_1)),
            complete = listOf(note("imp_done", n(R.string.story_imp_done_1))),
        ),
        dungeonChapter("goblin_cave", 2_000L,
            opening  = note("goblin_prompt", n(R.string.story_goblin_prompt_1)),
            complete = listOf(note("goblin_done", n(R.string.story_goblin_done_1))),
        ),
        dungeonChapter("spider_den", 4_000L,
            start    = note("spider_start", n(R.string.story_spider_start_1)),
            complete = listOf(note("spider_done", n(R.string.story_spider_done_1), t(R.string.story_spider_done_2))),
        ),
        dungeonChapter("bandit_camp", 8_000L,
            complete   = listOf(note("bandit_done", n(R.string.story_bandit_done_1))),
            grantsItem = OBSIDIAN_KEY,
        ),
        dungeonChapter("undead_crypt", 16_000L,
            start    = note("crypt_start", n(R.string.story_crypt_start_1)),
            complete = listOf(note("crypt_done", n(R.string.story_crypt_done_1), n(R.string.story_crypt_done_2))),
        ),
        dungeonChapter("fortress_ruins", 32_000L,
            complete = listOf(note("fortress_done", n(R.string.story_fortress_done_1), n(R.string.story_fortress_done_2), t(R.string.story_fortress_done_3))),
        ),
        dungeonChapter("orc_stronghold", 50_000L,
            start    = note("orc_start", n(R.string.story_orc_start_1)),
            complete = listOf(
                note("orc_done", n(R.string.story_orc_done_1)),
                note("orc_return", n(R.string.story_orc_return_1), n(R.string.story_orc_return_2), n(R.string.story_orc_return_3)),
            ),
        ),
        dungeonChapter("volcanic_depths", 75_000L,
            start      = note("volcanic_start", n(R.string.story_volcanic_start_1)),
            complete   = listOf(note("volcanic_done", n(R.string.story_volcanic_done_1), n(R.string.story_volcanic_done_2))),
            grantsItem = MOUNTAIN_HEART,
        ),
        dungeonChapter("dragon_lair", 100_000L,
            start      = note("dragon_start", n(R.string.story_dragon_start_1)),
            complete   = listOf(note("dragon_done", n(R.string.story_dragon_done_1), n(R.string.story_dragon_done_2))),
            grantsItem = DRAGON_MOLT,
        ),
        dungeonChapter("infernal_stronghold", 150_000L,
            start         = note("infernal_start", n(R.string.story_infernal_start_1), n(R.string.story_infernal_start_2)),
            complete      = listOf(note("infernal_done", n(R.string.story_infernal_done_1), n(R.string.story_infernal_done_2))),
            grantsItem    = EYE_OF_CHAOS,
            requiresItems = listOf(MOUNTAIN_HEART, DRAGON_MOLT),
            consumesItems = listOf(MOUNTAIN_HEART, DRAGON_MOLT),
        ),
        // Clearing the temple ends as the duel begins; the duel itself is the Kur'zul boss.
        dungeonChapter("ancient_temple", 200_000L,
            start         = note("temple_start", n(R.string.story_temple_start_1), n(R.string.story_temple_start_2), n(R.string.story_temple_start_3), n(R.string.story_temple_start_4)),
            complete      = listOf(note("temple_done", n(R.string.story_temple_done_1))),
            requiresItems = listOf(OBSIDIAN_KEY, EYE_OF_CHAOS),
        ),
        Chapter(
            id         = "kurzul",
            titleRes   = R.string.story_chapter_kurzul,
            dungeonKey = null,
            objectives = listOf(
                Objective("defeat", R.string.story_obj_defeat_kurzul) { it is Event.BossDefeated && it.bossId == KURZUL_BOSS },
            ),
            beats = listOf(
                Beat(note("kurzul_done", n(R.string.story_kurzul_done_1), n(R.string.story_kurzul_done_2), n(R.string.story_kurzul_done_3), n(R.string.story_kurzul_done_4)), Trigger.ChapterComplete),
            ),
            rewardTitle = BANE_TITLE,
        ),
    )

    val LANDFALL_INDEX: Int = CHAPTERS.indexOfFirst { it.id == "landfall" }
    val KURZUL_INDEX: Int = CHAPTERS.indexOfFirst { it.id == "kurzul" }

    /** The Kur'zul boss appears once the story reaches his chapter, and stays after the ending. */
    fun storyBossUnlocked(started: Boolean, chapterIndex: Int): Boolean = started && chapterIndex >= KURZUL_INDEX

    private val notesById: Map<String, Note> = CHAPTERS.flatMap { c -> c.beats.map { it.note } }.associateBy { it.id }
    private val chapterByNote: Map<String, Int> = CHAPTERS.withIndex()
        .flatMap { (i, c) -> c.beats.map { it.note.id to i } }.toMap()
    /** Keepsake to the dungeon whose story clear grants it, for re-granting a lost one. */
    private val keepsakeSource: Map<String, String> = CHAPTERS
        .mapNotNull { c -> c.grantsItem?.let { item -> c.dungeonKey?.let { item to it } } }.toMap()

    fun noteById(id: String): Note? = notesById[id]
    fun chapterIndexOfNote(id: String): Int? = chapterByNote[id]

    fun counterKey(chapter: Chapter, objective: Objective) = "${chapter.id}.${objective.id}"

    fun isObjectiveDone(chapter: Chapter, objective: Objective, counters: Map<String, Int>) =
        (counters[counterKey(chapter, objective)] ?: 0) >= 1

    fun missingItems(chapter: Chapter, inventory: Map<String, Int>): List<String> =
        chapter.requiresItems.filter { (inventory[it] ?: 0) <= 0 }

    fun titlesEarned(started: Boolean, chapterIndex: Int): Set<String> = buildSet {
        if (!started) return@buildSet
        CHAPTERS.take(chapterIndex.coerceIn(0, CHAPTERS.size)).forEach { c -> c.rewardTitle?.let { add(it) } }
    }

    // ------------------------------------------------------------------ progression

    data class State(
        val started: Boolean,
        val chapter: Int,
        val counters: Map<String, Int>,
        val delivered: List<String>,
    )

    data class Outcome(
        val state: State,
        /** Notes delivered by this step, oldest first. */
        val newNotes: List<String> = emptyList(),
        val coins: Long = 0L,
        val titles: Set<String> = emptySet(),
        val grantItems: Map<String, Int> = emptyMap(),
        val consumeItems: Map<String, Int> = emptyMap(),
    ) {
        val changed: Boolean get() = newNotes.isNotEmpty() || coins > 0 || titles.isNotEmpty() ||
            grantItems.isNotEmpty() || consumeItems.isNotEmpty()
    }

    private class Builder(start: State) {
        var chapter = start.chapter
        val counters = start.counters.toMutableMap()
        val delivered = start.delivered.toMutableList()
        val newNotes = mutableListOf<String>()
        var coins = 0L
        val titles = mutableSetOf<String>()
        val grant = mutableMapOf<String, Int>()
        val consume = mutableMapOf<String, Int>()

        fun deliver(note: Note) {
            if (note.id in delivered) return
            delivered += note.id
            newNotes += note.id
        }

        fun deliverWhere(chapter: Chapter, pick: (Trigger) -> Boolean) =
            chapter.beats.filter { pick(it.trigger) }.forEach { deliver(it.note) }

        /** Completes finished chapters and opens the next ones until one still has work left. */
        fun advance(inventory: Map<String, Int>) {
            while (chapter < CHAPTERS.size) {
                val c = CHAPTERS[chapter]
                if (!c.objectives.all { isObjectiveDone(c, it, counters) }) return
                deliverWhere(c) { it == Trigger.ChapterComplete }
                coins += c.rewardCoins
                c.rewardTitle?.let { titles += it }
                for (item in c.consumesItems) {
                    if ((inventory[item] ?: 0) + (grant[item] ?: 0) > 0) consume[item] = 1
                }
                c.grantsItem?.let { item -> grant[item] = 1 }
                chapter++
                if (chapter < CHAPTERS.size) deliverWhere(CHAPTERS[chapter]) { it == Trigger.ChapterStart }
            }
        }

        fun build(started: Boolean) = Outcome(
            state        = State(started, chapter, counters.toMap(), delivered.toList()),
            newNotes     = newNotes.toList(),
            coins        = coins,
            titles       = titles.toSet(),
            grantItems   = grant.toMap(),
            consumeItems = consume.toMap(),
        )
    }

    /** Starts the story. A no-op once it has begun. */
    fun begin(state: State, inventory: Map<String, Int>): Outcome {
        if (state.started) return Outcome(state)
        val b = Builder(state.copy(chapter = 0))
        b.deliverWhere(CHAPTERS[0]) { it == Trigger.ChapterStart }
        b.advance(inventory)
        return b.build(started = true)
    }

    fun onEvent(state: State, event: Event, inventory: Map<String, Int>): Outcome {
        if (!state.started || state.chapter !in CHAPTERS.indices) return Outcome(state)
        val c = CHAPTERS[state.chapter]
        val b = Builder(state)
        if (event is Event.SessionStarted) {
            c.beats.forEach { beat ->
                val trig = beat.trigger
                if (trig is Trigger.SessionStart && trig.matches(event)) b.deliver(beat.note)
            }
            return b.build(started = true)
        }
        // A keepsake this chapter needs went missing: clearing the dungeon that first
        // granted it hands it back, so the story can never get stuck.
        if (event is Event.DungeonCleared) {
            for (item in missingItems(c, inventory)) {
                if (keepsakeSource[item] == event.dungeonKey) b.grant[item] = 1
            }
        }
        val held = c.requiresItems.all { (inventory[it] ?: 0) + (b.grant[it] ?: 0) > 0 }
        if (held) {
            for (obj in c.objectives) {
                if (isObjectiveDone(c, obj, b.counters) || !obj.counts(event)) continue
                b.counters[counterKey(c, obj)] = 1
                b.deliverWhere(c) { it is Trigger.ObjectiveDone && it.objectiveId == obj.id }
            }
        }
        b.advance(inventory)
        return b.build(started = true)
    }
}
