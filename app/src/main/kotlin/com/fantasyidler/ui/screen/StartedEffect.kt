package com.fantasyidler.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope

/** Run display-only work while this screen is visible, without dropping its UI state. */
@Composable
internal fun StartedEffect(key: Any?, block: suspend CoroutineScope.() -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, key) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED, block)
    }
}
