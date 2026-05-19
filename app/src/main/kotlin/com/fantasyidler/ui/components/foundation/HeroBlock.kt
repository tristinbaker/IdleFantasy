package com.fantasyidler.ui.components.foundation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/**
 * Wide "hero" section header — full-width gold-tinted gradient backdrop, leading
 * icon-disk + title + subtitle + optional trailing action, plus optional content
 * slot beneath the title row (XP bars, chips, etc.). Used by Profile, Settings,
 * Combat active-session, Farming.
 */
@Composable
fun HeroBlock(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
    accentColor: Color = LocalFantasyTokens.current.colors.primary,
) {
    val tokens = LocalFantasyTokens.current
    val heroShape = RoundedCornerShape(tokens.spacing.xl - tokens.spacing.s)
    Surface(
        shape    = heroShape,
        color    = tokens.colors.surfaceVariant,
        border   = BorderStroke(tokens.shapes.hairlineStroke, accentColor.copy(alpha = 0.35f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(heroShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.14f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(tokens.spacing.l),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(tokens.spacing.l - tokens.spacing.xs))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = title,
                        style      = tokens.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color      = tokens.colors.onSurface,
                    )
                    if (subtitle != null) {
                        Spacer(Modifier.height(tokens.spacing.xs))
                        Text(
                            text  = subtitle,
                            style = tokens.typography.bodyMedium,
                            color = tokens.colors.onSurfaceMuted,
                        )
                    }
                }
                if (trailing != null) {
                    Spacer(Modifier.width(tokens.spacing.m + tokens.spacing.s))
                    trailing()
                }
            }
            if (content != null) {
                Spacer(Modifier.height(tokens.spacing.m + tokens.spacing.s))
                content()
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewHeroBlock() {
    FantasyPreviewSurface {
        HeroBlock(
            title = "Farming",
            subtitle = "3 patches ready to harvest",
            leading = { IconDisk(emoji = "🌱") },
        )
    }
}
