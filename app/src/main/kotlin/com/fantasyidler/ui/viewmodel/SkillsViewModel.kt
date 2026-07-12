package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.json.AgilityCourseData
import com.fantasyidler.data.json.BoneData
import com.fantasyidler.data.json.FishData
import com.fantasyidler.data.json.LogData
import com.fantasyidler.data.json.OreData
import com.fantasyidler.data.json.RuneData
import com.fantasyidler.data.json.ThievingNpcData
import com.fantasyidler.data.json.TreeData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.ChurchRepository
import com.fantasyidler.repository.FarmingRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.GuildRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.DailyQuestRepository
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.WeeklyQuestRepository
import com.fantasyidler.repository.QueuedSessionStarter
import com.fantasyidler.repository.SeasonalEventRepository
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.simulator.ThievingSimulator
import com.fantasyidler.simulator.XpTable
import com.fantasyidler.util.toolEfficiency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import javax.inject.Inject
import android.content.Context
import com.fantasyidler.R
import com.fantasyidler.util.GameStrings
import dagger.hilt.android.qualifiers.ApplicationContext

// ---------------------------------------------------------------------------
// UI State
// ---------------------------------------------------------------------------



data class SessionResult(
    val skillName: String,
    val xpGained: Long,
    val itemsGained: Map<String, Int>,
    /** New levels reached during the session, ascending. */
    val levelUps: List<Int>,
)

data class SkillsUiState(
    val skillLevels: Map<String, Int> = emptyMap(),
    val skillXp: Map<String, Long> = emptyMap(),
    val activeSession: SkillSession? = null,
    val isLoading: Boolean = true,
    /** Non-null while the activity selection bottom sheet is open. */
    val sheetSkill: SheetState? = null,
    /** Non-null while a "start session" is in progress (shows loading). */
    val startingSession: Boolean = false,
    /** One-shot event message to display as a snackbar. Consumed by the UI. */
    val snackbarMessage: String? = null,
    /** Non-null when a new pet was found; drives the pet-found dialog. Consumed by the UI. */
    val petFoundName: String? = null,
    /** Non-null after a session is collected — drives the result sheet. Consumed by the UI. */
    val sessionResult: SessionResult? = null,
    val anySessionActive: Boolean = false,
    val queueSize: Int = 0,
    val maxQueueSize: Int = 3,
    val miningEfficiency: Float = 1.0f,
    val woodcuttingEfficiency: Float = 1.0f,
    val fishingEfficiency: Float = 1.0f,
    val farmingEfficiency: Float = 1.0f,
    val firemakingEfficiency: Float = 1.0f,
    val smithingEfficiency: Float = 1.0f,
    val agilityEfficiency: Float = 1.0f,
    val cookingEfficiency: Float = 1.0f,
    val cropsReadyCount: Int = 0,
    val xpBonusMult: Float = 1.0f,
    val sessionDurationMs: Long = 0L,
    /** Actual per-log burn duration, tinderbox tier bonus applied. Keyed by log key. */
    val firemakingPerLogMs: Map<String, Long> = emptyMap(),
    val skillPrestige: Map<String, Int> = emptyMap(),
    val inventory: Map<String, Int> = emptyMap(),
    val petBoostBySkill: Map<String, Int> = emptyMap(),
    val activeQuests: Map<String, List<QuestIndicator>> = emptyMap(),
)

sealed class SheetState {
    data class Mining(val ores: Map<String, OreData>) : SheetState()
    data class Woodcutting(val trees: Map<String, TreeData>) : SheetState()
    data class Fishing(val fish: Map<String, FishData>) : SheetState()
    data class Agility(val courses: Map<String, AgilityCourseData>) : SheetState()
    /** availableLogs = logs the player currently has in inventory */
    data class Firemaking(val availableLogs: Map<String, LogData>, val questFills: Map<String, List<QuestFillSuggestion>> = emptyMap()) : SheetState()
    data class Runecrafting(
        val availableRunes: Map<String, RuneData>,
        val essenceQty: Int,
        val questFills: Map<String, List<QuestFillSuggestion>> = emptyMap(),
    ) : SheetState()
    /** Bones the player currently has in inventory, with their counts. */
    data class Prayer(
        val availableBones: Map<String, BoneData>,
        val inventory: Map<String, Int>,
        val questFills: List<QuestFillSuggestion> = emptyList(),
    ) : SheetState()
    /** Opens the inline craft sheet for one of the instant-craft skills. */
    data class Crafting(val skillName: String) : SheetState()
    /** NPCs available to pickpocket, filtered to player's thieving level. */
    data class Thieving(val npcs: Map<String, ThievingNpcData>) : SheetState()
    data object Mercantile : SheetState()
    data object Farming : SheetState()
    data object ComingSoon : SheetState()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class SkillsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val questRepo: QuestRepository,
    private val guildRepo: GuildRepository,
    private val farmingRepo: FarmingRepository,
    private val queuedSessionStarter: QueuedSessionStarter,
    private val dailyQuestRepo: DailyQuestRepository,
    private val weeklyQuestRepo: WeeklyQuestRepository,
    private val seasonalEventRepo: SeasonalEventRepository,
    private val json: Json,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillsUiState())

    val uiState: StateFlow<SkillsUiState> = combine(
        playerRepo.playerFlow,
        sessionRepo.activeSessionFlow,
        _uiState,
        farmingRepo.observePatches(),
        questRepo.observeProgress(),
    ) { player, session, extra, patches, questProgress ->
        val nonCombatSession = session?.takeIf { it.skillName != "combat" }
        val now = System.currentTimeMillis()
        val cropsReady = patches.count { it.remainingMs(gameData.crops, now) <= 0 }
        if (player == null) {
            extra.copy(isLoading = true, activeSession = nonCombatSession, anySessionActive = session != null, cropsReadyCount = cropsReady)
        } else {
            val levels:   Map<String, Int>     = json.decodeFromString(player.skillLevels)
            val xp:       Map<String, Long>    = json.decodeFromString(player.skillXp)
            val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
            val inv:      Map<String, Int>     = json.decodeFromString(player.inventory)
            val flags = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
            extra.copy(
                isLoading             = false,
                skillLevels           = levels,
                skillXp               = xp,
                activeSession         = nonCombatSession,
                anySessionActive      = session != null,
                queueSize             = flags.sessionQueue.size,
                maxQueueSize          = playerRepo.maxQueueSize(flags),
                miningEfficiency      = gameData.toolEfficiency(equipped[EquipSlot.PICKAXE],     EquipSlot.PICKAXE,     0),
                woodcuttingEfficiency = gameData.toolEfficiency(equipped[EquipSlot.AXE],         EquipSlot.AXE,         0),
                fishingEfficiency     = gameData.toolEfficiency(equipped[EquipSlot.FISHING_ROD], EquipSlot.FISHING_ROD, 0),
                farmingEfficiency     = gameData.toolEfficiency(equipped[EquipSlot.HOE],         EquipSlot.HOE,         0),
                firemakingEfficiency  = gameData.toolEfficiency(equipped[EquipSlot.TINDERBOX],      EquipSlot.TINDERBOX,      0),
                smithingEfficiency    = gameData.toolEfficiency(equipped[EquipSlot.HAMMER],         EquipSlot.HAMMER,         0),
                agilityEfficiency     = gameData.toolEfficiency(equipped[EquipSlot.GRAPPLING_HOOK], EquipSlot.GRAPPLING_HOOK, 0),
                cookingEfficiency     = gameData.toolEfficiency(equipped[EquipSlot.FRYING_PAN],     EquipSlot.FRYING_PAN,     0),
                xpBonusMult           = (if (flags.xpBoostExpiresAt > System.currentTimeMillis()) 2.0f else 1.0f) * ChurchRepository.xpMultiplier(flags),
                sessionDurationMs     = SkillSimulator.sessionDurationMs(levels[Skills.AGILITY] ?: 1, flags.skillPrestige[Skills.AGILITY] ?: 0),
                firemakingPerLogMs    = gameData.logs.mapValues { (_, log) ->
                    val toolEff = gameData.toolEfficiency(equipped[EquipSlot.TINDERBOX], EquipSlot.TINDERBOX, log.levelRequired)
                    (SkillSimulator.sessionDurationMs(levels[Skills.AGILITY] ?: 1, flags.skillPrestige[Skills.AGILITY] ?: 0) / 60L / toolEff).toLong()
                },
                skillPrestige         = flags.skillPrestige,
                inventory             = inv,
                cropsReadyCount       = cropsReady,
                petBoostBySkill       = (Skills.GATHERING + Skills.CRAFTING_SKILLS + Skills.SUPPORT + listOf(Skills.AGILITY, Skills.SLAYER))
                    .associateWith { key -> petBoostFor(player.pets, key) }
                    .filterValues { it > 0 },
                activeQuests          = computeActiveQuests(questProgress, flags, inv),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SkillsUiState())

    // ------------------------------------------------------------------
    // Activity selection sheet
    // ------------------------------------------------------------------

    fun onSkillTapped(skillKey: String) {
        // Allow the sheet to open even with an active session — the user can queue from the sheet.
        // The authoritative queue/block check happens inside startSession.
        val session = _uiState.value.activeSession

        val state = uiState.value
        val miningLevel   = state.skillLevels[Skills.MINING]      ?: 1
        val wcLevel       = state.skillLevels[Skills.WOODCUTTING]  ?: 1
        val fishingLevel  = state.skillLevels[Skills.FISHING]      ?: 1
        val agilityLevel  = state.skillLevels[Skills.AGILITY]      ?: 1
        val fmLevel       = state.skillLevels[Skills.FIREMAKING]   ?: 1
        val inventory     = state.skillLevels // placeholder — inventory resolved below

        val sheet: SheetState = when (skillKey) {
            Skills.MINING -> SheetState.Mining(
                ores = gameData.ores.filter { (_, ore) -> ore.levelRequired <= miningLevel }
            )
            Skills.WOODCUTTING -> SheetState.Woodcutting(
                trees = gameData.trees.filter { (_, tree) -> tree.levelRequired <= wcLevel }
            )
            Skills.FISHING -> SheetState.Fishing(
                fish = gameData.fish.filter { (_, f) -> f.levelRequired <= fishingLevel }
            )
            Skills.AGILITY -> SheetState.Agility(
                courses = gameData.agilityCourses.filter { (_, c) -> c.levelRequired <= agilityLevel }
            )
            Skills.FIREMAKING -> {
                // Only show logs the player has in inventory
                viewModelScope.launch {
                    val player = playerRepo.getOrCreatePlayer()
                    val inv: Map<String, Int> = json.decodeFromString(player.inventory)
                    val flags = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
                    val questProgress = questRepo.observeProgress().first().associateBy { it.questId }
                    val availableLogs = gameData.logs.filter { (key, log) ->
                        inv.containsKey(key) && log.levelRequired <= fmLevel
                    }
                    val logToAsh = mapOf(
                        "log" to "ashes", "oak_log" to "oak_ashes", "willow_log" to "willow_ashes",
                        "maple_log" to "maple_ashes", "yew_log" to "yew_ashes",
                        "magic_log" to "magic_ashes", "redwood_log" to "redwood_ashes",
                    )
                    val questFills = availableLogs.keys.associateWith { logKey ->
                        computeItemFills(logToAsh[logKey] ?: "ashes", questProgress, flags)
                    }
                    _uiState.update { it.copy(sheetSkill = SheetState.Firemaking(availableLogs, questFills)) }
                }
                return
            }
            Skills.RUNECRAFTING -> {
                viewModelScope.launch {
                    val player = playerRepo.getOrCreatePlayer()
                    val inv: Map<String, Int> = json.decodeFromString(player.inventory)
                    val flags = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
                    val reservedEssence = flags.sessionQueue
                        .filter { it.skillName == Skills.RUNECRAFTING }
                        .sumOf { action -> (gameData.runes[action.activityKey]?.essenceCost ?: 0) * action.qty }
                    val essenceQty = ((inv["rune_essence"] ?: 0) - reservedEssence).coerceAtLeast(0)
                    val rcLevel = state.skillLevels[Skills.RUNECRAFTING] ?: 1
                    val available = gameData.runes.filter { (_, rune) -> rune.levelRequired <= rcLevel }
                    val questProgress2 = questRepo.observeProgress().first().associateBy { it.questId }
                    val questFills = available.keys.associateWith { runeKey ->
                        computeItemFills(runeKey, questProgress2, flags)
                    }
                    _uiState.update { it.copy(sheetSkill = SheetState.Runecrafting(available, essenceQty, questFills)) }
                }
                return
            }
            Skills.PRAYER -> {
                viewModelScope.launch {
                    val player = playerRepo.getOrCreatePlayer()
                    val inv: Map<String, Int> = json.decodeFromString(player.inventory)
                    val flags = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
                    val reserved = reservedQty(flags.sessionQueue, Skills.PRAYER)
                    val effectiveCounts = inv.mapValues { (k, v) -> v - (reserved[k] ?: 0) }
                    val available = gameData.bones
                        .filter { (key, _) -> (effectiveCounts[key] ?: 0) > 0 }
                        .entries.sortedBy { it.value.xpPerBone }
                        .associate { it.key to it.value }
                    val questProgress = questRepo.observeProgress().first().associateBy { it.questId }
                    val questFills = computePrayerFills(questProgress, flags)
                    _uiState.update {
                        it.copy(sheetSkill = SheetState.Prayer(available, effectiveCounts.filterKeys { k -> k in gameData.bones }, questFills))
                    }
                }
                return
            }
            Skills.SMITHING,
            Skills.COOKING,
            Skills.FLETCHING,
            Skills.CRAFTING,
            Skills.HERBLORE,
            Skills.CONSTRUCTION -> SheetState.Crafting(skillKey)
            Skills.THIEVING -> {
                val thievingLevel = state.skillLevels[Skills.THIEVING] ?: 1
                SheetState.Thieving(
                    npcs = gameData.thievingNpcs.filter { (_, npc) -> npc.levelRequired <= thievingLevel }
                )
            }
            Skills.MERCANTILE -> SheetState.Mercantile
            Skills.FARMING    -> SheetState.Farming
            else             -> SheetState.ComingSoon
        }
        _uiState.update { it.copy(sheetSkill = sheet) }
    }

    fun dismissSheet() = _uiState.update { it.copy(sheetSkill = null) }

    // ------------------------------------------------------------------
    // Session start
    // ------------------------------------------------------------------

    fun startMiningSession(oreKey: String) = startSession(Skills.MINING, oreKey) {
        val oreData = gameData.ores[oreKey]
            ?: throw IllegalArgumentException("Unknown ore: $oreKey")
        val player  = playerRepo.getOrCreatePlayer()
        val levels: Map<String, Int>  = json.decodeFromString(player.skillLevels)
        val xpMap:  Map<String, Long> = json.decodeFromString(player.skillXp)
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
        val flags: PlayerFlags = json.decodeFromString(player.flags)
        val (petKey, petChance) = petDropParams(Skills.MINING)

        SkillSimulator.simulateMining(
            oreKey           = oreKey,
            oreData          = oreData,
            gems             = gameData.gems,
            startXp          = xpMap[Skills.MINING] ?: 0L,
            agilityLevel     = levels[Skills.AGILITY] ?: 1,
            agilityPrestige  = flags.skillPrestige[Skills.AGILITY] ?: 0,
            petBoostPct      = petBoostFor(player.pets, Skills.MINING),
            toolEfficiency   = gameData.toolEfficiency(equipped[EquipSlot.PICKAXE], EquipSlot.PICKAXE, oreData.levelRequired),
            petDropKey       = petKey,
            petDropChance    = petChance,
        )
    }

    fun startWoodcuttingSession(treeKey: String) = startSession(Skills.WOODCUTTING, treeKey) {
        val treeData = gameData.trees[treeKey]
            ?: throw IllegalArgumentException("Unknown tree: $treeKey")
        val player  = playerRepo.getOrCreatePlayer()
        val levels: Map<String, Int>  = json.decodeFromString(player.skillLevels)
        val xpMap:  Map<String, Long> = json.decodeFromString(player.skillXp)
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
        val flags: PlayerFlags = json.decodeFromString(player.flags)
        val (petKey, petChance) = petDropParams(Skills.WOODCUTTING)

        SkillSimulator.simulateWoodcutting(
            treeData         = treeData,
            startXp          = xpMap[Skills.WOODCUTTING] ?: 0L,
            agilityLevel     = levels[Skills.AGILITY] ?: 1,
            agilityPrestige  = flags.skillPrestige[Skills.AGILITY] ?: 0,
            petBoostPct      = petBoostFor(player.pets, Skills.WOODCUTTING),
            toolEfficiency   = gameData.toolEfficiency(equipped[EquipSlot.AXE], EquipSlot.AXE, treeData.levelRequired),
            petDropKey       = petKey,
            petDropChance    = petChance,
        )
    }

    fun startAgilitySession(courseKey: String) = startSession(Skills.AGILITY, courseKey) {
        val courseData = gameData.agilityCourses[courseKey]
            ?: throw IllegalArgumentException("Unknown course: $courseKey")
        val player  = playerRepo.getOrCreatePlayer()
        val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
        val flags: PlayerFlags = json.decodeFromString(player.flags)
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)

        SkillSimulator.simulateAgility(
            courseData      = courseData,
            startXp         = (json.decodeFromString<Map<String, Long>>(player.skillXp))[Skills.AGILITY] ?: 0L,
            agilityLevel    = levels[Skills.AGILITY] ?: 1,
            agilityPrestige = flags.skillPrestige[Skills.AGILITY] ?: 0,
            petBoostPct     = petBoostFor(player.pets, Skills.AGILITY),
            toolEfficiency  = gameData.toolEfficiency(equipped[EquipSlot.GRAPPLING_HOOK], EquipSlot.GRAPPLING_HOOK, courseData.levelRequired),
        )
    }

    fun startFiremakingSession(logKey: String, qty: Int) {
        viewModelScope.launch {
            val player = playerRepo.getOrCreatePlayer()
            val inv: Map<String, Int> = json.decodeFromString(player.inventory)
            val available = inv[logKey] ?: 0
            if (available <= 0) {
                _uiState.update { it.copy(snackbarMessage = context.getString(R.string.skill_no_logs)) }
                return@launch
            }
            val actualQty = qty.coerceIn(1, available)
            val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            val agility = levels[Skills.AGILITY] ?: 1
            val flags = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
            val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
            val logData = gameData.logs[logKey]
            val toolEff = gameData.toolEfficiency(equipped[EquipSlot.TINDERBOX], EquipSlot.TINDERBOX, logData?.levelRequired ?: 0)
            val perLogMs = (SkillSimulator.sessionDurationMs(agility, flags.skillPrestige[Skills.AGILITY] ?: 0) / 60L / toolEff).toLong()
            val logXp = logData?.xpPerLog?.toLong() ?: 0L
            val xpQueueMult = (if (flags.xpBoostExpiresAt > System.currentTimeMillis()) 2.0 else 1.0) * ChurchRepository.xpMultiplier(flags)
            val action = QueuedAction(
                skillName           = Skills.FIREMAKING,
                activityKey         = logKey,
                skillDisplayName    = "Firemaking",
                qty                 = actualQty,
                estimatedXpGain     = (actualQty.toLong() * logXp * xpQueueMult * toolEff).toLong(),
                estimatedDurationMs = actualQty.toLong() * perLogMs,
            )

            if (sessionRepo.getActiveSession() != null) {
                val enqueued = playerRepo.enqueueAction(action)
                if (enqueued) playerRepo.consumeItems(mapOf(logKey to actualQty))
                if (enqueued) queuedSessionStarter.startNextQueued()
                _uiState.update {
                    it.copy(
                        snackbarMessage = if (enqueued) context.getString(R.string.slayer_queue_added, "Firemaking") else context.getString(R.string.slayer_queue_full),
                    )
                }
                return@launch
            }

            playerRepo.consumeItems(mapOf(logKey to actualQty))
            _uiState.update { it.copy(startingSession = true, sheetSkill = null) }
            try {
                playerRepo.enqueueAction(action)
                queuedSessionStarter.startNextQueued()
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = context.getString(R.string.skill_session_start_failed, e.message ?: "")) }
            } finally {
                _uiState.update { it.copy(startingSession = false) }
            }
        }
    }

    fun startRunecraftingSession(runeKey: String, qty: Int, catalystKey: String? = null) {
        viewModelScope.launch {
            val runeData = gameData.runes[runeKey] ?: return@launch
            val player   = playerRepo.getOrCreatePlayer()
            val inv: Map<String, Int> = json.decodeFromString(player.inventory)
            val availableEssence = inv["rune_essence"] ?: 0
            if (availableEssence < runeData.essenceCost * qty) {
                _uiState.update { it.copy(snackbarMessage = context.getString(R.string.skill_not_enough_rune_essence)) }
                return@launch
            }

            if (sessionRepo.getActiveSession() != null) {
                val actDisplay = runeKey.replace('_', ' ').replaceFirstChar { it.uppercase() }
                val levels     = json.decodeFromString<Map<String, Int>>(player.skillLevels)
                val agility    = levels[Skills.AGILITY]      ?: 1
                val rcLevel    = levels[Skills.RUNECRAFTING]  ?: 1
                val rcFlags = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
                val perItemMs  = SkillSimulator.sessionDurationMs(agility, rcFlags.skillPrestige[Skills.AGILITY] ?: 0) / 60
                val ashBon     = catalystKey?.let { ashRuneBonusForKey(it) } ?: 0
                val mult       = when { rcLevel >= 75 -> 3; rcLevel >= 50 -> 2; else -> 1 } + ashBon
                val xpQueueMult = (if (rcFlags.xpBoostExpiresAt > System.currentTimeMillis()) 2.0 else 1.0) * ChurchRepository.xpMultiplier(rcFlags)
                val ashCost = if (catalystKey != null) (qty + 9) / 10 else 0
                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(
                        skillName           = Skills.RUNECRAFTING,
                        activityKey         = runeKey,
                        skillDisplayName    = "Runecrafting",
                        qty                 = qty,
                        estimatedXpGain     = (qty.toLong() * (runeData.xpPerRune * mult).toLong() * xpQueueMult).toLong(),
                        estimatedDurationMs = qty.toLong() * perItemMs,
                        catalystKey         = catalystKey,
                        catalystQty         = ashCost,
                    )
                )
                if (enqueued) {
                    playerRepo.consumeItems(mapOf("rune_essence" to runeData.essenceCost * qty))
                    if (catalystKey != null) {
                        playerRepo.consumeItems(mapOf(catalystKey to ashCost))
                    }
                    queuedSessionStarter.startNextQueued()
                }
                _uiState.update {
                    it.copy(
                        snackbarMessage = if (enqueued) context.getString(R.string.skill_added_to_queue_activity, "Runecrafting", actDisplay) else context.getString(R.string.slayer_queue_full),
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(startingSession = true, sheetSkill = null) }
            try {
                val xpMap:   Map<String, Long> = json.decodeFromString(player.skillXp)
                val levels:  Map<String, Int>  = json.decodeFromString(player.skillLevels)
                val agilityLevel = levels[Skills.AGILITY] ?: 1
                val rcActFlags = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }

                // Compute totals without building one frame per essence — stays within
                // Android's 2 MB CursorWindow per-row limit for large qty values.
                val ashBonus = catalystKey?.let { ashRuneBonusForKey(it) } ?: 0
                val startXp = xpMap[Skills.RUNECRAFTING] ?: 0L
                var currentXp   = startXp
                var totalRunes  = 0
                var totalXpGain = 0
                for (i in 1..qty) {
                    val level = XpTable.levelForXp(currentXp)
                    val multiplier = when {
                        level >= 75 -> 3
                        level >= 50 -> 2
                        else        -> 1
                    } + ashBonus
                    val xpGain = (runeData.xpPerRune * multiplier).toInt()
                    totalRunes  += multiplier
                    totalXpGain += xpGain
                    currentXp   += xpGain
                }
                val frames = listOf(
                    SessionFrame(
                        minute      = 1,
                        xpGain      = totalXpGain,
                        xpBefore    = startXp,
                        xpAfter     = currentXp,
                        levelBefore = XpTable.levelForXp(startXp),
                        levelAfter  = XpTable.levelForXp(currentXp),
                        items       = mapOf(runeKey to totalRunes),
                        leveledUp   = XpTable.levelForXp(currentXp) > XpTable.levelForXp(startXp),
                        kills       = qty,
                    )
                )

                val perEssenceMs = SkillSimulator.sessionDurationMs(agilityLevel, rcActFlags.skillPrestige[Skills.AGILITY] ?: 0) / 60
                val framesJson   = json.encodeToString(
                    json.serializersModule.serializer<List<SessionFrame>>(),
                    frames,
                )
                playerRepo.consumeItems(mapOf("rune_essence" to runeData.essenceCost * qty))
                val ashCost = if (catalystKey != null) (qty + 9) / 10 else 0
                if (catalystKey != null) {
                    playerRepo.consumeItems(mapOf(catalystKey to ashCost))
                }
                sessionRepo.startSession(
                    skillName        = Skills.RUNECRAFTING,
                    activityKey      = runeKey,
                    frames           = framesJson,
                    durationMs       = qty.toLong() * perEssenceMs,
                    skillDisplayName = "Runecrafting",
                    catalystKey      = catalystKey,
                    catalystQty      = ashCost,
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = context.getString(R.string.skill_session_start_failed, e.message ?: "")) }
            } finally {
                _uiState.update { it.copy(startingSession = false) }
            }
        }
    }

    fun startPrayerSession(boneKey: String, qty: Int) {
        viewModelScope.launch {
            val bone   = gameData.bones[boneKey] ?: return@launch
            val player = playerRepo.getOrCreatePlayer()
            val inv: Map<String, Int> = json.decodeFromString(player.inventory)
            val available = inv[boneKey] ?: 0
            if (available < qty) {
                _uiState.update { it.copy(snackbarMessage = context.getString(R.string.skill_not_enough_item, bone.displayName)) }
                return@launch
            }

            if (sessionRepo.getActiveSession() != null) {
                val agility   = (json.decodeFromString<Map<String, Int>>(player.skillLevels))[Skills.AGILITY] ?: 1
                val prayerFlags = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
                val perBoneMs = SkillSimulator.sessionDurationMs(agility, prayerFlags.skillPrestige[Skills.AGILITY] ?: 0) / 60
                val xpQueueMult = (if (prayerFlags.xpBoostExpiresAt > System.currentTimeMillis()) 2.0 else 1.0) * ChurchRepository.xpMultiplier(prayerFlags)
                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(
                        skillName           = Skills.PRAYER,
                        activityKey         = boneKey,
                        skillDisplayName    = "Prayer",
                        qty                 = qty,
                        estimatedXpGain     = (qty.toLong() * bone.xpPerBone.toLong() * xpQueueMult).toLong(),
                        estimatedDurationMs = qty.toLong() * perBoneMs,
                    )
                )
                if (enqueued) playerRepo.consumeItems(mapOf(boneKey to qty))
                if (enqueued) queuedSessionStarter.startNextQueued()
                _uiState.update {
                    it.copy(
                        snackbarMessage = if (enqueued) context.getString(R.string.skill_added_to_queue_activity, "Prayer", bone.displayName) else context.getString(R.string.slayer_queue_full),
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(startingSession = true, sheetSkill = null) }
            try {
                val xpMap:   Map<String, Long> = json.decodeFromString(player.skillXp)
                val levels:  Map<String, Int>  = json.decodeFromString(player.skillLevels)
                val currentXp  = xpMap[Skills.PRAYER] ?: 0L
                val levelBefore = XpTable.levelForXp(currentXp)
                val totalXpGain = (qty * bone.xpPerBone).toInt()
                val xpAfter     = currentXp + totalXpGain
                val levelAfter  = XpTable.levelForXp(xpAfter)
                val frames = listOf(
                    SessionFrame(
                        minute      = 1,
                        xpGain      = totalXpGain,
                        xpBefore    = currentXp,
                        xpAfter     = xpAfter,
                        levelBefore = levelBefore,
                        levelAfter  = levelAfter,
                        items       = emptyMap(),
                        leveledUp   = levelAfter > levelBefore,
                        kills       = qty, // total bones buried (for quest tracking + consumption)
                    )
                )

                val agilityLevel = levels[Skills.AGILITY] ?: 1
                val prayerActFlags = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
                val perBoneMs    = SkillSimulator.sessionDurationMs(agilityLevel, prayerActFlags.skillPrestige[Skills.AGILITY] ?: 0) / 60
                val framesJson   = json.encodeToString(
                    json.serializersModule.serializer<List<SessionFrame>>(),
                    frames,
                )
                playerRepo.consumeItems(mapOf(boneKey to qty))
                sessionRepo.startSession(
                    skillName        = Skills.PRAYER,
                    activityKey      = boneKey,
                    frames           = framesJson,
                    durationMs       = qty.toLong() * perBoneMs,
                    skillDisplayName = "Prayer",
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = context.getString(R.string.skill_session_start_failed, e.message ?: "")) }
            } finally {
                _uiState.update { it.copy(startingSession = false) }
            }
        }
    }

    fun startFishingSession(fishKey: String) = startSession(Skills.FISHING, activityKey = fishKey) {
        val fishData = gameData.fish[fishKey]
            ?: throw IllegalArgumentException("Unknown fish: $fishKey")
        val player  = playerRepo.getOrCreatePlayer()
        val levels: Map<String, Int>  = json.decodeFromString(player.skillLevels)
        val xpMap:  Map<String, Long> = json.decodeFromString(player.skillXp)
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
        val flags: PlayerFlags = json.decodeFromString(player.flags)
        val (petKey, petChance) = petDropParams(Skills.FISHING)

        SkillSimulator.simulateFishing(
            fishKey          = fishKey,
            fishData         = fishData,
            startXp          = xpMap[Skills.FISHING] ?: 0L,
            agilityLevel     = levels[Skills.AGILITY] ?: 1,
            agilityPrestige  = flags.skillPrestige[Skills.AGILITY] ?: 0,
            petBoostPct      = petBoostFor(player.pets, Skills.FISHING),
            rodEfficiency    = gameData.toolEfficiency(equipped[EquipSlot.FISHING_ROD], EquipSlot.FISHING_ROD, fishData.levelRequired),
            petDropKey       = petKey,
            petDropChance    = petChance,
            fishingSkillData = gameData.fishingSkillData,
        )
    }

    fun startThievingSession(npcKey: String) {
        val npc = gameData.thievingNpcs[npcKey] ?: return
        val (petKey, petChance) = petDropParams(Skills.THIEVING)
        viewModelScope.launch {
            if (sessionRepo.getActiveSession() != null) {
                val player = playerRepo.getOrCreatePlayer()
                val agility = (json.decodeFromString<Map<String, Int>>(player.skillLevels))[Skills.AGILITY] ?: 1
                val thievingFlags = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(
                        skillName           = Skills.THIEVING,
                        activityKey         = npcKey,
                        skillDisplayName    = "Thieving",
                        estimatedDurationMs = SkillSimulator.sessionDurationMs(agility, thievingFlags.skillPrestige[Skills.AGILITY] ?: 0),
                    )
                )
                if (enqueued) queuedSessionStarter.startNextQueued()
                _uiState.update {
                    it.copy(
                        sheetSkill = null,
                        snackbarMessage = if (enqueued)
                            context.getString(R.string.skill_added_to_queue_activity, "Thieving", npc.displayName)
                        else
                            context.getString(R.string.slayer_queue_full),
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(startingSession = true, sheetSkill = null) }
            try {
                val player = playerRepo.getOrCreatePlayer()
                val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
                val xpMap: Map<String, Long> = json.decodeFromString(player.skillXp)
                val flags: PlayerFlags = json.decodeFromString(player.flags)
                val result = ThievingSimulator.simulate(
                    npcKey          = npcKey,
                    npc             = npc,
                    startXp         = xpMap[Skills.THIEVING] ?: 0L,
                    thievingLevel   = levels[Skills.THIEVING] ?: 1,
                    agilityLevel    = levels[Skills.AGILITY] ?: 1,
                    agilityPrestige = flags.skillPrestige[Skills.AGILITY] ?: 0,
                    petBoostPct     = petBoostFor(player.pets, Skills.THIEVING),
                    petDropKey      = petKey,
                    petDropChance   = petChance,
                )
                val framesJson = json.encodeToString(
                    json.serializersModule.serializer<List<SessionFrame>>(),
                    result.frames,
                )
                sessionRepo.startSession(
                    skillName        = Skills.THIEVING,
                    activityKey      = npcKey,
                    frames           = framesJson,
                    durationMs       = result.durationMs,
                    skillDisplayName = "Thieving",
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = context.getString(R.string.skill_session_start_failed, e.message ?: "")) }
            } finally {
                _uiState.update { it.copy(startingSession = false) }
            }
        }
    }

    private fun startSession(
        skillName: String,
        activityKey: String,
        simulate: suspend () -> SkillSimulator.Result,
    ) {
        viewModelScope.launch {
            if (sessionRepo.getActiveSession() != null) {
                val displayName  = skillName.replaceFirstChar { it.uppercase() }
                val actDisplay   = activityKey.replace('_', ' ').replaceFirstChar { it.uppercase() }
                val player       = playerRepo.getOrCreatePlayer()
                val agility      = (json.decodeFromString<Map<String, Int>>(player.skillLevels))[Skills.AGILITY] ?: 1
                val gatherFlags = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
                val xpQueueMult = (if (gatherFlags.xpBoostExpiresAt > System.currentTimeMillis()) 2.0 else 1.0) * ChurchRepository.xpMultiplier(gatherFlags)
                val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
                val petBoostPct = petBoostFor(player.pets, skillName)
                val rawXp = when (skillName) {
                    Skills.MINING      -> SkillSimulator.estimateGatheringXp(
                        gameData.ores[activityKey]?.xpPerOre ?: 0,
                        gameData.toolEfficiency(equipped[EquipSlot.PICKAXE], EquipSlot.PICKAXE),
                    )
                    Skills.WOODCUTTING -> SkillSimulator.estimateGatheringXp(
                        gameData.trees[activityKey]?.xpPerLog ?: 0,
                        gameData.toolEfficiency(equipped[EquipSlot.AXE], EquipSlot.AXE),
                    )
                    Skills.FISHING     -> SkillSimulator.estimateGatheringXp(
                        gameData.fish[activityKey]?.xpPerCatch ?: 0,
                        gameData.toolEfficiency(equipped[EquipSlot.FISHING_ROD], EquipSlot.FISHING_ROD),
                    )
                    Skills.AGILITY     -> {
                        val course = gameData.agilityCourses[activityKey]
                        SkillSimulator.estimateAgilityXp(course?.xpPerSuccess ?: 0, course?.levelRequired ?: 1, agility)
                    }
                    else               -> 0L
                }
                val petBoostedXp = if (petBoostPct > 0) (rawXp * (1.0 + petBoostPct / 100.0)).toLong() else rawXp
                val estimatedXpGain = (petBoostedXp * xpQueueMult).toLong()
                val agilityPrestige = gatherFlags.skillPrestige[Skills.AGILITY] ?: 0
                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(
                        skillName           = skillName,
                        activityKey         = activityKey,
                        skillDisplayName    = displayName,
                        estimatedXpGain     = estimatedXpGain,
                        estimatedDurationMs = SkillSimulator.sessionDurationMs(agility, agilityPrestige),
                    )
                )
                if (enqueued) queuedSessionStarter.startNextQueued()
                _uiState.update {
                    it.copy(
                        sheetSkill = null,
                        snackbarMessage = if (enqueued) {
                            if (activityKey.isNotEmpty())
                                context.getString(R.string.skill_added_to_queue_activity, displayName, actDisplay)
                            else
                                context.getString(R.string.slayer_queue_added, displayName)
                        } else {
                            context.getString(R.string.slayer_queue_full)
                        },
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(startingSession = true, sheetSkill = null) }
            try {
                val result = simulate()
                val framesJson = json.encodeToString(
                    json.serializersModule.serializer<List<com.fantasyidler.data.model.SessionFrame>>(),
                    result.frames,
                )
                sessionRepo.startSession(
                    skillName        = skillName,
                    activityKey      = activityKey,
                    frames           = framesJson,
                    durationMs       = result.durationMs,
                    skillDisplayName = skillName.replaceFirstChar { it.uppercase() },
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(snackbarMessage = context.getString(R.string.skill_session_start_failed, e.message ?: ""))
                }
            } finally {
                _uiState.update { it.copy(startingSession = false) }
            }
        }
    }

    // ------------------------------------------------------------------
    // Session collection + abandon
    // ------------------------------------------------------------------

    fun collectSession() {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSession() ?: return@launch
            if (!session.completed && System.currentTimeMillis() < session.endsAt) return@launch

            val frames: List<com.fantasyidler.data.model.SessionFrame> =
                json.decodeFromString(session.frames)

            val currentLevels = playerRepo.getSkillLevels()
            if (!isSkillSessionStillEligible(session, currentLevels, gameData)) {
                refundVoidedSessionMaterials(session, frames, playerRepo, gameData)
                sessionRepo.abandonSession(session.sessionId)
                _uiState.update {
                    it.copy(snackbarMessage = context.getString(
                        R.string.skill_session_voided_prestige,
                        GameStrings.skillName(context, session.skillName),
                    ))
                }
                queuedSessionStarter.startNextQueued()
                return@launch
            }

            val totalXp  = frames.sumOf { it.xpGain.toLong() }
            // Display value only — mirrors the boost/blessing/prestige math applySessionResults()
            // already applies internally when crediting the player's actual skill XP below.
            val flags: PlayerFlags = json.decodeFromString(playerRepo.getOrCreatePlayer().flags)
            val boostActive = flags.xpBoostExpiresAt > System.currentTimeMillis()
            val blessingMult = ChurchRepository.xpMultiplier(flags)
            val prestigeLevel = flags.skillPrestige[session.skillName] ?: 0
            val baseDisplayXp = ((if (boostActive) totalXp * 2 else totalXp) * blessingMult).toLong()
            val displayXp = if (prestigeLevel > 0) (baseDisplayXp * (1.0 + prestigeLevel * 0.10)).toLong() else baseDisplayXp
            val allItems = mutableMapOf<String, Int>()
            val levelUps = mutableListOf<Int>()
            for (frame in frames) {
                for ((item, qty) in frame.items) {
                    allItems[item] = (allItems[item] ?: 0) + qty
                }
                if (frame.leveledUp) levelUps.add(frame.levelAfter)
            }

            // Separate pet drops from regular loot
            val petIds = gameData.pets.keys
            val petDrops   = allItems.filterKeys { it in petIds }
            val regularItems = allItems.filterKeys { it !in petIds }

            // Thieving coins go to balance rather than inventory
            val thievingCoins = if (session.skillName == Skills.THIEVING)
                (regularItems["coins"] ?: 0).toLong() else 0L
            val inventoryItems = if (session.skillName == Skills.THIEVING)
                regularItems.filterKeys { it != "coins" } else regularItems

            playerRepo.applySessionResults(
                skillName   = session.skillName,
                xpGained    = totalXp,
                itemsGained = inventoryItems,
            )
            if (thievingCoins > 0) playerRepo.addCoins(thievingCoins)

            // Record quest / daily / guild progress
            val gatheringSkills = setOf(Skills.MINING, Skills.WOODCUTTING, Skills.FISHING, Skills.AGILITY)
            val craftingSkills  = setOf(Skills.SMITHING, Skills.COOKING, Skills.FLETCHING, Skills.CRAFTING,
                Skills.HERBLORE, Skills.FIREMAKING, Skills.RUNECRAFTING, Skills.CONSTRUCTION)
            when (session.skillName) {
                in gatheringSkills -> {
                    questRepo.recordGathering(session.skillName, regularItems)
                    playerRepo.recordDailyGathering(regularItems)
                    seasonalEventRepo.recordGathering(regularItems)
                    when (session.skillName) {
                        Skills.AGILITY -> guildRepo.recordGuildSessions()
                        else           -> guildRepo.recordGuildGathering(session.skillName, regularItems)
                    }
                }
                in craftingSkills -> {
                    questRepo.recordCrafting(session.skillName, regularItems)
                    playerRepo.recordDailyCrafting(regularItems)
                    guildRepo.recordGuildCrafting(session.skillName, regularItems)
                    seasonalEventRepo.recordCrafting(regularItems)
                }
                Skills.THIEVING -> {
                    val successCount = frames.count { it.success }
                    questRepo.recordThieving(session.activityKey, successCount, inventoryItems)
                    guildRepo.recordGuildThieving(session.activityKey, successCount)
                }
                Skills.PRAYER -> {
                    val buried = frames.sumOf { it.kills }
                    val isAshSession = gameData.bones[session.activityKey]?.isAsh == true
                    if (!isAshSession) {
                        questRepo.recordBuried(buried)
                        guildRepo.recordGuildPrayer(buried)
                    }
                    playerRepo.recordDailyPrayer(buried)
                }
                Skills.MERCANTILE -> {
                    val coins = regularItems["_coins"]?.toLong() ?: 0L
                    guildRepo.recordGuildTrade(coins)
                }
            }

            // Handle pet drops
            var petFoundName: String? = null
            for ((petId, _) in petDrops) {
                val petData = gameData.pets[petId] ?: continue
                val added = playerRepo.addPetIfNew(petId, petData.boostPercent)
                if (added) petFoundName = petData.displayName
            }

            sessionRepo.deleteSession(session.sessionId)

            _uiState.update {
                it.copy(
                    sessionResult = SessionResult(
                        skillName   = session.skillName,
                        xpGained    = displayXp,
                        itemsGained = regularItems,
                        levelUps    = levelUps,
                    ),
                    petFoundName = petFoundName,
                )
            }

            // Auto-start next queued session, if any
            queuedSessionStarter.startNextQueued()
        }
    }

    fun resultConsumed() = _uiState.update { it.copy(sessionResult = null) }

    fun abandonSession() {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSession() ?: return@launch
            val frames: List<com.fantasyidler.data.model.SessionFrame> = json.decodeFromString(session.frames)
            if (session.skillName == Skills.MERCANTILE) {
                val coinCost = gameData.tradeRoutes.firstOrNull { it.id == session.activityKey }?.coinCost?.toLong() ?: 0L
                if (coinCost > 0) playerRepo.addCoins(coinCost)
            } else {
                playerSessionMaterials(session.skillName, session.activityKey, frames.sumOf { it.kills }, gameData)
                    ?.let { playerRepo.addItems(it) }
            }
            if (session.catalystKey != null && session.catalystQty > 0) {
                playerRepo.addItem(session.catalystKey, session.catalystQty)
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

    fun snackbarConsumed() = _uiState.update { it.copy(snackbarMessage = null) }
    fun petDialogConsumed() = _uiState.update { it.copy(petFoundName = null) }

    fun prestigeSkill(skillName: String) {
        viewModelScope.launch {
            val activeSession = sessionRepo.getActiveSession()
            val abandonedSession = activeSession?.takeIf { it.skillName == skillName }
            if (abandonedSession != null) {
                val frames: List<SessionFrame> = json.decodeFromString(abandonedSession.frames)
                playerSessionMaterials(abandonedSession.skillName, abandonedSession.activityKey, frames.sumOf { it.kills }, gameData)
                    ?.let { playerRepo.addItems(it) }
                if (abandonedSession.catalystKey != null && abandonedSession.catalystQty > 0) {
                    playerRepo.addItem(abandonedSession.catalystKey, abandonedSession.catalystQty)
                }
                sessionRepo.abandonSession(abandonedSession.sessionId)
            }
            val evicted = playerRepo.evictQueueForSkill(skillName)
            for (action in evicted) {
                if (action.coinRefund > 0) playerRepo.addCoins(action.coinRefund)
                playerSessionMaterials(action.skillName, action.activityKey, action.qty, gameData)
                    ?.let { playerRepo.addItems(it) }
                if (action.catalystKey != null && action.catalystQty > 0) {
                    playerRepo.addItem(action.catalystKey, action.catalystQty)
                }
            }
            playerRepo.prestigeSkill(skillName)
            if (abandonedSession != null) queuedSessionStarter.startNextQueued()
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Sums qty already committed to the queue for each activityKey under [skillName]. */
    private fun reservedQty(queue: List<QueuedAction>, skillName: String): Map<String, Int> =
        queue.filter { it.skillName == skillName }
             .groupingBy { it.activityKey }
             .fold(0) { acc, a -> acc + a.qty }

    /** Returns (petId, dropChancePerFrame) for gathering skill pets (1/1000 per frame). */
    private fun petDropParams(skillKey: String): Pair<String?, Double> {
        val pet = gameData.pets.values.firstOrNull { it.boostedSkill == skillKey } ?: return null to 0.0
        return pet.id to (1.0 / 1000.0)
    }

    /**
     * Looks up the pet XP boost percentage for [skillKey].
     * Pets store their boosted_skill as a JSON string; we decode inline.
     */
    private fun ashRuneBonusForKey(ashKey: String): Int = when (ashKey) {
        "ashes"         -> 1
        "oak_ashes"     -> 2
        "willow_ashes"  -> 3
        "maple_ashes"   -> 4
        "yew_ashes"     -> 5
        "magic_ashes"   -> 6
        "redwood_ashes" -> 7
        else            -> 0
    }

    private fun computeItemFills(
        itemKey: String,
        questProgress: Map<String, com.fantasyidler.data.model.QuestProgress>,
        flags: PlayerFlags,
    ): List<QuestFillSuggestion> {
        val fills = mutableListOf<QuestFillSuggestion>()

        for ((id, quest) in gameData.quests) {
            if (quest.type != "craft") continue
            if (quest.target != itemKey) continue
            val prog = questProgress[id]
            if (prog?.completed == true) continue
            val remaining = quest.amount - (prog?.progress ?: 0)
            if (remaining <= 0) continue
            val prereqDone = quest.requiresPrevious == null ||
                    questProgress[quest.requiresPrevious]?.completed == true
            if (prereqDone) fills += QuestFillSuggestion(quest.name, remaining)
        }

        val completedIds = questProgress.entries.filter { it.value.completed }.map { it.key }.toSet()
        for ((id, quest) in gameData.guildQuests) {
            if (quest.type != "craft") continue
            if (quest.target != itemKey) continue
            val prog = questProgress[id]
            if (prog?.completed == true) continue
            val rep = flags.guildReputation[quest.guild] ?: 0L
            if (guildRepo.guildLevel(quest.guild, rep, completedIds) < quest.guildLevelRequired) continue
            val effectiveAmount = guildRepo.effectiveQuestAmountFromFlags(quest, flags)
            val remaining = effectiveAmount - (prog?.progress ?: 0)
            if (remaining > 0) fills += QuestFillSuggestion(quest.name, remaining)
        }

        for (daily in dailyQuestRepo.getActiveDailyQuests(flags)) {
            if (daily.claimed) continue
            if (daily.template.type != "craft") continue
            if (daily.template.target != itemKey) continue
            val remaining = daily.template.amount - daily.progress
            if (remaining > 0) fills += QuestFillSuggestion(context.getString(R.string.quest_fill_daily), remaining)
        }

        for (weekly in weeklyQuestRepo.getActiveWeeklyQuests(flags)) {
            if (weekly.claimed) continue
            if (weekly.template.type != "craft") continue
            if (weekly.template.target != itemKey) continue
            val remaining = weekly.template.amount - weekly.progress
            if (remaining > 0) fills += QuestFillSuggestion(context.getString(R.string.quest_fill_weekly), remaining)
        }

        val guildPool = gameData.guildDailyPool.associateBy { it.id }
        val activeGuildIds = flags.guildDailyIds.filter { it !in flags.guildDailyClaimed }
        for (id in activeGuildIds) {
            val template = guildPool[id] ?: continue
            if (template.type != "craft") continue
            if (template.target != itemKey) continue
            val progress = flags.guildDailyProgress[id] ?: 0
            val remaining = template.amount - progress
            if (remaining > 0) fills += QuestFillSuggestion(context.getString(R.string.quest_fill_guild), remaining)
        }

        return fills.sortedBy { it.qty }
    }

    private fun computePrayerFills(
        questProgress: Map<String, com.fantasyidler.data.model.QuestProgress>,
        flags: PlayerFlags,
    ): List<QuestFillSuggestion> {
        val fills = mutableListOf<QuestFillSuggestion>()

        for ((id, quest) in gameData.quests) {
            if (quest.type != "prayer") continue
            val prog = questProgress[id]
            if (prog?.completed == true) continue
            val remaining = quest.amount - (prog?.progress ?: 0)
            if (remaining <= 0) continue
            val prereqDone = quest.requiresPrevious == null ||
                    questProgress[quest.requiresPrevious]?.completed == true
            if (prereqDone) fills += QuestFillSuggestion(quest.name, remaining)
        }

        val completedIds = questProgress.entries.filter { it.value.completed }.map { it.key }.toSet()
        for ((id, quest) in gameData.guildQuests) {
            if (quest.type != "prayer") continue
            val prog = questProgress[id]
            if (prog?.completed == true) continue
            val rep = flags.guildReputation[quest.guild] ?: 0L
            if (guildRepo.guildLevel(quest.guild, rep, completedIds) < quest.guildLevelRequired) continue
            val effectiveAmount = guildRepo.effectiveQuestAmountFromFlags(quest, flags)
            val remaining = effectiveAmount - (prog?.progress ?: 0)
            if (remaining > 0) fills += QuestFillSuggestion(quest.name, remaining)
        }

        for (daily in dailyQuestRepo.getActiveDailyQuests(flags)) {
            if (daily.claimed) continue
            if (daily.template.type != "prayer") continue
            val remaining = daily.template.amount - daily.progress
            if (remaining > 0) fills += QuestFillSuggestion(context.getString(R.string.quest_fill_daily), remaining)
        }

        for (weekly in weeklyQuestRepo.getActiveWeeklyQuests(flags)) {
            if (weekly.claimed) continue
            if (weekly.template.type != "prayer") continue
            val remaining = weekly.template.amount - weekly.progress
            if (remaining > 0) fills += QuestFillSuggestion(context.getString(R.string.quest_fill_weekly), remaining)
        }

        val guildPool = gameData.guildDailyPool.associateBy { it.id }
        val activeGuildIds = flags.guildDailyIds.filter { it !in flags.guildDailyClaimed }
        for (id in activeGuildIds) {
            val template = guildPool[id] ?: continue
            if (template.type != "prayer") continue
            val progress = flags.guildDailyProgress[id] ?: 0
            val remaining = template.amount - progress
            if (remaining > 0) fills += QuestFillSuggestion(context.getString(R.string.quest_fill_guild), remaining)
        }

        return fills.sortedBy { it.qty }
    }

    private fun computeActiveQuests(
        questProgress: List<com.fantasyidler.data.model.QuestProgress>,
        flags: PlayerFlags,
        inventory: Map<String, Int>,
    ): Map<String, List<QuestIndicator>> {
        val result = mutableMapOf<String, MutableList<QuestIndicator>>()
        val progressById = questProgress.associateBy { it.questId }

        val activeDailies = dailyQuestRepo.getActiveDailyQuests(flags).filter { !it.claimed }
        val activeWeeklies = weeklyQuestRepo.getActiveWeeklyQuests(flags).filter { !it.claimed }
        val guildPool = gameData.guildDailyPool.associateBy { it.id }
        val activeGuildDailyIds = flags.guildDailyIds.filter { it !in flags.guildDailyClaimed }
        val completedIds = progressById.entries.filter { it.value.completed }.map { it.key }.toSet()

        fun addIndicator(key: String, skill: String, category: QuestCategory, remaining: Int) {
            val isCompletable = when (skill) {
                Skills.RUNECRAFTING -> {
                    val rune = gameData.runes[key]
                    val cost = rune?.essenceCost ?: 1
                    val essence = inventory["rune_essence"] ?: 0
                    (essence / cost) >= remaining
                }
                Skills.FIREMAKING -> {
                    val logKey = when (key) {
                        "ashes" -> "log"
                        "oak_ashes" -> "oak_log"
                        "willow_ashes" -> "willow_log"
                        "maple_ashes" -> "maple_log"
                        "yew_ashes" -> "yew_log"
                        "magic_ashes" -> "magic_log"
                        "redwood_ashes" -> "redwood_log"
                        else -> key
                    }
                    val logs = inventory[logKey] ?: 0
                    logs >= remaining
                }
                Skills.PRAYER -> {
                    val bones = inventory[key] ?: 0
                    bones >= remaining
                }
                else -> true
            }
            result.getOrPut(key) { mutableListOf() }.add(QuestIndicator(category, isCompletable))
        }

        fun checkAndAdd(questType: String, questSkill: String, questTarget: String, questAmount: Int, questProgressVal: Int, category: QuestCategory) {
            val remaining = questAmount - questProgressVal
            if (remaining <= 0) return

            when (questType) {
                "gather" -> {
                    addIndicator(questTarget, questSkill, category, remaining)
                }
                "gather_any" -> {
                    when (questSkill) {
                        Skills.MINING -> gameData.ores.keys.forEach { addIndicator(it, questSkill, category, remaining) }
                        Skills.WOODCUTTING -> gameData.trees.keys.forEach { addIndicator(it, questSkill, category, remaining) }
                        Skills.FISHING -> gameData.fish.keys.forEach { addIndicator(it, questSkill, category, remaining) }
                    }
                }
                "pickpocket" -> {
                    addIndicator(questTarget, questSkill, category, remaining)
                }
                "pickpocket_any" -> {
                    gameData.thievingNpcs.keys.forEach { addIndicator(it, questSkill, category, remaining) }
                }
                "sessions" -> {
                    if (questSkill == Skills.AGILITY) {
                        addIndicator(questTarget, questSkill, category, remaining)
                    }
                }
                "burn" -> {
                    val logToAsh = mapOf(
                        "log" to "ashes", "oak_log" to "oak_ashes", "willow_log" to "willow_ashes",
                        "maple_log" to "maple_ashes", "yew_log" to "yew_ashes",
                        "magic_log" to "magic_ashes", "redwood_log" to "redwood_ashes"
                    )
                    val ashKey = logToAsh[questTarget] ?: questTarget
                    addIndicator(ashKey, questSkill, category, remaining)
                }
                "burn_any" -> {
                    if (questSkill == Skills.FIREMAKING) {
                        gameData.logs.keys.forEach { logKey ->
                            val logToAsh = mapOf(
                                "log" to "ashes", "oak_log" to "oak_ashes", "willow_log" to "willow_ashes",
                                "maple_log" to "maple_ashes", "yew_log" to "yew_ashes",
                                "magic_log" to "magic_ashes", "redwood_log" to "redwood_ashes"
                            )
                            val ashKey = logToAsh[logKey] ?: logKey
                            addIndicator(ashKey, questSkill, category, remaining)
                        }
                    }
                }
                "craft" -> {
                    if (questSkill == Skills.RUNECRAFTING) {
                        addIndicator(questTarget, questSkill, category, remaining)
                    }
                }
                "craft_any" -> {
                    if (questSkill == Skills.RUNECRAFTING) {
                        gameData.runes.keys.forEach { addIndicator(it, questSkill, category, remaining) }
                    }
                }
                "prayer" -> {
                    if (questSkill == Skills.PRAYER) {
                        if (questTarget.isNotEmpty()) {
                            addIndicator(questTarget, questSkill, category, remaining)
                        } else {
                            gameData.bones.keys.forEach { addIndicator(it, questSkill, category, remaining) }
                        }
                    }
                }
            }
        }

        for ((id, quest) in gameData.quests) {
            val prog = progressById[id]
            if (prog?.completed == true) continue
            val prereqDone = quest.requiresPrevious == null ||
                    progressById[quest.requiresPrevious]?.completed == true
            if (!prereqDone) continue

            checkAndAdd(quest.type, quest.skill, quest.target, quest.amount, prog?.progress ?: 0, QuestCategory.MAIN)
        }

        for ((id, quest) in gameData.guildQuests) {
            val prog = progressById[id]
            if (prog?.completed == true) continue
            val rep = flags.guildReputation[quest.guild] ?: 0L
            if (guildRepo.guildLevel(quest.guild, rep, completedIds) < quest.guildLevelRequired) continue

            val effectiveAmount = guildRepo.effectiveQuestAmountFromFlags(quest, flags)
            checkAndAdd(quest.type, quest.guild, quest.target, effectiveAmount, prog?.progress ?: 0, QuestCategory.MAIN)
        }

        for (daily in activeDailies) {
            checkAndAdd(daily.template.type, daily.template.skill, daily.template.target, daily.template.amount, daily.progress, QuestCategory.DAILY)
        }

        for (weekly in activeWeeklies) {
            checkAndAdd(weekly.template.type, weekly.template.skill, weekly.template.target, weekly.template.amount, weekly.progress, QuestCategory.DAILY)
        }

        for (id in activeGuildDailyIds) {
            val template = guildPool[id] ?: continue
            val progress = flags.guildDailyProgress[id] ?: 0
            checkAndAdd(template.type, template.guild, template.target, template.amount, progress, QuestCategory.DAILY)
        }

        return result
    }

    private fun petBoostFor(petsJson: String, skillKey: String): Int {
        val pets = try {
            json.decodeFromString<List<com.fantasyidler.data.model.OwnedPet>>(petsJson)
        } catch (_: Exception) {
            return 0
        }
        return pets.sumOf { pet ->
            val pd = gameData.pets[pet.id]
            if (pd != null && (pd.boostedSkill == skillKey || pd.boostedSkill == "all")) pd.boostPercent else 0
        }
    }
}

/** XP progress fraction (0.0–1.0) for display in XP bars. */
fun xpProgressFraction(xp: Long): Float = XpTable.progressFraction(xp)

/** Formatted level string for display. */
fun levelDisplay(xp: Long): Int = XpTable.levelForXp(xp)

/** XP needed to reach the next level, or 0 if already at max level. */
fun xpToNextLevel(xp: Long): Long = XpTable.xpToNextLevel(xp)

/** Total XP required for the next level (absolute threshold). */
fun nextLevelThreshold(xp: Long): Long = XpTable.nextLevelThreshold(xp)
