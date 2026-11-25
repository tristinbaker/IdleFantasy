package com.fantasyidler.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * Settings > Swipe between tabs. Off stops tabbed screens' pagers from responding to
 * swipes, so tabs only change when tapped; TV remotes otherwise made some screens bounce
 * between tabs (issue #2118).
 */
val LocalTabSwipeEnabled = compositionLocalOf { true }
