package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.json.CropData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.FarmingPatch
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.FarmingRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.simulator.XpTable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

data class FarmingUiState(
    val farmingLevel:   Int                    = 1,
    val farmingXp:      Long                   = 0L,
    val patchCount:     Int                    = 3,
    val patches:        List<FarmingPatch>     = emptyList(),
    val inventory:      Map<String, Int>       = emptyMap(),
    val availableCrops: List<CropData>         = emptyList(),
    /** Epoch-ms "now" updated every 10 seconds for time-remaining calculations. */
    val now:            Long                   = System.currentTimeMillis(),
    val snackbarMessage: String?               = null,
    val isLoading:      Boolean                = true,
    /** Non-null when the plant sheet should be open for a specific patch number. */
    val plantingPatchNumber: Int?              = null,
    /** Just-harvested result, shown briefly then cleared. */
    val harvestResult: HarvestResult?          = null,
)

data class HarvestResult(
    val cropName: String,
    val itemsGained: Map<String, Int>,
    val xpGained: Long,
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class FarmingViewModel @Inject constructor(
    private val farmingRepo: FarmingRepository,
    private val playerRepo: PlayerRepository,
    private val gameData: GameDataRepository,
    private val json: Json,
) : ViewModel() {

    private val _extra = MutableStateFlow(FarmingUiState())

    /** Ticks every 10 seconds so time-remaining labels stay live. */
    private val nowFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(10_000L)
        }
    }

    val uiState: StateFlow<FarmingUiState> = combine(
        playerRepo.playerFlow,
        farmingRepo.observePatches(),
        nowFlow,
        _extra,
    ) { player, patches, now, extra ->
        if (player == null) return@combine extra.copy(isLoading = true, now = now)

        val levels: Map<String, Int>  = json.decodeFromString(player.skillLevels)
        val xpMap:  Map<String, Long> = json.decodeFromString(player.skillXp)
        val inv:    Map<String, Int>  = json.decodeFromString(player.inventory)

        val farmingLevel = levels[Skills.FARMING] ?: 1
        val farmingXp    = xpMap[Skills.FARMING]  ?: 0L
        val patchCount   = farmingRepo.patchCountForLevel(farmingLevel)

        val availableCrops = gameData.crops.values
            .filter { it.levelRequired <= farmingLevel }
            .sortedBy { it.levelRequired }

        extra.copy(
            isLoading      = false,
            farmingLevel   = farmingLevel,
            farmingXp      = farmingXp,
            patchCount     = patchCount,
            patches        = patches,
            inventory      = inv,
            availableCrops = availableCrops,
            now            = now,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FarmingUiState())

    // ------------------------------------------------------------------

    fun openPlantSheet(patchNumber: Int) = _extra.update { it.copy(plantingPatchNumber = patchNumber) }
    fun closePlantSheet()               = _extra.update { it.copy(plantingPatchNumber = null) }

    fun plantCrop(patchNumber: Int, crop: CropData) {
        viewModelScope.launch {
            closePlantSheet()
            val ok = farmingRepo.plantCrop(patchNumber, crop)
            if (!ok) _extra.update { it.copy(snackbarMessage = "No ${crop.seedName.replace('_', ' ')} in inventory") }
        }
    }

    fun harvestPatch(patchNumber: Int) {
        viewModelScope.launch {
            val patch = uiState.value.patches.firstOrNull { it.patchNumber == patchNumber } ?: return@launch
            val crop  = gameData.crops[patch.cropType] ?: return@launch

            // Snapshot inventory before harvest to compute diff
            val invBefore: Map<String, Int> = json.decodeFromString(playerRepo.getOrCreatePlayer().inventory)
            val xpBefore = (json.decodeFromString<Map<String, Long>>(playerRepo.getOrCreatePlayer().skillXp))[Skills.FARMING] ?: 0L

            farmingRepo.harvestPatch(patchNumber)

            val player   = playerRepo.getOrCreatePlayer()
            val invAfter: Map<String, Int> = json.decodeFromString(player.inventory)
            val xpAfter  = (json.decodeFromString<Map<String, Long>>(player.skillXp))[Skills.FARMING] ?: 0L

            val gained = invAfter.mapValues { (k, v) -> v - (invBefore[k] ?: 0) }.filter { it.value > 0 }
            _extra.update {
                it.copy(harvestResult = HarvestResult(crop.displayName, gained, xpAfter - xpBefore))
            }
        }
    }

    fun clearPatch(patchNumber: Int) {
        viewModelScope.launch { farmingRepo.clearPatch(patchNumber) }
    }

    fun harvestResultConsumed() = _extra.update { it.copy(harvestResult = null) }
    fun snackbarConsumed()      = _extra.update { it.copy(snackbarMessage = null) }
}

/** Remaining ms until a patch is ready. Negative means ready. */
fun FarmingPatch.remainingMs(crops: Map<String, CropData>, now: Long): Long {
    val plantedAt = plantedAt ?: return Long.MAX_VALUE
    val crop      = crops[cropType] ?: return Long.MAX_VALUE
    return (plantedAt + crop.growthTimeMs) - now
}
