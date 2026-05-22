package com.fantasyidler.ui.scene

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Per-session event channel. Adapters emit; the Stage subscribes.
 *
 * Buffer capacity of 16 means tryEmit() never drops events under normal pacing
 * (combat is one event every ~100ms worst case; skills synthesize ≤ 60/minute).
 *
 * One bus per active session. Created in SessionSceneSheet's DisposableEffect,
 * dropped when the sheet closes.
 */
class SceneEventBus {
    private val _events = MutableSharedFlow<SceneEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SceneEvent> = _events.asSharedFlow()

    suspend fun emit(event: SceneEvent) = _events.emit(event)
    fun tryEmit(event: SceneEvent): Boolean = _events.tryEmit(event)
}
