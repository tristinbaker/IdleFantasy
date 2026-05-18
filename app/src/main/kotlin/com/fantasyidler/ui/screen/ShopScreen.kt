package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.ui.components.BigStepper
import com.fantasyidler.ui.components.ChunkyCard
import com.fantasyidler.ui.components.ClaimBadge
import com.fantasyidler.ui.components.EntityIconDisk
import com.fantasyidler.ui.components.SectionHeader
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.ShopEntry
import com.fantasyidler.ui.viewmodel.ShopTransaction
import com.fantasyidler.ui.viewmodel.ShopViewModel
import com.fantasyidler.util.GameStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    onBack: () -> Unit,
    viewModel: ShopViewModel = hiltViewModel(),
) {
    val state             by viewModel.uiState.collectAsState()
    val context           = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.snackbarConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.label_shop)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        var subTab by remember { mutableIntStateOf(0) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = subTab) {
                Tab(
                    selected = subTab == 0,
                    onClick  = { subTab = 0 },
                    text     = { Text(stringResource(R.string.btn_buy)) },
                )
                Tab(
                    selected = subTab == 1,
                    onClick  = { subTab = 1 },
                    text     = { Text(stringResource(R.string.btn_sell)) },
                )
            }

            when (subTab) {
                0 -> BuyList(
                    entries       = viewModel.buyEntries,
                    coins         = state.coins,
                    xpBoostActive = state.xpBoostActive,
                    onBuy         = viewModel::openBuy,
                )
                else -> SellList(
                    inventory          = state.inventory,
                    equipped           = state.equipped,
                    context            = context,
                    priceFor           = viewModel::sellPriceFor,
                    categoryFor        = viewModel::sellCategoryFor,
                    onSell             = { key -> viewModel.openSell(key, GameStrings.itemName(context, key)) },
                    onSellJunk         = viewModel::sellJunk,
                    onSellOldEquipment = viewModel::sellOldEquipment,
                )
            }
        }
    }

    state.transaction?.let { t ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissTransaction,
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            TransactionSheet(
                transaction = t,
                coins       = state.coins,
                onSetQty    = viewModel::setTransactionQty,
                onConfirm   = viewModel::confirmTransaction,
                onDismiss   = viewModel::dismissTransaction,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Buy list
// ---------------------------------------------------------------------------

@Composable
private fun BuyList(
    entries: List<ShopEntry>,
    coins: Long,
    xpBoostActive: Boolean,
    onBuy: (ShopEntry) -> Unit,
) {
    val grouped = remember(entries) { entries.groupBy { it.categoryName } }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        grouped.forEach { (category, categoryEntries) ->
            item(key = "hdr_$category") {
                Box(modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) {
                    SectionHeader(category)
                }
            }
            items(categoryEntries, key = { it.key }) { entry ->
                val canAfford  = coins >= entry.price
                val isXpBoost  = entry.key == ShopViewModel.XP_BOOST_KEY
                val isActiveBoost = isXpBoost && xpBoostActive

                ChunkyCard(
                    onClick = { onBuy(entry) },
                    enabled = true,
                    highlight = isActiveBoost,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EntityIconDisk(entityId = entry.key, contentDescription = entry.displayName, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text       = entry.displayName,
                                    style      = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color      = if (canAfford) MaterialTheme.colorScheme.onSurface
                                                 else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                )
                                if (isActiveBoost) {
                                    Spacer(Modifier.width(6.dp))
                                    ClaimBadge(text = stringResource(R.string.shop_active), pulse = true)
                                }
                            }
                            if (entry.description.isNotBlank()) {
                                Text(
                                    text  = entry.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                        alpha = if (canAfford) 1f else 0.5f,
                                    ),
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        PricePill(price = entry.price.toLong(), enabled = canAfford)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ---------------------------------------------------------------------------
// Sell list
// ---------------------------------------------------------------------------

private val SELL_CATEGORY_ORDER = listOf("Weapons", "Armor", "Tools", "Food", "Materials", "Misc")

@Composable
private fun SellList(
    inventory: Map<String, Int>,
    equipped: Map<String, String?>,
    context: android.content.Context,
    priceFor: (String) -> Int,
    categoryFor: (String) -> String,
    onSell: (String) -> Unit,
    onSellJunk: () -> Unit,
    onSellOldEquipment: () -> Unit,
) {
    val grouped = remember(inventory) {
        inventory.entries
            .groupBy { categoryFor(it.key) }
            .entries
            .sortedBy { SELL_CATEGORY_ORDER.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick  = onSellJunk,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.shop_sell_junk)) }
                OutlinedButton(
                    onClick  = onSellOldEquipment,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.shop_sell_old_gear)) }
            }
        }
        if (inventory.isEmpty()) {
            item {
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = stringResource(R.string.label_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            grouped.forEach { (category, entries) ->
                item(key = "sell_hdr_$category") {
                    Box(modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) {
                        SectionHeader(category)
                    }
                }
                items(entries, key = { it.key }) { (key, qty) ->
                    val sellPrice  = priceFor(key)
                    val isEquipped = equipped.values.any { it == key }
                    val displayName = GameStrings.itemName(context, key)

                    ChunkyCard(onClick = { onSell(key) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EntityIconDisk(entityId = key, contentDescription = displayName, size = 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text       = displayName,
                                        style      = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (isEquipped) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text  = stringResource(R.string.shop_equipped_label),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Text(
                                    text  = stringResource(R.string.shop_qty_in_inv, qty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            PricePill(price = sellPrice.toLong(), enabled = true)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PricePill(price: Long, enabled: Boolean) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = GoldPrimary.copy(alpha = if (enabled) 0.20f else 0.08f),
    ) {
        Text(
            text       = "$price",
            modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style      = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color      = if (enabled) GoldPrimary else GoldPrimary.copy(alpha = 0.5f),
        )
    }
}

// ---------------------------------------------------------------------------
// Transaction sheet
// ---------------------------------------------------------------------------

@Composable
private fun TransactionSheet(
    transaction: ShopTransaction,
    coins: Long,
    onSetQty: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val qty   = transaction.qty
    val total = transaction.priceEach.toLong() * qty
    val maxQty = transaction.maxQty.coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EntityIconDisk(
            entityId = transaction.key,
            contentDescription = transaction.displayName,
            size = 72.dp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text       = if (transaction.isBuy) stringResource(R.string.shop_buy_prefix, transaction.displayName)
                         else stringResource(R.string.shop_sell_prefix, transaction.displayName),
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = stringResource(R.string.shop_price_each_long, transaction.priceEach.toString()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        if (maxQty > 1) {
            BigStepper(
                value         = qty,
                onValueChange = onSetQty,
                minValue      = 1,
                maxValue      = maxQty,
                onMax         = { onSetQty(maxQty) },
            )
            Spacer(Modifier.height(16.dp))
        }

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text  = if (transaction.isBuy) stringResource(R.string.shop_total_cost) else stringResource(R.string.shop_youll_receive),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PricePill(price = total, enabled = !transaction.isBuy || coins >= total)
        }

        if (transaction.isBuy && coins < total) {
            Spacer(Modifier.height(8.dp))
            Text(
                text  = stringResource(R.string.error_not_enough_coins),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.btn_cancel))
            }
            Button(
                onClick  = onConfirm,
                modifier = Modifier.weight(1f),
                enabled  = !transaction.isBuy || coins >= total,
            ) {
                Text(if (transaction.isBuy) stringResource(R.string.btn_buy)
                     else stringResource(R.string.btn_sell))
            }
        }
    }
}
