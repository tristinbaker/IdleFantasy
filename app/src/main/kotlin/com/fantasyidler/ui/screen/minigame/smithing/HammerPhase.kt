package com.fantasyidler.ui.screen.minigame.smithing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import kotlin.math.abs

private const val TOTAL_BEATS = 4
private const val SWEEP_DURATION_MS = 1200
private const val PERFECT_HALF_WINDOW = 0.06f   // ±6% of width around center
private const val GOOD_HALF_WINDOW    = 0.16f   // ±16% of width around center

/** Result the screen pipes into the SceneEventBus on each tap. */
data class HammerTap(val classification: TapResult)

enum class TapResult { Perfect, Good, Miss }

/**
 * "Tap on the beat" widget. A 4dp marker sweeps left → right and back on a
 * 1200ms loop. The player has [TOTAL_BEATS] taps to score; the closer to the
 * centre target, the higher the per-tap score. Phase ends after the fourth
 * tap and reports the averaged score via [onComplete].
 */
@Composable
fun HammerPhase(
    onTap: (HammerTap) -> Unit,
    onComplete: (Float) -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    val markerProgress = remember { Animatable(0f) }
    val tapScores = remember { mutableStateListOf<Float>() }
    val classifications = remember { mutableStateListOf<TapResult>() }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Ping-pong sweep using a single Animatable in a repeating tween.
        markerProgress.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = SWEEP_DURATION_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        )
    }

    // Failsafe: 8 seconds without 4 taps → auto-complete with whatever we have.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(8_000L)
        if (!done && tapScores.size < TOTAL_BEATS) {
            done = true
            val pad = List(TOTAL_BEATS - tapScores.size) { 0f }
            onComplete((tapScores + pad).average().toFloat())
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text  = stringResource(R.string.sm_phase_hint_hammer),
            style = tokens.typography.bodyMedium,
            color = tokens.colors.onSurfaceMuted,
        )
        Spacer(Modifier.height(tokens.spacing.s))

        // Beat readout — a row of pips showing the four beat slots filled in.
        Row(
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
            modifier = Modifier.padding(vertical = tokens.spacing.xs),
        ) {
            repeat(TOTAL_BEATS) { i ->
                val color = when (classifications.getOrNull(i)) {
                    TapResult.Perfect -> tokens.colors.primary
                    TapResult.Good    -> tokens.colors.secondary
                    TapResult.Miss    -> tokens.colors.error
                    null              -> tokens.colors.border
                }
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(14.dp)
                        .padding(2.dp),
                ) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
                        drawCircle(color = color, radius = 5.dp.toPx())
                    }
                }
            }
        }

        // The sweep meter.
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

            // Target band (perfect zone)
            val perfectLeft = w * (0.5f - PERFECT_HALF_WINDOW)
            val perfectWidth = w * PERFECT_HALF_WINDOW * 2f
            drawRect(
                color = tokens.colors.primary,
                topLeft = Offset(perfectLeft, centerY - 10.dp.toPx()),
                size    = Size(perfectWidth, 20.dp.toPx()),
            )
            // Good zone (lighter)
            val goodLeft = w * (0.5f - GOOD_HALF_WINDOW)
            val goodWidth = w * GOOD_HALF_WINDOW * 2f
            drawRect(
                color = tokens.colors.secondary.copy(alpha = 0.35f),
                topLeft = Offset(goodLeft, centerY - 6.dp.toPx()),
                size    = Size(goodWidth, 12.dp.toPx()),
                style   = Stroke(width = 1.5.dp.toPx()),
            )

            // Marker
            val mx = w * markerProgress.value
            drawLine(
                color = tokens.colors.onSurface,
                start = Offset(mx, 0f),
                end   = Offset(mx, h),
                strokeWidth = 4.dp.toPx(),
            )
        }

        Spacer(Modifier.height(tokens.spacing.s))

        ChunkyButton(
            text    = stringResource(R.string.sm_btn_hammer),
            enabled = !done,
            onClick = {
                if (done) return@ChunkyButton
                val dist = abs(markerProgress.value - 0.5f)
                val (cls, score) = when {
                    dist <= PERFECT_HALF_WINDOW -> TapResult.Perfect to 1.0f
                    dist <= GOOD_HALF_WINDOW    -> TapResult.Good    to 0.6f
                    else                        -> TapResult.Miss   to 0.0f
                }
                tapScores += score
                classifications += cls
                onTap(HammerTap(cls))

                if (tapScores.size >= TOTAL_BEATS) {
                    done = true
                    onComplete(tapScores.average().toFloat())
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
