package com.fantasyidler.ui.viewmodel

import com.fantasyidler.util.withAppLocale

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.R
import com.fantasyidler.data.json.BlessingData
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.BlessingActivateResult
import com.fantasyidler.repository.BoostRepository
import com.fantasyidler.repository.ChurchRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.blessingPrayerCapeMult
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.TownRepository
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
    val blessingDuration: Long = 0,
    val allBlessings: List<BlessingData> = emptyList(),
    val unlockedBlessingKeys: Set<String> = emptySet(),
    val activeBlessing: BlessingData? = null,
    val activeBlessingRemainingMs: Long = 0L,
    val prayerCapeMult: Float = 1f,
    val totalBoneEquivalent: Int = 0,
    val totalBoneCount: Int = 0,
    val showDeactivateConfirm: Boolean = false,
    val snackbarMessage: String? = null,
    /** Ironman characters can only use defensive blessings. */
    val ironman: Boolean = false,
    /** Bone-cost multiplier from prestige (gnome Trickster's Favor). */
    val blessingCostMult: Double = 1.0,
)

@HiltViewModel
class ChurchViewModel @Inject constructor(
    private val boostRepo: BoostRepository,
    val townRepo: TownRepository,
    private val playerRepo: PlayerRepository,
    private val gameData: GameDataRepository,
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
        val prayerCapeMult = blessingPrayerCapeMult(player, flags, gameData)
        val active      = ChurchRepository.activeBlessing(flags, gameData.blessings)
        val remaining   = if (active != null) (flags.activeBlessingExpiresAt - System.currentTimeMillis()).coerceAtLeast(0L) else 0L
        extra.copy(
            isLoading                 = false,
            prayerLevel               = prayerLevel,
            blessingDuration          = (townRepo.blessingDurationMs(flags) * boostRepo.blessingDurationMultiplier(flags)).toLong(),
            blessingCostMult          = boostRepo.blessingCostMultiplier(flags),
            allBlessings              = gameData.blessings,
            unlockedBlessingKeys      = churchRepo.blessingsForLevel(prayerLevel).map { it.key }.toSet(),
            activeBlessing            = active,
            activeBlessingRemainingMs = remaining,
            prayerCapeMult            = prayerCapeMult,
            totalBoneEquivalent       = ChurchRepository.totalBoneEquivalent(inventory),
            totalBoneCount            = ChurchRepository.totalBoneCount(inventory),
            ironman                   = flags.ironman,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChurchUiState())

    fun activateBlessing(key: String) {
        val wasActive = uiState.value.activeBlessing?.key == key
        viewModelScope.launch {
            when (val result = churchRepo.activateBlessing(key)) {
                is BlessingActivateResult.Success -> {
                    val msgRes = if (wasActive) R.string.church_blessing_extended else R.string.church_blessing_activated
                    _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(msgRes)) }
                }
                is BlessingActivateResult.AlreadyActive ->
                    _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.church_already_active)) }
                is BlessingActivateResult.NotEnoughBones ->
                    _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.church_not_enough_bones, result.needed)) }
                is BlessingActivateResult.LevelTooLow ->
                    _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.church_locked_level, result.requiredLevel)) }
                is BlessingActivateResult.IronmanBlocked ->
                    _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.ironman_blessing_blocked)) }
            }
        }
    }

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
