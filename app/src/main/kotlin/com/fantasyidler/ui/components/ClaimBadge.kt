package com.fantasyidler.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.theme.SuccessGreen

/**
 * Small attention-grabbing pill — "Ready", "Claim", "+5", a checkmark — anchored
 * to claimable surfaces (quests, harvested patches, completed sessions). When
 * [pulse] is true the badge oscillates its alpha between ~0.78 and 1.0 with a
 * 1.8s cycle, mirroring [FantasyMotion.Idle], so it feels alive without
 * being a flashing annoyance.
 *
 * Used on top of [IconDisk] (via Box overlay) or trailing inside a [ChunkyCard].
 */
@Composable
fun ClaimBadge(
    text: String,
    modifier: Modifier = Modifier,
    pulse: Boolean = true,
    color: Color = GoldPrimary,
    icon: ImageVector? = null,
) {
    val alpha = pulseAlpha(pulse)
    Surface(
        shape    = RoundedCornerShape(8.dp),
        color    = color,
        border   = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
        modifier = modifier.alpha(alpha),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(end = 3.dp),
                )
                Spacer(Modifier.width(2.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 11.sp,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

/**
 * Convenience: a green check stamp, e.g. for unlocked achievements.
 */
@Composable
fun ClaimStamp(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape    = RoundedCornerShape(8.dp),
        color    = SuccessGreen.copy(alpha = 0.20f),
        border   = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.6f)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.padding(end = 3.dp),
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = SuccessGreen,
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
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "claim-alpha",
    )
    return alphaState
}
