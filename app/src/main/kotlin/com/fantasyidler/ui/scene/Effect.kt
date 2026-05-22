package com.fantasyidler.ui.scene

import androidx.compose.ui.graphics.Color

/**
 * Transient visual overlay triggered by a SceneEvent. Stage.kt renders each
 * Effect for its declared duration then disposes it.
 *
 * Effects always target a [tag] that must match a Layer.tag in the active
 * SceneConfig. Unknown tags log a warning and skip — never crash.
 */
sealed class Effect {
    abstract val durationMs: Int

    /** Brightness + hue pulse on the tagged layer. Used on hits/produces. */
    data class HitFlash(
        val tag: String,
        override val durationMs: Int = 240,
    ) : Effect()

    /** Translational jitter. */
    data class Shake(
        val tag: String,
        val magnitude: ShakeMagnitude = ShakeMagnitude.Small,
        override val durationMs: Int = 280,
    ) : Effect()

    /** Floating "+N" text rising from the tagged layer. */
    data class DamagePopup(
        val tag: String,
        val amount: Int,
        val color: Color,
        override val durationMs: Int = 700,
    ) : Effect()

    /**
     * Spawn [entityId] at the [fromTag] layer and arc to [toTag]. If [toTag] is
     * a special sentinel ("OFF_TOP", "OFF_BOTTOM"), Stage arcs to that screen
     * edge. Used for "ore pops out and flies to inventory".
     */
    data class ArcOut(
        val fromTag: String,
        val toTag: String,
        val entityId: String,
        override val durationMs: Int = 600,
    ) : Effect()

    /** Radial particle pop centred on the layer. */
    data class Burst(
        val tag: String,
        val count: Int = 6,
        override val durationMs: Int = 400,
    ) : Effect()

    /** Coloured halo behind the layer for a window. */
    data class Glow(
        val tag: String,
        val color: Color,
        override val durationMs: Int = 800,
    ) : Effect()
}

enum class ShakeMagnitude(val dp: Float) {
    Small(2f),
    Medium(4f),
    Large(8f),
}
