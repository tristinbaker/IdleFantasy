package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.Skills
import com.fantasyidler.data.json.HerbloreRecipe
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
    /** Combat stat bonuses granted by this consumable (herblore only). */
    val effects: Map<String, Int> = emptyMap(),
    /** Combat style of the output weapon, if applicable (e.g. "attack", "ranged", "magic"). */
    val outputCombatStyle: String? = null,
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
    val herbloreLevel:  Int = 1,
    val skillLevels:    Map<String, Int> = emptyMap(),
    val skillXp:        Map<String, Long> = emptyMap(),
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
        Skills.HERBLORE  -> herbloreLevel  >= recipe.levelRequired
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

    private val scrollIndices = mutableMapOf<Int, Int>()
    fun getScrollIndex(tab: Int): Int = scrollIndices[tab] ?: 0
    fun saveScrollIndex(tab: Int, index: Int) { scrollIndices[tab] = index }

    val uiState: StateFlow<CraftingUiState> = combine(
        playerRepo.playerFlow,
        sessionRepo.activeSessionFlow,
        _extra,
    ) { player, _, extra ->
        if (player == null) {
            extra
        } else {
            val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            val xp: Map<String, Long> = json.decodeFromString(player.skillXp)
            val inventory: Map<String, Int> = json.decodeFromString(player.inventory)
            extra.copy(
                smithingLevel      = levels[Skills.SMITHING]  ?: 1,
                cookingLevel       = levels[Skills.COOKING]   ?: 1,
                fletchingLevel     = levels[Skills.FLETCHING] ?: 1,
                craftingLevel      = levels[Skills.CRAFTING]  ?: 1,
                herbloreLevel      = levels[Skills.HERBLORE]  ?: 1,
                skillLevels        = levels,
                skillXp            = xp,
                inventory          = inventory,
                effectiveInventory = computeEffectiveInventory(inventory),
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
                outputCombatStyle   = equip?.combatStyle,
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
                outputCombatStyle   = gameData.equipment[r.itemName]?.combatStyle,
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
                outputCombatStyle   = equip?.combatStyle,
                category            = "Jewellery",
                tier                = tierFromKey(key),
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
                val agility   = state.skillLevels[Skills.AGILITY] ?: 1
                val perItemMs = SkillSimulator.sessionDurationMs(agility) / 60
                val action = QueuedAction(
                    skillName           = recipe.skillName,
                    activityKey         = recipe.key,
                    skillDisplayName    = recipe.skillName.replaceFirstChar { it.uppercase() },
                    qty                 = qty,
                    estimatedDurationMs = qty.toLong() * perItemMs,
                )
                val enqueued = playerRepo.enqueueAction(action)
                if (enqueued) playerRepo.consumeItems(recipe.materials.mapValues { it.value * qty })
                _extra.update {
                    it.copy(
                        snackbarMessage = if (enqueued) "Added to queue: ${recipe.displayName}." else "Queue is full (3/3).",
                        selectedRecipe  = null,
                    )
                }
                return@launch
            }

            // Build a single aggregate frame regardless of qty to stay within
            // Android's 2 MB CursorWindow per-row limit.
            val player = playerRepo.getOrCreatePlayer()
            val freshInv: Map<String, Int> = json.decodeFromString(player.inventory)
            if (!recipe.materials.all { (item, needed) -> (freshInv[item] ?: 0) >= needed * qty }) {
                _extra.update { it.copy(snackbarMessage = "Not enough materials") }
                return@launch
            }
            val xpMap: Map<String, Long> = json.decodeFromString(player.skillXp)
            val startXp     = xpMap[recipe.skillName] ?: 0L
            val levelBefore = XpTable.levelForXp(startXp)
            val totalXpGain = (qty * recipe.xpPerItem).toInt()
            val xpAfter     = startXp + totalXpGain
            val levelAfter  = XpTable.levelForXp(xpAfter)
            val frames = listOf(
                SessionFrame(
                    minute      = 1,
                    xpGain      = totalXpGain,
                    xpBefore    = startXp,
                    xpAfter     = xpAfter,
                    levelBefore = levelBefore,
                    levelAfter  = levelAfter,
                    items       = mapOf(recipe.outputKey to recipe.outputQty * qty),
                    leveledUp   = levelAfter > levelBefore,
                    kills       = qty,
                )
            )

            val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            val agilityLevel = levels[Skills.AGILITY] ?: 1
            // 1 item per minute, reduced by agility (same formula as gathering skills)
            val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel) / 60

            val framesJson = json.encodeToString(
                json.serializersModule.serializer<List<SessionFrame>>(),
                frames,
            )
            playerRepo.consumeItems(recipe.materials.mapValues { it.value * qty })
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

    private val craftingSkills = setOf(Skills.SMITHING, Skills.COOKING, Skills.FLETCHING, Skills.CRAFTING, Skills.HERBLORE)

    private fun computeEffectiveInventory(inventory: Map<String, Int>): Map<String, Int> {
        // Materials are now consumed from inventory at session start/queue time,
        // so the actual inventory is already the ground truth.
        return inventory
    }
}
