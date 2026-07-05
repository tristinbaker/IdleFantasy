package com.fantasyidler.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.R
import com.fantasyidler.data.model.OwnedPet
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QuestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val playerRepo: PlayerRepository,
    private val questRepo: QuestRepository,
    private val gameData: GameDataRepository,
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
        val flags: PlayerFlags = try { json.decodeFromString(player.flags) } catch (_: Exception) { PlayerFlags() }
        val completedQuests = questProgress.count { it.completed }
        val totalLevel  = levels.values.sum()
        val combatLevel = combatLevelFrom(levels)
        val prestigeMap = flags.skillPrestige

        val groups = linkedMapOf<String, List<Achievement>>()

        groups["Levelling"] = listOf(
            ach("total_50",   R.string.achievement_total_50_name,   R.string.achievement_total_50_desc,   "⚔️",  totalLevel >= 50),
            ach("total_100",  R.string.achievement_total_100_name,  R.string.achievement_total_100_desc,  "🗺️",  totalLevel >= 100),
            ach("total_250",  R.string.achievement_total_250_name,  R.string.achievement_total_250_desc,  "📈",  totalLevel >= 250),
            ach("total_500",  R.string.achievement_total_500_name,  R.string.achievement_total_500_desc,  "🏅",  totalLevel >= 500),
            ach("total_750",  R.string.achievement_total_750_name,  R.string.achievement_total_750_desc,  "🥇",  totalLevel >= 750),
            ach("total_1000", R.string.achievement_total_1000_name, R.string.achievement_total_1000_desc, "🌟",  totalLevel >= 1000),
            ach("total_1500", R.string.achievement_total_1500_name, R.string.achievement_total_1500_desc, "👑",  totalLevel >= 1500),
            ach("skill_99",   R.string.achievement_skill_99_name,   R.string.achievement_skill_99_desc,   "💯",  levels.values.any { it >= 99 } || prestigeMap.values.any { it >= 1 }),
            ach("all_99",     R.string.achievement_all_99_name,     R.string.achievement_all_99_desc,     "🏆",  Skills.ALL.all { (levels[it] ?: 1) >= 99 }),
        )

        groups["Combat"] = listOf(
            ach("combat_10", R.string.achievement_combat_10_name, R.string.achievement_combat_10_desc, "🗡️", combatLevel >= 10),
            ach("combat_30", R.string.achievement_combat_30_name, R.string.achievement_combat_30_desc, "⚔️", combatLevel >= 30),
            ach("combat_50", R.string.achievement_combat_50_name, R.string.achievement_combat_50_desc, "🛡️", combatLevel >= 50),
            ach("combat_75",  R.string.achievement_combat_75_name,  R.string.achievement_combat_75_desc,  "💀", combatLevel >= 75),
            ach("combat_99",  R.string.achievement_combat_99_name,  R.string.achievement_combat_99_desc,  "👹", combatLevel >= 99),
            ach("combat_113", R.string.achievement_combat_113_name, R.string.achievement_combat_113_desc, "🏆", combatLevel >= 113),
        )

        groups["Quests"] = listOf(
            ach("quest_1",   R.string.achievement_quest_1_name,   R.string.achievement_quest_1_desc,   "📜",  completedQuests >= 1),
            ach("quest_5",   R.string.achievement_quest_5_name,   R.string.achievement_quest_5_desc,   "📜",  completedQuests >= 5),
            ach("quest_25",  R.string.achievement_quest_25_name,  R.string.achievement_quest_25_desc,  "📚",  completedQuests >= 25),
            ach("quest_50",  R.string.achievement_quest_50_name,  R.string.achievement_quest_50_desc,  "🏅",  completedQuests >= 50),
            ach("quest_all", R.string.achievement_quest_all_name, R.string.achievement_quest_all_desc, "🏆",  completedQuests >= gameData.quests.size),
        )

        groups["Collection"] = listOf(
            ach("pet_first", R.string.achievement_pet_first_name, R.string.achievement_pet_first_desc, "🐾", pets.isNotEmpty()),
            ach("pet_all",   R.string.achievement_pet_all_name,   R.string.achievement_pet_all_desc,   "🦁", pets.size >= gameData.pets.size),
        )

        groups["Prestige"] = listOf(
            ach("prestige_first",   R.string.achievement_prestige_first_name,   R.string.achievement_prestige_first_desc,   "⭐", prestigeMap.values.any { it >= 1 }),
            ach("prestige_any_3",   R.string.achievement_prestige_any_3_name,   R.string.achievement_prestige_any_3_desc,   "🌟", prestigeMap.values.any { it >= 3 }),
            ach("prestige_all_1",   R.string.achievement_prestige_all_1_name,   R.string.achievement_prestige_all_1_desc,   "⭐⭐", Skills.ALL.all { (prestigeMap[it] ?: 0) >= 1 }),
            ach("prestige_all_3",   R.string.achievement_prestige_all_3_name,   R.string.achievement_prestige_all_3_desc,   "👑", Skills.ALL.all { (prestigeMap[it] ?: 0) >= 3 }),
        )

        val townTiers = flags.townBuildingTiers
        val allBuildingKeys = gameData.townBuildings.keys
        groups["Town"] = listOf(
            ach("town_first_upgrade", R.string.achievement_town_first_upgrade_name, R.string.achievement_town_first_upgrade_desc, "🏗️",
                allBuildingKeys.any { (townTiers[it] ?: 0) >= 1 }),
            ach("town_all_tier1", R.string.achievement_town_all_tier1_name, R.string.achievement_town_all_tier1_desc, "🏘️",
                allBuildingKeys.all { (townTiers[it] ?: 0) >= 1 }),
            ach("town_one_maxed", R.string.achievement_town_one_maxed_name, R.string.achievement_town_one_maxed_desc, "🏰",
                allBuildingKeys.any { (townTiers[it] ?: 0) >= (gameData.townBuildings[it]?.tiers?.size ?: 0) }),
            ach("town_all_maxed", R.string.achievement_town_all_maxed_name, R.string.achievement_town_all_maxed_desc, "👑",
                allBuildingKeys.all { (townTiers[it] ?: 0) >= (gameData.townBuildings[it]?.tiers?.size ?: 0) }),
        )

        groups["Tower"] = listOf(
            ach("tower_first_floor",      R.string.achievement_tower_first_floor_name,      R.string.achievement_tower_first_floor_desc,      "🗼", flags.towerBestFloor >= 1),
            ach("tower_floor_10",         R.string.achievement_tower_floor_10_name,         R.string.achievement_tower_floor_10_desc,         "🗼", flags.towerBestFloor >= 10),
            ach("tower_floor_50",         R.string.achievement_tower_floor_50_name,         R.string.achievement_tower_floor_50_desc,         "🗼", flags.towerBestFloor >= 50),
            ach("tower_floor_100",        R.string.achievement_tower_floor_100_name,        R.string.achievement_tower_floor_100_desc,        "🏆", flags.towerBestFloor >= 100),
            ach("tower_floor_250",        R.string.achievement_tower_floor_250_name,        R.string.achievement_tower_floor_250_desc,        "👑", flags.towerBestFloor >= 250),
            ach("tower_all_milestones",   R.string.achievement_tower_all_milestones_name,   R.string.achievement_tower_all_milestones_desc,   "🌟", flags.towerMilestonesClaimed.size >= 25),
        )

        val all = groups.values.flatten()
        AchievementsUiState(
            isLoading     = false,
            byGroup       = groups,
            unlockedCount = all.count { it.isUnlocked },
            totalCount    = all.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AchievementsUiState())

    private fun ach(id: String, nameRes: Int, descRes: Int, emoji: String, unlocked: Boolean) =
        Achievement(id = id, name = context.getString(nameRes), description = context.getString(descRes), emoji = emoji, isUnlocked = unlocked)
}
