package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.ui.scene.SceneEvent
import com.fantasyidler.ui.scene.SceneEventBus
import com.fantasyidler.ui.screen.minigame.smithing.MinigameResult
import com.fantasyidler.ui.screen.minigame.smithing.SmithingPhase
import com.fantasyidler.ui.screen.minigame.smithing.SmithingUiState
import com.fantasyidler.ui.screen.minigame.smithing.TapResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Smithing minigame orchestration.
 *
 *  - Owns the phase FSM (HAMMER → HEAT → QUENCH → COMPLETE) and per-phase
 *    scores.
 *  - Holds the [SceneEventBus] the screen passes to its hosted [Stage] so we
 *    can emit anvil hits, sword glow, and the steam arc into the scene.
 *  - On COMPLETE: applies the reward. If an active smithing session exists,
 *    fast-forwards its `endsAt` (with a 2× relevance multiplier). Otherwise
 *    awards one scaled `bronze_sword` craft via PlayerRepository.
 */
@HiltViewModel
class SmithingMinigameViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
) : ViewModel() {

    val sceneBus = SceneEventBus()

    private val _uiState = MutableStateFlow(SmithingUiState())
    val uiState: StateFlow<SmithingUiState> = _uiState.asStateFlow()

    fun onHammerTap(classification: TapResult) {
        // Decode tap quality back into an "amount" for the scene config's
        // Hit event factory: Perfect=2 → Medium shake + 6 sparks, Good=1 →
        // Small shake + 3 sparks, Miss=0 → muted Attempt.
        viewModelScope.launch {
            val amount = when (classification) {
                TapResult.Perfect -> 2
                TapResult.Good    -> 1
                TapResult.Miss    -> 0
            }
            if (amount > 0) {
                sceneBus.emit(SceneEvent.Hit(attacker = "hammer", target = "anvil", amount = amount))
            } else {
                sceneBus.emit(SceneEvent.Attempt(toolTag = "hammer"))
            }
        }
    }

    fun onHammerComplete(score: Float) {
        _uiState.update { it.copy(hammerScore = score, phase = SmithingPhase.HEAT) }
    }

    fun onHeatComplete(score: Float) {
        // Successful heating swaps the sword sprite.
        val swordSprite = if (score >= 0.5f) "smithy_sword_hot" else "smithy_sword_cold"
        _uiState.update {
            it.copy(heatScore = score, phase = SmithingPhase.QUENCH, swordEntityId = swordSprite)
        }
    }

    fun onQuenchComplete(score: Float) {
        val didQuench = score >= 0.4f
        val finalSprite = when {
            didQuench                                  -> "smithy_sword_finished"
            _uiState.value.swordEntityId == "smithy_sword_hot" -> "smithy_sword_hot"
            else                                       -> "smithy_sword_cold"
        }
        viewModelScope.launch {
            if (didQuench) {
                sceneBus.emit(SceneEvent.Produce(item = "smithy_water_bucket", fromTag = "water_bucket"))
            }
            val total = (uiState.value.hammerScore + uiState.value.heatScore + score) / 3f
            if (total >= 0.92f) sceneBus.emit(SceneEvent.LevelUp(skill = "smithing"))

            val rewardResult = applyReward(totalScore = total)

            _uiState.update {
                it.copy(
                    quenchScore    = score,
                    phase          = SmithingPhase.COMPLETE,
                    swordEntityId  = finalSprite,
                    result         = rewardResult,
                )
            }
        }
    }

    /**
     * Branches on session presence:
     *  - Active smithing session → fastForward(skill="smithing", ms) ×2 relevance
     *  - No session              → award one scaled bronze_sword craft
     *  - Score < threshold       → NoReward (player can retry)
     */
    private suspend fun applyReward(totalScore: Float): MinigameResult {
        if (totalScore <= 0.05f) return MinigameResult.NoReward

        val baseSkipMs = 60_000L
        val rawSkipMs = (totalScore * baseSkipMs.toDouble()).toLong()

        val active = sessionRepo.getActiveSession()
        if (active != null && !active.completed && active.skillName == "smithing") {
            val multiplier = 2L
            val requested = rawSkipMs * multiplier
            val applied = sessionRepo.fastForward(skill = "smithing", ms = requested)
            return if (applied > 0L) {
                MinigameResult.SessionAccelerated(appliedMs = applied)
            } else {
                MinigameResult.SessionAcceleratedNoBoost(appliedMs = 0L)
            }
        }

        // No matching active session: mint a scaled bronze_sword craft. The
        // canonical recipe entry sources its XP from the data file so re-tuning
        // the recipe automatically retunes the fallback.
        val recipe = gameData.smithingRecipes["bronze_sword"]
        val xpPerItem = recipe?.xpPerItem ?: 12.0
        val xp = (xpPerItem * totalScore).toLong().coerceAtLeast(1L)
        val itemsAwarded: Map<String, Int> = if (totalScore >= 0.5f) {
            mapOf("bronze_sword" to 1)
        } else {
            emptyMap()
        }
        playerRepo.applySessionResults(
            skillName   = "smithing",
            xpGained    = xp,
            itemsGained = itemsAwarded,
        )
        return MinigameResult.Fallback(
            xpAwarded   = xp,
            itemAwarded = itemsAwarded.keys.firstOrNull(),
        )
    }
}
