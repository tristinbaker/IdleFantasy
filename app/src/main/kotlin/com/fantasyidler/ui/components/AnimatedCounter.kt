package com.fantasyidler.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.fantasyidler.ui.motion.FantasyMotion

/**
 * A Text that animates between values rather than hard-replacing them. Used for coin
 * counters, session countdowns, XP gains — anything where seeing the number *change*
 * is part of the satisfaction.
 *
 * Direction: when the new value is greater the digits slide up from below (gain);
 * when smaller, they slide down from above (loss).
 */
@Composable
fun AnimatedCounter(
    value: Long,
    modifier: Modifier = Modifier,
    format: (Long) -> String = { it.toString() },
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val goingUp = targetState > initialState
            val intoSlide   = if (goingUp) { h: Int ->  h } else { h: Int -> -h }
            val outOfSlide  = if (goingUp) { h: Int -> -h } else { h: Int ->  h }
            (slideInVertically(animationSpec = FantasyMotion.smooth(), initialOffsetY = intoSlide) +
                fadeIn(animationSpec = FantasyMotion.Smooth))
                .togetherWith(
                    slideOutVertically(animationSpec = FantasyMotion.smooth(), targetOffsetY = outOfSlide) +
                        fadeOut(animationSpec = FantasyMotion.Smooth)
                )
        },
        label = "AnimatedCounter",
        modifier = modifier,
    ) { current ->
        Text(
            text = format(current),
            style = style,
            fontWeight = fontWeight,
            color = color,
        )
    }
}
