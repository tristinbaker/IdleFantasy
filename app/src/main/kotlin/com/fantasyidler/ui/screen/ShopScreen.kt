package com.fantasyidler.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
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
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
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
                onMinus     = { viewModel.setTransactionQty(t.qty - 1) },
                onPlus      = { viewModel.setTransactionQty(t.qty + 1) },
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

    LazyColumn(Modifier.fillMaxSize()) {
        grouped.forEach { (category, categoryEntries) ->
            item(key = "hdr_$category") { ShopSectionHeader(category) }
            items(categoryEntries, key = { it.key }) { entry ->
                val canAfford  = coins >= entry.price
                val isXpBoost  = entry.key == ShopViewModel.XP_BOOST_KEY
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBuy(entry) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text       = entry.displayName,
                                style      = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color      = if (canAfford) MaterialTheme.colorScheme.onSurface
                                             else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            )
                            if (isXpBoost && xpBoostActive) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text       = "ACTIVE",
                                    style      = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color      = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        if (entry.description.isNotBlank()) {
                            Text(
                                text     = entry.description,
                                style    = MaterialTheme.typography.bodySmall,
                                color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = if (canAfford) 1f else 0.38f,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        text       = "${entry.price} coins",
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = if (canAfford) GoldPrimary else GoldPrimary.copy(alpha = 0.38f),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick  = onSellJunk,
                    modifier = Modifier.weight(1f),
                ) { Text("Sell Junk") }
                OutlinedButton(
                    onClick  = onSellOldEquipment,
                    modifier = Modifier.weight(1f),
                ) { Text("Sell Old Gear") }
            }
            HorizontalDivider()
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
                item(key = "sell_hdr_$category") { ShopSectionHeader(category) }
                items(entries, key = { it.key }) { (key, qty) ->
                    val sellPrice = priceFor(key)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSell(key) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text       = GameStrings.itemName(context, key),
                                style      = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text  = "×$qty in inventory",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text       = "$sellPrice coins ea.",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = GoldPrimary,
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ---------------------------------------------------------------------------
// Transaction sheet
// ---------------------------------------------------------------------------

@Composable
private fun TransactionSheet(
    transaction: ShopTransaction,
    coins: Long,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onSetQty: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val qty   = transaction.qty
    val total = transaction.priceEach.toLong() * qty
    var textValue by remember { mutableStateOf(qty.toString()) }
    LaunchedEffect(qty) { if (textValue.toIntOrNull() != qty) textValue = qty.toString() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
        Text(
            text       = if (transaction.isBuy) "Buy: ${transaction.displayName}"
                         else "Sell: ${transaction.displayName}",
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = "${transaction.priceEach} coins each",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        if (transaction.maxQty > 1) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onMinus, enabled = qty > 1) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                }
                OutlinedTextField(
                    value         = textValue,
                    onValueChange = { new ->
                        val filtered = new.filter { it.isDigit() }
                        textValue = filtered
                        filtered.toIntOrNull()?.let { onSetQty(it) }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction    = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        val parsed = textValue.toIntOrNull()?.coerceIn(1, transaction.maxQty.coerceAtLeast(1)) ?: 1
                        onSetQty(parsed); textValue = parsed.toString()
                    }),
                    textStyle  = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign  = TextAlign.Center,
                    ),
                    singleLine = true,
                    modifier   = Modifier.width(90.dp),
                )
                IconButton(onClick = onPlus, enabled = qty < transaction.maxQty) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase")
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text  = if (transaction.isBuy) "Total cost" else "You'll receive",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text       = "$total coins",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = GoldPrimary,
            )
        }

        if (transaction.isBuy && coins < total) {
            Text(
                text  = stringResource(R.string.error_not_enough_coins),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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

// ---------------------------------------------------------------------------
// Section header (buy list categories)
// ---------------------------------------------------------------------------

@Composable
private fun ShopSectionHeader(title: String) {
    Column {
        HorizontalDivider()
        Text(
            text     = title.uppercase(),
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
