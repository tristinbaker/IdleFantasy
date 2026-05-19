package com.fantasyidler.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant

// Deprecated: prefer [ChunkyButton(variant = ChunkyButtonVariant.Secondary)] directly.
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = ChunkyButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    variant = ChunkyButtonVariant.Secondary,
    enabled = enabled,
)
