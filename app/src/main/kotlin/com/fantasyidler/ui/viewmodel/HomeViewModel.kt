package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.util.formatXp
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

data class HomeUiState(
    val isLoading: Boolean = true,
    val coins: Long = 0L,
    val skillLevels: Map<String, Int> = emptyMap(),
    val skillXp: Map<String, Long> = emptyMap(),
    val activeSession: SkillSession? = null,
    val snackbarMessage: String? = null,
    val batteryPromptShown: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val questRepo: QuestRepository,
    private val json: Json,
) : ViewModel() {

    private val _extra = MutableStateFlow(HomeUiState())

    val uiState: StateFlow<HomeUiState> = combine(
        playerRepo.playerFlow,
        sessionRepo.activeSessionFlow,
        _extra,
    ) { player, session, extra ->
        if (player == null) extra.copy(isLoading = true, activeSession = session)
        else {
            val flags: PlayerFlags = json.decodeFromString(player.flags)
            extra.copy(
                isLoading           = false,
                coins               = player.coins,
                skillLevels         = json.decodeFromString(player.skillLevels),
                skillXp             = json.decodeFromString(player.skillXp),
                activeSession       = session,
                batteryPromptShown  = flags.batteryPromptShown,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    // ------------------------------------------------------------------
    // Session actions
    // ------------------------------------------------------------------

    fun collectSession() {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSession() ?: return@launch
            if (!session.completed) return@launch

            val frames: List<SessionFrame> = json.decodeFromString(session.frames)
            val message: String
            val petIds = gameData.pets.keys

            if (session.skillName == "boss") {
                val frames: List<SessionFrame> = json.decodeFromString(session.frames)
                val frame = frames.firstOrNull()
                val won   = (frame?.kills ?: 0) > 0
                if (won && frame != null) {
                    val allItems    = frame.items.toMutableMap()
                    val coinsGained = allItems.remove("coins")?.toLong() ?: 0L
                    val petIds      = gameData.pets.keys
                    val petDrops    = allItems.filterKeys { it in petIds }
                    val loot        = allItems.filterKeys { it !in petIds }
                    playerRepo.applyMultiSkillResults(frame.xpBySkill, loot, coinsGained)
                    for ((petId, _) in petDrops) {
                        val petData = gameData.pets[petId] ?: continue
                        playerRepo.addPetIfNew(petId, petData.boostPercent)
                    }
                }
                val bossName = gameData.bosses[session.activityKey]?.displayName ?: session.activityKey
                message = if (won) "Defeated $bossName!" else "Defeated by $bossName."
                sessionRepo.deleteSession(session.sessionId)
            } else if (session.skillName == "combat") {
                val xpPerSkill      = mutableMapOf<String, Long>()
                val items           = mutableMapOf<String, Int>()
                val killsByEnemy    = mutableMapOf<String, Int>()
                for (frame in frames) {
                    for ((skill, xp) in frame.xpBySkill) {
                        xpPerSkill[skill] = (xpPerSkill[skill] ?: 0L) + xp
                    }
                    for ((item, qty) in frame.items) {
                        items[item] = (items[item] ?: 0) + qty
                    }
                    for ((enemy, kills) in frame.killsByEnemy) {
                        killsByEnemy[enemy] = (killsByEnemy[enemy] ?: 0) + kills
                    }
                }
                val coinsGained = items.remove("coins")?.toLong() ?: 0L
                playerRepo.applyMultiSkillResults(xpPerSkill, items, coinsGained)
                questRepo.recordCombat(session.activityKey, killsByEnemy, items)
                val dungeonName = gameData.dungeons[session.activityKey]?.displayName
                    ?: session.activityKey
                val totalXp = xpPerSkill.values.sum()
                message = "Collected +${totalXp.formatXp()} XP from $dungeonName"
            } else {
                val totalXp = frames.sumOf { it.xpGain.toLong() }
                val allItems = mutableMapOf<String, Int>()
                for (frame in frames) {
                    for ((item, qty) in frame.items) {
                        allItems[item] = (allItems[item] ?: 0) + qty
                    }
                }
                val petDrops     = allItems.filterKeys { it in petIds }
                val regularItems = allItems.filterKeys { it !in petIds }
                playerRepo.applySessionResults(session.skillName, totalXp, regularItems)

                val gatheringSkills = setOf(Skills.MINING, Skills.WOODCUTTING, Skills.FISHING,
                    Skills.AGILITY, Skills.FIREMAKING, Skills.RUNECRAFTING)
                val craftingSkills = setOf(Skills.SMITHING, Skills.COOKING, Skills.FLETCHING, Skills.CRAFTING)
                when (session.skillName) {
                    in gatheringSkills -> questRepo.recordGathering(session.skillName, regularItems)
                    in craftingSkills  -> questRepo.recordCrafting(session.skillName, regularItems)
                }

                // Handle pet drops
                var petMessage: String? = null
                for ((petId, _) in petDrops) {
                    val petData = gameData.pets[petId] ?: continue
                    val added = playerRepo.addPetIfNew(petId, petData.boostPercent)
                    if (added) petMessage = "You found a pet: ${petData.displayName}!"
                }

                val itemSummary = regularItems.entries
                    .sortedByDescending { it.value }
                    .joinToString(", ") { (key, qty) -> "$qty ${gameData.itemDisplayName(key)}" }

                message = petMessage ?: buildString {
                    append("Collected +${totalXp.formatXp()} XP")
                    if (itemSummary.isNotEmpty()) append(" • $itemSummary")
                }
            }

            sessionRepo.deleteSession(session.sessionId)
            _extra.update { it.copy(snackbarMessage = message) }
        }
    }

    fun abandonSession() {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSession() ?: return@launch
            sessionRepo.abandonSession(session.sessionId)
        }
    }

    fun debugFinishSession() {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSession() ?: return@launch
            sessionRepo.markCompleted(session.sessionId)
        }
    }

    fun markBatteryPromptShown() {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(batteryPromptShown = true))
        }
    }

    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }
}

// ---------------------------------------------------------------------------
// Derived helpers (pure, used by HomeScreen)
// ---------------------------------------------------------------------------

fun combatLevelFrom(levels: Map<String, Int>): Int {
    val atk = levels[Skills.ATTACK]    ?: 1
    val str = levels[Skills.STRENGTH]  ?: 1
    val def = levels[Skills.DEFENSE]   ?: 1
    val hp  = levels[Skills.HITPOINTS] ?: 1
    return (((atk + str) * 0.325) + (def + hp) * 0.25).toInt().coerceAtLeast(1)
}

fun totalLevelFrom(levels: Map<String, Int>): Int =
    levels.values.sum()
