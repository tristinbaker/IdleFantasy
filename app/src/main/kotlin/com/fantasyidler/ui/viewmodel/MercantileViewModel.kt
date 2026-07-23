package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.json.TradeRouteData
import com.fantasyidler.data.json.XpRange
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.ChurchRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QueuedSessionStarter
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.GuildRepository
import com.fantasyidler.repository.DailyQuestRepository
import com.fantasyidler.repository.WeeklyQuestRepository
import com.fantasyidler.simulator.MercantileSimulator
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.simulator.XpTable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import javax.inject.Inject
import android.content.Context
import com.fantasyidler.R
import dagger.hilt.android.qualifiers.ApplicationContext

data class MercantileUiState(
    val mercantileLevel: Int = 1,
    val mercantileXp: Long = 0L,
    val coins: Long = 0L,
    val tradeRoutes: List<TradeRouteData> = emptyList(),
    val isLoading: Boolean = true,
    val startingSession: Boolean = false,
    val snackbarMessage: String? = null,
    val anySessionActive: Boolean = false,
    val queueSize: Int = 0,
    val maxQueueSize: Int = 3,
    val activeQuests: Map<String, List<QuestIndicator>> = emptyMap(),
)

@HiltViewModel
class MercantileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val queuedSessionStarter: QueuedSessionStarter,
    private val questRepo: QuestRepository,
    private val guildRepo: GuildRepository,
    private val dailyQuestRepo: DailyQuestRepository,
    private val weeklyQuestRepo: WeeklyQuestRepository,
    private val json: Json,
) : ViewModel() {

    private val _extra = MutableStateFlow(MercantileUiState())

    val uiState: StateFlow<MercantileUiState> = combine(
        playerRepo.playerFlow,
        sessionRepo.activeSessionFlow,
        questRepo.observeProgress(),
        _extra,
    ) { player, session, questProgress, extra ->
        if (player == null) extra.copy(isLoading = true)
        else {
            val levels: Map<String, Int>  = json.decodeFromString(player.skillLevels)
            val xp:     Map<String, Long> = json.decodeFromString(player.skillXp)
            val flags   = try { json.decodeFromString<PlayerFlags>(player.flags) } catch (_: Exception) { PlayerFlags() }
            val level   = levels[Skills.MERCANTILE] ?: 1
            val routes  = gameData.tradeRoutes.filter { it.levelRequired <= level }
            extra.copy(
                isLoading        = false,
                mercantileLevel  = level,
                mercantileXp     = xp[Skills.MERCANTILE] ?: 0L,
                coins            = player.coins,
                tradeRoutes      = routes,
                anySessionActive = session != null,
                queueSize        = flags.sessionQueue.size,
                maxQueueSize     = playerRepo.maxQueueSize(flags),
                activeQuests     = computeActiveQuests(questProgress, flags, player.coins),
            )
        }
    }.flowOn(Dispatchers.Default)
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MercantileUiState())

    fun startTradeRoute(routeId: String) {
        viewModelScope.launch {
            val route = gameData.tradeRoutes.firstOrNull { it.id == routeId } ?: return@launch
            val player = playerRepo.getOrCreatePlayer()

            if (player.coins < route.coinCost) {
                _extra.update { it.copy(snackbarMessage = context.getString(R.string.mercantile_not_enough_coins, route.coinCost.toString())) }
                return@launch
            }

            val levels: Map<String, Int>  = json.decodeFromString(player.skillLevels)
            val xp:     Map<String, Long> = json.decodeFromString(player.skillXp)
            val agilityLevel = levels[Skills.AGILITY] ?: 1
            val mercFlags: PlayerFlags = json.decodeFromString(player.flags)

            if (sessionRepo.getActiveSession() != null) {
                val spent = playerRepo.spendCoins(route.coinCost.toLong())
                if (!spent) {
                    _extra.update { it.copy(snackbarMessage = context.getString(R.string.mercantile_not_enough_coins, route.coinCost.toString())) }
                    return@launch
                }
                val startXp = xp[Skills.MERCANTILE] ?: 0L
                val currentLevel = XpTable.levelForXp(startXp)
                val sortedKeys = route.xpRanges.keys.mapNotNull { it.toIntOrNull() }.sorted()
                val matchedKey = sortedKeys.lastOrNull { it <= currentLevel } ?: sortedKeys.firstOrNull()
                val xpRange = matchedKey?.let { route.xpRanges[it.toString()] } ?: XpRange(1, 1)

                val expectedRawXp = (xpRange.min + xpRange.max) * 30L
                val xpQueueMult = (if (mercFlags.xpBoostExpiresAt > System.currentTimeMillis()) 2.0 else 1.0) * ChurchRepository.xpMultiplier(mercFlags)
                val prestigeLevel = mercFlags.skillPrestige[Skills.MERCANTILE] ?: 0
                val prestigeMult = 1.0 + prestigeLevel * 0.10
                val estimatedXpGain = (expectedRawXp * xpQueueMult * prestigeMult).toLong()

                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(
                        skillName           = Skills.MERCANTILE,
                        activityKey         = routeId,
                        skillDisplayName    = "Mercantile",
                        estimatedXpGain     = estimatedXpGain,
                        estimatedDurationMs = SkillSimulator.sessionDurationMs(agilityLevel, mercFlags.skillPrestige[Skills.AGILITY] ?: 0),
                        coinRefund          = route.coinCost.toLong(),
                    )
                )
                if (!enqueued) {
                    playerRepo.addCoins(route.coinCost.toLong())
                }
                _extra.update {
                    it.copy(snackbarMessage = if (enqueued)
                        context.getString(R.string.mercantile_added_to_queue, route.displayName)
                    else
                        context.getString(R.string.snackbar_queue_full))
                }
                return@launch
            }

            _extra.update { it.copy(startingSession = true) }
            try {
                val spent = playerRepo.spendCoins(route.coinCost.toLong())
                if (!spent) {
                    _extra.update { it.copy(snackbarMessage = context.getString(R.string.mercantile_not_enough_coins, route.coinCost.toString())) }
                    return@launch
                }

                val startXp = xp[Skills.MERCANTILE] ?: 0L
                val result  = MercantileSimulator.simulate(
                    route, startXp, agilityLevel,
                    agilityPrestige = mercFlags.skillPrestige[Skills.AGILITY] ?: 0,
                )
                val framesJson = json.encodeToString(
                    json.serializersModule.serializer<List<SessionFrame>>(),
                    result.frames,
                )
                sessionRepo.startSession(
                    skillName        = Skills.MERCANTILE,
                    activityKey      = routeId,
                    frames           = framesJson,
                    durationMs       = result.durationMs,
                    skillDisplayName = "Mercantile",
                )
            } catch (e: Exception) {
                playerRepo.addCoins(route.coinCost.toLong())
                _extra.update { it.copy(snackbarMessage = context.getString(R.string.mercantile_route_start_failed, e.message ?: "")) }
            } finally {
                _extra.update { it.copy(startingSession = false) }
            }
        }
    }

    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }

    private fun computeActiveQuests(
        questProgress: List<com.fantasyidler.data.model.QuestProgress>,
        flags: PlayerFlags,
        coins: Long,
    ): Map<String, List<QuestIndicator>> {
        val result = mutableMapOf<String, MutableList<QuestIndicator>>()
        val progressById = questProgress.associateBy { it.questId }

        val activeDailies = dailyQuestRepo.getActiveDailyQuests(flags).filter { !it.claimed }
        val activeWeeklies = weeklyQuestRepo.getActiveWeeklyQuests(flags).filter { !it.claimed }
        val guildPool = gameData.guildDailyPool.associateBy { it.id }
        val activeGuildDailyIds = flags.guildDailyIds.filter { it !in flags.guildDailyClaimed }
        val completedIds = progressById.entries.filter { it.value.completed }.map { it.key }.toSet()

        fun addIndicator(key: String, category: QuestCategory, remaining: Int) {
            val route = gameData.tradeRoutes.find { it.id == key }
            val cost = route?.coinCost ?: 0
            val isCompletable = coins >= (cost * remaining)
            result.getOrPut("${Skills.MERCANTILE}:$key") { mutableListOf() }.add(QuestIndicator(category, isCompletable))
        }

        fun checkAndAdd(questType: String, questSkill: String, questTarget: String, questAmount: Int, questProgressVal: Int, category: QuestCategory) {
            if (questSkill != Skills.MERCANTILE) return
            val remaining = questAmount - questProgressVal
            if (remaining <= 0) return

            when (questType) {
                "trade" -> {
                    addIndicator(questTarget, category, remaining)
                }
            }
        }

        for ((id, quest) in gameData.quests) {
            val prog = progressById[id]
            if (prog?.completed == true) continue
            val prereqDone = quest.requiresPrevious == null ||
                    progressById[quest.requiresPrevious]?.completed == true
            if (!prereqDone) continue

            checkAndAdd(quest.type, quest.skill, quest.target, quest.amount, prog?.progress ?: 0, QuestCategory.MAIN)
        }

        for ((id, quest) in gameData.guildQuests) {
            val prog = progressById[id]
            if (prog?.completed == true) continue
            if (guildRepo.guildLevel(quest.guild, flags.guildDailyTierCounts, completedIds) < quest.guildLevelRequired) continue

            val effectiveAmount = guildRepo.effectiveQuestAmountFromFlags(quest, flags)
            checkAndAdd(quest.type, quest.guild, quest.target, effectiveAmount, prog?.progress ?: 0, QuestCategory.MAIN)
        }

        for (daily in activeDailies) {
            checkAndAdd(daily.template.type, daily.template.skill, daily.template.target, daily.template.amount, daily.progress, QuestCategory.DAILY)
        }

        for (weekly in activeWeeklies) {
            checkAndAdd(weekly.template.type, weekly.template.skill, weekly.template.target, weekly.template.amount, weekly.progress, QuestCategory.DAILY)
        }

        for (id in activeGuildDailyIds) {
            val template = guildPool[id] ?: continue
            val progress = flags.guildDailyProgress[id] ?: 0
            checkAndAdd(template.type, template.guild, template.target, template.amount, progress, QuestCategory.DAILY)
        }

        return result
    }
}
