package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyDialog
import com.fantasyidler.ui.screen.minigame.MarketCenterStage
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.ui.viewmodel.MarketCenterUiState
import com.fantasyidler.ui.viewmodel.MarketCenterViewModel
import com.fantasyidler.ui.viewmodel.MarketNavEvent

/**
 * Phase 1 of the minigame-hub overhaul. Hosts the walk-around plaza
 * ([MarketCenterStage]) and routes per-station nav events: the vendor stall
 * opens [ShopScreen] via [onOpenShop]; crafting stations show a
 * Coming-Soon dialog (Phase 3 wires the first real minigame).
 */
@Composable
fun MinigameHubScreen(
    onBack: () -> Unit = {},
    onOpenShop: () -> Unit = {},
    viewModel: MarketCenterViewModel = hiltViewModel(),
) {
    val tokens = LocalFantasyTokens.current
    val state: MarketCenterUiState by viewModel.uiState.collectAsState()

    LaunchedEffect(state.navEvent) {
        when (state.navEvent) {
            MarketNavEvent.OpenShop -> {
                viewModel.consumeNavEvent()
                onOpenShop()
            }
            else -> Unit   // ComingSoon shown as a dialog below; null = no-op
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MarketCenterStage(viewModel = viewModel, onBack = onBack)

        val pending = state.navEvent
        if (pending is MarketNavEvent.ComingSoon) {
            ChunkyDialog(
                title            = stringResource(pending.displayNameRes),
                onDismissRequest = viewModel::consumeNavEvent,
                body = {
                    Text(
                        text  = stringResource(R.string.mh_coming_soon_body),
                        style = tokens.typography.bodyMedium,
                        color = tokens.colors.onSurface,
                    )
                },
                actions = {
                    ChunkyButton(
                        text    = stringResource(R.string.mh_coming_soon_dismiss),
                        onClick = viewModel::consumeNavEvent,
                    )
                },
            )
        }
    }
}
