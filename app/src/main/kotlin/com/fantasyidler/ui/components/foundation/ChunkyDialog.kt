package com.fantasyidler.ui.components.foundation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.window.Dialog
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/**
 * Chunky dialog — replaces Material [androidx.compose.material3.AlertDialog].
 * Uses [com.fantasyidler.ui.theme.fantasy.FantasyShapes.card] +
 * `tokens.elevation.sheet` for the drop shadow.
 */
@Composable
fun ChunkyDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    body: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape  = tokens.shapes.card,
            color  = tokens.colors.surface,
            border = BorderStroke(tokens.shapes.hairlineStroke, tokens.colors.primary.copy(alpha = 0.45f)),
            modifier = modifier
                .fillMaxWidth()
                .shadow(elevation = tokens.elevation.sheet, shape = tokens.shapes.card),
        ) {
            Column(modifier = Modifier.padding(tokens.spacing.l)) {
                Text(
                    text       = title,
                    style      = tokens.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = tokens.colors.onSurface,
                )
                if (body != null) {
                    Spacer(Modifier.height(tokens.spacing.m))
                    body()
                }
                Spacer(Modifier.height(tokens.spacing.l))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.m, Alignment.End),
                ) {
                    actions()
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewChunkyDialog() {
    FantasyPreviewSurface {
        ChunkyDialog(
            title = "Reset progress?",
            onDismissRequest = {},
            body = { Text("This cannot be undone.") },
            actions = {
                ChunkyButton(
                    text = "Cancel",
                    onClick = {},
                    variant = ChunkyButtonVariant.Secondary,
                )
                ChunkyButton(
                    text = "Reset",
                    onClick = {},
                    variant = ChunkyButtonVariant.Destructive,
                )
            },
        )
    }
}
