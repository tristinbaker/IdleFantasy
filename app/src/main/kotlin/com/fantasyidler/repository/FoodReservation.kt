package com.fantasyidler.repository

import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession

/**
 * Single source of truth for "how much equipped food is still spendable".
 *
 * Food is simulated up-front (frames store per-minute foodConsumed) but deducted
 * from inventory only at collection. Every session simulated while older sessions
 * sit completed-but-uncollected must therefore reserve the whole backlog —
 * otherwise sessions 2..N simulate against a phantom full supply and the summed
 * deductions exceed what the player owns (issue #1960: ~1700 used from 836).
 *
 * All "food left / food available" reads for session-creating simulations that
 * deduct at collect — every simulateDungeon/simulateBoss caller except the worker
 * queue (worker collect never deducts food, so reserving player backlog there
 * would penalize fights for food nobody deducts) — plus the combat-screen
 * remaining counts derive from [remaining]; the live inventory stays the source
 * for *owned* (pre-deduction) counts.
 */
object FoodReservation {

    /** Skills whose sessions simulate food and deduct it at collect time. */
    val FOOD_SKILLS: Set<String> = setOf("combat", "boss", "tower")

    /** Total foodConsumed across frames (one entry per food key). */
    fun sumConsumed(frames: List<SessionFrame>): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        for (frame in frames) {
            for ((key, qty) in frame.foodConsumed) out[key] = (out[key] ?: 0) + qty
        }
        return out
    }

    /**
     * Food already simulated but not yet deducted: summed over every completed
     * session in [sessions] whose skill consumes food. Collected sessions are
     * deleted from the store, so the backlog is exactly the uncollected remainder.
     * A single undecodable session is skipped, never zeroing the whole backlog.
     */
    fun aggregatePending(
        sessions: List<SkillSession>,
        decode: (String) -> List<SessionFrame>,
    ): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        for (session in sessions) {
            if (!session.completed || session.skillName !in FOOD_SKILLS) continue
            val frames = try {
                decode(session.frames)
            } catch (_: Exception) {
                continue
            }
            for ((key, qty) in sumConsumed(frames)) out[key] = (out[key] ?: 0) + qty
        }
        return out
    }

    /** Pending-aware "food left": live inventory minus the uncollected backlog. */
    fun remaining(
        inventory: Map<String, Int>,
        pending: Map<String, Int>,
    ): Map<String, Int> =
        inventory.mapValues { (key, qty) -> (qty - (pending[key] ?: 0)).coerceAtLeast(0) }

    /**
     * Simulation input convention (kept from the inline blocks this replaces):
     * pending-aware remainder restricted to equipped keys with qty > 0.
     * Empty means "no honest supply" — the caller simulates the no-food fight
     * (deaths stop repeat chains per existing convention) instead of a phantom
     * full-supply one. Never switches foods on its own.
     */
    fun available(
        inventory: Map<String, Int>,
        equippedKeys: Set<String>,
        pending: Map<String, Int>,
    ): Map<String, Int> =
        remaining(inventory, pending)
            .filterKeys { it in equippedKeys }
            .filterValues { it > 0 }

    /**
     * Pending-aware no-food gate: true when any equipped food is still spendable.
     * Manual starts with an exhausted supply must show the existing no-food
     * warning instead of launching a silent no-food death run (issue #1960).
     */
    fun hasUsableFood(
        equippedKeys: Set<String>,
        inventory: Map<String, Int>,
        pending: Map<String, Int>,
    ): Boolean = available(inventory, equippedKeys, pending).isNotEmpty()
}
