package com.fantasyidler.ui.screen.minigame.smithing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.ui.components.EntityIcon
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/**
 * End-of-minigame summary. Three score chips, one "what you earned" line,
 * and a single Continue button that pops the screen.
 */
@Composable
fun SmithingResultOverlay(
    result: MinigameResult,
    hammerScore: Float,
    heatScore: Float,
    quenchScore: Float,
    onContinue: () -> Unit,
) {
    val tokens = LocalFantasyTokens.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape  = tokens.shapes.card,
            color  = tokens.colors.surface,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(tokens.spacing.m),
        ) {
            Column(
                modifier = Modifier.padding(tokens.spacing.l),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EntityIcon(
                    entityId = "smithy_sword_finished",
                    size = 96.dp,
                )
                Spacer(Modifier.height(tokens.spacing.s))
                Text(
                    text  = stringResource(R.string.sm_result_title),
                    style = tokens.typography.titleLarge,
                    color = tokens.colors.onSurface,
                )
                Spacer(Modifier.height(tokens.spacing.m))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ScoreChip(
                        labelRes = R.string.sm_result_score_hammer,
                        score    = hammerScore,
                        modifier = Modifier.weight(1f),
                    )
                    ScoreChip(
                        labelRes = R.string.sm_result_score_heat,
                        score    = heatScore,
                        modifier = Modifier.weight(1f),
                    )
                    ScoreChip(
                        labelRes = R.string.sm_result_score_quench,
                        score    = quenchScore,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(tokens.spacing.m))

                Text(
                    text  = result.summaryLine(),
                    style = tokens.typography.bodyMedium,
                    color = tokens.colors.onSurface,
                )

                Spacer(Modifier.height(tokens.spacing.l))

                ChunkyButton(
                    text    = stringResource(R.string.sm_result_continue),
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ScoreChip(
    labelRes: Int,
    score: Float,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFantasyTokens.current
    val (bandTextRes, color) = when {
        score >= 0.85f -> R.string.sm_result_perfect to tokens.colors.primary
        score >= 0.45f -> R.string.sm_result_good    to tokens.colors.secondary
        else            -> R.string.sm_result_miss    to tokens.colors.error
    }
    Surface(
        shape  = tokens.shapes.card,
        color  = color.copy(alpha = 0.15f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(tokens.spacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text  = stringResource(labelRes),
                style = tokens.typography.labelSmall,
                color = tokens.colors.onSurfaceMuted,
            )
            Text(
                text  = stringResource(bandTextRes),
                style = tokens.typography.bodyLarge,
                color = color,
            )
        }
    }
}

/**
 * Composes the result-card body line based on what reward path actually ran.
 * Pulled out so the screen can keep all i18n in one place.
 */
@Composable
private fun MinigameResult.summaryLine(): String = when (this) {
    is MinigameResult.SessionAccelerated ->
        stringResource(R.string.sm_result_relevant_skip, appliedMs / 1000)
    is MinigameResult.SessionAcceleratedNoBoost ->
        stringResource(R.string.sm_result_simple_skip, appliedMs / 1000)
    is MinigameResult.Fallback ->
        stringResource(R.string.sm_result_fallback_reward, xpAwarded)
    is MinigameResult.NoReward ->
        stringResource(R.string.sm_result_no_reward)
}
