package com.fantasyidler.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.R
import com.fantasyidler.data.json.SeasonalEventData
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.SeasonalBountyTaskWithProgress
import com.fantasyidler.repository.SeasonalEventRepository
import com.fantasyidler.repository.SeasonalMinigameResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class SeasonalEventUiState(
    val isLoading: Boolean = true,
    val event: SeasonalEventData? = null,
    val tokens: Int = 0,
    val bountyTasks: List<SeasonalBountyTaskWithProgress> = emptyList(),
    val minigameCooldownAt: Long = 0L,
    val snackbarMessage: String? = null,
)

@HiltViewModel
class SeasonalEventViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerRepo: PlayerRepository,
    private val gameData: GameDataRepository,
    private val seasonalEventRepo: SeasonalEventRepository,
    private val json: Json,
) : ViewModel() {

    private val _extra = MutableStateFlow(SeasonalEventUiState())

    init {
        viewModelScope.launch { seasonalEventRepo.ensureBountySlotsRefreshed() }
    }

    val uiState: StateFlow<SeasonalEventUiState> = combine(
        playerRepo.playerFlow,
        _extra,
    ) { player, extra ->
        val event = seasonalEventRepo.activeEvent()
        if (player == null || event == null) {
            extra.copy(isLoading = false, event = null)
        } else {
            val flags: PlayerFlags = try { json.decodeFromString(player.flags) } catch (_: Exception) { PlayerFlags() }
            extra.copy(
                isLoading          = false,
                event              = event,
                tokens             = flags.seasonalTokensByEvent[event.id] ?: 0,
                bountyTasks        = seasonalEventRepo.bountyTasksWithProgress(event, flags),
                minigameCooldownAt = flags.seasonalMinigameCooldownAt,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeasonalEventUiState())

    fun claimBountyTask(taskId: String) {
        viewModelScope.launch { seasonalEventRepo.claimBountyTask(taskId) }
    }

    /** Called by the Bounty Board's per-slot countdown once it reaches zero, so the new task appears live. */
    fun refreshBountySlots() {
        viewModelScope.launch { seasonalEventRepo.ensureBountySlotsRefreshed() }
    }

    /** [won] is whether the player landed enough hits during the whack-a-mole rounds. */
    fun submitMinigameAttempt(won: Boolean) {
        viewModelScope.launch {
            when (seasonalEventRepo.submitMinigameAttempt(won)) {
                is SeasonalMinigameResult.Success -> _extra.update { it.copy(snackbarMessage = context.getString(R.string.seasonal_minigame_success)) }
                is SeasonalMinigameResult.Failure -> _extra.update { it.copy(snackbarMessage = context.getString(R.string.seasonal_minigame_failure)) }
                is SeasonalMinigameResult.OnCooldown, SeasonalMinigameResult.NoActiveEvent -> {}
            }
        }
    }

    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }

    fun dungeonDisplayName(key: String): String = gameData.dungeons[key]?.displayName ?: key
    fun bossDisplayName(key: String): String = gameData.bosses[key]?.displayName ?: key
}
