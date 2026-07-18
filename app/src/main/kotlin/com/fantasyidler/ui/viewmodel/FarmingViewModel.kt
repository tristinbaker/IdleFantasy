package com.fantasyidler.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.R
import com.fantasyidler.data.json.CropData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.FarmingPatch
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.FarmingRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.GuildRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.TownRepository
import com.fantasyidler.simulator.XpTable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
    /** patchNumber.toString() → ash key applied as fertilizer. */
    val fertilizer:     Map<String, String>    = emptyMap(),
    val availableCrops: List<CropData>         = emptyList(),
    val allCrops:       Map<String, CropData>  = emptyMap(),
    /** Epoch-ms "now" updated every 10 seconds for time-remaining calculations. */
    val now:            Long                   = System.currentTimeMillis(),
    val snackbarMessage: String?               = null,
    val isLoading:      Boolean                = true,
    /** Non-null when the plant sheet should be open for a specific patch number. */
    val plantingPatchNumber: Int?              = null,
    /** Ash key last used as fertilizer; pre-selected when the plant sheet opens. */
    val lastFertilizerKey: String?             = null,
    /** Just-harvested result, shown briefly then cleared. */
    val harvestResult: HarvestResult?          = null,
    /** True once the magic bean has been planted; used to hide it from the seed picker permanently. */
    val magicBeanPlanted: Boolean              = false,
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
    @ApplicationContext private val context: Context,
    private val farmingRepo: FarmingRepository,
    private val playerRepo: PlayerRepository,
    private val guildRepo: GuildRepository,
    private val gameData: GameDataRepository,
    private val townRepo: TownRepository,
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
        val flags = try { json.decodeFromString<com.fantasyidler.data.model.PlayerFlags>(player.flags) } catch (_: Exception) { com.fantasyidler.data.model.PlayerFlags() }

        val farmingLevel = levels[Skills.FARMING] ?: 1
        val farmingXp    = xpMap[Skills.FARMING]  ?: 0L
        val patchCount   = farmingRepo.patchCountForLevel(farmingLevel) + townRepo.extraFarmPlots(flags)

        val availableCrops = gameData.crops.values
            .filter { it.levelRequired <= farmingLevel }
            .filter { it.id != "magic_bean" || (!flags.magicBeanPlanted && (inv["magic_bean"] ?: 0) > 0) }
            .sortedBy { it.levelRequired }

        extra.copy(
            isLoading        = false,
            farmingLevel     = farmingLevel,
            farmingXp        = farmingXp,
            patchCount       = patchCount,
            patches          = patches,
            inventory        = inv,
            availableCrops   = availableCrops,
            allCrops         = gameData.crops,
            fertilizer       = flags.farmingFertilizer,
            lastFertilizerKey = flags.lastFertilizerKey,
            magicBeanPlanted = flags.magicBeanPlanted,
            now              = now,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FarmingUiState())

    // ------------------------------------------------------------------

    fun openPlantSheet(patchNumber: Int) = _extra.update { it.copy(plantingPatchNumber = patchNumber) }
    fun closePlantSheet()               = _extra.update { it.copy(plantingPatchNumber = null) }

    fun harvestAndPlantAll() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val finishedPatches = uiState.value.patches.filter { it.cropType != "magic_bean" && it.remainingMs(gameData.crops, now) <= 0 }

            if (finishedPatches.isNotEmpty()) {
                val playerBefore = playerRepo.getOrCreatePlayer()
                val invBefore = json.decodeFromString<Map<String, Int>>(playerBefore.inventory)
                val xpBefore = json.decodeFromString<Map<String, Long>>(playerBefore.skillXp)[Skills.FARMING] ?: 0L

                farmingRepo.harvestPatches(finishedPatches.map { it.patchNumber })

                val playerAfter = playerRepo.getOrCreatePlayer()
                val invAfter = json.decodeFromString<Map<String, Int>>(playerAfter.inventory)
                val xpAfter = json.decodeFromString<Map<String, Long>>(playerAfter.skillXp)[Skills.FARMING] ?: 0L

                val gained = invAfter.mapValues { (k, v) -> v - (invBefore[k] ?: 0) }.filter { it.value > 0 }
                guildRepo.recordGuildGathering(Skills.FARMING, gained)
                _extra.update { it.copy(harvestResult = HarvestResult(context.getString(R.string.farming_harvest_and_plant), gained, xpAfter - xpBefore)) }
                delay(300)
            }

            val emptyPatches = farmingRepo.getEmptyPatches(uiState.value.patchCount)
            if (emptyPatches.isNotEmpty()) {
                _extra.update { it.copy(plantingPatchNumber = -1) }
            } else if (finishedPatches.isEmpty()) {
                _extra.update { it.copy(snackbarMessage = context.getString(R.string.farming_no_crops_ready)) }
            }
        }
    }

    fun plantCrop(patchNumber: Int, crop: CropData, ashKey: String? = null) {
        viewModelScope.launch {
            closePlantSheet()
            val flags = playerRepo.getFlags()
            if (flags.lastFertilizerKey != ashKey) {
                playerRepo.updateFlags(flags.copy(lastFertilizerKey = ashKey))
            }
            val seedName = crop.seedName.replace('_', ' ')

            if (patchNumber == -1) {
                delay(300)
                val emptyPatches = farmingRepo.getEmptyPatches(uiState.value.patchCount)

                if (crop.id == "magic_bean") {
                    val planted = emptyPatches.firstOrNull()?.let { farmingRepo.plantCrop(it, crop, ashKey) } ?: false
                    val msg = if (planted) context.getString(R.string.farming_magic_bean_one_plot)
                              else context.getString(R.string.farming_no_seeds_inventory, seedName)
                    _extra.update { it.copy(snackbarMessage = msg) }
                } else {
                    val plantedCount = farmingRepo.plantCrops(emptyPatches, crop, ashKey)
                    val msg = if (plantedCount == 0) {
                        if (emptyPatches.isEmpty()) context.getString(R.string.farming_no_empty_patches)
                        else context.getString(R.string.farming_no_seeds_inventory, seedName)
                    } else {
                        val base = context.getString(R.string.farming_planted, plantedCount, seedName)
                        if (plantedCount < emptyPatches.size) "$base - ${context.getString(R.string.farming_no_seeds_inventory, seedName)}" else base
                    }
                    _extra.update { it.copy(snackbarMessage = msg) }
                }
            } else {
                if (!farmingRepo.plantCrop(patchNumber, crop, ashKey)) {
                    _extra.update { it.copy(snackbarMessage = context.getString(R.string.farming_no_seeds_inventory, seedName)) }
                }
            }
        }
    }

    fun climbBeanstalk(patchNumber: Int) {
        viewModelScope.launch {
            farmingRepo.climbBeanstalk(patchNumber)
            _extra.update { it.copy(snackbarMessage = context.getString(R.string.farming_bean_climbed)) }
        }
    }

    fun harvestPatch(patchNumber: Int) {
        viewModelScope.launch {
            val patch = uiState.value.patches.firstOrNull { it.patchNumber == patchNumber } ?: return@launch
            if (patch.cropType == "magic_bean") return@launch
            val crop  = gameData.crops[patch.cropType] ?: return@launch

            // Snapshot inventory before harvest to compute diff
            val invBefore: Map<String, Int> = json.decodeFromString(playerRepo.getOrCreatePlayer().inventory)
            val xpBefore = (json.decodeFromString<Map<String, Long>>(playerRepo.getOrCreatePlayer().skillXp))[Skills.FARMING] ?: 0L

            farmingRepo.harvestPatch(patchNumber)

            val player   = playerRepo.getOrCreatePlayer()
            val invAfter: Map<String, Int> = json.decodeFromString(player.inventory)
            val xpAfter  = (json.decodeFromString<Map<String, Long>>(player.skillXp))[Skills.FARMING] ?: 0L

            val gained = invAfter.mapValues { (k, v) -> v - (invBefore[k] ?: 0) }.filter { it.value > 0 }
            guildRepo.recordGuildGathering(Skills.FARMING, gained)
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
