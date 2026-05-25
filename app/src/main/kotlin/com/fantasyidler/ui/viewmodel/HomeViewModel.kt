package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.BuildConfig
import com.fantasyidler.data.model.HiredWorker
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
import com.fantasyidler.repository.WorkerQueuedSessionStarter
import com.fantasyidler.simulator.SkillSimulator
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
    val pendingCollectCount: Int = 0,
    val snackbarMessage: String? = null,
    val sessionSummary: SessionSummary? = null,
    val characterSetupDone: Boolean = false,
    val characterName: String = "",
    val sessionQueue: List<QueuedAction> = emptyList(),
    val showWhatsNew: Boolean = false,
    /** Epoch ms when the last queued task will finish; 0 if queue is empty. */
    val queueEndsAt: Long = 0L,
    val workerSession: SkillSession? = null,
    val workerPendingCollect: Boolean = false,
    val hiredWorker: HiredWorker? = null,
    val workerQueue: List<QueuedAction> = emptyList(),
    val workerSummary: SessionSummary? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val questRepo: QuestRepository,
    private val queuedSessionStarter: QueuedSessionStarter,
    private val workerStarter: WorkerQueuedSessionStarter,
    private val json: Json,
) : ViewModel() {

    private val _extra = MutableStateFlow(HomeUiState())

    init {
        viewModelScope.launch { sessionRepo.recoverActiveSession(queuedSessionStarter) }
        viewModelScope.launch { sessionRepo.recoverActiveWorkerSession(workerStarter) }
        viewModelScope.launch { playerRepo.awardMissingCapes() }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        combine(playerRepo.playerFlow, sessionRepo.activeSessionFlow, sessionRepo.completedCountFlow) { a, b, c -> Triple(a, b, c) },
        combine(sessionRepo.activeWorkerSessionFlow, sessionRepo.workerCompletedCountFlow, _extra) { a, b, c -> Triple(a, b, c) },
    ) { (player, session, completedCount), (workerSession, workerCompleted, extra) ->
        if (player == null) extra.copy(
            isLoading = true, activeSession = session, pendingCollectCount = completedCount,
            workerSession = workerSession, workerPendingCollect = workerCompleted > 0,
        )
        else {
            val flags: PlayerFlags = json.decodeFromString(player.flags)
            val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            val agilityLevel = levels[Skills.AGILITY] ?: 1
            val sessionMs    = SkillSimulator.sessionDurationMs(agilityLevel)
            val perItemMs    = sessionMs / 60
            val queueStart   = session?.endsAt ?: System.currentTimeMillis()
            val queueEndsAt  = if (flags.sessionQueue.isEmpty()) 0L
                               else queueStart + flags.sessionQueue.sumOf {
                                   when {
                                       it.estimatedDurationMs > 0 -> it.estimatedDurationMs
                                       it.qty > 0                 -> it.qty.toLong() * perItemMs
                                       else                       -> sessionMs
                                   }
                               }
            extra.copy(
                isLoading           = false,
                coins               = player.coins,
                skillLevels         = levels,
                skillXp             = json.decodeFromString(player.skillXp),
                activeSession       = session,
                pendingCollectCount = completedCount,
                characterSetupDone  = flags.characterSetupDone,
                characterName       = flags.characterName,
                sessionQueue        = flags.sessionQueue,
                showWhatsNew        = flags.lastSeenVersionCode < BuildConfig.VERSION_CODE,
                queueEndsAt         = queueEndsAt,
                workerSession       = workerSession,
                workerPendingCollect = workerCompleted > 0,
                hiredWorker         = flags.hiredWorker,
                workerQueue         = flags.hiredWorker?.sessionQueue ?: emptyList(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    // ------------------------------------------------------------------
    // Session actions
    // ------------------------------------------------------------------

    fun collectSession() {
        viewModelScope.launch {
            // If the latest session timed out but its alarm hasn't fired yet, mark it completed now.
            val latest = sessionRepo.getActiveSession()
            if (latest != null && !latest.completed && System.currentTimeMillis() >= latest.endsAt) {
                sessionRepo.markCompleted(latest.sessionId)
            }

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
            val awardedCapes = mutableListOf<String>()

            val gatheringSkills = setOf(Skills.MINING, Skills.WOODCUTTING, Skills.FISHING,
                Skills.AGILITY, Skills.FIREMAKING, Skills.RUNECRAFTING)
            val craftingSkills  = setOf(Skills.SMITHING, Skills.COOKING, Skills.FLETCHING, Skills.CRAFTING, Skills.HERBLORE)

            for (session in sessions) {
                val frames: List<SessionFrame> = json.decodeFromString(session.frames)
                when (session.skillName) {
                    "boss" -> {
                        val frame = frames.lastOrNull() ?: continue
                        val won = frame.kills > 0
                        bossWon = won
                        val its   = frame.items.toMutableMap()
                        val coins = if (won) its.remove("coins")?.toLong() ?: 0L else 0L
                        val pets  = its.filterKeys { it in petIds }
                        val loot  = if (won) its.filterKeys { it !in petIds } else emptyMap()
                        val allFoodConsumed = mutableMapOf<String, Int>()
                        for (f in frames) f.foodConsumed.forEach { (k, v) -> allFoodConsumed[k] = (allFoodConsumed[k] ?: 0) + v }
                        awardedCapes += playerRepo.applyMultiSkillResults(frame.xpBySkill, loot, coins)
                        if (allFoodConsumed.isNotEmpty()) playerRepo.consumeItems(allFoodConsumed)
                        if (won) {
                            for ((id, _) in pets) {
                                val pd = gameData.pets[id] ?: continue
                                if (playerRepo.addPetIfNew(id, pd.boostPercent))
                                    petMessage = "You found a pet: ${pd.displayName}!"
                            }
                            questRepo.recordCombat(
                                dungeonKey   = session.activityKey,
                                killsByEnemy = mapOf(session.activityKey to 1),
                                loot         = loot,
                            )
                            playerRepo.recordDailyKills(mapOf(session.activityKey to 1))
                            for ((item, qty) in loot) combinedItems[item] = (combinedItems[item] ?: 0) + qty
                            combinedCoins += coins
                        }
                        for ((skill, xp) in frame.xpBySkill) combinedXpBySkill[skill] = (combinedXpBySkill[skill] ?: 0L) + xp
                    }
                    "combat" -> {
                        val xpPerSkill = mutableMapOf<String, Long>()
                        val its        = mutableMapOf<String, Int>()
                        val kills      = mutableMapOf<String, Int>()
                        val food       = mutableMapOf<String, Int>()
                        val arrows     = mutableMapOf<String, Int>()
                        val died       = frames.any { it.died }
                        if (died) anyDied = true
                        for (frame in frames) {
                            for ((skill, xp) in frame.xpBySkill)      xpPerSkill[skill] = (xpPerSkill[skill] ?: 0L) + xp
                            for ((item, qty) in frame.items)           its[item]         = (its[item] ?: 0) + qty
                            for ((e, k) in frame.killsByEnemy)         kills[e]          = (kills[e] ?: 0) + k
                            for ((f, q) in frame.foodConsumed)         food[f]           = (food[f] ?: 0) + q
                            for ((a, q) in frame.arrowsConsumed)       arrows[a]         = (arrows[a] ?: 0) + q
                        }
                        if (died) {
                            xpPerSkill.replaceAll { _, xp -> maxOf(1L, (xp * 0.1).toLong()) }
                            its.replaceAll { _, qty -> maxOf(0, (qty * 0.1).toInt()) }
                            its.entries.removeIf { it.value == 0 }
                        }
                        val coins = (its.remove("coins")?.toLong() ?: 0L).let { if (died) maxOf(0L, (it * 0.1).toLong()) else it }
                        val pets  = its.filterKeys { it in petIds }
                        val loot  = its.filterKeys { it !in petIds }
                        awardedCapes += playerRepo.applyMultiSkillResults(xpPerSkill, loot, coins)
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
                            playerRepo.incrementDungeonRun(session.activityKey)
                            if (kills.isNotEmpty()) playerRepo.recordDailyKills(kills)
                        }
                        if (food.isNotEmpty())   playerRepo.consumeItems(food)
                        if (arrows.isNotEmpty()) playerRepo.consumeItems(arrows)
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
                        awardedCapes += playerRepo.applySessionResults(session.skillName, totalXp, regular)
                        when (session.skillName) {
                            in gatheringSkills -> {
                                questRepo.recordGathering(session.skillName, regular)
                                playerRepo.recordDailyGathering(regular)
                            }
                            in craftingSkills  -> {
                                questRepo.recordCrafting(session.skillName, regular)
                                playerRepo.recordDailyCrafting(regular)
                            }
                            Skills.PRAYER      -> {
                                val buried = frames.sumOf { it.kills }
                                questRepo.recordBuried(buried)
                                playerRepo.recordDailyPrayer(buried)
                            }
                        }
                        // Firemaking logs consumed at collect time; all other input materials consumed at session start.
                        if (session.skillName == Skills.FIREMAKING) {
                            playerRepo.consumeItems(mapOf(session.activityKey to frames.size))
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
            val n    = sessions.size
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

            val capeMessage = if (awardedCapes.isNotEmpty()) {
                val names = awardedCapes.joinToString(", ") { gameData.itemDisplayName(it) }
                "Congratulations! You received: $names"
            } else null
            val snackbar = listOfNotNull(petMessage, capeMessage).joinToString(" • ").ifEmpty { null }
            _extra.update { it.copy(sessionSummary = summary, snackbarMessage = snackbar) }
            queuedSessionStarter.startNextQueued()
        }
    }

    fun onSessionExpiredLocally(sessionId: String) {
        viewModelScope.launch {
            val session = sessionRepo.getSession(sessionId) ?: return@launch
            if (!session.completed) {
                sessionRepo.markCompleted(sessionId)
                queuedSessionStarter.startNextQueued()
            }
        }
    }

    fun repeatActiveSession() {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSession() ?: return@launch
            val craftingSkills = setOf(Skills.SMITHING, Skills.COOKING, Skills.FLETCHING,
                Skills.CRAFTING, Skills.HERBLORE, Skills.RUNECRAFTING, Skills.PRAYER)
            val frames = json.decodeFromString<List<SessionFrame>>(session.frames)
            val qty = if (session.skillName in craftingSkills)
                frames.firstOrNull()?.kills ?: 0 else 0
            val displayName = when (session.skillName) {
                "combat" -> gameData.dungeons[session.activityKey]?.displayName ?: session.activityKey
                "boss"   -> gameData.bosses[session.activityKey]?.displayName ?: session.activityKey
                else     -> session.skillName.toTitleCase()
            }
            val materials = playerSessionMaterials(session.skillName, session.activityKey, qty, gameData)
            if (materials != null) {
                val ok = playerRepo.consumeItems(materials)
                if (!ok) {
                    _extra.update { it.copy(snackbarMessage = "Not enough materials to repeat $displayName.") }
                    return@launch
                }
            }
            val enqueued = playerRepo.enqueueAction(QueuedAction(
                skillName           = session.skillName,
                activityKey         = session.activityKey,
                skillDisplayName    = displayName,
                qty                 = qty,
                estimatedDurationMs = session.endsAt - session.startedAt,
            ))
            if (!enqueued && materials != null) playerRepo.addItems(materials)
            _extra.update {
                it.copy(snackbarMessage = if (enqueued) "Added to queue: $displayName." else "Queue is full (3/3).")
            }
        }
    }

    fun abandonSession() {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSession() ?: return@launch
            val frames: List<SessionFrame> = json.decodeFromString(session.frames)
            playerSessionMaterials(session.skillName, session.activityKey, frames.sumOf { it.kills }, gameData)
                ?.let { playerRepo.addItems(it) }
            sessionRepo.abandonSession(session.sessionId)
            queuedSessionStarter.startNextQueued()
        }
    }

    fun debugFinishSession() {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSession() ?: return@launch
            sessionRepo.markCompleted(session.sessionId)
        }
    }

    fun debugFinishWorkerSession() {
        viewModelScope.launch {
            val session = sessionRepo.getActiveWorkerSession() ?: return@launch
            sessionRepo.markCompleted(session.sessionId)
        }
    }

    fun onWorkerSessionExpiredLocally(sessionId: String) {
        viewModelScope.launch {
            val session = sessionRepo.getSession(sessionId) ?: return@launch
            if (!session.completed) {
                sessionRepo.markCompleted(sessionId)
                workerStarter.startNextQueued()
            }
        }
    }

    fun collectWorkerSession() {
        viewModelScope.launch {
            val latest = sessionRepo.getActiveWorkerSession()
            if (latest != null && !latest.completed && System.currentTimeMillis() >= latest.endsAt) {
                sessionRepo.markCompleted(latest.sessionId)
            }

            val sessions = sessionRepo.getAllCompletedWorkerSessions()
            if (sessions.isEmpty()) return@launch

            val petIds = gameData.pets.keys
            val flags: PlayerFlags = json.decodeFromString(playerRepo.getOrCreatePlayer().flags)
            val boostActive = flags.xpBoostExpiresAt > System.currentTimeMillis()
            val xpMult = if (boostActive) 2L else 1L

            val combinedXpBySkill = mutableMapOf<String, Long>()
            val combinedItems     = mutableMapOf<String, Int>()
            val combinedKills     = mutableMapOf<String, Int>()
            var combinedCoins     = 0L
            var anyDied           = false
            var petMessage: String? = null
            val awardedCapes = mutableListOf<String>()

            val gatheringSkills = setOf(Skills.MINING, Skills.WOODCUTTING, Skills.FISHING,
                Skills.AGILITY, Skills.FIREMAKING, Skills.RUNECRAFTING)
            val craftingSkills  = setOf(Skills.SMITHING, Skills.COOKING, Skills.FLETCHING, Skills.CRAFTING, Skills.HERBLORE)

            for (session in sessions) {
                val frames: List<SessionFrame> = json.decodeFromString(session.frames)
                val mult = session.efficiencyMultiplier

                when (session.skillName) {
                    "boss" -> {
                        val frame = frames.lastOrNull() ?: continue
                        val won = frame.kills > 0
                        if (won) {
                            val its   = frame.items.toMutableMap()
                            val coins = its.remove("coins")?.toLong() ?: 0L
                            val pets  = its.filterKeys { it in petIds }
                            val loot  = its.filterKeys { it !in petIds }
                            awardedCapes += playerRepo.applyMultiSkillResults(frame.xpBySkill, loot, coins, mult)
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
                        val died       = frames.any { it.died }
                        if (died) anyDied = true
                        for (frame in frames) {
                            for ((skill, xp) in frame.xpBySkill) xpPerSkill[skill] = (xpPerSkill[skill] ?: 0L) + xp
                            for ((item, qty) in frame.items)      its[item]         = (its[item] ?: 0) + qty
                            for ((e, k) in frame.killsByEnemy)    kills[e]          = (kills[e] ?: 0) + k
                        }
                        if (died) {
                            xpPerSkill.replaceAll { _, xp -> maxOf(1L, (xp * 0.1).toLong()) }
                            its.replaceAll { _, qty -> maxOf(0, (qty * 0.1).toInt()) }
                            its.entries.removeIf { it.value == 0 }
                        }
                        val coins = (its.remove("coins")?.toLong() ?: 0L).let { if (died) maxOf(0L, (it * 0.1).toLong()) else it }
                        val pets  = its.filterKeys { it in petIds }
                        val loot  = its.filterKeys { it !in petIds }
                        awardedCapes += playerRepo.applyMultiSkillResults(xpPerSkill, loot, coins, mult)
                        for ((id, _) in pets) {
                            val pd = gameData.pets[id] ?: continue
                            if (playerRepo.addPetIfNew(id, pd.boostPercent))
                                petMessage = "You found a pet: ${pd.displayName}!"
                        }
                        if (!died) {
                            questRepo.recordCombat(
                                dungeonKey        = session.activityKey,
                                killsByEnemy      = kills,
                                loot              = loot,
                                combatStyle       = detectCombatStyle(xpPerSkill),
                                foodConsumedTotal = 0,
                            )
                            playerRepo.incrementDungeonRun(session.activityKey)
                        }
                        for ((skill, xp) in xpPerSkill) combinedXpBySkill[skill] = (combinedXpBySkill[skill] ?: 0L) + xp
                        for ((item, qty) in loot)        combinedItems[item]      = (combinedItems[item] ?: 0) + qty
                        for ((e, k) in kills)            combinedKills[e]         = (combinedKills[e] ?: 0) + k
                        combinedCoins += coins
                    }
                    else -> {
                        val totalXp = frames.sumOf { it.xpGain.toLong() }
                        val its     = mutableMapOf<String, Int>()
                        for (frame in frames) for ((item, qty) in frame.items) its[item] = (its[item] ?: 0) + qty
                        val pets    = its.filterKeys { it in petIds }
                        val regular = its.filterKeys { it !in petIds }
                        awardedCapes += playerRepo.applySessionResults(session.skillName, totalXp, regular, mult)
                        when (session.skillName) {
                            in gatheringSkills -> questRepo.recordGathering(session.skillName, regular)
                            in craftingSkills  -> questRepo.recordCrafting(session.skillName, regular)
                            Skills.PRAYER      -> questRepo.recordBuried(frames.sumOf { it.kills })
                        }
                        for ((id, _) in pets) {
                            val pd = gameData.pets[id] ?: continue
                            if (playerRepo.addPetIfNew(id, pd.boostPercent))
                                petMessage = "You found a pet: ${pd.displayName}!"
                        }
                        val scaledXp      = if (mult == 1.0f) totalXp else (totalXp * mult).toLong()
                        val scaledRegular = if (mult == 1.0f) regular
                            else regular.mapValues { (_, v) -> (v * mult).toInt().coerceAtLeast(1) }
                        combinedXpBySkill[session.skillName] = (combinedXpBySkill[session.skillName] ?: 0L) + scaledXp
                        for ((item, qty) in scaledRegular) combinedItems[item] = (combinedItems[item] ?: 0) + qty
                    }
                }
            }

            for (session in sessions) sessionRepo.deleteSession(session.sessionId)
            playerRepo.clearHiredWorker()

            val n    = sessions.size
            val last = sessions.last()
            val title = when {
                n > 1 -> "Worker: $n Sessions Complete!"
                last.skillName == "boss" -> {
                    val bossName = gameData.bosses[last.activityKey]?.displayName ?: last.activityKey
                    "Worker: Defeated $bossName!"
                }
                last.skillName == "combat" -> {
                    val dungeonName = gameData.dungeons[last.activityKey]?.displayName ?: last.activityKey
                    if (anyDied) "Worker: $dungeonName — Died" else "Worker: $dungeonName Complete!"
                }
                else -> "Worker: ${last.skillName.toTitleCase()} Complete"
            }

            val useTotalLabel = n == 1 && combinedXpBySkill.size == 1 && combinedKills.isEmpty()
            val singleXp = combinedXpBySkill.values.firstOrNull() ?: 0L

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
                foodConsumedLines = emptyList(),
                boostWasActive  = boostActive,
            )

            val capeMessage = if (awardedCapes.isNotEmpty()) {
                val names = awardedCapes.joinToString(", ") { gameData.itemDisplayName(it) }
                "Congratulations! You received: $names"
            } else null
            val snackbar = listOfNotNull(petMessage, capeMessage).joinToString(" • ").ifEmpty { null }
            _extra.update { it.copy(workerSummary = summary, snackbarMessage = snackbar) }
        }
    }

    fun dismissWorker() {
        viewModelScope.launch {
            val flags: PlayerFlags = json.decodeFromString(playerRepo.getOrCreatePlayer().flags)

            val session = sessionRepo.getActiveWorkerSession()
            if (session != null) {
                val frames: List<SessionFrame> = json.decodeFromString(session.frames)
                val qty = frames.sumOf { it.kills }
                workerMaterialsFor(session.skillName, session.activityKey, qty)
                    ?.let { playerRepo.addItems(it) }
                sessionRepo.abandonSession(session.sessionId)
            }

            for (action in flags.hiredWorker?.sessionQueue ?: emptyList()) {
                workerMaterialsFor(action.skillName, action.activityKey, action.qty)
                    ?.let { playerRepo.addItems(it) }
            }

            flags.hiredWorker?.tier?.hireCost?.let { playerRepo.addCoins(it) }
            playerRepo.clearHiredWorker()
        }
    }

    private fun workerMaterialsFor(skillName: String, activityKey: String, qty: Int): Map<String, Int>? =
        playerSessionMaterials(skillName, activityKey, qty, gameData)
            ?: if (skillName == Skills.FIREMAKING && qty > 0) mapOf(activityKey to qty) else null

    fun workerSummaryConsumed() = _extra.update { it.copy(workerSummary = null) }

    fun removeFromQueue(index: Int) {
        viewModelScope.launch {
            val action = playerRepo.getQueue().getOrNull(index) ?: return@launch
            playerRepo.removeFromQueue(index)
            playerSessionMaterials(action.skillName, action.activityKey, action.qty, gameData)
                ?.let { playerRepo.addItems(it) }
        }
    }

    fun saveCharacterProfile(name: String, gender: String, race: String) {
        viewModelScope.launch { playerRepo.updateCharacterProfile(name, gender, race) }
    }

    fun dismissCharacterSetup() {
        viewModelScope.launch { playerRepo.dismissCharacterSetup() }
    }

    fun summaryConsumed() = _extra.update { it.copy(sessionSummary = null) }
    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }

    fun dismissWhatsNew() {
        viewModelScope.launch {
            playerRepo.markWhatsNewSeen(BuildConfig.VERSION_CODE)
        }
    }
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

fun playerSessionMaterials(
    skillName: String,
    activityKey: String,
    qty: Int,
    gameData: GameDataRepository,
): Map<String, Int>? {
    if (qty <= 0) return null
    return when (skillName) {
        Skills.PRAYER       -> mapOf(activityKey to qty)
        Skills.RUNECRAFTING -> gameData.runes[activityKey]?.let { mapOf("rune_essence" to it.essenceCost * qty) }
        Skills.SMITHING     -> gameData.smithingRecipes[activityKey]?.materials?.mapValues { it.value * qty }
        Skills.COOKING      -> gameData.cookingRecipes[activityKey]?.let { mapOf(it.rawItem to qty) }
        Skills.FLETCHING    -> gameData.fletchingRecipes[activityKey]?.materials?.mapValues { it.value * qty }
        Skills.CRAFTING     -> gameData.craftingRecipes[activityKey]?.materials?.mapValues { it.value * qty }
        Skills.HERBLORE     -> gameData.herbloreRecipes[activityKey]?.materials?.mapValues { it.value * qty }
        else                -> null
    }
}
