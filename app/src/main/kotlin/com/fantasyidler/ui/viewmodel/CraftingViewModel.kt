package com.fantasyidler.ui.viewmodel

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
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.repository.WeeklyQuestRepository
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
import android.content.Context
import com.fantasyidler.R
import dagger.hilt.android.qualifiers.ApplicationContext

// ---------------------------------------------------------------------------
// Quest fill suggestion (shown in CraftSheet when quests match the recipe)
// ---------------------------------------------------------------------------

data class QuestFillSuggestion(val label: String, val qty: Int)

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
    val craftQuantity:  Int = 1,
    /** Ash catalyst key selected for a herblore brew, or null for no catalyst. */
    val herbloreAshKey: String? = null,
    val snackbarMessage: String? = null,
    val isLoading: Boolean = true,
    val questFills: List<QuestFillSuggestion> = emptyList(),
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
            val flags: PlayerFlags = json.decodeFromString(player.flags)
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
                effectiveInventory = computeEffectiveInventory(inventory),
                isLoading          = false,
                questFills         = computeQuestFills(extra.selectedRecipe, questProgress, flags),
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
            val category = when {
                isPlank              -> "Plank"
                r.type == "component"  -> "Component"
                r.type == "ammunition" -> "Ammunition"
                r.type == "weapon"     -> "Weapon"
                else                   -> ""
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
                tier                = if (isPlank) "" else tierFromKey(r.itemName),
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

    fun openRecipe(recipe: CraftableRecipe) {
        val max = uiState.value.maxCraftable(recipe).coerceAtLeast(1)
        _extra.update { it.copy(selectedRecipe = recipe, craftQuantity = max) }
    }

    fun dismissRecipe() = _extra.update { it.copy(selectedRecipe = null, herbloreAshKey = null) }

    fun setHerbloreAsh(key: String?) = _extra.update { it.copy(herbloreAshKey = key) }

    /** [max] should come from the combined uiState (which has inventory), not _extra. */
    fun setQuantity(qty: Int, max: Int) =
        _extra.update { it.copy(craftQuantity = qty.coerceIn(1, max.coerceAtLeast(1))) }

    fun craft() {
        val state  = uiState.value          // combined state — has inventory
        val recipe = state.selectedRecipe ?: return
        val max    = state.maxCraftable(recipe).coerceAtLeast(1)
        val qty    = state.craftQuantity.coerceIn(1, max)
        val ashKey = if (recipe.skillName == Skills.HERBLORE) state.herbloreAshKey else null

        viewModelScope.launch {
            // Enqueue if a session is already running
            if (sessionRepo.getActiveSession() != null) {
                val craftFlags = playerRepo.getFlags()
                val agility   = state.skillLevels[Skills.AGILITY] ?: 1
                val perItemMs = SkillSimulator.sessionDurationMs(agility, craftFlags.skillPrestige[Skills.AGILITY] ?: 0) / 60
                val totalOutput = qty * recipe.outputQty
                val xpQueueMult = (if (craftFlags.xpBoostExpiresAt > System.currentTimeMillis()) 2.0 else 1.0) * ChurchRepository.xpMultiplier(craftFlags)
                val action = QueuedAction(
                    skillName           = recipe.skillName,
                    activityKey         = recipe.key,
                    skillDisplayName    = recipe.skillName.replaceFirstChar { it.uppercase() },
                    qty                 = qty,
                    outputQty           = if (totalOutput != qty) totalOutput else 0,
                    estimatedXpGain     = (qty * recipe.xpPerItem * xpQueueMult).toLong(),
                    estimatedDurationMs = qty.toLong() * perItemMs,
                    catalystKey         = ashKey,
                )
                val enqueued = playerRepo.enqueueAction(action)
                if (enqueued) playerRepo.consumeItems(recipe.materials.mapValues { it.value * qty })
                _extra.update {
                    it.copy(
                        snackbarMessage = if (enqueued) context.getString(R.string.snackbar_added_to_queue, recipe.displayName) else context.getString(R.string.snackbar_queue_full),
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
                _extra.update { it.copy(snackbarMessage = context.getString(R.string.skill_not_enough_materials)) }
                return@launch
            }
            val xpMap: Map<String, Long> = json.decodeFromString(player.skillXp)
            val startXp     = xpMap[recipe.skillName] ?: 0L
            val levelBefore = XpTable.levelForXp(startXp)
            val totalXpGain = (qty * recipe.xpPerItem).toInt()
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
            val flags = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
            val agilityLevel = levels[Skills.AGILITY] ?: 1
            // 1 item per minute, reduced by agility (same formula as gathering skills)
            val perItemMs = SkillSimulator.sessionDurationMs(agilityLevel, flags.skillPrestige[Skills.AGILITY] ?: 0) / 60

            val framesJson = json.encodeToString(
                json.serializersModule.serializer<List<SessionFrame>>(),
                frames,
            )
            playerRepo.consumeItems(recipe.materials.mapValues { it.value * qty })
            if (ashKey != null) playerRepo.consumeItems(mapOf(ashKey to qty))
            sessionRepo.startSession(
                skillName        = recipe.skillName,
                activityKey      = recipe.key,
                frames           = framesJson,
                durationMs       = qty * perItemMs,
                skillDisplayName = recipe.skillName,
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
                    fills += QuestFillSuggestion(quest.name, ceilDiv(remaining, recipe.outputQty))
            }
        }

        // Guild progression quests (guild_quests.json, tracked in same quest_progress table)
        val completedIds = progressById.entries.filter { it.value.completed }.map { it.key }.toSet()
        for ((id, quest) in gameData.guildQuests) {
            if (quest.type != "craft" && quest.type != "craft_any") continue
            val prog = progressById[id]
            if (prog?.completed == true) continue
            // Skip if the player's current guild level is below the quest's requirement
            val rep = flags.guildReputation[quest.guild] ?: 0L
            if (guildRepo.guildLevel(quest.guild, rep, completedIds) < quest.guildLevelRequired) continue
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
                    fills += QuestFillSuggestion(quest.name, ceilDiv(remaining, recipe.outputQty))
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
                fills += QuestFillSuggestion(context.getString(R.string.quest_fill_daily), ceilDiv(remaining, recipe.outputQty))
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
                fills += QuestFillSuggestion(context.getString(R.string.quest_fill_weekly), ceilDiv(remaining, recipe.outputQty))
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
                fills += QuestFillSuggestion(context.getString(R.string.quest_fill_guild), ceilDiv(remaining, recipe.outputQty))
        }

        return fills.sortedBy { it.qty }
    }

    private fun craftAnyTargetMatches(target: String, recipe: CraftableRecipe): Boolean = when (target) {
        "any_fish" -> recipe.materials.keys.any { it in gameData.fish.keys }
        else       -> true
    }

    private fun ceilDiv(a: Int, b: Int) = if (b <= 0) a else (a + b - 1) / b
}
