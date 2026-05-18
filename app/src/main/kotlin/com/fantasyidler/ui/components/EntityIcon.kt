package com.fantasyidler.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The single primitive used everywhere a named game entity needs a visual: items, ores,
 * equipment, dungeons, enemies, recipe outputs.
 *
 * Behavior:
 * - Resolves [entityId] to an `R.drawable.<entityId>` at runtime. If found, renders
 *   the bitmap with nearest-neighbor scaling ([FilterQuality.None]) so 1× pixel art
 *   stays sharp at every density.
 * - If no drawable exists yet, falls back per [fallback]. Default `TierColored`
 *   renders a rounded color-block in the tier color from [tier] (auto-detected from
 *   the id if not passed) with the first letter of [label]. This means every list in
 *   the app gets a colored placeholder *today*, and the day a PNG drops into
 *   `app/src/main/res/drawable-xxhdpi/<entityId>.png` the placeholder is replaced
 *   without any callsite change.
 *
 * @param entityId snake_case id from the JSON data files, e.g. "copper_ore",
 *                 "iron_sword", "goblin_cave".
 * @param size the side length of the icon (the visual is always square).
 * @param tier overrides automatic tier detection from the id.
 * @param label used for the fallback's initial letter; defaults to [entityId].
 */
@Composable
fun EntityIcon(
    entityId: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    tier: Tier? = null,
    label: String = entityId,
    fallback: EntityFallback = EntityFallback.TierColored,
) {
    val context = LocalContext.current
    val drawableId = remember(entityId) {
        if (entityId.isBlank()) 0
        else context.resources.getIdentifier(entityId, "drawable", context.packageName)
    }

    if (drawableId != 0) {
        Image(
            painter = painterResource(id = drawableId),
            contentDescription = label,
            filterQuality = FilterQuality.None,
            modifier = modifier.size(size),
        )
    } else {
        EntityIconFallback(
            label = label,
            tier = tier ?: tierFromKey(entityId),
            fallback = fallback,
            size = size,
            modifier = modifier,
        )
    }
}

/** Convenience overload for situations where a drawable id is already known. */
@Composable
fun EntityIcon(
    @DrawableRes drawableId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    Image(
        painter = painterResource(id = drawableId),
        contentDescription = contentDescription,
        filterQuality = FilterQuality.None,
        modifier = modifier.size(size),
    )
}

sealed class EntityFallback {
    /** Rounded color-block in the tier color with the first letter of the label. */
    data object TierColored : EntityFallback()

    /** A Material icon (e.g. AutoMirrored.Filled.HelpOutline) as the placeholder. */
    data class MaterialIcon(val icon: ImageVector) : EntityFallback()

    /** No visual; an empty Box reserving [size] × [size]. */
    data object None : EntityFallback()
}

@Composable
private fun EntityIconFallback(
    label: String,
    tier: Tier?,
    fallback: EntityFallback,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    when (fallback) {
        is EntityFallback.TierColored -> {
            val tint = tier?.color() ?: MaterialTheme.colorScheme.surfaceVariant
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = tint.copy(alpha = 0.30f),
                modifier = modifier.size(size),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (tier != null) tint else LocalContentColor.current,
                    )
                }
            }
        }
        is EntityFallback.MaterialIcon -> {
            androidx.compose.material3.Icon(
                imageVector = fallback.icon,
                contentDescription = label,
                modifier = modifier.size(size),
                tint = Color.Unspecified,
            )
        }
        EntityFallback.None -> {
            Box(modifier = modifier.size(size))
        }
    }
}
