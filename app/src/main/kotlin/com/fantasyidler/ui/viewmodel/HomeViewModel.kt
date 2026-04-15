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
import com.fantasyidler.util.toTitleCase
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

// ---------------------------------------------------------------------------
// Session summary shown in the collect dialog
// ---------------------------------------------------------------------------

data class SessionSummary(
    val title: String,
    val died: Boolean = false,
    /** Skill name → "+X XP" label — for multi-skill sessions (combat). */
    val xpLines: List<Pair<String, String>> = emptyList(),
    /** Single XP label for single-skill sessions (gathering/crafting/prayer). */
    val totalXpLabel: String = "",
    /** Item display name → "×qty" label */
    val itemLines: List<Pair<String, String>> = emptyList(),
    val coinsGained: Long = 0L,
    /** Enemy display name → "×kills" label — combat only */
    val killLines: List<Pair<String, String>> = emptyList(),
    /** Bone type display name + count — prayer only */
    val boneBuriedLabel: String = "",
    /** Whether the 2× XP boost was active during this session. */
    val boostWasActive: Boolean = false,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val coins: Long = 0L,
    val skillLevels: Map<String, Int> = emptyMap(),
    val skillXp: Map<String, Long> = emptyMap(),
    val activeSession: SkillSession? = null,
    val snackbarMessage: String? = null,
    val batteryPromptShown: Boolean = false,
    val sessionSummary: SessionSummary? = null,
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
            val petIds    = gameData.pets.keys

            // Check if XP boost was active when player collected
            val flags: PlayerFlags = json.decodeFromString(
                playerRepo.getOrCreatePlayer().flags
            )
            val boostActive = flags.xpBoostExpiresAt > System.currentTimeMillis()

            val summary: SessionSummary

            if (session.skillName == "boss") {
                val frame = frames.firstOrNull()
                val won   = (frame?.kills ?: 0) > 0
                if (won && frame != null) {
                    val allItems    = frame.items.toMutableMap()
                    val coinsGained = allItems.remove("coins")?.toLong() ?: 0L
                    val petDrops    = allItems.filterKeys { it in petIds }
                    val loot        = allItems.filterKeys { it !in petIds }
                    playerRepo.applyMultiSkillResults(frame.xpBySkill, loot, coinsGained)
                    for ((petId, _) in petDrops) {
                        val petData = gameData.pets[petId] ?: continue
                        playerRepo.addPetIfNew(petId, petData.boostPercent)
                    }
                    val bossName = gameData.bosses[session.activityKey]?.displayName ?: session.activityKey
                    summary = SessionSummary(
                        title         = "Defeated $bossName!",
                        xpLines       = frame.xpBySkill.entries.sortedByDescending { it.value }
                            .map { (skill, xp) -> Pair(skill.toTitleCase(), "+${(xp * (if (boostActive) 2 else 1)).formatXp()} XP") },
                        itemLines     = loot.entries.sortedByDescending { it.value }
                            .map { (key, qty) -> Pair(gameData.itemDisplayName(key), "×$qty") },
                        coinsGained   = coinsGained,
                        boostWasActive = boostActive,
                    )
                } else {
                    val bossName = gameData.bosses[session.activityKey]?.displayName ?: session.activityKey
                    summary = SessionSummary(title = "Defeated by $bossName.", died = true)
                }

            } else if (session.skillName == "combat") {
                val xpPerSkill   = mutableMapOf<String, Long>()
                val items        = mutableMapOf<String, Int>()
                val killsByEnemy = mutableMapOf<String, Int>()
                val foodConsumed = mutableMapOf<String, Int>()
                val playerDied   = frames.any { it.died }

                for (frame in frames) {
                    for ((skill, xp) in frame.xpBySkill) xpPerSkill[skill] = (xpPerSkill[skill] ?: 0L) + xp
                    for ((item, qty) in frame.items)      items[item]       = (items[item] ?: 0) + qty
                    for ((e, k) in frame.killsByEnemy)    killsByEnemy[e]   = (killsByEnemy[e] ?: 0) + k
                    for ((f, q) in frame.foodConsumed)    foodConsumed[f]   = (foodConsumed[f] ?: 0) + q
                }

                if (playerDied) {
                    xpPerSkill.replaceAll { _, xp -> maxOf(1L, (xp * 0.1).toLong()) }
                    items.replaceAll { _, qty -> maxOf(0, (qty * 0.1).toInt()) }
                    items.entries.removeIf { it.value == 0 }
                }

                val coinsGained = (items.remove("coins")?.toLong() ?: 0L).let {
                    if (playerDied) maxOf(0L, (it * 0.1).toLong()) else it
                }
                val petDrops     = items.filterKeys { it in petIds }
                val regularItems = items.filterKeys { it !in petIds }

                playerRepo.applyMultiSkillResults(xpPerSkill, regularItems, coinsGained)
                for ((petId, _) in petDrops) {
                    val petData = gameData.pets[petId] ?: continue
                    playerRepo.addPetIfNew(petId, petData.boostPercent)
                }
                if (!playerDied) {
                    val combatStyle = detectCombatStyle(xpPerSkill)
                    questRepo.recordCombat(session.activityKey, killsByEnemy, regularItems, combatStyle)
                }
                // Consume food from inventory (best effort)
                if (foodConsumed.isNotEmpty()) playerRepo.consumeItems(foodConsumed)

                val dungeonName = gameData.dungeons[session.activityKey]?.displayName ?: session.activityKey
                val xpMult = if (boostActive) 2L else 1L
                summary = SessionSummary(
                    title         = if (playerDied) "$dungeonName — You Died" else "$dungeonName Complete!",
                    died          = playerDied,
                    xpLines       = xpPerSkill.entries.sortedByDescending { it.value }
                        .map { (skill, xp) -> Pair(skill.toTitleCase(), "+${(xp * xpMult).formatXp()} XP") },
                    itemLines     = regularItems.entries.sortedByDescending { it.value }
                        .map { (key, qty) -> Pair(gameData.itemDisplayName(key), "×$qty") },
                    coinsGained   = coinsGained,
                    killLines     = killsByEnemy.entries.sortedByDescending { it.value }
                        .map { (enemy, kills) ->
                            Pair(gameData.enemies[enemy]?.displayName ?: enemy.toTitleCase(), "×$kills")
                        },
                    boostWasActive = boostActive,
                )

            } else {
                // Gathering, crafting, prayer, etc.
                val totalXp  = frames.sumOf { it.xpGain.toLong() }
                val allItems = mutableMapOf<String, Int>()
                for (frame in frames) {
                    for ((item, qty) in frame.items) allItems[item] = (allItems[item] ?: 0) + qty
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
                    Skills.PRAYER      -> questRepo.recordBuried(frames.sumOf { it.kills })
                }

                for ((petId, _) in petDrops) {
                    val petData = gameData.pets[petId] ?: continue
                    playerRepo.addPetIfNew(petId, petData.boostPercent)
                }

                val skillLabel = session.skillName.toTitleCase()
                val xpMult     = if (boostActive) 2L else 1L
                val displayXp  = totalXp * xpMult

                summary = when (session.skillName) {
                    Skills.PRAYER -> {
                        val boneCount  = frames.sumOf { it.kills }
                        val boneName   = gameData.bones[session.activityKey]?.displayName ?: session.activityKey
                        SessionSummary(
                            title           = "Prayer Session Complete",
                            totalXpLabel    = "+${displayXp.formatXp()} XP",
                            boneBuriedLabel = "$boneCount $boneName buried",
                            boostWasActive  = boostActive,
                        )
                    }
                    else -> {
                        SessionSummary(
                            title         = "$skillLabel Session Complete",
                            totalXpLabel  = "+${displayXp.formatXp()} XP",
                            itemLines     = regularItems.entries.sortedByDescending { it.value }
                                .map { (key, qty) -> Pair(gameData.itemDisplayName(key), "×$qty") },
                            boostWasActive = boostActive,
                        )
                    }
                }
            }

            sessionRepo.deleteSession(session.sessionId)
            _extra.update { it.copy(sessionSummary = summary) }
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

    fun summaryConsumed() = _extra.update { it.copy(sessionSummary = null) }
    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }
}

// ---------------------------------------------------------------------------
// Derived helpers (pure, used by HomeScreen + HomeViewModel)
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

/** Infer the combat style used in a session from XP distribution. */
fun detectCombatStyle(xpPerSkill: Map<String, Long>): String {
    val rangedXp = xpPerSkill[Skills.RANGED]   ?: 0L
    val magicXp  = xpPerSkill[Skills.MAGIC]    ?: 0L
    val attackXp = xpPerSkill[Skills.ATTACK]   ?: 0L
    val strXp    = xpPerSkill[Skills.STRENGTH] ?: 0L
    return when {
        rangedXp > attackXp && rangedXp > strXp -> "ranged"
        magicXp  > attackXp && magicXp  > strXp -> "magic"
        else                                     -> "melee"
    }
}

