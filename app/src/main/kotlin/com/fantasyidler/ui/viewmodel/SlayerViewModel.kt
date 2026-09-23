package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.simulator.HeirloomStats
import com.fantasyidler.R
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.Skills
import com.fantasyidler.data.model.SlayerTask
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.repository.BoostRepository
import com.fantasyidler.repository.DailyQuestRepository
import com.fantasyidler.repository.ForetelResult
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.GuildRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.QueuedSessionStarter
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.repository.SlayerRepository
import com.fantasyidler.repository.TownRepository
import com.fantasyidler.repository.WeeklyQuestRepository
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.withAppLocale
import com.fantasyidler.util.xpMultiplierBreakdown
import android.content.Context
import com.fantasyidler.data.model.QuestProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
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
    /** Current player session queue size. */
    val queueSize: Int = 0,
    val maxQueueSize: Int = 3,
    val unlockedDungeons: Set<String> = emptySet(),
    val inventory: Map<String, Int> = emptyMap(),
    val skillLevels: Map<String, Int> = emptyMap(),
    val skillXp: Map<String, Long> = emptyMap(),
    val activeWeaponSlot: String? = null,
    /** Non-null when the player has tapped Buy on a lamp and needs to choose a skill. */
    val pendingLamp: PendingLamp? = null,
    val snackbarMessage: String? = null,
    /** Non-null while the weapon-picker sheet is open before queuing a slayer dungeon. */
    val pendingSlayerDungeonKey: String? = null,
    /** Weapons currently equipped: slot key -> EquipmentData. Used by the weapon picker sheet. */
    val slayerEquippedWeapons: Map<String, EquipmentData> = emptyMap(),
    /** The weapon slot selected in the slayer weapon picker sheet. */
    val slayerSelectedWeaponSlot: String? = null,
    /** Pre-assigned future tasks, up to [maxForetellSlots]. */
    val foretelledTasks: List<SlayerTask> = emptyList(),
    /** Dungeon display names that contain each foretold task's enemy, parallel to [foretelledTasks]. */
    val foretelledTaskDungeons: List<List<String>> = emptyList(),
    /** Bone cost (units) for the next foretell slot. */
    val nextForetelCostUnits: Int = 10,
    /** Foretell queue capacity: base 3, extended by Foresight prestige nodes. */
    val maxForetellSlots: Int = 3,
    /** Guild daily / daily / weekly quests tied to the Slayer guild, for the "Quests" button. */
    val slayerQuests: List<SheetQuestSummary> = emptyList(),
)

@HiltViewModel
class SlayerViewModel @Inject constructor(
    private val boostRepo: BoostRepository,
    private val playerRepo: PlayerRepository,
    private val slayerRepo: SlayerRepository,
    val gameData: GameDataRepository,
    private val queuedSessionStarter: QueuedSessionStarter,
    private val sessionRepo: SessionRepository,
    @ApplicationContext private val context: Context,
    private val townRepo: TownRepository,
    private val questRepo: QuestRepository,
    private val guildRepo: GuildRepository,
    private val dailyQuestRepo: DailyQuestRepository,
    private val weeklyQuestRepo: WeeklyQuestRepository,
    private val json: Json,
) : ViewModel() {

    init {
        // Mirror the ticker Home/CombatViewModel already run: Doze can defer AlarmManager
        // for hours, so while the Slayer view is open, drain overdue sessions ourselves so
        // the queue advances and playerFlow emits fresh state (issue #1848).
        viewModelScope.launch {
            while (true) {
                try { sessionRepo.completeOverdueSessions(queuedSessionStarter) } catch (_: Exception) {}
                delay(1_000L)
            }
        }
    }

    /** Equipment data keyed by item key, for the shop stats display. */
    val shopEquipment: Map<String, EquipmentData> by lazy {
        listOf("slayer_helm", "abyssal_whip", "slayer_platebody", "slayer_platelegs", "slayer_plateskirt")
            .mapNotNull { key -> gameData.equipment[key]?.let { key to it } }
            .toMap()
    }

    private val _extra = MutableStateFlow(SlayerUiState())

    val uiState: StateFlow<SlayerUiState> = combine(
        playerRepo.playerFlow,
        _extra,
        questRepo.observeProgress(),
    ) { player, extra, questProgress ->
        if (player == null) extra.copy(isLoading = true)
        else {
            val levels:    Map<String, Int>  = json.decodeFromString(player.skillLevels)
            val xpMap:     Map<String, Long> = json.decodeFromString(player.skillXp)
            val flags:     PlayerFlags       = json.decodeFromString(player.flags)
            val inventory: Map<String, Int>  = json.decodeFromString(player.inventory)
            val unlockedDungeons = flags.unlockedDungeons.toSet()
            fun dungeonEntriesFor(key: String) = gameData.dungeons.entries
                .filter { (_, d) -> d.enemySpawns.any { it.enemy == key } }
                .filter { (k, d) -> !d.loreUnlockOnly || k in unlockedDungeons }
                // Best hunting ground first: the queue shortcut takes the head of this
                // list, which was previously just map iteration order and could pick a
                // dungeon where the task enemy barely spawns (hellhound report)
                .sortedByDescending { (_, d) ->
                    val total = d.enemySpawns.sumOf { it.weight }
                    if (total == 0) 0.0
                    else d.enemySpawns.first { it.enemy == key }.weight.toDouble() / total
                }
            val taskDungeonEntries = flags.activeSlayerTask?.enemyKey?.let { dungeonEntriesFor(it) } ?: emptyList()
            val taskDungeons     = taskDungeonEntries.map { (key, _) -> GameStrings.dungeonName(context.withAppLocale(), key) }
            val taskDungeonKeys  = taskDungeonEntries.map { (k, _) -> k }
            val foretelledTaskDungeons = flags.foretelledTasks.map { task ->
                dungeonEntriesFor(task.enemyKey).map { (key, _) -> GameStrings.dungeonName(context.withAppLocale(), key) }
            }
            val taskIsStuck = flags.activeSlayerTask?.enemyKey?.let { key ->
                val dungeonKeys = gameData.dungeons.values
                    .filter { d -> d.enemySpawns.any { it.enemy == key } }
                    .map { it.name }
                dungeonKeys.isNotEmpty() &&
                    dungeonKeys.all { it in gameData.expeditionLockedDungeons && it !in unlockedDungeons }
            } ?: false
            val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
            val equipMap = HeirloomStats.resolveAll(gameData.equipment, levels, flags.heirloomXp)
            val equippedWeapons = EquipSlot.WEAPON_SLOTS
                .mapNotNull { slot -> equipped[slot]?.let { key -> equipMap[key]?.let { slot to it } } }
                .toMap()
            val nextForetelCost = slayerRepo.foretelCostUnits(flags.foretelledTasks.size)
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
                maxQueueSize          = playerRepo.maxQueueSize(flags),
                unlockedDungeons      = unlockedDungeons,
                inventory             = inventory,
                skillLevels           = levels,
                skillXp               = xpMap,
                activeWeaponSlot      = flags.activeWeaponSlot,
                slayerEquippedWeapons = equippedWeapons,
                foretelledTasks       = flags.foretelledTasks,
                foretelledTaskDungeons = foretelledTaskDungeons,
                nextForetelCostUnits  = nextForetelCost,
                maxForetellSlots      = slayerRepo.maxForetellSlots(flags),
                slayerQuests          = computeSlayerQuests(questProgress, flags),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SlayerUiState())

    /** Retrieve guild daily / daily / weekly quests tied to the Slayer guild, for the "Quests" button. */
    private fun computeSlayerQuests(
        questProgress: List<QuestProgress>,
        flags: PlayerFlags,
    ): List<SheetQuestSummary> {
        val completedIds = questProgress.filter { it.completed }.map { it.questId }.toSet()
        val result = mutableListOf<SheetQuestSummary>()

        val dailies = guildRepo.getGuildDailiesWithProgress(Skills.SLAYER, flags)
        if (dailies.isNotEmpty()) {
            val level = guildRepo.guildLevel(Skills.SLAYER, flags.guildDailyTierCounts, completedIds)
            val maxed = level >= GuildRepository.DAILIES_REQUIRED_PER_TIER.size
            result += dailies.map { daily ->
                SheetQuestSummary(
                    questId    = daily.template.id,
                    questName  = daily.template.name,
                    guild      = Skills.SLAYER,
                    type       = daily.template.type,
                    target     = daily.template.target,
                    progress   = daily.progress.coerceAtMost(daily.template.amount),
                    amount     = daily.template.amount,
                    claimed    = daily.claimed,
                    source     = SheetQuestSource.GUILD,
                    guildMaxed = maxed,
                )
            }
        }

        for (dq in dailyQuestRepo.getActiveDailyQuests(flags)) {
            if (dq.template.skill != Skills.SLAYER) continue
            result += SheetQuestSummary(
                questId     = dq.template.id,
                questName   = dq.template.displayName,
                guild       = Skills.SLAYER,
                type        = dq.template.type,
                target      = dq.template.target,
                progress    = dq.progress.coerceAtMost(dq.template.amount),
                amount      = dq.template.amount,
                claimed     = dq.claimed,
                source      = SheetQuestSource.DAILY,
                description = dq.template.description,
            )
        }

        for (wq in weeklyQuestRepo.getActiveWeeklyQuests(flags)) {
            if (wq.template.skill != Skills.SLAYER) continue
            result += SheetQuestSummary(
                questId     = wq.template.id,
                questName   = wq.template.displayName,
                guild       = Skills.SLAYER,
                type        = wq.template.type,
                target      = wq.template.target,
                progress    = wq.progress.coerceAtMost(wq.template.amount),
                amount      = wq.template.amount,
                claimed     = wq.claimed,
                source      = SheetQuestSource.WEEKLY,
                description = wq.template.description,
            )
        }

        return result
    }

    fun getNewTask() {
        viewModelScope.launch {
            val state = uiState.value
            if (state.activeTask != null) return@launch
            val success = slayerRepo.assignTask(state.slayerLevel, state.unlockedDungeons)
            if (!success) {
                _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.slayer_no_eligible_tasks)) }
            }
        }
    }

    fun skipTask() {
        viewModelScope.launch {
            val state = uiState.value
            val success = slayerRepo.skipTask(state.slayerLevel, state.unlockedDungeons)
            if (!success) {
                _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.slayer_not_enough_points)) }
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
            _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.slayer_not_enough_points)) }
            return
        }
        _extra.update { it.copy(pendingLamp = PendingLamp(xpAmount, cost)) }
    }

    fun dismissLampPicker() = _extra.update { it.copy(pendingLamp = null) }

    fun selectLampSkill(skillKey: String) {
        val lamp = _extra.value.pendingLamp ?: return
        _extra.update { it.copy(pendingLamp = null) }
        viewModelScope.launch {
            val result = slayerRepo.spendPointsForXp(skillKey, lamp.xpAmount, lamp.cost)
            _extra.update {
                it.copy(
                    snackbarMessage = if (result.success) {
                        val b = result.breakdown!!
                        val skillDisplay = GameStrings.skillName(context, skillKey)
                        val suffix = xpMultiplierBreakdown(b.baseXp, b.boostFactor, b.blessingMult, b.prestigeXpPct)?.let { s -> " $s" } ?: ""
                        context.withAppLocale().getString(R.string.slayer_lamp_purchased, b.finalXp.formatXp(), skillDisplay) + suffix
                    } else context.withAppLocale().getString(R.string.slayer_not_enough_points)
                )
            }
        }
    }

    fun buyEquipment(itemKey: String, cost: Int) {
        viewModelScope.launch {
            val state = uiState.value
            if ((state.inventory[itemKey] ?: 0) > 0) {
                _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.slayer_already_owned)) }
                return@launch
            }
            val success = slayerRepo.spendPointsForItem(itemKey, cost)
            _extra.update {
                it.copy(
                    snackbarMessage = if (success) context.withAppLocale().getString(R.string.slayer_purchased)
                                      else context.withAppLocale().getString(R.string.slayer_not_enough_points)
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
            val preselect = state.activeWeaponSlot ?: state.slayerEquippedWeapons.keys.firstOrNull()
            _extra.update { it.copy(pendingSlayerDungeonKey = dungeonKey, slayerSelectedWeaponSlot = preselect) }
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
            val dungeonName = GameStrings.dungeonName(context, dungeonKey)
            // Persist an explicit picker choice app-wide like the Combat screen's
            // selectWeaponSlot does, before reading player state so the queued
            // snapshot/spell/preview reflect the applied loadout (issue #1617).
            if (weaponSlot != null) {
                val priorFlags: PlayerFlags = json.decodeFromString(playerRepo.getOrCreatePlayer().flags)
                if (priorFlags.activeWeaponSlot != weaponSlot) {
                    playerRepo.updateFlags(priorFlags.copy(activeWeaponSlot = weaponSlot))
                    EquipSlot.combatStyleForSlot(weaponSlot)?.let { style -> playerRepo.applyLoadout(style, gameData.equipment) }
                }
            }
            val player   = playerRepo.getOrCreatePlayer()
            val levels: Map<String, Int>       = json.decodeFromString(player.skillLevels)
            val agility  = levels[Skills.AGILITY] ?: 1
            val flags: PlayerFlags             = json.decodeFromString(player.flags)
            val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
            val inventory: Map<String, Int>    = json.decodeFromString(player.inventory)
            val resolvedWeaponSlot = weaponSlot
                ?: flags.activeWeaponSlot
                ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equipped[it] != null }
                ?: EquipSlot.WEAPON_ATK
            val rememberedSpell  = flags.activeSpell?.let { gameData.spells[it] }
            val rememberedPotion = flags.activePotionKey?.takeIf { (inventory[it] ?: 0) > 0 }
            val previewXp = estimateDungeonPreviewXp(
                gameData      = gameData,
                boostRepo     = boostRepo,
                townRepo      = townRepo,
                json          = json,
                dungeonKey    = dungeonKey,
                weaponSlot    = resolvedWeaponSlot,
                equipped      = equipped,
                inventory     = inventory,
                levels        = levels,
                flags         = flags,
                selectedSpell = rememberedSpell,
                potionKey     = rememberedPotion,
                petsJson      = player.pets,
            )
            val enqueued = playerRepo.enqueueAction(
                QueuedAction(
                    skillName           = "combat",
                    activityKey         = dungeonKey,
                    skillDisplayName    = dungeonName,
                    estimatedDurationMs = SkillSimulator.sessionDurationMs(agility, boostRepo.sessionFloorReductionMin(flags), townRepo.playerSessionDurationMultiplier(flags)),
                    estimatedXpGain     = previewXp,
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
                    snackbarMessage = if (enqueued) context.withAppLocale().getString(R.string.slayer_queue_added, dungeonName)
                                      else context.withAppLocale().getString(R.string.slayer_queue_full)
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
                val preselect = state.activeWeaponSlot ?: state.slayerEquippedWeapons.keys.firstOrNull()
                _extra.update { it.copy(pendingSlayerDungeonKey = dungeonKey, slayerSelectedWeaponSlot = preselect) }
            } else {
                doQueueTaskDungeon(dungeonKey, weaponSlot = null)
            }
        }
    }

    fun foretellTask() {
        viewModelScope.launch {
            val state = uiState.value
            when (val result = slayerRepo.foretelTask(state.slayerLevel, state.unlockedDungeons)) {
                is ForetelResult.Success ->
                    _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.slayer_foretell_success, GameStrings.enemyName(context, result.task.enemyKey))) }
                ForetelResult.QueueFull ->
                    _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.slayer_foretell_queue_full, state.maxForetellSlots)) }
                ForetelResult.NoEligibleTasks ->
                    _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.slayer_no_eligible_tasks)) }
                is ForetelResult.NotEnoughBones ->
                    _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.slayer_foretell_not_enough_bones, result.costUnits)) }
            }
        }
    }

    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }
}
