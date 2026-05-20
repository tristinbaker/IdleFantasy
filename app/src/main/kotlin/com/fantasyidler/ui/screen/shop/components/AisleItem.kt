package com.fantasyidler.ui.screen.shop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fantasyidler.ui.components.EntityIcon
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/**
 * One tile inside an [AisleSection]. The icon sits on a shelf-plank rectangle;
 * a chip below it shows the price tag. Sale items wear a darker pouch-tinted
 * background and show their pre-discount price with a strikethrough beside the
 * sale price.
 */
@Composable
fun AisleItem(
    entityId: String,
    displayName: String,
    price: Int,
    modifier: Modifier = Modifier,
    originalPrice: Int? = null,
    isOnSale: Boolean = false,
    percentOff: Int = 0,
    dim: Boolean = false,
    emojiFallback: String? = null,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val tokens = LocalFantasyTokens.current

    val tileBackground = if (isOnSale) {
        tokens.colors.secondary.copy(alpha = 0.55f)   // leather-pouch tint
    } else {
        tokens.colors.surface.copy(alpha = 0.40f)     // shelf-plank tint
    }
    val borderShape = RoundedCornerShape(tokens.spacing.s)

    Column(
        modifier            = modifier
            .clip(borderShape)
            .background(tileBackground)
            .clickable(onClick = onClick)
            .padding(vertical = tokens.spacing.s, horizontal = tokens.spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            EntityIcon(
                entityId      = entityId,
                label         = displayName,
                size          = 48.dp,
                emojiFallback = emojiFallback,
            )
            if (isOnSale) {
                SalePercentBadge(
                    percentOff = percentOff,
                    modifier   = Modifier.align(Alignment.TopEnd),
                )
            }
        }
        Spacer(Modifier.height(tokens.spacing.xs))
        Text(
            text       = displayName,
            style      = tokens.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color      = if (dim) tokens.colors.onSurfaceMuted else tokens.colors.onSurface,
            textAlign  = TextAlign.Center,
            maxLines   = 1,
        )
        if (badge != null) {
            Spacer(Modifier.height(tokens.spacing.xs))
            Text(
                text  = badge,
                style = tokens.typography.labelSmall,
                color = tokens.colors.warning,
            )
        }
        Spacer(Modifier.height(tokens.spacing.xs))
        PriceTag(price = price, originalPrice = originalPrice?.takeIf { isOnSale }, dim = dim)
    }
}

@Composable
private fun PriceTag(price: Int, originalPrice: Int?, dim: Boolean) {
    val tokens = LocalFantasyTokens.current
    Surface(
        shape = tokens.shapes.chip,
        color = tokens.colors.primary.copy(alpha = if (dim) 0.10f else 0.22f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
            modifier = Modifier.padding(horizontal = tokens.spacing.s, vertical = tokens.spacing.xs),
        ) {
            if (originalPrice != null) {
                Text(
                    text       = "$originalPrice",
                    style      = tokens.typography.labelSmall,
                    color      = tokens.colors.onSurfaceMuted,
                    textDecoration = TextDecoration.LineThrough,
                )
            }
            Text(
                text       = "$price",
                style      = tokens.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color      = if (dim) tokens.colors.primary.copy(alpha = 0.5f) else tokens.colors.primary,
            )
        }
    }
}

@Composable
private fun SalePercentBadge(percentOff: Int, modifier: Modifier = Modifier) {
    val tokens = LocalFantasyTokens.current
    Surface(
        shape    = tokens.shapes.chip,
        color    = tokens.colors.warning,
        modifier = modifier.size(width = 34.dp, height = 18.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            Text(
                text       = "-$percentOff%",
                style      = tokens.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color      = tokens.colors.onPrimary,
            )
        }
    }
}
