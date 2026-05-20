package com.fantasyidler.ui.screen.shop.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/**
 * One "aisle" in the medieval shop. Renders a category header above a
 * wood-grain panel whose contents are wrapped 2-per-row by the caller.
 *
 * Visual treatment:
 * - Brown secondary-container surface (matches the existing palette).
 * - Faint horizontal "shelf plank" strokes drawn over the surface so the panel
 *   reads as wood, no drawable required.
 */
@Composable
fun AisleSection(
    title: String,
    modifier: Modifier = Modifier,
    headerTrailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val tokens = LocalFantasyTokens.current

    Column(modifier = modifier) {
        // Header strip
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.s, vertical = tokens.spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text       = title,
                style      = tokens.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = tokens.colors.primary,
            )
            if (headerTrailing != null) headerTrailing()
        }

        // Aisle body — wood-grain panel
        Surface(
            shape    = RoundedCornerShape(tokens.spacing.m),
            color    = tokens.colors.secondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(tokens.spacing.m)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Decorative shelf-plank lines so the panel reads as wood.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val plankColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.12f)
                    val rowHeight = size.height / 4
                    repeat(3) { i ->
                        val y = rowHeight * (i + 1)
                        drawLine(
                            color       = plankColor,
                            start       = Offset(0f, y),
                            end         = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }
                Box(modifier = Modifier.padding(tokens.spacing.m)) {
                    content()
                }
            }
        }
    }
}
