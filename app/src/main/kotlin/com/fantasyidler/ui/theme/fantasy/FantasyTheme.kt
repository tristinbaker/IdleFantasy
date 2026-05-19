package com.fantasyidler.ui.theme.fantasy

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.fantasyidler.ui.theme.AppShapes

/**
 * Wraps the app in [MaterialTheme] (so legacy `MaterialTheme.colorScheme`
 * lookups in existing screens keep working) and provides [LocalFantasyTokens]
 * so all foundation primitives can read chunky design tokens via
 * `LocalFantasyTokens.current`.
 */
@Composable
fun FantasyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) DefaultDarkTokens else DefaultLightTokens
    val c = tokens.colors

    val materialScheme = if (darkTheme) {
        darkColorScheme(
            primary              = c.primary,
            onPrimary            = c.onPrimary,
            secondary            = c.secondary,
            secondaryContainer   = c.secondaryContainer,
            background           = c.background,
            onBackground         = c.onSurface,
            surface              = c.surface,
            onSurface            = c.onSurface,
            surfaceVariant       = c.surfaceVariant,
            onSurfaceVariant     = c.onSurfaceMuted,
            error                = c.error,
            onError              = c.onSurface,
        )
    } else {
        lightColorScheme(
            primary              = c.primary,
            onPrimary            = c.onPrimary,
            secondary            = c.secondary,
            secondaryContainer   = c.secondaryContainer,
            background           = c.background,
            onBackground         = c.onSurface,
            surface              = c.surface,
            onSurface            = c.onSurface,
            surfaceVariant       = c.surfaceVariant,
            onSurfaceVariant     = c.onSurfaceMuted,
            error                = c.error,
            onError              = c.onSurface,
        )
    }

    CompositionLocalProvider(LocalFantasyTokens provides tokens) {
        MaterialTheme(
            colorScheme = materialScheme,
            shapes      = AppShapes,
            content     = content,
        )
    }
}
