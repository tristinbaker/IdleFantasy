package com.fantasyidler.ui.viewmodel

import com.fantasyidler.util.withAppLocale

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.data.json.BlessingData
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.model.DungeonRunStats
import com.fantasyidler.data.model.ElderContent
import com.fantasyidler.data.model.HiredWorker
import com.fantasyidler.data.model.OwnedPet
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.BoostRepository
import com.fantasyidler.repository.ChurchRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.GuildRepository
import com.fantasyidler.repository.MonumentRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.simulator.PrestigeBoosts
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.QueuedSessionStarter
import com.fantasyidler.repository.SeasonalEventRepository
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.repository.SlayerRepository
import com.fantasyidler.repository.SaveSlotRepository
import com.fantasyidler.repository.TitleRepository
import com.fantasyidler.repository.TownRepository
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.Player
import com.fantasyidler.data.model.RecentSession
import com.fantasyidler.repository.WorkerQueuedSessionStarter
import com.fantasyidler.repository.resolveCapeMultiplier
import com.fantasyidler.repository.blessingPrayerCapeMult
import com.fantasyidler.simulator.SkillSimulator
import kotlin.math.roundToInt
import com.fantasyidler.ui.screen.UNLOCK_TOLERANCE
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.craftDurationEfficiency
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.singleBatchItems
import com.fantasyidler.util.toTitleCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** Summary of the currently active Seasonal Event, shown as a row on the Home screen. Null between events. */
data class SeasonalEventSummary(
    val id: String,
    val displayName: String,
    val tokens: Int,
    val goal: Int,
    val endMs: Long,
    val bannerIcon: String? = null,
)

private val COMBAT_CAPE_SKILLS = setOf(
    "attack", "strength", "defense", "ranged", "magic", "hp",
    "warriors", "archers", "mages",
)

/** Which combat XP stat(s) each combat/guild cape's percentage bonus applies to. */
private val CAPE_SKILL_TO_COMBAT_STATS: Map<String, Set<String>> = mapOf(
    "attack"   to setOf(Skills.ATTACK),
    "strength" to setOf(Skills.STRENGTH),
    "defense"  to setOf(Skills.DEFENSE),
    "ranged"   to setOf(Skills.RANGED),
    "magic"    to setOf(Skills.MAGIC),
    "hp"       to setOf(Skills.HITPOINTS),
    "warriors" to setOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENSE),
    "archers"  to setOf(Skills.RANGED),
    "mages"    to setOf(Skills.MAGIC),
)

/**
 * True when a completed session is unambiguously an Elder Isle session, derived from the
 * activity key rather than the `isElderSession` DB flag. Isle activity keys (Coastal Run,
 * Mythrite Ore, elder recipes, isle dungeons, etc.) can't be reached from the mainland,
 * so the key itself is authoritative. This backstops any code path that failed to stamp
 * `session.isElderSession = true` at start time — reports of "no isle XP" in v1.15.3
 * indicate the flag can silently end up false despite the session being isle-side.
 */
private fun sessionIsIsleByActivity(session: SkillSession): Boolean {
    val key = session.activityKey
    return when (session.skillName) {
        Skills.MINING       -> key in ElderContent.ORES
        Skills.WOODCUTTING  -> key in ElderContent.TREES
        Skills.FISHING      -> key in ElderContent.FISH
        Skills.SMITHING     -> key in ElderContent.SMITHING_RECIPES
        Skills.COOKING      -> key in ElderContent.COOKING_RECIPES
        Skills.AGILITY      -> key in ElderContent.AGILITY_COURSES
        "combat"            -> key in ISLE_DUNGEON_KEYS
        "boss"              -> key in ISLE_BOSS_KEYS
        else                -> false
    }
}
private val ISLE_DUNGEON_KEYS = setOf("beach_and_cliffs", "ancient_forest", "volcano_peak", "abyssal_depths")
private val ISLE_BOSS_KEYS    = setOf("last_elder")

private fun applyCombatCapeBonus(xpPerSkill: MutableMap<String, Long>, capeSkill: String?, capeBonus: Float) {
    if (capeSkill == null || capeBonus <= 0f) return
    val boostedStats = CAPE_SKILL_TO_COMBAT_STATS[capeSkill] ?: return
    for (stat in boostedStats) {
        xpPerSkill[stat]?.let { xpPerSkill[stat] = (it * (1.0 + capeBonus)).toLong() }
    }
}

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
    /** Whether any 2× XP boost was active during this session. */
    val boostWasActive: Boolean = false,
    /** Per-row 2x-boost factor (1, 2, or 4: purchased and prestige boosts stack) — parallel to xpLines. */
    val xpLineBoostFactors: List<Long> = emptyList(),
    /** 2x-boost factor for the single-skill totalXpLabel case. */
    val totalXpBoostFactor: Long = 1L,
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
    val rareItems: Set<String> = emptySet(),
)

data class HomeUiState(
    val isLoading: Boolean = true,
    /** True while collectSession() is processing a batch; guards against double-collecting on repeat taps. */
    val isCollecting: Boolean = false,
    val coins: Long = 0L,
    val skillLevels: Map<String, Int> = emptyMap(),
    val skillXp: Map<String, Long> = emptyMap(),
    val activeSession: SkillSession? = null,
    val pendingCollectCount: Int = 0,
    val snackbarMessage: String? = null,
    /** Non-null when a new pet was found; drives the pet-found dialog. Consumed by the UI. */
    val petFoundName: String? = null,
    val sessionSummary: SessionSummary? = null,
    val characterSetupDone: Boolean = false,
    val characterName: String = "",
    val characterRace: String = "",
    val characterSkinTone: Int = 1,
    val characterHairStyle: Int = 1,
    val characterHairColor: String = "a",
    val characterEyeStyle: Int = 1,
    val characterBeardStyle: Int = 0,
    val characterBeardColor: String = "a",
    val showCharacterViewer: Boolean = true,
    val showStatsBar: Boolean = true,
    val showSessionEndTime: Boolean = true,
    val equippedTitle: String? = null,
    /** Resolved, localized title name (e.g. "Master Smith"), or null if none equipped. */
    val titleName: String? = null,
    val sessionQueue: List<QueuedAction> = emptyList(),
    val maxQueueSize: Int = 3,
    /** Highest Tower floor already cleared; used to preview upcoming queued floor numbers live. */
    val towerCurrentFloor: Int = 0,
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
    val allBlessings: List<BlessingData> = emptyList(),
    val prayerCapeMult: Float = 1f,
    val activeBlessingRemainingMs: Long = 0L,
    /** True when the Grand Monument's once-a-day touch is unlocked and unclaimed today. */
    val monumentTouchAvailable: Boolean = false,
    val showMonumentTouchIndicator: Boolean = true,
    val xpBoostRemainingMs: Long = 0L,
    /** Skill → remaining ms for active post-prestige 48h boosts (earned, so shown for ironmen too). */
    val prestigeBoostsRemainingMs: Map<String, Long> = emptyMap(),
    val ironman: Boolean = false,
    val recentSessions: List<RecentSession> = emptyList(),
    val showRecentActivityLog: Boolean = true,
    val showJournalButton: Boolean = true,
    /** Show the top-bar character switch button; hidden with a single character to not confuse new players. */
    val showCharacterSwitch: Boolean = false,
    val showSeasonalEvents: Boolean = true,
    val collapsibleTownGrid: Boolean = true,
    val elderIsleUnlocked: Boolean = false,
    val onElderIsle: Boolean = false,
    /** True once the Dock town-building is at tier 1+. Shows the Set Sail button on the
     *  mainland Home tab even before the Sea Serpent is defeated, so the sail-blocked
     *  message can guide the player to the boss fight. */
    val dockBuilt: Boolean = false,
    /** True until the player dismisses the first-arrival welcome splash on Elder Isle. */
    val showIsleWelcome: Boolean = false,
    /** Snapshot of the player's shared inventory. Exposed for isle Home stat chips. */
    val inventory: Map<String, Int> = emptyMap(),
    /** Per-dungeon completed-run counts. Isle Quests tab uses this for quest-chain progress. */
    val dungeonRuns: Map<String, Int> = emptyMap(),
    /** Lifetime kills per enemy/boss key. Isle Quests uses this for boss-kill quest checks. */
    val enemyKills: Map<String, Int> = emptyMap(),
    val townGridExpanded: Boolean = true,
    val playerNotes: String = "",
    val journalSheetOpen: Boolean = false,
    /** Total claimable guild quests + dailies across all guilds. Drives the badge on the town menu button. */
    val guildClaimableCount: Int = 0,
    /** The currently active Seasonal Event, or null if no event is running right now. */
    val activeSeasonalEvent: SeasonalEventSummary? = null,
    /** Total XP the active session will grant (single-skill only; 0 for combat/boss/expedition). */
    val activeSessionXpGain: Long = 0L,
    /** Batch totals of the active crafting-type session (empty for gathering/combat sessions). */
    val activeSessionAssignedItems: Map<String, Int> = emptyMap(),
    /** 1-based index of the boss fight currently running within a multi-fight repeat request. 0 = not repeating. */
    val activeBossRepeatIndex: Int = 0,
    /** Total fights requested for the current boss repeat run. */
    val activeBossRepeatTotal: Int = 0,
    /** 1-based index of the dungeon run currently running within a multi-run repeat request. 0 = not repeating. */
    val activeDungeonRepeatIndex: Int = 0,
    /** Total runs requested for the current dungeon repeat run. */
    val activeDungeonRepeatTotal: Int = 0,
    /** Total XP the first worker's active session will grant. */
    val workerSessionXpGain: Long = 0L,
    /** Total XP the second worker's active session will grant. */
    val workerSession2XpGain: Long = 0L,
    /** Total items assigned to the first worker's active batch job (crafting-style sessions only). */
    val workerSessionAssignedItems: Map<String, Int> = emptyMap(),
    /** Total items assigned to the second worker's active batch job (crafting-style sessions only). */
    val workerSession2AssignedItems: Map<String, Int> = emptyMap(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val boostRepo: BoostRepository,
    @ApplicationContext private val context: Context,
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val questRepo: QuestRepository,
    private val guildRepo: GuildRepository,
    private val townRepo: TownRepository,
    private val queuedSessionStarter: QueuedSessionStarter,
    private val workerStarter: WorkerQueuedSessionStarter,
    private val slayerRepo: SlayerRepository,
    private val monumentRepo: MonumentRepository,
    private val seasonalEventRepo: SeasonalEventRepository,
    private val titleRepo: TitleRepository,
    private val saveSlotRepo: SaveSlotRepository,
    private val json: Json,
) : ViewModel() {

    private val _extra = MutableStateFlow(HomeUiState())

    init {
        viewModelScope.launch { sessionRepo.recoverActiveSession(queuedSessionStarter) }
        viewModelScope.launch { sessionRepo.recoverActiveWorkerSession(1, workerStarter) }
        viewModelScope.launch { sessionRepo.recoverActiveWorkerSession(2, workerStarter) }
        viewModelScope.launch { playerRepo.awardMissingCapes() }
        viewModelScope.launch { playerRepo.migratePetsFromInventory(gameData.pets) }
        viewModelScope.launch { playerRepo.ensureCharacterCreatedAt() }
        viewModelScope.launch { guildRepo.migrateLegacyGuildReputation() }
        viewModelScope.launch { playerRepo.migrateLegacyPrestigePointsIfNeeded() }
        // A newly started seasonal event re-shows the banner once, so players who hid
        // it still learn the event exists (issue #1650 follow-up).
        viewModelScope.launch {
            val event = seasonalEventRepo.activeEvent() ?: return@launch
            if (playerRepo.getFlags().seasonalBannerReshownEventId != event.id) {
                playerRepo.updateFlagsAtomically {
                    it.copy(showSeasonalEvents = true, seasonalBannerReshownEventId = event.id)
                }
            }
        }
        // AlarmManager delivery can be deferred by Doze for hours (issue 517: overnight
        // sessions frozen until their late alarms fire). While the app is open this
        // ticker completes overdue sessions and workers within a second.
        viewModelScope.launch {
            while (true) {
                try { sessionRepo.completeOverdueSessions(queuedSessionStarter, workerStarter) } catch (_: Exception) {}
                delay(1_000L)
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
            val mainlandLevels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            val mainlandXpForState: Map<String, Long> = json.decodeFromString(player.skillXp)
            // On isle: swap the whole tab's skill pool so the active-session banner projects
            // against elder XP (not mainland level 99). Off isle: pass through mainland.
            val levels: Map<String, Int> = if (flags.onElderIsle)
                mainlandLevels.mapValues { flags.elderSkillLevels[it.key] ?: 1 } else mainlandLevels
            val homeSkillXp: Map<String, Long> = if (flags.onElderIsle)
                mainlandXpForState.mapValues { flags.elderSkillXp[it.key] ?: 0L } else mainlandXpForState
            val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
            val agilityLevel    = levels[Skills.AGILITY] ?: 1
            val floorReductionMin = boostRepo.sessionFloorReductionMin(flags)
            val chronosMult     = townRepo.playerSessionDurationMultiplier(flags)
            val sessionMs       = SkillSimulator.sessionDurationMs(agilityLevel, floorReductionMin, chronosMult)
            val perItemMs    = sessionMs / 60
            // A repeat chain only ever has its current run in the DB; the remaining runs
            // live in the repeat flags, so price them in or the queue ETA covers just the
            // current run (issue #1750). Priced like the queued-entry sum below.
            val activeChainRemainMs = session?.takeIf { !it.completed }?.let { s ->
                when {
                    s.skillName == "combat" && flags.activeDungeonRepeatSnapshot != null ->
                        (flags.activeDungeonRepeatTotal - flags.activeDungeonRepeatIndex).coerceAtLeast(0) * sessionMs
                    s.skillName == "boss" && flags.activeBossRepeatSnapshot != null ->
                        (flags.activeBossRepeatTotal - flags.activeBossRepeatIndex).coerceAtLeast(0) *
                            (gameData.bosses[s.activityKey]?.durationMinutes?.toLong() ?: 60L) * perItemMs
                    else -> 0L
                }
            } ?: 0L
            val queueStart   = (session?.takeIf { !it.completed }?.endsAt ?: System.currentTimeMillis()) + activeChainRemainMs
            // Recomputed live from current agility/gear rather than the frozen value stored at
            // queue time, so the countdown reacts to level-ups and tool swaps (issues #938, #940).
            // Boss fights alone use a fixed wall-clock duration unrelated to agility or gear.
            val queueEndsAt  = if (flags.sessionQueue.isEmpty()) 0L
                               else queueStart + flags.sessionQueue.sumOf {
                                   when {
                                       // repeatCount defaults to 1 for every non-boss/combat entry,
                                       // so this only changes anything for repeated boss fights and
                                       // dungeon runs (issue #1194).
                                       it.skillName == "boss" -> it.estimatedDurationMs * it.repeatCount
                                       it.qty > 0 -> {
                                           val eff = gameData.craftDurationEfficiency(it.skillName, it.activityKey, equipped, skillLevels = levels, heirloomXp = flags.heirloomXp)
                                           it.qty.toLong() * (perItemMs / eff).toLong()
                                       }
                                       else -> sessionMs * it.repeatCount
                                   }
                               }
            val innXpMult = townRepo.workerXpMultiplier(flags)
            val capeMult = blessingPrayerCapeMult(player, flags, gameData)
            val sessionXpGain: (SkillSession?) -> Long = { s ->
                if (s == null || s.skillName in listOf("combat", "boss", "expedition", "farming", "tower", "carnival")) 0L
                else try {
                    val base = json.decodeFromString<List<SessionFrame>>(s.frames).sumOf { it.xpGain.toLong() }
                    // Same multiplier chain collection applies (applySessionResults), so the
                    // card matches the eventual payout and reacts to boosts live (issue #1748).
                    val boostMult = boostRepo.xpMultiplier(s.skillName, flags, capeMult)
                    if (s.isWorkerSession) (base * s.efficiencyMultiplier * innXpMult * boostMult).toLong()
                    else (base * boostMult).toLong()
                } catch (_: Exception) { 0L }
            }
            val activeSessionXpGain   = sessionXpGain(session)
            val workerSessionXpGain   = sessionXpGain(workerSession)
            val workerSession2XpGain  = sessionXpGain(workerSession2)
            // Batch totals for the active crafting-type session. Direct-start crafts store the
            // whole batch in one frame, queue-started ones split it into up to 60 bucket frames,
            // so totals are summed across frames rather than using singleBatchItems(). Restricted
            // to batch skills (and pet drops filtered out) so pre-rolled gathering/combat loot and
            // pet finds are never revealed mid-session.
            val craftingBatchSkills = setOf(Skills.SMITHING, Skills.COOKING, Skills.FLETCHING, Skills.CRAFTING,
                Skills.HERBLORE, Skills.FIREMAKING, Skills.RUNECRAFTING, Skills.CONSTRUCTION)
            val activeSessionBatchTotals: Map<String, Int> = when {
                session == null -> emptyMap()
                session.skillName == Skills.PRAYER -> try {
                    val bones = json.decodeFromString<List<SessionFrame>>(session.frames).sumOf { it.kills }
                    if (bones > 0) mapOf(session.activityKey to bones) else emptyMap()
                } catch (_: Exception) { emptyMap() }
                session.skillName in craftingBatchSkills -> try {
                    val totals = mutableMapOf<String, Int>()
                    json.decodeFromString<List<SessionFrame>>(session.frames).forEach { f ->
                        f.items.forEach { (k, v) -> if (k !in gameData.pets) totals[k] = (totals[k] ?: 0) + v }
                    }
                    totals
                } catch (_: Exception) { emptyMap() }
                else -> emptyMap()
            }
            val progressMap      = guildProgress.associateBy { it.questId }
            val completedQuestIds = guildProgress.filter { it.completed }.map { it.questId }.toSet()
            val guildClaimableCount = GuildRepository.ALL_GUILDS.sumOf { guild ->
                val level = guildRepo.guildLevel(guild, flags.guildDailyTierCounts, completedQuestIds)
                val claimableQuests = gameData.guildQuests.values
                    .filter { it.guild == guild && level >= it.guildLevelRequired }
                    .count { quest ->
                        val row = progressMap[quest.id]
                        row != null && !row.completed && row.progress >= guildRepo.effectiveQuestAmountFromFlags(quest, flags)
                    }
                val dailies = guildRepo.getGuildDailiesWithProgress(guild, flags)
                claimableQuests + dailies.count { it.progress >= it.template.amount && !it.claimed }
            }
            val activeSeasonalEvent = seasonalEventRepo.activeEvent()?.let { event ->
                SeasonalEventSummary(
                    id          = event.id,
                    displayName = event.displayName,
                    tokens      = (flags.seasonalTokensByEvent[event.id] ?: 0).coerceAtMost(event.tokenGoal),
                    goal        = event.tokenGoal,
                    endMs       = event.endMs,
                    bannerIcon  = event.bannerIcon,
                )
            }
            extra.copy(
                isLoading           = false,
                coins               = player.coins,
                skillLevels         = levels,
                skillXp             = homeSkillXp,
                activeSession       = session,
                pendingCollectCount = completedCount,
                characterSetupDone  = flags.characterSetupDone,
                characterName       = flags.characterName,
                characterRace       = flags.characterRace,
                characterSkinTone   = flags.characterSkinTone,
                characterHairStyle  = flags.characterHairStyle,
                characterHairColor  = flags.characterHairColor,
                characterEyeStyle   = flags.characterEyeStyle,
                characterBeardStyle = flags.characterBeardStyle,
                characterBeardColor = flags.characterBeardColor,
                showCharacterViewer = flags.showCharacterViewer,
                showStatsBar        = flags.showStatsBar,
                showSessionEndTime  = flags.showSessionEndTime,
                equippedTitle       = flags.equippedTitle,
                titleName           = titleRepo.displayName(context, flags.equippedTitle, flags),
                // Queue entries baked the boost multiplier valid at enqueue time into their
                // estimate; swap it for the live full per-skill chain (per-skill 2x boosts and
                // prestige included) so previews match the eventual payout (issues #1748, #1790).
                // Legacy entries (mult 0) are shown as stored.
                sessionQueue        = flags.sessionQueue.map { a ->
                    if (a.xpBoostMultAtQueue > 0.0 && a.estimatedXpGain > 0L)
                        a.copy(estimatedXpGain = (a.estimatedXpGain * (boostRepo.xpMultiplier(a.skillName, flags, capeMult) / a.xpBoostMultAtQueue)).toLong())
                    else a
                },
                maxQueueSize        = playerRepo.maxQueueSize(flags),
                showWhatsNew        = flags.lastSeenVersionCode < BuildConfig.VERSION_CODE,
                queueEndsAt         = queueEndsAt,
                towerCurrentFloor   = flags.towerCurrentFloor,
                workerSession        = workerSession,
                workerSession2       = workerSession2,
                workerPendingCollect1 = workerData.completedCount1 > 0,
                workerPendingCollect2 = workerData.completedCount2 > 0,
                hiredWorker         = flags.hiredWorker,
                hiredWorker2        = flags.hiredWorker2,
                workerQueue         = flags.hiredWorker?.sessionQueue ?: emptyList(),
                workerQueue2        = flags.hiredWorker2?.sessionQueue ?: emptyList(),
                activeBlessingKey          = flags.activeBlessingKey,
                allBlessings               = gameData.blessings,
                prayerCapeMult             = capeMult,
                activeBlessingRemainingMs  = (flags.activeBlessingExpiresAt - System.currentTimeMillis()).coerceAtLeast(0L),
                monumentTouchAvailable     = flags.monumentTier >= 2 && !monumentRepo.touchedToday(flags),
                showMonumentTouchIndicator = flags.showMonumentTouchIndicator,
                xpBoostRemainingMs         = if (flags.ironman) 0L else (flags.xpBoostExpiresAt - System.currentTimeMillis()).coerceAtLeast(0L),
                prestigeBoostsRemainingMs  = flags.prestigeXpBoosts
                    .mapValues { (it.value - System.currentTimeMillis()).coerceAtLeast(0L) }
                    .filterValues { it > 0L },
                ironman                    = flags.ironman,
                recentSessions             = flags.recentSessions,
                showRecentActivityLog      = flags.showRecentActivityLog,
                showJournalButton          = flags.showJournalButton,
                showCharacterSwitch        = saveSlotRepo.hasMultipleCharacters(),
                showSeasonalEvents         = flags.showSeasonalEvents,
                collapsibleTownGrid        = flags.collapsibleTownGrid,
                elderIsleUnlocked          = flags.elderIsleUnlocked,
                onElderIsle                = flags.onElderIsle,
                dockBuilt                  = (flags.townBuildingTiers["dock"] ?: 0) >= 1,
                showIsleWelcome            = flags.onElderIsle && !flags.elderIsleWelcomed,
                inventory                  = try { json.decodeFromString<Map<String, Int>>(player.inventory) } catch (_: Exception) { emptyMap() },
                dungeonRuns                = flags.dungeonRuns,
                enemyKills                 = flags.enemyKills,
                townGridExpanded           = flags.townGridExpanded,
                playerNotes                = flags.playerNotes,
                guildClaimableCount        = guildClaimableCount,
                activeSeasonalEvent        = activeSeasonalEvent,
                activeSessionXpGain        = activeSessionXpGain,
                activeSessionAssignedItems = activeSessionBatchTotals,
                activeBossRepeatIndex      = flags.activeBossRepeatIndex,
                activeBossRepeatTotal      = flags.activeBossRepeatTotal,
                activeDungeonRepeatIndex   = flags.activeDungeonRepeatIndex,
                activeDungeonRepeatTotal   = flags.activeDungeonRepeatTotal,
                workerSessionXpGain        = workerSessionXpGain,
                workerSession2XpGain       = workerSession2XpGain,
                workerSessionAssignedItems  = workerSession?.singleBatchItems(json) ?: emptyMap(),
                workerSession2AssignedItems = workerSession2?.singleBatchItems(json) ?: emptyMap(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    // ------------------------------------------------------------------
    // Session actions
    // ------------------------------------------------------------------

    /** Read-only inputs shared by the per-session collect helpers. */
    private class CollectContext(
        val flags: PlayerFlags,
        val inventory: Map<String, Int>,
        val equippedCape: EquipmentData?,
        val capeScalingBySkill: Map<String, Int>,
        val blessingCoinMult: Float,
        val petIds: Set<String>,
        val player: Player,
    )

    /** Mutable totals accumulated across one collect batch (for the summary popup). */
    private class CollectAcc {
        val combinedXpBySkill       = mutableMapOf<String, Long>()
        val combinedItems           = mutableMapOf<String, Int>()
        val combinedKills           = mutableMapOf<String, Int>()
        val combinedFood            = mutableMapOf<String, Int>()
        val combinedArrows          = mutableMapOf<String, Int>()
        val combinedArrowsReclaimed = mutableMapOf<String, Int>()
        val combinedRunes           = mutableMapOf<String, Int>()
        val combinedRunesReclaimed  = mutableMapOf<String, Int>()
        val dailyKills              = mutableMapOf<String, Int>()
        val combinedBones           = mutableMapOf<String, Int>() // boneName -> count
        var combinedCoins           = 0L
        var anyDied                 = false
        var petFoundName: String?   = null
        var bossWon: Boolean?       = null  // set when a session is a boss fight
        var bossCoinsReduced        = false
        val voidedSessionIds        = mutableSetOf<String>()
        val awardedCapes            = mutableListOf<String>()
        var expeditionNoteLines: List<String> = emptyList()
        var expeditionUnlockMessage: String?  = null
    }

    fun collectSession() {
        if (_extra.value.isCollecting) return
        if (saveSlotRepo.switchInProgress) {
            _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.collect_blocked_switching)) }
            return
        }
        _extra.update { it.copy(isCollecting = true) }
        viewModelScope.launch(Dispatchers.Default) {
          try {
            // If the latest session timed out but its alarm hasn't fired yet, mark it completed now.
            val latest = sessionRepo.getActiveSession()
            if (latest != null && !latest.completed && System.currentTimeMillis() >= latest.endsAt && sessionRepo.hasTrustedClock(latest)) {
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
            // Ironman characters play at pure base rates: every XP/yield/coin multiplier is inert
            // (all boostRepo prestige lookups return identity values for them).
            val capeScalingBySkill = boostRepo.capeScalingBySkill(flags)
            val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
            val inventory: Map<String, Int>    = json.decodeFromString(player.inventory)
            val equippedCape = equipped[EquipSlot.CAPE]?.let { gameData.equipment[it] }
            val boostFactorFor   = { skill: String -> boostRepo.xpBoostFactor(skill, flags) }
            val blessingCapeMult = blessingPrayerCapeMult(player, flags, gameData)
            val blessingXpMult   = if (flags.ironman) 1.0f else ChurchRepository.xpMultiplier(flags, blessingCapeMult, gameData.blessings)
            val blessingCoinMult = if (flags.ironman) 1.0f else ChurchRepository.coinMultiplier(flags, blessingCapeMult, gameData.blessings) *
                PlayerRepository.gooseCoinMultiplier(json.decodeFromString<List<OwnedPet>>(player.pets)).toFloat()

            val ctx = CollectContext(flags, inventory, equippedCape, capeScalingBySkill, blessingCoinMult, petIds, player)
            val acc = CollectAcc()

            val currentLevelsForVoidCheck = playerRepo.getSkillLevels()
            for (session in sessions) {
                val frames: List<SessionFrame> = json.decodeFromString(session.frames)
                // Level dropped mid-session (prestige): only the XP is forfeited — loot, coins,
                // kills, and quest progress still pay out. Zeroing happens at each branch's XP
                // application point because combat style detection needs the raw per-skill XP.
                val grantXp = isSkillSessionStillEligible(session, currentLevelsForVoidCheck, gameData)
                if (!grantXp) acc.voidedSessionIds += session.sessionId
                when (session.skillName) {
                    "tower"  -> collectTowerSession(session, frames, grantXp, ctx, acc)
                    "boss"   -> collectBossSession(session, frames, grantXp, ctx, acc)
                    "combat" -> collectDungeonSession(session, frames, grantXp, ctx, acc)
                    "expedition" -> {
                        val result = collectExpeditionSession(
                            session = session,
                            frames = if (grantXp) frames else frames.map { it.copy(xpGain = 0, xpBySkill = emptyMap()) },
                            petIds = ctx.petIds,
                            flags = ctx.flags,
                            awardedCapes = acc.awardedCapes,
                            combinedXpBySkill = acc.combinedXpBySkill,
                            combinedItems = acc.combinedItems
                        )
                        if (result.petFoundName != null) acc.petFoundName = result.petFoundName
                        acc.expeditionNoteLines = result.noteLines
                        acc.expeditionUnlockMessage = result.unlockMessage
                    }
                    Skills.MERCANTILE -> collectMercantileSession(session, frames, grantXp, ctx, acc)
                    else -> collectGenericSkillSession(session, frames, grantXp, ctx, acc)
                }
            }

            for (session in sessions) sessionRepo.deleteSession(session.sessionId)
            queuedSessionStarter.startNextQueued()
            reconcileTowerQueue()
            if (acc.dailyKills.isNotEmpty()) playerRepo.recordDailyKills(acc.dailyKills)

            val rareItemsDisplayNames = mutableSetOf<String>()
            for (session in sessions) {
                if (session.skillName == "boss") {
                    val boss = gameData.bosses[session.activityKey]
                    val bossRareKeys = boss?.rareDrops?.map { it.item }?.toSet() ?: emptySet()
                    val bossPetKey = boss?.pet?.id
                    val combinedBossRareKeys = bossRareKeys + listOfNotNull(bossPetKey)
                    combinedBossRareKeys.forEach { key ->
                        rareItemsDisplayNames.add(GameStrings.itemName(context,key))
                    }
                }
                if (session.skillName == "combat") {
                    val dungeon = gameData.dungeons[session.activityKey]
                    dungeon?.rareDrops?.forEach {
                        rareItemsDisplayNames.add(GameStrings.itemName(context,it.item))
                    }
                    dungeon?.enemySpawns?.forEach { spawn ->
                        gameData.enemies[spawn.enemy]?.dropTable?.forEach { entry ->
                            if (entry.chance <= 0.005) {
                                rareItemsDisplayNames.add(GameStrings.itemName(context,entry.item))
                            }
                        }
                    }
                }
            }

            // ── Recent sessions log ───────────────────────────────────────
            val newEntries = sessions.map { s ->
                val activityDisplay = when (s.skillName) {
                    "boss"       -> GameStrings.bossName(context, s.activityKey)
                    "combat"     -> GameStrings.dungeonName(context, s.activityKey)
                    "expedition" -> gameData.skillingDungeons[s.activityKey]?.displayName
                    else         -> null
                } ?: s.activityKey.replace("_", " ").split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.titlecase() } }
                RecentSession(
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
                n > 1 -> context.withAppLocale().getString(R.string.home_sessions_complete_title, n)
                last.sessionId in acc.voidedSessionIds -> context.withAppLocale().getString(R.string.home_session_voided)
                last.skillName == "boss" -> {
                    val bossName = GameStrings.bossName(context, last.activityKey)
                    if (acc.bossWon == true) context.withAppLocale().getString(R.string.home_boss_defeated_title, bossName)
                    else context.withAppLocale().getString(R.string.home_boss_defeated_by_title, bossName)
                }
                last.skillName == "combat" -> {
                    val dungeonName = GameStrings.dungeonName(context, last.activityKey)
                    if (acc.anyDied) context.withAppLocale().getString(R.string.home_dungeon_died_title, dungeonName)
                    else context.withAppLocale().getString(R.string.home_dungeon_complete_title, dungeonName)
                }
                last.skillName == "expedition" -> {
                    val expFallback = gameData.skillingDungeons[last.activityKey]?.displayName ?: last.activityKey
                    val expName = GameStrings.skillingDungeonName(context, last.activityKey, expFallback)
                    context.withAppLocale().getString(R.string.home_expedition_complete_title, expName)
                }
                else -> context.withAppLocale().getString(
                    R.string.home_skill_session_complete_title,
                    GameStrings.skillName(context, last.skillName),
                )
            }

            // For single-skill non-combat sessions use the compact totalXpLabel
            val useTotalLabel = n == 1 && acc.combinedXpBySkill.size == 1 && acc.combinedKills.isEmpty()
            val singleXp      = acc.combinedXpBySkill.values.firstOrNull() ?: 0L
            val totalRawXp    = acc.combinedXpBySkill.values.sum()
            val boneBuriedLines = acc.combinedBones.entries.map { (name, count) ->
                Pair(context.withAppLocale().getString(R.string.home_bones_buried_row, name), "×$count")
            }

            val displayedCoins    = (acc.combinedCoins.toDouble() * blessingCoinMult).toLong()
            val coinBlessingBonus = displayedCoins - acc.combinedCoins
            val sortedXpEntries   = acc.combinedXpBySkill.entries.sortedByDescending { it.value }
            val singleXpFactor    = acc.combinedXpBySkill.keys.firstOrNull()?.let(boostFactorFor) ?: 1L
            val xpLineBonuses     = sortedXpEntries.map { (skill, xp) ->
                val base = xp * boostFactorFor(skill)
                ((base.toDouble() * blessingXpMult).toLong() - base).coerceAtLeast(0L)
            }
            val singleXpBonus = run {
                val base = singleXp * singleXpFactor
                ((base.toDouble() * blessingXpMult).toLong() - base).coerceAtLeast(0L)
            }

            val summary = SessionSummary(
                title          = title,
                died           = acc.anyDied,
                xpLines        = if (useTotalLabel) emptyList()
                                 else sortedXpEntries
                                     .map { (skill, xp) -> Pair(GameStrings.skillName(context, skill), "+${((xp * boostFactorFor(skill)).toDouble() * blessingXpMult).toLong().formatXp()} XP") },
                xpLineValues   = if (useTotalLabel) emptyList()
                                 else sortedXpEntries
                                     .map { (skill, xp) -> ((xp * boostFactorFor(skill)).toDouble() * blessingXpMult).toLong() },
                totalXpLabel      = if (useTotalLabel) "+${((singleXp * singleXpFactor).toDouble() * blessingXpMult).toLong().formatXp()} XP" else "",
                totalXpLabelBonus = if (useTotalLabel) singleXpBonus else 0L,
                totalXpValue      = if (useTotalLabel) ((singleXp * singleXpFactor).toDouble() * blessingXpMult).toLong() else 0L,
                itemLines      = acc.combinedItems.entries.sortedByDescending { it.value }
                                     .map { (key, qty) -> Pair(GameStrings.itemName(context,key), "×$qty") },
                coinsGained    = displayedCoins,
                killLines      = acc.combinedKills.entries.sortedByDescending { it.value }
                                     .map { (enemy, kills) -> Pair(enemy, "×$kills") },
                foodConsumedLines = acc.combinedFood.entries.sortedByDescending { it.value }
                                     .map { (food, qty) -> Pair(GameStrings.itemName(context,food), "×$qty") },
                arrowsConsumedLines  = acc.combinedArrows.entries.sortedByDescending { it.value }
                                         .map { (key, qty) -> Pair(GameStrings.itemName(context,key), "×$qty") },
                arrowsReclaimedLines = acc.combinedArrowsReclaimed.entries.sortedByDescending { it.value }
                                         .map { (key, qty) -> Pair(GameStrings.itemName(context,key), "+$qty") },
                runesConsumedLines   = acc.combinedRunes.entries.sortedByDescending { it.value }
                                         .map { (key, qty) -> Pair(GameStrings.itemName(context,key), "×$qty") },
                runesReclaimedLines  = acc.combinedRunesReclaimed.entries.sortedByDescending { it.value }
                                         .map { (key, qty) -> Pair(GameStrings.itemName(context,key), "+$qty") },
                boneBuriedLines  = boneBuriedLines,
                boostWasActive   = acc.combinedXpBySkill.keys.any { boostFactorFor(it) > 1L },
                xpLineBoostFactors = if (useTotalLabel) emptyList()
                                     else sortedXpEntries.map { (skill, _) -> boostFactorFor(skill) },
                totalXpBoostFactor = if (useTotalLabel) singleXpFactor else 1L,
                xpLineBonuses    = xpLineBonuses,
                coinBlessingBonus = coinBlessingBonus,
                noteLines        = acc.expeditionNoteLines +
                                     (if (acc.bossCoinsReduced) listOf(context.withAppLocale().getString(R.string.session_note_boss_coin_cap)) else emptyList()),
                unlockMessage    = acc.expeditionUnlockMessage,
                rareItems        = rareItemsDisplayNames,
            )

            val capeMessage = if (acc.awardedCapes.isNotEmpty()) {
                val names = acc.awardedCapes.joinToString(", ") { GameStrings.itemName(context,it) }
                context.withAppLocale().getString(R.string.home_congratulations_received, names)
            } else null
            _extra.update { it.copy(
                sessionSummary  = summary,
                snackbarMessage = capeMessage,
                petFoundName    = acc.petFoundName,
            ) }
          } finally {
            _extra.update { it.copy(isCollecting = false) }
          }
        }
    }

    private suspend fun collectTowerSession(session: SkillSession, frames: List<SessionFrame>, grantXp: Boolean, ctx: CollectContext, acc: CollectAcc) {
        val playerDied = frames.any { it.died }
        if (playerDied) acc.anyDied = true
        val towerXpPerSkill = mutableMapOf<String, Long>()
        val towerAllItems   = mutableMapOf<String, Int>()
        val towerFood       = mutableMapOf<String, Int>()
        val towerKills      = mutableMapOf<String, Int>()
        val towerArrows     = mutableMapOf<String, Int>()
        val towerRunes      = mutableMapOf<String, Int>()
        for (frame in frames) {
            for ((skill, xp) in frame.xpBySkill)    towerXpPerSkill[skill] = (towerXpPerSkill[skill] ?: 0L) + xp
            for ((item,  qty) in frame.items)        towerAllItems[item]     = (towerAllItems[item] ?: 0) + qty
            for ((food,  qty) in frame.foodConsumed) towerFood[food]         = (towerFood[food] ?: 0) + qty
            for ((e,     k)   in frame.killsByEnemy) towerKills[e]           = (towerKills[e] ?: 0) + k
            for ((arrow, qty) in frame.arrowsConsumed) towerArrows[arrow]    = (towerArrows[arrow] ?: 0) + qty
            for ((rune,  qty) in frame.runesConsumed) towerRunes[rune]       = (towerRunes[rune] ?: 0) + qty
        }
        if (playerDied) {
            val keep = boostRepo.deathKeepFraction(ctx.flags)
            towerXpPerSkill.replaceAll { _, xp -> maxOf(1L, (xp * keep).toLong()) }
            towerAllItems.replaceAll { _, qty -> maxOf(0, (qty * keep).toInt()) }
            towerAllItems.entries.removeIf { it.value == 0 }
        }
        
        if (!playerDied && towerKills.isNotEmpty()) {
            val style = detectCombatStyle(towerXpPerSkill)
            questRepo.recordCombat(
                dungeonKey        = session.activityKey,
                killsByEnemy      = towerKills,
                loot              = towerAllItems,
                combatStyle       = style,
                foodConsumedTotal = towerFood.values.sum(),
            )
            for ((e, k) in towerKills) acc.dailyKills[e] = (acc.dailyKills[e] ?: 0) + k
            guildRepo.recordGuildCombat(towerKills, style)
            var towerSlayerXp = 0L
            for ((enemy, k) in towerKills) towerSlayerXp += slayerRepo.recordKills(enemy, k)
            if (towerSlayerXp > 0L) towerXpPerSkill[Skills.SLAYER] = (towerXpPerSkill[Skills.SLAYER] ?: 0L) + towerSlayerXp
        }

        val towerCoinsRaw    = towerAllItems.remove("coins")?.toLong() ?: 0L
        val towerFlags       = playerRepo.getFlags()
        val towerXpMult      = if (towerFlags.ironman) 1.0 else 1.0 + towerFlags.towerXpBonusPct / 100.0
        val towerCoinMult    = if (towerFlags.ironman) 1.0 else 1.0 + towerFlags.towerCoinBonusPct / 100.0
        val towerXpForRepo   = if (grantXp) towerXpPerSkill.mapValues { (_, xp) -> (xp * towerXpMult).toLong() } else emptyMap()
        val towerCoinsGained = (towerCoinsRaw * towerCoinMult).toLong()

        playerRepo.applyMultiSkillResults(towerXpForRepo, towerAllItems, towerCoinsGained, sessionId = session.sessionId)

        val skillLvls = playerRepo.getSkillLevels()
        val arrowsReclaimed = towerArrows.mapValues { (_, qty) -> (qty * (reclaimChance(skillLvls[Skills.RANGED] ?: 1) + boostRepo.arrowReclaimBonus(ctx.flags)).coerceAtMost(0.95)).toInt() }.filterValues { it > 0 }
        val runesReclaimed  = towerRunes.mapValues { (_, qty) -> (qty * (reclaimChance(skillLvls[Skills.MAGIC] ?: 1) + boostRepo.runeReclaimBonus(ctx.flags)).coerceAtMost(0.95)).toInt() }.filterValues { it > 0 }
        val finalTowerArrows = towerArrows.mapValues { (k, v) -> v - (arrowsReclaimed[k] ?: 0) }.filterValues { it > 0 }
        val finalTowerRunes  = towerRunes.mapValues { (k, v) -> v - (runesReclaimed[k] ?: 0) }.filterValues { it > 0 }

        val totalConsumables = towerFood + finalTowerArrows + finalTowerRunes
        if (totalConsumables.isNotEmpty()) playerRepo.consumeItems(totalConsumables)
        val floor = session.activityKey.removePrefix("tower_floor_").toIntOrNull() ?: 1
        val updatedTowerFlags = playerRepo.getFlags()
        if (playerDied) {
            // Death drops you back to your last checkpoint (every 25 floors of
            // your best-ever progress), matching TowerViewModel.collectFloor() --
            // this path had drifted to a flat reset to 0 (issue #1183).
            val checkpointFloor = (updatedTowerFlags.towerBestFloor / TowerViewModel.TOWER_CHECKPOINT_INTERVAL) * TowerViewModel.TOWER_CHECKPOINT_INTERVAL
            playerRepo.updateFlags(updatedTowerFlags.copy(towerCurrentFloor = checkpointFloor))
        } else {
            playerRepo.updateFlags(updatedTowerFlags.copy(
                towerCurrentFloor = floor,
                towerBestFloor    = maxOf(updatedTowerFlags.towerBestFloor, floor),
            ))
        }
        for ((skill, xp) in towerXpForRepo) acc.combinedXpBySkill[skill] = (acc.combinedXpBySkill[skill] ?: 0L) + prestigeAdjustedXp(skill, xp, ctx.flags)
        for ((item, qty) in towerAllItems)  acc.combinedItems[item] = (acc.combinedItems[item] ?: 0) + qty
        acc.combinedCoins += towerCoinsGained
    }

    private suspend fun collectBossSession(session: SkillSession, frames: List<SessionFrame>, grantXp: Boolean, ctx: CollectContext, acc: CollectAcc) {
        // Elder Isle boss route: XP into elder pool, coins into shared, no mainland hooks.
        if (session.isElderSession || sessionIsIsleByActivity(session)) {
            val elderXp    = mutableMapOf<String, Long>()
            val elderItems = mutableMapOf<String, Int>()
            val elderFood  = mutableMapOf<String, Int>()
            val elderArrows = mutableMapOf<String, Int>()
            val elderRunes = mutableMapOf<String, Int>()
            var won = false
            for (frame in frames) {
                for ((skill, xp) in frame.xpBySkill) elderXp[skill] = (elderXp[skill] ?: 0L) + xp
                for ((item, qty) in frame.items) elderItems[item] = (elderItems[item] ?: 0) + qty
                for ((f, q) in frame.foodConsumed)   elderFood[f]   = (elderFood[f] ?: 0) + q
                for ((a, q) in frame.arrowsConsumed) elderArrows[a] = (elderArrows[a] ?: 0) + q
                for ((r, q) in frame.runesConsumed)  elderRunes[r]  = (elderRunes[r] ?: 0) + q
                if (frame.kills > 0 || frame.killsByEnemy.isNotEmpty()) won = true
            }
            val elderCoins = elderItems.remove("coins")?.toLong() ?: 0L
            val elderPets = elderItems.filterKeys { it in ctx.petIds }
            val elderLoot = elderItems.filterKeys { it !in ctx.petIds }
            if (!grantXp) elderXp.clear()
            playerRepo.applyElderMultiSkillResults(elderXp, elderLoot, elderCoins)
            for ((id, _) in elderPets) {
                val pd = gameData.pets[id] ?: continue
                if (playerRepo.addPetIfNew(id, pd.boostPercent))
                    acc.petFoundName = GameStrings.petName(context, pd.id)
            }
            acc.bossWon = won
            val bossSkillLvls = playerRepo.getSkillLevels()
            val bossArrowsRec = elderArrows.mapValues { (_, qty) -> (qty * (reclaimChance(bossSkillLvls[Skills.RANGED] ?: 1) + boostRepo.arrowReclaimBonus(ctx.flags)).coerceAtMost(0.95)).toInt() }.filterValues { it > 0 }
            val bossRunesRec  = elderRunes.mapValues  { (_, qty) -> (qty * (reclaimChance(bossSkillLvls[Skills.MAGIC] ?: 1) + boostRepo.runeReclaimBonus(ctx.flags)).coerceAtMost(0.95)).toInt() }.filterValues { it > 0 }
            if (elderFood.isNotEmpty())     playerRepo.consumeItems(elderFood)
            if (elderArrows.isNotEmpty())   playerRepo.consumeItems(elderArrows)
            if (bossArrowsRec.isNotEmpty()) playerRepo.addItems(bossArrowsRec)
            if (bossRunesRec.isNotEmpty())  playerRepo.addItems(bossRunesRec)
            if (won) {
                acc.dailyKills[session.activityKey] = (acc.dailyKills[session.activityKey] ?: 0) + 1
                acc.combinedKills[session.activityKey] = (acc.combinedKills[session.activityKey] ?: 0) + 1
            }
            for ((skill, xp) in elderXp) acc.combinedXpBySkill[skill] = (acc.combinedXpBySkill[skill] ?: 0L) + xp
            for ((item, qty) in elderLoot) acc.combinedItems[item] = (acc.combinedItems[item] ?: 0) + qty
            for ((f, q) in elderFood)            acc.combinedFood[f]           = (acc.combinedFood[f] ?: 0) + q
            for ((a, q) in elderArrows)          acc.combinedArrows[a]         = (acc.combinedArrows[a] ?: 0) + q
            for ((a, q) in bossArrowsRec)        acc.combinedArrowsReclaimed[a] = (acc.combinedArrowsReclaimed[a] ?: 0) + q
            for ((r, q) in elderRunes)           acc.combinedRunes[r]          = (acc.combinedRunes[r] ?: 0) + q
            for ((r, q) in bossRunesRec)         acc.combinedRunesReclaimed[r]  = (acc.combinedRunesReclaimed[r] ?: 0) + q
            acc.combinedCoins += elderCoins
            if (won && session.activityKey == "last_elder") {
                val f = playerRepo.getFlags()
                if ("ancient_signet" !in f.seenItemKeys) {
                    playerRepo.updateFlags(f.copy(seenItemKeys = f.seenItemKeys + "ancient_signet"))
                }
            }
            return
        }
        val frame = frames.lastOrNull() ?: return
        val won = frame.kills > 0 || frame.killsByEnemy.isNotEmpty()
        acc.bossWon = won
        val its   = frame.items.toMutableMap()
        val coins = if (won) {
            val base = its.remove("coins")?.toLong() ?: 0L
            val mult = playerRepo.rollBossCoinSoftCap(session.activityKey)
            if (mult < 1.0 && base > 0L) acc.bossCoinsReduced = true
            (base * mult).toLong()
        } else 0L
        val pets  = its.filterKeys { it in ctx.petIds }
        val loot  = if (won) its.filterKeys { it !in ctx.petIds } else emptyMap()
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
        for (skill in bossXpBySkill.keys) {
            val mult = resolveCapeMultiplier(skill, ctx.equippedCape, ctx.inventory.keys, ctx.flags.townBuildingTiers, ctx.capeScalingBySkill, gameData.equipment, ctx.flags.ironman)
            if (mult > 1f) {
                bossXpBySkill[skill] = (bossXpBySkill[skill]!! * mult.toDouble()).toLong()
            }
        }
        if (!grantXp) bossXpBySkill.clear()
        val bossSkillLvls    = playerRepo.getSkillLevels()
        val bossArrowsRec    = allArrowsConsumed.mapValues { (_, qty) -> (qty * (reclaimChance(bossSkillLvls[Skills.RANGED] ?: 1) + boostRepo.arrowReclaimBonus(ctx.flags)).coerceAtMost(0.95)).toInt() }.filterValues { it > 0 }
        val bossRunesRec     = allRunesConsumed.mapValues  { (_, qty) -> (qty * (reclaimChance(bossSkillLvls[Skills.MAGIC] ?: 1) + boostRepo.runeReclaimBonus(ctx.flags)).coerceAtMost(0.95)).toInt() }.filterValues { it > 0 }
        val ownedPets: List<OwnedPet> = if (ctx.flags.ironman) emptyList()
            else try { json.decodeFromString(ctx.player.pets) } catch (_: Exception) { emptyList() }
        val perSkillPetBoostPct = bossXpBySkill.keys.associateWith { skill ->
            ownedPets.sumOf { ownedPet ->
                val pd = gameData.pets[ownedPet.id]
                if (pd == null) 0
                else when {
                    pd.boostedSkill == "all" -> pd.boostPercent
                    pd.boostedSkill == skill -> pd.boostPercent
                    pd.boostedSkill == "combat" && skill in Skills.COMBAT -> pd.boostPercent
                    else -> 0
                }
            }
        }.filterValues { it > 0 }
        acc.awardedCapes += playerRepo.applyMultiSkillResults(bossXpBySkill, loot, coins, perSkillPetBoostPct = perSkillPetBoostPct, sessionId = session.sessionId)
        if (allFoodConsumed.isNotEmpty())   playerRepo.consumeItems(allFoodConsumed)
        if (allArrowsConsumed.isNotEmpty()) playerRepo.consumeItems(allArrowsConsumed)
        if (bossArrowsRec.isNotEmpty())     playerRepo.addItems(bossArrowsRec)
        if (bossRunesRec.isNotEmpty())      playerRepo.addItems(bossRunesRec)
        for ((f, q) in allFoodConsumed)    acc.combinedFood[f]             = (acc.combinedFood[f] ?: 0) + q
        for ((a, q) in allArrowsConsumed)  acc.combinedArrows[a]           = (acc.combinedArrows[a] ?: 0) + q
        for ((a, q) in bossArrowsRec)      acc.combinedArrowsReclaimed[a]  = (acc.combinedArrowsReclaimed[a] ?: 0) + q
        for ((r, q) in allRunesConsumed)   acc.combinedRunes[r]            = (acc.combinedRunes[r] ?: 0) + q
        for ((r, q) in bossRunesRec)       acc.combinedRunesReclaimed[r]   = (acc.combinedRunesReclaimed[r] ?: 0) + q
        if (won) {
            for ((id, _) in pets) {
                val pd = gameData.pets[id] ?: continue
                if (playerRepo.addPetIfNew(id, pd.boostPercent))
                    acc.petFoundName = GameStrings.petName(context, pd.id)
            }
            questRepo.recordCombat(
                dungeonKey   = session.activityKey,
                killsByEnemy = mapOf(session.activityKey to 1),
                loot         = loot,
            )
            acc.dailyKills[session.activityKey] = (acc.dailyKills[session.activityKey] ?: 0) + 1
            acc.combinedKills[session.activityKey] = (acc.combinedKills[session.activityKey] ?: 0) + 1
            playerRepo.recordWeeklyProgress("boss", session.activityKey, 1)
            guildRepo.recordGuildCombat(mapOf(session.activityKey to 1), frames.lastOrNull()?.combatStyle?.ifEmpty { "melee" } ?: "melee")
            seasonalEventRepo.recordCombat(mapOf(session.activityKey to 1))
            seasonalEventRepo.recordBossDefeat(session.activityKey)
            for ((item, qty) in loot) acc.combinedItems[item] = (acc.combinedItems[item] ?: 0) + qty
            acc.combinedCoins += coins
            // First Sea Serpent kill completes the Voyage and unlocks Elder Isle travel.
            if (session.activityKey == "sea_serpent") {
                val f = playerRepo.getFlags()
                if (!f.elderIsleUnlocked) {
                    playerRepo.updateFlags(f.copy(seaSerpentDefeated = true, elderIsleUnlocked = true))
                }
            }
        }
        for ((skill, xp) in bossXpBySkill) {
            val petPct = perSkillPetBoostPct[skill] ?: 0
            val withPet = if (petPct > 0) (xp * (1.0 + petPct / 100.0)).toLong() else xp
            acc.combinedXpBySkill[skill] = (acc.combinedXpBySkill[skill] ?: 0L) + prestigeAdjustedXp(skill, withPet, ctx.flags)
        }
    }

    private suspend fun collectDungeonSession(session: SkillSession, frames: List<SessionFrame>, grantXp: Boolean, ctx: CollectContext, acc: CollectAcc) {
        // Elder Isle dungeons route combat XP into the elder pool and bypass every mainland
        // boost/quest hook, matching the bonus-flow rule. Loot lands in shared inventory.
        if (session.isElderSession || sessionIsIsleByActivity(session)) {
            val elderXpPerSkill = mutableMapOf<String, Long>()
            val elderItems      = mutableMapOf<String, Int>()
            val elderKills      = mutableMapOf<String, Int>()
            val elderFood       = mutableMapOf<String, Int>()
            val elderArrows     = mutableMapOf<String, Int>()
            val elderRunes      = mutableMapOf<String, Int>()
            val elderDied       = frames.any { it.died }
            if (elderDied) acc.anyDied = true
            for (frame in frames) {
                for ((skill, xp) in frame.xpBySkill)  elderXpPerSkill[skill] = (elderXpPerSkill[skill] ?: 0L) + xp
                for ((item, qty) in frame.items)       elderItems[item]       = (elderItems[item] ?: 0) + qty
                for ((e, k) in frame.killsByEnemy)     elderKills[e]          = (elderKills[e] ?: 0) + k
                for ((f, q) in frame.foodConsumed)     elderFood[f]           = (elderFood[f] ?: 0) + q
                for ((a, q) in frame.arrowsConsumed)   elderArrows[a]         = (elderArrows[a] ?: 0) + q
                for ((r, q) in frame.runesConsumed)    elderRunes[r]          = (elderRunes[r] ?: 0) + q
            }
            val elderCoins = elderItems.remove("coins")?.toLong() ?: 0L
            val elderPets = elderItems.filterKeys { it in ctx.petIds }
            val elderLoot = elderItems.filterKeys { it !in ctx.petIds }
            if (!grantXp) elderXpPerSkill.clear()
            playerRepo.applyElderMultiSkillResults(elderXpPerSkill, elderLoot, elderCoins)
            for ((id, _) in elderPets) {
                val pd = gameData.pets[id] ?: continue
                if (playerRepo.addPetIfNew(id, pd.boostPercent))
                    acc.petFoundName = GameStrings.petName(context, pd.id)
            }
            val skillLvls = playerRepo.getSkillLevels()
            val elderArrowsRec = elderArrows.mapValues { (_, qty) -> (qty * (reclaimChance(skillLvls[Skills.RANGED] ?: 1) + boostRepo.arrowReclaimBonus(ctx.flags)).coerceAtMost(0.95)).toInt() }.filterValues { it > 0 }
            val elderRunesRec  = elderRunes.mapValues  { (_, qty) -> (qty * (reclaimChance(skillLvls[Skills.MAGIC] ?: 1) + boostRepo.runeReclaimBonus(ctx.flags)).coerceAtMost(0.95)).toInt() }.filterValues { it > 0 }
            if (elderFood.isNotEmpty())       playerRepo.consumeItems(elderFood)
            if (elderArrows.isNotEmpty())     playerRepo.consumeItems(elderArrows)
            if (elderArrowsRec.isNotEmpty())  playerRepo.addItems(elderArrowsRec)
            if (elderRunesRec.isNotEmpty())   playerRepo.addItems(elderRunesRec)
            // Isle dungeon runs feed the story quest counters (dungeonRuns[key]) even though
            // mainland questRepo/guildRepo hooks stay walled off, per bonus-flow rule.
            // Without this the Voyage/Landing/Ascent quests stay at 0 forever.
            if (!elderDied) playerRepo.incrementDungeonRun(session.activityKey)
            if (elderKills.isNotEmpty()) {
                for ((e, k) in elderKills) acc.dailyKills[e] = (acc.dailyKills[e] ?: 0) + k
            }
            val elderRunFlags = playerRepo.getFlags()
            playerRepo.updateFlags(elderRunFlags.copy(
                dungeonLastRunStats = elderRunFlags.dungeonLastRunStats + (session.activityKey to DungeonRunStats(
                    foodConsumed = elderFood.values.sum(),
                    killCount    = elderKills.values.sum(),
                    survived     = !elderDied,
                ))
            ))
            for ((skill, xp) in elderXpPerSkill) acc.combinedXpBySkill[skill] = (acc.combinedXpBySkill[skill] ?: 0L) + xp
            for ((item, qty) in elderLoot)       acc.combinedItems[item]      = (acc.combinedItems[item] ?: 0) + qty
            for ((e, k) in elderKills)           acc.combinedKills[e]         = (acc.combinedKills[e] ?: 0) + k
            for ((f, q) in elderFood)            acc.combinedFood[f]          = (acc.combinedFood[f] ?: 0) + q
            for ((a, q) in elderArrows)          acc.combinedArrows[a]        = (acc.combinedArrows[a] ?: 0) + q
            for ((a, q) in elderArrowsRec)       acc.combinedArrowsReclaimed[a] = (acc.combinedArrowsReclaimed[a] ?: 0) + q
            for ((r, q) in elderRunes)           acc.combinedRunes[r]         = (acc.combinedRunes[r] ?: 0) + q
            for ((r, q) in elderRunesRec)        acc.combinedRunesReclaimed[r] = (acc.combinedRunesReclaimed[r] ?: 0) + q
            acc.combinedCoins += elderCoins
            return
        }
        val xpPerSkill = mutableMapOf<String, Long>()
        val its        = mutableMapOf<String, Int>()
        val kills      = mutableMapOf<String, Int>()
        val food   = mutableMapOf<String, Int>()
        val arrows = mutableMapOf<String, Int>()
        val runes  = mutableMapOf<String, Int>()
        val died   = frames.any { it.died }
        if (died) acc.anyDied = true
        for (frame in frames) {
            for ((skill, xp) in frame.xpBySkill)      xpPerSkill[skill] = (xpPerSkill[skill] ?: 0L) + xp
            for ((item, qty) in frame.items)           its[item]         = (its[item] ?: 0) + qty
            for ((e, k) in frame.killsByEnemy)         kills[e]          = (kills[e] ?: 0) + k
            for ((f, q) in frame.foodConsumed)         food[f]           = (food[f] ?: 0) + q
            for ((a, q) in frame.arrowsConsumed)       arrows[a]         = (arrows[a] ?: 0) + q
            for ((r, q) in frame.runesConsumed)        runes[r]          = (runes[r] ?: 0) + q
        }
        for (skill in xpPerSkill.keys) {
            val mult = resolveCapeMultiplier(skill, ctx.equippedCape, ctx.inventory.keys, ctx.flags.townBuildingTiers, ctx.capeScalingBySkill, gameData.equipment, ctx.flags.ironman)
            if (mult > 1f) {
                xpPerSkill[skill] = (xpPerSkill[skill]!! * mult.toDouble()).toLong()
            }
        }
        if (died) {
            val keep = boostRepo.deathKeepFraction(ctx.flags)
            xpPerSkill.replaceAll { _, xp -> maxOf(1L, (xp * keep).toLong()) }
            its.replaceAll { _, qty -> maxOf(0, (qty * keep).toInt()) }
            its.entries.removeIf { it.value == 0 }
        }
        val coins = its.remove("coins")?.toLong() ?: 0L
        val pets  = its.filterKeys { it in ctx.petIds }
        val loot  = its.filterKeys { it !in ctx.petIds }
        var slayerXp = 0L
        for ((enemy, k) in kills) slayerXp += slayerRepo.recordKills(enemy, k)
        if (slayerXp > 0L) xpPerSkill[Skills.SLAYER] = (xpPerSkill[Skills.SLAYER] ?: 0L) + slayerXp
        val combatStyle = detectCombatStyle(xpPerSkill)
        if (!grantXp) xpPerSkill.clear()
        acc.awardedCapes += playerRepo.applyMultiSkillResults(xpPerSkill, loot, coins, sessionId = session.sessionId)
        for ((id, _) in pets) {
            val pd = gameData.pets[id] ?: continue
            if (playerRepo.addPetIfNew(id, pd.boostPercent))
                acc.petFoundName = GameStrings.petName(context, pd.id)
        }
        if (!died) {
            questRepo.recordCombat(
                dungeonKey         = session.activityKey,
                killsByEnemy       = kills,
                loot               = loot,
                combatStyle        = combatStyle,
                foodConsumedTotal  = food.values.sum(),
            )
            playerRepo.incrementDungeonRun(session.activityKey)
            if (kills.isNotEmpty()) {
                for ((e, k) in kills) acc.dailyKills[e] = (acc.dailyKills[e] ?: 0) + k
                guildRepo.recordGuildCombat(kills, combatStyle)
                seasonalEventRepo.recordCombat(kills)
            }
            seasonalEventRepo.recordExpeditionCompletion(session.activityKey)
        }
        val skillLvls      = playerRepo.getSkillLevels()
        val arrowsReclaimed = arrows.mapValues { (_, qty) -> (qty * (reclaimChance(skillLvls[Skills.RANGED] ?: 1) + boostRepo.arrowReclaimBonus(ctx.flags)).coerceAtMost(0.95)).toInt() }.filterValues { it > 0 }
        val runesReclaimed  = runes.mapValues  { (_, qty) -> (qty * (reclaimChance(skillLvls[Skills.MAGIC] ?: 1) + boostRepo.runeReclaimBonus(ctx.flags)).coerceAtMost(0.95)).toInt() }.filterValues { it > 0 }
        if (food.isNotEmpty())          playerRepo.consumeItems(food)
        if (arrows.isNotEmpty())        playerRepo.consumeItems(arrows)
        if (arrowsReclaimed.isNotEmpty()) playerRepo.addItems(arrowsReclaimed)
        if (runesReclaimed.isNotEmpty())  playerRepo.addItems(runesReclaimed)
        val dungeonRunFlags = playerRepo.getFlags()
        playerRepo.updateFlags(dungeonRunFlags.copy(
            dungeonLastRunStats = dungeonRunFlags.dungeonLastRunStats + (session.activityKey to DungeonRunStats(
                foodConsumed = food.values.sum(),
                killCount    = kills.values.sum(),
                survived     = !died,
            ))
        ))
        for ((skill, xp) in xpPerSkill) {
            acc.combinedXpBySkill[skill] = (acc.combinedXpBySkill[skill] ?: 0L) + prestigeAdjustedXp(skill, xp, ctx.flags)
        }
        for ((item, qty) in loot)        acc.combinedItems[item]      = (acc.combinedItems[item] ?: 0) + qty
        for ((e, k) in kills)            acc.combinedKills[e]         = (acc.combinedKills[e] ?: 0) + k
        for ((f, q) in food)             acc.combinedFood[f]          = (acc.combinedFood[f] ?: 0) + q
        for ((a, q) in arrows)           acc.combinedArrows[a]         = (acc.combinedArrows[a] ?: 0) + q
        for ((a, q) in arrowsReclaimed)  acc.combinedArrowsReclaimed[a] = (acc.combinedArrowsReclaimed[a] ?: 0) + q
        for ((r, q) in runes)            acc.combinedRunes[r]          = (acc.combinedRunes[r] ?: 0) + q
        for ((r, q) in runesReclaimed)   acc.combinedRunesReclaimed[r] = (acc.combinedRunesReclaimed[r] ?: 0) + q
        acc.combinedCoins += coins
    }

    private suspend fun collectMercantileSession(session: SkillSession, frames: List<SessionFrame>, grantXp: Boolean, ctx: CollectContext, acc: CollectAcc) {
        val totalXp    = if (grantXp) frames.sumOf { it.xpGain.toLong() } else 0L
        val coinReturn = frames.sumOf { (it.items["_coins"] ?: 0).toLong() }
        val mercantileCapeMult    = resolveCapeMultiplier(Skills.MERCANTILE, ctx.equippedCape, ctx.inventory.keys, ctx.flags.townBuildingTiers, ctx.capeScalingBySkill, gameData.equipment, ctx.flags.ironman)
        val mercantilePrestigeMult = boostRepo.coinMultiplier(Skills.MERCANTILE, ctx.flags).toFloat()
        // acc.combinedCoins must stay pre-blessing here, like every other skill's coin
        // total below -- the summary popup applies ctx.blessingCoinMult to acc.combinedCoins
        // once already (see displayedCoins), so baking blessing in twice inflated the
        // shown total without it ever landing in the real balance (issue #1192).
        val coinReturnPreBlessing = (coinReturn.toDouble() * mercantileCapeMult * mercantilePrestigeMult).toLong()
        val coinReturnBoosted = (coinReturnPreBlessing * ctx.blessingCoinMult).toLong()
        acc.awardedCapes += playerRepo.applySessionResults(Skills.MERCANTILE, totalXp, emptyMap(), sessionId = session.sessionId)
        playerRepo.addCoins(coinReturnBoosted)
        guildRepo.recordGuildTrade(session.activityKey, coinReturnBoosted)
        val pets = frames.asSequence().flatMap { it.items.keys }.filter { it in ctx.petIds }.toSet()
        for (id in pets) {
            val pd = gameData.pets[id] ?: continue
            if (playerRepo.addPetIfNew(id, pd.boostPercent))
                acc.petFoundName = GameStrings.petName(context, pd.id)
        }
        playerRepo.recordWeeklyProgress("mercantile", session.activityKey, frames.size)
        acc.combinedXpBySkill[Skills.MERCANTILE] = (acc.combinedXpBySkill[Skills.MERCANTILE] ?: 0L) + prestigeAdjustedXp(Skills.MERCANTILE, totalXp, ctx.flags)
        acc.combinedCoins += coinReturnPreBlessing
    }

    /**
     * XP as the payout actually grants it: applySessionResults bakes prestige xp_pct into the
     * granted amount, so every summary line fed from raw frame XP must bake it in too or the
     * dialog understates what was paid (issue #1790).
     */
    private fun prestigeAdjustedXp(skill: String, xp: Long, flags: PlayerFlags): Long {
        val xpPct = boostRepo.prestigeXpPct(skill, flags)
        return if (xpPct > 0) (xp * (1.0 + xpPct / 100.0)).toLong() else xp
    }

    private suspend fun collectGenericSkillSession(session: SkillSession, frames: List<SessionFrame>, grantXp: Boolean, ctx: CollectContext, acc: CollectAcc) {
        val totalXp = if (grantXp) frames.sumOf { it.xpGain.toLong() } else 0L
        val its     = mutableMapOf<String, Int>()
        for (frame in frames) for ((item, qty) in frame.items) its[item] = (its[item] ?: 0) + qty
        // Elder Isle sessions bypass every mainland boost/cape/heirloom path and write XP into
        // the elder pool. The isle economy is walled off from mainland modifiers, per the
        // bonus-flow rule in the design doc.
        if (session.isElderSession || sessionIsIsleByActivity(session)) {
            val elderPets = its.filterKeys { it in ctx.petIds }
            val elderLoot = its.filterKeys { it != "coins" && it !in ctx.petIds }
            playerRepo.applyElderSessionResults(session.skillName, totalXp, elderLoot)
            for ((id, _) in elderPets) {
                val pd = gameData.pets[id] ?: continue
                if (playerRepo.addPetIfNew(id, pd.boostPercent))
                    acc.petFoundName = GameStrings.petName(context, pd.id)
            }
            acc.combinedXpBySkill[session.skillName] = (acc.combinedXpBySkill[session.skillName] ?: 0L) + totalXp
            for ((item, qty) in elderLoot) acc.combinedItems[item] = (acc.combinedItems[item] ?: 0) + qty
            return
        }
        val coinsFromItems = (its.remove("coins") ?: 0).toLong()
        if (coinsFromItems > 0) {
            val coinsBoosted = (coinsFromItems * boostRepo.coinMultiplier(session.skillName, ctx.flags)).toLong()
            playerRepo.addCoins(coinsBoosted)
            acc.combinedCoins += coinsBoosted
        }
        val pets       = its.filterKeys { it in ctx.petIds }
        val rawRegular = its.filterKeys { it !in ctx.petIds }
        val capeMult   = resolveCapeMultiplier(session.skillName, ctx.equippedCape, ctx.inventory.keys, ctx.flags.townBuildingTiers, ctx.capeScalingBySkill, gameData.equipment, ctx.flags.ironman)
        // Prestige yield nodes plus the flow-state ramp scale gathered items (v1.14.0).
        val sessionDurMs = (session.endsAt - session.startedAt).coerceAtLeast(0L)
        val prestigeItemMult = boostRepo.yieldMultiplier(session.skillName, ctx.flags) *
            boostRepo.flowMultiplier(session.skillName, ctx.flags, boostRepo.flowElapsedMs(ctx.flags, session.skillName, sessionDurMs))
        val itemMult    = (if (capeMult > 1f && rawRegular.isNotEmpty()) capeMult.toDouble() else 1.0) * prestigeItemMult
        val effectiveXp = if (capeMult > 1f && rawRegular.isEmpty()) (totalXp.toDouble() * capeMult).toLong() else totalXp
        val regular     = if (itemMult > 1.0 && rawRegular.isNotEmpty()) rawRegular.mapValues { (_, qty) -> (qty * itemMult).roundToInt().coerceAtLeast(qty) } else rawRegular
        acc.awardedCapes += playerRepo.applySessionResults(session.skillName, effectiveXp, regular, sessionId = session.sessionId)
        when (session.skillName) {
            in COLLECT_GATHERING_SKILLS -> {
                questRepo.recordGathering(session.skillName, regular)
                playerRepo.recordDailyGathering(regular)
                seasonalEventRepo.recordGathering(regular)
                when (session.skillName) {
                    Skills.AGILITY      -> {
                        guildRepo.recordGuildSessions(session.activityKey)
                        playerRepo.recordWeeklyProgress("agility", session.activityKey, frames.size)
                    }
                    Skills.RUNECRAFTING -> guildRepo.recordGuildCrafting(session.skillName, regular)
                    else                -> guildRepo.recordGuildGathering(session.skillName, regular)
                }
            }
            in COLLECT_CRAFTING_SKILLS  -> {
                questRepo.recordCrafting(session.skillName, regular)
                playerRepo.recordDailyCrafting(regular)
                guildRepo.recordGuildCrafting(session.skillName, regular)
                seasonalEventRepo.recordCrafting(regular)
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
                    playerRepo.recordDailyPrayer(buried)
                }
            }
            Skills.FARMING     -> guildRepo.recordGuildGathering(Skills.FARMING, regular)
        }
        for ((id, _) in pets) {
            val pd = gameData.pets[id] ?: continue
            if (playerRepo.addPetIfNew(id, pd.boostPercent))
                acc.petFoundName = GameStrings.petName(context, pd.id)
        }
        acc.combinedXpBySkill[session.skillName] = (acc.combinedXpBySkill[session.skillName] ?: 0L) + prestigeAdjustedXp(session.skillName, totalXp, ctx.flags)
        for ((item, qty) in regular) acc.combinedItems[item] = (acc.combinedItems[item] ?: 0) + qty
        if (session.skillName == Skills.PRAYER) {
            val count = frames.sumOf { it.kills }
            val name  = GameStrings.itemName(context, session.activityKey)
            acc.combinedBones[name] = (acc.combinedBones[name] ?: 0) + count
        }
    }


    fun onSessionExpiredLocally(sessionId: String) {
        viewModelScope.launch {
            val session = sessionRepo.getSession(sessionId) ?: return@launch
            if (!session.completed && sessionRepo.hasTrustedClock(session)) {
                sessionRepo.markCompleted(sessionId)
                queuedSessionStarter.startNextQueued()
            }
        }
    }

    fun bossEmoji(activityKey: String): String? = gameData.bosses[activityKey]?.emoji

    fun bossDurationMinutes(activityKey: String): Int? = gameData.bosses[activityKey]?.durationMinutes

    /** Race -> skills with race-locked prestige branches, for the character setup sheet. */
    val raceProficiencies: Map<String, List<String>> by lazy {
        PrestigeBoosts.raceProficiencies(gameData.prestigeTrees)
    }

    fun repeatActiveSession() {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSession() ?: return@launch
            // Repeat must respect the same combat level gate as starting fresh, or a
            // prestiged player can chain content far above their level (issue #1542).
            // Raid bosses are exempt: their level is flavor, mercenaries are the bar.
            if (session.skillName == "combat" || session.skillName == "boss") {
                val gateLevels: Map<String, Int> = json.decodeFromString(playerRepo.getOrCreatePlayer().skillLevels)
                val requiredLvl = when (session.skillName) {
                    "combat" -> gameData.dungeons[session.activityKey]?.let { it.recommendedLevel - UNLOCK_TOLERANCE }
                    else     -> gameData.bosses[session.activityKey]?.takeIf { !it.raid }?.combatLevelRequired
                }
                if (requiredLvl != null && combatLevelFrom(gateLevels) < requiredLvl) {
                    val blockedName = if (session.skillName == "combat") GameStrings.dungeonName(context, session.activityKey)
                                      else GameStrings.bossName(context, session.activityKey)
                    _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.repeat_blocked_level, blockedName)) }
                    return@launch
                }
            }
            val craftingSkills = setOf(Skills.SMITHING, Skills.COOKING, Skills.FLETCHING,
                Skills.CRAFTING, Skills.HERBLORE, Skills.FIREMAKING, Skills.RUNECRAFTING, Skills.PRAYER,
                Skills.CONSTRUCTION)
            val frames = json.decodeFromString<List<SessionFrame>>(session.frames)
            val qty = if (session.skillName in craftingSkills)
                frames.sumOf { it.kills } else 0
            val flags = playerRepo.getFlags()
            val nextTowerFloor = if (session.skillName == "tower") {
                val lastQueuedFloor = flags.sessionQueue
                    .lastOrNull { it.skillName == "tower" }
                    ?.activityKey?.removePrefix("tower_floor_")?.toIntOrNull()
                val runningFloor = lastQueuedFloor
                    ?: session.activityKey.removePrefix("tower_floor_").toIntOrNull()
                    ?: flags.towerCurrentFloor
                runningFloor + 1
            } else null
            val activityKeyForRepeat = if (nextTowerFloor != null) "tower_floor_$nextTowerFloor" else session.activityKey
            val displayName = when (session.skillName) {
                "combat"     -> GameStrings.dungeonName(context, session.activityKey)
                "boss"       -> GameStrings.bossName(context, session.activityKey)
                "expedition" -> gameData.skillingDungeons[session.activityKey]?.displayName ?: session.activityKey
                "tower"      -> "Infinite Tower: Floor $nextTowerFloor"
                else         -> session.skillName.toTitleCase()
            }
            val coinCostForRepeat = if (session.skillName == Skills.MERCANTILE) {
                gameData.tradeRoutes.firstOrNull { it.id == session.activityKey }?.coinCost?.toLong() ?: 0L
            } else 0L
            if (coinCostForRepeat > 0) {
                val ok = playerRepo.spendCoins(coinCostForRepeat)
                if (!ok) {
                    _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.home_not_enough_coins_repeat, displayName)) }
                    return@launch
                }
            }
            val materials = playerSessionMaterials(session.skillName, session.activityKey, qty, gameData)
            if (materials != null) {
                val ok = playerRepo.consumeItems(materials)
                if (!ok) {
                    if (coinCostForRepeat > 0) playerRepo.addCoins(coinCostForRepeat)
                    _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.home_not_enough_materials_repeat, displayName)) }
                    return@launch
                }
            }
            val isCombat = session.skillName == "combat" || session.skillName == "boss"
            val player = playerRepo.getOrCreatePlayer()
            val weaponSlot = if (isCombat) {
                val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
                flags.activeWeaponSlot
                    ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equipped[it] != null }
                    ?: EquipSlot.WEAPON_ATK
            } else null
            val xpQueueMult = if (flags.ironman) 1.0 else (if (flags.xpBoostExpiresAt > System.currentTimeMillis()) 2.0 else 1.0) * ChurchRepository.xpMultiplier(flags, blessingPrayerCapeMult(player, flags, gameData), gameData.blessings)
            val rawXpGain = frames.sumOf { it.xpGain }
            // The original fight/run count isn't stored on the session itself, only in the
            // repeat-chain flags set when it was first started -- carry it forward so
            // repeating a 100-fight boss session queues 100 more, not just 1 (issue #1188).
            val repeatCount = when (session.skillName) {
                "boss"   -> flags.activeBossRepeatTotal.takeIf { it > 0 } ?: 1
                "combat" -> flags.activeDungeonRepeatTotal.takeIf { it > 0 } ?: 1
                else     -> 1
            }
            val enqueued = playerRepo.enqueueAction(QueuedAction(
                skillName           = session.skillName,
                activityKey         = activityKeyForRepeat,
                skillDisplayName    = displayName,
                qty                 = qty,
                repeatCount         = repeatCount,
                estimatedDurationMs = session.endsAt - session.startedAt,
                estimatedXpGain     = if (session.skillName in listOf("carnival", "expedition", "tower")) 0L
                                      else (rawXpGain * xpQueueMult).toLong(),
                xpBoostMultAtQueue  = xpQueueMult,
                weaponSlot          = weaponSlot,
                equippedSnapshot    = if (isCombat) player.equipped else null,
                spellName           = flags.activeSpell,
                arrowsKey           = flags.equippedArrows,
                potionKey           = flags.activePotionKey,
                coinRefund          = coinCostForRepeat,
            ))
            if (enqueued && nextTowerFloor != null) reconcileTowerQueue()
            if (!enqueued) {
                if (coinCostForRepeat > 0) playerRepo.addCoins(coinCostForRepeat)
                if (materials != null) playerRepo.addItems(materials)
            }
            // Combat/boss/expedition/tower/carnival already have a specific name baked into
            // displayName above; every other skill only has its skill name there, so the queued
            // message would otherwise drop the specific activity (e.g. "Fishing" instead of
            // "Fishing — Raw Salmon") that the direct-queue path already shows (issue #1168).
            val queueMessage = when (session.skillName) {
                "combat", "boss", "expedition", "tower", "carnival" ->
                    context.withAppLocale().getString(R.string.snackbar_added_to_queue, displayName)
                else -> context.withAppLocale().getString(
                    R.string.skill_added_to_queue_activity,
                    GameStrings.skillName(context, session.skillName),
                    GameStrings.activityName(context, session.skillName, session.activityKey),
                )
            }
            _extra.update {
                it.copy(snackbarMessage = if (enqueued) queueMessage else context.withAppLocale().getString(R.string.snackbar_queue_full))
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
                val storedMats: Map<String, Int>? = session.consumedMaterials?.let {
                    try { json.decodeFromString<Map<String, Int>>(it) } catch (_: Exception) { null }
                }
                if (!storedMats.isNullOrEmpty()) {
                    playerRepo.addItems(storedMats)
                } else {
                    playerSessionMaterials(session.skillName, session.activityKey, frames.sumOf { it.kills }, gameData)
                        ?.let { playerRepo.addItems(it) }
                }
            }
            if (session.catalystKey != null && session.catalystQty > 0) {
                playerRepo.addItem(session.catalystKey, session.catalystQty)
            }
            sessionRepo.abandonSession(session.sessionId)
            if (session.skillName == "boss") playerRepo.clearActiveBossRepeat()
            if (session.skillName == "combat") playerRepo.clearActiveDungeonRepeat()
            queuedSessionStarter.startNextQueued()
            reconcileTowerQueue()
        }
    }

    fun debugFinishSession() {
        viewModelScope.launch {
            queuedSessionStarter.debugFinishActiveSessionWithRepeats()
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
            if (!session.completed && sessionRepo.hasTrustedClock(session)) {
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
                if (latest != null && !latest.completed && System.currentTimeMillis() >= latest.endsAt && sessionRepo.hasTrustedClock(latest)) {
                    sessionRepo.markCompleted(latest.sessionId)
                }
            }

            val slot1Sessions = sessionRepo.getAllCompletedWorkerSessions(1)
            val slot2Sessions = sessionRepo.getAllCompletedWorkerSessions(2)
            val sessions = slot1Sessions + slot2Sessions
            if (sessions.isEmpty()) return@launch

            val petIds = gameData.pets.keys
            val workerPlayer = playerRepo.getOrCreatePlayer()
            val flags: PlayerFlags = json.decodeFromString(workerPlayer.flags)
            val boostFactorFor   = { skill: String -> boostRepo.xpBoostFactor(skill, flags) }
            val workerCapeMult   = blessingPrayerCapeMult(workerPlayer, flags, gameData)
            val blessingXpMult   = if (flags.ironman) 1.0f else ChurchRepository.xpMultiplier(flags, workerCapeMult, gameData.blessings)
            val blessingCoinMult = if (flags.ironman) 1.0f else ChurchRepository.coinMultiplier(flags, workerCapeMult, gameData.blessings) *
                PlayerRepository.gooseCoinMultiplier(json.decodeFromString<List<OwnedPet>>(workerPlayer.pets)).toFloat()
            val innXpMult        = townRepo.workerXpMultiplier(flags)
            val workerOwnedPets: List<OwnedPet> = if (flags.ironman) emptyList()
                else try { json.decodeFromString(workerPlayer.pets) } catch (_: Exception) { emptyList() }

            val combinedXpBySkill = mutableMapOf<String, Long>()
            val combinedItems     = mutableMapOf<String, Int>()
            val combinedKills     = mutableMapOf<String, Int>()
            var combinedCoins     = 0L
            var anyDied           = false
            var petFoundName: String? = null
            val awardedCapes = mutableListOf<String>()

            val gatheringSkills = setOf(Skills.MINING, Skills.WOODCUTTING, Skills.FISHING, Skills.AGILITY)
            val craftingSkills  = setOf(Skills.SMITHING, Skills.COOKING, Skills.FLETCHING, Skills.CRAFTING, Skills.HERBLORE, Skills.FIREMAKING, Skills.RUNECRAFTING)

            val currentLevelsForVoidCheck = playerRepo.getSkillLevels()
            for (session in sessions) {
                // Prestige mid-session forfeits only the XP (zeroed at the frame level here —
                // no combat-style detection in the worker path needs the raw values).
                val grantXp = isSkillSessionStillEligible(session, currentLevelsForVoidCheck, gameData)
                val frames: List<SessionFrame> = json.decodeFromString<List<SessionFrame>>(session.frames)
                    .let { if (grantXp) it else it.map { f -> f.copy(xpGain = 0, xpBySkill = emptyMap()) } }
                val mult = session.efficiencyMultiplier

                when (session.skillName) {
                    "boss" -> {
                        val frame = frames.lastOrNull() ?: continue
                        val won = frame.kills > 0 || frame.killsByEnemy.isNotEmpty()
                        if (won) {
                            val its   = frame.items.toMutableMap()
                            val coins = its.remove("coins")?.toLong() ?: 0L
                            val pets  = its.filterKeys { it in petIds }
                            val loot  = its.filterKeys { it !in petIds }
                            val workerBossXp = mutableMapOf<String, Long>()
                            for (f in frames) f.xpBySkill.forEach { (k, v) -> workerBossXp[k] = (workerBossXp[k] ?: 0L) + v }
                            val workerBossPetBoost = workerBossXp.keys.associateWith { skill ->
                                workerOwnedPets.sumOf { ownedPet ->
                                    val pd = gameData.pets[ownedPet.id]
                                    if (pd == null) 0
                                    else when {
                                        pd.boostedSkill == "all" -> pd.boostPercent
                                        pd.boostedSkill == skill -> pd.boostPercent
                                        pd.boostedSkill == "combat" && skill in Skills.COMBAT -> pd.boostPercent
                                        else -> 0
                                    }
                                }
                            }.filterValues { it > 0 }
                            awardedCapes += playerRepo.applyMultiSkillResults(workerBossXp, loot, coins, mult, workerBossPetBoost, sessionId = session.sessionId)
                            for ((id, _) in pets) {
                                val pd = gameData.pets[id] ?: continue
                                if (playerRepo.addPetIfNew(id, pd.boostPercent))
                                    petFoundName = GameStrings.petName(context, pd.id)
                            }
                            for ((skill, xp) in workerBossXp) {
                                val petPct = workerBossPetBoost[skill] ?: 0
                                val withPet = if (petPct > 0) (xp * (1.0 + petPct / 100.0)).toLong() else xp
                                combinedXpBySkill[skill] = (combinedXpBySkill[skill] ?: 0L) + prestigeAdjustedXp(skill, withPet, flags)
                            }
                            for ((item, qty) in loot) combinedItems[item] = (combinedItems[item] ?: 0) + qty
                            combinedCoins += coins
                            combinedKills[session.activityKey] = (combinedKills[session.activityKey] ?: 0) + 1
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
                            val keep = boostRepo.deathKeepFraction(flags)
                            xpPerSkill.replaceAll { _, xp -> maxOf(1L, (xp * keep).toLong()) }
                            its.replaceAll { _, qty -> maxOf(0, (qty * keep).toInt()) }
                            its.entries.removeIf { it.value == 0 }
                        }
                        val coins = (its.remove("coins")?.toLong() ?: 0L).let { if (died) maxOf(0L, (it * boostRepo.deathKeepFraction(flags)).toLong()) else it }
                        val pets  = its.filterKeys { it in petIds }
                        val loot  = its.filterKeys { it !in petIds }
                        awardedCapes += playerRepo.applyMultiSkillResults(xpPerSkill, loot, coins, mult, sessionId = session.sessionId)
                        for ((id, _) in pets) {
                            val pd = gameData.pets[id] ?: continue
                            if (playerRepo.addPetIfNew(id, pd.boostPercent))
                                petFoundName = GameStrings.petName(context, pd.id)
                        }
                        if (!died) {
                            playerRepo.incrementDungeonRun(session.activityKey)
                        }
                        for ((skill, xp) in xpPerSkill) {
                            combinedXpBySkill[skill] = (combinedXpBySkill[skill] ?: 0L) + prestigeAdjustedXp(skill, xp, flags)
                        }
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
                        awardedCapes += playerRepo.applySessionResults(skillName, totalXp, regular, mult, sessionId = session.sessionId)
                        for ((id, _) in pets) {
                            val pd = gameData.pets[id] ?: continue
                            if (playerRepo.addPetIfNew(id, pd.boostPercent))
                                petFoundName = GameStrings.petName(context, pd.id)
                        }
                        val scaledXp      = if (mult == 1.0f) totalXp else (totalXp * mult).toLong()
                        val scaledRegular = if (mult == 1.0f) regular
                            else regular.mapValues { (_, v) -> (v * mult).toInt().coerceAtLeast(1) }
                        combinedXpBySkill[skillName] = (combinedXpBySkill[skillName] ?: 0L) + prestigeAdjustedXp(skillName, scaledXp, flags)
                        for ((item, qty) in scaledRegular) combinedItems[item] = (combinedItems[item] ?: 0) + qty
                    }
                    else -> {
                        val baseXp  = frames.sumOf { it.xpGain.toLong() }
                        val totalXp = (baseXp * innXpMult).toLong()
                        val its     = mutableMapOf<String, Int>()
                        for (frame in frames) for ((item, qty) in frame.items) its[item] = (its[item] ?: 0) + qty
                        val coinsFromItems = (its.remove("coins") ?: 0).toLong()
                        if (coinsFromItems > 0) {
                            val coinsBoosted = (coinsFromItems * boostRepo.coinMultiplier(session.skillName, flags)).toLong()
                            playerRepo.addCoins(coinsBoosted)
                            combinedCoins += coinsBoosted
                        }
                        val pets       = its.filterKeys { it in petIds }
                        val rawRegular = its.filterKeys { it !in petIds }
                        val sessionDurMs = (session.endsAt - session.startedAt).coerceAtLeast(0L)
                        val prestigeItemMult = boostRepo.yieldMultiplier(session.skillName, flags) *
                            boostRepo.flowMultiplier(session.skillName, flags, boostRepo.flowElapsedMs(flags, session.skillName, sessionDurMs))
                        val regular = if (prestigeItemMult > 1.0) rawRegular.mapValues { (_, qty) -> (qty * prestigeItemMult).roundToInt().coerceAtLeast(qty) } else rawRegular
                        awardedCapes += playerRepo.applySessionResults(session.skillName, totalXp, regular, mult, sessionId = session.sessionId)
                        for ((id, _) in pets) {
                            val pd = gameData.pets[id] ?: continue
                            if (playerRepo.addPetIfNew(id, pd.boostPercent))
                                petFoundName = GameStrings.petName(context, pd.id)
                        }
                        val scaledXp      = if (mult == 1.0f) totalXp else (totalXp * mult).toLong()
                        val scaledRegular = if (mult == 1.0f) regular
                            else regular.mapValues { (_, v) -> (v * mult).toInt().coerceAtLeast(1) }
                        combinedXpBySkill[session.skillName] = (combinedXpBySkill[session.skillName] ?: 0L) + prestigeAdjustedXp(session.skillName, scaledXp, flags)
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
                n > 1 -> context.withAppLocale().getString(R.string.worker_sessions_complete_title, n)
                last.skillName == "boss" -> {
                    val bossName = GameStrings.bossName(context, last.activityKey)
                    context.withAppLocale().getString(R.string.worker_boss_defeated_title, bossName)
                }
                last.skillName == "combat" -> {
                    val dungeonName = GameStrings.dungeonName(context, last.activityKey)
                    if (anyDied) context.withAppLocale().getString(R.string.worker_dungeon_died_title, dungeonName)
                    else context.withAppLocale().getString(R.string.worker_dungeon_complete_title, dungeonName)
                }
                else -> context.withAppLocale().getString(
                    R.string.worker_skill_complete_title,
                    GameStrings.skillName(context, last.skillName),
                )
            }

            val useTotalLabel    = n == 1 && combinedXpBySkill.size == 1 && combinedKills.isEmpty()
            val singleXp         = combinedXpBySkill.values.firstOrNull() ?: 0L
            val displayedCoins   = (combinedCoins.toDouble() * blessingCoinMult).toLong()
            val coinBlessingBonus = displayedCoins - combinedCoins
            val sortedXpEntries   = combinedXpBySkill.entries.sortedByDescending { it.value }
            val singleXpFactor    = combinedXpBySkill.keys.firstOrNull()?.let(boostFactorFor) ?: 1L
            val xpLineBonuses     = sortedXpEntries.map { (skill, xp) ->
                val base = xp * boostFactorFor(skill)
                ((base.toDouble() * blessingXpMult).toLong() - base).coerceAtLeast(0L)
            }
            val singleXpBonus = run {
                val base = singleXp * singleXpFactor
                ((base.toDouble() * blessingXpMult).toLong() - base).coerceAtLeast(0L)
            }

            val summary = SessionSummary(
                title          = title,
                died           = anyDied,
                xpLines        = if (useTotalLabel) emptyList()
                                 else sortedXpEntries
                                     .map { (skill, xp) -> Pair(GameStrings.skillName(context, skill), "+${((xp * boostFactorFor(skill)).toDouble() * blessingXpMult).toLong().formatXp()} XP") },
                xpLineValues   = if (useTotalLabel) emptyList()
                                 else sortedXpEntries
                                     .map { (skill, xp) -> ((xp * boostFactorFor(skill)).toDouble() * blessingXpMult).toLong() },
                totalXpLabel      = if (useTotalLabel) "+${((singleXp * singleXpFactor).toDouble() * blessingXpMult).toLong().formatXp()} XP" else "",
                totalXpLabelBonus = if (useTotalLabel) singleXpBonus else 0L,
                totalXpValue      = if (useTotalLabel) ((singleXp * singleXpFactor).toDouble() * blessingXpMult).toLong() else 0L,
                itemLines      = combinedItems.entries.sortedByDescending { it.value }
                                     .map { (key, qty) -> Pair(GameStrings.itemName(context,key), "×$qty") },
                coinsGained    = displayedCoins,
                killLines      = combinedKills.entries.sortedByDescending { it.value }
                                     .map { (enemy, kills) -> Pair(enemy, "×$kills") },
                foodConsumedLines = emptyList(),
                boostWasActive   = combinedXpBySkill.keys.any { boostFactorFor(it) > 1L },
                xpLineBoostFactors = if (useTotalLabel) emptyList()
                                     else sortedXpEntries.map { (skill, _) -> boostFactorFor(skill) },
                totalXpBoostFactor = if (useTotalLabel) singleXpFactor else 1L,
                xpLineBonuses    = xpLineBonuses,
                coinBlessingBonus = coinBlessingBonus,
            )

            val capeMessage = if (awardedCapes.isNotEmpty()) {
                val names = awardedCapes.joinToString(", ") { GameStrings.itemName(context,it) }
                context.withAppLocale().getString(R.string.home_congratulations_received, names)
            } else null
            _extra.update { it.copy(
                workerSummary   = summary,
                snackbarMessage = capeMessage,
                petFoundName    = petFoundName,
            ) }
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
        val hasTowerActions = uiState.value.sessionQueue.any { it.skillName == "tower" }
        viewModelScope.launch {
            val action = playerRepo.removeFromQueue(index) ?: return@launch
            if (action.coinRefund > 0) playerRepo.addCoins(action.coinRefund)
            if (action.consumedMaterials.isNotEmpty()) {
                playerRepo.addItems(action.consumedMaterials)
            } else {
                playerSessionMaterials(action.skillName, action.activityKey, action.qty, gameData)
                    ?.let { playerRepo.addItems(it) }
            }
            if (action.catalystKey != null && action.catalystQty > 0) {
                playerRepo.addItem(action.catalystKey, action.catalystQty)
            }
            if (hasTowerActions) reconcileTowerQueue()
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val hasTowerActions = uiState.value.sessionQueue.any { it.skillName == "tower" }
        viewModelScope.launch {
            playerRepo.moveQueueItem(fromIndex, toIndex)
            if (hasTowerActions) reconcileTowerQueue()
        }
    }

    fun saveCharacterProfile(name: String, gender: String, race: String, ironman: Boolean? = null) {
        viewModelScope.launch { playerRepo.updateCharacterProfile(name, gender, race, ironman) }
    }

    fun dismissCharacterSetup() {
        viewModelScope.launch { playerRepo.dismissCharacterSetup() }
    }

    fun summaryConsumed() = _extra.update { it.copy(sessionSummary = null) }
    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }
    fun petDialogConsumed() = _extra.update { it.copy(petFoundName = null) }

    fun openJournal() = _extra.update { it.copy(journalSheetOpen = true) }
    fun dismissJournal() = _extra.update { it.copy(journalSheetOpen = false) }

    fun updateNotes(text: String) {
        viewModelScope.launch {
            playerRepo.updateFlags(playerRepo.getFlags().copy(playerNotes = text))
        }
    }

    fun dismissWhatsNew() {
        viewModelScope.launch {
            playerRepo.markWhatsNewSeen(BuildConfig.VERSION_CODE)
        }
    }

    fun toggleTownGridExpanded() {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(townGridExpanded = !flags.townGridExpanded))
        }
    }

    /**
     * Set sail to Elder Isle or return to mainland. Blocked while any session is running,
     * so the player is never mid-fight when the whole app UI swaps context.
     */
    fun toggleElderIsleLocation() {
        viewModelScope.launch {
            if (sessionRepo.getActiveSession() != null) {
                _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.elder_isle_sail_blocked_by_session)) }
                return@launch
            }
            val flags = playerRepo.getFlags()
            if (!flags.elderIsleUnlocked && !flags.onElderIsle) {
                // Split the blocked message: pre-Dock vs Dock-built-but-Serpent-alive. The
                // second case is the one players hit after a Dock upgrade, so name the boss.
                val dockBuilt = (flags.townBuildingTiers["dock"] ?: 0) >= 1
                val messageRes = if (dockBuilt && !flags.seaSerpentDefeated)
                    R.string.elder_isle_sail_blocked_serpent
                else R.string.elder_isle_sail_blocked_locked
                _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(messageRes)) }
                return@launch
            }
            playerRepo.updateFlags(flags.copy(onElderIsle = !flags.onElderIsle))
        }
    }

    /** Marks the isle welcome splash as seen so it stops appearing on future landings. */
    fun dismissIsleWelcome() {
        viewModelScope.launch {
            playerRepo.updateFlagsAtomically { it.copy(elderIsleWelcomed = true) }
        }
    }

    private suspend fun reconcileTowerQueue() {
        val activeSession = sessionRepo.getActiveSession()
        val runningFloor = if (activeSession?.skillName == "tower") {
            activeSession.activityKey.removePrefix("tower_floor_").toIntOrNull() ?: 1
        } else {
            val player = playerRepo.getOrCreatePlayer()
            val flags: PlayerFlags = try { json.decodeFromString(player.flags) } catch (_: Exception) { PlayerFlags() }
            flags.towerCurrentFloor
        }

        playerRepo.updateFlagsAtomically { flags ->
            var nextFloor = runningFloor + 1
            var changed = false
            val newQueue = flags.sessionQueue.map { action ->
                if (action.skillName == "tower") {
                    val expectedKey = "tower_floor_$nextFloor"
                    val expectedName = "Infinite Tower: Floor $nextFloor"
                    nextFloor++
                    if (action.activityKey != expectedKey || action.skillDisplayName != expectedName) {
                        changed = true
                        action.copy(activityKey = expectedKey, skillDisplayName = expectedName)
                    } else action
                } else action
            }
            if (changed) flags.copy(sessionQueue = newQueue) else flags
        }
    }

    private data class ExpeditionResult(
        val petFoundName: String?,
        val noteLines: List<String>,
        val unlockMessage: String?
    )

    private suspend fun collectExpeditionSession(
        session: SkillSession,
        frames: List<SessionFrame>,
        petIds: Set<String>,
        flags: PlayerFlags,
        awardedCapes: MutableList<String>,
        combinedXpBySkill: MutableMap<String, Long>,
        combinedItems: MutableMap<String, Int>
    ): ExpeditionResult {
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
        var petFoundName: String? = null
        for ((id, _) in pets) {
            val pd = gameData.pets[id] ?: continue
            if (playerRepo.addPetIfNew(id, pd.boostPercent))
                petFoundName = GameStrings.petName(context, pd.id)
        }
        combinedXpBySkill[skillName] = (combinedXpBySkill[skillName] ?: 0L) + prestigeAdjustedXp(skillName, totalXp, flags)
        for ((item, qty) in regular) combinedItems[item] = (combinedItems[item] ?: 0) + qty
        var localUnlockMsg: String? = null
        var localNotesFound = 0
        var localOldCount = 0
        var localNewCount = 0

        playerRepo.updateFlagsAtomically { currentFlags ->
            val pityCount    = currentFlags.expeditionPityRuns[session.activityKey] ?: 0
            val notesFound   = if (rawNotesFound == 0 && pityCount >= 9) 1 else rawNotesFound
            localNotesFound = notesFound
            val newPityCount = if (notesFound > 0) 0 else pityCount + 1
            val newPityRuns  = currentFlags.expeditionPityRuns.toMutableMap().apply {
                if (newPityCount == 0) remove(session.activityKey) else put(session.activityKey, newPityCount)
            }
            if (notesFound > 0 && dungeonData != null) {
                val oldCount = currentFlags.skillingDungeonNotes[session.activityKey] ?: 0
                localOldCount = oldCount
                val newCount = minOf(oldCount + notesFound, dungeonData.noteThreshold)
                localNewCount = newCount
                val newNotes = currentFlags.skillingDungeonNotes.toMutableMap()
                newNotes[session.activityKey] = newCount
                val newUnlocked = currentFlags.unlockedDungeons.toMutableList()
                if (newCount >= dungeonData.noteThreshold && !currentFlags.unlockedDungeons.contains(dungeonData.unlockDungeon)) {
                    newUnlocked += dungeonData.unlockDungeon
                    localUnlockMsg = dungeonData.unlockMessage
                }
                currentFlags.copy(
                    skillingDungeonNotes = newNotes,
                    unlockedDungeons = newUnlocked,
                    expeditionPityRuns = newPityRuns,
                )
            } else {
                currentFlags.copy(expeditionPityRuns = newPityRuns)
            }
        }

        var noteLines: List<String> = emptyList()
        var unlockMessage: String? = null
        if (localNotesFound > 0 && dungeonData != null) {
            val revealed = dungeonData.noteTexts.take(localNewCount.coerceAtMost(dungeonData.noteTexts.size))
                .mapIndexed { i, text -> GameStrings.skillingDungeonNote(context, session.activityKey, i, text) }
            val newlyRevealedTexts = revealed.drop(localOldCount.coerceAtMost(revealed.size))
            noteLines = newlyRevealedTexts
            unlockMessage = localUnlockMsg
        }
        return ExpeditionResult(petFoundName, noteLines, unlockMessage)
    }
}

// ---------------------------------------------------------------------------
// Derived helpers (pure, used by HomeScreen + HomeViewModel)
// ---------------------------------------------------------------------------

fun combatLevelFrom(levels: Map<String, Int>): Int {
    val atk = levels[Skills.ATTACK]    ?: 1
    val str = levels[Skills.STRENGTH]  ?: 1
    val ran = levels[Skills.RANGED]    ?: 1
    val mag = levels[Skills.MAGIC]     ?: 1
    val def = levels[Skills.DEFENSE]   ?: 1
    val hp  = levels[Skills.HITPOINTS] ?: 1
    // 2.0, not 2: integer division would drop the half level an odd atk+str kept under
    // the old 0.325 * (atk + str) formula, lowering some melee players' combat level.
    return (0.65 * maxOf((atk + str) / 2.0, ran.toDouble(), mag.toDouble()) + (def + hp) * 0.25).toInt().coerceAtLeast(1)
}

// Sums only canonical skills: saves from before v1.1.5 can carry a stale "combat"
// entry (old combat-quest claims), which must not inflate the total.
fun totalLevelFrom(levels: Map<String, Int>): Int =
    levels.filterKeys { it in Skills.ALL }.values.sum()

/** Infer the combat style used in a session from XP distribution. */
private val COLLECT_GATHERING_SKILLS = setOf(Skills.MINING, Skills.WOODCUTTING, Skills.FISHING, Skills.AGILITY)
private val COLLECT_CRAFTING_SKILLS  = setOf(Skills.SMITHING, Skills.COOKING, Skills.FLETCHING, Skills.CRAFTING, Skills.HERBLORE, Skills.FIREMAKING, Skills.RUNECRAFTING, Skills.CONSTRUCTION)

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

/**
 * True if [session]'s relevant level (combat level for boss/combat/tower, the specific
 * skill's level otherwise) hasn't dropped below what it was when the session started
 * ([SkillSession.levelAtStart]). False means it dropped mid-session — almost certainly via
 * a prestige — and the session pays out with its XP zeroed (loot, coins, kills, and quest
 * progress are kept; only the pre-prestige XP is forfeited, so prestige timing can't be
 * used to cheese re-leveling). This is a regression check, not a difficulty/unlock gate:
 * a session that was legitimately allowed to start (e.g. an under-levelled Tower floor
 * pushed via gear) always stays eligible on its own.
 */
fun isSkillSessionStillEligible(
    session: SkillSession,
    currentLevels: Map<String, Int>,
    gameData: GameDataRepository,
): Boolean {
    val currentLevel = when (session.skillName) {
        "boss", "combat", "tower" -> combatLevelFrom(currentLevels)
        "expedition" -> gameData.skillingDungeons[session.activityKey]?.skill?.let { currentLevels[it] } ?: 1
        else -> currentLevels[session.skillName] ?: 1
    }
    return currentLevel >= session.levelAtStart
}

