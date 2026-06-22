package com.fantasyidler.simulator

import com.fantasyidler.data.model.SessionFrame
import kotlin.random.Random

object CarnivalSimulator {

    private const val FRAMES = 60

    // Drop chance per frame: 15% at level 1, scales linearly to 35% at level 99.
    private fun ticketChance(skillLevel: Int): Double =
        0.15 + (skillLevel - 1).coerceIn(0, 98) * (0.20 / 98.0)

    // XP per frame in the relevant skill (small, ~5-10% of normal training rate).
    private fun xpPerFrame(activityKey: String): Int = when (activityKey) {
        "archery_range"         -> 10
        "strongman_competition" -> 8
        "wizards_duel"          -> 12
        "fishing_derby"         -> 9
        else                    -> 8
    }

    // Skill name for the xpBySkill map entry reported in frames.
    fun relevantSkill(activityKey: String): String = when (activityKey) {
        "archery_range"         -> "ranged"
        "strongman_competition" -> "strength"
        "wizards_duel"          -> "magic"
        "fishing_derby"         -> "fishing"
        else                    -> "ranged"
    }

    fun simulate(
        activityKey: String,
        relevantSkillLevel: Int,
        petBoostPct: Int,
        agilityLevel: Int,
        fairgroundsTier: Int = 0,
    ): SkillSimulator.Result {
        val tierBonus = fairgroundsTier.coerceIn(0, 3) * 0.05
        val chance = ticketChance(relevantSkillLevel) + tierBonus
        val baseXpFrame = xpPerFrame(activityKey)
        val skillKey = relevantSkill(activityKey)

        val frames = (1..FRAMES).map { minute ->
            val tickets = if (Random.nextDouble() < chance) 1 else 0
            val xpGain = if (petBoostPct > 0) (baseXpFrame * (1.0 + petBoostPct / 100.0)).toInt() else baseXpFrame
            val items = if (tickets > 0) mapOf("carnival_ticket" to tickets) else emptyMap()
            SessionFrame(
                minute       = minute,
                xpGain       = xpGain,
                xpBefore     = 0L,
                xpAfter      = 0L,
                levelBefore  = relevantSkillLevel,
                levelAfter   = relevantSkillLevel,
                items        = items,
                leveledUp    = false,
                xpBySkill    = mapOf(skillKey to xpGain.toLong()),
            )
        }

        return SkillSimulator.Result(
            frames     = frames,
            durationMs = SkillSimulator.sessionDurationMs(agilityLevel),
        )
    }
}
