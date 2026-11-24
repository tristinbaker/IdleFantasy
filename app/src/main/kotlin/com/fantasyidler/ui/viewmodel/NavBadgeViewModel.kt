package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.DailyQuestRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.WeeklyQuestRepository
import com.fantasyidler.simulator.PrestigeBoosts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class NavBadgeViewModel @Inject constructor(
    questRepo: QuestRepository,
    gameData: GameDataRepository,
    playerRepo: PlayerRepository,
    dailyQuestRepo: DailyQuestRepository,
    weeklyQuestRepo: WeeklyQuestRepository,
    private val json: Json,
) : ViewModel() {

    val questsClaimableCount: StateFlow<Int> = combine(
        questRepo.observeProgress(),
        playerRepo.playerFlow,
    ) { progressList, player ->
        val progressMap = progressList.associateBy { it.questId }
        val regularCount = gameData.quests.count { (id, quest) ->
            val prog = progressMap[id]
            if (prog?.completed == true) return@count false
            val prereq = quest.requiresPrevious
            val prereqDone = prereq == null || progressMap[prereq]?.completed == true
            prereqDone && (prog?.progress ?: 0) >= quest.amount
        }
        if (player == null) return@combine regularCount
        val flags: PlayerFlags = json.decodeFromString(player.flags)
        if (flags.onElderIsle) return@combine 0
        val dailyCount = dailyQuestRepo.getActiveDailyQuests(flags)
            .count { it.progress >= it.template.amount && !it.claimed }
        val weeklyCount = weeklyQuestRepo.getActiveWeeklyQuests(flags)
            .count { it.progress >= it.template.amount && !it.claimed }
        val storyUnread = if (flags.storyTabVisible) flags.storyDelivered.count { it !in flags.storyRead } else 0
        regularCount + dailyCount + weeklyCount + storyUnread
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val skillsReadyToPrestige: StateFlow<Set<String>> = playerRepo.playerFlow
        .map { player ->
            if (player == null) return@map emptySet()
            val flags: PlayerFlags = json.decodeFromString(player.flags)
            // Prestige is a mainland-only system; suppress the nav badges while on the isle
            // so they don't nag the player about work the isle bar doesn't even expose.
            if (flags.onElderIsle) return@map emptySet()
            val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
            Skills.ALL.filterTo(mutableSetOf()) { skill ->
                (levels[skill] ?: 1) >= 99 &&
                    PrestigeBoosts.prestigeHasReward(gameData.prestigeTrees, flags, skill)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val hasCombatPrestige: StateFlow<Boolean> = skillsReadyToPrestige
        .map { ready -> COMBAT_PRESTIGE_SKILLS.any { it in ready } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val hasSkillPrestige: StateFlow<Boolean> = skillsReadyToPrestige
        .map { ready -> ready.any { it !in COMBAT_PRESTIGE_SKILLS } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private companion object {
        val COMBAT_PRESTIGE_SKILLS = setOf(
            Skills.ATTACK, Skills.STRENGTH, Skills.DEFENSE,
            Skills.RANGED, Skills.MAGIC, Skills.HITPOINTS,
        )
    }
}
