package com.fantasyidler.ui.components.foundation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/**
 * Red-tinted card for irreversible actions — visually impossible to confuse with
 * a normal settings row. Wraps a [content] slot so each reset row keeps its own
 * layout while the surrounding chrome screams "stop and think."
 */
@Composable
fun DangerZone(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    Surface(
        shape    = tokens.shapes.card,
        color    = tokens.colors.error.copy(alpha = 0.10f),
        border   = BorderStroke(tokens.shapes.hairlineStroke, tokens.colors.error.copy(alpha = 0.55f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(tokens.spacing.l)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = tokens.colors.error,
                    modifier = Modifier.size(tokens.spacing.xl - tokens.spacing.xs),
                )
                Spacer(Modifier.width(tokens.spacing.m + tokens.spacing.xs))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = title,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color      = tokens.colors.error,
                    )
                    if (subtitle != null) {
                        Text(
                            text  = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = tokens.colors.onSurfaceMuted,
                        )
                    }
                }
            }
            Spacer(Modifier.height(tokens.spacing.m + tokens.spacing.s))
            content()
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewDangerZone() {
    FantasyPreviewSurface {
        DangerZone(title = "Reset progress", subtitle = "This can't be undone") {
            Text("…")
        }
    }
}
