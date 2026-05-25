package com.fantasyidler.ui.screen.minigame.smithing

import androidx.compose.runtime.Immutable

/** The four states of the smithing minigame FSM. */
enum class SmithingPhase { HAMMER, HEAT, QUENCH, COMPLETE }

/**
 * UI-side state for the smithing minigame screen.
 *
 * Score fields are `[0..1]` and start at 0 — they're only meaningful after
 * the corresponding phase has reported. [swordEntityId] is the visual that
 * appears on the anvil in the Stage; the screen reads it and rebuilds the
 * SceneConfig on phase changes.
 *
 * [result] is non-null only when `phase == COMPLETE`.
 */
@Immutable
data class SmithingUiState(
    val phase: SmithingPhase = SmithingPhase.HAMMER,
    val hammerScore: Float = 0f,
    val heatScore: Float = 0f,
    val quenchScore: Float = 0f,
    val swordEntityId: String = "smithy_sword_cold",
    val result: MinigameResult? = null,
)

/**
 * Outcome of the post-minigame reward step. The result overlay reads this to
 * pick which summary line and which fanfare to show.
 */
sealed interface MinigameResult {
    /** Active smithing session was accelerated by [appliedMs]. ×2 relevance was applied upstream. */
    data class SessionAccelerated(val appliedMs: Long) : MinigameResult

    /**
     * Active smithing session existed but the fast-forward applied 0ms — typically
     * because the session was already past its end. Surfaces a softer message.
     */
    data class SessionAcceleratedNoBoost(val appliedMs: Long) : MinigameResult

    /** No matching session; awarded a scaled bronze_sword craft. */
    data class Fallback(
        val xpAwarded: Long,
        val itemAwarded: String?,
    ) : MinigameResult

    /** Total score too low to award anything. */
    data object NoReward : MinigameResult
}

/** Score chip thresholds shared between the result card and any future per-phase callouts. */
val PERFECT_THRESHOLD: Float = 0.85f
val GOOD_THRESHOLD: Float = 0.45f
