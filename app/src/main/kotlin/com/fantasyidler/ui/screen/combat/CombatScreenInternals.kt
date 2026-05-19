package com.fantasyidler.ui.screen.combat

import com.fantasyidler.data.model.Skills

/**
 * Shared constants + helpers for the [com.fantasyidler.ui.screen.CombatScreen]
 * slice. Kept `internal` so consumers outside this package don't take a
 * dependency on combat-screen-private details.
 */

/** Combat skill keys, in canonical UI order. */
internal val COMBAT_SKILLS: List<String> = listOf(
    Skills.ATTACK, Skills.STRENGTH, Skills.DEFENSE,
    Skills.RANGED, Skills.MAGIC, Skills.HITPOINTS, Skills.PRAYER,
)

/** XP-bearing skill keys for the live combat HUD ribbon (no prayer). */
internal val COMBAT_HUD_SKILLS: List<String> = listOf(
    Skills.ATTACK, Skills.STRENGTH, Skills.DEFENSE,
    Skills.RANGED, Skills.MAGIC, Skills.HITPOINTS,
)

/** Dungeons within this many levels of the recommendation are still enterable. */
internal const val UNLOCK_TOLERANCE: Int = 5

/** Arrow tiers from best to worst — mirrors `CombatViewModel.ARROW_TIERS`. */
internal val ARROW_TIERS: List<String> = listOf(
    "runite_arrow", "adamantite_arrow", "mithril_arrow",
    "steel_arrow", "iron_arrow", "bronze_arrow",
)

/**
 * Simplified OSRS combat-level formula using melee stats only — the same
 * approximation the old `CombatScreen.combatLevel(...)` exposed.
 */
internal fun combatLevel(levels: Map<String, Int>): Int {
    val attack   = levels[Skills.ATTACK]    ?: 1
    val strength = levels[Skills.STRENGTH]  ?: 1
    val defense  = levels[Skills.DEFENSE]   ?: 1
    val hp       = levels[Skills.HITPOINTS] ?: 1
    return (((attack + strength) * 0.325) + (defense + hp) * 0.25).toInt().coerceAtLeast(1)
}
