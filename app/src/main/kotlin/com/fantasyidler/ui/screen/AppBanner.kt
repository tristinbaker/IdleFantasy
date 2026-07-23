package com.fantasyidler.ui.screen

import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QueuedBanner(val message: String, val onConsumed: () -> Unit)

/**
 * App-wide replacement for both Android Toasts and per-screen Snackbars. Enqueuing is a
 * plain non-suspending call, so unlike SnackbarHostState.showSnackbar() there's no
 * cancellable suspend point for a later message to race with -- that race is what caused
 * messages to "annihilate" each other under the old per-screen Snackbar approach (#1105).
 */
object AppBannerCenter {
    private val pending = ArrayDeque<QueuedBanner>()
    private val _current = MutableStateFlow<QueuedBanner?>(null)
    val current: StateFlow<QueuedBanner?> = _current.asStateFlow()

    @Synchronized
    fun enqueue(message: String, onConsumed: () -> Unit = {}) {
        pending.addLast(QueuedBanner(message, onConsumed))
        if (_current.value == null) advance()
    }

    @Synchronized
    fun dismissCurrent() {
        _current.value?.onConsumed?.invoke()
        advance()
    }

    private fun advance() {
        _current.value = pending.removeFirstOrNull()
    }
}

/** Drop-in replacement for the old Toast-based effect -- same signature, same call sites. */
@Composable
fun AppBannerEffect(message: String?, onConsumed: () -> Unit) {
    LaunchedEffect(message) {
        message?.let { AppBannerCenter.enqueue(it, onConsumed) }
    }
}

private const val BANNER_DISPLAY_MS = 2_500L

/**
 * Hosted once at the navigation root so banners persist across screen changes. Rendered in
 * its own Dialog window (non-modal, no scrim) rather than as regular content, because a
 * ModalBottomSheet or AlertDialog opens its own Android window above normal screen content --
 * a banner drawn as a plain Composable overlay would be invisible behind either of those.
 *
 * The window is sized to wrap its content (full width, only as tall as the banner itself)
 * rather than the full screen -- a MATCH_PARENT window would swallow every touch on screen
 * (swipe-to-scroll, pulling down the notification shade) since FLAG_NOT_TOUCH_MODAL only lets
 * touches outside the window's bounds pass through, not touches that land on empty space
 * inside it.
 */
@Composable
fun AppBannerHost() {
    val current by AppBannerCenter.current.collectAsState()

    LaunchedEffect(current) {
        if (current != null) {
            delay(BANNER_DISPLAY_MS)
            AppBannerCenter.dismissCurrent()
        }
    }

    val banner = current ?: return

    Dialog(
        onDismissRequest = { AppBannerCenter.dismissCurrent() },
        properties = DialogProperties(
            dismissOnBackPress      = false,
            dismissOnClickOutside   = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.let { window ->
                window.setGravity(Gravity.TOP)
                window.setDimAmount(0f)
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
                window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }

        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }

        AnimatedVisibility(
            visible = visible,
            enter   = slideInVertically { -it } + fadeIn(),
            exit    = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            Surface(
                color           = MaterialTheme.colorScheme.primary,
                contentColor    = MaterialTheme.colorScheme.onPrimary,
                shape           = RoundedCornerShape(12.dp),
                tonalElevation  = 8.dp,
                shadowElevation = 8.dp,
                modifier        = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication        = null,
                        onClick           = { AppBannerCenter.dismissCurrent() },
                    ),
            ) {
                Text(
                    text     = banner.message,
                    style    = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}
