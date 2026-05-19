package com.fantasyidler.ui.components.foundation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.ui.motion.FantasyMotion
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/**
 * Small attention-grabbing pill — "Ready", "Claim", "+5", a checkmark — anchored
 * to claimable surfaces. When [pulse] is true, alpha oscillates with
 * `FantasyMotion.IDLE_MS` cadence so it feels alive without flashing.
 */
@Composable
fun ClaimBadge(
    text: String,
    modifier: Modifier = Modifier,
    pulse: Boolean = true,
    color: Color = LocalFantasyTokens.current.colors.primary,
    icon: ImageVector? = null,
) {
    val tokens = LocalFantasyTokens.current
    val alpha = pulseAlpha(pulse)
    Surface(
        shape    = tokens.shapes.chip,
        color    = color,
        border   = BorderStroke(tokens.shapes.hairlineStroke, color.copy(alpha = 0.6f)),
        modifier = modifier.alpha(alpha),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s - tokens.spacing.xs / 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tokens.colors.onPrimary,
                    modifier = Modifier.padding(end = tokens.spacing.xs),
                )
                Spacer(Modifier.width(tokens.spacing.xs))
            }
            Text(
                text       = text,
                style      = tokens.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color      = tokens.colors.onPrimary,
            )
        }
    }
}

/** Static success stamp for unlocked achievements. */
@Composable
fun ClaimStamp(
    text: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFantasyTokens.current
    Surface(
        shape    = tokens.shapes.chip,
        color    = tokens.colors.success.copy(alpha = 0.20f),
        border   = BorderStroke(tokens.shapes.hairlineStroke, tokens.colors.success.copy(alpha = 0.6f)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s - tokens.spacing.xs / 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = tokens.colors.success,
                modifier = Modifier.padding(end = tokens.spacing.xs),
            )
            Spacer(Modifier.width(tokens.spacing.xs))
            Text(
                text       = text,
                style      = tokens.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color      = tokens.colors.success,
            )
        }
    }
}

@Composable
private fun pulseAlpha(pulse: Boolean): Float {
    if (!pulse) return 1f
    val transition = rememberInfiniteTransition(label = "claim-pulse")
    val alphaState by transition.animateFloat(
        initialValue = 0.78f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = FantasyMotion.IDLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "claim-alpha",
    )
    return alphaState
}

@PreviewLightDark
@Composable
private fun PreviewClaimBadge() {
    FantasyPreviewSurface {
        ClaimBadge(text = "Claim", pulse = false)
    }
}
