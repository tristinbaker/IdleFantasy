package com.fantasyidler.ui.screen.minigame

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import com.fantasyidler.R
import com.fantasyidler.ui.components.EntityIcon
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.ui.viewmodel.MarketCenterUiState
import com.fantasyidler.ui.viewmodel.MarketCenterViewModel
import kotlin.math.roundToInt

/**
 * Full-screen plaza. Owns the per-frame movement loop, lays out the
 * background + station + decor + avatar sprites by normalized coordinates,
 * and surfaces the joystick + back affordance + EnterPrompt.
 *
 * State + interaction events live in [MarketCenterViewModel]; this composable
 * is a pure renderer + input router.
 */
@Composable
fun MarketCenterStage(
    viewModel: MarketCenterViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFantasyTokens.current
    val state by viewModel.uiState.collectAsState()
    val joystick = remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(viewModel) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last).coerceAtLeast(0L)) / 1_000_000_000f
                last = now
                viewModel.onTick(joystick.value, dt)
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx  = with(LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }

        Image(
            painter            = painterResource(id = R.drawable.plaza_floor),
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            filterQuality      = FilterQuality.None,
            modifier           = Modifier.fillMaxSize(),
        )

        // Background-layer decor first so stations and the avatar draw on top.
        MarketLayout.decor.forEach { d ->
            PlacedSprite(
                entityId      = d.id,
                normalizedX   = d.x,
                normalizedY   = d.y,
                widthFraction = d.widthFraction,
                widthPx       = widthPx,
                heightPx      = heightPx,
            )
        }

        MarketLayout.stations.forEach { s ->
            PlacedSprite(
                entityId      = s.id,
                normalizedX   = s.x,
                normalizedY   = s.y,
                widthFraction = s.widthFraction,
                widthPx       = widthPx,
                heightPx      = heightPx,
            )
        }

        AvatarLayer(state = state, widthPx = widthPx, heightPx = heightPx)

        EnterPrompt(
            station = state.nearestStation,
            onEnter = viewModel::onEnterTapped,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = tokens.spacing.xl + tokens.spacing.xxl),
        )

        // Back affordance — the top HUD is hidden on this route.
        Surface(
            shape    = CircleShape,
            color    = tokens.colors.surface.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(tokens.spacing.m),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.mh_back),
                    tint               = tokens.colors.onSurface,
                )
            }
        }

        VirtualJoystick(
            onChange = { joystick.value = it },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = tokens.spacing.xl, bottom = tokens.spacing.xl),
        )
    }
}

@Composable
private fun PlacedSprite(
    entityId: String,
    normalizedX: Float,
    normalizedY: Float,
    widthFraction: Float,
    widthPx: Float,
    heightPx: Float,
) {
    val context    = LocalContext.current
    val density    = LocalDensity.current
    val drawableId = remember(entityId) {
        context.resources.getIdentifier(entityId, "drawable", context.packageName)
    }

    val spriteWidthPx = widthPx * widthFraction
    val widthDp       = with(density) { spriteWidthPx.toDp() }

    if (drawableId != 0) {
        val bitmap         = androidx.compose.ui.graphics.ImageBitmap.imageResource(id = drawableId)
        val aspect         = bitmap.height.toFloat() / bitmap.width.toFloat()
        val spriteHeightPx = spriteWidthPx * aspect
        val heightDp       = with(density) { spriteHeightPx.toDp() }
        val topLeftX       = widthPx  * normalizedX - spriteWidthPx  / 2f
        val topLeftY       = heightPx * normalizedY - spriteHeightPx / 2f

        Image(
            bitmap             = bitmap,
            contentDescription = entityId,
            filterQuality      = FilterQuality.None,
            modifier           = Modifier
                .offset { IntOffset(topLeftX.roundToInt(), topLeftY.roundToInt()) }
                .size(widthDp, heightDp),
        )
    } else {
        // Missing drawable: render the tier-colored square fallback.
        val topLeftX = widthPx  * normalizedX - spriteWidthPx / 2f
        val topLeftY = heightPx * normalizedY - spriteWidthPx / 2f
        Box(
            modifier = Modifier.offset { IntOffset(topLeftX.roundToInt(), topLeftY.roundToInt()) }
        ) {
            EntityIcon(entityId = entityId, size = widthDp)
        }
    }
}

@Composable
private fun AvatarLayer(state: MarketCenterUiState, widthPx: Float, heightPx: Float) {
    val density       = LocalDensity.current
    val spriteWidthPx = widthPx * MarketLayout.avatarWidthFraction
    val widthDp       = with(density) { spriteWidthPx.toDp() }

    val centerXPx = widthPx  * state.avatarX
    val centerYPx = heightPx * state.avatarY
    val topLeftX  = centerXPx - spriteWidthPx / 2f
    val topLeftY  = centerYPx - spriteWidthPx / 2f   // avatar sprites are square

    Box(
        modifier = Modifier.offset { IntOffset(topLeftX.roundToInt(), topLeftY.roundToInt()) }
    ) {
        WalkingAvatar(state = state, sizeDp = widthDp)
    }
}
