package com.fantasyidler.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fantasyidler.ui.components.foundation.ChunkyCard as FoundationChunkyCard

/** Thin proxy — see [com.fantasyidler.ui.components.foundation.ChunkyCard]. */
@Composable
fun ChunkyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlight: Boolean = false,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) = FoundationChunkyCard(
    modifier = modifier,
    onClick = onClick,
    highlight = highlight,
    enabled = enabled,
    contentPadding = contentPadding,
    content = content,
)
