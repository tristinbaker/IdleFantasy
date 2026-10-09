package com.fantasyidler.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fix verification for #2039: validated isle quest progress must not regress
 * on sell or transform — mainland-like locked progress.
 *
 * Covers: sell-all, partial transform (ore -> bar), armor sell, in-progress
 * live display, and cap at target.
 */
class ElderQuestsProgressTest {

    private fun snapshot(inventory: Map<String, Int>) = ElderQuests.Snapshot(
        dungeonRuns = emptyMap(),
        enemyKills = emptyMap(),
        inventory = inventory,
    )

    @Test
    fun `validated mythrite quest stays at target after selling all`() {
        val quest = ElderQuests.CHAIN.first { it.id == "act1_rowans_cache" }
        val completed = setOf("act1_rowans_cache")
        val afterSell = snapshot(mapOf("mythrite_ore" to 0))
        assertEquals(quest.target, ElderQuests.displayProgress(quest, afterSell, completed))
    }

    @Test
    fun `validated voidsteel quest stays at target after smelting into bars`() {
        val quest = ElderQuests.CHAIN.first { it.id == "act3_voidsteel_study" }
        val completed = setOf("act1_first_steps", "act1_rowans_cache", "act2_library_salvage", "act3_voidsteel_study")
        // 1200 ore smelted into 600 bars: live ore is 0, bars don't count toward the ore quest.
        val afterSmelt = snapshot(mapOf("voidsteel_ore" to 0, "voidsteel_bar" to 600))
        assertEquals(quest.target, ElderQuests.displayProgress(quest, afterSmelt, completed))
    }

    @Test
    fun `validated coastal armor quest stays complete after selling pieces`() {
        val quest = ElderQuests.CHAIN.first { it.id == "act2_first_armor" }
        val completed = setOf("act1_first_steps", "act1_rowans_cache", "act1_first_cooking", "act2_cutting_vines", "act2_library_salvage", "act2_first_armor")
        val afterSell = snapshot(emptyMap())
        assertEquals(quest.target, ElderQuests.displayProgress(quest, afterSell, completed))
    }

    @Test
    fun `in-progress quest still shows live inventory`() {
        val quest = ElderQuests.CHAIN.first { it.id == "act1_rowans_cache" }
        val open = emptySet<String>()
        assertEquals(300, ElderQuests.displayProgress(quest, snapshot(mapOf("mythrite_ore" to 300)), open))
    }

    @Test
    fun `in-progress quest caps live progress at target`() {
        val quest = ElderQuests.CHAIN.first { it.id == "act1_rowans_cache" }
        val open = emptySet<String>()
        assertEquals(quest.target, ElderQuests.displayProgress(quest, snapshot(mapOf("mythrite_ore" to 9999)), open))
    }

    @Test
    fun `next open quest after validated chain still shows live progress`() {
        val cooking = ElderQuests.CHAIN.first { it.id == "act1_first_cooking" }
        val completed = setOf("act1_first_steps", "act1_rowans_cache")
        assertEquals(100, ElderQuests.displayProgress(cooking, snapshot(mapOf("tidepool_crab" to 100)), completed))
        // Once that one validates too, it locks as well.
        assertEquals(
            cooking.target,
            ElderQuests.displayProgress(cooking, snapshot(mapOf("tidepool_crab" to 0)), completed + "act1_first_cooking"),
        )
    }
}
