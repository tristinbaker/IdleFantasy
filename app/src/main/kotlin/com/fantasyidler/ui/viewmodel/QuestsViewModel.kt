package com.fantasyidler.ui.viewmodel

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
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject

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
    val snackbarMessage: String? = null,
    val hideCompleted: Boolean = false,
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class QuestsViewModel @Inject constructor(
    private val questRepo: QuestRepository,
    private val gameData: GameDataRepository,
    private val playerRepo: PlayerRepository,
    private val dailyQuestRepo: DailyQuestRepository,
    private val json: Json,
) : ViewModel() {

    private val _extra = MutableStateFlow(QuestsUiState())

    init {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            _extra.update { it.copy(hideCompleted = flags.hideCompletedQuests) }
            // Trigger a DB refresh if daily quests have rolled over, and seed nextDailyReset.
            playerRepo.getRefreshedDailyFlags()
            _extra.update { it.copy(nextDailyReset = dailyQuestRepo.nextResetMs()) }
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

        extra.copy(
            isLoading      = false,
            questsByGroup  = questsByGroup,
            claimableCount = claimable,
            completedCount = completed,
            dailyQuests    = dailyQuests,
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

        playerRepo.applyMultiSkillResults(
            xpPerSkill  = xpPerSkill,
            itemsGained = rewards.items,
            coinsGained = rewards.coins.toLong(),
        )

        val parts = buildList {
            if (rewards.xp > 0) {
                if (quest.skill == "combat") {
                    add("+${rewards.xp.toLong().formatXp()} combat XP (split across 7 skills)")
                } else {
                    add("+${rewards.xp.toLong().formatXp()} XP")
                }
            }
            if (rewards.coins > 0) add("+${rewards.coins.toLong().formatCoins()} coins")
            rewards.items.forEach { (_, qty) -> add("×$qty item${if (qty != 1) "s" else ""}") }
        }
        val claimedText = if (parts.isNotEmpty()) " Claimed: ${parts.joinToString(", ")}" else ""
        _extra.update { it.copy(snackbarMessage = "Quest complete: ${quest.name}!$claimedText") }
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
            val flags = playerRepo.getFlags()
            val ownedItems = playerRepo.getInventory().keys +
                playerRepo.getEquipped().values.filterNotNull()
            val (newFlags, reward) = dailyQuestRepo.claimQuest(flags, templateId, ownedItems.toSet())
            playerRepo.updateFlags(newFlags)

            val message = when (reward) {
                is DailyReward.CoinsReward -> {
                    playerRepo.addCoins(reward.amount.toLong())
                    "Daily quest complete! +${reward.amount.toLong().formatCoins()} coins"
                }
                is DailyReward.DwarvenItemReward -> {
                    playerRepo.addItem(reward.itemKey, 1)
                    "Daily quest complete! You found dwarven gear!"
                }
            }
            _extra.update { it.copy(snackbarMessage = message) }
        }
    }

    // ---------------------------------------------------------------------------
    // Group helpers
    // ---------------------------------------------------------------------------

    private val gatheringSkills = setOf(
        Skills.MINING, Skills.WOODCUTTING, Skills.FISHING,
        Skills.AGILITY, Skills.FIREMAKING, Skills.RUNECRAFTING,
    )
    private val craftingSkills = setOf(
        Skills.SMITHING, Skills.COOKING, Skills.FLETCHING, Skills.CRAFTING, Skills.HERBLORE, Skills.PRAYER,
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
