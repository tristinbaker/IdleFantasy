package com.fantasyidler.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Carries the user's in-app Text Size preference so it can be re-applied inside dialogs and
 * bottom sheets. Those open their own Android window and derive a fresh LocalDensity from it,
 * so the LocalDensity override MainActivity applies to the rest of the composition doesn't
 * reach them (issue #1128) -- but a plain CompositionLocal like this one does.
 */
val LocalAppFontScale = compositionLocalOf { 1f }

/** Wraps a dialog/bottom sheet's content so it honours the app's Text Size setting. */
@Composable
fun ScaledSheetContent(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, LocalAppFontScale.current)) {
        content()
    }
}
