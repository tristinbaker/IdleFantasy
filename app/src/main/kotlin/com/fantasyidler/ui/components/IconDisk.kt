package com.fantasyidler.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fantasyidler.ui.theme.GoldPrimary

/**
 * Gold-tinted circular icon backdrop — the leading element on (almost) every
 * row in the app. Three overloads cover the common content payloads:
 *
 *  - Material [ImageVector] (settings rows, quick actions)
 *  - Emoji [String]         (skills, crops — until SD-generated sprites land)
 *  - Game [entityId]        (items, ores, equipment — routes through EntityIcon
 *                            which already handles the tier-coloured fallback)
 *
 * @param size      diameter; 40dp is the default body row size, 32dp suits
 *                  dense settings rows, 48dp suits hero blocks.
 * @param ringColor when non-null, draws a 2dp ring inside the disk — used by
 *                  Equipment slots to flash the tier rarity of the equipped
 *                  item.
 */
@Composable
fun IconDisk(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = GoldPrimary,
    background: Color = GoldPrimary.copy(alpha = 0.18f),
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

@Composable
fun IconDisk(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    background: Color = GoldPrimary.copy(alpha = 0.18f),
    ringColor: Color? = null,
) {
    DiskFrame(modifier = modifier, size = size, background = background, ringColor = ringColor) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
fun EntityIconDisk(
    entityId: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    background: Color = GoldPrimary.copy(alpha = 0.18f),
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
    Surface(
        shape = CircleShape,
        color = background,
        border = ringColor?.let { BorderStroke(2.dp, it) },
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}
