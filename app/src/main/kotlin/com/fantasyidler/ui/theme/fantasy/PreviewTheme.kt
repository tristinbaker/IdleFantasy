package com.fantasyidler.ui.theme.fantasy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Wraps preview content in [FantasyTheme] + a surface-colored backdrop so
 * `@PreviewLightDark` renders foundation primitives against the same canvas
 * they will live on at runtime.
 */
@Composable
fun FantasyPreviewSurface(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    FantasyTheme(darkTheme = darkTheme) {
        val tokens = LocalFantasyTokens.current
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(tokens.colors.background)
                .padding(tokens.spacing.l),
        ) {
            content()
        }
    }
}
