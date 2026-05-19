package com.fantasyidler.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fantasyidler.ui.components.foundation.DangerZone as FoundationDangerZone

/** Thin proxy — see [com.fantasyidler.ui.components.foundation.DangerZone]. */
@Composable
fun DangerZone(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) = FoundationDangerZone(title = title, modifier = modifier, subtitle = subtitle, content = content)
