package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.simulator.XpTable
import kotlinx.serialization.serializer
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
// Unified recipe model (normalises all 4 recipe types for display + crafting)
// ---------------------------------------------------------------------------

data class CraftableRecipe(
    val key: String,
    val displayName: String,
    val levelRequired: Int,
    /** Ingredients per single craft action (before multiplying by quantity). */
    val materials: Map<String, Int>,
    /** Item key added to inventory on success. */
    val outputKey: String,
    val outputQty: Int,
    val xpPerItem: Double,
    val skillName: String,
    val outputAttackBonus: Int = 0,
    val outputStrengthBonus: Int = 0,
    val outputDefenseBonus: Int = 0,
    val outputHealingValue: Int = 0,
    val outputDamage: Int = 0,
    val outputRequirements: Map<String, Int> = emptyMap(),
    /** Broad category for filter chips (e.g. "Weapon", "Armour", "Bar", "Food"). */
    val category: String = "",
    /** Material tier for filter chips (e.g. "Bronze", "Iron", "Rune"). */
    val tier: String = "",
)

private fun tierFromKey(key: String) =
    key.substringBefore('_').replaceFirstChar { it.uppercase() }

private val ARMOUR_SLOTS = setOf(
    EquipSlot.HEAD, EquipSlot.BODY, EquipSlot.LEGS,
    EquipSlot.BOOTS, EquipSlot.CAPE, EquipSlot.SHIELD,
)

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

data class CraftingUiState(
    val smithingLevel:  Int = 1,
    val cookingLevel:   Int = 1,
    val fletchingLevel: Int = 1,
    val craftingLevel:  Int = 1,
    val skillLevels:    Map<String, Int> = emptyMap(),
    val inventory:      Map<String, Int> = emptyMap(),
    /** Inventory minus materials already reserved by active session + queue. */
    val effectiveInventory: Map<String, Int> = emptyMap(),
    /** Non-null while the craft-quantity sheet is open. */
    val selectedRecipe: CraftableRecipe? = null,
    val craftQuantity:  Int = 1,
    val snackbarMessage: String? = null,
    val isLoading: Boolean = true,
) {
    /** Returns how many times [recipe] can be crafted given [effectiveInventory]. */
    fun maxCraftable(recipe: CraftableRecipe): Int {
        if (recipe.materials.isEmpty()) return 0
        return recipe.materials.minOf { (item, needed) ->
            (effectiveInventory[item] ?: 0) / needed
        }
    }

    /** True if the player meets the level requirement for [recipe]. */
    fun meetsLevel(recipe: CraftableRecipe): Boolean = when (recipe.skillName) {
        Skills.SMITHING  -> smithingLevel  >= recipe.levelRequired
        Skills.COOKING   -> cookingLevel   >= recipe.levelRequired
        Skills.FLETCHING -> fletchingLevel >= recipe.levelRequired
        Skills.CRAFTING  -> craftingLevel  >= recipe.levelRequired
        else             -> false
    }
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class CraftingViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val questRepo: QuestRepository,
    private val json: Json,
) : ViewModel() {

    private val _extra = MutableStateFlow(CraftingUiState())

    val uiState: StateFlow<CraftingUiState> = combine(
        playerRepo.playerFlow,
        sessionRepo.activeSessionFlow,
        _extra,
    ) { player, activeSession, extra ->
        if (player == null) {
            extra
        } else {
            val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            val inventory: Map<String, Int> = json.decodeFromString(player.inventory)
            val flags: PlayerFlags = json.decodeFromString(player.flags)
            val allRecipes = smithingRecipes + cookingRecipes + fletchingRecipes + jewelleryRecipes
            extra.copy(
                smithingLevel      = levels[Skills.SMITHING]  ?: 1,
                cookingLevel       = levels[Skills.COOKING]   ?: 1,
                fletchingLevel     = levels[Skills.FLETCHING] ?: 1,
                craftingLevel      = levels[Skills.CRAFTING]  ?: 1,
                skillLevels        = levels,
                inventory          = inventory,
                effectiveInventory = computeEffectiveInventory(inventory, activeSession, flags.sessionQueue, allRecipes),
                isLoading          = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CraftingUiState())

    // ------------------------------------------------------------------
    // Recipe lists (normalised)
    // ------------------------------------------------------------------

    val smithingRecipes: List<CraftableRecipe> by lazy {
        gameData.smithingRecipes.map { (key, r) ->
            val equip = gameData.equipment[key]
            val category = when (r.type) {
                "bar"       -> "Bar"
                "component" -> "Component"
                "tool"      -> "Tool"
                "equipment" -> when (equip?.slot) {
                    EquipSlot.WEAPON -> "Weapon"
                    in ARMOUR_SLOTS  -> "Armour"
                    else             -> "Equipment"
                }
                else -> ""
            }
            CraftableRecipe(
                key                 = key,
                displayName         = r.displayName,
                levelRequired       = r.levelRequired,
                materials           = r.materials,
                outputKey           = key,
                outputQty           = r.outputQuantity,
                xpPerItem           = r.xpPerItem,
                skillName           = Skills.SMITHING,
                outputAttackBonus   = equip?.attackBonus    ?: 0,
                outputStrengthBonus = equip?.strengthBonus  ?: 0,
                outputDefenseBonus  = equip?.defenseBonus   ?: 0,
                outputRequirements  = equip?.requirements   ?: emptyMap(),
                category            = category,
                tier                = tierFromKey(key),
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
            val category = when (r.type) {
                "component"  -> "Component"
                "ammunition" -> "Ammunition"
                "weapon"     -> "Weapon"
                else         -> ""
            }
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
                category            = category,
                tier                = tierFromKey(r.itemName),
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
                outputAttackBonus   = equip?.attackBonus    ?: 0,
                outputStrengthBonus = equip?.strengthBonus  ?: 0,
                outputDefenseBonus  = equip?.defenseBonus   ?: 0,
                outputRequirements  = equip?.requirements   ?: emptyMap(),
                category            = "Jewellery",
                tier                = tierFromKey(key),
            )
        }.sortedBy { it.levelRequired }
    }

    // ------------------------------------------------------------------
    // Craft sheet
    // ------------------------------------------------------------------

    fun openRecipe(recipe: CraftableRecipe) {
        val max = uiState.value.maxCraftable(recipe).coerceAtLeast(1)
        _extra.update { it.copy(selectedRecipe = recipe, craftQuantity = max) }
    }

    fun dismissRecipe() = _extra.update { it.copy(selectedRecipe = null) }

    /** [max] should come from the combined uiState (which has inventory), not _extra. */
    fun setQuantity(qty: Int, max: Int) =
        _extra.update { it.copy(craftQuantity = qty.coerceIn(1, max.coerceAtLeast(1))) }

    fun craft() {
        val state  = uiState.value          // combined state — has inventory
        val recipe = state.selectedRecipe ?: return
        val max    = state.maxCraftable(recipe).coerceAtLeast(1)
        val qty    = state.craftQuantity.coerceIn(1, max)

        viewModelScope.launch {
            // Enqueue if a session is already running
            if (sessionRepo.getActiveSession() != null) {
                val action = QueuedAction(
                    skillName        = recipe.skillName,
                    activityKey      = recipe.key,
                    skillDisplayName = recipe.skillName.replaceFirstChar { it.uppercase() },
                    qty              = qty,
                )
                val enqueued = playerRepo.enqueueAction(action)
                _extra.update {
                    it.copy(
                        snackbarMessage = if (enqueued) "Added to queue: ${recipe.displayName}." else "Queue is full (3/3).",
                        selectedRecipe  = null,
                    )
                }
                return@launch
            }

            // Build frames — 1 item crafted per minute
            val player = playerRepo.getOrCreatePlayer()
            val freshInv: Map<String, Int> = json.decodeFromString(player.inventory)
            if (!recipe.materials.all { (item, needed) -> (freshInv[item] ?: 0) >= needed * qty }) {
                _extra.update { it.copy(snackbarMessage = "Not enough materials") }
                return@launch
            }
            val xpMap: Map<String, Long> = json.decodeFromString(player.skillXp)
            var currentXp = xpMap[recipe.skillName] ?: 0L
            val frames = mutableListOf<SessionFrame>()
            for (i in 1..qty) {
                val xpBefore    = currentXp
                val levelBefore = XpTable.levelForXp(currentXp)
                val xpGain      = recipe.xpPerItem.toInt()
                currentXp      += xpGain
                val levelAfter  = XpTable.levelForXp(currentXp)
                frames += SessionFrame(
                    minute      = i,
                    xpGain      = xpGain,
                    xpBefore    = xpBefore,
                    xpAfter     = currentXp,
                    levelBefore = levelBefore,
                    levelAfter  = levelAfter,
                    items       = mapOf(recipe.outputKey to recipe.outputQty),
                    leveledUp   = levelAfter > levelBefore,
                )
            }

            val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            val agilityLevel = levels[Skills.AGILITY] ?: 1
            // 1 item per minute, reduced by agility (same formula as gathering skills)
            val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel) / 60

            val framesJson = json.encodeToString(
                json.serializersModule.serializer<List<SessionFrame>>(),
                frames,
            )
            sessionRepo.startSession(
                skillName        = recipe.skillName,
                activityKey      = recipe.key,
                frames           = framesJson,
                durationMs       = qty * perItemMs,
                skillDisplayName = recipe.skillName,
            )
            _extra.update { it.copy(selectedRecipe = null) }
        }
    }

    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }

    private val craftingSkills = setOf(Skills.SMITHING, Skills.COOKING, Skills.FLETCHING, Skills.CRAFTING)

    private fun computeEffectiveInventory(
        inventory: Map<String, Int>,
        activeSession: SkillSession?,
        queue: List<QueuedAction>,
        allRecipes: List<CraftableRecipe>,
    ): Map<String, Int> {
        val eff = inventory.toMutableMap()
        val activityKeys = buildList {
            activeSession?.let { if (it.skillName in craftingSkills) add(it.activityKey) }
            for (action in queue) {
                if (action.skillName in craftingSkills) add(action.activityKey)
            }
        }
        for (key in activityKeys) {
            val recipe = allRecipes.find { it.key == key } ?: continue
            if (recipe.materials.isEmpty()) continue
            val qty = recipe.materials.minOf { (item, needed) ->
                (eff[item] ?: 0) / needed.coerceAtLeast(1)
            }
            if (qty <= 0) continue
            for ((item, needed) in recipe.materials) {
                eff[item] = (eff[item] ?: 0) - qty * needed
            }
        }
        return eff
    }
}
