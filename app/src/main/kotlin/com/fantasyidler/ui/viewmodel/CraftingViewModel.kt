package com.fantasyidler.ui.viewmodel

import com.fantasyidler.util.withAppLocale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.repository.ChurchRepository
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.Skills
import com.fantasyidler.data.json.HerbloreRecipe
import com.fantasyidler.data.model.QuestProgress
import com.fantasyidler.repository.DailyQuestRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.GuildRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.SeasonalEventRepository
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.repository.TownRepository
import com.fantasyidler.repository.WeeklyQuestRepository
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.simulator.XpTable
import com.fantasyidler.util.craftDurationEfficiency
import kotlinx.serialization.serializer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import com.fantasyidler.util.GameStrings
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import android.content.Context
import com.fantasyidler.R
import dagger.hilt.android.qualifiers.ApplicationContext

// ---------------------------------------------------------------------------
// Quest fill suggestion (shown in CraftSheet when quests match the recipe)
// ---------------------------------------------------------------------------

data class QuestFillSuggestion(val label: String, val qty: Int)

/** Ordinal order is the display order of the indicator icons. */
enum class QuestCategory(val emoji: String) {
    DAILY("⏰"),
    WEEKLY("📅"),
    GUILD_DAILY("⚒️"),
    GUILD("🏰"),
    MAIN("📜"),
}

data class QuestIndicator(
    val category: QuestCategory,
    val isCompletable: Boolean,
    /** Source quest id, used to dedupe skill-row counts when one quest spans many activities. */
    val questId: String = "",
)

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
    val smithingLevel:      Int = 1,
    val cookingLevel:       Int = 1,
    val fletchingLevel:     Int = 1,
    val craftingLevel:      Int = 1,
    val herbloreLevel:      Int = 1,
    val constructionLevel:  Int = 1,
    val skillLevels:        Map<String, Int> = emptyMap(),
    val skillXp:        Map<String, Long> = emptyMap(),
    val inventory:      Map<String, Int> = emptyMap(),
    /** Inventory minus materials already reserved by active session + queue. */
    val effectiveInventory: Map<String, Int> = emptyMap(),
    /** Non-null while the craft-quantity sheet is open. */
    val selectedRecipe: CraftableRecipe? = null,
    /** Ash catalyst key selected for a herblore brew, or null for no catalyst. */
    val herbloreAshKey: String? = null,
    val snackbarMessage: String? = null,
    val isLoading: Boolean = true,
    val questFills: List<QuestFillSuggestion> = emptyList(),
    val recipeQuests: Map<String, List<QuestIndicator>> = emptyMap(),
    /** Actual per-item craft duration for [selectedRecipe], tool efficiency applied. 0 if nothing selected. */
    val craftPerItemMs: Long = 0L,
    val isQueueFull: Boolean = false,
    /** Full XP multiplier for [selectedRecipe] (tool efficiency, pet, XP boost, blessing), matching what collection awards. */
    val craftXpMult: Double = 1.0,
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
        Skills.SMITHING      -> smithingLevel      >= recipe.levelRequired
        Skills.COOKING       -> cookingLevel       >= recipe.levelRequired
        Skills.FLETCHING     -> fletchingLevel     >= recipe.levelRequired
        Skills.CRAFTING      -> craftingLevel      >= recipe.levelRequired
        Skills.HERBLORE      -> herbloreLevel      >= recipe.levelRequired
        Skills.CONSTRUCTION  -> constructionLevel  >= recipe.levelRequired
        else                 -> false
    }
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class CraftingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val questRepo: QuestRepository,
    private val dailyQuestRepo: DailyQuestRepository,
    private val weeklyQuestRepo: WeeklyQuestRepository,
    private val guildRepo: GuildRepository,
    private val seasonalEventRepo: SeasonalEventRepository,
    private val townRepo: TownRepository,
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
        questRepo.observeProgress(),
    ) { player, _, extra, questProgress ->
        if (player == null) {
            extra
        } else {
            val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            val xp: Map<String, Long> = json.decodeFromString(player.skillXp)
            val inventory: Map<String, Int> = json.decodeFromString(player.inventory)
            val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
            val flags: PlayerFlags = json.decodeFromString(player.flags)
            val effInv = computeEffectiveInventory(inventory)
            val selectedRecipe = extra.selectedRecipe
            val selectedEff = if (selectedRecipe != null) craftToolEfficiency(selectedRecipe, equipped) else 1.0f
            val perItemMs = if (selectedRecipe != null) {
                val agility = levels[Skills.AGILITY] ?: 1
                (SkillSimulator.sessionDurationMs(agility, flags.skillPrestige[Skills.AGILITY] ?: 0, townRepo.playerSessionDurationMultiplier(flags)) / 60 / selectedEff).toLong()
            } else 0L
            val xpMult = if (selectedRecipe != null) {
                val boostMult = if (flags.ironman) 1.0
                                else (if (flags.xpBoostExpiresAt > System.currentTimeMillis()) 2.0 else 1.0) * ChurchRepository.xpMultiplier(flags, equipped, inventory.keys, gameData.equipment)
                val petPct = petBoostFor(player.pets, selectedRecipe.skillName, flags.ironman)
                selectedEff * boostMult * (1.0 + petPct / 100.0)
            } else 1.0
            extra.copy(
                smithingLevel      = levels[Skills.SMITHING]      ?: 1,
                cookingLevel       = levels[Skills.COOKING]       ?: 1,
                fletchingLevel     = levels[Skills.FLETCHING]     ?: 1,
                craftingLevel      = levels[Skills.CRAFTING]      ?: 1,
                herbloreLevel      = levels[Skills.HERBLORE]      ?: 1,
                constructionLevel  = levels[Skills.CONSTRUCTION]  ?: 1,
                skillLevels        = levels,
                skillXp            = xp,
                inventory          = inventory,
                effectiveInventory = effInv,
                isLoading          = false,
                questFills         = computeQuestFills(extra.selectedRecipe, questProgress, flags),
                recipeQuests       = computeRecipeQuests(allRecipes, questProgress, flags, effInv),
                craftPerItemMs     = perItemMs,
                isQueueFull        = flags.sessionQueue.size >= playerRepo.maxQueueSize(flags),
                craftXpMult        = xpMult,
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
            val isPlank = r.itemName == "plank" || r.itemName.endsWith("_plank")
            val isStaff = r.itemName.startsWith("staff_of_")
            // Base items with no material prefix would leak product words into the tier chips
            val untiered = isPlank || isStaff || r.itemName == "arrow_shaft" || r.itemName == "shortbow"
            val category = when {
                isPlank                                               -> "Plank"
                isStaff                                               -> "Staff"
                r.type == "ammunition" || r.itemName == "arrow_shaft" -> "Arrow"
                r.type == "weapon"                                    -> "Bow"
                r.type == "component"                                 -> "Component"
                else                                                  -> ""
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
                tier                = if (untiered) "" else tierFromKey(r.itemName),
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

    val constructionRecipes: List<CraftableRecipe> by lazy {
        gameData.constructionRecipes.map { (key, r) ->
            CraftableRecipe(
                key           = key,
                displayName   = r.displayName,
                levelRequired = r.levelRequired,
                materials     = r.materials,
                outputKey     = key,
                outputQty     = r.outputQuantity,
                xpPerItem     = r.xpPerItem,
                skillName     = Skills.CONSTRUCTION,
                category      = "Furniture",
            )
        }.sortedBy { it.levelRequired }
    }

    // ------------------------------------------------------------------
    // Craft sheet
    // ------------------------------------------------------------------

    fun openRecipe(recipe: CraftableRecipe) =
        _extra.update { it.copy(selectedRecipe = recipe) }

    fun dismissRecipe() = _extra.update { it.copy(selectedRecipe = null, herbloreAshKey = null) }

    fun setHerbloreAsh(key: String?) = _extra.update { it.copy(herbloreAshKey = key) }

    private fun craftToolEfficiency(recipe: CraftableRecipe, equipped: Map<String, String?>): Float =
        gameData.craftDurationEfficiency(recipe.skillName, recipe.key, equipped)

    private fun petBoostFor(petsJson: String, skillKey: String, ironman: Boolean = false): Int {
        if (ironman) return 0
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

    /**
     * Quick-queues the recipe producing [targetKey] toward a guild daily.
     * Returns false when no known recipe outputs the target; shows the usual
     * snackbar when the level or materials fall short.
     */
    fun queueCraftForDaily(targetKey: String, remaining: Int): Boolean {
        val recipe = allRecipes.firstOrNull { it.outputKey == targetKey } ?: return false
        val state  = uiState.value
        val max    = state.maxCraftable(recipe)
        if ((state.skillLevels[recipe.skillName] ?: 1) < recipe.levelRequired || max <= 0) {
            _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.skill_not_enough_materials)) }
            return true
        }
        craft(recipe, ceilDiv(remaining, recipe.outputQty).coerceIn(1, max))
        return true
    }

    /** Starts or enqueues [qty] crafts of [recipe]. */
    fun craft(recipe: CraftableRecipe, qty: Int, ashKey: String? = null) {
        val state = uiState.value

        viewModelScope.launch {
            val player = playerRepo.getOrCreatePlayer()
            val flags: PlayerFlags = json.decodeFromString(player.flags)
            val saveChance = townRepo.secondaryMaterialSaveChance(flags)
            val matsToConsume = applyMaterialPreservation(recipe.materials, qty, saveChance)
            val ashQtyToConsume = if (ashKey != null) applyQtyPreservation(qty, saveChance) else 0

            // Enqueue if a session is already running
            if (sessionRepo.getActiveSession() != null) {
                val agility   = state.skillLevels[Skills.AGILITY] ?: 1
                val toolEff   = craftToolEfficiency(recipe, json.decodeFromString(player.equipped))
                val perItemMs = (SkillSimulator.sessionDurationMs(agility, flags.skillPrestige[Skills.AGILITY] ?: 0, townRepo.playerSessionDurationMultiplier(flags)) / 60 / toolEff).toLong()
                val totalOutput = qty * recipe.outputQty
                val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
                val inventory: Map<String, Int> = json.decodeFromString(player.inventory)
                val xpQueueMult = if (flags.ironman) 1.0 else (if (flags.xpBoostExpiresAt > System.currentTimeMillis()) 2.0 else 1.0) * ChurchRepository.xpMultiplier(flags, equipped, inventory.keys, gameData.equipment)
                val queuePetPct = petBoostFor(player.pets, recipe.skillName, flags.ironman)
                val action = QueuedAction(
                    skillName           = recipe.skillName,
                    activityKey         = recipe.key,
                    skillDisplayName    = recipe.skillName.replaceFirstChar { it.uppercase() },
                    qty                 = qty,
                    outputQty           = if (totalOutput != qty) totalOutput else 0,
                    estimatedXpGain     = (qty * recipe.xpPerItem * xpQueueMult * toolEff * (1.0 + queuePetPct / 100.0)).toLong(),
                    estimatedDurationMs = qty.toLong() * perItemMs,
                    catalystKey         = ashKey,
                    catalystQty         = ashQtyToConsume,
                )
                val enqueued = playerRepo.enqueueAction(action)
                if (enqueued) {
                    playerRepo.consumeItems(matsToConsume)
                    if (ashKey != null && ashQtyToConsume > 0) playerRepo.consumeItems(mapOf(ashKey to ashQtyToConsume))
                }
                _extra.update {
                    it.copy(
                        snackbarMessage = if (enqueued) context.withAppLocale().getString(R.string.snackbar_added_to_queue, recipe.displayName) else context.withAppLocale().getString(R.string.snackbar_queue_full),
                        selectedRecipe  = null,
                    )
                }
                return@launch
            }

            // Build a single aggregate frame regardless of qty to stay within
            // Android's 2 MB CursorWindow per-row limit.
            val freshInv: Map<String, Int> = json.decodeFromString(player.inventory)
            if (!recipe.materials.all { (item, needed) -> (freshInv[item] ?: 0) >= needed * qty }) {
                _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.skill_not_enough_materials)) }
                return@launch
            }
            val xpMap: Map<String, Long> = json.decodeFromString(player.skillXp)
            val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
            val startXp     = xpMap[recipe.skillName] ?: 0L
            val levelBefore = XpTable.levelForXp(startXp)
            val efficiency = craftToolEfficiency(recipe, equipped)
            val petPct = petBoostFor(player.pets, recipe.skillName, flags.ironman)
            val totalXpGain = (qty * recipe.xpPerItem * efficiency * (1.0 + petPct / 100.0)).toInt()
            val xpAfter     = startXp + totalXpGain
            val levelAfter  = XpTable.levelForXp(xpAfter)
            val outputKey = if (ashKey != null && recipe.skillName == Skills.HERBLORE)
                "enhanced_${recipe.outputKey}" else recipe.outputKey
            val frames = listOf(
                SessionFrame(
                    minute      = 1,
                    xpGain      = totalXpGain,
                    xpBefore    = startXp,
                    xpAfter     = xpAfter,
                    levelBefore = levelBefore,
                    levelAfter  = levelAfter,
                    items       = mapOf(outputKey to recipe.outputQty * qty),
                    leveledUp   = levelAfter > levelBefore,
                    kills       = qty,
                )
            )

            val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            val agilityLevel = levels[Skills.AGILITY] ?: 1
            // 1 item per minute, reduced by agility (same formula as gathering skills) and by tool efficiency
            val perItemMs = (SkillSimulator.sessionDurationMs(agilityLevel, flags.skillPrestige[Skills.AGILITY] ?: 0, townRepo.playerSessionDurationMultiplier(flags)) / 60 / efficiency).toLong()

            val framesJson = json.encodeToString(
                json.serializersModule.serializer<List<SessionFrame>>(),
                frames,
            )
            playerRepo.consumeItems(matsToConsume)
            if (ashKey != null && ashQtyToConsume > 0) playerRepo.consumeItems(mapOf(ashKey to ashQtyToConsume))
            sessionRepo.startSession(
                skillName        = recipe.skillName,
                activityKey      = recipe.key,
                frames           = framesJson,
                durationMs       = qty * perItemMs,
                skillDisplayName = recipe.skillName,
                catalystKey      = ashKey,
                catalystQty      = ashQtyToConsume,
            )
            _extra.update { it.copy(selectedRecipe = null, herbloreAshKey = null) }
        }
    }

    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }

    private val craftingSkills = setOf(Skills.SMITHING, Skills.COOKING, Skills.FLETCHING, Skills.CRAFTING, Skills.HERBLORE, Skills.CONSTRUCTION)

    private fun computeEffectiveInventory(inventory: Map<String, Int>): Map<String, Int> {
        // Materials are now consumed from inventory at session start/queue time,
        // so the actual inventory is already the ground truth.
        return inventory
    }

    private fun computeQuestFills(
        recipe: CraftableRecipe?,
        questProgress: List<QuestProgress>,
        flags: PlayerFlags,
    ): List<QuestFillSuggestion> {
        if (recipe == null) return emptyList()
        val fills = mutableListOf<QuestFillSuggestion>()
        val progressById = questProgress.associateBy { it.questId }

        // Regular quests
        for ((id, quest) in gameData.quests) {
            if (quest.type != "craft" && quest.type != "craft_any") continue
            val prog = progressById[id]
            if (prog?.completed == true) continue
            val progress = prog?.progress ?: 0
            val matches = when (quest.type) {
                "craft"     -> quest.target == recipe.outputKey
                "craft_any" -> quest.skill == recipe.skillName && craftAnyTargetMatches(quest.target, recipe)
                else        -> false
            }
            if (matches) {
                val remaining = quest.amount - progress
                val prereqDone = quest.requiresPrevious == null ||
                        progressById[quest.requiresPrevious]?.completed == true
                if (remaining > 0 && prereqDone)
                    fills += QuestFillSuggestion(GameStrings.questName(context, id, quest.name), ceilDiv(remaining, recipe.outputQty))
            }
        }

        // Guild progression quests (guild_quests.json, tracked in same quest_progress table)
        val completedIds = progressById.entries.filter { it.value.completed }.map { it.key }.toSet()
        for ((id, quest) in gameData.guildQuests) {
            if (quest.type != "craft" && quest.type != "craft_any") continue
            val prog = progressById[id]
            if (prog?.completed == true) continue
            // Skip if the player's current guild level is below the quest's requirement
            if (guildRepo.guildLevel(quest.guild, flags.guildDailyTierCounts, completedIds) < quest.guildLevelRequired) continue
            val progress = prog?.progress ?: 0
            val matches = when (quest.type) {
                "craft"     -> quest.target == recipe.outputKey
                "craft_any" -> quest.guild  == recipe.skillName
                else        -> false
            }
            if (matches) {
                val effectiveAmount = guildRepo.effectiveQuestAmountFromFlags(quest, flags)
                val remaining = effectiveAmount - progress
                if (remaining > 0)
                    fills += QuestFillSuggestion(GameStrings.questName(context, id, quest.name), ceilDiv(remaining, recipe.outputQty))
            }
        }

        // Daily quests
        for (daily in dailyQuestRepo.getActiveDailyQuests(flags)) {
            if (daily.claimed) continue
            val remaining = daily.template.amount - daily.progress
            if (remaining <= 0) continue
            val matches = when (daily.template.type) {
                "craft"     -> daily.template.target == recipe.outputKey
                "craft_any" -> daily.template.skill  == recipe.skillName
                else        -> false
            }
            if (matches)
                fills += QuestFillSuggestion(context.withAppLocale().getString(R.string.quest_fill_daily), ceilDiv(remaining, recipe.outputQty))
        }

        // Weekly quests
        for (weekly in weeklyQuestRepo.getActiveWeeklyQuests(flags)) {
            if (weekly.claimed) continue
            val remaining = weekly.template.amount - weekly.progress
            if (remaining <= 0) continue
            val matches = when (weekly.template.type) {
                "craft"     -> weekly.template.target == recipe.outputKey
                "craft_any" -> weekly.template.skill  == recipe.skillName
                else        -> false
            }
            if (matches)
                fills += QuestFillSuggestion(context.withAppLocale().getString(R.string.quest_fill_weekly), ceilDiv(remaining, recipe.outputQty))
        }

        // Guild daily quests
        val guildPool = gameData.guildDailyPool.associateBy { it.id }
        val activeGuildIds = flags.guildDailyIds.filter { it !in flags.guildDailyClaimed }
        for (id in activeGuildIds) {
            val template = guildPool[id] ?: continue
            val progress = flags.guildDailyProgress[id] ?: 0
            val remaining = template.amount - progress
            if (remaining <= 0) continue
            val matches = when (template.type) {
                "craft"     -> template.target == recipe.outputKey
                "craft_any" -> template.guild  == recipe.skillName
                else        -> false
            }
            if (matches)
                fills += QuestFillSuggestion(context.withAppLocale().getString(R.string.quest_fill_guild), ceilDiv(remaining, recipe.outputQty))
        }

        // Seasonal Event Bounty Board
        seasonalEventRepo.activeEvent()?.let { event ->
            for (taskProgress in seasonalEventRepo.bountyTasksWithProgress(event, flags)) {
                if (taskProgress.cooldownUntilMs != null) continue
                val task = taskProgress.task
                if (task.type != "craft" || task.target != recipe.outputKey) continue
                val remaining = task.amount - taskProgress.progress
                if (remaining > 0)
                    fills += QuestFillSuggestion(GameStrings.seasonalEventName(context, event.id, event.displayName), ceilDiv(remaining, recipe.outputQty))
            }
        }

        return fills.sortedBy { it.qty }
    }

    private val allRecipes: List<CraftableRecipe> by lazy {
        smithingRecipes + cookingRecipes + fletchingRecipes + herbloreRecipes + constructionRecipes + jewelleryRecipes
    }

    private fun computeRecipeQuests(
        allRecipes: List<CraftableRecipe>,
        questProgress: List<QuestProgress>,
        flags: PlayerFlags,
        effectiveInventory: Map<String, Int>,
    ): Map<String, List<QuestIndicator>> {
        val result = mutableMapOf<String, MutableList<QuestIndicator>>()
        val progressById = questProgress.associateBy { it.questId }

        val activeDailies = dailyQuestRepo.getActiveDailyQuests(flags).filter { !it.claimed }
        val activeWeeklies = weeklyQuestRepo.getActiveWeeklyQuests(flags).filter { !it.claimed }
        val guildPool = gameData.guildDailyPool.associateBy { it.id }
        val activeGuildDailyIds = flags.guildDailyIds.filter { it !in flags.guildDailyClaimed }
        val completedIds = progressById.entries.filter { it.value.completed }.map { it.key }.toSet()

        for (recipe in allRecipes) {
            val key = recipe.outputKey
            val skill = recipe.skillName

            val max = if (recipe.materials.isEmpty()) 0
                      else recipe.materials.minOf { (item, needed) ->
                          (effectiveInventory[item] ?: 0) / needed
                      }

            val indicators = mutableListOf<QuestIndicator>()

            // 1. Regular Quests
            for ((id, quest) in gameData.quests) {
                if (quest.type != "craft" && quest.type != "craft_any") continue
                val prog = progressById[id]
                if (prog?.completed == true) continue
                val progress = prog?.progress ?: 0
                val remaining = quest.amount - progress
                if (remaining <= 0) continue

                val matches = when (quest.type) {
                    "craft"     -> quest.target == key
                    "craft_any" -> quest.skill == skill && craftAnyTargetMatches(quest.target, recipe)
                    else        -> false
                }
                if (matches) {
                    val prereqDone = quest.requiresPrevious == null ||
                            progressById[quest.requiresPrevious]?.completed == true
                    if (prereqDone) {
                        val neededCrafts = ceilDiv(remaining, recipe.outputQty)
                        indicators.add(QuestIndicator(QuestCategory.MAIN, max >= neededCrafts))
                    }
                }
            }

            // 2. Guild Progression Quests
            for ((id, quest) in gameData.guildQuests) {
                if (quest.type != "craft" && quest.type != "craft_any") continue
                val prog = progressById[id]
                if (prog?.completed == true) continue
                if (guildRepo.guildLevel(quest.guild, flags.guildDailyTierCounts, completedIds) < quest.guildLevelRequired) continue
                val progress = prog?.progress ?: 0
                val effectiveAmount = guildRepo.effectiveQuestAmountFromFlags(quest, flags)
                val remaining = effectiveAmount - progress
                if (remaining <= 0) continue

                val matches = when (quest.type) {
                    "craft"     -> quest.target == key
                    "craft_any" -> quest.guild  == skill
                    else        -> false
                }
                if (matches) {
                    val neededCrafts = ceilDiv(remaining, recipe.outputQty)
                    indicators.add(QuestIndicator(QuestCategory.GUILD, max >= neededCrafts))
                }
            }

            // 3. Daily Quests
            for (daily in activeDailies) {
                val remaining = daily.template.amount - daily.progress
                if (remaining <= 0) continue
                val matches = when (daily.template.type) {
                    "craft"     -> daily.template.target == key
                    "craft_any" -> daily.template.skill  == skill
                    else        -> false
                }
                if (matches) {
                    val neededCrafts = ceilDiv(remaining, recipe.outputQty)
                    indicators.add(QuestIndicator(QuestCategory.DAILY, max >= neededCrafts))
                }
            }

            // 4. Weekly Quests
            for (weekly in activeWeeklies) {
                val remaining = weekly.template.amount - weekly.progress
                if (remaining <= 0) continue
                val matches = when (weekly.template.type) {
                    "craft"     -> weekly.template.target == key
                    "craft_any" -> weekly.template.skill  == skill
                    else        -> false
                }
                if (matches) {
                    val neededCrafts = ceilDiv(remaining, recipe.outputQty)
                    indicators.add(QuestIndicator(QuestCategory.WEEKLY, max >= neededCrafts))
                }
            }

            // 5. Guild Daily Quests
            for (id in activeGuildDailyIds) {
                val template = guildPool[id] ?: continue
                val progress = flags.guildDailyProgress[id] ?: 0
                val remaining = template.amount - progress
                if (remaining <= 0) continue
                val matches = when (template.type) {
                    "craft"     -> template.target == key
                    "craft_any" -> template.guild  == skill
                    else        -> false
                }
                if (matches) {
                    val neededCrafts = ceilDiv(remaining, recipe.outputQty)
                    indicators.add(QuestIndicator(QuestCategory.GUILD_DAILY, max >= neededCrafts))
                }
            }

            if (indicators.isNotEmpty()) {
                result[key] = indicators
            }
        }
        return result
    }

    private fun craftAnyTargetMatches(target: String, recipe: CraftableRecipe): Boolean = when (target) {
        "any_fish" -> recipe.materials.keys.any { it in gameData.fish.keys }
        else       -> true
    }

    private fun ceilDiv(a: Int, b: Int) = if (b <= 0) a else (a + b - 1) / b

    private fun applyMaterialPreservation(materials: Map<String, Int>, qty: Int, saveChance: Float): Map<String, Int> {
        val totalMats = materials.mapValues { it.value * qty }
        if (saveChance <= 0f || totalMats.size <= 1) return totalMats
        val entries = totalMats.entries.toList()
        val result = mutableMapOf<String, Int>()
        result[entries[0].key] = entries[0].value
        for (i in 1 until entries.size) {
            val (item, totalQty) = entries[i]
            var toConsume = 0
            for (u in 0 until totalQty) {
                if (kotlin.random.Random.nextFloat() >= saveChance) toConsume++
            }
            if (toConsume > 0) result[item] = toConsume
        }
        return result
    }

    private fun applyQtyPreservation(totalQty: Int, saveChance: Float): Int {
        if (saveChance <= 0f) return totalQty
        var toConsume = 0
        for (u in 0 until totalQty) {
            if (kotlin.random.Random.nextFloat() >= saveChance) toConsume++
        }
        return toConsume
    }
}
