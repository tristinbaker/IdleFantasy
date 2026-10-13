package com.fantasyidler.ui.viewmodel

import com.fantasyidler.simulator.SkillSimulator

// Issue #2066: direct-start duration helpers. On the isle the only lever is
// elder Agility (60 -> 45 min); mainland agility, prestige floor and Chronos
// Spire are ignored — same rule as QueuedSessionStarter (queued path).
internal fun directDungeonDurationMs(
    isElder: Boolean,
    elderAgility: Int,
    mainlandAgility: Int,
    floorReductionMin: Double,
    chronosMultiplier: Float,
): Long {
    if (isElder) return SkillSimulator.elderSessionDurationMs(elderAgility)
    return SkillSimulator.sessionDurationMs(mainlandAgility, floorReductionMin, chronosMultiplier)
}

internal fun directBossFrameMs(
    isElder: Boolean,
    elderAgility: Int,
    mainlandAgility: Int,
    floorReductionMin: Double,
    chronosMultiplier: Float,
): Long {
    if (isElder) return SkillSimulator.elderSessionDurationMs(elderAgility) / 60L
    return SkillSimulator.sessionDurationMs(mainlandAgility, floorReductionMin, chronosMultiplier) / 60L
}
