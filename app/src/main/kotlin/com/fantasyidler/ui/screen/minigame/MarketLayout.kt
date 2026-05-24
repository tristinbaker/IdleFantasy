package com.fantasyidler.ui.screen.minigame

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import com.fantasyidler.R

/**
 * Canonical placement + interact data for the Market Center plaza.
 *
 * Coordinates are normalized to the plaza's measured size, top-left origin,
 * y growing downward. `widthFraction` is the sprite's width as a fraction of
 * the plaza width — height is derived from the bitmap's aspect ratio at draw
 * time. `interactRadius` is in the same normalized units as positions.
 */
object MarketLayout {

    val stations: List<Station> = listOf(
        Station(
            id              = "smithy",
            displayNameRes  = R.string.mh_station_smithy,
            x               = 0.18f,
            y               = 0.30f,
            widthFraction   = 0.22f,
            interactRadius  = 0.16f,
            skill           = "smithing",
        ),
        Station(
            id              = "runic_outcrop",
            displayNameRes  = R.string.mh_station_runic_outcrop,
            x               = 0.50f,
            y               = 0.22f,
            widthFraction   = 0.20f,
            interactRadius  = 0.16f,
            skill           = "runecrafting",
        ),
        Station(
            id              = "herbalist_cart",
            displayNameRes  = R.string.mh_station_herbalist_cart,
            x               = 0.82f,
            y               = 0.30f,
            widthFraction   = 0.22f,
            interactRadius  = 0.16f,
            skill           = "herblore",
        ),
        Station(
            id              = "cook_fire",
            displayNameRes  = R.string.mh_station_cook_fire,
            x               = 0.28f,
            y               = 0.78f,
            widthFraction   = 0.18f,
            interactRadius  = 0.15f,
            skill           = "cooking",
        ),
        Station(
            id              = "vendor_stall",
            displayNameRes  = R.string.mh_station_vendor_stall,
            x               = 0.74f,
            y               = 0.78f,
            widthFraction   = 0.24f,
            interactRadius  = 0.17f,
            opensShop       = true,
        ),
    )

    val decor: List<Decor> = listOf(
        Decor(id = "market_well",    x = 0.50f, y = 0.55f, widthFraction = 0.14f),
        Decor(id = "market_banner",  x = 0.05f, y = 0.10f, widthFraction = 0.10f),
        Decor(id = "market_banner",  x = 0.95f, y = 0.10f, widthFraction = 0.10f),
        Decor(id = "market_lantern", x = 0.10f, y = 0.92f, widthFraction = 0.06f),
        Decor(id = "market_lantern", x = 0.90f, y = 0.92f, widthFraction = 0.06f),
        Decor(id = "market_barrel",  x = 0.42f, y = 0.45f, widthFraction = 0.08f),
        Decor(id = "market_crate",   x = 0.58f, y = 0.45f, widthFraction = 0.08f),
        Decor(id = "market_cart",    x = 0.50f, y = 0.92f, widthFraction = 0.18f),
    )

    val avatarStart: Offset = Offset(0.50f, 0.55f)
    val avatarWidthFraction: Float = 0.08f

    val minX: Float = 0.04f
    val maxX: Float = 0.96f
    val minY: Float = 0.18f
    val maxY: Float = 0.88f

    val speedPerSecond: Float = 0.30f
    val joystickDeadzone: Float = 0.15f
    val walkFrameSwapMs: Long = 220L
}

@Immutable
data class Station(
    val id: String,
    val displayNameRes: Int,
    val x: Float,
    val y: Float,
    val widthFraction: Float,
    val interactRadius: Float,
    val skill: String? = null,
    val opensShop: Boolean = false,
)

@Immutable
data class Decor(
    val id: String,
    val x: Float,
    val y: Float,
    val widthFraction: Float,
)

enum class Direction { NORTH, SOUTH, EAST, WEST;

    val slug: String get() = name.lowercase()
}
