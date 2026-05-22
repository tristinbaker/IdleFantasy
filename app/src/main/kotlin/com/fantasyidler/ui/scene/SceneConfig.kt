package com.fantasyidler.ui.scene

import kotlin.reflect.KClass

/**
 * A complete scene declaration: which layers exist, what background to use,
 * and how each event type should compose into effects.
 *
 * Pure data — instances live in SceneCatalog.kt and are referenced by
 * SessionSceneSheet to drive a specific surface (Mining, Combat, Fishing, …).
 *
 * @property id Stable id used for logging + analytics (e.g., "mining").
 * @property backgroundEntityId Optional entityId rendered as a faded full-bleed
 *           sprite behind the layers (e.g., dungeon background for Combat,
 *           null for skills).
 * @property layers Initial layer set. Output-type layers should set visible=false.
 * @property eventMap Maps an event type to a factory that, given the event,
 *           produces zero or more Effects to play. Factories let us read
 *           per-event payload (e.g., the produced item's entityId for ArcOut).
 */
data class SceneConfig(
    val id: String,
    val backgroundEntityId: String?,
    val layers: List<Layer>,
    val eventMap: Map<KClass<out SceneEvent>, (SceneEvent) -> List<Effect>>,
)
