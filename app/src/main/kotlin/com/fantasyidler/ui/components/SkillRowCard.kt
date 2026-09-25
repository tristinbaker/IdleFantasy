package com.fantasyidler.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Reusable icon + 4-row info card: icon (left), header row, optional progress bar,
 * optional description row, optional action row. Rows that pass null simply don't
 * render, collapsing the layout rather than leaving blank space.
 */
@Composable
fun SkillRowCard(
    onClick: () -> Unit,
    icon: @Composable BoxScope.() -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    description: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(44.dp), content = icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            header()
            if (progress != null) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            if (description != null) {
                Spacer(Modifier.height(6.dp))
                description()
            }
            if (action != null) {
                Spacer(Modifier.height(if (description != null) 4.dp else 6.dp))
                action()
            }
        }
    }
}
