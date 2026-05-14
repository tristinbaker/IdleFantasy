package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.json.AgilityCourseData
import com.fantasyidler.data.json.BoneData
import com.fantasyidler.data.json.LogData
import com.fantasyidler.data.json.OreData
import com.fantasyidler.data.json.RuneData
import com.fantasyidler.data.json.TreeData
import com.fantasyidler.data.model.EquipSlot
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
import com.fantasyidler.simulator.SkillSimulator
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
    val sessionDurationMs: Long = 0L,
)

sealed class SheetState {
    data class Mining(val ores: Map<String, OreData>) : SheetState()
    data class Woodcutting(val trees: Map<String, TreeData>) : SheetState()
    data object Fishing : SheetState()
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
    private val queuedSessionStarter: QueuedSessionStarter,
    private val json: Json,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SkillsUiState())

    val uiState: StateFlow<SkillsUiState> = combine(
        playerRepo.playerFlow,
        sessionRepo.activeSessionFlow,
        _uiState,
    ) { player, session, extra ->
        // Combat sessions are managed by CombatViewModel; hide them here.
        val nonCombatSession = session?.takeIf { it.skillName != "combat" }
        if (player == null) {
            extra.copy(isLoading = true, activeSession = nonCombatSession, anySessionActive = session != null)
        } else {
            val levels:   Map<String, Int>     = json.decodeFromString(player.skillLevels)
            val xp:       Map<String, Long>    = json.decodeFromString(player.skillXp)
            val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
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
                sessionDurationMs     = SkillSimulator.sessionDurationMs(levels[Skills.AGILITY] ?: 1),
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
        val miningLevel  = state.skillLevels[Skills.MINING]      ?: 1
        val wcLevel      = state.skillLevels[Skills.WOODCUTTING]  ?: 1
        val agilityLevel = state.skillLevels[Skills.AGILITY]      ?: 1
        val fmLevel      = state.skillLevels[Skills.FIREMAKING]   ?: 1
        val inventory    = state.skillLevels // placeholder — inventory resolved below

        val sheet: SheetState = when (skillKey) {
            Skills.MINING -> SheetState.Mining(
                ores = gameData.ores.filter { (_, ore) -> ore.levelRequired <= miningLevel }
            )
            Skills.WOODCUTTING -> SheetState.Woodcutting(
                trees = gameData.trees.filter { (_, tree) -> tree.levelRequired <= wcLevel }
            )
            Skills.FISHING -> SheetState.Fishing
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
                    val inv: Map<String, Int> = json.decodeFromString(playerRepo.getOrCreatePlayer().inventory)
                    val essenceQty = inv["rune_essence"] ?: 0
                    val rcLevel = state.skillLevels[Skills.RUNECRAFTING] ?: 1
                    val available = gameData.runes.filter { (_, rune) -> rune.levelRequired <= rcLevel }
                    _uiState.update { it.copy(sheetSkill = SheetState.Runecrafting(available, essenceQty)) }
                }
                return
            }
            Skills.PRAYER -> {
                viewModelScope.launch {
                    val inv: Map<String, Int> = json.decodeFromString(playerRepo.getOrCreatePlayer().inventory)
                    val available = gameData.bones.filter { (key, _) -> inv.containsKey(key) }
                    _uiState.update {
                        it.copy(sheetSkill = SheetState.Prayer(available, inv.filterKeys { k -> k in gameData.bones }))
                    }
                }
                return
            }
            Skills.SMITHING,
            Skills.COOKING,
            Skills.FLETCHING,
            Skills.CRAFTING  -> SheetState.Crafting(skillKey)
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

    fun startFiremakingSession(logKey: String) = startSession(Skills.FIREMAKING, logKey) {
        val player  = playerRepo.getOrCreatePlayer()
        val levels: Map<String, Int>  = json.decodeFromString(player.skillLevels)
        val xpMap:  Map<String, Long> = json.decodeFromString(player.skillXp)

        SkillSimulator.simulateGathering(
            skillData          = gameData.firemakingSkillData,
            startXp            = xpMap[Skills.FIREMAKING] ?: 0L,
            agilityLevel       = levels[Skills.AGILITY] ?: 1,
            petBoostPct        = petBoostFor(player.pets, Skills.FIREMAKING),
            forcedDropPerFrame = ashForLog(logKey),
        )
    }

    /** Maps a log key to the ash variant produced by burning it. Falls back to base ashes. */
    private fun ashForLog(logKey: String): String = when (logKey) {
        "oak_log"     -> "oak_ashes"
        "willow_log"  -> "willow_ashes"
        "maple_log"   -> "maple_ashes"
        "yew_log"     -> "yew_ashes"
        "magic_log"   -> "magic_ashes"
        "redwood_log" -> "redwood_ashes"
        else          -> "ashes"
    }

    fun startRunecraftingSession(runeKey: String, qty: Int) {
        viewModelScope.launch {
            if (sessionRepo.getActiveSession() != null) {
                val actDisplay = runeKey.replace('_', ' ').replaceFirstChar { it.uppercase() }
                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(Skills.RUNECRAFTING, runeKey, "Runecrafting", qty = qty)
                )
                _uiState.update {
                    it.copy(
                        snackbarMessage = if (enqueued) "Added to queue: Runecrafting — $actDisplay." else "Queue is full (3/3).",
                        sheetSkill = null,
                    )
                }
                return@launch
            }

            val runeData = gameData.runes[runeKey] ?: return@launch
            val player   = playerRepo.getOrCreatePlayer()
            val inv: Map<String, Int> = json.decodeFromString(player.inventory)
            if ((inv["rune_essence"] ?: 0) < runeData.essenceCost * qty) {
                _uiState.update { it.copy(snackbarMessage = "Not enough Rune Essence") }
                return@launch
            }

            _uiState.update { it.copy(startingSession = true, sheetSkill = null) }
            try {
                val xpMap:   Map<String, Long> = json.decodeFromString(player.skillXp)
                val levels:  Map<String, Int>  = json.decodeFromString(player.skillLevels)
                val agilityLevel = levels[Skills.AGILITY] ?: 1

                var currentXp = xpMap[Skills.RUNECRAFTING] ?: 0L
                val frames = mutableListOf<SessionFrame>()
                for (i in 1..qty) {
                    val levelBefore = XpTable.levelForXp(currentXp)
                    val multiplier = when {
                        levelBefore >= 75 -> 3
                        levelBefore >= 50 -> 2
                        else              -> 1
                    }
                    val runesProduced = multiplier
                    val xpGain = (runeData.xpPerRune * runesProduced).toInt()
                    currentXp += xpGain
                    val levelAfter = XpTable.levelForXp(currentXp)
                    frames += SessionFrame(
                        minute      = i,
                        xpGain      = xpGain,
                        xpBefore    = currentXp - xpGain,
                        xpAfter     = currentXp,
                        levelBefore = levelBefore,
                        levelAfter  = levelAfter,
                        items       = mapOf(runeKey to runesProduced),
                        leveledUp   = levelAfter > levelBefore,
                    )
                }

                val perEssenceMs = SkillSimulator.sessionDurationMs(agilityLevel) / 60
                val framesJson   = json.encodeToString(
                    json.serializersModule.serializer<List<SessionFrame>>(),
                    frames,
                )
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
            if (sessionRepo.getActiveSession() != null) {
                val bone = gameData.bones[boneKey]
                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(Skills.PRAYER, boneKey, "Prayer", qty = qty)
                )
                _uiState.update {
                    it.copy(
                        snackbarMessage = if (enqueued) "Added to queue: Prayer — ${bone?.displayName ?: boneKey}." else "Queue is full (3/3).",
                        sheetSkill = null,
                    )
                }
                return@launch
            }

            val bone   = gameData.bones[boneKey] ?: return@launch
            val player = playerRepo.getOrCreatePlayer()
            val inv: Map<String, Int> = json.decodeFromString(player.inventory)
            if ((inv[boneKey] ?: 0) < qty) {
                _uiState.update { it.copy(snackbarMessage = "Not enough ${bone.displayName}") }
                return@launch
            }

            _uiState.update { it.copy(startingSession = true, sheetSkill = null) }
            try {
                val xpMap:   Map<String, Long> = json.decodeFromString(player.skillXp)
                val levels:  Map<String, Int>  = json.decodeFromString(player.skillLevels)
                var currentXp = xpMap[Skills.PRAYER] ?: 0L
                val frames = mutableListOf<SessionFrame>()
                for (i in 1..qty) {
                    val levelBefore = XpTable.levelForXp(currentXp)
                    val xpGain      = bone.xpPerBone.toInt()
                    currentXp      += xpGain
                    val levelAfter  = XpTable.levelForXp(currentXp)
                    frames += SessionFrame(
                        minute      = i,
                        xpGain      = xpGain,
                        xpBefore    = currentXp - xpGain,
                        xpAfter     = currentXp,
                        levelBefore = levelBefore,
                        levelAfter  = levelAfter,
                        items       = emptyMap(),
                        leveledUp   = levelAfter > levelBefore,
                        kills       = 1, // each frame = 1 bone buried (for quest tracking)
                    )
                }

                val agilityLevel = levels[Skills.AGILITY] ?: 1
                val perBoneMs    = SkillSimulator.sessionDurationMs(agilityLevel) / 60
                val framesJson   = json.encodeToString(
                    json.serializersModule.serializer<List<SessionFrame>>(),
                    frames,
                )
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

    fun startFishingSession() = startSession(Skills.FISHING, activityKey = "") {
        val player  = playerRepo.getOrCreatePlayer()
        val levels: Map<String, Int>  = json.decodeFromString(player.skillLevels)
        val xpMap:  Map<String, Long> = json.decodeFromString(player.skillXp)
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
        val (petKey, petChance) = petDropParams(Skills.FISHING)

        SkillSimulator.simulateGathering(
            skillData        = gameData.fishingSkillData,
            startXp          = xpMap[Skills.FISHING] ?: 0L,
            agilityLevel     = levels[Skills.AGILITY] ?: 1,
            petBoostPct      = petBoostFor(player.pets, Skills.FISHING),
            toolEfficiency   = toolEfficiency(equipped[EquipSlot.FISHING_ROD], EquipSlot.FISHING_ROD, levels[Skills.FISHING] ?: 1),
            petDropKey       = petKey,
            petDropChance    = petChance,
        )
    }

    private fun startSession(
        skillName: String,
        activityKey: String,
        simulate: suspend () -> SkillSimulator.Result,
    ) {
        viewModelScope.launch {
            if (sessionRepo.getActiveSession() != null) {
                val displayName = skillName.replaceFirstChar { it.uppercase() }
                val actDisplay  = activityKey.replace('_', ' ').replaceFirstChar { it.uppercase() }
                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(skillName, activityKey, displayName)
                )
                _uiState.update {
                    it.copy(
                        snackbarMessage = if (enqueued)
                            "Added to queue: $displayName${if (activityKey.isNotEmpty()) " — $actDisplay" else ""}."
                        else
                            "Queue is full (3/3).",
                        sheetSkill = null,
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

            playerRepo.applySessionResults(
                skillName   = session.skillName,
                xpGained    = totalXp,
                itemsGained = regularItems,
            )

            // Record quest progress
            val gatheringSkills = setOf(Skills.MINING, Skills.WOODCUTTING, Skills.FISHING,
                Skills.AGILITY, Skills.FIREMAKING, Skills.RUNECRAFTING)
            val craftingSkills = setOf(Skills.SMITHING, Skills.COOKING, Skills.FLETCHING, Skills.CRAFTING)
            when (session.skillName) {
                in gatheringSkills -> questRepo.recordGathering(session.skillName, regularItems)
                in craftingSkills  -> questRepo.recordCrafting(session.skillName, regularItems)
                Skills.PRAYER      -> questRepo.recordBuried(frames.sumOf { it.kills })
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
            sessionRepo.abandonSession(session.sessionId)
        }
    }

    fun debugFinishSession() {
        viewModelScope.launch {
            val session = sessionRepo.getActiveSession() ?: return@launch
            sessionRepo.markCompleted(session.sessionId)
        }
    }

    fun snackbarConsumed() = _uiState.update { it.copy(snackbarMessage = null) }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

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
    private fun petBoostFor(petsJson: String, skillKey: String): Int {
        val pets = try {
            json.decodeFromString<List<com.fantasyidler.data.model.OwnedPet>>(petsJson)
        } catch (_: Exception) {
            return 0
        }
        // A pet boosts one skill — find the first matching one
        val petId = pets.firstOrNull { pet ->
            gameData.pets[pet.id]?.boostedSkill == skillKey
        } ?: return 0
        return gameData.pets[petId.id]?.boostPercent ?: 0
    }
}

/** XP progress fraction (0.0–1.0) for display in XP bars. */
fun xpProgressFraction(xp: Long): Float = XpTable.progressFraction(xp)

/** Formatted level string for display. */
fun levelDisplay(xp: Long): Int = XpTable.levelForXp(xp)
