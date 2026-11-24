package com.fantasyidler.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Issue #2109 — "Hiding completed quests causes a crash".
 *
 * Pure-logic pin for the tab-filter + pager-index arithmetic used by QuestsScreen.
 * The Compose pager itself can't run on JVM, so this test pins the exact helpers
 * the composable must use: which tabs stay visible and how the current page is
 * coerced when that list shrinks. (Proportionate test-first: the crash needs the
 * Compose pager runtime; the JVM pins the index arithmetic that caused it.)
 */
class QuestsTabGroupsTest {

    private val allGroups = listOf("Daily", "Weekly", "Gathering", "Crafting", "Combat", "Special")

    private fun groupsWith(vararg nonEmpty: String): Map<String, List<Int>> =
        allGroups.associateWith { if (it in nonEmpty) listOf(1) else emptyList() }

    @Test
    fun `hide off keeps every tab`() {
        assertEquals(allGroups, visibleQuestTabGroups(allGroups, groupsWith(), hideCompleted = false))
    }

    @Test
    fun `hide on always keeps Daily and Weekly even when empty`() {
        assertEquals(
            listOf("Daily", "Weekly"),
            visibleQuestTabGroups(allGroups, groupsWith(), hideCompleted = true),
        )
    }

    @Test
    fun `hide on keeps non-empty groups in order`() {
        assertEquals(
            listOf("Daily", "Weekly", "Crafting", "Special"),
            visibleQuestTabGroups(allGroups, groupsWith("Crafting", "Special"), hideCompleted = true),
        )
    }

    @Test
    fun `old inline indexing crashes on the issue scenario`() {
        // Documents the bug: standing on "Special" (index 5), hiding completed
        // leaves only Daily+Weekly, and tabGroups[5] throws.
        val tabGroups = visibleQuestTabGroups(allGroups, groupsWith(), hideCompleted = true)
        try {
            @Suppress("UNUSED_EXPRESSION")
            tabGroups[5]
            fail("expected IndexOutOfBoundsException for stale page 5 over $tabGroups")
        } catch (_: IndexOutOfBoundsException) {
            // RED: this is the #2109 crash.
        }
    }

    @Test
    fun `stale page is coerced into the shrunken list instead of crashing`() {
        val tabGroups = visibleQuestTabGroups(allGroups, groupsWith(), hideCompleted = true)
        val page = coerceQuestTabPage(currentPage = 5, tabCount = tabGroups.size)
        assertTrue("coerced page $page must be a valid index", page in tabGroups.indices)
        assertEquals("lands on the rightmost remaining tab (Weekly)", "Weekly", tabGroups[page])
    }

    @Test
    fun `page inside range is left alone`() {
        assertEquals(2, coerceQuestTabPage(currentPage = 2, tabCount = 6))
        assertEquals(0, coerceQuestTabPage(currentPage = 0, tabCount = 2))
    }

    @Test
    fun `empty tab list coerces to zero without crashing`() {
        assertEquals(0, coerceQuestTabPage(currentPage = 3, tabCount = 0))
    }
}
