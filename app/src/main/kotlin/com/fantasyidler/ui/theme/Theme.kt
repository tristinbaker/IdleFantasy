package com.fantasyidler.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val FantasyColorScheme = darkColorScheme(
    primary             = GoldPrimary,
    onPrimary           = DarkBackground,
    primaryContainer    = GoldContainer,
    onPrimaryContainer  = GoldOnContainer,
    secondary           = BrownSecondary,
    onSecondary         = ParchmentText,
    secondaryContainer  = BrownContainer,
    onSecondaryContainer = BrownOnContainer,
    background          = DarkBackground,
    onBackground        = ParchmentText,
    surface             = DarkSurface,
    onSurface           = ParchmentText,
    surfaceVariant      = DarkSurfaceVariant,
    onSurfaceVariant    = ParchmentTextMuted,
    error               = ErrorRed,
    onError             = ParchmentText,
)

/**
 * Fantasy Idler always uses the dark theme — it fits the RPG aesthetic and is easier
 * on the eyes for long play sessions. Dynamic colour is intentionally not used.
 */
@Composable
fun FantasyIdlerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FantasyColorScheme,
        typography  = AppTypography,
        content     = content,
    )
}
