package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.json.TradeRouteData
import com.fantasyidler.util.GameStrings
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.MercantileUiState
import com.fantasyidler.ui.viewmodel.MercantileViewModel
import com.fantasyidler.ui.viewmodel.xpProgressFraction
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MercantileScreen(
    onBack: () -> Unit = {},
    viewModel: MercantileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    ToastMessageEffect(state.snackbarMessage, viewModel::snackbarConsumed)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.skill_mercantile)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                MercantileStatsHeader(state)
            }
            item {
                Text(
                    text     = stringResource(R.string.mercantile_routes_label),
                    style    = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            items(state.tradeRoutes, key = { it.id }) { route ->
                TradeRouteRow(
                    route         = route,
                    playerCoins   = state.coins,
                    isStarting    = state.startingSession,
                    sessionActive = state.anySessionActive,
                    queueFull     = state.queueSize >= state.maxQueueSize,
                    onStart       = { viewModel.startTradeRoute(route.id) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Sheet-mode entry point (used when shown inside a ModalBottomSheet)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MercantileSheetContent(
    onDismiss: () -> Unit = {},
    viewModel: MercantileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    ToastMessageEffect(state.snackbarMessage, viewModel::snackbarConsumed)

    if (state.isLoading) {
        Box(
            Modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        return
    }

    Box(Modifier.fillMaxWidth()) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item { MercantileStatsHeader(state) }
            item {
                Text(
                    text       = stringResource(R.string.mercantile_routes_label),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            items(state.tradeRoutes, key = { it.id }) { route ->
                TradeRouteRow(
                    route         = route,
                    playerCoins   = state.coins,
                    isStarting    = state.startingSession,
                    sessionActive = state.anySessionActive,
                    queueFull     = state.queueSize >= state.maxQueueSize,
                    onStart       = { viewModel.startTradeRoute(route.id) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun MercantileStatsHeader(state: MercantileUiState) {
    Surface(
        color    = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text  = stringResource(R.string.mercantile_level_label, state.mercantileLevel),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = stringResource(R.string.mercantile_xp_label, state.mercantileXp.formatXp()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = stringResource(R.string.mercantile_coins_label, state.coins.formatCoins()),
                style = MaterialTheme.typography.bodySmall,
                color = GoldPrimary,
            )
        }
    }
}

@Composable
private fun TradeRouteRow(
    route: TradeRouteData,
    playerCoins: Long,
    isStarting: Boolean,
    sessionActive: Boolean,
    queueFull: Boolean,
    onStart: () -> Unit,
) {
    val context = LocalContext.current
    val canAfford = playerCoins >= route.coinCost
    val costStr   = route.coinCost.toLong().formatCoins()
    val minReturn = route.coinRanges.values.minOf { it.min } * 60L
    val maxReturn = route.coinRanges.values.maxOf { it.max } * 60L

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text       = GameStrings.tradeRouteName(context, route.id, route.displayName),
            style      = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text  = GameStrings.tradeRouteDesc(context, route.id, route.description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text  = stringResource(R.string.mercantile_cost_label, costStr),
                style = MaterialTheme.typography.labelSmall,
                color = if (canAfford) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text  = stringResource(R.string.mercantile_return_range, minReturn.formatCoins(), maxReturn.formatCoins()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick  = onStart,
            enabled  = !isStarting && canAfford && (!sessionActive || !queueFull),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (sessionActive && !queueFull) stringResource(R.string.mercantile_queue_label)
                else stringResource(R.string.mercantile_dispatch_label)
            )
        }
    }
}
