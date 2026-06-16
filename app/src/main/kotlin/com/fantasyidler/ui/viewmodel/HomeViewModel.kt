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
import com.fantasyidler.repository.ChurchRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.GuildRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.QueuedSessionStarter
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.repository.SlayerRepository
import com.fantasyidler.repository.TownRepository
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.repository.WorkerQueuedSessionStarter
import com.fantasyidler.simulator.SkillSimulator
import kotlin.math.roundToInt
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
    /** Arrow display name → "×qty" label — ranged combat only */
    val arrowsConsumedLines: List<Pair<String, String>> = emptyList(),
    /** Arrow display name → "+qty" label — ranged combat only */
    val arrowsReclaimedLines: List<Pair<String, String>> = emptyList(),
    /** Rune display name → "×qty" label — magic combat only */
    val runesConsumedLines: List<Pair<String, String>> = emptyList(),
    /** Rune display name → "+qty" label — magic combat only */
    val runesReclaimedLines: List<Pair<String, String>> = emptyList(),
    /** Bone type display name + count per type — prayer only */
    val boneBuriedLines: List<Pair<String, String>> = emptyList(),
    /** Whether the 2× XP boost was active during this session. */
    val boostWasActive: Boolean = false,
    /** Per-row XP bonus from active prayer blessing — parallel to xpLines, 0 if no blessing. */
    val xpLineBonuses: List<Long> = emptyList(),
    /** Per-row total XP (after boost + blessing) as Long — parallel to xpLines, for breakdown display. */
    val xpLineValues: List<Long> = emptyList(),
    /** XP bonus for the single-skill totalXpLabel case — 0 if no blessing. */
    val totalXpLabelBonus: Long = 0L,
    /** Total XP for the single-skill totalXpLabel case as Long — for breakdown display. */
    val totalXpValue: Long = 0L,
    /** Extra coins granted by active prayer blessing — 0 if no blessing. */
    val coinBlessingBonus: Long = 0L,
    /** Expedition: highlighted lore note lines found during the session. */
    val noteLines: List<String> = emptyList(),
    /** Expedition: set when this collect triggered a new combat dungeon unlock. */
    val unlockMessage: String? = null,
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
    val workerSession2: SkillSession? = null,
    val workerPendingCollect1: Boolean = false,
    val workerPendingCollect2: Boolean = false,
    val hiredWorker: HiredWorker? = null,
    val hiredWorker2: HiredWorker? = null,
    val workerQueue: List<QueuedAction> = emptyList(),
    val workerQueue2: List<QueuedAction> = emptyList(),
    val workerSummary: SessionSummary? = null,
    val activeBlessingKey: String = "",
    val activeBlessingRemainingMs: Long = 0L,
    val xpBoostRemainingMs: Long = 0L,
    val recentSessions: List<com.fantasyidler.data.model.RecentSession> = emptyList(),
    val showRecentActivityLog: Boolean = true,
    /** Total claimable guild quests + dailies across all guilds. Drives the badge on the town menu button. */
    val guildClaimableCount: Int = 0,
    /** Whether the Town section in the home screen is expanded. Survives recomposition. */
    val townExpanded: Boolean = false,
    /** Total XP the active session will grant (single-skill only; 0 for combat/boss/expedition). */
    val activeSessionXpGain: Long = 0L,
    /** Total XP the first worker's active session will grant. */
    val workerSessionXpGain: Long = 0L,
    /** Total XP the second worker's active session will grant. */
    val workerSession2XpGain: Long = 0L,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val questRepo: QuestRepository,
    private val guildRepo: GuildRepository,
    private val townRepo: TownRepository,
    private val queuedSessionStarter: QueuedSessionStarter,
    private val workerStarter: WorkerQueuedSessionStarter,
    private val slayerRepo: SlayerRepository,
    private val json: Json,
) : ViewModel() {

    private val _extra = MutableStateFlow(HomeUiState())

    init {
        viewModelScope.launch { sessionRepo.recoverActiveSession(queuedSessionStarter) }
        viewModelScope.launch { sessionRepo.recoverActiveWorkerSession(1, workerStarter) }
        viewModelScope.launch { sessionRepo.recoverActiveWorkerSession(2, workerStarter) }
        viewModelScope.launch { playerRepo.awardMissingCapes() }
        viewModelScope.launch { playerRepo.migratePetsFromInventory(gameData.pets.keys) }
        // AlarmManager delivery can be deferred by Doze for hours (issue 517: overnight
        // sessions frozen until their late alarms fire). While the app is open this
        // ticker completes overdue sessions and workers within a second.
        viewModelScope.launch {
            while (true) {
                try { sessionRepo.completeOverdueSessions(queuedSessionStarter, workerStarter) } catch (_: Exception) {}
                kotlinx.coroutines.delay(1_000L)
            }
        }
    }

    private data class WorkerFlowData(
        val session1: SkillSession?,
        val session2: SkillSession?,
        val completedCount1: Int,
        val completedCount2: Int,
        val extra: HomeUiState,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        combine(playerRepo.playerFlow, sessionRepo.activeSessionFlow, sessionRepo.completedCountFlow) { a, b, c -> Triple(a, b, c) },
        combine(
            sessionRepo.activeWorkerSessionFlow(1),
            sessionRepo.activeWorkerSessionFlow(2),
            combine(sessionRepo.workerCompletedCountFlow(1), sessionRepo.workerCompletedCountFlow(2)) { c1, c2 -> Pair(c1, c2) },
            _extra,
        ) { w1, w2, counts, extra -> WorkerFlowData(w1, w2, counts.first, counts.second, extra) },
        guildRepo.observeQuestProgress(),
    ) { playerTriple, workerData, guildProgress ->
        val (player, session, completedCount) = playerTriple
        val workerSession  = workerData.session1
        val workerSession2 = workerData.session2
        val extra = workerData.extra
        if (player == null) extra.copy(
            isLoading = true, activeSession = session, pendingCollectCount = completedCount,
            workerSession = workerSession, workerSession2 = workerSession2,
            workerPendingCollect1 = workerData.completedCount1 > 0,
            workerPendingCollect2 = workerData.completedCount2 > 0,
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
            val innXpMult = townRepo.workerXpMultiplier(flags)
            val sessionXpGain: (SkillSession?) -> Long = { s ->
                if (s == null || s.skillName in listOf("combat", "boss", "expedition", "farming")) 0L
                else try {
                    val base = json.decodeFromString<List<SessionFrame>>(s.frames).sumOf { it.xpGain.toLong() }
                    if (s.isWorkerSession) (base * s.efficiencyMultiplier * innXpMult).toLong()
                    else base
                } catch (_: Exception) { 0L }
            }
            val activeSessionXpGain   = sessionXpGain(session)
            val workerSessionXpGain   = sessionXpGain(workerSession)
            val workerSession2XpGain  = sessionXpGain(workerSession2)
            val progressMap      = guildProgress.associateBy { it.questId }
            val completedQuestIds = guildProgress.filter { it.completed }.map { it.questId }.toSet()
            val guildClaimableCount = GuildRepository.ALL_GUILDS.sumOf { guild ->
                val rep   = flags.guildReputation[guild] ?: 0L
                val level = guildRepo.guildLevel(guild, rep, completedQuestIds)
                val claimableQuests = gameData.guildQuests.values
                    .filter { it.guild == guild && level >= it.guildLevelRequired }
                    .count { quest ->
                        val row = progressMap[quest.id]
                        row != null && !row.completed && row.progress >= guildRepo.effectiveQuestAmountFromFlags(quest, flags)
                    }
                val dailies = guildRepo.getGuildDailiesWithProgress(guild, flags)
                claimableQuests + dailies.count { it.progress >= it.template.amount && !it.claimed }
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
                workerSession        = workerSession,
                workerSession2       = workerSession2,
                workerPendingCollect1 = workerData.completedCount1 > 0,
                workerPendingCollect2 = workerData.completedCount2 > 0,
                hiredWorker         = flags.hiredWorker,
                hiredWorker2        = flags.hiredWorker2,
                workerQueue         = flags.hiredWorker?.sessionQueue ?: emptyList(),
                workerQueue2        = flags.hiredWorker2?.sessionQueue ?: emptyList(),
                activeBlessingKey          = flags.activeBlessingKey,
                activeBlessingRemainingMs  = (flags.activeBlessingExpiresAt - System.currentTimeMillis()).coerceAtLeast(0L),
                xpBoostRemainingMs         = (flags.xpBoostExpiresAt - System.currentTimeMillis()).coerceAtLeast(0L),
                recentSessions             = flags.recentSessions,
                showRecentActivityLog      = flags.showRecentActivityLog,
                guildClaimableCount        = guildClaimableCount,
                activeSessionXpGain        = activeSessionXpGain,
                workerSessionXpGain        = workerSessionXpGain,
                workerSession2XpGain       = workerSession2XpGain,
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

            // Refresh guild dailies before recording any session progress so that any
            // sessions collected after the 6am cutoff are written to today's daily IDs,
            // not yesterday's stale ones (which would be wiped on the next guild screen open).
            guildRepo.ensureGuildDailiesRefreshed()

            val petIds = gameData.pets.keys
            val player = playerRepo.getOrCreatePlayer()
            val flags: PlayerFlags = json.decodeFromString(player.flags)
            val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
            val equippedCape = equipped[EquipSlot.CAPE]?.let { gameData.equipment[it] }
            val capeSkill    = equippedCape?.capeSkill
            val capeBonus    = equippedCape?.capeBonus ?: 0f
            val boostActive      = flags.xpBoostExpiresAt > System.currentTimeMillis()
            val xpMult           = if (boostActive) 2L else 1L
            val prayerCapeMult   = if (capeSkill == "prayer") 1f + capeBonus else 1f
            val blessingXpMult   = ChurchRepository.xpMultiplier(flags) * prayerCapeMult
            val blessingCoinMult = ChurchRepository.coinMultiplier(flags) * prayerCapeMult

            // ── Accumulators ──────────────────────────────────────────────
            val combinedXpBySkill       = mutableMapOf<String, Long>()
            val combinedItems           = mutableMapOf<String, Int>()
            val combinedKills           = mutableMapOf<String, Int>()
            val combinedFood            = mutableMapOf<String, Int>()
            val combinedArrows          = mutableMapOf<String, Int>()
            val combinedArrowsReclaimed = mutableMapOf<String, Int>()
            val combinedRunes           = mutableMapOf<String, Int>()
            val combinedRunesReclaimed  = mutableMapOf<String, Int>()
            val dailyKills              = mutableMapOf<String, Int>()
            var combinedCoins     = 0L
            var anyDied           = false
            val combinedBones     = mutableMapOf<String, Int>() // boneName → count
            var petMessage: String? = null
            var bossWon: Boolean? = null  // set when session is a boss fight
            val awardedCapes = mutableListOf<String>()
            var pendingExpeditionSummary: SessionSummary? = null

            val gatheringSkills = setOf(Skills.MINING, Skills.WOODCUTTING, Skills.FISHING, Skills.AGILITY)
            val craftingSkills  = setOf(Skills.SMITHING, Skills.COOKING, Skills.FLETCHING, Skills.CRAFTING, Skills.HERBLORE, Skills.FIREMAKING, Skills.RUNECRAFTING, Skills.CONSTRUCTION)

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
                        val allFoodConsumed   = mutableMapOf<String, Int>()
                        val allArrowsConsumed = mutableMapOf<String, Int>()
                        val allRunesConsumed  = mutableMapOf<String, Int>()
                        val bossXpBySkill     = mutableMapOf<String, Long>()
                        for (f in frames) {
                            f.foodConsumed.forEach   { (k, v) -> allFoodConsumed[k]   = (allFoodConsumed[k] ?: 0) + v }
                            f.arrowsConsumed.forEach { (k, v) -> allArrowsConsumed[k] = (allArrowsConsumed[k] ?: 0) + v }
                            f.runesConsumed.forEach  { (k, v) -> allRunesConsumed[k]  = (allRunesConsumed[k] ?: 0) + v }
                            f.xpBySkill.forEach      { (k, v) -> bossXpBySkill[k]     = (bossXpBySkill[k] ?: 0L) + v }
                        }
                        val bossSkillLvls    = playerRepo.getSkillLevels()
                        val bossArrowsRec    = allArrowsConsumed.mapValues { (_, qty) -> (qty * reclaimChance(bossSkillLvls[Skills.RANGED] ?: 1)).toInt() }.filterValues { it > 0 }
                        val bossRunesRec     = allRunesConsumed.mapValues  { (_, qty) -> (qty * reclaimChance(bossSkillLvls[Skills.MAGIC]  ?: 1)).toInt() }.filterValues { it > 0 }
                        awardedCapes += playerRepo.applyMultiSkillResults(bossXpBySkill, loot, coins)
                        if (allFoodConsumed.isNotEmpty())   playerRepo.consumeItems(allFoodConsumed)
                        if (allArrowsConsumed.isNotEmpty()) playerRepo.consumeItems(allArrowsConsumed)
                        if (bossArrowsRec.isNotEmpty())     playerRepo.addItems(bossArrowsRec)
                        if (bossRunesRec.isNotEmpty())      playerRepo.addItems(bossRunesRec)
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
                            dailyKills[session.activityKey] = (dailyKills[session.activityKey] ?: 0) + 1
                            playerRepo.recordWeeklyProgress("boss", session.activityKey, 1)
                            guildRepo.recordGuildCombat(mapOf(session.activityKey to 1), detectCombatStyle(bossXpBySkill))
                            for ((item, qty) in loot) combinedItems[item] = (combinedItems[item] ?: 0) + qty
                            combinedCoins += coins
                        }
                        for ((skill, xp) in bossXpBySkill) combinedXpBySkill[skill] = (combinedXpBySkill[skill] ?: 0L) + xp
                    }
                    "combat" -> {
                        val xpPerSkill = mutableMapOf<String, Long>()
                        val its        = mutableMapOf<String, Int>()
                        val kills      = mutableMapOf<String, Int>()
                        val food   = mutableMapOf<String, Int>()
                        val arrows = mutableMapOf<String, Int>()
                        val runes  = mutableMapOf<String, Int>()
                        val died   = frames.any { it.died }
                        if (died) anyDied = true
                        for (frame in frames) {
                            for ((skill, xp) in frame.xpBySkill)      xpPerSkill[skill] = (xpPerSkill[skill] ?: 0L) + xp
                            for ((item, qty) in frame.items)           its[item]         = (its[item] ?: 0) + qty
                            for ((e, k) in frame.killsByEnemy)         kills[e]          = (kills[e] ?: 0) + k
                            for ((f, q) in frame.foodConsumed)         food[f]           = (food[f] ?: 0) + q
                            for ((a, q) in frame.arrowsConsumed)       arrows[a]         = (arrows[a] ?: 0) + q
                            for ((r, q) in frame.runesConsumed)        runes[r]          = (runes[r] ?: 0) + q
                        }
                        if (died) {
                            xpPerSkill.replaceAll { _, xp -> maxOf(1L, (xp * 0.1).toLong()) }
                            its.replaceAll { _, qty -> maxOf(0, (qty * 0.1).toInt()) }
                            its.entries.removeIf { it.value == 0 }
                        }
                        val coins = (its.remove("coins")?.toLong() ?: 0L).let { if (died) maxOf(0L, (it * 0.1).toLong()) else it }
                        val pets  = its.filterKeys { it in petIds }
                        val loot  = its.filterKeys { it !in petIds }
                        if (!died) {
                            var slayerXp = 0L
                            for ((enemy, k) in kills) slayerXp += slayerRepo.recordKills(enemy, k)
                            if (slayerXp > 0L) xpPerSkill[Skills.SLAYER] = (xpPerSkill[Skills.SLAYER] ?: 0L) + slayerXp
                        }
                        awardedCapes += playerRepo.applyMultiSkillResults(xpPerSkill, loot, coins)
                        for ((id, _) in pets) {
                            val pd = gameData.pets[id] ?: continue
                            if (playerRepo.addPetIfNew(id, pd.boostPercent))
                                petMessage = "You found a pet: ${pd.displayName}!"
                        }
                        if (!died) {
                            val style = detectCombatStyle(xpPerSkill)
                            questRepo.recordCombat(
                                dungeonKey         = session.activityKey,
                                killsByEnemy       = kills,
                                loot               = loot,
                                combatStyle        = style,
                                foodConsumedTotal  = food.values.sum(),
                            )
                            playerRepo.incrementDungeonRun(session.activityKey)
                            if (kills.isNotEmpty()) {
                                for ((e, k) in kills) dailyKills[e] = (dailyKills[e] ?: 0) + k
                                guildRepo.recordGuildCombat(kills, style)
                            }
                        }
                        val skillLvls      = playerRepo.getSkillLevels()
                        val arrowsReclaimed = arrows.mapValues { (_, qty) -> (qty * reclaimChance(skillLvls[Skills.RANGED] ?: 1)).toInt() }.filterValues { it > 0 }
                        val runesReclaimed  = runes.mapValues  { (_, qty) -> (qty * reclaimChance(skillLvls[Skills.MAGIC]  ?: 1)).toInt() }.filterValues { it > 0 }
                        if (food.isNotEmpty())          playerRepo.consumeItems(food)
                        if (arrows.isNotEmpty())        playerRepo.consumeItems(arrows)
                        if (arrowsReclaimed.isNotEmpty()) playerRepo.addItems(arrowsReclaimed)
                        if (runesReclaimed.isNotEmpty())  playerRepo.addItems(runesReclaimed)
                        for ((skill, xp) in xpPerSkill) combinedXpBySkill[skill] = (combinedXpBySkill[skill] ?: 0L) + xp
                        for ((item, qty) in loot)        combinedItems[item]      = (combinedItems[item] ?: 0) + qty
                        for ((e, k) in kills)            combinedKills[e]         = (combinedKills[e] ?: 0) + k
                        for ((f, q) in food)             combinedFood[f]          = (combinedFood[f] ?: 0) + q
                        for ((a, q) in arrows)           combinedArrows[a]         = (combinedArrows[a] ?: 0) + q
                        for ((a, q) in arrowsReclaimed)  combinedArrowsReclaimed[a] = (combinedArrowsReclaimed[a] ?: 0) + q
                        for ((r, q) in runes)            combinedRunes[r]          = (combinedRunes[r] ?: 0) + q
                        for ((r, q) in runesReclaimed)   combinedRunesReclaimed[r] = (combinedRunesReclaimed[r] ?: 0) + q
                        combinedCoins += coins
                    }
                    "expedition" -> {
                        val dungeonData = gameData.skillingDungeons[session.activityKey]
                        val totalXp = frames.sumOf { it.xpGain.toLong() }
                        val its     = mutableMapOf<String, Int>()
                        for (frame in frames) for ((item, qty) in frame.items) its[item] = (its[item] ?: 0) + qty
                        val rawNotesFound = its.entries.filter { it.key.startsWith("note_") }.sumOf { it.value }
                        val regular    = its.filterKeys { !it.startsWith("note_") && it !in petIds }
                        val pets       = its.filterKeys { it in petIds }
                        val skillName  = dungeonData?.skill ?: Skills.MINING
                        awardedCapes += playerRepo.applySessionResults(skillName, totalXp, regular)
                        questRepo.recordGathering(skillName, regular)
                        playerRepo.recordDailyGathering(regular)
                        guildRepo.recordGuildGathering(skillName, regular)
                        for ((id, _) in pets) {
                            val pd = gameData.pets[id] ?: continue
                            if (playerRepo.addPetIfNew(id, pd.boostPercent))
                                petMessage = "You found a pet: ${pd.displayName}!"
                        }
                        combinedXpBySkill[skillName] = (combinedXpBySkill[skillName] ?: 0L) + totalXp
                        for ((item, qty) in regular) combinedItems[item] = (combinedItems[item] ?: 0) + qty
                        val currentFlags = playerRepo.getFlags()
                        val pityCount    = currentFlags.expeditionPityRuns[session.activityKey] ?: 0
                        val notesFound   = if (rawNotesFound == 0 && pityCount >= 9) 1 else rawNotesFound
                        val newPityCount = if (notesFound > 0) 0 else pityCount + 1
                        val newPityRuns  = currentFlags.expeditionPityRuns.toMutableMap().apply {
                            if (newPityCount == 0) remove(session.activityKey) else put(session.activityKey, newPityCount)
                        }
                        if (notesFound > 0 && dungeonData != null) {
                            val oldCount = currentFlags.skillingDungeonNotes[session.activityKey] ?: 0
                            val newCount = minOf(oldCount + notesFound, dungeonData.noteThreshold)
                            val newNotes = currentFlags.skillingDungeonNotes.toMutableMap()
                            newNotes[session.activityKey] = newCount
                            val newUnlocked = currentFlags.unlockedDungeons.toMutableList()
                            val unlockMsg: String?
                            if (newCount >= dungeonData.noteThreshold && !currentFlags.unlockedDungeons.contains(dungeonData.unlockDungeon)) {
                                newUnlocked += dungeonData.unlockDungeon
                                unlockMsg = dungeonData.unlockMessage
                            } else {
                                unlockMsg = null
                            }
                            playerRepo.updateFlags(currentFlags.copy(
                                skillingDungeonNotes = newNotes,
                                unlockedDungeons = newUnlocked,
                                expeditionPityRuns = newPityRuns,
                            ))
                            val revealed = dungeonData.noteTexts.take(newCount.coerceAtMost(dungeonData.noteTexts.size))
                            val newlyRevealedTexts = revealed.drop(oldCount.coerceAtMost(revealed.size))
                            val noteLabels = newlyRevealedTexts.map { it }
                            val expDisplayXp  = ((totalXp * xpMult).toDouble() * blessingXpMult).toLong()
                            val expXpBonus    = (expDisplayXp - totalXp * xpMult).coerceAtLeast(0L)
                            pendingExpeditionSummary = SessionSummary(
                                title = "${dungeonData.displayName} Expedition Complete",
                                totalXpLabel      = "+${expDisplayXp.formatXp()} XP",
                                totalXpLabelBonus = expXpBonus,
                                itemLines      = regular.entries.sortedByDescending { it.value }
                                    .map { (key, qty) -> Pair(gameData.itemDisplayName(key), "×$qty") },
                                noteLines      = noteLabels,
                                unlockMessage  = unlockMsg,
                                boostWasActive = boostActive,
                            )
                        } else {
                            // No note this run — persist the incremented pity counter.
                            playerRepo.updateFlags(currentFlags.copy(expeditionPityRuns = newPityRuns))
                        }
                    }
                    Skills.MERCANTILE -> {
                        val totalXp    = frames.sumOf { it.xpGain.toLong() }
                        val coinReturn = frames.sumOf { (it.items["_coins"] ?: 0).toLong() }
                        val mercantileCapeMult = if (capeSkill == "mercantile") 1f + capeBonus else 1f
                        val coinReturnBoosted = (coinReturn * blessingCoinMult * mercantileCapeMult).toLong()
                        awardedCapes += playerRepo.applySessionResults(Skills.MERCANTILE, totalXp, emptyMap())
                        playerRepo.addCoins(coinReturnBoosted)
                        guildRepo.recordGuildTrade(coinReturnBoosted)
                        playerRepo.recordWeeklyProgress("mercantile", session.activityKey, frames.size)
                        combinedXpBySkill[Skills.MERCANTILE] = (combinedXpBySkill[Skills.MERCANTILE] ?: 0L) + totalXp
                        combinedCoins += coinReturnBoosted
                    }
                    else -> {
                        val totalXp = frames.sumOf { it.xpGain.toLong() }
                        val its     = mutableMapOf<String, Int>()
                        for (frame in frames) for ((item, qty) in frame.items) its[item] = (its[item] ?: 0) + qty
                        val pets       = its.filterKeys { it in petIds }
                        val rawRegular = its.filterKeys { it !in petIds }
                        val capeMult   = if (capeSkill == session.skillName) 1f + capeBonus else 1f
                        val effectiveXp = if (capeMult > 1f && rawRegular.isEmpty()) (totalXp * capeMult).toLong() else totalXp
                        val regular     = if (capeMult > 1f && rawRegular.isNotEmpty()) rawRegular.mapValues { (_, qty) -> (qty * capeMult).roundToInt() } else rawRegular
                        awardedCapes += playerRepo.applySessionResults(session.skillName, effectiveXp, regular)
                        when (session.skillName) {
                            in gatheringSkills -> {
                                questRepo.recordGathering(session.skillName, regular)
                                playerRepo.recordDailyGathering(regular)
                                when (session.skillName) {
                                    Skills.AGILITY      -> {
                                        guildRepo.recordGuildSessions()
                                        playerRepo.recordWeeklyProgress("agility", session.activityKey, frames.size)
                                    }
                                    Skills.RUNECRAFTING -> guildRepo.recordGuildCrafting(session.skillName, regular)
                                    else                -> guildRepo.recordGuildGathering(session.skillName, regular)
                                }
                            }
                            in craftingSkills  -> {
                                questRepo.recordCrafting(session.skillName, regular)
                                playerRepo.recordDailyCrafting(regular)
                                guildRepo.recordGuildCrafting(session.skillName, regular)
                            }
                            Skills.THIEVING    -> {
                                val successCount = frames.count { it.success }
                                questRepo.recordThieving(session.activityKey, successCount, regular.filterKeys { it != "coins" })
                                guildRepo.recordGuildThieving(session.activityKey, successCount)
                            }
                            Skills.PRAYER      -> {
                                val buried = frames.sumOf { it.kills }
                                val isAshSession = gameData.bones[session.activityKey]?.isAsh == true
                                if (!isAshSession) {
                                    questRepo.recordBuried(buried)
                                    guildRepo.recordGuildPrayer(buried)
                                }
                                playerRepo.recordDailyPrayer(buried)
                            }
                            Skills.FARMING     -> guildRepo.recordGuildGathering(Skills.FARMING, regular)
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
            if (dailyKills.isNotEmpty()) playerRepo.recordDailyKills(dailyKills)

            // ── Recent sessions log ───────────────────────────────────────
            val newEntries = sessions.map { s ->
                val activityDisplay = when (s.skillName) {
                    "boss"       -> gameData.bosses[s.activityKey]?.displayName
                    "combat"     -> gameData.dungeons[s.activityKey]?.displayName
                    "expedition" -> gameData.skillingDungeons[s.activityKey]?.displayName
                    else         -> null
                } ?: s.activityKey.replace("_", " ").split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.titlecase() } }
                com.fantasyidler.data.model.RecentSession(
                    skillName = s.skillName,
                    activityDisplayName = activityDisplay,
                    activityKey = s.activityKey,
                )
            }
            val updatedFlags = playerRepo.getFlags()
            playerRepo.updateFlags(updatedFlags.copy(
                recentSessions = (newEntries.reversed() + updatedFlags.recentSessions).take(10),
            ))

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
                last.skillName == "expedition" -> {
                    val expName = gameData.skillingDungeons[last.activityKey]?.displayName ?: last.activityKey
                    "$expName Expedition Complete"
                }
                last.skillName == Skills.PRAYER -> "Prayer Session Complete"
                else -> "${last.skillName.toTitleCase()} Session Complete"
            }

            // For single-skill non-combat sessions use the compact totalXpLabel
            val useTotalLabel = n == 1 && combinedXpBySkill.size == 1 && combinedKills.isEmpty()
            val singleXp      = combinedXpBySkill.values.firstOrNull() ?: 0L
            val totalRawXp    = combinedXpBySkill.values.sum()
            val boneBuriedLines = combinedBones.entries.map { (name, count) -> Pair("$name buried", "×$count") }

            val displayedCoins    = (combinedCoins.toDouble() * blessingCoinMult).toLong()
            val coinBlessingBonus = displayedCoins - combinedCoins
            val sortedXpEntries   = combinedXpBySkill.entries.sortedByDescending { it.value }
            val xpLineBonuses     = sortedXpEntries.map { (_, xp) ->
                val base = xp * xpMult
                ((base.toDouble() * blessingXpMult).toLong() - base).coerceAtLeast(0L)
            }
            val singleXpBonus = run {
                val base = singleXp * xpMult
                ((base.toDouble() * blessingXpMult).toLong() - base).coerceAtLeast(0L)
            }

            val summary = SessionSummary(
                title          = title,
                died           = anyDied,
                xpLines        = if (useTotalLabel) emptyList()
                                 else sortedXpEntries
                                     .map { (skill, xp) -> Pair(skill.toTitleCase(), "+${((xp * xpMult).toDouble() * blessingXpMult).toLong().formatXp()} XP") },
                xpLineValues   = if (useTotalLabel) emptyList()
                                 else sortedXpEntries
                                     .map { (_, xp) -> ((xp * xpMult).toDouble() * blessingXpMult).toLong() },
                totalXpLabel      = if (useTotalLabel) "+${((singleXp * xpMult).toDouble() * blessingXpMult).toLong().formatXp()} XP" else "",
                totalXpLabelBonus = if (useTotalLabel) singleXpBonus else 0L,
                totalXpValue      = if (useTotalLabel) ((singleXp * xpMult).toDouble() * blessingXpMult).toLong() else 0L,
                itemLines      = combinedItems.entries.sortedByDescending { it.value }
                                     .map { (key, qty) -> Pair(gameData.itemDisplayName(key), "×$qty") },
                coinsGained    = displayedCoins,
                killLines      = combinedKills.entries.sortedByDescending { it.value }
                                     .map { (enemy, kills) -> Pair(gameData.enemies[enemy]?.displayName ?: enemy.toTitleCase(), "×$kills") },
                foodConsumedLines = combinedFood.entries.sortedByDescending { it.value }
                                     .map { (food, qty) -> Pair(gameData.itemDisplayName(food), "×$qty") },
                arrowsConsumedLines  = combinedArrows.entries.sortedByDescending { it.value }
                                         .map { (key, qty) -> Pair(gameData.itemDisplayName(key), "×$qty") },
                arrowsReclaimedLines = combinedArrowsReclaimed.entries.sortedByDescending { it.value }
                                         .map { (key, qty) -> Pair(gameData.itemDisplayName(key), "+$qty") },
                runesConsumedLines   = combinedRunes.entries.sortedByDescending { it.value }
                                         .map { (key, qty) -> Pair(gameData.itemDisplayName(key), "×$qty") },
                runesReclaimedLines  = combinedRunesReclaimed.entries.sortedByDescending { it.value }
                                         .map { (key, qty) -> Pair(gameData.itemDisplayName(key), "+$qty") },
                boneBuriedLines  = boneBuriedLines,
                boostWasActive   = boostActive,
                xpLineBonuses    = xpLineBonuses,
                coinBlessingBonus = coinBlessingBonus,
            )

            val capeMessage = if (awardedCapes.isNotEmpty()) {
                val names = awardedCapes.joinToString(", ") { gameData.itemDisplayName(it) }
                "Congratulations! You received: $names"
            } else null
            val snackbar = listOfNotNull(petMessage, capeMessage).joinToString(" • ").ifEmpty { null }
            _extra.update { it.copy(sessionSummary = pendingExpeditionSummary ?: summary, snackbarMessage = snackbar) }
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
                Skills.CRAFTING, Skills.HERBLORE, Skills.FIREMAKING, Skills.RUNECRAFTING, Skills.PRAYER)
            val frames = json.decodeFromString<List<SessionFrame>>(session.frames)
            val qty = if (session.skillName in craftingSkills)
                frames.firstOrNull()?.kills ?: 0 else 0
            val displayName = when (session.skillName) {
                "combat"     -> gameData.dungeons[session.activityKey]?.displayName ?: session.activityKey
                "boss"       -> gameData.bosses[session.activityKey]?.displayName ?: session.activityKey
                "expedition" -> gameData.skillingDungeons[session.activityKey]?.displayName ?: session.activityKey
                else         -> session.skillName.toTitleCase()
            }
            val coinCostForRepeat = if (session.skillName == Skills.MERCANTILE) {
                gameData.tradeRoutes.firstOrNull { it.id == session.activityKey }?.coinCost?.toLong() ?: 0L
            } else 0L
            if (coinCostForRepeat > 0) {
                val ok = playerRepo.spendCoins(coinCostForRepeat)
                if (!ok) {
                    _extra.update { it.copy(snackbarMessage = "Not enough coins to repeat $displayName.") }
                    return@launch
                }
            }
            val materials = playerSessionMaterials(session.skillName, session.activityKey, qty, gameData)
            if (materials != null) {
                val ok = playerRepo.consumeItems(materials)
                if (!ok) {
                    if (coinCostForRepeat > 0) playerRepo.addCoins(coinCostForRepeat)
                    _extra.update { it.copy(snackbarMessage = "Not enough materials to repeat $displayName.") }
                    return@launch
                }
            }
            val isCombat = session.skillName == "combat" || session.skillName == "boss"
            val weaponSlot = if (isCombat) {
                val totalXpBySkill = frames.fold(mutableMapOf<String, Long>()) { acc, f ->
                    f.xpBySkill.forEach { (k, v) -> acc[k] = (acc[k] ?: 0L) + v }
                    acc
                }
                val magicXp  = totalXpBySkill[Skills.MAGIC]   ?: 0L
                val rangedXp = totalXpBySkill[Skills.RANGED]  ?: 0L
                val strXp    = totalXpBySkill[Skills.STRENGTH] ?: 0L
                val atkXp    = totalXpBySkill[Skills.ATTACK]  ?: 0L
                when {
                    magicXp  > atkXp && magicXp  > strXp -> EquipSlot.WEAPON_MAGIC
                    rangedXp > atkXp && rangedXp > strXp -> EquipSlot.WEAPON_RANGED
                    strXp    > atkXp                     -> EquipSlot.WEAPON_STR
                    else                                 -> EquipSlot.WEAPON_ATK
                }
            } else null
            val flags = playerRepo.getFlags()
            val xpQueueMult = (if (flags.xpBoostExpiresAt > System.currentTimeMillis()) 2.0 else 1.0) * ChurchRepository.xpMultiplier(flags)
            val rawXpGain = frames.sumOf { it.xpGain }
            val player = if (isCombat) playerRepo.getOrCreatePlayer() else null
            val enqueued = playerRepo.enqueueAction(QueuedAction(
                skillName           = session.skillName,
                activityKey         = session.activityKey,
                skillDisplayName    = displayName,
                qty                 = qty,
                estimatedDurationMs = session.endsAt - session.startedAt,
                estimatedXpGain     = if (session.skillName in listOf("carnival", "expedition")) 0L
                                      else (rawXpGain * xpQueueMult).toLong(),
                weaponSlot          = weaponSlot,
                equippedSnapshot    = player?.equipped,
                spellName           = flags.activeSpell,
                arrowsKey           = flags.equippedArrows,
                coinRefund          = coinCostForRepeat,
            ))
            if (!enqueued) {
                if (coinCostForRepeat > 0) playerRepo.addCoins(coinCostForRepeat)
                if (materials != null) playerRepo.addItems(materials)
            }
            _extra.update {
                it.copy(snackbarMessage = if (enqueued) "Added to queue: $displayName." else "Queue is full (3/3).")
            }
        }
    }

    fun abandonSession() {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSession() ?: return@launch
            val frames: List<SessionFrame> = json.decodeFromString(session.frames)
            if (session.skillName == Skills.MERCANTILE) {
                val coinCost = gameData.tradeRoutes.firstOrNull { it.id == session.activityKey }?.coinCost?.toLong() ?: 0L
                if (coinCost > 0) playerRepo.addCoins(coinCost)
            } else {
                playerSessionMaterials(session.skillName, session.activityKey, frames.sumOf { it.kills }, gameData)
                    ?.let { playerRepo.addItems(it) }
            }
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

    fun debugFinishWorkerSession(slot: Int = 1) {
        viewModelScope.launch {
            val session = sessionRepo.getActiveWorkerSession(slot) ?: return@launch
            sessionRepo.markCompleted(session.sessionId)
        }
    }

    fun onWorkerSessionExpiredLocally(sessionId: String) {
        viewModelScope.launch {
            val session = sessionRepo.getSession(sessionId) ?: return@launch
            if (!session.completed) {
                sessionRepo.markCompleted(sessionId)
                workerStarter.startNextQueued(session.workerSlot.coerceAtLeast(1))
            }
        }
    }

    fun collectWorkerSession() {
        viewModelScope.launch {
            sessionRepo.markAllExpiredWorkerSessions()
            for (slot in 1..2) {
                val latest = sessionRepo.getActiveWorkerSession(slot)
                if (latest != null && !latest.completed && System.currentTimeMillis() >= latest.endsAt) {
                    sessionRepo.markCompleted(latest.sessionId)
                }
            }

            val slot1Sessions = sessionRepo.getAllCompletedWorkerSessions(1)
            val slot2Sessions = sessionRepo.getAllCompletedWorkerSessions(2)
            val sessions = slot1Sessions + slot2Sessions
            if (sessions.isEmpty()) return@launch

            val petIds = gameData.pets.keys
            val flags: PlayerFlags = json.decodeFromString(playerRepo.getOrCreatePlayer().flags)
            val boostActive      = flags.xpBoostExpiresAt > System.currentTimeMillis()
            val xpMult           = if (boostActive) 2L else 1L
            val blessingXpMult   = ChurchRepository.xpMultiplier(flags)
            val blessingCoinMult = ChurchRepository.coinMultiplier(flags)
            val innXpMult        = townRepo.workerXpMultiplier(flags)

            val combinedXpBySkill = mutableMapOf<String, Long>()
            val combinedItems     = mutableMapOf<String, Int>()
            val combinedKills     = mutableMapOf<String, Int>()
            var combinedCoins     = 0L
            var anyDied           = false
            var petMessage: String? = null
            val awardedCapes = mutableListOf<String>()

            val gatheringSkills = setOf(Skills.MINING, Skills.WOODCUTTING, Skills.FISHING, Skills.AGILITY)
            val craftingSkills  = setOf(Skills.SMITHING, Skills.COOKING, Skills.FLETCHING, Skills.CRAFTING, Skills.HERBLORE, Skills.FIREMAKING, Skills.RUNECRAFTING)

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
                            val workerBossXp = mutableMapOf<String, Long>()
                            for (f in frames) f.xpBySkill.forEach { (k, v) -> workerBossXp[k] = (workerBossXp[k] ?: 0L) + v }
                            awardedCapes += playerRepo.applyMultiSkillResults(workerBossXp, loot, coins, mult)
                            for ((id, _) in pets) {
                                val pd = gameData.pets[id] ?: continue
                                if (playerRepo.addPetIfNew(id, pd.boostPercent))
                                    petMessage = "You found a pet: ${pd.displayName}!"
                            }
                            for ((skill, xp) in workerBossXp) combinedXpBySkill[skill] = (combinedXpBySkill[skill] ?: 0L) + xp
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
                            playerRepo.incrementDungeonRun(session.activityKey)
                        }
                        for ((skill, xp) in xpPerSkill) combinedXpBySkill[skill] = (combinedXpBySkill[skill] ?: 0L) + xp
                        for ((item, qty) in loot)        combinedItems[item]      = (combinedItems[item] ?: 0) + qty
                        for ((e, k) in kills)            combinedKills[e]         = (combinedKills[e] ?: 0) + k
                        combinedCoins += coins
                    }
                    "expedition" -> {
                        val dungeonData = gameData.skillingDungeons[session.activityKey]
                        val totalXp = frames.sumOf { it.xpGain.toLong() }
                        val its     = mutableMapOf<String, Int>()
                        for (frame in frames) for ((item, qty) in frame.items) its[item] = (its[item] ?: 0) + qty
                        val pets    = its.filterKeys { it in petIds }
                        val regular = its.filterKeys { !it.startsWith("note_") && it !in petIds }
                        val skillName = dungeonData?.skill ?: Skills.MINING
                        awardedCapes += playerRepo.applySessionResults(skillName, totalXp, regular, mult)
                        for ((id, _) in pets) {
                            val pd = gameData.pets[id] ?: continue
                            if (playerRepo.addPetIfNew(id, pd.boostPercent))
                                petMessage = "You found a pet: ${pd.displayName}!"
                        }
                        val scaledXp      = if (mult == 1.0f) totalXp else (totalXp * mult).toLong()
                        val scaledRegular = if (mult == 1.0f) regular
                            else regular.mapValues { (_, v) -> (v * mult).toInt().coerceAtLeast(1) }
                        combinedXpBySkill[skillName] = (combinedXpBySkill[skillName] ?: 0L) + scaledXp
                        for ((item, qty) in scaledRegular) combinedItems[item] = (combinedItems[item] ?: 0) + qty
                    }
                    else -> {
                        val baseXp  = frames.sumOf { it.xpGain.toLong() }
                        val totalXp = (baseXp * innXpMult).toLong()
                        val its     = mutableMapOf<String, Int>()
                        for (frame in frames) for ((item, qty) in frame.items) its[item] = (its[item] ?: 0) + qty
                        val pets    = its.filterKeys { it in petIds }
                        val regular = its.filterKeys { it !in petIds }
                        awardedCapes += playerRepo.applySessionResults(session.skillName, totalXp, regular, mult)
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
            if (slot1Sessions.isNotEmpty()) playerRepo.clearHiredWorker(1)
            if (slot2Sessions.isNotEmpty()) playerRepo.clearHiredWorker(2)

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

            val useTotalLabel    = n == 1 && combinedXpBySkill.size == 1 && combinedKills.isEmpty()
            val singleXp         = combinedXpBySkill.values.firstOrNull() ?: 0L
            val displayedCoins   = (combinedCoins.toDouble() * blessingCoinMult).toLong()
            val coinBlessingBonus = displayedCoins - combinedCoins
            val sortedXpEntries   = combinedXpBySkill.entries.sortedByDescending { it.value }
            val xpLineBonuses     = sortedXpEntries.map { (_, xp) ->
                val base = xp * xpMult
                ((base.toDouble() * blessingXpMult).toLong() - base).coerceAtLeast(0L)
            }
            val singleXpBonus = run {
                val base = singleXp * xpMult
                ((base.toDouble() * blessingXpMult).toLong() - base).coerceAtLeast(0L)
            }

            val summary = SessionSummary(
                title          = title,
                died           = anyDied,
                xpLines        = if (useTotalLabel) emptyList()
                                 else sortedXpEntries
                                     .map { (skill, xp) -> Pair(skill.toTitleCase(), "+${((xp * xpMult).toDouble() * blessingXpMult).toLong().formatXp()} XP") },
                xpLineValues   = if (useTotalLabel) emptyList()
                                 else sortedXpEntries
                                     .map { (_, xp) -> ((xp * xpMult).toDouble() * blessingXpMult).toLong() },
                totalXpLabel      = if (useTotalLabel) "+${((singleXp * xpMult).toDouble() * blessingXpMult).toLong().formatXp()} XP" else "",
                totalXpLabelBonus = if (useTotalLabel) singleXpBonus else 0L,
                totalXpValue      = if (useTotalLabel) ((singleXp * xpMult).toDouble() * blessingXpMult).toLong() else 0L,
                itemLines      = combinedItems.entries.sortedByDescending { it.value }
                                     .map { (key, qty) -> Pair(gameData.itemDisplayName(key), "×$qty") },
                coinsGained    = displayedCoins,
                killLines      = combinedKills.entries.sortedByDescending { it.value }
                                     .map { (enemy, kills) -> Pair(gameData.enemies[enemy]?.displayName ?: enemy.toTitleCase(), "×$kills") },
                foodConsumedLines = emptyList(),
                boostWasActive   = boostActive,
                xpLineBonuses    = xpLineBonuses,
                coinBlessingBonus = coinBlessingBonus,
            )

            val capeMessage = if (awardedCapes.isNotEmpty()) {
                val names = awardedCapes.joinToString(", ") { gameData.itemDisplayName(it) }
                "Congratulations! You received: $names"
            } else null
            val snackbar = listOfNotNull(petMessage, capeMessage).joinToString(" • ").ifEmpty { null }
            _extra.update { it.copy(workerSummary = summary, snackbarMessage = snackbar) }
        }
    }

    fun dismissWorker(slot: Int = 1) {
        viewModelScope.launch {
            val flags: PlayerFlags = json.decodeFromString(playerRepo.getOrCreatePlayer().flags)
            val worker = if (slot == 2) flags.hiredWorker2 else flags.hiredWorker

            val session = sessionRepo.getActiveWorkerSession(slot)
            if (session != null) {
                val frames: List<SessionFrame> = json.decodeFromString(session.frames)
                val qty = frames.sumOf { it.kills }
                workerMaterialsFor(session.skillName, session.activityKey, qty)
                    ?.let { playerRepo.addItems(it) }
                sessionRepo.abandonSession(session.sessionId)
            }

            for (action in worker?.sessionQueue ?: emptyList()) {
                workerMaterialsFor(action.skillName, action.activityKey, action.qty)
                    ?.let { playerRepo.addItems(it) }
            }

            worker?.tier?.hireCost?.let { playerRepo.addCoins(it) }
            playerRepo.clearHiredWorker(slot)
        }
    }

    private fun workerMaterialsFor(skillName: String, activityKey: String, qty: Int): Map<String, Int>? =
        playerSessionMaterials(skillName, activityKey, qty, gameData)
            ?: null

    fun workerSummaryConsumed() = _extra.update { it.copy(workerSummary = null) }

    fun removeFromQueue(index: Int) {
        viewModelScope.launch {
            val action = playerRepo.removeFromQueue(index) ?: return@launch
            if (action.coinRefund > 0) playerRepo.addCoins(action.coinRefund)
            playerSessionMaterials(action.skillName, action.activityKey, action.qty, gameData)
                ?.let { playerRepo.addItems(it) }
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch { playerRepo.moveQueueItem(fromIndex, toIndex) }
    }

    fun saveCharacterProfile(name: String, gender: String, race: String) {
        viewModelScope.launch { playerRepo.updateCharacterProfile(name, gender, race) }
    }

    fun dismissCharacterSetup() {
        viewModelScope.launch { playerRepo.dismissCharacterSetup() }
    }

    fun summaryConsumed() = _extra.update { it.copy(sessionSummary = null) }
    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }

    fun toggleTownExpanded() = _extra.update { it.copy(townExpanded = !it.townExpanded) }

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
        Skills.HERBLORE      -> gameData.herbloreRecipes[activityKey]?.materials?.mapValues { it.value * qty }
        Skills.FIREMAKING    -> mapOf(activityKey to qty)
        Skills.CONSTRUCTION  -> gameData.constructionRecipes[activityKey]?.materials?.mapValues { it.value * qty }
        else                 -> null
    }
}

/** Returns the fraction of consumed ammo/runes a player recoups: 25% at level 1, 75% at level 99. */
private fun reclaimChance(level: Int): Double = 0.25 + (level - 1) / 98.0 * 0.50
