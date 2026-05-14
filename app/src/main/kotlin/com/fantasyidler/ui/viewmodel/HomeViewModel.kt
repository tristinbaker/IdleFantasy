package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.QueuedSessionStarter
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
    /** Food display name → "×qty" label — combat only */
    val foodConsumedLines: List<Pair<String, String>> = emptyList(),
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
    val sessionSummary: SessionSummary? = null,
    val characterSetupDone: Boolean = false,
    val characterName: String = "",
    val sessionQueue: List<QueuedAction> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val questRepo: QuestRepository,
    private val queuedSessionStarter: QueuedSessionStarter,
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
                isLoading          = false,
                coins              = player.coins,
                skillLevels        = json.decodeFromString(player.skillLevels),
                skillXp            = json.decodeFromString(player.skillXp),
                activeSession      = session,
                characterSetupDone = flags.characterSetupDone,
                characterName      = flags.characterName,
                sessionQueue       = flags.sessionQueue,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    // ------------------------------------------------------------------
    // Session actions
    // ------------------------------------------------------------------

    fun collectSession() {
        viewModelScope.launch {
            val latest = sessionRepo.getActiveSession() ?: return@launch
            if (!latest.completed && System.currentTimeMillis() < latest.endsAt) return@launch
            // If timed out but alarm hasn't fired yet, mark completed so it shows up in the query
            if (!latest.completed) sessionRepo.markCompleted(latest.sessionId)

            val sessions = sessionRepo.getAllCompletedSessions()
            if (sessions.isEmpty()) return@launch

            val petIds = gameData.pets.keys
            val flags: PlayerFlags = json.decodeFromString(playerRepo.getOrCreatePlayer().flags)
            val boostActive = flags.xpBoostExpiresAt > System.currentTimeMillis()
            val xpMult = if (boostActive) 2L else 1L

            // ── Accumulators ──────────────────────────────────────────────
            val combinedXpBySkill = mutableMapOf<String, Long>()
            val combinedItems     = mutableMapOf<String, Int>()
            val combinedKills     = mutableMapOf<String, Int>()
            val combinedFood      = mutableMapOf<String, Int>()
            var combinedCoins     = 0L
            var anyDied           = false
            val combinedBones     = mutableMapOf<String, Int>() // boneName → count
            var petMessage: String? = null
            var bossWon: Boolean? = null  // set when session is a boss fight

            val gatheringSkills = setOf(Skills.MINING, Skills.WOODCUTTING, Skills.FISHING,
                Skills.AGILITY, Skills.FIREMAKING, Skills.RUNECRAFTING)
            val craftingSkills  = setOf(Skills.SMITHING, Skills.COOKING, Skills.FLETCHING, Skills.CRAFTING)

            for (session in sessions) {
                val frames: List<SessionFrame> = json.decodeFromString(session.frames)
                when (session.skillName) {
                    "boss" -> {
                        val frame = frames.firstOrNull() ?: continue
                        val won = frame.kills > 0
                        bossWon = won
                        if (won) {
                            val its   = frame.items.toMutableMap()
                            val coins = its.remove("coins")?.toLong() ?: 0L
                            val pets  = its.filterKeys { it in petIds }
                            val loot  = its.filterKeys { it !in petIds }
                            playerRepo.applyMultiSkillResults(frame.xpBySkill, loot, coins)
                            for ((id, _) in pets) {
                                val pd = gameData.pets[id] ?: continue
                                if (playerRepo.addPetIfNew(id, pd.boostPercent))
                                    petMessage = "You found a pet: ${pd.displayName}!"
                            }
                            for ((skill, xp) in frame.xpBySkill) combinedXpBySkill[skill] = (combinedXpBySkill[skill] ?: 0L) + xp
                            for ((item, qty) in loot) combinedItems[item] = (combinedItems[item] ?: 0) + qty
                            combinedCoins += coins
                        }
                    }
                    "combat" -> {
                        val xpPerSkill = mutableMapOf<String, Long>()
                        val its        = mutableMapOf<String, Int>()
                        val kills      = mutableMapOf<String, Int>()
                        val food       = mutableMapOf<String, Int>()
                        val died       = frames.any { it.died }
                        if (died) anyDied = true
                        for (frame in frames) {
                            for ((skill, xp) in frame.xpBySkill) xpPerSkill[skill] = (xpPerSkill[skill] ?: 0L) + xp
                            for ((item, qty) in frame.items)      its[item]         = (its[item] ?: 0) + qty
                            for ((e, k) in frame.killsByEnemy)    kills[e]          = (kills[e] ?: 0) + k
                            for ((f, q) in frame.foodConsumed)    food[f]           = (food[f] ?: 0) + q
                        }
                        if (died) {
                            xpPerSkill.replaceAll { _, xp -> maxOf(1L, (xp * 0.1).toLong()) }
                            its.replaceAll { _, qty -> maxOf(0, (qty * 0.1).toInt()) }
                            its.entries.removeIf { it.value == 0 }
                        }
                        val coins = (its.remove("coins")?.toLong() ?: 0L).let { if (died) maxOf(0L, (it * 0.1).toLong()) else it }
                        val pets  = its.filterKeys { it in petIds }
                        val loot  = its.filterKeys { it !in petIds }
                        playerRepo.applyMultiSkillResults(xpPerSkill, loot, coins)
                        for ((id, _) in pets) {
                            val pd = gameData.pets[id] ?: continue
                            if (playerRepo.addPetIfNew(id, pd.boostPercent))
                                petMessage = "You found a pet: ${pd.displayName}!"
                        }
                        if (!died) {
                            questRepo.recordCombat(
                                dungeonKey         = session.activityKey,
                                killsByEnemy       = kills,
                                loot               = loot,
                                combatStyle        = detectCombatStyle(xpPerSkill),
                                foodConsumedTotal  = food.values.sum(),
                            )
                        }
                        if (food.isNotEmpty()) playerRepo.consumeItems(food)
                        for ((skill, xp) in xpPerSkill) combinedXpBySkill[skill] = (combinedXpBySkill[skill] ?: 0L) + xp
                        for ((item, qty) in loot)        combinedItems[item]      = (combinedItems[item] ?: 0) + qty
                        for ((e, k) in kills)            combinedKills[e]         = (combinedKills[e] ?: 0) + k
                        for ((f, q) in food)             combinedFood[f]          = (combinedFood[f] ?: 0) + q
                        combinedCoins += coins
                    }
                    else -> {
                        val totalXp = frames.sumOf { it.xpGain.toLong() }
                        val its     = mutableMapOf<String, Int>()
                        for (frame in frames) for ((item, qty) in frame.items) its[item] = (its[item] ?: 0) + qty
                        val pets    = its.filterKeys { it in petIds }
                        val regular = its.filterKeys { it !in petIds }
                        playerRepo.applySessionResults(session.skillName, totalXp, regular)
                        when (session.skillName) {
                            in gatheringSkills -> questRepo.recordGathering(session.skillName, regular)
                            in craftingSkills  -> questRepo.recordCrafting(session.skillName, regular)
                            Skills.PRAYER      -> questRepo.recordBuried(frames.sumOf { it.kills })
                        }
                        // Consume input materials at collect time (best-effort, like food)
                        when (session.skillName) {
                            Skills.PRAYER -> playerRepo.consumeItems(mapOf(session.activityKey to frames.size))
                            Skills.RUNECRAFTING -> {
                                val rune = gameData.runes[session.activityKey]
                                if (rune != null) playerRepo.consumeItems(mapOf("rune_essence" to rune.essenceCost * frames.size))
                            }
                            in craftingSkills -> {
                                val mats = when (session.skillName) {
                                    Skills.SMITHING  -> gameData.smithingRecipes[session.activityKey]?.materials
                                    Skills.COOKING   -> gameData.cookingRecipes[session.activityKey]?.let { mapOf(it.rawItem to 1) }
                                    Skills.FLETCHING -> gameData.fletchingRecipes[session.activityKey]?.materials
                                    Skills.CRAFTING  -> gameData.craftingRecipes[session.activityKey]?.materials
                                    else             -> null
                                }
                                if (mats != null) playerRepo.consumeItems(mats.mapValues { (_, needed) -> needed * frames.size })
                            }
                        }
                        for ((id, _) in pets) {
                            val pd = gameData.pets[id] ?: continue
                            if (playerRepo.addPetIfNew(id, pd.boostPercent))
                                petMessage = "You found a pet: ${pd.displayName}!"
                        }
                        combinedXpBySkill[session.skillName] = (combinedXpBySkill[session.skillName] ?: 0L) + totalXp
                        for ((item, qty) in regular) combinedItems[item] = (combinedItems[item] ?: 0) + qty
                        if (session.skillName == Skills.PRAYER) {
                            val count = frames.sumOf { it.kills }
                            val name  = gameData.bones[session.activityKey]?.displayName ?: session.activityKey
                            combinedBones[name] = (combinedBones[name] ?: 0) + count
                        }
                    }
                }
            }

            for (session in sessions) sessionRepo.deleteSession(session.sessionId)

            // ── Build summary ─────────────────────────────────────────────
            val n = sessions.size
            val last = sessions.last()

            val title = when {
                n > 1 -> "$n Sessions Complete!"
                last.skillName == "boss" -> {
                    val bossName = gameData.bosses[last.activityKey]?.displayName ?: last.activityKey
                    if (bossWon == true) "Defeated $bossName!" else "Defeated by $bossName."
                }
                last.skillName == "combat" -> {
                    val dungeonName = gameData.dungeons[last.activityKey]?.displayName ?: last.activityKey
                    if (anyDied) "$dungeonName — You Died" else "$dungeonName Complete!"
                }
                last.skillName == Skills.PRAYER -> "Prayer Session Complete"
                else -> "${last.skillName.toTitleCase()} Session Complete"
            }

            // For single-skill non-combat sessions use the compact totalXpLabel
            val useTotalLabel = n == 1 && combinedXpBySkill.size == 1 && combinedKills.isEmpty()
            val singleXp = combinedXpBySkill.values.firstOrNull() ?: 0L
            val boneBuriedLabel = combinedBones.entries.joinToString(", ") { (name, count) -> "$count $name buried" }

            val summary = SessionSummary(
                title          = title,
                died           = anyDied,
                xpLines        = if (useTotalLabel) emptyList()
                                 else combinedXpBySkill.entries.sortedByDescending { it.value }
                                     .map { (skill, xp) -> Pair(skill.toTitleCase(), "+${(xp * xpMult).formatXp()} XP") },
                totalXpLabel   = if (useTotalLabel) "+${(singleXp * xpMult).formatXp()} XP" else "",
                itemLines      = combinedItems.entries.sortedByDescending { it.value }
                                     .map { (key, qty) -> Pair(gameData.itemDisplayName(key), "×$qty") },
                coinsGained    = combinedCoins,
                killLines      = combinedKills.entries.sortedByDescending { it.value }
                                     .map { (enemy, kills) -> Pair(gameData.enemies[enemy]?.displayName ?: enemy.toTitleCase(), "×$kills") },
                foodConsumedLines = combinedFood.entries.sortedByDescending { it.value }
                                     .map { (food, qty) -> Pair(gameData.itemDisplayName(food), "×$qty") },
                boneBuriedLabel = boneBuriedLabel,
                boostWasActive  = boostActive,
            )

            _extra.update { it.copy(sessionSummary = summary, snackbarMessage = petMessage) }
            queuedSessionStarter.startNextQueued()
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

    fun removeFromQueue(index: Int) {
        viewModelScope.launch { playerRepo.removeFromQueue(index) }
    }

    fun saveCharacterProfile(name: String, gender: String, race: String) {
        viewModelScope.launch { playerRepo.updateCharacterProfile(name, gender, race) }
    }

    fun dismissCharacterSetup() {
        viewModelScope.launch { playerRepo.dismissCharacterSetup() }
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
