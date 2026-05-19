package com.fantasyidler.ui.components.foundation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.offset
import com.fantasyidler.ui.motion.FantasyMotion
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/** Sized variants for [RewardBurst]. Pick by the loot's importance. */
sealed class RewardSize {
    /** 80ms scale pulse + haptic — for trivial pickups (a single ore tick). */
    object Micro : RewardSize()
    /** Floating "+N" with vertical drift + fade — for stack increments. */
    object Small : RewardSize()
    /** Bordered toast with bouncy entry — for non-trivial loot. */
    object Medium : RewardSize()
    /** Full-screen overlay with radial burst — for tier-up moments. */
    object Big : RewardSize()
}

/**
 * Tiered reward animation. Pass a [size] matching the loot's importance; the
 * primitive picks the matching choreography. Callers wrap one in a Box overlay
 * (Micro/Small/Medium) or full-screen Box (Big). All sizes auto-dismiss via
 * `onComplete` so screens stay declarative.
 */
@Composable
fun RewardBurst(
    label: String,
    visible: Boolean,
    size: RewardSize,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (size) {
        RewardSize.Micro  -> MicroBurst(label = label, visible = visible, onComplete = onComplete, modifier = modifier)
        RewardSize.Small  -> SmallBurst(label = label, visible = visible, onComplete = onComplete, modifier = modifier)
        RewardSize.Medium -> MediumBurst(label = label, visible = visible, onComplete = onComplete, modifier = modifier)
        RewardSize.Big    -> BigBurst(label = label, visible = visible, onComplete = onComplete, modifier = modifier)
    }
}

@Composable
private fun MicroBurst(label: String, visible: Boolean, onComplete: () -> Unit, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    LaunchedEffect(visible) {
        if (visible) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scale.animateTo(targetValue = 1.18f, animationSpec = tween(durationMillis = 80))
            scale.animateTo(targetValue = 1.0f,  animationSpec = tween(durationMillis = 80))
            onComplete()
        }
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Text(text = label, modifier = Modifier.scale(scale.value))
    }
}

@Composable
private fun SmallBurst(label: String, visible: Boolean, onComplete: () -> Unit, modifier: Modifier) {
    val tokens = LocalFantasyTokens.current
    var driftY by remember { mutableStateOf(0) }
    var localAlpha by remember { mutableStateOf(1f) }
    LaunchedEffect(visible) {
        if (visible) {
            driftY = 0
            localAlpha = 1f
            val frames = 24
            repeat(frames) { i ->
                driftY = -((i + 1) * 2)
                localAlpha = 1f - (i.toFloat() / frames)
                kotlinx.coroutines.delay(16)
            }
            onComplete()
        }
    }
    if (visible) {
        Text(
            text       = label,
            style      = tokens.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = tokens.colors.primary,
            modifier   = modifier
                .offset { IntOffset(x = 0, y = driftY) }
                .alpha(localAlpha),
        )
    }
}

@Composable
private fun MediumBurst(label: String, visible: Boolean, onComplete: () -> Unit, modifier: Modifier) {
    val tokens = LocalFantasyTokens.current
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(visible) {
        if (visible) {
            scale.snapTo(0.6f)
            scale.animateTo(targetValue = 1.0f, animationSpec = tokens.motion.bouncy)
            kotlinx.coroutines.delay(1_200)
            onComplete()
        }
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Surface(
            shape  = tokens.shapes.card,
            color  = tokens.colors.surface,
            border = BorderStroke(tokens.shapes.borderStroke, tokens.colors.primary),
            modifier = Modifier.scale(scale.value),
        ) {
            Text(
                text       = label,
                style      = tokens.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = tokens.colors.onSurface,
                modifier   = Modifier.padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m + tokens.spacing.xs),
            )
        }
    }
}

@Composable
private fun BigBurst(label: String, visible: Boolean, onComplete: () -> Unit, modifier: Modifier) {
    val tokens = LocalFantasyTokens.current
    val scale = remember { Animatable(0.4f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            scale.snapTo(0.4f)
            scale.animateTo(targetValue = 1.1f, animationSpec = tween(durationMillis = FantasyMotion.SMOOTH_MS))
            scale.animateTo(targetValue = 1.0f, animationSpec = tween(durationMillis = FantasyMotion.SNAPPY_MS))
            // 3 quick shakes that ease out.
            repeat(3) { i ->
                val amplitude = (3 - i) * 4
                shake.animateTo(targetValue = amplitude.toFloat(),  animationSpec = tween(40))
                shake.animateTo(targetValue = -amplitude.toFloat(), animationSpec = tween(40))
            }
            shake.animateTo(0f, animationSpec = tween(60))
            kotlinx.coroutines.delay(1_400)
            onComplete()
        }
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.colors.background.copy(alpha = 0.85f))
                .offset { IntOffset(x = shake.value.toInt(), y = 0) },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape  = tokens.shapes.card,
                color  = tokens.colors.primary,
                border = BorderStroke(tokens.shapes.borderStroke, tokens.colors.onPrimary),
                modifier = Modifier.scale(scale.value),
            ) {
                Text(
                    text       = label,
                    style      = tokens.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color      = tokens.colors.onPrimary,
                    modifier   = Modifier.padding(tokens.spacing.xl),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewRewardBurstMicro() {
    FantasyPreviewSurface {
        RewardBurst(label = "+1", visible = true, size = RewardSize.Micro, onComplete = {})
    }
}

@PreviewLightDark
@Composable
private fun PreviewRewardBurstSmall() {
    FantasyPreviewSurface {
        RewardBurst(label = "+15 XP", visible = true, size = RewardSize.Small, onComplete = {})
    }
}

@PreviewLightDark
@Composable
private fun PreviewRewardBurstMedium() {
    FantasyPreviewSurface {
        RewardBurst(label = "Mithril Sword!", visible = true, size = RewardSize.Medium, onComplete = {})
    }
}

@PreviewLightDark
@Composable
private fun PreviewRewardBurstBig() {
    FantasyPreviewSurface {
        Box(modifier = Modifier.fillMaxSize()) {
            RewardBurst(label = "LEVEL UP!", visible = true, size = RewardSize.Big, onComplete = {})
        }
    }
}
