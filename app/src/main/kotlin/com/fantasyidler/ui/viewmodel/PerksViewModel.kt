package com.fantasyidler.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.model.PerkState
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.perks.PerkCategory
import com.fantasyidler.data.perks.PerkRepository
import com.fantasyidler.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class PerksViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val perkRepo: PerkRepository,
    private val json: Json,
) : ViewModel() {

    @Immutable
    data class UiState(
        val isLoading: Boolean = true,
        val snapshot: PerkRepository.Snapshot = PerkRepository.Snapshot(
            earnedAp        = 0,
            earnedGathering = 0,
            earnedCrafting  = 0,
            earnedCombat    = 0,
            state           = PerkState(),
        ),
        val selectedCategory: PerkCategory = PerkCategory.ADVANTAGE,
        val snackbarMessage: String? = null,
    )

    private val _extra = MutableStateFlow(UiState())

    val uiState: StateFlow<UiState> = combine(
        playerRepo.playerFlow,
        _extra,
    ) { player, extra ->
        if (player == null) extra.copy(isLoading = true)
        else {
            val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            val flags : PlayerFlags      = json.decodeFromString(player.flags)
            val snap = perkRepo.buildSnapshot(levels, flags.perks)
            extra.copy(isLoading = false, snapshot = snap)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun selectCategory(category: PerkCategory) {
        _extra.update { it.copy(selectedCategory = category) }
    }

    fun purchase(perkId: String) {
        viewModelScope.launch {
            val ok = perkRepo.purchase(perkId)
            _extra.update { it.copy(snackbarMessage = if (ok) "Perk purchased." else "Not enough points.") }
        }
    }

    fun snackbarConsumed() {
        _extra.update { it.copy(snackbarMessage = null) }
    }
}
