package com.fantasyidler.ui.screen.minigame.smithing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import kotlin.math.abs

private const val CROSS_DURATION_MS = 1400
private const val GREEN_HALF_WINDOW = 0.15f      // ±15% of width around center
private const val PHASE_TIMEOUT_MS = 6_000L      // 3 ping-pongs ~ 4.2s, give a generous tail

/**
 * Sliding indicator inside a window with a centred green zone. The indicator
 * ping-pongs over [CROSS_DURATION_MS] per traversal. Player taps once; closer
 * to centre = higher score. Failsafe: auto-completes at 0 if no tap arrives
 * before the timeout.
 */
@Composable
fun QuenchPhase(
    onComplete: (Float) -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    val markerProgress = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }
    var lastTapPosition by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(Unit) {
        markerProgress.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = CROSS_DURATION_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        )
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(PHASE_TIMEOUT_MS)
        if (!done) {
            done = true
            onComplete(0f)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text  = stringResource(R.string.sm_phase_hint_quench),
            style = tokens.typography.bodyMedium,
            color = tokens.colors.onSurfaceMuted,
        )
        Spacer(Modifier.height(tokens.spacing.s))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(vertical = tokens.spacing.s),
        ) {
            val w = size.width
            val h = size.height
            val centerY = h / 2f

            // Track
            drawLine(
                color = tokens.colors.border,
                start = Offset(0f, centerY),
                end   = Offset(w, centerY),
                strokeWidth = 4.dp.toPx(),
            )
            // Green target zone
            val greenLeft  = w * (0.5f - GREEN_HALF_WINDOW)
            val greenWidth = w * GREEN_HALF_WINDOW * 2f
            drawRect(
                color = tokens.colors.primary.copy(alpha = 0.55f),
                topLeft = Offset(greenLeft, centerY - 12.dp.toPx()),
                size    = Size(greenWidth, 24.dp.toPx()),
            )

            // Marker (or final captured position if tapped)
            val mx = (lastTapPosition ?: markerProgress.value) * w
            val markerColor = if (lastTapPosition != null) tokens.colors.onPrimary
                              else tokens.colors.onSurface
            drawLine(
                color = markerColor,
                start = Offset(mx, 0f),
                end   = Offset(mx, h),
                strokeWidth = 5.dp.toPx(),
            )
        }

        Spacer(Modifier.height(tokens.spacing.s))

        ChunkyButton(
            text    = stringResource(R.string.sm_btn_quench),
            enabled = !done,
            onClick = {
                if (done) return@ChunkyButton
                val pos = markerProgress.value
                lastTapPosition = pos
                done = true
                val dist = abs(pos - 0.5f)
                val score = when {
                    dist <= GREEN_HALF_WINDOW -> 1.0f - (dist / GREEN_HALF_WINDOW) * 0.2f  // 1.0 → 0.8 inside
                    else                      -> {
                        // Outside green: linear falloff to 0 at the edges.
                        val falloff = (dist - GREEN_HALF_WINDOW) / (0.5f - GREEN_HALF_WINDOW)
                        (0.6f * (1f - falloff)).coerceAtLeast(0f)
                    }
                }
                onComplete(score)
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
