package com.fantasyidler.ui.scene

/**
 * The complete catalog of declared scenes. Slice 1 ships MINING only;
 * Slices 2–4 add Combat + the rest of the 14 scenes.
 *
 * Adding a new scene = adding a `val` here. No new composables needed
 * unless the scene introduces a brand-new archetype.
 */
object SceneCatalog {

    /**
     * Archetype 2 — Tool + node. Pickaxe (RIGHT_ACTOR, idle Swing) strikes the
     * ore node (CENTER_HOLD). On each Produce event, the node shakes, a burst
     * fires, and the produced ore arcs off-top to suggest inventory pickup.
     *
     * The `tool` layer's entityId is overridden at construction time by the
     * host (see SessionSceneSheet) with the player's best-equipped pickaxe id;
     * the catalog default is "bronze_pickaxe".
     *
     * The `target` layer's entityId is overridden with the selected ore's node
     * id (e.g., "copper_ore_node").
     */
    val MINING: SceneConfig = SceneConfig(
        id = "mining",
        backgroundEntityId = null,
        layers = listOf(
            Layer(
                tag = "target",
                entityId = "copper_ore_node",  // overridable
                position = LayerPosition.CENTER_HOLD,
                idleBehavior = IdleBehavior.None,
            ),
            Layer(
                tag = "tool",
                entityId = "bronze_pickaxe",  // overridable
                position = LayerPosition.RIGHT_ACTOR,
                idleBehavior = IdleBehavior.Swing,
            ),
            Layer(
                tag = "output",
                entityId = null,  // populated per event
                position = LayerPosition.CENTER_HOLD,
                idleBehavior = IdleBehavior.None,
                visible = false,
            ),
        ),
        eventMap = mapOf(
            SceneEvent.Produce::class to { event ->
                val produce = event as SceneEvent.Produce
                listOf(
                    Effect.Shake(tag = "target", magnitude = ShakeMagnitude.Small),
                    Effect.Burst(tag = "target", count = 6),
                    Effect.ArcOut(
                        fromTag = "target",
                        toTag = "OFF_TOP",
                        entityId = produce.item,
                    ),
                )
            },
            SceneEvent.Attempt::class to { _ ->
                // Dry minute — just a small shake so the stage doesn't look frozen.
                listOf(Effect.Shake(tag = "target", magnitude = ShakeMagnitude.Small))
            },
            SceneEvent.LevelUp::class to { _ ->
                listOf(
                    Effect.Burst(tag = "tool", count = 12),
                    Effect.HitFlash(tag = "tool"),
                )
            },
        ),
    )

    /**
     * Returns a copy of [MINING] with the tool and target entity ids
     * overridden per-session.
     */
    fun mining(pickaxeId: String, oreNodeId: String): SceneConfig {
        return MINING.copy(
            layers = MINING.layers.map { layer ->
                when (layer.tag) {
                    "tool" -> layer.copy(entityId = pickaxeId)
                    "target" -> layer.copy(entityId = oreNodeId)
                    else -> layer
                }
            },
        )
    }
}
