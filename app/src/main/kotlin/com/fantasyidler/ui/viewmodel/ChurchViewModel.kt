package com.fantasyidler.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.R
import com.fantasyidler.data.json.BlessingData
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.BlessingActivateResult
import com.fantasyidler.repository.ChurchRepository
import com.fantasyidler.repository.PlayerRepository
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

data class ChurchUiState(
    val isLoading: Boolean = true,
    val prayerLevel: Int = 1,
    val churchTier: Int = 0,
    val allBlessings: List<BlessingData> = emptyList(),
    val unlockedBlessingKeys: Set<String> = emptySet(),
    val activeBlessing: BlessingData? = null,
    val activeBlessingRemainingMs: Long = 0L,
    val totalBoneEquivalent: Int = 0,
    val pendingBlessingKey: String? = null,
    val showDeactivateConfirm: Boolean = false,
    val snackbarMessage: String? = null,
)

@HiltViewModel
class ChurchViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val churchRepo: ChurchRepository,
    private val json: Json,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _extra = MutableStateFlow(ChurchUiState())

    val uiState: StateFlow<ChurchUiState> = combine(
        playerRepo.playerFlow,
        _extra,
    ) { player, extra ->
        if (player == null) return@combine extra.copy(isLoading = true)
        val flags: PlayerFlags          = json.decodeFromString(player.flags)
        val levels: Map<String, Int>    = json.decodeFromString(player.skillLevels)
        val inventory: Map<String, Int> = json.decodeFromString(player.inventory)
        val prayerLevel = levels[Skills.PRAYER] ?: 1
        val churchTier  = flags.townBuildingTiers["church"] ?: 0
        val active      = ChurchRepository.activeBlessing(flags)
        val remaining   = if (active != null) (flags.activeBlessingExpiresAt - System.currentTimeMillis()).coerceAtLeast(0L) else 0L
        extra.copy(
            isLoading                 = false,
            prayerLevel               = prayerLevel,
            churchTier                = churchTier,
            allBlessings              = ChurchRepository.ALL_BLESSINGS,
            unlockedBlessingKeys      = churchRepo.blessingsForLevel(prayerLevel).map { it.key }.toSet(),
            activeBlessing            = active,
            activeBlessingRemainingMs = remaining,
            totalBoneEquivalent       = ChurchRepository.totalBoneEquivalent(inventory),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChurchUiState())

    fun activateBlessing(key: String) {
        _extra.update { it.copy(pendingBlessingKey = key) }
    }

    fun confirmActivate() {
        val key = _extra.value.pendingBlessingKey ?: return
        _extra.update { it.copy(pendingBlessingKey = null) }
        viewModelScope.launch {
            when (val result = churchRepo.activateBlessing(key)) {
                is BlessingActivateResult.Success -> {}
                is BlessingActivateResult.AlreadyActive ->
                    _extra.update { it.copy(snackbarMessage = context.getString(R.string.church_already_active)) }
                is BlessingActivateResult.NotEnoughBones ->
                    _extra.update { it.copy(snackbarMessage = context.getString(R.string.church_not_enough_bones, result.needed)) }
            }
        }
    }

    fun dismissConfirm() = _extra.update { it.copy(pendingBlessingKey = null) }

    fun deactivateBlessing() {
        _extra.update { it.copy(showDeactivateConfirm = true) }
    }

    fun confirmDeactivate() {
        _extra.update { it.copy(showDeactivateConfirm = false) }
        viewModelScope.launch {
            churchRepo.deactivateBlessing()
        }
    }

    fun dismissDeactivate() {
        _extra.update { it.copy(showDeactivateConfirm = false) }
    }

    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }
}
