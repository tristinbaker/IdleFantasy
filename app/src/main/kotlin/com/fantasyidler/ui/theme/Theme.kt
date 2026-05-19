package com.fantasyidler.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.fantasyidler.ui.theme.fantasy.FantasyTheme

private val DarkColorScheme = darkColorScheme(
    primary              = GoldPrimary,
    onPrimary            = DarkBackground,
    primaryContainer     = GoldContainer,
    onPrimaryContainer   = GoldOnContainer,
    secondary            = BrownSecondary,
    onSecondary          = ParchmentText,
    secondaryContainer   = BrownContainer,
    onSecondaryContainer = BrownOnContainer,
    background           = DarkBackground,
    onBackground         = ParchmentText,
    surface              = DarkSurface,
    onSurface            = ParchmentText,
    surfaceVariant       = DarkSurfaceVariant,
    onSurfaceVariant     = ParchmentTextMuted,
    error                = ErrorRed,
    onError              = ParchmentText,
)

private val LightColorScheme = lightColorScheme(
    primary              = GoldPrimary,
    onPrimary            = DarkText,
    primaryContainer     = GoldContainerLight,
    onPrimaryContainer   = GoldOnContainerLight,
    secondary            = BrownSecondary,
    onSecondary          = LightBackground,
    secondaryContainer   = BrownContainerLight,
    onSecondaryContainer = BrownOnContainerLight,
    background           = LightBackground,
    onBackground         = DarkText,
    surface              = LightSurface,
    onSurface            = DarkText,
    surfaceVariant       = LightSurfaceVariant,
    onSurfaceVariant     = DarkTextMuted,
    error                = ErrorRed,
    onError              = ParchmentText,
)

/**
 * Existing top-level app theme. Delegates to [FantasyTheme] so screens get
 * `LocalFantasyTokens.current` for free, then layers the legacy MaterialTheme
 * mappings on top — keeping every existing `MaterialTheme.typography.*` and
 * `MaterialTheme.colorScheme.*` lookup intact during the migration.
 */
@Composable
fun FantasyIdlerTheme(
    themePreference: String = "dark",
    content: @Composable () -> Unit,
) {
    val useDark = when (themePreference) {
        "light"  -> false
        "dark"   -> true
        else     -> isSystemInDarkTheme()
    }
    FantasyTheme(darkTheme = useDark) {
        MaterialTheme(
            colorScheme = if (useDark) DarkColorScheme else LightColorScheme,
            typography  = AppTypography,
            shapes      = AppShapes,
            content     = content,
        )
    }
}
