package com.fantasyidler.ui.scene.adapter

import com.fantasyidler.ui.scene.SceneEvent
import com.fantasyidler.ui.scene.SceneEventBus
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Synthesizes per-tick SceneEvents from the skill simulator's per-minute frames.
 *
 * The skill simulator emits one frame per minute carrying `items: List<Drop>` and
 * `xpGain`. There's no intra-minute granularity in persisted data. This adapter
 * spreads each frame's N items evenly across the minute (with small jitter so
 * "pop, pop, pop" doesn't feel robotic) and emits a Produce per item.
 *
 * Zero memory at rest — the adapter is created per sheet open, runs as a
 * coroutine, and dies when the sheet closes.
 *
 * @property toolTag Layer tag for the active tool (default "tool").
 * @property outputTag Layer tag the produced item should appear from
 *                    (default "target" for Mining where ore comes out of the
 *                    node, not the pickaxe).
 * @property jitterFraction ±fraction applied to each interval. 0.15 = ±15%.
 *                          Set to 0f in tests for deterministic timing.
 */
class SkillSceneAdapter(
    private val bus: SceneEventBus,
    private val toolTag: String = "tool",
    private val outputTag: String = "target",
    private val random: Random = Random.Default,
    private val jitterFraction: Float = 0.15f,
) {

    /**
     * Play one minute-frame's worth of events, blocking for [durationMs].
     *
     * - Empty [items]: emit one Attempt mid-frame and wait the rest.
     * - Non-empty [items]: emit one Produce per item at evenly-spaced
     *   intervals with optional jitter, padding to [durationMs] total.
     */
    suspend fun playFrame(
        items: List<String>,
        durationMs: Long = 60_000L,
    ) {
        if (items.isEmpty()) {
            delay(durationMs / 2)
            bus.emit(SceneEvent.Attempt(toolTag))
            delay(durationMs - durationMs / 2)
            return
        }

        val n = items.size
        val baseInterval = durationMs / n
        var elapsed = 0L

        for (item in items) {
            val rawJitter = baseInterval * jitterFraction * (random.nextFloat() * 2f - 1f)
            val wait = (baseInterval + rawJitter.toLong()).coerceAtLeast(50L)
            // Don't overshoot the frame.
            val safeWait = if (elapsed + wait > durationMs) (durationMs - elapsed).coerceAtLeast(0L) else wait
            delay(safeWait)
            elapsed += safeWait
            bus.emit(SceneEvent.Produce(item = item, fromTag = outputTag))
        }

        val remainder = durationMs - elapsed
        if (remainder > 0) delay(remainder)
    }
}
