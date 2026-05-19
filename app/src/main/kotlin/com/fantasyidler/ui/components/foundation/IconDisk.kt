package com.fantasyidler.ui.components.foundation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import com.fantasyidler.ui.components.EntityIcon
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/** Gold-tinted circular icon backdrop — Material [ImageVector] payload. */
@Composable
fun IconDisk(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = LocalFantasyTokens.current.spacing.xxl + LocalFantasyTokens.current.spacing.m,
    tint: Color = LocalFantasyTokens.current.colors.primary,
    background: Color = LocalFantasyTokens.current.colors.primary.copy(alpha = 0.18f),
    ringColor: Color? = null,
) {
    DiskFrame(modifier = modifier, size = size, background = background, ringColor = ringColor) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/** Gold-tinted circular icon backdrop — emoji payload. */
@Composable
fun IconDisk(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = LocalFantasyTokens.current.spacing.xxl + LocalFantasyTokens.current.spacing.m,
    background: Color = LocalFantasyTokens.current.colors.primary.copy(alpha = 0.18f),
    ringColor: Color? = null,
) {
    DiskFrame(modifier = modifier, size = size, background = background, ringColor = ringColor) {
        Text(
            text  = emoji,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/** Gold-tinted circular icon backdrop — game entity sprite payload. */
@Composable
fun EntityIconDisk(
    entityId: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = LocalFantasyTokens.current.spacing.xxl + LocalFantasyTokens.current.spacing.m,
    background: Color = LocalFantasyTokens.current.colors.primary.copy(alpha = 0.18f),
    ringColor: Color? = null,
) {
    DiskFrame(modifier = modifier, size = size, background = background, ringColor = ringColor) {
        EntityIcon(
            entityId = entityId,
            label = contentDescription,
            size = size * 0.7f,
        )
    }
}

@Composable
private fun DiskFrame(
    modifier: Modifier,
    size: Dp,
    background: Color,
    ringColor: Color?,
    content: @Composable () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    Surface(
        shape = tokens.shapes.badge,
        color = background,
        border = ringColor?.let { BorderStroke(tokens.shapes.borderStroke, it) },
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewIconDisk() {
    FantasyPreviewSurface {
        IconDisk(emoji = "⚒")
    }
}
