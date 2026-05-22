package com.fantasyidler.ui.scene

/**
 * Tick-level events the scene engine reacts to. Adapters emit these; the Stage
 * translates them into Effects on tagged Layers via SceneConfig.eventMap.
 */
sealed class SceneEvent {

    /** An attack landed. [amount] is positive; 0 means a miss (use Dodge for that). */
    data class Hit(val attacker: String, val target: String, val amount: Int) : SceneEvent()

    /** A potential hit was avoided. */
    data class Dodge(val target: String) : SceneEvent()

    /** An item was produced by an action. [fromTag] is the layer it should appear to come from. */
    data class Produce(val item: String, val fromTag: String) : SceneEvent()

    /** Skill or tool action fired but produced nothing this tick. Keeps the stage alive on dry minutes. */
    data class Attempt(val toolTag: String) : SceneEvent()

    /** Player levelled up the [skill] mid-session. */
    data class LevelUp(val skill: String) : SceneEvent()

    /** Session terminated. [outcome] is freeform ("complete", "abandoned", "died"). */
    data class SessionEnd(val outcome: String) : SceneEvent()
}
