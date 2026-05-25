package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.json.BoneData
import com.fantasyidler.data.json.FishData
import com.fantasyidler.data.json.LogData
import com.fantasyidler.data.json.OreData
import com.fantasyidler.data.json.RuneData
import com.fantasyidler.data.json.TreeData
import com.fantasyidler.data.json.AgilityCourseData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.HiredWorker
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.repository.WorkerQueuedSessionStarter
import com.fantasyidler.simulator.SkillSimulator
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

data class WorkerSkillsUiState(
    val skillLevels: Map<String, Int> = emptyMap(),
    val skillXp: Map<String, Long> = emptyMap(),
    val activeSession: SkillSession? = null,
    val isLoading: Boolean = true,
    val sheetSkill: SheetState? = null,
    val snackbarMessage: String? = null,
    val workerQueue: List<QueuedAction> = emptyList(),
    val hiredWorker: HiredWorker? = null,
    val miningEfficiency: Float = 1.0f,
    val woodcuttingEfficiency: Float = 1.0f,
    val fishingEfficiency: Float = 1.0f,
    /** Agility-based per-item duration — used for crafting/prayer/runecrafting estimates. */
    val sessionDurationMs: Long = 0L,
    /** Worker tier fixed duration for gathering/combat sessions. */
    val gatheringDurationMs: Long = 0L,
    val maxCraftQty: Int = Int.MAX_VALUE,
    val inventory: Map<String, Int> = emptyMap(),
    val selectedRecipe: CraftableRecipe? = null,
    val craftQuantity: Int = 1,
) {
    val workerQueueFull: Boolean get() = workerQueue.size >= 3

    fun maxCraftable(recipe: CraftableRecipe): Int {
        if (recipe.materials.isEmpty()) return 0
        return recipe.materials.minOf { (item, needed) -> (inventory[item] ?: 0) / needed }
    }
}

@HiltViewModel
class WorkerSkillsViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val workerStarter: WorkerQueuedSessionStarter,
    private val json: Json,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkerSkillsUiState())

    val uiState: StateFlow<WorkerSkillsUiState> = combine(
        playerRepo.playerFlow,
        sessionRepo.activeWorkerSessionFlow,
        _uiState,
    ) { player, workerSession, extra ->
        if (player == null) {
            extra.copy(isLoading = true, activeSession = workerSession)
        } else {
            val levels: Map<String, Int>     = json.decodeFromString(player.skillLevels)
            val xp: Map<String, Long>        = json.decodeFromString(player.skillXp)
            val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
            val flags = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
            val inv: Map<String, Int>        = json.decodeFromString(player.inventory)
            val agilityMs   = SkillSimulator.sessionDurationMs(levels[Skills.AGILITY] ?: 1)
            val tierDurationMs = flags.hiredWorker?.tier?.durationMs ?: agilityMs
            extra.copy(
                isLoading             = false,
                skillLevels           = levels,
                skillXp               = xp,
                activeSession         = workerSession,
                workerQueue           = flags.hiredWorker?.sessionQueue ?: emptyList(),
                hiredWorker           = flags.hiredWorker,
                miningEfficiency      = toolEfficiency(equipped[EquipSlot.PICKAXE],     EquipSlot.PICKAXE),
                woodcuttingEfficiency = toolEfficiency(equipped[EquipSlot.AXE],         EquipSlot.AXE),
                fishingEfficiency     = toolEfficiency(equipped[EquipSlot.FISHING_ROD], EquipSlot.FISHING_ROD),
                sessionDurationMs     = flags.hiredWorker?.tier?.craftingSessionMs ?: agilityMs,
                gatheringDurationMs   = tierDurationMs,
                maxCraftQty           = flags.hiredWorker?.tier?.maxCraftQty ?: Int.MAX_VALUE,
                inventory             = inv,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkerSkillsUiState())

    // ------------------------------------------------------------------
    // Recipe lists (mirrors CraftingViewModel, lazy)
    // ------------------------------------------------------------------

    val smithingRecipes: List<CraftableRecipe> by lazy {
        gameData.smithingRecipes.map { (key, r) ->
            val equip = gameData.equipment[key]
            CraftableRecipe(
                key                 = key,
                displayName         = r.displayName,
                levelRequired       = r.levelRequired,
                materials           = r.materials,
                outputKey           = key,
                outputQty           = r.outputQuantity,
                xpPerItem           = r.xpPerItem,
                skillName           = Skills.SMITHING,
                outputAttackBonus   = equip?.attackBonus   ?: 0,
                outputStrengthBonus = equip?.strengthBonus ?: 0,
                outputDefenseBonus  = equip?.defenseBonus  ?: 0,
                outputRequirements  = equip?.requirements  ?: emptyMap(),
                outputCombatStyle   = equip?.combatStyle,
            )
        }.sortedBy { it.levelRequired }
    }

    val cookingRecipes: List<CraftableRecipe> by lazy {
        gameData.cookingRecipes.map { (key, r) ->
            CraftableRecipe(
                key                = key,
                displayName        = r.displayName,
                levelRequired      = r.levelRequired,
                materials          = mapOf(r.rawItem to 1),
                outputKey          = r.cookedItem,
                outputQty          = 1,
                xpPerItem          = r.xpPerItem,
                skillName          = Skills.COOKING,
                outputHealingValue = r.healingValue,
                category           = "Food",
            )
        }.sortedBy { it.levelRequired }
    }

    val fletchingRecipes: List<CraftableRecipe> by lazy {
        gameData.fletchingRecipes.map { (_, r) ->
            CraftableRecipe(
                key                 = r.itemName,
                displayName         = r.displayName,
                levelRequired       = r.levelRequired,
                materials           = r.materials,
                outputKey           = r.itemName,
                outputQty           = r.outputQuantity,
                xpPerItem           = r.xpPerItem,
                skillName           = Skills.FLETCHING,
                outputDamage        = r.damage        ?: 0,
                outputAttackBonus   = r.attackBonus   ?: 0,
                outputStrengthBonus = r.strengthBonus ?: 0,
                outputCombatStyle   = gameData.equipment[r.itemName]?.combatStyle,
            )
        }.sortedBy { it.levelRequired }
    }

    val jewelleryRecipes: List<CraftableRecipe> by lazy {
        gameData.craftingRecipes.map { (key, r) ->
            val equip = gameData.equipment[key]
            CraftableRecipe(
                key                 = key,
                displayName         = r.displayName,
                levelRequired       = r.levelRequired,
                materials           = r.materials,
                outputKey           = key,
                outputQty           = r.outputQuantity,
                xpPerItem           = r.xpPerItem,
                skillName           = Skills.CRAFTING,
                outputAttackBonus   = equip?.attackBonus   ?: 0,
                outputStrengthBonus = equip?.strengthBonus ?: 0,
                outputDefenseBonus  = equip?.defenseBonus  ?: 0,
                outputRequirements  = equip?.requirements  ?: emptyMap(),
                outputCombatStyle   = equip?.combatStyle,
            )
        }.sortedBy { it.levelRequired }
    }

    val herbloreRecipes: List<CraftableRecipe> by lazy {
        gameData.herbloreRecipes.map { (key, r) ->
            CraftableRecipe(
                key           = key,
                displayName   = r.displayName,
                levelRequired = r.levelRequired,
                materials     = r.materials,
                outputKey     = key,
                outputQty     = r.outputQuantity,
                xpPerItem     = r.xpPerItem,
                skillName     = Skills.HERBLORE,
                category      = "Potion",
                effects       = r.effects,
            )
        }.sortedBy { it.levelRequired }
    }

    // ------------------------------------------------------------------
    // Activity sheet dispatch
    // ------------------------------------------------------------------

    fun onSkillTapped(skillKey: String) {
        val state = uiState.value
        val miningLevel  = state.skillLevels[Skills.MINING]     ?: 1
        val wcLevel      = state.skillLevels[Skills.WOODCUTTING] ?: 1
        val fishingLevel = state.skillLevels[Skills.FISHING]    ?: 1
        val agilityLevel = state.skillLevels[Skills.AGILITY]    ?: 1
        val fmLevel      = state.skillLevels[Skills.FIREMAKING]  ?: 1
        val rcLevel      = state.skillLevels[Skills.RUNECRAFTING] ?: 1

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
                viewModelScope.launch {
                    val inv: Map<String, Int> = json.decodeFromString(playerRepo.getOrCreatePlayer().inventory)
                    val available = gameData.logs.filter { (key, log) ->
                        inv.containsKey(key) && log.levelRequired <= fmLevel
                    }
                    _uiState.update { it.copy(sheetSkill = SheetState.Firemaking(available)) }
                }
                return
            }
            Skills.RUNECRAFTING -> {
                viewModelScope.launch {
                    val inv: Map<String, Int> = json.decodeFromString(playerRepo.getOrCreatePlayer().inventory)
                    val essenceQty = inv["rune_essence"] ?: 0
                    val available = gameData.runes.filter { (_, rune) -> rune.levelRequired <= rcLevel }
                    _uiState.update { it.copy(sheetSkill = SheetState.Runecrafting(available, essenceQty)) }
                }
                return
            }
            Skills.PRAYER -> {
                viewModelScope.launch {
                    val inv: Map<String, Int> = json.decodeFromString(playerRepo.getOrCreatePlayer().inventory)
                    val available = gameData.bones.filter { (key, _) -> (inv[key] ?: 0) > 0 }
                    _uiState.update {
                        it.copy(sheetSkill = SheetState.Prayer(available, inv.filterKeys { k -> k in gameData.bones }))
                    }
                }
                return
            }
            Skills.SMITHING,
            Skills.COOKING,
            Skills.FLETCHING,
            Skills.CRAFTING,
            Skills.HERBLORE -> SheetState.Crafting(skillKey)
            else             -> SheetState.ComingSoon
        }
        _uiState.update { it.copy(sheetSkill = sheet) }
    }

    fun dismissSheet() = _uiState.update { it.copy(sheetSkill = null, selectedRecipe = null) }

    // ------------------------------------------------------------------
    // Gathering sessions (no material consumption at queue time)
    // ------------------------------------------------------------------

    fun startMiningSession(oreKey: String)          = enqueueGathering(Skills.MINING,     "Mining",     oreKey)
    fun startWoodcuttingSession(treeKey: String)    = enqueueGathering(Skills.WOODCUTTING, "Woodcutting", treeKey)
    fun startFishingSession(fishKey: String)        = enqueueGathering(Skills.FISHING,    "Fishing",    fishKey)
    fun startAgilitySession(courseKey: String)      = enqueueGathering(Skills.AGILITY,    "Agility",    courseKey)

    private fun enqueueGathering(skillName: String, displayName: String, activityKey: String) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            val tier = flags.hiredWorker?.tier ?: return@launch
            val enqueued = playerRepo.enqueueWorkerAction(
                QueuedAction(
                    skillName           = skillName,
                    activityKey         = activityKey,
                    skillDisplayName    = displayName,
                    estimatedDurationMs = tier.durationMs,
                )
            )
            if (enqueued) {
                workerStarter.startNextQueued()
                _uiState.update { it.copy(sheetSkill = null) }
            } else {
                _uiState.update { it.copy(snackbarMessage = "Worker is already busy.", sheetSkill = null) }
            }
        }
    }

    // ------------------------------------------------------------------
    // Firemaking (consumes logs at queue time)
    // ------------------------------------------------------------------

    fun startFiremakingSession(logKey: String) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            val tier  = flags.hiredWorker?.tier ?: return@launch
            if (!playerRepo.consumeItems(mapOf(logKey to 1))) {
                _uiState.update { it.copy(snackbarMessage = "Not enough logs.", sheetSkill = null) }
                return@launch
            }
            val enqueued = playerRepo.enqueueWorkerAction(
                QueuedAction(
                    skillName           = Skills.FIREMAKING,
                    activityKey         = logKey,
                    skillDisplayName    = "Firemaking",
                    estimatedDurationMs = tier.durationMs,
                )
            )
            if (enqueued) {
                workerStarter.startNextQueued()
                _uiState.update { it.copy(sheetSkill = null) }
            } else {
                playerRepo.addItem(logKey, 1)
                _uiState.update { it.copy(snackbarMessage = "Worker is already busy.", sheetSkill = null) }
            }
        }
    }

    // ------------------------------------------------------------------
    // Runecrafting (consumes rune essence at queue time)
    // ------------------------------------------------------------------

    fun startRunecraftingSession(runeKey: String, qty: Int) {
        viewModelScope.launch {
            val runeData = gameData.runes[runeKey] ?: return@launch
            val flags = playerRepo.getFlags()
            val tier  = flags.hiredWorker?.tier ?: return@launch
            val perItemMs = tier.craftingPerItemMs
            val qty = qty.coerceAtMost(tier.maxCraftQty)

            val totalEssence = runeData.essenceCost * qty
            if (!playerRepo.consumeItems(mapOf("rune_essence" to totalEssence))) {
                _uiState.update { it.copy(snackbarMessage = "Not enough Rune Essence.", sheetSkill = null) }
                return@launch
            }
            val enqueued = playerRepo.enqueueWorkerAction(
                QueuedAction(
                    skillName           = Skills.RUNECRAFTING,
                    activityKey         = runeKey,
                    skillDisplayName    = "Runecrafting",
                    qty                 = qty,
                    estimatedDurationMs = qty.toLong() * perItemMs,
                )
            )
            if (enqueued) {
                workerStarter.startNextQueued()
                _uiState.update { it.copy(sheetSkill = null) }
            } else {
                playerRepo.addItem("rune_essence", totalEssence)
                _uiState.update { it.copy(snackbarMessage = "Worker is already busy.", sheetSkill = null) }
            }
        }
    }

    // ------------------------------------------------------------------
    // Prayer (consumes bones at queue time)
    // ------------------------------------------------------------------

    fun startPrayerSession(boneKey: String, qty: Int) {
        viewModelScope.launch {
            val bone  = gameData.bones[boneKey] ?: return@launch
            val flags = playerRepo.getFlags()
            val tier  = flags.hiredWorker?.tier ?: return@launch
            val perBoneMs = tier.craftingPerItemMs
            val qty = qty.coerceAtMost(tier.maxCraftQty)

            if (!playerRepo.consumeItems(mapOf(boneKey to qty))) {
                _uiState.update { it.copy(snackbarMessage = "Not enough ${bone.displayName}.", sheetSkill = null) }
                return@launch
            }
            val enqueued = playerRepo.enqueueWorkerAction(
                QueuedAction(
                    skillName           = Skills.PRAYER,
                    activityKey         = boneKey,
                    skillDisplayName    = "Prayer",
                    qty                 = qty,
                    estimatedDurationMs = qty.toLong() * perBoneMs,
                )
            )
            if (enqueued) {
                workerStarter.startNextQueued()
                _uiState.update { it.copy(sheetSkill = null) }
            } else {
                playerRepo.addItem(boneKey, qty)
                _uiState.update { it.copy(snackbarMessage = "Worker is already busy.", sheetSkill = null) }
            }
        }
    }

    // ------------------------------------------------------------------
    // Crafting sheet (consumes materials at queue time)
    // ------------------------------------------------------------------

    fun openRecipe(recipe: CraftableRecipe) {
        val state = uiState.value
        val max = minOf(state.maxCraftable(recipe), state.maxCraftQty).coerceAtLeast(1)
        _uiState.update { it.copy(selectedRecipe = recipe, craftQuantity = max) }
    }

    fun dismissRecipe() = _uiState.update { it.copy(selectedRecipe = null) }

    fun setQuantity(qty: Int, max: Int) =
        _uiState.update { it.copy(craftQuantity = qty.coerceIn(1, max.coerceAtLeast(1))) }

    fun craft() {
        val state  = uiState.value
        val recipe = state.selectedRecipe ?: return
        val max    = state.maxCraftable(recipe).coerceAtLeast(1)
        val qty    = state.craftQuantity.coerceIn(1, max)

        viewModelScope.launch {
            val flags     = playerRepo.getFlags()
            val tier      = flags.hiredWorker?.tier ?: return@launch
            val perItemMs = tier.craftingPerItemMs
            val qty       = qty.coerceAtMost(tier.maxCraftQty)

            val totalMaterials = recipe.materials.mapValues { (_, v) -> v * qty }
            if (!playerRepo.consumeItems(totalMaterials)) {
                _uiState.update { it.copy(snackbarMessage = "Not enough materials.", selectedRecipe = null, sheetSkill = null) }
                return@launch
            }
            val enqueued = playerRepo.enqueueWorkerAction(
                QueuedAction(
                    skillName           = recipe.skillName,
                    activityKey         = recipe.key,
                    skillDisplayName    = recipe.skillName.replaceFirstChar { it.uppercase() },
                    qty                 = qty,
                    estimatedDurationMs = qty.toLong() * perItemMs,
                )
            )
            if (enqueued) {
                workerStarter.startNextQueued()
                _uiState.update { it.copy(selectedRecipe = null, sheetSkill = null) }
            } else {
                for ((item, needed) in totalMaterials) playerRepo.addItem(item, needed)
                _uiState.update { it.copy(snackbarMessage = "Worker is already busy.", selectedRecipe = null, sheetSkill = null) }
            }
        }
    }

    fun snackbarConsumed() = _uiState.update { it.copy(snackbarMessage = null) }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private val TOOL_TIERS = listOf(1, 15, 30, 55, 70, 85)
    private fun tierIndex(level: Int) = TOOL_TIERS.indexOfLast { it <= level }.coerceAtLeast(0)

    private fun toolEfficiency(itemKey: String?, slot: String): Float {
        if (itemKey == null) return 1.0f
        val eq = gameData.equipment[itemKey] ?: return 1.0f
        return when (slot) {
            EquipSlot.PICKAXE     -> eq.miningEfficiency      ?: 1.0f
            EquipSlot.AXE         -> eq.woodcuttingEfficiency ?: 1.0f
            EquipSlot.FISHING_ROD -> eq.fishingEfficiency     ?: 1.0f
            else                  -> 1.0f
        }
    }
}
