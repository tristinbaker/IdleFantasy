package com.fantasyidler.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.R
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.TownRepository
import com.fantasyidler.repository.UpgradeBuildingResult
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

data class BuilderUiState(
    val isLoading: Boolean = true,
    val constructionLevel: Int = 1,
    val coins: Long = 0L,
    val inventory: Map<String, Int> = emptyMap(),
    val innTier: Int = 0,
    val guildHallTier: Int = 0,
    val churchTier: Int = 0,
    val fairgroundsTier: Int = 0,
    val gardenTier: Int = 0,
    val queueMasterTier: Int = 0,
    val snackbarMessage: String? = null,
)

@HiltViewModel
class BuilderViewModel @Inject constructor(
    val gameData: GameDataRepository,
    val townRepo: TownRepository,
    private val playerRepo: PlayerRepository,
    @ApplicationContext private val context: Context,
    private val json: Json,
) : ViewModel() {

    private val _extra = MutableStateFlow(BuilderUiState())

    val uiState: StateFlow<BuilderUiState> = combine(
        playerRepo.playerFlow,
        _extra,
    ) { player, extra ->
        if (player == null) return@combine extra.copy(isLoading = true)
        val flags: PlayerFlags          = json.decodeFromString(player.flags)
        val levels: Map<String, Int>    = json.decodeFromString(player.skillLevels)
        val inventory: Map<String, Int> = json.decodeFromString(player.inventory)
        extra.copy(
            isLoading         = false,
            constructionLevel = levels[Skills.CONSTRUCTION] ?: 1,
            coins             = player.coins,
            inventory         = inventory,
            innTier           = flags.townBuildingTiers["inn"] ?: 0,
            guildHallTier     = flags.townBuildingTiers["guild_hall"] ?: 0,
            churchTier        = flags.townBuildingTiers["church"] ?: 0,
            fairgroundsTier   = flags.townBuildingTiers["fairgrounds"] ?: 0,
            gardenTier        = flags.townBuildingTiers["garden"] ?: 0,
            queueMasterTier   = flags.townBuildingTiers["queue_master"] ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BuilderUiState())

    fun upgrade(buildingKey: String) {
        viewModelScope.launch {
            val currentTier = when (buildingKey) {
                "inn"          -> uiState.value.innTier
                "guild_hall"   -> uiState.value.guildHallTier
                "fairgrounds"  -> uiState.value.fairgroundsTier
                "garden"       -> uiState.value.gardenTier
                "queue_master" -> uiState.value.queueMasterTier
                else           -> uiState.value.churchTier
            }
            val def = gameData.townBuildings[buildingKey]
            when (townRepo.upgradeBuilding(buildingKey)) {
                UpgradeBuildingResult.Success ->
                    _extra.update { it.copy(snackbarMessage = context.getString(R.string.town_upgrade_success)) }
                UpgradeBuildingResult.InsufficientLevel -> {
                    val req = def?.tiers?.getOrNull(currentTier)?.constructionLevelRequired ?: 0
                    _extra.update { it.copy(snackbarMessage = context.getString(R.string.town_upgrade_fail_level, req)) }
                }
                UpgradeBuildingResult.InsufficientCoins ->
                    _extra.update { it.copy(snackbarMessage = context.getString(R.string.town_upgrade_fail_coins)) }
                UpgradeBuildingResult.InsufficientMaterials ->
                    _extra.update { it.copy(snackbarMessage = context.getString(R.string.town_upgrade_fail_mats)) }
                else -> {}
            }
        }
    }

    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }
}
