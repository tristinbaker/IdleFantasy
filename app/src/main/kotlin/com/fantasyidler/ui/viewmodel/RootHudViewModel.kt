package com.fantasyidler.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * The narrow slice of player state the persistent top HUD displays. Lives at the
 * root scaffold and survives tab switches, so it's intentionally kept thin — only
 * what the three HUD regions need.
 */
@Immutable
data class RootHudUiState(
    val isLoading: Boolean = true,
    val coins: Long = 0L,
    val combatLevel: Int = 1,
    val activeSession: SkillSession? = null,
)

@HiltViewModel
class RootHudViewModel @Inject constructor(
    playerRepo: PlayerRepository,
    sessionRepo: SessionRepository,
    private val json: Json,
) : ViewModel() {

    val uiState: StateFlow<RootHudUiState> = combine(
        playerRepo.playerFlow,
        sessionRepo.activeSessionFlow,
    ) { player, session ->
        if (player == null) {
            RootHudUiState(isLoading = true, activeSession = session)
        } else {
            val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            RootHudUiState(
                isLoading     = false,
                coins         = player.coins,
                combatLevel   = combatLevelFrom(levels),
                activeSession = session,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootHudUiState())
}
