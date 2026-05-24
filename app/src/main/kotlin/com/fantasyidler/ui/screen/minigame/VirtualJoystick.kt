package com.fantasyidler.ui.screen.minigame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.fantasyidler.ui.components.EntityIcon
import kotlin.math.roundToInt

/**
 * Bottom-left HUD joystick. Reports a normalized [Offset] in [-1, 1] × [-1, 1]
 * (x = east+, y = south+) via [onChange]. Knob position is owned here and
 * resets on release.
 */
@Composable
fun VirtualJoystick(
    onChange: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Int = 112,
) {
    val knobOffset   = remember { mutableStateOf(Offset.Zero) }
    val baseRadiusPx = with(LocalDensity.current) { (sizeDp / 2).dp.toPx() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(sizeDp.dp)
            .pointerInput(Unit) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val released = !change.pressed ||
                            event.type == PointerEventType.Release ||
                            event.type == PointerEventType.Exit
                        if (released) {
                            knobOffset.value = Offset.Zero
                            onChange(Offset.Zero)
                        } else {
                            val raw = Offset(change.position.x - centerX, change.position.y - centerY)
                            val mag = raw.getDistance()
                            val clamped = if (mag <= baseRadiusPx || mag == 0f) raw
                                          else raw * (baseRadiusPx / mag)
                            knobOffset.value = clamped
                            onChange(Offset(clamped.x / baseRadiusPx, clamped.y / baseRadiusPx))
                            change.consume()
                        }
                    }
                }
            },
    ) {
        EntityIcon(entityId = "joystick_base", size = sizeDp.dp)
        EntityIcon(
            entityId = "joystick_knob",
            size     = (sizeDp / 2).dp,
            modifier = Modifier.offset {
                IntOffset(knobOffset.value.x.roundToInt(), knobOffset.value.y.roundToInt())
            },
        )
    }
}
