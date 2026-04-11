package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.model.SessionFrame
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
)

// ---------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------

data class CraftingUiState(
    val smithingLevel:  Int = 1,
    val cookingLevel:   Int = 1,
    val fletchingLevel: Int = 1,
    val craftingLevel:  Int = 1,
    val inventory:      Map<String, Int> = emptyMap(),
    /** Non-null while the craft-quantity sheet is open. */
    val selectedRecipe: CraftableRecipe? = null,
    val craftQuantity:  Int = 1,
    val snackbarMessage: String? = null,
    val isLoading: Boolean = true,
) {
    /** Returns how many times [recipe] can be crafted given [inventory]. */
    fun maxCraftable(recipe: CraftableRecipe): Int {
        if (recipe.materials.isEmpty()) return 0
        return recipe.materials.minOf { (item, needed) ->
            (inventory[item] ?: 0) / needed
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
        _extra,
    ) { player, extra ->
        if (player == null) {
            extra
        } else {
            val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            val inventory: Map<String, Int> = json.decodeFromString(player.inventory)
            extra.copy(
                smithingLevel  = levels[Skills.SMITHING]  ?: 1,
                cookingLevel   = levels[Skills.COOKING]   ?: 1,
                fletchingLevel = levels[Skills.FLETCHING] ?: 1,
                craftingLevel  = levels[Skills.CRAFTING]  ?: 1,
                inventory      = inventory,
                isLoading      = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CraftingUiState())

    // ------------------------------------------------------------------
    // Recipe lists (normalised)
    // ------------------------------------------------------------------

    val smithingRecipes: List<CraftableRecipe> by lazy {
        gameData.smithingRecipes.map { (key, r) ->
            CraftableRecipe(
                key          = key,
                displayName  = r.displayName,
                levelRequired = r.levelRequired,
                materials    = r.materials,
                outputKey    = key,
                outputQty    = r.outputQuantity,
                xpPerItem    = r.xpPerItem,
                skillName    = Skills.SMITHING,
            )
        }.sortedBy { it.levelRequired }
    }

    val cookingRecipes: List<CraftableRecipe> by lazy {
        gameData.cookingRecipes.map { (key, r) ->
            CraftableRecipe(
                key           = key,
                displayName   = r.displayName,
                levelRequired = r.levelRequired,
                materials     = mapOf(r.rawItem to 1),
                outputKey     = r.cookedItem,
                outputQty     = 1,
                xpPerItem     = r.xpPerItem,
                skillName     = Skills.COOKING,
            )
        }.sortedBy { it.levelRequired }
    }

    val fletchingRecipes: List<CraftableRecipe> by lazy {
        gameData.fletchingRecipes.map { (_, r) ->
            CraftableRecipe(
                key           = r.itemName,
                displayName   = r.displayName,
                levelRequired = r.levelRequired,
                materials     = r.materials,
                outputKey     = r.itemName,
                outputQty     = r.outputQuantity,
                xpPerItem     = r.xpPerItem,
                skillName     = Skills.FLETCHING,
            )
        }.sortedBy { it.levelRequired }
    }

    val jewelleryRecipes: List<CraftableRecipe> by lazy {
        gameData.craftingRecipes.map { (key, r) ->
            CraftableRecipe(
                key           = key,
                displayName   = r.displayName,
                levelRequired = r.levelRequired,
                materials     = r.materials,
                outputKey     = key,
                outputQty     = r.outputQuantity,
                xpPerItem     = r.xpPerItem,
                skillName     = Skills.CRAFTING,
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
            // Block if any session is already running
            if (sessionRepo.getActiveSession() != null) {
                _extra.update {
                    it.copy(
                        snackbarMessage = "Finish your current session first.",
                        selectedRecipe  = null,
                    )
                }
                return@launch
            }

            // Consume materials immediately (locks them in for this session)
            val consumed = playerRepo.consumeMaterials(recipe.materials, qty)
            if (!consumed) {
                _extra.update { it.copy(snackbarMessage = "Not enough materials") }
                return@launch
            }

            // Build frames — 1 item crafted per minute
            val player = playerRepo.getOrCreatePlayer()
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
}
