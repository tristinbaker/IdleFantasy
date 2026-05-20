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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.ui.components.EmptyState
import com.fantasyidler.ui.components.foundation.BigStepper
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant
import com.fantasyidler.ui.components.foundation.ChunkySheet
import com.fantasyidler.ui.components.foundation.EntityIconDisk
import com.fantasyidler.ui.screen.shop.components.AisleItem
import com.fantasyidler.ui.screen.shop.components.AisleSection
import com.fantasyidler.ui.screen.shop.shopEmojiFor
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
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
    val tokens            = LocalFantasyTokens.current
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
                title = {
                    Text(
                        text  = stringResource(R.string.label_shop),
                        style = tokens.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_back),
                            tint               = tokens.colors.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor   = tokens.colors.surface,
                    titleContentColor = tokens.colors.onSurface,
                ),
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
            ShopTabRow(subTab) { subTab = it }
            when {
                state.isLoading -> ShopLoading()
                subTab == 0 -> BuyAisles(
                    entries       = viewModel.buyEntries,
                    coins         = state.coins,
                    xpBoostActive = state.xpBoostActive,
                    onBuy         = viewModel::openBuy,
                )
                else -> SellAisles(
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
        ChunkySheet(
            onDismissRequest = viewModel::dismissTransaction,
            sheetState       = sheetState,
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
// Tabs
// ---------------------------------------------------------------------------

@Composable
private fun ShopTabRow(selected: Int, onSelect: (Int) -> Unit) {
    val tokens = LocalFantasyTokens.current
    TabRow(
        selectedTabIndex = selected,
        containerColor   = tokens.colors.surface,
        contentColor     = tokens.colors.primary,
    ) {
        Tab(
            selected = selected == 0,
            onClick  = { onSelect(0) },
            text     = { Text(stringResource(R.string.btn_buy),  style = tokens.typography.labelSmall) },
        )
        Tab(
            selected = selected == 1,
            onClick  = { onSelect(1) },
            text     = { Text(stringResource(R.string.btn_sell), style = tokens.typography.labelSmall) },
        )
    }
}

// ---------------------------------------------------------------------------
// Buy aisles
// ---------------------------------------------------------------------------

@Composable
private fun BuyAisles(
    entries: List<ShopEntry>,
    coins: Long,
    xpBoostActive: Boolean,
    onBuy: (ShopEntry) -> Unit,
) {
    val tokens  = LocalFantasyTokens.current
    val grouped = remember(entries) { entries.groupBy { it.categoryName } }

    if (entries.isEmpty()) {
        ShopEmpty(
            title = stringResource(R.string.shop_empty_buy_title),
            desc  = stringResource(R.string.shop_empty_buy_desc),
        )
        return
    }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = tokens.spacing.l, vertical = tokens.spacing.m + tokens.spacing.s),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.l),
    ) {
        grouped.forEach { (category, categoryEntries) ->
            val saleCount = categoryEntries.count { it.isOnSale }
            item(key = "aisle_$category") {
                AisleSection(
                    title          = category,
                    headerTrailing = if (saleCount > 0) {
                        { SaleCountChip(saleCount) }
                    } else null,
                ) {
                    AisleGrid(items = categoryEntries) { entry ->
                        val canAfford = coins >= entry.price
                        val isActiveBoost = entry.key == ShopViewModel.XP_BOOST_KEY && xpBoostActive
                        AisleItem(
                            entityId      = entry.key,
                            displayName   = entry.displayName,
                            price         = entry.price,
                            originalPrice = entry.originalPrice,
                            isOnSale      = entry.isOnSale,
                            percentOff    = entry.percentOff,
                            dim           = !canAfford && !isActiveBoost,
                            badge         = if (isActiveBoost) stringResource(R.string.shop_active) else null,
                            emojiFallback = shopEmojiFor(entry.categoryName, entry.key),
                            onClick       = { onBuy(entry) },
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(tokens.spacing.l)) }
    }
}

// ---------------------------------------------------------------------------
// Sell aisles
// ---------------------------------------------------------------------------

private val SELL_CATEGORY_ORDER = listOf("Weapons", "Armor", "Tools", "Food", "Materials", "Misc")

@Composable
private fun SellAisles(
    inventory: Map<String, Int>,
    equipped: Map<String, String?>,
    context: android.content.Context,
    priceFor: (String) -> Int,
    categoryFor: (String) -> String,
    onSell: (String) -> Unit,
    onSellJunk: () -> Unit,
    onSellOldEquipment: () -> Unit,
) {
    val tokens  = LocalFantasyTokens.current
    val grouped = remember(inventory) {
        inventory.entries
            .groupBy { categoryFor(it.key) }
            .entries
            .sortedBy { SELL_CATEGORY_ORDER.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = tokens.spacing.l, vertical = tokens.spacing.m + tokens.spacing.s),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.l),
    ) {
        item {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.m),
            ) {
                ChunkyButton(
                    text     = stringResource(R.string.shop_sell_junk),
                    onClick  = onSellJunk,
                    variant  = ChunkyButtonVariant.Secondary,
                    modifier = Modifier.weight(1f),
                )
                ChunkyButton(
                    text     = stringResource(R.string.shop_sell_old_gear),
                    onClick  = onSellOldEquipment,
                    variant  = ChunkyButtonVariant.Secondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (inventory.isEmpty()) {
            item {
                ShopEmpty(
                    title = stringResource(R.string.shop_empty_sell_title),
                    desc  = stringResource(R.string.shop_empty_sell_desc),
                )
            }
        } else {
            grouped.forEach { (category, entries) ->
                item(key = "sell_aisle_$category") {
                    AisleSection(title = category) {
                        AisleGrid(items = entries.toList()) { entry ->
                            val key         = entry.key
                            val qty         = entry.value
                            val isEquipped  = equipped.values.any { it == key }
                            val displayName = GameStrings.itemName(context, key)
                            AisleItem(
                                entityId      = key,
                                displayName   = displayName,
                                price         = priceFor(key),
                                badge         = when {
                                    isEquipped -> stringResource(R.string.shop_equipped_label)
                                    qty > 1    -> "×$qty"
                                    else       -> null
                                },
                                emojiFallback = shopEmojiFor(category, key),
                                onClick       = { onSell(key) },
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(tokens.spacing.l)) }
    }
}

// ---------------------------------------------------------------------------
// Grid wrapper — two columns per row, fills shorter rows with a Spacer.
// ---------------------------------------------------------------------------

@Composable
private fun <T> AisleGrid(items: List<T>, item: @Composable (T) -> Unit) {
    val tokens = LocalFantasyTokens.current
    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.s)) {
        items.chunked(2).forEach { pair ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.s),
            ) {
                Box(modifier = Modifier.weight(1f)) { item(pair[0]) }
                if (pair.size == 2) {
                    Box(modifier = Modifier.weight(1f)) { item(pair[1]) }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sale count chip — shown in an aisle header when items are discounted today.
// ---------------------------------------------------------------------------

@Composable
private fun SaleCountChip(count: Int) {
    val tokens = LocalFantasyTokens.current
    Surface(
        shape = tokens.shapes.chip,
        color = tokens.colors.warning.copy(alpha = 0.20f),
    ) {
        Text(
            text       = "$count on sale",
            modifier   = Modifier.padding(horizontal = tokens.spacing.s, vertical = tokens.spacing.xs),
            style      = tokens.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color      = tokens.colors.warning,
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
    val tokens = LocalFantasyTokens.current
    val qty    = transaction.qty
    val total  = transaction.priceEach.toLong() * qty
    val maxQty = transaction.maxQty.coerceAtLeast(1)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = tokens.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EntityIconDisk(
            entityId           = transaction.key,
            contentDescription = transaction.displayName,
            size               = tokens.spacing.xxl + tokens.spacing.xxl + tokens.spacing.m,
            emojiFallback      = shopEmojiFor("", transaction.key),
        )
        Spacer(Modifier.height(tokens.spacing.m + tokens.spacing.s))
        Text(
            text       = if (transaction.isBuy) stringResource(R.string.shop_buy_prefix, transaction.displayName)
                         else stringResource(R.string.shop_sell_prefix, transaction.displayName),
            style      = tokens.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = tokens.colors.onSurface,
        )
        Spacer(Modifier.height(tokens.spacing.xs))
        Text(
            text  = stringResource(R.string.shop_price_each_long, transaction.priceEach.toString()),
            style = tokens.typography.bodyMedium,
            color = tokens.colors.onSurfaceMuted,
        )
        Spacer(Modifier.height(tokens.spacing.l + tokens.spacing.s))

        if (maxQty > 1) {
            BigStepper(
                value         = qty,
                onValueChange = onSetQty,
                minValue      = 1,
                maxValue      = maxQty,
                onMax         = { onSetQty(maxQty) },
            )
            Spacer(Modifier.height(tokens.spacing.l))
        }

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text  = if (transaction.isBuy) stringResource(R.string.shop_total_cost)
                        else stringResource(R.string.shop_youll_receive),
                style = tokens.typography.bodyMedium,
                color = tokens.colors.onSurfaceMuted,
            )
            PricePill(price = total, enabled = !transaction.isBuy || coins >= total)
        }

        if (transaction.isBuy && coins < total) {
            Spacer(Modifier.height(tokens.spacing.m))
            Text(
                text  = stringResource(R.string.error_not_enough_coins),
                style = tokens.typography.bodyMedium,
                color = tokens.colors.error,
            )
        }

        Spacer(Modifier.height(tokens.spacing.l + tokens.spacing.s))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.m + tokens.spacing.s),
        ) {
            ChunkyButton(
                text     = stringResource(R.string.btn_cancel),
                onClick  = onDismiss,
                variant  = ChunkyButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
            ChunkyButton(
                text     = if (transaction.isBuy) stringResource(R.string.btn_buy)
                           else stringResource(R.string.btn_sell),
                onClick  = onConfirm,
                variant  = ChunkyButtonVariant.Primary,
                enabled  = !transaction.isBuy || coins >= total,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PricePill(price: Long, enabled: Boolean) {
    val tokens = LocalFantasyTokens.current
    Surface(
        shape = tokens.shapes.chip,
        color = tokens.colors.primary.copy(alpha = if (enabled) 0.20f else 0.08f),
    ) {
        Text(
            text       = "$price",
            modifier   = Modifier.padding(horizontal = tokens.spacing.m + tokens.spacing.xs, vertical = tokens.spacing.s),
            style      = tokens.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color      = if (enabled) tokens.colors.primary else tokens.colors.primary.copy(alpha = 0.5f),
        )
    }
}

// ---------------------------------------------------------------------------
// State containers
// ---------------------------------------------------------------------------

@Composable
private fun ShopLoading() {
    val tokens = LocalFantasyTokens.current
    Box(
        modifier         = Modifier.fillMaxSize().padding(tokens.spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = tokens.colors.primary)
    }
}

@Composable
private fun ShopEmpty(title: String, desc: String) {
    EmptyState(title = title, description = desc)
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
private fun PreviewShopLoading() {
    FantasyPreviewSurface {
        Box(modifier = Modifier.size(LocalFantasyTokens.current.spacing.xxl * 6)) {
            ShopLoading()
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewShopEmpty() {
    FantasyPreviewSurface {
        ShopEmpty(title = "Inventory empty", desc = "Train to fill it.")
    }
}

@PreviewLightDark
@Composable
private fun PreviewPricePill() {
    FantasyPreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(LocalFantasyTokens.current.spacing.m)) {
            PricePill(price = 250, enabled = true)
            PricePill(price = 999, enabled = false)
        }
    }
}
