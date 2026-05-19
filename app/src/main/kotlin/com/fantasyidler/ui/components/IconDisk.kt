package com.fantasyidler.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.components.foundation.EntityIconDisk as FoundationEntityIconDisk
import com.fantasyidler.ui.components.foundation.IconDisk as FoundationIconDisk

/** Thin proxy — see [com.fantasyidler.ui.components.foundation.IconDisk]. */
@Composable
fun IconDisk(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = GoldPrimary,
    background: Color = GoldPrimary.copy(alpha = 0.18f),
    ringColor: Color? = null,
) = FoundationIconDisk(
    imageVector = imageVector,
    contentDescription = contentDescription,
    modifier = modifier,
    size = size,
    tint = tint,
    background = background,
    ringColor = ringColor,
)

/** Thin proxy — see [com.fantasyidler.ui.components.foundation.IconDisk]. */
@Composable
fun IconDisk(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    background: Color = GoldPrimary.copy(alpha = 0.18f),
    ringColor: Color? = null,
) = FoundationIconDisk(
    emoji = emoji,
    modifier = modifier,
    size = size,
    background = background,
    ringColor = ringColor,
)

/** Thin proxy — see [com.fantasyidler.ui.components.foundation.EntityIconDisk]. */
@Composable
fun EntityIconDisk(
    entityId: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    background: Color = GoldPrimary.copy(alpha = 0.18f),
    ringColor: Color? = null,
) = FoundationEntityIconDisk(
    entityId = entityId,
    contentDescription = contentDescription,
    modifier = modifier,
    size = size,
    background = background,
    ringColor = ringColor,
)
