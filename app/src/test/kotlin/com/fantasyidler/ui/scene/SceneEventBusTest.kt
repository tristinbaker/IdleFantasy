package com.fantasyidler.ui.scene

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneEventBusTest {

    @Test
    fun `emitted events are delivered to a subscriber`() = runTest {
        val bus = SceneEventBus()
        val collected = mutableListOf<SceneEvent>()

        // UNDISPATCHED ensures the collector subscribes to the SharedFlow
        // before launch() returns — required because the bus has replay = 0
        // and would otherwise drop events emitted before subscription.
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.events.take(2).toList(collected)
        }

        bus.emit(SceneEvent.Produce(item = "copper_ore", fromTag = "output"))
        bus.emit(SceneEvent.Attempt(toolTag = "tool"))
        job.join()

        assertEquals(2, collected.size)
        assertEquals("copper_ore", (collected[0] as SceneEvent.Produce).item)
        assertEquals("tool", (collected[1] as SceneEvent.Attempt).toolTag)
    }

    @Test
    fun `tryEmit returns true when buffer has capacity`() {
        val bus = SceneEventBus()
        // Buffer capacity is 16 — no subscribers needed for tryEmit to succeed.
        repeat(16) {
            assertTrue(bus.tryEmit(SceneEvent.Attempt("tool")))
        }
    }
}
