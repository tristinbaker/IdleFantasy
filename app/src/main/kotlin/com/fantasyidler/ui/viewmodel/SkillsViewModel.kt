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
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.QueuedSessionStarter
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.simulator.ThievingSimulator
import com.fantasyidler.simulator.XpTable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import javax.inject.Inject

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
    /** Non-null after a session is collected — drives the result sheet. Consumed by the UI. */
    val sessionResult: SessionResult? = null,
    val anySessionActive: Boolean = false,
    val queueSize: Int = 0,
    val miningEfficiency: Float = 1.0f,
    val woodcuttingEfficiency: Float = 1.0f,
    val fishingEfficiency: Float = 1.0f,
    val farmingEfficiency: Float = 1.0f,
    val cropsReadyCount: Int = 0,
    val xpBonusMult: Float = 1.0f,
    val sessionDurationMs: Long = 0L,
    val skillPrestige: Map<String, Int> = emptyMap(),
    val inventory: Map<String, Int> = emptyMap(),
)

sealed class SheetState {
    data class Mining(val ores: Map<String, OreData>) : SheetState()
    data class Woodcutting(val trees: Map<String, TreeData>) : SheetState()
    data class Fishing(val fish: Map<String, FishData>) : SheetState()
    data class Agility(val courses: Map<String, AgilityCourseData>) : SheetState()
    /** availableLogs = logs the player currently has in inventory */
    data class Firemaking(val availableLogs: Map<String, LogData>) : SheetState()
    data class Runecrafting(
        val availableRunes: Map<String, RuneData>,
        val essenceQty: Int,
    ) : SheetState()
    /** Bones the player currently has in inventory, with their counts. */
    data class Prayer(
        val availableBones: Map<String, BoneData>,
        val inventory: Map<String, Int>,
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
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val questRepo: QuestRepository,
    private val guildRepo: GuildRepository,
    private val farmingRepo: FarmingRepository,
    private val queuedSessionStarter: QueuedSessionStarter,
    private val json: Json,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillsUiState())

    val uiState: StateFlow<SkillsUiState> = combine(
        playerRepo.playerFlow,
        sessionRepo.activeSessionFlow,
        _uiState,
        farmingRepo.observePatches(),
    ) { player, session, extra, patches ->
        // Combat sessions are managed by CombatViewModel; hide them here.
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
                miningEfficiency      = toolEfficiency(equipped[EquipSlot.PICKAXE],     EquipSlot.PICKAXE,     0),
                woodcuttingEfficiency = toolEfficiency(equipped[EquipSlot.AXE],         EquipSlot.AXE,         0),
                fishingEfficiency     = toolEfficiency(equipped[EquipSlot.FISHING_ROD], EquipSlot.FISHING_ROD, 0),
                farmingEfficiency     = toolEfficiency(equipped[EquipSlot.HOE],         EquipSlot.HOE,         0),
                xpBonusMult           = (if (flags.xpBoostExpiresAt > System.currentTimeMillis()) 2.0f else 1.0f) * ChurchRepository.xpMultiplier(flags),
                sessionDurationMs     = SkillSimulator.sessionDurationMs(levels[Skills.AGILITY] ?: 1),
                skillPrestige         = flags.skillPrestige,
                inventory             = inv,
                cropsReadyCount       = cropsReady,
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
                    val inv: Map<String, Int> = kotlinx.serialization.json.Json
                        .decodeFromString(playerRepo.getOrCreatePlayer().inventory)
                    val availableLogs = gameData.logs.filter { (key, log) ->
                        inv.containsKey(key) && log.levelRequired <= fmLevel
                    }
                    _uiState.update { it.copy(sheetSkill = SheetState.Firemaking(availableLogs)) }
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
                    _uiState.update { it.copy(sheetSkill = SheetState.Runecrafting(available, essenceQty)) }
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
                    _uiState.update {
                        it.copy(sheetSkill = SheetState.Prayer(available, effectiveCounts.filterKeys { k -> k in gameData.bones }))
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
        val (petKey, petChance) = petDropParams(Skills.MINING)

        SkillSimulator.simulateMining(
            oreKey           = oreKey,
            oreData          = oreData,
            gems             = gameData.gems,
            startXp          = xpMap[Skills.MINING] ?: 0L,
            agilityLevel     = levels[Skills.AGILITY] ?: 1,
            petBoostPct      = petBoostFor(player.pets, Skills.MINING),
            toolEfficiency   = toolEfficiency(equipped[EquipSlot.PICKAXE], EquipSlot.PICKAXE, oreData.levelRequired),
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
        val (petKey, petChance) = petDropParams(Skills.WOODCUTTING)

        SkillSimulator.simulateWoodcutting(
            treeData         = treeData,
            startXp          = xpMap[Skills.WOODCUTTING] ?: 0L,
            agilityLevel     = levels[Skills.AGILITY] ?: 1,
            petBoostPct      = petBoostFor(player.pets, Skills.WOODCUTTING),
            toolEfficiency   = toolEfficiency(equipped[EquipSlot.AXE], EquipSlot.AXE, treeData.levelRequired),
            petDropKey       = petKey,
            petDropChance    = petChance,
        )
    }

    fun startAgilitySession(courseKey: String) = startSession(Skills.AGILITY, courseKey) {
        val courseData = gameData.agilityCourses[courseKey]
            ?: throw IllegalArgumentException("Unknown course: $courseKey")
        val player  = playerRepo.getOrCreatePlayer()
        val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)

        SkillSimulator.simulateAgility(
            courseData   = courseData,
            startXp      = (json.decodeFromString<Map<String, Long>>(player.skillXp))[Skills.AGILITY] ?: 0L,
            agilityLevel = levels[Skills.AGILITY] ?: 1,
            petBoostPct  = petBoostFor(player.pets, Skills.AGILITY),
        )
    }

    fun startFiremakingSession(logKey: String, qty: Int) {
        viewModelScope.launch {
            val player = playerRepo.getOrCreatePlayer()
            val inv: Map<String, Int> = json.decodeFromString(player.inventory)
            val available = inv[logKey] ?: 0
            if (available <= 0) {
                _uiState.update { it.copy(snackbarMessage = "No logs in inventory") }
                return@launch
            }
            val actualQty = qty.coerceIn(1, available)
            val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            val agility = levels[Skills.AGILITY] ?: 1
            val perLogMs = SkillSimulator.sessionDurationMs(agility) / 60L
            val logXp = gameData.logs[logKey]?.xpPerLog?.toLong() ?: 0L
            val flags = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
            val xpQueueMult = (if (flags.xpBoostExpiresAt > System.currentTimeMillis()) 2.0 else 1.0) * ChurchRepository.xpMultiplier(flags)
            val action = QueuedAction(
                skillName           = Skills.FIREMAKING,
                activityKey         = logKey,
                skillDisplayName    = "Firemaking",
                qty                 = actualQty,
                estimatedXpGain     = (actualQty.toLong() * logXp * xpQueueMult).toLong(),
                estimatedDurationMs = actualQty.toLong() * perLogMs,
            )

            if (sessionRepo.getActiveSession() != null) {
                val enqueued = playerRepo.enqueueAction(action)
                if (enqueued) playerRepo.consumeItems(mapOf(logKey to actualQty))
                _uiState.update {
                    it.copy(
                        snackbarMessage = if (enqueued) "Added to queue: Firemaking." else "Queue is full (3/3).",
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
                _uiState.update { it.copy(snackbarMessage = "Failed to start session: ${e.message}") }
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
                _uiState.update { it.copy(snackbarMessage = "Not enough Rune Essence") }
                return@launch
            }

            if (sessionRepo.getActiveSession() != null) {
                val actDisplay = runeKey.replace('_', ' ').replaceFirstChar { it.uppercase() }
                val levels     = json.decodeFromString<Map<String, Int>>(player.skillLevels)
                val agility    = levels[Skills.AGILITY]      ?: 1
                val rcLevel    = levels[Skills.RUNECRAFTING]  ?: 1
                val perItemMs  = SkillSimulator.sessionDurationMs(agility) / 60
                val ashBon     = catalystKey?.let { ashRuneBonusForKey(it) } ?: 0
                val mult       = when { rcLevel >= 75 -> 3; rcLevel >= 50 -> 2; else -> 1 } + ashBon
                val rcFlags = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
                val xpQueueMult = (if (rcFlags.xpBoostExpiresAt > System.currentTimeMillis()) 2.0 else 1.0) * ChurchRepository.xpMultiplier(rcFlags)
                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(
                        skillName           = Skills.RUNECRAFTING,
                        activityKey         = runeKey,
                        skillDisplayName    = "Runecrafting",
                        qty                 = qty,
                        estimatedXpGain     = (qty.toLong() * (runeData.xpPerRune * mult).toLong() * xpQueueMult).toLong(),
                        estimatedDurationMs = qty.toLong() * perItemMs,
                        catalystKey         = catalystKey,
                    )
                )
                if (enqueued) {
                    playerRepo.consumeItems(mapOf("rune_essence" to runeData.essenceCost * qty))
                    if (catalystKey != null) {
                        val ashCost = (qty + 9) / 10
                        playerRepo.consumeItems(mapOf(catalystKey to ashCost))
                    }
                }
                _uiState.update {
                    it.copy(
                        snackbarMessage = if (enqueued) "Added to queue: Runecrafting — $actDisplay." else "Queue is full (3/3).",
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(startingSession = true, sheetSkill = null) }
            try {
                val xpMap:   Map<String, Long> = json.decodeFromString(player.skillXp)
                val levels:  Map<String, Int>  = json.decodeFromString(player.skillLevels)
                val agilityLevel = levels[Skills.AGILITY] ?: 1

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

                val perEssenceMs = SkillSimulator.sessionDurationMs(agilityLevel) / 60
                val framesJson   = json.encodeToString(
                    json.serializersModule.serializer<List<SessionFrame>>(),
                    frames,
                )
                playerRepo.consumeItems(mapOf("rune_essence" to runeData.essenceCost * qty))
                if (catalystKey != null) {
                    val ashCost = (qty + 9) / 10
                    playerRepo.consumeItems(mapOf(catalystKey to ashCost))
                }
                sessionRepo.startSession(
                    skillName        = Skills.RUNECRAFTING,
                    activityKey      = runeKey,
                    frames           = framesJson,
                    durationMs       = qty.toLong() * perEssenceMs,
                    skillDisplayName = "Runecrafting",
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to start session: ${e.message}") }
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
                _uiState.update { it.copy(snackbarMessage = "Not enough ${bone.displayName}") }
                return@launch
            }

            if (sessionRepo.getActiveSession() != null) {
                val agility   = (json.decodeFromString<Map<String, Int>>(player.skillLevels))[Skills.AGILITY] ?: 1
                val perBoneMs = SkillSimulator.sessionDurationMs(agility) / 60
                val prayerFlags = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
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
                _uiState.update {
                    it.copy(
                        snackbarMessage = if (enqueued) "Added to queue: Prayer — ${bone.displayName}." else "Queue is full (3/3).",
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
                val perBoneMs    = SkillSimulator.sessionDurationMs(agilityLevel) / 60
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
                _uiState.update { it.copy(snackbarMessage = "Failed to start session: ${e.message}") }
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
        val (petKey, petChance) = petDropParams(Skills.FISHING)

        SkillSimulator.simulateFishing(
            fishKey          = fishKey,
            fishData         = fishData,
            startXp          = xpMap[Skills.FISHING] ?: 0L,
            agilityLevel     = levels[Skills.AGILITY] ?: 1,
            petBoostPct      = petBoostFor(player.pets, Skills.FISHING),
            rodEfficiency    = toolEfficiency(equipped[EquipSlot.FISHING_ROD], EquipSlot.FISHING_ROD, fishData.levelRequired),
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
                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(
                        skillName           = Skills.THIEVING,
                        activityKey         = npcKey,
                        skillDisplayName    = "Thieving",
                        estimatedDurationMs = SkillSimulator.sessionDurationMs(agility),
                    )
                )
                _uiState.update {
                    it.copy(
                        snackbarMessage = if (enqueued)
                            "Added to queue: Thieving — ${npc.displayName}."
                        else
                            "Queue is full (3/3).",
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(startingSession = true, sheetSkill = null) }
            try {
                val player = playerRepo.getOrCreatePlayer()
                val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
                val xpMap: Map<String, Long> = json.decodeFromString(player.skillXp)
                val result = ThievingSimulator.simulate(
                    npcKey         = npcKey,
                    npc            = npc,
                    startXp        = xpMap[Skills.THIEVING] ?: 0L,
                    thievingLevel  = levels[Skills.THIEVING] ?: 1,
                    agilityLevel   = levels[Skills.AGILITY] ?: 1,
                    petBoostPct    = petBoostFor(player.pets, Skills.THIEVING),
                    petDropKey     = petKey,
                    petDropChance  = petChance,
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
                _uiState.update { it.copy(snackbarMessage = "Failed to start session: ${e.message}") }
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
                val estimatedXpGain = (when (skillName) {
                    Skills.MINING      -> SkillSimulator.estimateGatheringXp(
                        gameData.ores[activityKey]?.xpPerOre ?: 0,
                        toolEfficiency(equipped[EquipSlot.PICKAXE], EquipSlot.PICKAXE),
                    )
                    Skills.WOODCUTTING -> SkillSimulator.estimateGatheringXp(
                        gameData.trees[activityKey]?.xpPerLog ?: 0,
                        toolEfficiency(equipped[EquipSlot.AXE], EquipSlot.AXE),
                    )
                    Skills.FISHING     -> SkillSimulator.estimateGatheringXp(
                        gameData.fish[activityKey]?.xpPerCatch ?: 0,
                        toolEfficiency(equipped[EquipSlot.FISHING_ROD], EquipSlot.FISHING_ROD),
                    )
                    Skills.AGILITY     -> {
                        val course = gameData.agilityCourses[activityKey]
                        SkillSimulator.estimateAgilityXp(course?.xpPerSuccess ?: 0, course?.levelRequired ?: 1, agility)
                    }
                    else               -> 0L
                } * xpQueueMult).toLong()
                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(
                        skillName           = skillName,
                        activityKey         = activityKey,
                        skillDisplayName    = displayName,
                        estimatedXpGain     = estimatedXpGain,
                        estimatedDurationMs = SkillSimulator.sessionDurationMs(agility),
                    )
                )
                _uiState.update {
                    it.copy(
                        snackbarMessage = if (enqueued)
                            "Added to queue: $displayName${if (activityKey.isNotEmpty()) " — $actDisplay" else ""}."
                        else
                            "Queue is full (3/3).",
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
                    it.copy(snackbarMessage = "Failed to start session: ${e.message}")
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
            val totalXp  = frames.sumOf { it.xpGain.toLong() }
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
                    when (session.skillName) {
                        Skills.AGILITY -> guildRepo.recordGuildSessions()
                        else           -> guildRepo.recordGuildGathering(session.skillName, regularItems)
                    }
                }
                in craftingSkills -> {
                    questRepo.recordCrafting(session.skillName, regularItems)
                    playerRepo.recordDailyCrafting(regularItems)
                    guildRepo.recordGuildCrafting(session.skillName, regularItems)
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
            var petMessage: String? = null
            for ((petId, _) in petDrops) {
                val petData = gameData.pets[petId] ?: continue
                val added = playerRepo.addPetIfNew(petId, petData.boostPercent)
                if (added) petMessage = "You found a pet: ${petData.displayName}!"
            }

            sessionRepo.deleteSession(session.sessionId)

            _uiState.update {
                it.copy(
                    sessionResult = SessionResult(
                        skillName   = session.skillName,
                        xpGained    = totalXp,
                        itemsGained = regularItems,
                        levelUps    = levelUps,
                    ),
                    snackbarMessage = petMessage,
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

    fun prestigeSkill(skillName: String) {
        viewModelScope.launch {
            val activeSession = sessionRepo.getActiveSession()
            val abandonedSession = activeSession?.takeIf { it.skillName == skillName }
            if (abandonedSession != null) {
                val frames: List<SessionFrame> = json.decodeFromString(abandonedSession.frames)
                playerSessionMaterials(abandonedSession.skillName, abandonedSession.activityKey, frames.sumOf { it.kills }, gameData)
                    ?.let { playerRepo.addItems(it) }
                sessionRepo.abandonSession(abandonedSession.sessionId)
            }
            val evicted = playerRepo.evictQueueForSkill(skillName)
            for (action in evicted) {
                if (action.coinRefund > 0) playerRepo.addCoins(action.coinRefund)
                playerSessionMaterials(action.skillName, action.activityKey, action.qty, gameData)
                    ?.let { playerRepo.addItems(it) }
            }
            playerRepo.prestigeSkill(skillName)
            grantStarterGearAfterPrestige(skillName)
            if (abandonedSession != null) queuedSessionStarter.startNextQueued()
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private suspend fun grantStarterGearAfterPrestige(prestigedSkill: String) {
        val levels   = playerRepo.getSkillLevels()
        val equipped = playerRepo.getEquipped().toMutableMap()
        val allEquip = gameData.equipment

        val starterForSlot = mapOf(
            EquipSlot.WEAPON_ATK    to "bronze_sword",
            EquipSlot.WEAPON_STR    to "bronze_scimitar",
            EquipSlot.WEAPON_RANGED to "wooden_bow",
            EquipSlot.WEAPON_MAGIC  to "basic_staff",
            EquipSlot.HEAD          to "bronze_full_helmet",
            EquipSlot.BODY          to "bronze_platebody",
            EquipSlot.LEGS          to "bronze_platelegs",
            EquipSlot.SHIELD        to "bronze_kiteshield",
            EquipSlot.BOOTS         to "bronze_boots",
        )

        val toGrant = mutableMapOf<String, Int>()
        for ((slot, starterKey) in starterForSlot) {
            val currentKey = equipped[slot]
            val currentValid = currentKey != null &&
                (allEquip[currentKey]?.requirements?.all { (skill, lvl) -> (levels[skill] ?: 1) >= lvl } == true)
            if (currentValid) continue

            val style = EquipSlot.combatStyleForSlot(slot)
            val inventory = playerRepo.getOrCreatePlayer().let {
                json.decodeFromString<Map<String, Int>>(it.inventory)
            }
            val bestFromInv = inventory.keys
                .mapNotNull { k -> allEquip[k]?.let { eq -> k to eq } }
                .filter { (_, eq) ->
                    if (style != null) eq.slot == "weapon" && eq.combatStyle == style else eq.slot == slot
                }
                .filter { (_, eq) -> eq.requirements.all { (skill, lvl) -> (levels[skill] ?: 1) >= lvl } }
                .maxByOrNull { (_, eq) -> eq.attackBonus + eq.strengthBonus + eq.defenseBonus }
                ?.first

            if (bestFromInv != null) {
                equipped[slot] = bestFromInv
            } else if (allEquip[starterKey] != null) {
                toGrant[starterKey] = (toGrant[starterKey] ?: 0) + 1
                equipped[slot] = starterKey
            }
        }

        if (toGrant.isNotEmpty()) playerRepo.addItems(toGrant)
        playerRepo.updateEquipped(equipped)
    }

    /** Sums qty already committed to the queue for each activityKey under [skillName]. */
    private fun reservedQty(queue: List<QueuedAction>, skillName: String): Map<String, Int> =
        queue.filter { it.skillName == skillName }
             .groupingBy { it.activityKey }
             .fold(0) { acc, a -> acc + a.qty }

    private val TOOL_TIERS = listOf(1, 15, 30, 55, 70, 85)

    private fun tierIndex(level: Int): Int =
        TOOL_TIERS.indexOfLast { it <= level }.coerceAtLeast(0)

    /**
     * Returns the efficiency multiplier for the equipped tool in [slot].
     * Falls back to 1.0 (no tool equipped or unknown item).
     *
     * If [resourceLevelRequired] > 0, applies a per-tier bonus of +0.25x for each
     * tier the tool is above the resource: base × (1.0 + 0.25 × tierDiff).
     */
    private fun toolEfficiency(itemKey: String?, slot: String, resourceLevelRequired: Int = 0): Float {
        if (itemKey == null) return 1.0f
        val eq = gameData.equipment[itemKey] ?: return 1.0f
        val base = when (slot) {
            EquipSlot.PICKAXE     -> eq.miningEfficiency      ?: 1.0f
            EquipSlot.AXE         -> eq.woodcuttingEfficiency ?: 1.0f
            EquipSlot.FISHING_ROD -> eq.fishingEfficiency     ?: 1.0f
            EquipSlot.HOE         -> 1f + (eq.farmingEfficiency ?: 0f)
            else                  -> 1.0f
        }
        if (resourceLevelRequired <= 0) return base
        val skillKey = when (slot) {
            EquipSlot.PICKAXE     -> Skills.MINING
            EquipSlot.AXE         -> Skills.WOODCUTTING
            EquipSlot.FISHING_ROD -> Skills.FISHING
            else                  -> return base
        }
        val toolReqLevel = eq.requirements[skillKey] ?: 1
        val tierDiff = tierIndex(toolReqLevel) - tierIndex(resourceLevelRequired)
        return if (tierDiff > 0) base * (1.0f + 0.25f * tierDiff) else base
    }

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

    private fun petBoostFor(petsJson: String, skillKey: String): Int {
        val pets = try {
            json.decodeFromString<List<com.fantasyidler.data.model.OwnedPet>>(petsJson)
        } catch (_: Exception) {
            return 0
        }
        val petId = pets.firstOrNull { pet ->
            gameData.pets[pet.id]?.boostedSkill == skillKey || gameData.pets[pet.id]?.boostedSkill == "all"
        } ?: return 0
        return gameData.pets[petId.id]?.boostPercent ?: 0
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
