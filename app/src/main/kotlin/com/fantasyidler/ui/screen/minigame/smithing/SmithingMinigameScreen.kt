package com.fantasyidler.ui.screen.minigame.smithing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.ui.scene.SceneCatalog
import com.fantasyidler.ui.scene.Stage
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.ui.viewmodel.SmithingMinigameViewModel

/**
 * Phase 3 of the minigame-hub overhaul. Hosts the HAMMER → HEAT → QUENCH
 * sequence on top of a Stage rendered from [SceneCatalog.smithy].
 *
 * The screen is a sub-route (no top HUD), so the back arrow in the top-left
 * is the only chrome. Tapping it any time before [SmithingPhase.COMPLETE]
 * pops the back stack without applying any rewards.
 */
@Composable
fun SmithingMinigameScreen(
    onBack: () -> Unit = {},
    viewModel: SmithingMinigameViewModel = hiltViewModel(),
) {
    val tokens = LocalFantasyTokens.current
    val state: SmithingUiState by viewModel.uiState.collectAsState()

    // SceneConfig rebuilt on phase/sprite changes — the sword's entityId is
    // the only thing that changes, and Stage's mutable state (active effects,
    // event bus subscription) persists because the composable identity holds.
    val sceneConfig = remember(state.swordEntityId) {
        SceneCatalog.smithy(swordEntityId = state.swordEntityId)
    }

    Box(modifier = Modifier.fillMaxSize().background(tokens.colors.background)) {

        Column(modifier = Modifier.fillMaxSize()) {

            // Top bar — back arrow + title chip.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.s),
            ) {
                Surface(
                    shape = CircleShape,
                    color = tokens.colors.surfaceVariant,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.sm_close),
                            tint = tokens.colors.onSurfaceMuted,
                        )
                    }
                }
                Text(
                    text  = stringResource(R.string.sm_title),
                    style = tokens.typography.titleLarge,
                    color = tokens.colors.onSurface,
                )
                Spacer(Modifier.weight(1f))
                PhaseBadge(phase = state.phase)
            }

            // Stage takes the upper half — anvil + sword + hammer + forge + bucket.
            Stage(
                config = sceneConfig,
                bus    = viewModel.sceneBus,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            // Phase widget pinned to the bottom — recomposes through AnimatedContent.
            Surface(
                color = tokens.colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(vertical = tokens.spacing.m)) {
                    AnimatedContent(
                        targetState = state.phase,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "smithing_phase",
                    ) { phase ->
                        when (phase) {
                            SmithingPhase.HAMMER -> HammerPhase(
                                onTap      = { tap -> viewModel.onHammerTap(tap.classification) },
                                onComplete = viewModel::onHammerComplete,
                            )
                            SmithingPhase.HEAT -> HeatPhase(
                                onComplete = viewModel::onHeatComplete,
                            )
                            SmithingPhase.QUENCH -> QuenchPhase(
                                onComplete = viewModel::onQuenchComplete,
                            )
                            SmithingPhase.COMPLETE -> {
                                // Result is rendered as an overlay; bottom slot stays empty.
                                Spacer(Modifier.height(tokens.spacing.l))
                            }
                        }
                    }
                }
            }
        }

        // Result overlay sits on top of everything when the run is complete.
        val result = state.result
        if (state.phase == SmithingPhase.COMPLETE && result != null) {
            SmithingResultOverlay(
                result      = result,
                hammerScore = state.hammerScore,
                heatScore   = state.heatScore,
                quenchScore = state.quenchScore,
                onContinue  = onBack,
            )
        }
    }
}

@Composable
private fun PhaseBadge(phase: SmithingPhase) {
    val tokens = LocalFantasyTokens.current
    val (labelRes, active) = when (phase) {
        SmithingPhase.HAMMER  -> R.string.sm_phase_hammer to true
        SmithingPhase.HEAT    -> R.string.sm_phase_heat   to true
        SmithingPhase.QUENCH  -> R.string.sm_phase_quench to true
        SmithingPhase.COMPLETE -> R.string.sm_result_title to false
    }
    Surface(
        shape = tokens.shapes.card,
        color = if (active) tokens.colors.primary.copy(alpha = 0.18f)
                else tokens.colors.surfaceVariant,
    ) {
        Text(
            text  = stringResource(labelRes),
            style = tokens.typography.bodyMedium,
            color = if (active) tokens.colors.primary else tokens.colors.onSurfaceMuted,
            modifier = Modifier.padding(horizontal = tokens.spacing.m, vertical = tokens.spacing.xs),
        )
    }
}
