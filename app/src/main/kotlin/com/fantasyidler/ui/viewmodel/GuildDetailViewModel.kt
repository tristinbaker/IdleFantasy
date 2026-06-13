package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.GuildDailyWithProgress
import com.fantasyidler.repository.GuildQuestClaimResult
import com.fantasyidler.repository.GuildQuestWithProgress
import com.fantasyidler.repository.GuildRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.toTitleCase
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

data class GuildDetailUiState(
    val isLoading: Boolean = true,
    val guildKey: String = "",
    val guildLevel: Int = 0,
    val guildRep: Long = 0L,
    val repInLevel: Long = 0L,
    val repForLevel: Long = 1L,
    val quests: List<GuildQuestWithProgress> = emptyList(),
    val dailies: List<GuildDailyWithProgress> = emptyList(),
    val nextResetMs: Long = 0L,
    val allCurrentLevelQuestsDone: Boolean = false,
    val questGateBlocked: Boolean = false,
    val snackbarMessage: String? = null,
    val inventory: Map<String, Int> = emptyMap(),
)

@HiltViewModel
class GuildDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playerRepo: PlayerRepository,
    private val guildRepo: GuildRepository,
    private val gameData: GameDataRepository,
    private val json: Json,
) : ViewModel() {

    val guild: String = savedStateHandle["guild"] ?: ""

    private val _extra = MutableStateFlow(GuildDetailUiState(guildKey = guild))

    init {
        viewModelScope.launch {
            guildRepo.ensureGuildDailiesRefreshed()
            _extra.update { it.copy(nextResetMs = guildRepo.nextResetMs()) }
        }
    }

    val uiState: StateFlow<GuildDetailUiState> = combine(
        playerRepo.playerFlow,
        guildRepo.observeQuestProgress(),
        _extra,
    ) { player, progressList, extra ->
        if (player == null) return@combine extra

        val flags: PlayerFlags = json.decodeFromString(player.flags)
        val inventory: Map<String, Int> = json.decodeFromString(player.inventory)
        val rep   = flags.guildReputation[guild] ?: 0L
        val completedQuestIds = progressList.filter { it.completed }.map { it.questId }.toSet()
        val level = guildRepo.guildLevel(guild, rep, completedQuestIds)
        val (repInLevel, repForLevel) = repProgressForLevel(rep, level)

        val progressMap = progressList.associateBy { it.questId }
        val quests = gameData.guildQuests.values
            .filter { it.guild == guild }
            .sortedBy { it.guildLevelRequired }
            .map { quest ->
                val row = progressMap[quest.id]
                GuildQuestWithProgress(
                    quest           = quest,
                    progress        = row?.progress ?: 0,
                    completed       = row?.completed ?: false,
                    effectiveAmount = guildRepo.effectiveQuestAmountFromFlags(quest, flags),
                )
            }

        val dailies = guildRepo.getGuildDailiesWithProgress(guild, flags)

        val tierQuests = gameData.guildQuests.values.filter { it.guild == guild && it.guildLevelRequired == level }
        val allCurrentLevelQuestsDone = level >= 1 && tierQuests.all { it.id in completedQuestIds }
        val questGateBlocked = level < 10 && tierQuests.isNotEmpty() && tierQuests.any { it.id !in completedQuestIds }

        extra.copy(
            isLoading                 = false,
            guildKey                  = guild,
            guildLevel                = level,
            guildRep                  = rep,
            repInLevel                = repInLevel,
            repForLevel               = repForLevel,
            quests                    = quests,
            dailies                   = dailies,
            allCurrentLevelQuestsDone = allCurrentLevelQuestsDone,
            questGateBlocked          = questGateBlocked,
            inventory                 = inventory,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuildDetailUiState(guildKey = guild))

    fun claimGuildQuest(questId: String) {
        viewModelScope.launch {
            when (val result = guildRepo.claimGuildQuestReward(questId)) {
                is GuildQuestClaimResult.Success -> {
                    val rewards = result.rewards
                    if (rewards.xp > 0 && rewards.xpSkill.isNotBlank()) {
                        playerRepo.applySessionResults(rewards.xpSkill, rewards.xp.toLong(), rewards.items)
                    } else if (rewards.items.isNotEmpty()) {
                        playerRepo.addItems(rewards.items)
                    }
                    if (rewards.coins > 0) playerRepo.addCoins(rewards.coins.toLong())
                    guildRepo.ensureGuildDailiesRefreshed()
                    val questName = gameData.guildQuests[questId]?.name ?: questId
                    val parts = buildList {
                        if (rewards.xp > 0) add("+${rewards.xp.toLong().formatXp()} XP")
                        if (rewards.coins > 0) add("+${rewards.coins.toLong().formatCoins()} coins")
                        rewards.items.forEach { (key, qty) -> add("${key.toTitleCase()} x$qty") }
                    }
                    val suffix = if (parts.isNotEmpty()) " (${parts.joinToString(", ")})" else ""
                    _extra.update { it.copy(snackbarMessage = "Quest complete: $questName!$suffix") }
                }
                else -> Unit
            }
        }
    }

    fun claimGuildDaily(templateId: String) {
        viewModelScope.launch {
            val flags = playerRepo.getFlags()
            val (newFlags, rewards) = guildRepo.claimGuildDaily(flags, templateId) ?: return@launch
            playerRepo.updateFlags(newFlags)
            playerRepo.recordWeeklyProgress("guild_daily", "any", 1)
            if (rewards.xp > 0 && rewards.xpSkill.isNotBlank()) {
                playerRepo.applySessionResults(rewards.xpSkill, rewards.xp.toLong(), rewards.items)
            } else if (rewards.items.isNotEmpty()) {
                playerRepo.addItems(rewards.items)
            }
            if (rewards.coins > 0) playerRepo.addCoins(rewards.coins.toLong())
            val parts = buildList {
                if (rewards.xp > 0) add("+${rewards.xp.toLong().formatXp()} XP")
                if (rewards.coins > 0) add("+${rewards.coins.toLong().formatCoins()} coins")
                rewards.items.forEach { (key, qty) -> add("${key.toTitleCase()} x$qty") }
            }
            val suffix = if (parts.isNotEmpty()) " (${parts.joinToString(", ")})" else ""
            _extra.update { it.copy(snackbarMessage = "Daily reward claimed!$suffix") }
        }
    }

    fun contributeFarmingDaily(templateId: String) {
        viewModelScope.launch {
            val inventory: Map<String, Int> = json.decodeFromString(playerRepo.getOrCreatePlayer().inventory)
            val consumed = guildRepo.contributeFarmingDaily(templateId, inventory)
            if (consumed > 0) {
                _extra.update { it.copy(snackbarMessage = "Contributed $consumed items.") }
            }
        }
    }

    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }
}
