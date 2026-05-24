package com.fantasyidler.ui.screen.minigame

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.ui.motion.FantasyMotion
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/**
 * "Tap to enter <Station>" pill that fades in when the avatar is inside a
 * station's interact radius. Click delegates to [onEnter] — the parent wires
 * this to the ViewModel.
 */
@Composable
fun EnterPrompt(
    station: Station?,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFantasyTokens.current

    // Cache the last non-null station so the exit fade still has a label to
    // render once `station` flips back to null.
    var lastStation: Station? by remember { mutableStateOf(null) }
    if (station != null) lastStation = station
    val shown = station ?: lastStation

    AnimatedVisibility(
        visible  = station != null,
        enter    = fadeIn(FantasyMotion.smooth()),
        exit     = fadeOut(FantasyMotion.smooth()),
        modifier = modifier,
    ) {
        if (shown != null) {
            Surface(
                shape  = RoundedCornerShape(12.dp),
                color  = tokens.colors.surface,
                border = BorderStroke(tokens.shapes.hairlineStroke, tokens.colors.primary),
                modifier = Modifier
                    .shadow(elevation = tokens.elevation.sheet, shape = RoundedCornerShape(12.dp))
                    .clickable(onClick = onEnter),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier.padding(
                        horizontal = tokens.spacing.l,
                        vertical   = tokens.spacing.m,
                    ),
                ) {
                    Text(
                        text       = stringResource(shown.displayNameRes),
                        style      = tokens.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color      = tokens.colors.onSurface,
                        textAlign  = TextAlign.Center,
                    )
                    Text(
                        text      = stringResource(R.string.mh_enter_prompt),
                        style     = tokens.typography.labelSmall,
                        color     = tokens.colors.primary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
