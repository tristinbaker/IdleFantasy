package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.model.OwnedPet
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QuestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val isUnlocked: Boolean,
)

data class AchievementsUiState(
    val isLoading: Boolean = true,
    val byGroup: Map<String, List<Achievement>> = emptyMap(),
    val unlockedCount: Int = 0,
    val totalCount: Int = 0,
)

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val questRepo: QuestRepository,
    private val json: Json,
) : ViewModel() {

    val uiState: StateFlow<AchievementsUiState> = combine(
        playerRepo.playerFlow,
        questRepo.observeProgress(),
    ) { player, questProgress ->
        if (player == null) return@combine AchievementsUiState()

        val levels: Map<String, Int> = json.decodeFromString(player.skillLevels)
        val pets: List<OwnedPet> = try {
            json.decodeFromString(player.pets)
        } catch (_: Exception) { emptyList() }
        val completedQuests = questProgress.count { it.completed }
        val totalLevel  = levels.values.sum()
        val combatLevel = combatLevelFrom(levels)

        val groups = linkedMapOf<String, List<Achievement>>()

        groups["Levelling"] = listOf(
            ach("total_50",   "Adventurer",     "Reach total level 50",         "⚔️",  totalLevel >= 50),
            ach("total_100",  "Journeyman",     "Reach total level 100",        "🗺️",  totalLevel >= 100),
            ach("total_250",  "Seasoned",       "Reach total level 250",        "📈",  totalLevel >= 250),
            ach("total_500",  "Veteran",        "Reach total level 500",        "🏅",  totalLevel >= 500),
            ach("total_750",  "Master",         "Reach total level 750",        "🥇",  totalLevel >= 750),
            ach("total_1000", "Legend",         "Reach total level 1000",       "🌟",  totalLevel >= 1000),
            ach("total_1500", "Champion",       "Reach total level 1500",       "👑",  totalLevel >= 1500),
            ach("skill_99",   "First Mastery",  "Max any skill to level 99",    "💯",  levels.values.any { it >= 99 }),
            ach("all_99",     "Completionist",  "Max all skills to level 99",   "🏆",  levels.values.all { it >= 99 }),
        )

        groups["Combat"] = listOf(
            ach("combat_10", "Fighter",    "Reach combat level 10",  "🗡️", combatLevel >= 10),
            ach("combat_30", "Warrior",    "Reach combat level 30",  "⚔️", combatLevel >= 30),
            ach("combat_50", "Champion",   "Reach combat level 50",  "🛡️", combatLevel >= 50),
            ach("combat_75", "Elite",      "Reach combat level 75",  "💀", combatLevel >= 75),
            ach("combat_99", "Max Combat", "Reach combat level 99",  "👹", combatLevel >= 99),
        )

        groups["Quests"] = listOf(
            ach("quest_1",   "Quester",        "Complete 1 quest",    "📜",  completedQuests >= 1),
            ach("quest_5",   "Dedicated",      "Complete 5 quests",   "📜",  completedQuests >= 5),
            ach("quest_25",  "Quest Hound",    "Complete 25 quests",  "📚",  completedQuests >= 25),
            ach("quest_50",  "Quest Master",   "Complete 50 quests",  "🏅",  completedQuests >= 50),
            ach("quest_all", "Quest Champion", "Complete all quests", "🏆",  completedQuests >= 103),
        )

        groups["Collection"] = listOf(
            ach("pet_first", "Animal Friend", "Collect your first pet", "🐾", pets.isNotEmpty()),
            ach("pet_all",   "Menagerie",     "Collect all 7 pets",     "🦁", pets.size >= 7),
        )

        val all = groups.values.flatten()
        AchievementsUiState(
            isLoading     = false,
            byGroup       = groups,
            unlockedCount = all.count { it.isUnlocked },
            totalCount    = all.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AchievementsUiState())

    private fun ach(id: String, name: String, desc: String, emoji: String, unlocked: Boolean) =
        Achievement(id = id, name = name, description = desc, emoji = emoji, isUnlocked = unlocked)
}
