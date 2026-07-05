package com.fantasyidler.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.data.json.TradeRouteData
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QueuedSessionStarter
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.simulator.MercantileSimulator
import com.fantasyidler.simulator.SkillSimulator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
)

@HiltViewModel
class MercantileViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val queuedSessionStarter: QueuedSessionStarter,
    private val json: Json,
) : ViewModel() {

    private val _extra = MutableStateFlow(MercantileUiState())

    val uiState: StateFlow<MercantileUiState> = combine(
        playerRepo.playerFlow,
        sessionRepo.activeSessionFlow,
        _extra,
    ) { player, session, extra ->
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
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MercantileUiState())

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
                val enqueued = playerRepo.enqueueAction(
                    QueuedAction(
                        skillName           = Skills.MERCANTILE,
                        activityKey         = routeId,
                        skillDisplayName    = "Mercantile",
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
                val result  = MercantileSimulator.simulate(route, startXp, agilityLevel)
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
}
