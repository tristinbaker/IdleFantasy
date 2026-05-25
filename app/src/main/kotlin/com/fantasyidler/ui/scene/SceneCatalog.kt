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

    /**
     * Smithing-minigame scene. Forge on the left (breath idle reads as a
     * flickering fire), anvil + sword in the centre, hammer on the right
     * (no idle — the minigame's HAMMER phase animates it), water bucket on
     * the right ground.
     *
     * Effects wired by [SmithingMinigameViewModel]:
     *  - `Hit("anvil", _, _)`  → anvil shake + sparks  (HAMMER tap)
     *  - `Produce("sword", _)` → sword glow + steam arc (QUENCH success)
     *  - `Attempt(_)`          → muted shake             (HAMMER miss)
     *  - `LevelUp(_)`          → big burst              (perfect run)
     */
    val SMITHY: SceneConfig = SceneConfig(
        id = "smithy",
        backgroundEntityId = null,
        layers = listOf(
            Layer(
                tag = "forge",
                entityId = "smithy_forge_fire",
                position = LayerPosition.LEFT_GROUND,
                idleBehavior = IdleBehavior.Breath,
            ),
            Layer(
                tag = "anvil",
                entityId = "smithy_anvil",
                position = LayerPosition.CENTER_GROUND,
                idleBehavior = IdleBehavior.None,
            ),
            Layer(
                tag = "sword",
                entityId = "smithy_sword_cold",  // swapped per phase by smithy(...)
                position = LayerPosition.CENTER_HOLD,
                idleBehavior = IdleBehavior.None,
            ),
            Layer(
                tag = "hammer",
                entityId = "smithy_hammer",
                position = LayerPosition.RIGHT_ACTOR,
                idleBehavior = IdleBehavior.None,
            ),
            Layer(
                tag = "water_bucket",
                entityId = "smithy_water_bucket",
                position = LayerPosition.RIGHT_GROUND,
                idleBehavior = IdleBehavior.None,
            ),
        ),
        eventMap = mapOf(
            SceneEvent.Hit::class to { event ->
                val hit = event as SceneEvent.Hit
                val mag = when {
                    hit.amount >= 2 -> ShakeMagnitude.Medium
                    hit.amount >= 1 -> ShakeMagnitude.Small
                    else            -> ShakeMagnitude.Small
                }
                val burstCount = if (hit.amount >= 2) 6 else 3
                listOf(
                    Effect.Shake(tag = hit.target, magnitude = mag),
                    Effect.Burst(tag = hit.target, count = burstCount),
                )
            },
            SceneEvent.Produce::class to { _ ->
                // The quench "steam puff" is a Burst on the sword + a HitFlash
                // ripple. No ArcOut yet — Stage renders ArcOut as a real
                // EntityIcon and we don't have a `steam` sprite to fly.
                listOf(
                    Effect.Burst(tag = "sword", count = 10),
                    Effect.HitFlash(tag = "sword"),
                    Effect.Shake(tag = "water_bucket", magnitude = ShakeMagnitude.Small),
                )
            },
            SceneEvent.Attempt::class to { _ ->
                listOf(Effect.Shake(tag = "anvil", magnitude = ShakeMagnitude.Small))
            },
            SceneEvent.LevelUp::class to { _ ->
                listOf(
                    Effect.Burst(tag = "sword", count = 12),
                    Effect.HitFlash(tag = "sword"),
                )
            },
        ),
    )

    /**
     * Returns a copy of [SMITHY] with the sword layer's entityId swapped to
     * reflect the current minigame phase (`smithy_sword_cold` during HAMMER,
     * `smithy_sword_hot` after HEAT, `smithy_sword_finished` after QUENCH).
     */
    fun smithy(swordEntityId: String): SceneConfig {
        return SMITHY.copy(
            layers = SMITHY.layers.map { layer ->
                if (layer.tag == "sword") layer.copy(entityId = swordEntityId) else layer
            },
        )
    }
}
