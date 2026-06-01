package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.R
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.Skills
import com.fantasyidler.data.model.SlayerTask
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.SlayerRepository
import android.content.Context
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

data class PendingLamp(val xpAmount: Long, val cost: Int)

data class SlayerUiState(
    val isLoading: Boolean = true,
    val slayerLevel: Int = 1,
    val slayerXp: Long = 0L,
    val slayerPoints: Int = 0,
    val activeTask: SlayerTask? = null,
    /** Dungeon display names that contain the active task's enemy. */
    val taskDungeons: List<String> = emptyList(),
    val inventory: Map<String, Int> = emptyMap(),
    val skillLevels: Map<String, Int> = emptyMap(),
    /** Non-null when the player has tapped Buy on a lamp and needs to choose a skill. */
    val pendingLamp: PendingLamp? = null,
    val snackbarMessage: String? = null,
)

@HiltViewModel
class SlayerViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val slayerRepo: SlayerRepository,
    val gameData: GameDataRepository,
    @ApplicationContext private val context: Context,
    private val json: Json,
) : ViewModel() {

    /** Equipment data keyed by item key, for the shop stats display. */
    val shopEquipment: Map<String, EquipmentData> by lazy {
        listOf("slayer_helm", "abyssal_whip", "slayer_platebody", "slayer_platelegs")
            .mapNotNull { key -> gameData.equipment[key]?.let { key to it } }
            .toMap()
    }

    private val _extra = MutableStateFlow(SlayerUiState())

    val uiState: StateFlow<SlayerUiState> = combine(
        playerRepo.playerFlow,
        _extra,
    ) { player, extra ->
        if (player == null) extra.copy(isLoading = true)
        else {
            val levels:    Map<String, Int>  = json.decodeFromString(player.skillLevels)
            val xpMap:     Map<String, Long> = json.decodeFromString(player.skillXp)
            val flags:     PlayerFlags       = json.decodeFromString(player.flags)
            val inventory: Map<String, Int>  = json.decodeFromString(player.inventory)
            val taskDungeons = flags.activeSlayerTask?.enemyKey?.let { key ->
                gameData.dungeons.values
                    .filter { d -> d.enemySpawns.any { it.enemy == key } }
                    .map { it.displayName }
            } ?: emptyList()
            extra.copy(
                isLoading    = false,
                slayerLevel  = levels[Skills.SLAYER] ?: 1,
                slayerXp     = xpMap[Skills.SLAYER] ?: 0L,
                slayerPoints = flags.slayerPoints,
                activeTask   = flags.activeSlayerTask,
                taskDungeons = taskDungeons,
                inventory    = inventory,
                skillLevels  = levels,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SlayerUiState())

    fun getNewTask() {
        viewModelScope.launch {
            val state = uiState.value
            if (state.activeTask != null) return@launch
            val success = slayerRepo.assignTask(state.slayerLevel)
            if (!success) {
                _extra.update { it.copy(snackbarMessage = context.getString(R.string.slayer_no_eligible_tasks)) }
            }
        }
    }

    fun skipTask() {
        viewModelScope.launch {
            val state = uiState.value
            val success = slayerRepo.skipTask(state.slayerLevel)
            if (!success) {
                _extra.update { it.copy(snackbarMessage = context.getString(R.string.slayer_not_enough_points)) }
            }
        }
    }

    /** Called when the player taps Buy on a lamp — shows the skill picker instead of buying immediately. */
    fun showLampPicker(xpAmount: Long, cost: Int) {
        if (uiState.value.slayerPoints < cost) {
            _extra.update { it.copy(snackbarMessage = context.getString(R.string.slayer_not_enough_points)) }
            return
        }
        _extra.update { it.copy(pendingLamp = PendingLamp(xpAmount, cost)) }
    }

    fun dismissLampPicker() = _extra.update { it.copy(pendingLamp = null) }

    fun selectLampSkill(skillKey: String) {
        val lamp = _extra.value.pendingLamp ?: return
        _extra.update { it.copy(pendingLamp = null) }
        viewModelScope.launch {
            val success = slayerRepo.spendPointsForXp(skillKey, lamp.xpAmount, lamp.cost)
            _extra.update {
                val skillName = skillKey.split('_')
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                it.copy(
                    snackbarMessage = if (success) context.getString(R.string.slayer_lamp_purchased, skillName)
                                      else context.getString(R.string.slayer_not_enough_points)
                )
            }
        }
    }

    fun buyEquipment(itemKey: String, cost: Int) {
        viewModelScope.launch {
            val state = uiState.value
            if ((state.inventory[itemKey] ?: 0) > 0) {
                _extra.update { it.copy(snackbarMessage = context.getString(R.string.slayer_already_owned)) }
                return@launch
            }
            val success = slayerRepo.spendPointsForItem(itemKey, cost)
            _extra.update {
                it.copy(
                    snackbarMessage = if (success) context.getString(R.string.slayer_purchased)
                                      else context.getString(R.string.slayer_not_enough_points)
                )
            }
        }
    }

    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }
}
