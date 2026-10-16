package com.fantasyidler.ui.viewmodel

import com.fantasyidler.util.withAppLocale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.json.QuestData
import com.fantasyidler.data.json.QuestRewards
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.DailyQuestRepository
import com.fantasyidler.repository.DailyQuestWithProgress
import com.fantasyidler.repository.DailyReward
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.WeeklyBonusReward
import com.fantasyidler.repository.WeeklyQuestRepository
import com.fantasyidler.repository.WeeklyQuestWithProgress
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.xpMultiplierBreakdown
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.fantasyidler.util.GameStrings
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import android.content.Context
import com.fantasyidler.R
import dagger.hilt.android.qualifiers.ApplicationContext

// ---------------------------------------------------------------------------
// Data classes
// ---------------------------------------------------------------------------

data class QuestWithProgress(
    val quest: QuestData,
    val progress: Int,
    val completed: Boolean,
    val prereqCompleted: Boolean,
) {
    val isClaimable: Boolean get() = progress >= quest.amount && !completed && prereqCompleted
    val progressFraction: Float get() = (progress.toFloat() / quest.amount.toFloat()).coerceIn(0f, 1f)
}

data class QuestsUiState(
    val isLoading: Boolean = true,
    /** Groups in display order: "Gathering", "Crafting", "Combat", "Special". */
    val questsByGroup: Map<String, List<QuestWithProgress>> = emptyMap(),
    val claimableCount: Int = 0,
    val completedCount: Int = 0,
    val dailyQuests: List<DailyQuestWithProgress> = emptyList(),
    val nextDailyReset: Long = 0L,
    val weeklyQuests: List<WeeklyQuestWithProgress> = emptyList(),
    val nextWeeklyReset: Long = 0L,
    val snackbarMessage: String? = null,
    val hideCompleted: Boolean = false,
    val weeklyBonusClaimed: Boolean = false,
    /** Current pity-adjusted Divine gear drop chance (0-1), or null if every piece is owned. */
    val divineDropChance: Double? = null,
    /** Current 1-in-N odds of a gear drop per claimed daily, or null when both sets are complete. */
    val dwarvenDropDenominator: Int? = null,
    /** True once the Dwarven set is complete and the daily roll drops Divine pieces. */
    val dailyDropIsDivine: Boolean = false,
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class QuestsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val questRepo: QuestRepository,
    private val gameData: GameDataRepository,
    private val playerRepo: PlayerRepository,
    private val dailyQuestRepo: DailyQuestRepository,
    private val weeklyQuestRepo: WeeklyQuestRepository,
    private val json: Json,
) : ViewModel() {

    private val _extra = MutableStateFlow(QuestsUiState())

    init {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            _extra.update { it.copy(hideCompleted = flags.hideCompletedQuests) }
            // Trigger a DB refresh if daily quests have rolled over, and seed nextDailyReset.
            playerRepo.getRefreshedDailyFlags()
            _extra.update { 
                it.copy(
                    nextDailyReset = dailyQuestRepo.nextResetMs(resetHour = flags.dailyResetHour),
                    nextWeeklyReset = weeklyQuestRepo.nextResetMs(resetHour = flags.dailyResetHour)
                )
            }
        }
    }

    val uiState: StateFlow<QuestsUiState> = combine(
        questRepo.observeProgress(),
        playerRepo.playerFlow,
        _extra,
    ) { progressList, player, extra ->
        val progressMap = progressList.associateBy { it.questId }

        val flags: PlayerFlags = if (player != null)
            json.decodeFromString(player.flags) else PlayerFlags()

        val questsWithProgress = gameData.quests.values.map { quest ->
            val prog = progressMap[quest.id]
            val prereq = quest.requiresPrevious
            val prereqCompleted = prereq == null || progressMap[prereq]?.completed == true
            QuestWithProgress(
                quest           = quest,
                progress        = prog?.progress ?: 0,
                completed       = prog?.completed ?: false,
                prereqCompleted = prereqCompleted,
            )
        }.filter { qwp ->
            val requiredDungeon = qwp.quest.requiresDungeonUnlock
            requiredDungeon == null || flags.unlockedDungeons.contains(requiredDungeon)
        }

        val visibleQuests = questsWithProgress.filter { it.prereqCompleted || it.completed }
            .let { if (extra.hideCompleted) it.filter { qwp -> !qwp.completed } else it }
        val questsByGroup = buildGroupedMap(visibleQuests)
        val claimable = visibleQuests.count { it.isClaimable }
        val completed = visibleQuests.count { it.completed }

        val dailyQuests = if (player != null) {
            dailyQuestRepo.getActiveDailyQuests(flags)
        } else {
            extra.dailyQuests
        }

        val weeklyQuests = if (player != null) {
            weeklyQuestRepo.getActiveWeeklyQuests(flags)
        } else {
            extra.weeklyQuests
        }

        var divineDropChance = extra.divineDropChance
        var dwarvenDropDenominator = extra.dwarvenDropDenominator
        var dailyDropIsDivine = extra.dailyDropIsDivine
        if (player != null) {
            val inventory: Map<String, Int> = json.decodeFromString(player.inventory)
            val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
            val ownedItems = (inventory.keys + equipped.values.filterNotNull()).toSet()
            divineDropChance = weeklyQuestRepo.currentDivineDropChanceForDisplay(flags, ownedItems)
            dwarvenDropDenominator = dailyQuestRepo.dailyDropDenominatorForDisplay(flags, ownedItems)
            dailyDropIsDivine = dailyQuestRepo.dwarvenDropPool.all { it in ownedItems }
        }

        extra.copy(
            isLoading          = false,
            questsByGroup      = questsByGroup,
            claimableCount     = claimable,
            completedCount     = completed,
            dailyQuests        = dailyQuests,
            weeklyQuests       = weeklyQuests,
            divineDropChance   = divineDropChance,
            dwarvenDropDenominator = dwarvenDropDenominator,
            dailyDropIsDivine  = dailyDropIsDivine,
            weeklyBonusClaimed = flags.weeklyBonusClaimed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuestsUiState())

    // ---------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------

    fun claimReward(questId: String) {
        viewModelScope.launch {
            when (val result = questRepo.claimReward(questId)) {
                is QuestRepository.ClaimResult.Success -> applyClaimSuccess(questId, result.rewards)
                is QuestRepository.ClaimResult.BlockedByPrerequisite -> {
                    val blocker = gameData.quests[result.prerequisiteId]
                    val message = if (blocker != null) {
                        "Claim tier ${blocker.tier}: ${blocker.name} first"
                    } else {
                        "Claim the previous tier first"
                    }
                    _extra.update { it.copy(snackbarMessage = message) }
                }
                QuestRepository.ClaimResult.NotReady -> Unit
            }
        }
    }

    fun debugResetQuest(questId: String) {
        viewModelScope.launch {
            questRepo.resetQuestAndDependents(questId)
        }
    }

    private suspend fun applyClaimSuccess(questId: String, rewards: QuestRewards) {
        val quest = gameData.quests[questId] ?: return

        // Combat quests carry skill="combat", which isn't a real Skills.* key. Fan the XP
        // out across all 7 combat-related skills (attack/strength/defense/ranged/magic/hp/prayer)
        // so it actually lands somewhere visible to the player.
        val xpPerSkill: Map<String, Long> = if (quest.skill == "combat") {
            val total = rewards.xp.toLong()
            val per = total / Skills.COMBAT.size
            val remainder = total - per * Skills.COMBAT.size
            Skills.COMBAT.withIndex().associate { (i, skill) ->
                skill to (per + if (i == 0) remainder else 0L)
            }
        } else {
            mapOf(quest.skill to rewards.xp.toLong())
        }

        // Preview the ACTUAL XP that will be credited (boost/blessing/prestige) before crediting
        // it, so the confirmation snackbar discloses the real total instead of the flat reward.
        val breakdowns = xpPerSkill.mapValues { (skill, xp) -> playerRepo.previewFlatXpGrant(skill, xp) }

        playerRepo.applyMultiSkillResults(
            xpPerSkill  = xpPerSkill,
            itemsGained = rewards.items,
            coinsGained = rewards.coins.toLong(),
        )

        val parts = buildList {
            if (rewards.xp > 0) {
                if (quest.skill == "combat") {
                    val totalFinalXp = breakdowns.values.sumOf { it.finalXp }
                    add(context.withAppLocale().getString(R.string.reward_part_combat_xp, totalFinalXp.formatXp()))
                } else {
                    val b = breakdowns.getValue(quest.skill)
                    val suffix = xpMultiplierBreakdown(b.baseXp, b.boostFactor, b.blessingMult, b.prestigeXpPct)?.let { " $it" } ?: ""
                    add("+${b.finalXp.formatXp()} XP$suffix")
                }
            }
            if (rewards.coins > 0) add(context.withAppLocale().getString(R.string.reward_part_coins, rewards.coins.toLong().formatCoins()))
            rewards.items.forEach { (_, qty) -> add(context.resources.getQuantityString(R.plurals.plural_reward_items, qty, qty)) }
        }
        val claimedText = if (parts.isNotEmpty()) " " + context.withAppLocale().getString(R.string.quest_claimed_suffix, parts.joinToString(", ")) else ""
        val questName = GameStrings.questName(context, quest.id, quest.name)
        _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.quest_complete_notification, questName) + claimedText) }
    }

    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }

    fun toggleHideCompleted() {
        val newValue = !_extra.value.hideCompleted
        _extra.update { it.copy(hideCompleted = newValue) }
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            playerRepo.updateFlags(flags.copy(hideCompletedQuests = newValue))
        }
    }

    fun claimDailyQuest(templateId: String) {
        viewModelScope.launch {
            val reward = playerRepo.claimDailyQuest(templateId) ?: return@launch
            val message = when (reward) {
                is DailyReward.CoinsReward -> {
                    playerRepo.addCoins(reward.amount.toLong())
                    context.withAppLocale().getString(R.string.quest_daily_complete_coins, reward.amount.toLong().formatCoins())
                }
                is DailyReward.DwarvenItemReward -> {
                    context.withAppLocale().getString(R.string.quest_daily_complete_dwarven)
                }
                is DailyReward.DivineItemReward -> {
                    context.withAppLocale().getString(R.string.quest_daily_complete_divine)
                }
            }
            _extra.update { it.copy(snackbarMessage = message) }
        }
    }


    fun claimWeeklyQuest(templateId: String) {
        viewModelScope.launch {
            val rewardCoins = playerRepo.claimWeeklyQuest(templateId) ?: return@launch
            playerRepo.addCoins(rewardCoins)
            _extra.update { it.copy(snackbarMessage = context.withAppLocale().getString(R.string.quest_weekly_complete, rewardCoins.formatCoins())) }
        }
    }

    fun claimWeeklyBonus() {
        viewModelScope.launch {
            val reward = playerRepo.claimWeeklyBonus() ?: return@launch
            val message = when (reward) {
                is WeeklyBonusReward.CoinsReward -> {
                    playerRepo.addCoins(reward.amount)
                    context.withAppLocale().getString(R.string.quest_weekly_bonus_complete_coins, reward.amount.formatCoins())
                }
                is WeeklyBonusReward.DivineItemReward -> {
                    context.withAppLocale().getString(R.string.quest_weekly_bonus_complete_divine)
                }
            }
            _extra.update { it.copy(snackbarMessage = message) }
        }
    }


    // ---------------------------------------------------------------------------
    // Group helpers
    // ---------------------------------------------------------------------------

    private val gatheringSkills = setOf(
        Skills.MINING, Skills.WOODCUTTING, Skills.FISHING, Skills.AGILITY, Skills.THIEVING,
    )
    private val craftingSkills = setOf(
        Skills.SMITHING, Skills.COOKING, Skills.FLETCHING, Skills.CRAFTING,
        Skills.HERBLORE, Skills.PRAYER, Skills.FIREMAKING, Skills.RUNECRAFTING, Skills.CONSTRUCTION,
    )
    private val combatTypes = setOf("kill", "kill_enemy", "dungeon", "boss")
    private val specialTypes = setOf(
        "dungeon_melee_only", "dungeon_ranged_only", "dungeon_magic_only",
        "dungeon_no_food", "collect",
    )

    private fun groupFor(quest: QuestData): String = when {
        quest.skill in gatheringSkills                          -> "Gathering"
        quest.skill in craftingSkills                          -> "Crafting"
        quest.skill == "combat" && quest.type in combatTypes   -> "Combat"
        quest.skill == "slayer"                                -> "Combat"
        quest.type in specialTypes                             -> "Special"
        else                                                   -> "Special"
    }

    private fun buildGroupedMap(
        quests: List<QuestWithProgress>,
    ): Map<String, List<QuestWithProgress>> {
        val grouped = quests.groupBy { groupFor(it.quest) }
        val ordered = linkedMapOf<String, List<QuestWithProgress>>()
        for (group in listOf("Gathering", "Crafting", "Combat", "Special")) {
            val list = grouped[group] ?: emptyList()
            ordered[group] = list.sortedWith(
                compareBy({ it.quest.skill }, { it.quest.tier })
            )
        }
        return ordered
    }
}
