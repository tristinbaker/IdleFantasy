package com.fantasyidler.ui.components.foundation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/**
 * Section divider used in Settings (and any future preferences-style surface).
 * An icon disk + chunky title — pulled out of SettingsScreen.kt so other
 * preference-style screens (e.g. future profile/options surfaces) can reuse the
 * same visual rhythm without copy-pasting the layout.
 */
@Composable
fun SettingsSectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFantasyTokens.current
    Row(
        modifier          = modifier.padding(top = tokens.spacing.m + tokens.spacing.s, bottom = tokens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconDisk(
            imageVector        = icon,
            contentDescription = null,
            size               = tokens.spacing.xxl,
        )
        Spacer(Modifier.width(tokens.spacing.m + tokens.spacing.s))
        Text(
            text       = title,
            style      = tokens.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = tokens.colors.onSurface,
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewSettingsSectionHeader() {
    FantasyPreviewSurface {
        SettingsSectionHeader(icon = Icons.Filled.Palette, title = "Appearance")
    }
}
