package com.fantasyidler.ui.screen

import com.fantasyidler.data.model.Skills

/** Simplified OSRS combat level formula using melee stats only. */
internal fun combatLevel(levels: Map<String, Int>): Int {
    val attack  = levels[Skills.ATTACK]    ?: 1
    val strength = levels[Skills.STRENGTH] ?: 1
    val defence  = levels[Skills.DEFENSE]  ?: 1
    val hp       = levels[Skills.HITPOINTS] ?: 1
    return (((attack + strength) * 0.325) + (defence + hp) * 0.25).toInt().coerceAtLeast(1)
}
