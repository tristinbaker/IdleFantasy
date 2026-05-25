package com.fantasyidler.ui.screen.minigame.smithing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val FILL_DURATION_MS = 1700                  // 0.0 → 1.0
private const val OVERSHOOT_DURATION_MS = 700              // 1.0 → 1.3 (burned)
private const val TARGET_BAND_CENTER = 0.55f
private const val TARGET_BAND_HALF_WIDTH = 0.13f           // ±13% — about 25% wide

/**
 * Tap-and-hold gauge. Pressing the bar starts a fill animation; releasing
 * captures the value and scores it: perfect inside the target band, falling
 * off linearly to either side. Overshooting past 1.0 → score floor of 0.0
 * (burned). [onComplete] fires once.
 */
@Composable
fun HeatPhase(
    onComplete: (Float) -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    val temp = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var animJob by remember { mutableStateOf<Job?>(null) }

    val bandLow  = TARGET_BAND_CENTER - TARGET_BAND_HALF_WIDTH
    val bandHigh = TARGET_BAND_CENTER + TARGET_BAND_HALF_WIDTH

    fun finishWith(value: Float) {
        if (done) return
        done = true
        animJob?.cancel()
        val score = when {
            value in bandLow..bandHigh -> 1.0f
            value > 1.0f               -> 0.0f                   // overshoot / burned
            value < bandLow            -> (value / bandLow).coerceIn(0f, 0.5f)
            else                       -> {
                val over = (value - bandHigh) / (1f - bandHigh)
                (1f - over).coerceIn(0f, 0.9f)
            }
        }
        onComplete(score)
    }

    // Failsafe: 6 seconds of nothing → score 0.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(6_000L)
        if (!done && !holding) finishWith(0f)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = tokens.spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text  = stringResource(R.string.sm_phase_hint_heat),
            style = tokens.typography.bodyMedium,
            color = tokens.colors.onSurfaceMuted,
        )
        Spacer(Modifier.height(tokens.spacing.s))

        // The thermometer.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(vertical = tokens.spacing.xs),
        ) {
            val w = size.width
            val h = size.height
            // Track
            drawRect(
                color = tokens.colors.surfaceVariant,
                topLeft = Offset(0f, 0f),
                size    = Size(w, h),
            )
            // Target band
            val bandLeft  = w * bandLow
            val bandRight = w * bandHigh
            drawRect(
                color = tokens.colors.primary.copy(alpha = 0.35f),
                topLeft = Offset(bandLeft, 0f),
                size    = Size(bandRight - bandLeft, h),
            )
            // Fill (clamped visually at 1.0)
            val fillWidth = (temp.value.coerceAtMost(1.3f) / 1.3f) * w
            val fillColor = when {
                temp.value > 1.0f                  -> tokens.colors.error
                temp.value in bandLow..bandHigh    -> tokens.colors.primary
                else                               -> tokens.colors.secondary
            }
            drawRect(
                color = fillColor,
                topLeft = Offset(0f, 0f),
                size    = Size(fillWidth, h),
            )
        }

        Spacer(Modifier.height(tokens.spacing.s))

        // Press-and-hold button. detectTapGestures gives us onPress→tryAwaitRelease.
        val pressLabel = stringResource(R.string.sm_btn_hold_heat)
        Surface(
            shape  = tokens.shapes.button,
            color  = if (holding) tokens.colors.primary else tokens.colors.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.m)
                .pointerInput(done) {
                    if (done) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            holding = true
                            // Start fill animation
                            animJob = scope.launch {
                                temp.snapTo(0f)
                                temp.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = FILL_DURATION_MS),
                                )
                                // Overshoot into burned territory if still held.
                                temp.animateTo(
                                    targetValue = 1.3f,
                                    animationSpec = tween(durationMillis = OVERSHOOT_DURATION_MS),
                                )
                                if (!done) finishWith(temp.value)
                            }
                            tryAwaitRelease()
                            holding = false
                            if (!done) finishWith(temp.value)
                        },
                    )
                },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(tokens.spacing.m)) {
                Text(
                    text  = pressLabel,
                    style = tokens.typography.titleLarge,
                    color = if (holding) tokens.colors.onPrimary else tokens.colors.onSurface,
                )
            }
        }
    }
}
