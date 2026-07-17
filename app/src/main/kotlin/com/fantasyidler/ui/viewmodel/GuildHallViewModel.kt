package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.GuildRepository
import com.fantasyidler.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class GuildSummary(
    val guildKey: String,
    val level: Int,
    val dailiesCompletedThisTier: Int,
    val dailiesRequiredThisTier: Int,
    val claimableQuestCount: Int,
    val claimableDailyCount: Int,
    val hasDailiesAvailable: Boolean,
    val questGateBlocked: Boolean,
    val guildUnlocked: Boolean,
    val dailiesTodayTotal: Int,
    val dailiesTodayRemaining: Int,
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

    init {
        viewModelScope.launch { guildRepo.ensureGuildDailiesRefreshed() }
    }

    val uiState: StateFlow<GuildHallUiState> = combine(
        playerRepo.playerFlow,
        guildRepo.observeQuestProgress(),
    ) { player, progressList ->
        if (player == null) return@combine GuildHallUiState()

        val flags: PlayerFlags = json.decodeFromString(player.flags)
        val progressMap = progressList.associateBy { it.questId }
        val completedQuestIds = progressList.filter { it.completed }.map { it.questId }.toSet()

        val summaries = GuildRepository.ALL_GUILDS.map { guild ->
            val level = guildRepo.guildLevel(guild, flags.guildDailyTierCounts, completedQuestIds)
            val dailiesCompletedThisTier = if (level >= GuildRepository.DAILIES_REQUIRED_PER_TIER.size) 0
                else flags.guildDailyTierCounts["$guild:$level"] ?: 0
            val dailiesRequiredThisTier = GuildRepository.DAILIES_REQUIRED_PER_TIER.getOrElse(level) { 1 }

            val claimableQuests = gameData.guildQuests.values
                .filter { it.guild == guild && level >= it.guildLevelRequired }
                .count { quest ->
                    val row = progressMap[quest.id]
                    val effectiveAmount = guildRepo.effectiveQuestAmountFromFlags(quest, flags)
                    row != null && !row.completed && row.progress >= effectiveAmount
                }

            val dailies = guildRepo.getGuildDailiesWithProgress(guild, flags)
            val claimableDailies = dailies.count { it.progress >= it.template.amount && !it.claimed }
            val hasDailiesAvailable = dailies.isNotEmpty() && dailies.any { !it.claimed }

            val guildUnlocked = gameData.guildQuests.values.any { it.guild == guild && it.id in completedQuestIds }
            val tierQuests = gameData.guildQuests.values.filter { it.guild == guild && it.guildLevelRequired == level }
            val questGateBlocked = guildUnlocked && level < 10 && tierQuests.isNotEmpty() && tierQuests.any { it.id !in completedQuestIds }

            GuildSummary(
                guildKey                 = guild,
                level                    = level,
                dailiesCompletedThisTier = dailiesCompletedThisTier,
                dailiesRequiredThisTier  = dailiesRequiredThisTier,
                claimableQuestCount      = claimableQuests,
                claimableDailyCount      = claimableDailies,
                hasDailiesAvailable      = hasDailiesAvailable,
                questGateBlocked         = questGateBlocked,
                guildUnlocked            = guildUnlocked,
                dailiesTodayTotal        = dailies.size,
                dailiesTodayRemaining    = dailies.count { !it.claimed },
            )
        }

        GuildHallUiState(isLoading = false, guilds = summaries)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuildHallUiState())
}
