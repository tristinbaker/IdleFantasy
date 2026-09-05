package com.fantasyidler.ui.viewmodel

import com.fantasyidler.util.withAppLocale

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
import com.fantasyidler.repository.BoostRepository
import com.fantasyidler.repository.ChurchRepository
import com.fantasyidler.repository.blessingPrayerCapeMult
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
import com.fantasyidler.repository.TownRepository
import com.fantasyidler.simulator.PrestigeBoosts
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.simulator.ThievingSimulator
import com.fantasyidler.simulator.XpTable
import com.fantasyidler.util.toolEfficiency
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.random.Random
import javax.inject.Inject
import android.content.Context
import com.fantasyidler.R
import com.fantasyidler.data.model.OwnedPet
import com.fantasyidler.data.model.QuestProgress
import com.fantasyidler.util.GameStrings
import dagger.hilt.android.qualifiers.ApplicationContext

// ---------------------------------------------------------------------------
// UI State
// ---------------------------------------------------------------------------



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
    val thievingEfficiency: Float = 1.0f,
    val cookingEfficiency: Float = 1.0f,
    val cropsReadyCount: Int = 0,
    val xpBonusMult: Float = 1.0f,
    val petBoosts: Map<String, Int> = emptyMap(),
    val sessionDurationMs: Long = 0L,
    /** Actual per-log burn duration, tinderbox tier bonus applied. Keyed by log key. */
    val firemakingPerLogMs: Map<String, Long> = emptyMap(),
    val skillPrestige: Map<String, Int> = emptyMap(),
    /** Skills at 99+ where another prestige still earns points or an XP tier. */
    val prestigeReadySkills: Set<String> = emptySet(),
    val ironman: Boolean = false,
    val showPrestigeNotifications: Boolean = true,
    val inventory: Map<String, Int> = emptyMap(),
    val petBoostBySkill: Map<String, Int> = emptyMap(),
    val activeQuests: Map<String, List<QuestIndicator>> = emptyMap(),
    /** Timed (daily/weekly/guild daily) quest indicators per skill, for the overview rows. */
    val timedQuestsBySkill: Map<String, List<QuestIndicator>> = emptyMap(),
    val showSessionEndTime: Boolean = true,
    /** Guild dailies plus daily/weekly quests for each sheet skill, keyed by skill (guild keys match skill keys). */
    val sheetQuests: Map<String, List<SheetQuestSummary>> = emptyMap(),
    val seasonalEventEmoji: String? = null,
)

enum class SheetQuestSource { GUILD, DAILY, WEEKLY, SEASONAL }

data class SheetQuestSummary(
    val questId: String,
    val questName: String,
    /** Guild key for guild dailies; the quest's skill for daily/weekly quests. */
    val guild: String,
    val type: String,
    val target: String,
    val progress: Int,
    val amount: Int,
    val claimed: Boolean,
    val source: SheetQuestSource,
    /** Raw English description, used as the fallback when no localized objective exists. */
    val description: String = "",
    /** Guild dailies only: true once the guild's rank is capped, so dailies no longer advance tier progression. */
    val guildMaxed: Boolean = false,
    /** False when the player's skill level no longer meets the target activity's requirement (e.g. after prestige). */
    val meetsLevel: Boolean = true,
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
    private val boostRepo: BoostRepository,
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
    private val townRepo: TownRepository,
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
            val activeQuests = computeActiveQuests(questProgress, flags, inv)
            val activeEvent = seasonalEventRepo.activeEvent()
            val seasonalEmoji = if (activeEvent != null && "bounty" in activeEvent.pillars) activeEvent.iconEmoji else null
            extra.copy(
                isLoading             = false,
                skillLevels           = levels,
                skillXp               = xp,
                activeSession         = nonCombatSession,
                anySessionActive      = session != null,
                queueSize             = flags.sessionQueue.size,
                maxQueueSize          = playerRepo.maxQueueSize(flags),
                miningEfficiency      = gameData.toolEfficiency(equipped[EquipSlot.PICKAXE],     EquipSlot.PICKAXE,     0, skillLevels = levels, heirloomXp = flags.heirloomXp) * boostRepo.toolEffMultiplier(Skills.MINING, flags, levels[Skills.MINING] ?: 1),
                woodcuttingEfficiency = gameData.toolEfficiency(equipped[EquipSlot.AXE],         EquipSlot.AXE,         0, skillLevels = levels, heirloomXp = flags.heirloomXp) * boostRepo.toolEffMultiplier(Skills.WOODCUTTING, flags, levels[Skills.WOODCUTTING] ?: 1),
                fishingEfficiency     = gameData.toolEfficiency(equipped[EquipSlot.FISHING_ROD], EquipSlot.FISHING_ROD, 0, skillLevels = levels, heirloomXp = flags.heirloomXp) * boostRepo.toolEffMultiplier(Skills.FISHING, flags, levels[Skills.FISHING] ?: 1),
                farmingEfficiency     = gameData.toolEfficiency(equipped[EquipSlot.HOE],         EquipSlot.HOE,         0, skillLevels = levels, heirloomXp = flags.heirloomXp),
                firemakingEfficiency  = gameData.toolEfficiency(equipped[EquipSlot.TINDERBOX],      EquipSlot.TINDERBOX,      0, skillLevels = levels, heirloomXp = flags.heirloomXp),
                smithingEfficiency    = gameData.toolEfficiency(equipped[EquipSlot.HAMMER],         EquipSlot.HAMMER,         0, skillLevels = levels, heirloomXp = flags.heirloomXp),
                agilityEfficiency     = gameData.toolEfficiency(equipped[EquipSlot.GRAPPLING_HOOK], EquipSlot.GRAPPLING_HOOK, 0, skillLevels = levels, heirloomXp = flags.heirloomXp),
                thievingEfficiency    = gameData.toolEfficiency(equipped[EquipSlot.LOCKPICK],       EquipSlot.LOCKPICK,       0, skillLevels = levels, heirloomXp = flags.heirloomXp),
                cookingEfficiency     = gameData.toolEfficiency(equipped[EquipSlot.FRYING_PAN],     EquipSlot.FRYING_PAN,     0, skillLevels = levels, heirloomXp = flags.heirloomXp),
                xpBonusMult           = if (flags.ironman) 1.0f
                                        else (if (flags.xpBoostExpiresAt > System.currentTimeMillis()) 2.0f else 1.0f) * ChurchRepository.xpMultiplier(flags, blessingPrayerCapeMult(flags, equipped, inv.keys, gameData)),
                petBoosts             = listOf(Skills.MINING, Skills.WOODCUTTING, Skills.FISHING, Skills.AGILITY)
                    .associateWith { if (flags.ironman) 0 else petBoostFor(player.pets, it) },
                sessionDurationMs     = SkillSimulator.sessionDurationMs(levels[Skills.AGILITY] ?: 1, boostRepo.sessionFloorReductionMin(flags), townRepo.playerSessionDurationMultiplier(flags)),
                firemakingPerLogMs    = gameData.logs.mapValues { (_, log) ->
                    val toolEff = gameData.toolEfficiency(equipped[EquipSlot.TINDERBOX], EquipSlot.TINDERBOX, log.levelRequired, skillLevels = levels, heirloomXp = flags.heirloomXp)
                    (SkillSimulator.sessionDurationMs(levels[Skills.AGILITY] ?: 1, boostRepo.sessionFloorReductionMin(flags), townRepo.playerSessionDurationMultiplier(flags)) / 60L / toolEff).toLong()
                },
                skillPrestige         = flags.skillPrestige,
                prestigeReadySkills   = Skills.ALL.filterTo(mutableSetOf()) {
                    (levels[it] ?: 1) >= 99 && PrestigeBoosts.prestigeHasReward(gameData.prestigeTrees, flags, it)
                },
                ironman               = flags.ironman,
                showPrestigeNotifications = flags.showPrestigeNotifications,
                inventory             = inv,
                cropsReadyCount       = cropsReady,
                petBoostBySkill       = (Skills.GATHERING + Skills.CRAFTING_SKILLS + Skills.SUPPORT + listOf(Skills.AGILITY, Skills.SLAYER))
                    .associateWith { key -> if (flags.ironman) 0 else petBoostFor(player.pets, key) }
                    .filterValues { it > 0 },
                activeQuests          = activeQuests,
                timedQuestsBySkill    = activeQuests.entries
                    .groupBy({ it.key.substringBefore(':') }, { it.value })
                    .mapValues { (_, lists) ->
                        lists.flatten()
                            .filter {
                                it.category == QuestCategory.DAILY || it.category == QuestCategory.WEEKLY ||
                                it.category == QuestCategory.SEASONAL || it.category == QuestCategory.GUILD_DAILY
                            }
                            // "any"-target quests add one indicator per matching activity
                            // (e.g. every buriable bone), so collapse back to one per quest.
                            .groupBy { it.questId }
                            .map { (_, group) -> group.first().copy(isCompletable = group.any { it.isCompletable }) }
                    }
                    .filterValues { it.isNotEmpty() },
                showSessionEndTime    = flags.showSessionEndTime,
                sheetQuests           = computeSheetQuests(questProgress, flags, levels, inv),
                seasonalEventEmoji    = seasonalEmoji,
            )
        }
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SkillsUiState())

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
                    // Level-gated only: logs the player has run out of stay visible (dimmed,
                    // "0 in inventory") instead of vanishing — a disappearing row reads like
                    // the log type became unburnable (issue #1358).
                    val availableLogs = gameData.logs.filter { (_, log) ->
                        log.levelRequired <= fmLevel
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
            floorReductionMin  = boostRepo.sessionFloorReductionMin(flags),
            petBoostPct      = boostRepo.boostedPetPct(Skills.MINING, flags, petBoostFor(player.pets, Skills.MINING, flags.ironman)),
            toolEfficiency   = gameData.toolEfficiency(equipped[EquipSlot.PICKAXE], EquipSlot.PICKAXE, oreData.levelRequired, skillLevels = levels, heirloomXp = flags.heirloomXp) * boostRepo.toolEffMultiplier(Skills.MINING, flags, levels[Skills.MINING] ?: 1),
            petDropKey       = petKey,
            petDropChance    = petChance,
            chronosMultiplier = townRepo.playerSessionDurationMultiplier(flags),
            gemChanceMult    = boostRepo.bonusRollMultiplier(Skills.MINING, flags),
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
            floorReductionMin  = boostRepo.sessionFloorReductionMin(flags),
            petBoostPct      = boostRepo.boostedPetPct(Skills.WOODCUTTING, flags, petBoostFor(player.pets, Skills.WOODCUTTING, flags.ironman)),
            toolEfficiency   = gameData.toolEfficiency(equipped[EquipSlot.AXE], EquipSlot.AXE, treeData.levelRequired, skillLevels = levels, heirloomXp = flags.heirloomXp) * boostRepo.toolEffMultiplier(Skills.WOODCUTTING, flags, levels[Skills.WOODCUTTING] ?: 1),
            petDropKey       = petKey,
            petDropChance    = petChance,
            chronosMultiplier = townRepo.playerSessionDurationMultiplier(flags),
        )
    }

    fun startAgilitySession(courseKey: String, count: Int = 1) = startSession(Skills.AGILITY, courseKey, count) {
        val courseData = gameData.agilityCourses[courseKey]
            ?: throw IllegalArgumentException("Unknown course: $courseKey")
        val player  = playerRepo.getOrCreatePlayer()
        val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
        val flags: PlayerFlags = json.decodeFromString(player.flags)
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)

        val (petKey, petChance) = petDropParams(Skills.AGILITY)
        SkillSimulator.simulateAgility(
            courseData      = courseData,
            startXp         = (json.decodeFromString<Map<String, Long>>(player.skillXp))[Skills.AGILITY] ?: 0L,
            agilityLevel    = levels[Skills.AGILITY] ?: 1,
            floorReductionMin = boostRepo.sessionFloorReductionMin(flags),
            petBoostPct     = boostRepo.boostedPetPct(Skills.AGILITY, flags, petBoostFor(player.pets, Skills.AGILITY, flags.ironman)),
            toolEfficiency  = gameData.toolEfficiency(equipped[EquipSlot.GRAPPLING_HOOK], EquipSlot.GRAPPLING_HOOK, courseData.levelRequired, skillLevels = levels, heirloomXp = flags.heirloomXp),
            petDropKey      = petKey,
            petDropChance   = petChance,
            chronosMultiplier = townRepo.playerSessionDurationMultiplier(flags),
        )
    }

    fun startFiremakingSession(logKey: String, qty: Int) {
        viewModelScope.launch {
            val player = playerRepo.getOrCreatePlayer()
            val inv: Map<String, Int> = json.decodeFromString(player.inventory)
            val available = inv[logKey] ?: 0
            if (available <= 0) {
                _uiState.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.skill_no_logs)) }
                return@launch
            }
            val actualQty = qty.coerceIn(1, available)
            val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            val agility = levels[Skills.AGILITY] ?: 1
            val flags = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
            val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
            val logData = gameData.logs[logKey]
            val toolEff = gameData.toolEfficiency(equipped[EquipSlot.TINDERBOX], EquipSlot.TINDERBOX, logData?.levelRequired ?: 0, skillLevels = levels, heirloomXp = flags.heirloomXp)
            val perLogMs = (SkillSimulator.sessionDurationMs(agility, boostRepo.sessionFloorReductionMin(flags), townRepo.playerSessionDurationMultiplier(flags)) / 60L / toolEff).toLong()
            val logXp = logData?.xpPerLog?.toLong() ?: 0L
            val xpQueueMult = if (flags.ironman) 1.0 else (if (flags.xpBoostExpiresAt > System.currentTimeMillis()) 2.0 else 1.0) * ChurchRepository.xpMultiplier(flags, blessingPrayerCapeMult(player, flags, gameData))
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
                        snackbarMessage = if (enqueued) context.withAppLocale().getString(R.string.slayer_queue_added, "Firemaking") else context.withAppLocale().getString(R.string.slayer_queue_full),
                    )
                }
                return@launch
            }

            playerRepo.consumeItems(mapOf(logKey to actualQty))
            _uiState.update { it.copy(startingSession = true) }
            try {
                playerRepo.enqueueAction(action)
                queuedSessionStarter.startNextQueued()
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.skill_session_start_failed, e.message ?: "")) }
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
                _uiState.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.skill_not_enough_rune_essence)) }
                return@launch
            }
            if (catalystKey != null && (inv[catalystKey] ?: 0) < (qty + 9) / 10) {
                _uiState.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.skill_not_enough_materials)) }
                return@launch
            }

            if (sessionRepo.getActiveSession() != null) {
                val actDisplay = GameStrings.itemName(context, runeKey)
                val levels     = json.decodeFromString<Map<String, Int>>(player.skillLevels)
                val agility    = levels[Skills.AGILITY]      ?: 1
                val rcLevel    = levels[Skills.RUNECRAFTING]  ?: 1
                val rcFlags = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
                val perItemMs  = SkillSimulator.sessionDurationMs(agility, boostRepo.sessionFloorReductionMin(rcFlags), townRepo.playerSessionDurationMultiplier(rcFlags)) / 60
                val ashBon     = catalystKey?.let { ashRuneBonusForKey(it) } ?: 0
                val mult       = when { rcLevel >= 75 -> 3; rcLevel >= 50 -> 2; else -> 1 } + ashBon
                val xpQueueMult = if (rcFlags.ironman) 1.0 else (if (rcFlags.xpBoostExpiresAt > System.currentTimeMillis()) 2.0 else 1.0) * ChurchRepository.xpMultiplier(rcFlags, blessingPrayerCapeMult(player, rcFlags, gameData))
                val ashCost = if (catalystKey != null) (qty + 9) / 10 else 0
                val saveChance = townRepo.secondaryMaterialSaveChance(rcFlags)
                val consumedAshCost = if (catalystKey != null) applyQtyPreservation(ashCost, saveChance) else 0
                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(
                        skillName           = Skills.RUNECRAFTING,
                        activityKey         = runeKey,
                        skillDisplayName    = "Runecrafting",
                        qty                 = qty,
                        estimatedXpGain     = (qty.toLong() * (runeData.xpPerRune * mult).toLong() * xpQueueMult).toLong(),
                        estimatedDurationMs = qty.toLong() * perItemMs,
                        catalystKey         = catalystKey,
                        catalystQty         = consumedAshCost,
                    )
                )
                if (enqueued) {
                    playerRepo.consumeItems(mapOf("rune_essence" to runeData.essenceCost * qty))
                    if (catalystKey != null && consumedAshCost > 0) {
                        playerRepo.consumeItems(mapOf(catalystKey to consumedAshCost))
                    }
                    queuedSessionStarter.startNextQueued()
                }
                _uiState.update {
                    it.copy(
                        snackbarMessage = if (enqueued) context.withAppLocale().getString(R.string.skill_added_to_queue_activity, GameStrings.skillName(context, Skills.RUNECRAFTING), actDisplay) else context.withAppLocale().getString(R.string.slayer_queue_full),
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(startingSession = true) }
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

                val perEssenceMs = SkillSimulator.sessionDurationMs(agilityLevel, boostRepo.sessionFloorReductionMin(rcActFlags), townRepo.playerSessionDurationMultiplier(rcActFlags)) / 60
                val framesJson   = json.encodeToString(
                    json.serializersModule.serializer<List<SessionFrame>>(),
                    frames,
                )
                playerRepo.consumeItems(mapOf("rune_essence" to runeData.essenceCost * qty))
                val ashCost = if (catalystKey != null) (qty + 9) / 10 else 0
                val saveChance = townRepo.secondaryMaterialSaveChance(rcActFlags)
                val consumedAshCost = if (catalystKey != null) applyQtyPreservation(ashCost, saveChance) else 0
                if (catalystKey != null && consumedAshCost > 0) {
                    playerRepo.consumeItems(mapOf(catalystKey to consumedAshCost))
                }
                sessionRepo.startSession(
                    skillName        = Skills.RUNECRAFTING,
                    activityKey      = runeKey,
                    frames           = framesJson,
                    durationMs       = qty.toLong() * perEssenceMs,
                    skillDisplayName = "Runecrafting",
                    catalystKey      = catalystKey,
                    catalystQty      = consumedAshCost,
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.skill_session_start_failed, e.message ?: "")) }
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
                _uiState.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.skill_not_enough_item, bone.displayName)) }
                return@launch
            }

            if (sessionRepo.getActiveSession() != null) {
                val agility   = (json.decodeFromString<Map<String, Int>>(player.skillLevels))[Skills.AGILITY] ?: 1
                val prayerFlags = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
                val perBoneMs = SkillSimulator.sessionDurationMs(agility, boostRepo.sessionFloorReductionMin(prayerFlags), townRepo.playerSessionDurationMultiplier(prayerFlags)) / 60
                val xpQueueMult = if (prayerFlags.ironman) 1.0 else (if (prayerFlags.xpBoostExpiresAt > System.currentTimeMillis()) 2.0 else 1.0) * ChurchRepository.xpMultiplier(prayerFlags, blessingPrayerCapeMult(player, prayerFlags, gameData))
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
                        snackbarMessage = if (enqueued) context.withAppLocale().getString(R.string.skill_added_to_queue_activity, GameStrings.skillName(context, Skills.PRAYER), GameStrings.itemName(context, boneKey)) else context.withAppLocale().getString(R.string.slayer_queue_full),
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(startingSession = true) }
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
                val perBoneMs    = SkillSimulator.sessionDurationMs(agilityLevel, boostRepo.sessionFloorReductionMin(prayerActFlags), townRepo.playerSessionDurationMultiplier(prayerActFlags)) / 60
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
                _uiState.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.skill_session_start_failed, e.message ?: "")) }
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
            floorReductionMin  = boostRepo.sessionFloorReductionMin(flags),
            petBoostPct      = boostRepo.boostedPetPct(Skills.FISHING, flags, petBoostFor(player.pets, Skills.FISHING, flags.ironman)),
            rodEfficiency    = gameData.toolEfficiency(equipped[EquipSlot.FISHING_ROD], EquipSlot.FISHING_ROD, fishData.levelRequired, skillLevels = levels, heirloomXp = flags.heirloomXp) * boostRepo.toolEffMultiplier(Skills.FISHING, flags, levels[Skills.FISHING] ?: 1),
            petDropKey       = petKey,
            petDropChance    = petChance,
            fishingSkillData = gameData.fishingSkillData,
            chronosMultiplier = townRepo.playerSessionDurationMultiplier(flags),
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
                val levels = json.decodeFromString<Map<String, Int>>(player.skillLevels)
                val thievingLevel = levels[Skills.THIEVING] ?: 1
                val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
                val lockpickEff = gameData.toolEfficiency(equipped[EquipSlot.LOCKPICK], EquipSlot.LOCKPICK, npc.levelRequired, skillLevels = levels, heirloomXp = thievingFlags.heirloomXp)
                val successChance = (0.40 + (thievingLevel - npc.levelRequired) * 0.02 * lockpickEff +
                    boostRepo.thievingSuccessBonus(thievingFlags)).coerceIn(0.10, 0.98)
                val petBoostPct = petBoostFor(player.pets, Skills.THIEVING, thievingFlags.ironman)
                val petBoostedXp = if (petBoostPct > 0) (npc.baseXp * (1.0 + petBoostPct / 100.0)).toInt() else npc.baseXp
                val expectedXp = 60.0 * (successChance / (2.0 - successChance)) * petBoostedXp
                val xpQueueMult = if (thievingFlags.ironman) 1.0 else (if (thievingFlags.xpBoostExpiresAt > System.currentTimeMillis()) 2.0 else 1.0) * ChurchRepository.xpMultiplier(thievingFlags, blessingPrayerCapeMult(player, thievingFlags, gameData))
                val prestigeMult = 1.0 + boostRepo.prestigeXpPct(Skills.THIEVING, thievingFlags) / 100.0
                val estimatedXpGain = (expectedXp * xpQueueMult * prestigeMult).toLong()

                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(
                        skillName           = Skills.THIEVING,
                        activityKey         = npcKey,
                        skillDisplayName    = "Thieving",
                        estimatedXpGain     = estimatedXpGain,
                        estimatedDurationMs = SkillSimulator.sessionDurationMs(agility, boostRepo.sessionFloorReductionMin(thievingFlags), townRepo.playerSessionDurationMultiplier(thievingFlags)),
                    )
                )
                if (enqueued) queuedSessionStarter.startNextQueued()
                _uiState.update {
                    it.copy(
                        snackbarMessage = if (enqueued)
                            context.withAppLocale().getString(R.string.skill_added_to_queue_activity, GameStrings.skillName(context, Skills.THIEVING), GameStrings.thievingNpcName(context, npcKey))
                        else
                            context.withAppLocale().getString(R.string.slayer_queue_full),
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(startingSession = true) }
            try {
                val player = playerRepo.getOrCreatePlayer()
                val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
                val xpMap: Map<String, Long> = json.decodeFromString(player.skillXp)
                val flags: PlayerFlags = json.decodeFromString(player.flags)
                val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
                val result = ThievingSimulator.simulate(
                    npcKey          = npcKey,
                    npc             = npc,
                    startXp         = xpMap[Skills.THIEVING] ?: 0L,
                    thievingLevel   = levels[Skills.THIEVING] ?: 1,
                    agilityLevel    = levels[Skills.AGILITY] ?: 1,
                    floorReductionMin = boostRepo.sessionFloorReductionMin(flags),
                    petBoostPct     = boostRepo.boostedPetPct(Skills.THIEVING, flags, petBoostFor(player.pets, Skills.THIEVING, flags.ironman)),
                    petDropKey      = petKey,
                    petDropChance   = petChance,
                    toolEfficiency  = gameData.toolEfficiency(equipped[EquipSlot.LOCKPICK], EquipSlot.LOCKPICK, npc.levelRequired, skillLevels = levels, heirloomXp = flags.heirloomXp),
                    chronosMultiplier = townRepo.playerSessionDurationMultiplier(flags),
                    successBonus = boostRepo.thievingSuccessBonus(flags),
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
                _uiState.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.skill_session_start_failed, e.message ?: "")) }
            } finally {
                _uiState.update { it.copy(startingSession = false) }
            }
        }
    }

    private fun startSession(
        skillName: String,
        activityKey: String,
        count: Int = 1,
        simulate: suspend () -> SkillSimulator.Result,
    ) {
        viewModelScope.launch {
            // One sequential coroutine handles the whole count: a live start when no
            // session is running, then queued copies for the rest. Callers must not
            // loop over startSession instead -- concurrent launches would race the
            // getActiveSession check and start several live sessions.
            var toQueue = count
            if (sessionRepo.getActiveSession() == null) {
                _uiState.update { it.copy(startingSession = true) }
                try {
                    val result = simulate()
                    val framesJson = json.encodeToString(
                        json.serializersModule.serializer<List<SessionFrame>>(),
                        result.frames,
                    )
                    sessionRepo.startSession(
                        skillName        = skillName,
                        activityKey      = activityKey,
                        frames           = framesJson,
                        durationMs       = result.durationMs,
                        skillDisplayName = skillName.replaceFirstChar { it.uppercase() },
                    )
                    toQueue -= 1
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(snackbarMessage = context.withAppLocale().getString(R.string.skill_session_start_failed, e.message ?: ""))
                    }
                    return@launch
                } finally {
                    _uiState.update { it.copy(startingSession = false) }
                }
            }
            if (toQueue <= 0) return@launch

            val displayName  = GameStrings.skillName(context, skillName)
            val actDisplay   = GameStrings.activityName(context, skillName, activityKey)
            val player       = playerRepo.getOrCreatePlayer()
            val gatherLevels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            val agility      = gatherLevels[Skills.AGILITY] ?: 1
            val gatherFlags = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
            val xpQueueMult = if (gatherFlags.ironman) 1.0 else (if (gatherFlags.xpBoostExpiresAt > System.currentTimeMillis()) 2.0 else 1.0) * ChurchRepository.xpMultiplier(gatherFlags, blessingPrayerCapeMult(player, gatherFlags, gameData))
            val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
            val petBoostPct = petBoostFor(player.pets, skillName, gatherFlags.ironman)
            val rawXp = when (skillName) {
                Skills.MINING      -> SkillSimulator.estimateGatheringXp(
                    gameData.ores[activityKey]?.xpPerOre ?: 0,
                    gameData.toolEfficiency(equipped[EquipSlot.PICKAXE], EquipSlot.PICKAXE, skillLevels = gatherLevels, heirloomXp = gatherFlags.heirloomXp),
                )
                Skills.WOODCUTTING -> SkillSimulator.estimateGatheringXp(
                    gameData.trees[activityKey]?.xpPerLog ?: 0,
                    gameData.toolEfficiency(equipped[EquipSlot.AXE], EquipSlot.AXE, skillLevels = gatherLevels, heirloomXp = gatherFlags.heirloomXp),
                )
                Skills.FISHING     -> SkillSimulator.estimateGatheringXp(
                    gameData.fish[activityKey]?.xpPerCatch ?: 0,
                    gameData.toolEfficiency(equipped[EquipSlot.FISHING_ROD], EquipSlot.FISHING_ROD, skillLevels = gatherLevels, heirloomXp = gatherFlags.heirloomXp),
                )
                Skills.AGILITY     -> {
                    val course = gameData.agilityCourses[activityKey]
                    SkillSimulator.estimateAgilityXp(
                        course?.xpPerSuccess ?: 0, course?.levelRequired ?: 1, agility,
                        gameData.toolEfficiency(equipped[EquipSlot.GRAPPLING_HOOK], EquipSlot.GRAPPLING_HOOK, skillLevels = gatherLevels, heirloomXp = gatherFlags.heirloomXp),
                    )
                }
                else               -> 0L
            }
            val petBoostedXp = if (petBoostPct > 0) (rawXp * (1.0 + petBoostPct / 100.0)).toLong() else rawXp
            val estimatedXpGain = (petBoostedXp * xpQueueMult).toLong()
            val floorReductionMin = boostRepo.sessionFloorReductionMin(gatherFlags)
            val chronosMult     = townRepo.playerSessionDurationMultiplier(gatherFlags)
            var enqueuedAny = false
            for (i in 0 until toQueue) {
                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(
                        skillName           = skillName,
                        activityKey         = activityKey,
                        skillDisplayName    = displayName,
                        estimatedXpGain     = estimatedXpGain,
                        estimatedDurationMs = SkillSimulator.sessionDurationMs(agility, floorReductionMin, chronosMult),
                    )
                )
                if (!enqueued) break
                enqueuedAny = true
            }
            if (enqueuedAny) queuedSessionStarter.startNextQueued()
            _uiState.update {
                it.copy(
                    snackbarMessage = if (enqueuedAny) {
                        if (activityKey.isNotEmpty())
                            context.withAppLocale().getString(R.string.skill_added_to_queue_activity, displayName, actDisplay)
                        else
                            context.withAppLocale().getString(R.string.slayer_queue_added, displayName)
                    } else {
                        context.withAppLocale().getString(R.string.slayer_queue_full)
                    },
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Session collection + abandon
    // ------------------------------------------------------------------

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
            if (session.catalystKey != null && session.catalystQty > 0) {
                playerRepo.addItem(session.catalystKey, session.catalystQty)
            }
            sessionRepo.abandonSession(session.sessionId)
            queuedSessionStarter.startNextQueued()
        }
    }

    fun debugFinishSession() {
        viewModelScope.launch {
            queuedSessionStarter.debugFinishActiveSessionWithRepeats()
        }
    }

    fun snackbarConsumed() = _uiState.update { it.copy(snackbarMessage = null) }
    fun petDialogConsumed() = _uiState.update { it.copy(petFoundName = null) }

    fun prestigeSkill(skillName: String) {
        viewModelScope.launch {
            // The active session is deliberately left running: it completes normally and pays
            // out loot without XP (the eligibility check at collection zeroes it). Only queued
            // actions are evicted — they haven't started and can't start at level 1.
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
        questProgress: Map<String, QuestProgress>,
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
            if (prereqDone) fills += QuestFillSuggestion(GameStrings.questName(context, id, quest.name), remaining)
        }

        val completedIds = questProgress.entries.filter { it.value.completed }.map { it.key }.toSet()
        for ((id, quest) in gameData.guildQuests) {
            if (quest.type != "craft") continue
            if (quest.target != itemKey) continue
            val prog = questProgress[id]
            if (prog?.completed == true) continue
            if (guildRepo.guildLevel(quest.guild, flags.guildDailyTierCounts, completedIds) < quest.guildLevelRequired) continue
            val effectiveAmount = guildRepo.effectiveQuestAmountFromFlags(quest, flags)
            val remaining = effectiveAmount - (prog?.progress ?: 0)
            if (remaining > 0) fills += QuestFillSuggestion(GameStrings.questName(context, id, quest.name), remaining)
        }

        for (daily in dailyQuestRepo.getActiveDailyQuests(flags)) {
            if (daily.claimed) continue
            if (daily.template.type != "craft") continue
            if (daily.template.target != itemKey) continue
            val remaining = daily.template.amount - daily.progress
            if (remaining > 0) fills += QuestFillSuggestion(context.withAppLocale().getString(R.string.quest_fill_daily), remaining)
        }

        for (weekly in weeklyQuestRepo.getActiveWeeklyQuests(flags)) {
            if (weekly.claimed) continue
            if (weekly.template.type != "craft") continue
            if (weekly.template.target != itemKey) continue
            val remaining = weekly.template.amount - weekly.progress
            if (remaining > 0) fills += QuestFillSuggestion(context.withAppLocale().getString(R.string.quest_fill_weekly), remaining)
        }

        val guildPool = gameData.guildDailyPool.associateBy { it.id }
        val activeGuildIds = flags.guildDailyIds.filter { it !in flags.guildDailyClaimed }
        for (id in activeGuildIds) {
            val template = guildPool[id] ?: continue
            if (template.type != "craft") continue
            if (template.target != itemKey) continue
            val progress = flags.guildDailyProgress[id] ?: 0
            val remaining = template.amount - progress
            if (remaining > 0) fills += QuestFillSuggestion(context.withAppLocale().getString(R.string.quest_fill_guild), remaining)
        }

        return fills.sortedBy { it.qty }
    }

    private fun computePrayerFills(
        questProgress: Map<String, QuestProgress>,
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
            if (prereqDone) fills += QuestFillSuggestion(GameStrings.questName(context, id, quest.name), remaining)
        }

        val completedIds = questProgress.entries.filter { it.value.completed }.map { it.key }.toSet()
        for ((id, quest) in gameData.guildQuests) {
            if (quest.type != "prayer") continue
            val prog = questProgress[id]
            if (prog?.completed == true) continue
            if (guildRepo.guildLevel(quest.guild, flags.guildDailyTierCounts, completedIds) < quest.guildLevelRequired) continue
            val effectiveAmount = guildRepo.effectiveQuestAmountFromFlags(quest, flags)
            val remaining = effectiveAmount - (prog?.progress ?: 0)
            if (remaining > 0) fills += QuestFillSuggestion(GameStrings.questName(context, id, quest.name), remaining)
        }

        for (daily in dailyQuestRepo.getActiveDailyQuests(flags)) {
            if (daily.claimed) continue
            if (daily.template.type != "prayer") continue
            val remaining = daily.template.amount - daily.progress
            if (remaining > 0) fills += QuestFillSuggestion(context.withAppLocale().getString(R.string.quest_fill_daily), remaining)
        }

        for (weekly in weeklyQuestRepo.getActiveWeeklyQuests(flags)) {
            if (weekly.claimed) continue
            if (weekly.template.type != "prayer") continue
            val remaining = weekly.template.amount - weekly.progress
            if (remaining > 0) fills += QuestFillSuggestion(context.withAppLocale().getString(R.string.quest_fill_weekly), remaining)
        }

        val guildPool = gameData.guildDailyPool.associateBy { it.id }
        val activeGuildIds = flags.guildDailyIds.filter { it !in flags.guildDailyClaimed }
        for (id in activeGuildIds) {
            val template = guildPool[id] ?: continue
            if (template.type != "prayer") continue
            val progress = flags.guildDailyProgress[id] ?: 0
            val remaining = template.amount - progress
            if (remaining > 0) fills += QuestFillSuggestion(context.withAppLocale().getString(R.string.quest_fill_guild), remaining)
        }

        return fills.sortedBy { it.qty }
    }

    /** Guilds whose key doubles as a Skills-screen skill key (combat guilds are keyed by style, not skill). */
    private val skillGuilds = listOf(
        Skills.MINING, Skills.FISHING, Skills.WOODCUTTING, Skills.FARMING, Skills.THIEVING,
        Skills.FIREMAKING, Skills.AGILITY, Skills.SMITHING, Skills.COOKING, Skills.FLETCHING,
        Skills.CRAFTING, Skills.RUNECRAFTING, Skills.HERBLORE, Skills.CONSTRUCTION,
        Skills.PRAYER, Skills.MERCANTILE, Skills.SLAYER,
    )

    private fun computeSheetQuests(
        questProgress: List<QuestProgress>,
        flags: PlayerFlags,
        skillLevels: Map<String, Int>,
        inventory: Map<String, Int> = emptyMap(),
    ): Map<String, List<SheetQuestSummary>> {
        fun meetsLevel(type: String, skill: String, target: String): Boolean =
            (skillLevels[skill] ?: 1) >= sheetQuestLevelRequired(type, skill, target)
        val completedIds = questProgress.filter { it.completed }.map { it.questId }.toSet()
        val result = mutableMapOf<String, MutableList<SheetQuestSummary>>()
        for (guild in skillGuilds) {
            // Dailies only exist for unlocked guilds, so an empty list also covers the locked case.
            val dailies = guildRepo.getGuildDailiesWithProgress(guild, flags)
            if (dailies.isEmpty()) continue
            val level = guildRepo.guildLevel(guild, flags.guildDailyTierCounts, completedIds)
            val maxed = level >= GuildRepository.DAILIES_REQUIRED_PER_TIER.size
            result.getOrPut(guild) { mutableListOf() } += dailies.map { daily ->
                SheetQuestSummary(
                    questId    = daily.template.id,
                    questName  = daily.template.name,
                    guild      = guild,
                    type       = daily.template.type,
                    target     = daily.template.target,
                    progress   = daily.progress.coerceAtMost(daily.template.amount),
                    amount     = daily.template.amount,
                    claimed    = daily.claimed,
                    source     = SheetQuestSource.GUILD,
                    guildMaxed = maxed,
                    meetsLevel = meetsLevel(daily.template.type, guild, daily.template.target),
                )
            }
        }
        for (dq in dailyQuestRepo.getActiveDailyQuests(flags)) {
            val skill = dq.template.skill
            if (skill !in skillGuilds) continue
            result.getOrPut(skill) { mutableListOf() } += SheetQuestSummary(
                questId     = dq.template.id,
                questName   = dq.template.displayName,
                guild       = skill,
                type        = dq.template.type,
                target      = dq.template.target,
                progress    = dq.progress.coerceAtMost(dq.template.amount),
                amount      = dq.template.amount,
                claimed     = dq.claimed,
                source      = SheetQuestSource.DAILY,
                description = dq.template.description,
                meetsLevel  = meetsLevel(dq.template.type, skill, dq.template.target),
            )
        }
        for (wq in weeklyQuestRepo.getActiveWeeklyQuests(flags)) {
            val skill = wq.template.skill
            if (skill !in skillGuilds) continue
            result.getOrPut(skill) { mutableListOf() } += SheetQuestSummary(
                questId     = wq.template.id,
                questName   = wq.template.displayName,
                guild       = skill,
                type        = wq.template.type,
                target      = wq.template.target,
                progress    = wq.progress.coerceAtMost(wq.template.amount),
                amount      = wq.template.amount,
                claimed     = wq.claimed,
                source      = SheetQuestSource.WEEKLY,
                description = wq.template.description,
                meetsLevel  = meetsLevel(wq.template.type, skill, wq.template.target),
            )
        }
        for (bounty in seasonalEventRepo.getActiveBounties(flags, inventory)) {
            val task = bounty.task
            val skill = task.skill ?: continue
            if (skill !in skillGuilds) continue
            result.getOrPut(skill) { mutableListOf() } += SheetQuestSummary(
                questId     = task.id,
                questName   = task.displayName,
                guild       = skill,
                type        = task.type,
                target      = task.target,
                progress    = bounty.progress,
                amount      = task.amount,
                claimed     = false,
                source      = SheetQuestSource.SEASONAL,
                description = task.hint,
                meetsLevel  = meetsLevel(task.type, skill, task.target),
            )
        }
        return result
    }

    /**
     * Queues a session working toward [daily] from the sheet's quick-add button.
     * Returns false for daily types with no direct session mapping (farming, prayer,
     * trade); craft-guild dailies are routed through CraftingViewModel instead.
     */
    fun queueDailySession(daily: SheetQuestSummary): Boolean {
        // Guild dailies can outlive the level that rolled them (prestige resets the
        // skill), so re-check the activity requirement here (issue #1563).
        val level = uiState.value.skillLevels[daily.guild] ?: 1
        if (sheetQuestLevelRequired(daily.type, daily.guild, daily.target) > level) return false
        val remaining = (daily.amount - daily.progress).coerceAtLeast(1)
        when {
            daily.type == "gather" && daily.guild == Skills.MINING      -> startMiningSession(daily.target)
            daily.type == "gather" && daily.guild == Skills.WOODCUTTING -> {
                // Woodcutting gather quests target the log item, not the tree activity.
                val treeKey = gameData.trees.entries.firstOrNull { it.value.logName == daily.target }?.key
                    ?: return false
                startWoodcuttingSession(treeKey)
            }
            daily.type == "gather" && daily.guild == Skills.FISHING     -> startFishingSession(daily.target)
            daily.type == "pickpocket"                                  -> startThievingSession(daily.target)
            daily.type == "sessions" && daily.guild == Skills.AGILITY   -> {
                // Progress only counts on the quest's own course (recordGuildSessions
                // matches target), so queue that course, not the best unlocked one.
                if (daily.target !in gameData.agilityCourses) return false
                startAgilitySession(daily.target, remaining)
            }
            daily.type == "craft" && daily.guild == Skills.RUNECRAFTING -> startRunecraftingSession(daily.target, remaining)
            daily.type == "craft" && daily.guild == Skills.FIREMAKING   -> {
                val logKey = daily.target.replace("ashes", "log")
                val owned  = uiState.value.inventory[logKey] ?: 0
                if (owned <= 0) {
                    _uiState.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.skill_not_enough_materials)) }
                    return true
                }
                startFiremakingSession(logKey, minOf(remaining, owned))
            }
            else -> return false
        }
        return true
    }

    /**
     * Level needed in [guild] for the activity the quick-add "+" maps [type]/[target] to.
     * Craft-guild recipes are gated in CraftingViewModel.queueCraftForDaily instead.
     */
    private fun sheetQuestLevelRequired(type: String, guild: String, target: String): Int = when {
        (type == "gather" || type == "turn_in") && guild == Skills.MINING      -> gameData.ores[target]?.levelRequired
        (type == "gather" || type == "turn_in") && guild == Skills.WOODCUTTING -> gameData.trees.values.firstOrNull { it.logName == target }?.levelRequired
        (type == "gather" || type == "turn_in") && guild == Skills.FISHING     -> gameData.fish[target]?.levelRequired
        (type == "gather" || type == "turn_in") && guild == Skills.FARMING     -> gameData.crops[target]?.levelRequired
        type == "pickpocket"                            -> gameData.thievingNpcs[target]?.levelRequired
        type == "sessions" && guild == Skills.AGILITY   -> gameData.agilityCourses[target]?.levelRequired
        type == "craft" && guild == Skills.RUNECRAFTING -> gameData.runes[target]?.levelRequired
        type == "craft" && guild == Skills.FIREMAKING   -> gameData.logs[target.replace("ashes", "log")]?.levelRequired
        else -> null
    } ?: 1

    private fun computeActiveQuests(
        questProgress: List<QuestProgress>,
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

        fun addIndicator(key: String, skill: String, category: QuestCategory, remaining: Int, questId: String, customEmoji: String? = null) {
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
            // Prefixed by skill: some item keys (e.g. "ashes") are shared between skills
            // (Firemaking byproduct vs. Prayer buriable), and would otherwise leak
            // indicators across their sheets (issue #1014).
            result.getOrPut("$skill:$key") { mutableListOf() }.add(QuestIndicator(category, isCompletable, questId, customEmoji))
        }

        fun checkAndAdd(questId: String, questType: String, questSkill: String, questTarget: String, questAmount: Int, questProgressVal: Int, category: QuestCategory) {
            val remaining = questAmount - questProgressVal
            if (remaining <= 0) return

            when (questType) {
                "gather" -> {
                    addIndicator(questTarget, questSkill, category, remaining, questId)
                }
                "gather_any" -> {
                    when (questSkill) {
                        Skills.MINING -> gameData.ores.keys.forEach { addIndicator(it, questSkill, category, remaining, questId) }
                        Skills.WOODCUTTING -> gameData.trees.keys.forEach { addIndicator(it, questSkill, category, remaining, questId) }
                        Skills.FISHING -> gameData.fish.keys.forEach { addIndicator(it, questSkill, category, remaining, questId) }
                    }
                }
                "pickpocket" -> {
                    addIndicator(questTarget, questSkill, category, remaining, questId)
                }
                "pickpocket_any" -> {
                    gameData.thievingNpcs.keys.forEach { addIndicator(it, questSkill, category, remaining, questId) }
                }
                "sessions" -> {
                    if (questSkill == Skills.AGILITY) {
                        addIndicator(questTarget, questSkill, category, remaining, questId)
                    }
                }
                "burn" -> {
                    val logToAsh = mapOf(
                        "log" to "ashes", "oak_log" to "oak_ashes", "willow_log" to "willow_ashes",
                        "maple_log" to "maple_ashes", "yew_log" to "yew_ashes",
                        "magic_log" to "magic_ashes", "redwood_log" to "redwood_ashes"
                    )
                    val ashKey = logToAsh[questTarget] ?: questTarget
                    addIndicator(ashKey, questSkill, category, remaining, questId)
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
                            addIndicator(ashKey, questSkill, category, remaining, questId)
                        }
                    }
                }
                "craft" -> {
                    // Only Runecrafting/Firemaking have per-activity sheets on this screen;
                    // the other crafting skills' indicators exist solely to feed the skill-row
                    // icons (timedQuestsBySkill), which no sheet key collides with (issue #1408).
                    addIndicator(questTarget, questSkill, category, remaining, questId)
                }
                "craft_any" -> {
                    if (questSkill == Skills.RUNECRAFTING) {
                        gameData.runes.keys.forEach { addIndicator(it, questSkill, category, remaining, questId) }
                    } else {
                        addIndicator(questTarget.ifBlank { "any" }, questSkill, category, remaining, questId)
                    }
                }
                "prayer" -> {
                    if (questSkill == Skills.PRAYER) {
                        // Burial progress never filters by the quest's target (recordBuried,
                        // recordGuildPrayer, applyDailyPrayer), so every bone qualifies and the
                        // indicator must match (issue #1385). Ashes give Prayer XP but never
                        // count toward prayer quests (issue #1207).
                        gameData.bones.filterValues { !it.isAsh }.keys
                            .forEach { addIndicator(it, questSkill, category, remaining, questId) }
                    }
                }
                "trade" -> {
                    if (questSkill == Skills.MERCANTILE) {
                        addIndicator(questTarget, questSkill, category, remaining, questId)
                    }
                }
                "earn_coins" -> {
                    if (questSkill == Skills.MERCANTILE) {
                        addIndicator("coins", questSkill, category, remaining, questId)
                    }
                }
                "slayer_task" -> addIndicator("task", Skills.SLAYER, category, remaining, questId)
                "slayer_kill" -> addIndicator("kill", Skills.SLAYER, category, remaining, questId)
            }
        }

        for ((id, quest) in gameData.quests) {
            val prog = progressById[id]
            if (prog?.completed == true) continue
            val prereqDone = quest.requiresPrevious == null ||
                    progressById[quest.requiresPrevious]?.completed == true
            if (!prereqDone) continue

            checkAndAdd(id, quest.type, quest.skill, quest.target, quest.amount, prog?.progress ?: 0, QuestCategory.MAIN)
        }

        for ((id, quest) in gameData.guildQuests) {
            val prog = progressById[id]
            if (prog?.completed == true) continue
            if (guildRepo.guildLevel(quest.guild, flags.guildDailyTierCounts, completedIds) < quest.guildLevelRequired) continue

            val effectiveAmount = guildRepo.effectiveQuestAmountFromFlags(quest, flags)
            checkAndAdd(id, quest.type, quest.guild, quest.target, effectiveAmount, prog?.progress ?: 0, QuestCategory.GUILD)
        }

        for (daily in activeDailies) {
            checkAndAdd(daily.template.id, daily.template.type, daily.template.skill, daily.template.target, daily.template.amount, daily.progress, QuestCategory.DAILY)
        }

        for (weekly in activeWeeklies) {
            checkAndAdd(weekly.template.id, weekly.template.type, weekly.template.skill, weekly.template.target, weekly.template.amount, weekly.progress, QuestCategory.WEEKLY)
        }

        for (id in activeGuildDailyIds) {
            val template = guildPool[id] ?: continue
            val progress = flags.guildDailyProgress[id] ?: 0
            checkAndAdd(id, template.type, template.guild, template.target, template.amount, progress, QuestCategory.GUILD_DAILY)
        }

        // Seasonal Event Bounties
        val eventEmoji = seasonalEventRepo.activeEvent()?.iconEmoji ?: QuestCategory.SEASONAL.emoji
        for (bounty in seasonalEventRepo.getActiveBounties(flags, inventory)) {
            val task = bounty.task
            val remaining = task.amount - bounty.progress
            if (remaining <= 0) continue
            val skill = task.skill ?: continue
            when (task.type) {
                "gather", "craft" -> {
                    addIndicator(task.target, skill, QuestCategory.SEASONAL, remaining, task.id, eventEmoji)
                }
                "slayer_task" -> addIndicator("task", Skills.SLAYER, QuestCategory.SEASONAL, remaining, task.id, eventEmoji)
                "slayer_kill" -> addIndicator("kill", Skills.SLAYER, QuestCategory.SEASONAL, remaining, task.id, eventEmoji)
                "turn_in" -> {
                    val isCompletable = (inventory[task.target] ?: 0) >= task.amount
                    result.getOrPut("$skill:${task.target}") { mutableListOf() }
                        .add(QuestIndicator(QuestCategory.SEASONAL, isCompletable, task.id, eventEmoji))
                }
            }
        }

        return result
    }

    private fun petBoostFor(petsJson: String, skillKey: String, ironman: Boolean = false): Int {
        if (ironman) return 0
        val pets = try {
            json.decodeFromString<List<OwnedPet>>(petsJson)
        } catch (_: Exception) {
            return 0
        }
        return pets.sumOf { pet ->
            val pd = gameData.pets[pet.id]
            if (pd != null && (pd.boostedSkill == skillKey || pd.boostedSkill == "all")) pd.boostPercent else 0
        }
    }

    private fun applyQtyPreservation(totalQty: Int, saveChance: Float): Int {
        if (saveChance <= 0f) return totalQty
        var toConsume = 0
        for (u in 0 until totalQty) {
            if (Random.nextFloat() >= saveChance) toConsume++
        }
        return toConsume
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
