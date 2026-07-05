package com.fantasyidler.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.R
import com.fantasyidler.data.json.SkillingDungeonData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.Skills
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QueuedSessionStarter
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.simulator.SkillingDungeonSimulator
import com.fantasyidler.util.GameStrings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class SkillingDungeonUiItem(
    val key: String,
    val dungeon: SkillingDungeonData,
    val notesFound: Int,
    val isAccessible: Boolean,
    val lockReason: String?,
)

data class ExpeditionsUiState(
    val isLoading: Boolean = true,
    val skillLevels: Map<String, Int> = emptyMap(),
    val dungeonsBySkill: Map<String, List<SkillingDungeonUiItem>> = emptyMap(),
    val snackbarMessage: String? = null,
    val anySessionActive: Boolean = false,
)

@HiltViewModel
class ExpeditionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val queuedSessionStarter: QueuedSessionStarter,
    private val json: Json,
) : ViewModel() {

    private val _extra = MutableStateFlow(ExpeditionsUiState())

    val uiState: StateFlow<ExpeditionsUiState> = combine(
        playerRepo.playerFlow,
        sessionRepo.activeSessionFlow,
        _extra,
    ) { player, activeSession, extra ->
        if (player == null) return@combine extra.copy(isLoading = true)

        val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
        val flags: PlayerFlags = json.decodeFromString(player.flags)

        val equippedMap: Map<String, String?> = json.decodeFromString(player.equipped)

        val miningEff   = equippedMap["pickaxe"]?.let { gameData.equipment[it]?.miningEfficiency }     ?: 1.0f
        val woodEff     = equippedMap["axe"]?.let { gameData.equipment[it]?.woodcuttingEfficiency }     ?: 1.0f
        val fishingEff  = equippedMap["fishing_rod"]?.let { gameData.equipment[it]?.fishingEfficiency } ?: 1.0f

        val agilityLevel = levels[Skills.AGILITY] ?: 1

        val SKILL_ORDER = listOf(Skills.MINING, Skills.WOODCUTTING, Skills.FISHING, Skills.AGILITY, Skills.THIEVING)
        val dungeonsBySkill = SKILL_ORDER.associateWith { skill ->
            gameData.skillingDungeons.entries
                .filter { (_, d) -> d.skill == skill }
                .sortedBy { (_, d) -> d.levelRequired }
                .map { (key, dungeon) ->
                    val playerLevel = levels[skill] ?: 1
                    val notesFound = flags.skillingDungeonNotes[key] ?: 0
                    val levelOk = playerLevel >= dungeon.levelRequired
                    val prevOk = dungeon.requiresPreviousUnlock == null ||
                            flags.unlockedDungeons.contains(dungeon.requiresPreviousUnlock)
                    val isAccessible = levelOk && prevOk
                    val lockReason = when {
                        !levelOk -> context.getString(
                            R.string.expedition_lock_level,
                            GameStrings.skillName(context, skill),
                            dungeon.levelRequired,
                        )
                        !prevOk  -> {
                            val prereq = gameData.skillingDungeons.values
                                .firstOrNull { it.unlockDungeon == dungeon.requiresPreviousUnlock }
                            if (prereq != null)
                                context.getString(R.string.expedition_lock_notes, prereq.displayName)
                            else
                                context.getString(R.string.expedition_lock_prerequisite)
                        }
                        else -> null
                    }
                    SkillingDungeonUiItem(
                        key = key,
                        dungeon = dungeon,
                        notesFound = notesFound,
                        isAccessible = isAccessible,
                        lockReason = lockReason,
                    )
                }
        }

        extra.copy(
            isLoading = false,
            skillLevels = levels,
            dungeonsBySkill = dungeonsBySkill,
            anySessionActive = activeSession != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpeditionsUiState())

    fun startExpedition(key: String) {
        viewModelScope.launch {
            val dungeon = gameData.skillingDungeons[key] ?: return@launch
            val player = playerRepo.getOrCreatePlayer()
            val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            val agilityLevel = levels[Skills.AGILITY] ?: 1
            val flags: PlayerFlags = json.decodeFromString(player.flags)
            val agilityPrestige = flags.skillPrestige[Skills.AGILITY] ?: 0

            if (sessionRepo.getActiveSession()?.completed == false) {
                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(
                        skillName           = "expedition",
                        activityKey         = key,
                        skillDisplayName    = dungeon.displayName,
                        estimatedDurationMs = SkillSimulator.sessionDurationMs(agilityLevel, agilityPrestige),
                    )
                )
                _extra.update {
                    it.copy(snackbarMessage = if (enqueued) context.getString(R.string.snackbar_added_to_queue, dungeon.displayName) else context.getString(R.string.snackbar_queue_full))
                }
                return@launch
            }

            val equippedMap: Map<String, String?> = json.decodeFromString(player.equipped)
            val toolEfficiency: Float = when (dungeon.skill) {
                Skills.MINING      -> equippedMap[EquipSlot.PICKAXE]?.let { gameData.equipment[it]?.miningEfficiency }     ?: 1.0f
                Skills.WOODCUTTING -> equippedMap[EquipSlot.AXE]?.let { gameData.equipment[it]?.woodcuttingEfficiency }    ?: 1.0f
                Skills.FISHING     -> equippedMap[EquipSlot.FISHING_ROD]?.let { gameData.equipment[it]?.fishingEfficiency } ?: 1.0f
                else               -> 1.0f
            }
            val xpMap: Map<String, Long> = json.decodeFromString(player.skillXp)
            val result = SkillingDungeonSimulator.simulate(
                dungeonKey      = key,
                dungeon         = dungeon,
                startXp         = xpMap[dungeon.skill] ?: 0L,
                agilityLevel    = agilityLevel,
                agilityPrestige = agilityPrestige,
                toolEfficiency  = toolEfficiency,
            )
            sessionRepo.startSession(
                skillName        = "expedition",
                activityKey      = key,
                frames           = json.encodeToString(result.frames),
                durationMs       = result.durationMs,
                skillDisplayName = dungeon.displayName,
            )
            _extra.update { it.copy(snackbarMessage = "${dungeon.displayName} started.") }
        }
    }

    fun clearSnackbar() {
        _extra.update { it.copy(snackbarMessage = null) }
    }
}
