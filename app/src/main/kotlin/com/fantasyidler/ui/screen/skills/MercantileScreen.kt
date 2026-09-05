package com.fantasyidler.ui.screen.skills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.fantasyidler.data.model.Skills
import com.fantasyidler.simulator.MercantilePerks
import com.fantasyidler.ui.screen.AppBannerCenter
import com.fantasyidler.ui.screen.AppBannerEffect
import com.fantasyidler.ui.viewmodel.MercantileUiState
import com.fantasyidler.ui.viewmodel.MercantileViewModel
import com.fantasyidler.ui.viewmodel.QuestIndicator
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp

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

    AppBannerEffect(state.snackbarMessage, state.snackbarNonce, viewModel::snackbarConsumed)

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
                val questIndicators = state.activeQuests["${Skills.MERCANTILE}:${route.id}"] ?: emptyList()
                TradeRouteRow(
                    route           = route,
                    playerCoins     = state.coins,
                    coinReturnMult  = state.coinReturnMult,
                    isStarting      = state.startingSession,
                    sessionActive   = state.anySessionActive,
                    queueFull       = state.queueSize >= state.maxQueueSize,
                    questIndicators = questIndicators,
                    onStart         = { viewModel.startTradeRoute(route.id) },
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
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text       = stringResource(R.string.mercantile_perks_title),
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            val pct = MercantilePerks.tradePct(state.mercantileLevel)
            val next = MercantilePerks.nextTierLevel(state.mercantileLevel)
            Text(
                text  = if (pct == 0)
                    stringResource(R.string.mercantile_perk_trading_locked, MercantilePerks.TIER_LEVELS.first())
                else listOfNotNull(
                    stringResource(R.string.mercantile_perk_trading, pct, pct),
                    next?.let { stringResource(R.string.mercantile_perk_trading_next, it) },
                ).joinToString(" "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.guildUnlockLevel > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = stringResource(R.string.mercantile_perk_guild, state.guildUnlockLevel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TradeRouteRow(
    route: TradeRouteData,
    playerCoins: Long,
    coinReturnMult: Float,
    isStarting: Boolean,
    sessionActive: Boolean,
    queueFull: Boolean,
    questIndicators: List<QuestIndicator> = emptyList(),
    onStart: () -> Unit,
) {
    val context = LocalContext.current
    val canAfford = playerCoins >= route.coinCost
    val costStr   = route.coinCost.toLong().formatCoins()
    val minReturn = (route.coinRanges.values.minOf { it.min } * 60L * coinReturnMult.toDouble()).toLong()
    val maxReturn = (route.coinRanges.values.maxOf { it.max } * 60L * coinReturnMult.toDouble()).toLong()
    val queueFullMessage = stringResource(R.string.snackbar_queue_full)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text       = GameStrings.tradeRouteName(context, route.id, route.displayName),
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.weight(1f, fill = false),
            )
            if (questIndicators.isNotEmpty()) {
                QuestIndicatorIcons(questIndicators)
            }
        }
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Button(
                onClick = onStart,
                enabled = !isStarting && canAfford && (!sessionActive || !queueFull),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (sessionActive && !queueFull) stringResource(R.string.mercantile_queue_label)
                    else stringResource(R.string.mercantile_dispatch_label)
                )
            }
            // Only intercept taps when the button is disabled BECAUSE the queue is full;
            // with no active session the queue state must not block dispatching (issue #1485).
            if (sessionActive && queueFull) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null,
                            onClick           = { AppBannerCenter.enqueue(queueFullMessage) },
                        ),
                )
            }
        }
    }
}
