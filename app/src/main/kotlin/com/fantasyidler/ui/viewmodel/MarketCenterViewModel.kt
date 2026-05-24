package com.fantasyidler.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import com.fantasyidler.ui.screen.minigame.Direction
import com.fantasyidler.ui.screen.minigame.MarketLayout
import com.fantasyidler.ui.screen.minigame.Station
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.hypot

@Immutable
data class MarketCenterUiState(
    val avatarX: Float = MarketLayout.avatarStart.x,
    val avatarY: Float = MarketLayout.avatarStart.y,
    val facing: Direction = Direction.SOUTH,
    val isMoving: Boolean = false,
    val walkFrameA: Boolean = true,
    val nearestStation: Station? = null,
    val navEvent: MarketNavEvent? = null,
)

sealed interface MarketNavEvent {
    data object OpenShop : MarketNavEvent
    data class ComingSoon(val displayNameRes: Int) : MarketNavEvent
}

/**
 * Holds the plaza's avatar position, facing, walk-frame, and the proximity
 * check for the nearest station. Movement is driven by per-frame `onTick`
 * calls from the composable, which pass the current joystick offset and a
 * delta-time. No repo dependencies in v1 — the constructor stays open for a
 * future race-specific avatar swap via `PlayerRepository`.
 */
@HiltViewModel
class MarketCenterViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(MarketCenterUiState())
    val uiState: StateFlow<MarketCenterUiState> = _uiState.asStateFlow()

    private var walkFrameAccumulatorMs: Long = 0L

    fun onTick(joystick: Offset, deltaSeconds: Float) {
        val magnitude = hypot(joystick.x, joystick.y)
        val moving    = magnitude >= MarketLayout.joystickDeadzone

        _uiState.update { state ->
            val (newX, newY, newFacing) = if (moving) {
                val nx = (state.avatarX + joystick.x * MarketLayout.speedPerSecond * deltaSeconds)
                    .coerceIn(MarketLayout.minX, MarketLayout.maxX)
                val ny = (state.avatarY + joystick.y * MarketLayout.speedPerSecond * deltaSeconds)
                    .coerceIn(MarketLayout.minY, MarketLayout.maxY)
                val face = if (abs(joystick.x) > abs(joystick.y)) {
                    if (joystick.x > 0f) Direction.EAST else Direction.WEST
                } else {
                    if (joystick.y > 0f) Direction.SOUTH else Direction.NORTH
                }
                Triple(nx, ny, face)
            } else {
                walkFrameAccumulatorMs = 0L
                Triple(state.avatarX, state.avatarY, state.facing)
            }

            // Toggle walk frame on a fixed cadence so the animation reads
            // cleanly at any framerate.
            val swap = if (moving) {
                walkFrameAccumulatorMs += (deltaSeconds * 1000f).toLong()
                if (walkFrameAccumulatorMs >= MarketLayout.walkFrameSwapMs) {
                    walkFrameAccumulatorMs = 0L
                    true
                } else false
            } else false

            val nearest = MarketLayout.stations
                .minByOrNull { hypot(it.x - newX, it.y - newY) }
                ?.takeIf { hypot(it.x - newX, it.y - newY) <= it.interactRadius }

            state.copy(
                avatarX        = newX,
                avatarY        = newY,
                facing         = newFacing,
                isMoving       = moving,
                walkFrameA     = if (swap) !state.walkFrameA else state.walkFrameA,
                nearestStation = nearest,
            )
        }
    }

    fun onEnterTapped() {
        val station = _uiState.value.nearestStation ?: return
        val event = when {
            station.opensShop -> MarketNavEvent.OpenShop
            else              -> MarketNavEvent.ComingSoon(station.displayNameRes)
        }
        _uiState.update { it.copy(navEvent = event) }
    }

    fun consumeNavEvent() {
        _uiState.update { it.copy(navEvent = null) }
    }
}
