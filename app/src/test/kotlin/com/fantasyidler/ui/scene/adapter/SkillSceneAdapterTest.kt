package com.fantasyidler.ui.scene.adapter

import com.fantasyidler.ui.scene.SceneEvent
import com.fantasyidler.ui.scene.SceneEventBus
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)

class SkillSceneAdapterTest {

    @Test
    fun `playFrame emits one Produce per item`() = runTest {
        val bus = SceneEventBus()
        val collected = mutableListOf<SceneEvent>()
        val collectorJob = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.events.take(3).toList(collected)
        }

        val adapter = SkillSceneAdapter(
            bus = bus,
            random = Random(seed = 42),
            jitterFraction = 0f,  // deterministic intervals
        )

        adapter.playFrame(items = listOf("copper_ore", "tin_ore", "copper_ore"), durationMs = 3000L)
        collectorJob.join()

        val produces = collected.filterIsInstance<SceneEvent.Produce>()
        assertEquals(3, produces.size)
        assertEquals("copper_ore", produces[0].item)
        assertEquals("tin_ore", produces[1].item)
        assertEquals("copper_ore", produces[2].item)
        produces.forEach { assertEquals("target", it.fromTag) }
    }

    @Test
    fun `playFrame emits one Attempt when items is empty`() = runTest {
        val bus = SceneEventBus()
        val collected = mutableListOf<SceneEvent>()
        val collectorJob = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.events.take(1).toList(collected)
        }

        val adapter = SkillSceneAdapter(bus = bus, random = Random(0))
        adapter.playFrame(items = emptyList(), durationMs = 1000L)
        collectorJob.join()

        assertEquals(1, collected.size)
        assertTrue(collected[0] is SceneEvent.Attempt)
    }

    @Test
    fun `playFrame respects total durationMs even with jitter`() = runTest {
        val bus = SceneEventBus()
        val collectorJob = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.events.take(2).toList(mutableListOf())
        }

        val adapter = SkillSceneAdapter(
            bus = bus,
            random = Random(seed = 1),
            jitterFraction = 0.15f,
        )

        val start = currentTime
        adapter.playFrame(items = listOf("copper_ore", "copper_ore"), durationMs = 2000L)
        val elapsed = currentTime - start
        collectorJob.join()

        assertEquals(2000L, elapsed)
    }
}
