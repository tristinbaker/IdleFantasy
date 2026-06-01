package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.GuildRepository
import com.fantasyidler.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class GuildSummary(
    val guildKey: String,
    val level: Int,
    val rep: Long,
    val repInLevel: Long,
    val repForLevel: Long,
    val claimableQuestCount: Int,
    val claimableDailyCount: Int,
    val hasDailiesAvailable: Boolean,
    val questGateBlocked: Boolean,
)

data class GuildHallUiState(
    val isLoading: Boolean = true,
    val guilds: List<GuildSummary> = emptyList(),
)

@HiltViewModel
class GuildHallViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val guildRepo: GuildRepository,
    private val gameData: GameDataRepository,
    private val json: Json,
) : ViewModel() {

    val uiState: StateFlow<GuildHallUiState> = combine(
        playerRepo.playerFlow,
        guildRepo.observeQuestProgress(),
    ) { player, progressList ->
        if (player == null) return@combine GuildHallUiState()

        val flags: PlayerFlags = json.decodeFromString(player.flags)
        val progressMap = progressList.associateBy { it.questId }
        val completedQuestIds = progressList.filter { it.completed }.map { it.questId }.toSet()

        val summaries = GuildRepository.ALL_GUILDS.map { guild ->
            val rep   = flags.guildReputation[guild] ?: 0L
            val level = guildRepo.guildLevel(guild, rep, completedQuestIds)
            val (repInLevel, repForLevel) = repProgressForLevel(rep, level)

            val claimableQuests = gameData.guildQuests.values
                .filter { it.guild == guild && level >= it.guildLevelRequired }
                .count { quest ->
                    val row = progressMap[quest.id]
                    row != null && !row.completed && row.progress >= quest.amount
                }

            val dailies = guildRepo.getGuildDailiesWithProgress(guild, flags)
            val claimableDailies = dailies.count { it.progress >= it.template.amount && !it.claimed }
            val hasDailiesAvailable = dailies.isNotEmpty() && dailies.any { !it.claimed }

            val tierQuests = gameData.guildQuests.values.filter { it.guild == guild && it.guildLevelRequired == level }
            val questGateBlocked = rep > 0L && level < 10 && tierQuests.isNotEmpty() && tierQuests.any { it.id !in completedQuestIds }

            GuildSummary(
                guildKey            = guild,
                level               = level,
                rep                 = rep,
                repInLevel          = repInLevel,
                repForLevel         = repForLevel,
                claimableQuestCount = claimableQuests,
                claimableDailyCount = claimableDailies,
                hasDailiesAvailable = hasDailiesAvailable,
                questGateBlocked    = questGateBlocked,
            )
        }

        GuildHallUiState(isLoading = false, guilds = summaries)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuildHallUiState())
}

internal fun repProgressForLevel(rep: Long, level: Int): Pair<Long, Long> {
    if (level >= 10) return 1L to 1L
    val lo = if (level == 0) 0L else GuildRepository.REP_THRESHOLDS[level - 1]
    val hi = GuildRepository.REP_THRESHOLDS[level]
    return (rep - lo).coerceAtLeast(0L) to (hi - lo)
}
