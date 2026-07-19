package com.fantasyidler.ui.screen

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Shows [message] as a Toast and immediately marks it consumed via [onConsumed], rather than
 * a suspending Snackbar — navigating away mid-display would cancel a Snackbar's coroutine
 * before it consumed the message, leaving it to reappear next time the screen recomposes
 * (issue #921, #1059).
 */
@Composable
fun ToastMessageEffect(message: String?, onConsumed: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            onConsumed()
        }
    }
}
