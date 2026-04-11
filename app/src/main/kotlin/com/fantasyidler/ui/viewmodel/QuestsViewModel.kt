package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.json.QuestData
import com.fantasyidler.data.model.Skills
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
) {
    val isClaimable: Boolean get() = progress >= quest.amount && !completed
    val progressFraction: Float get() = (progress.toFloat() / quest.amount.toFloat()).coerceIn(0f, 1f)
}

data class QuestsUiState(
    val isLoading: Boolean = true,
    /** Groups in display order: "Gathering", "Crafting", "Combat", "Special". */
    val questsByGroup: Map<String, List<QuestWithProgress>> = emptyMap(),
    val claimableCount: Int = 0,
    val completedCount: Int = 0,
    val snackbarMessage: String? = null,
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

@HiltViewModel
class QuestsViewModel @Inject constructor(
    private val questRepo: QuestRepository,
    private val gameData: GameDataRepository,
    private val playerRepo: PlayerRepository,
    private val json: Json,
) : ViewModel() {

    private val _extra = MutableStateFlow(QuestsUiState())

    val uiState: StateFlow<QuestsUiState> = combine(
        questRepo.observeProgress(),
        _extra,
    ) { progressList, extra ->
        val progressMap = progressList.associateBy { it.questId }

        val questsWithProgress = gameData.quests.values.map { quest ->
            val prog = progressMap[quest.id]
            QuestWithProgress(
                quest     = quest,
                progress  = prog?.progress ?: 0,
                completed = prog?.completed ?: false,
            )
        }

        val questsByGroup = buildGroupedMap(questsWithProgress)
        val claimable = questsWithProgress.count { it.isClaimable }
        val completed = questsWithProgress.count { it.completed }

        extra.copy(
            isLoading      = false,
            questsByGroup  = questsByGroup,
            claimableCount = claimable,
            completedCount = completed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QuestsUiState())

    // ---------------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------------

    fun claimReward(questId: String) {
        viewModelScope.launch {
            val rewards = questRepo.claimReward(questId) ?: return@launch
            val quest = gameData.quests[questId] ?: return@launch

            // Apply XP + items + coins via applyMultiSkillResults so coins are handled properly
            playerRepo.applyMultiSkillResults(
                xpPerSkill  = mapOf(quest.skill to rewards.xp.toLong()),
                itemsGained = rewards.items,
                coinsGained = rewards.coins.toLong(),
            )

            // Build snackbar message
            val parts = buildList {
                if (rewards.xp > 0) add("+${rewards.xp.toLong().formatXp()} XP")
                if (rewards.coins > 0) add("+${rewards.coins.toLong().formatCoins()} coins")
                rewards.items.forEach { (_, qty) -> add("×$qty item${if (qty != 1) "s" else ""}") }
            }
            val claimedText = if (parts.isNotEmpty()) " Claimed: ${parts.joinToString(", ")}" else ""
            _extra.update { it.copy(snackbarMessage = "Quest complete: ${quest.name}!$claimedText") }
        }
    }

    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }

    // ---------------------------------------------------------------------------
    // Group helpers
    // ---------------------------------------------------------------------------

    private val gatheringSkills = setOf(
        Skills.MINING, Skills.WOODCUTTING, Skills.FISHING,
        Skills.AGILITY, Skills.FIREMAKING, Skills.RUNECRAFTING,
    )
    private val craftingSkills = setOf(
        Skills.SMITHING, Skills.COOKING, Skills.FLETCHING, Skills.CRAFTING, Skills.PRAYER,
    )
    private val combatTypes = setOf("kill", "kill_enemy", "dungeon")
    private val specialTypes = setOf(
        "dungeon_melee_only", "dungeon_ranged_only", "dungeon_magic_only",
        "dungeon_no_food", "collect",
    )

    private fun groupFor(quest: QuestData): String = when {
        quest.skill in gatheringSkills                          -> "Gathering"
        quest.skill in craftingSkills                          -> "Crafting"
        quest.skill == "combat" && quest.type in combatTypes   -> "Combat"
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
