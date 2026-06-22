package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.R
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.Skills
import com.fantasyidler.data.model.SlayerTask
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.repository.ForetelResult
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QueuedSessionStarter
import com.fantasyidler.repository.SlayerRepository
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.withAppLocale
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
    /** Dungeon keys that contain the active task's enemy (parallel to taskDungeons). */
    val taskDungeonKeys: List<String> = emptyList(),
    /** True when the active task's enemy only exists in expedition dungeons the player hasn't unlocked. */
    val taskIsStuck: Boolean = false,
    /** Current player session queue size (max 3). */
    val queueSize: Int = 0,
    val unlockedDungeons: Set<String> = emptySet(),
    val inventory: Map<String, Int> = emptyMap(),
    val skillLevels: Map<String, Int> = emptyMap(),
    /** Non-null when the player has tapped Buy on a lamp and needs to choose a skill. */
    val pendingLamp: PendingLamp? = null,
    val snackbarMessage: String? = null,
    /** Non-null while the weapon-picker sheet is open before queuing a slayer dungeon. */
    val pendingSlayerDungeonKey: String? = null,
    /** Weapons currently equipped: slot key -> EquipmentData. Used by the weapon picker sheet. */
    val slayerEquippedWeapons: Map<String, EquipmentData> = emptyMap(),
    /** The weapon slot selected in the slayer weapon picker sheet. */
    val slayerSelectedWeaponSlot: String? = null,
    /** Up to 3 pre-assigned future tasks. */
    val foretelledTasks: List<SlayerTask> = emptyList(),
    /** Bone cost (units) for the next foretell slot. */
    val nextForetelCostUnits: Int = 10,
)

@HiltViewModel
class SlayerViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val slayerRepo: SlayerRepository,
    val gameData: GameDataRepository,
    private val queuedSessionStarter: QueuedSessionStarter,
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
            val unlockedDungeons = flags.unlockedDungeons.toSet()
            val taskDungeonEntries = flags.activeSlayerTask?.enemyKey?.let { key ->
                gameData.dungeons.entries
                    .filter { (_, d) -> d.enemySpawns.any { it.enemy == key } }
                    .filter { (k, d) -> !d.loreUnlockOnly || k in unlockedDungeons }
            } ?: emptyList()
            val taskDungeons     = taskDungeonEntries.map { (key, _) -> GameStrings.dungeonName(context.withAppLocale(), key) }
            val taskDungeonKeys  = taskDungeonEntries.map { (k, _) -> k }
            val taskIsStuck = flags.activeSlayerTask?.enemyKey?.let { key ->
                val dungeonKeys = gameData.dungeons.values
                    .filter { d -> d.enemySpawns.any { it.enemy == key } }
                    .map { it.name }
                dungeonKeys.isNotEmpty() &&
                    dungeonKeys.all { it in gameData.expeditionLockedDungeons && it !in unlockedDungeons }
            } ?: false
            val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
            val equippedWeapons = EquipSlot.WEAPON_SLOTS
                .mapNotNull { slot -> equipped[slot]?.let { key -> gameData.equipment[key]?.let { slot to it } } }
                .toMap()
            val nextForetelCost = when (flags.foretelledTasks.size) { 0 -> 10; 1 -> 25; else -> 50 }
            extra.copy(
                isLoading             = false,
                slayerLevel           = levels[Skills.SLAYER] ?: 1,
                slayerXp              = xpMap[Skills.SLAYER] ?: 0L,
                slayerPoints          = flags.slayerPoints,
                activeTask            = flags.activeSlayerTask,
                taskDungeons          = taskDungeons,
                taskDungeonKeys       = taskDungeonKeys,
                taskIsStuck           = taskIsStuck,
                queueSize             = flags.sessionQueue.size,
                unlockedDungeons      = unlockedDungeons,
                inventory             = inventory,
                skillLevels           = levels,
                slayerEquippedWeapons = equippedWeapons,
                foretelledTasks       = flags.foretelledTasks,
                nextForetelCostUnits  = nextForetelCost,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SlayerUiState())

    fun getNewTask() {
        viewModelScope.launch {
            val state = uiState.value
            if (state.activeTask != null) return@launch
            val success = slayerRepo.assignTask(state.slayerLevel, state.unlockedDungeons)
            if (!success) {
                _extra.update { it.copy(snackbarMessage = context.getString(R.string.slayer_no_eligible_tasks)) }
            }
        }
    }

    fun skipTask() {
        viewModelScope.launch {
            val state = uiState.value
            val success = slayerRepo.skipTask(state.slayerLevel, state.unlockedDungeons)
            if (!success) {
                _extra.update { it.copy(snackbarMessage = context.getString(R.string.slayer_not_enough_points)) }
            }
        }
    }

    /** Free reroll when the active task is stuck behind an unvisited expedition dungeon. */
    fun rerollStuckTask() {
        viewModelScope.launch {
            val state = uiState.value
            if (!state.taskIsStuck) return@launch
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(activeSlayerTask = null))
            slayerRepo.assignTask(state.slayerLevel, state.unlockedDungeons)
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

    fun queueTaskDungeon() {
        val state = uiState.value
        val dungeonKey = state.taskDungeonKeys.firstOrNull { it in state.unlockedDungeons }
            ?: state.taskDungeonKeys.firstOrNull()
            ?: return
        if (state.slayerEquippedWeapons.size > 1) {
            _extra.update { it.copy(pendingSlayerDungeonKey = dungeonKey, slayerSelectedWeaponSlot = null) }
        } else {
            doQueueTaskDungeon(dungeonKey, weaponSlot = null)
        }
    }

    fun selectSlayerWeapon(slot: String) =
        _extra.update { it.copy(slayerSelectedWeaponSlot = slot) }

    fun confirmSlayerDungeonQueue() {
        val state = _extra.value
        val dungeonKey = state.pendingSlayerDungeonKey ?: return
        _extra.update { it.copy(pendingSlayerDungeonKey = null, slayerSelectedWeaponSlot = null) }
        doQueueTaskDungeon(dungeonKey, state.slayerSelectedWeaponSlot)
    }

    fun dismissSlayerDungeonPicker() =
        _extra.update { it.copy(pendingSlayerDungeonKey = null, slayerSelectedWeaponSlot = null) }

    private fun doQueueTaskDungeon(dungeonKey: String, weaponSlot: String?) {
        viewModelScope.launch {
            val state = uiState.value
            val dungeonName = gameData.dungeons[dungeonKey]?.displayName ?: dungeonKey
            val player   = playerRepo.getOrCreatePlayer()
            val agility  = (json.decodeFromString<Map<String, Int>>(player.skillLevels))[Skills.AGILITY] ?: 1
            val flags: PlayerFlags             = json.decodeFromString(player.flags)
            val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
            val resolvedWeaponSlot = weaponSlot
                ?: flags.activeWeaponSlot
                ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equipped[it] != null }
                ?: EquipSlot.WEAPON_ATK
            val enqueued = playerRepo.enqueueAction(
                QueuedAction(
                    skillName           = "combat",
                    activityKey         = dungeonKey,
                    skillDisplayName    = dungeonName,
                    estimatedDurationMs = SkillSimulator.sessionDurationMs(agility),
                    equippedSnapshot    = player.equipped,
                    arrowsKey           = flags.equippedArrows,
                    spellName           = flags.activeSpell,
                    potionKey           = flags.activePotionKey,
                    weaponSlot          = resolvedWeaponSlot,
                )
            )
            if (enqueued) queuedSessionStarter.startNextQueued()
            _extra.update {
                it.copy(
                    snackbarMessage = if (enqueued) context.getString(R.string.slayer_queue_added, dungeonName)
                                      else context.getString(R.string.slayer_queue_full)
                )
            }
        }
    }

    fun queueForetelledTaskDungeon(task: SlayerTask) {
        viewModelScope.launch {
            val state = uiState.value
            val dungeonKey = gameData.dungeons.entries
                .filter { (k, d) ->
                    d.enemySpawns.any { it.enemy == task.enemyKey } &&
                    (k !in gameData.expeditionLockedDungeons || k in state.unlockedDungeons)
                }
                .maxByOrNull { (k, _) -> playerRepo.getFlags().dungeonRuns[k] ?: 0 }
                ?.key ?: return@launch
            if (state.slayerEquippedWeapons.size > 1) {
                _extra.update { it.copy(pendingSlayerDungeonKey = dungeonKey, slayerSelectedWeaponSlot = null) }
            } else {
                doQueueTaskDungeon(dungeonKey, weaponSlot = null)
            }
        }
    }

    fun foretelTask() {
        viewModelScope.launch {
            val state = uiState.value
            when (val result = slayerRepo.foretelTask(state.slayerLevel, state.unlockedDungeons)) {
                is ForetelResult.Success ->
                    _extra.update { it.copy(snackbarMessage = context.getString(R.string.slayer_foretell_success, result.task.displayName)) }
                ForetelResult.QueueFull ->
                    _extra.update { it.copy(snackbarMessage = context.getString(R.string.slayer_foretell_queue_full)) }
                ForetelResult.NoEligibleTasks ->
                    _extra.update { it.copy(snackbarMessage = context.getString(R.string.slayer_no_eligible_tasks)) }
                is ForetelResult.NotEnoughBones ->
                    _extra.update { it.copy(snackbarMessage = context.getString(R.string.slayer_foretell_not_enough_bones, result.costUnits)) }
            }
        }
    }

    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }
}
