package com.fantasyidler.ui.scene

/**
 * One visual element on a Stage. Pure data — rendering lives in Stage.kt.
 *
 * - [tag] is the targetable identifier ("player", "tool", "target", "output", ...).
 *   Effects reference layers by tag.
 * - [entityId] is the snake_case sprite id resolved by EntityIcon. Null means
 *   the layer is purely a decorative slot (e.g., a Compose-drawn primitive).
 * - [position] is a relative anchor; Stage maps these to actual offsets.
 * - [idleBehavior] runs whenever no event-triggered animation is active on
 *   this layer.
 * - [visible] is the initial visibility — output layers start hidden and only
 *   appear during a Produce event.
 */
data class Layer(
    val tag: String,
    val entityId: String?,
    val position: LayerPosition,
    val idleBehavior: IdleBehavior = IdleBehavior.None,
    val visible: Boolean = true,
)

/**
 * Where on the Stage a Layer is anchored. Stage.kt translates these into
 * dp offsets relative to the Stage size; consumers don't pick pixel coords.
 */
enum class LayerPosition {
    LEFT_GROUND,
    RIGHT_GROUND,
    CENTER_GROUND,
    LEFT_HOLD,
    RIGHT_HOLD,
    CENTER_HOLD,
    RIGHT_ACTOR,
    LEFT_ACTOR,
    OFF_TOP,
    OFF_BOTTOM,
}

/**
 * Looping property animation a Layer runs when nothing else is happening.
 * Drives a simple Modifier transform inside Stage.kt; no per-behavior file.
 */
enum class IdleBehavior {
    None,
    Bob,      // vertical sway, ~3dp amplitude, 1.2s cycle
    Breath,   // gentle scale 1.0 ↔ 1.04, 1.8s cycle
    Swing,    // rotation -8° ↔ +8°, 0.9s cycle (used by tools)
    Wobble,   // tiny rotation + horizontal jitter, 0.7s cycle (fishing rod)
    Drift,    // slow horizontal translation, used for Agility hero (Slice 4)
}
