package com.fantasyidler.ui.screen

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.model.BulkSellReceipt
import com.fantasyidler.ui.theme.ScaledSheetContent
import com.fantasyidler.ui.viewmodel.BulkSellPreview
import com.fantasyidler.ui.viewmodel.ShopEntry
import com.fantasyidler.ui.viewmodel.ShopTransaction
import com.fantasyidler.ui.viewmodel.ShopViewModel
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatQuantity
import kotlinx.coroutines.launch

private fun localizedCategory(context: Context, raw: String): String {
    val resId = when (raw) {
        "Ores & Materials" -> R.string.shop_cat_ores_and_materials
        "Logs & Wood"      -> R.string.shop_cat_logs_and_wood
        "Seeds & Farming"  -> R.string.shop_cat_seeds_and_farming
        "Merchant's Guild" -> R.string.shop_cat_merchants_guild
        "Equipment"        -> R.string.shop_cat_equipment
        "Special"          -> R.string.shop_cat_special
        "Weapons"          -> R.string.shop_cat_weapons
        "Armor"            -> R.string.shop_cat_armor
        "Tools"            -> R.string.shop_cat_tools
        "Food"             -> R.string.shop_cat_food
        "Materials"        -> R.string.shop_cat_materials
        "Misc"             -> R.string.shop_cat_misc
        "Capes"            -> R.string.shop_cat_capes
        else               -> return raw
    }
    return context.getString(resId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    onBack: () -> Unit,
    viewModel: ShopViewModel = hiltViewModel(),
) {
    val state             by viewModel.uiState.collectAsState()
    val context           = LocalContext.current

    AppBannerEffect(state.snackbarMessage, viewModel::snackbarConsumed)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
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
    ) { padding ->
        val pagerState = rememberPagerState(pageCount = { if (state.ironman) 1 else 2 })
        val scope      = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.ironman) {
                // Ironman characters can only sell — no Buy tab at all.
                Text(
                    text     = stringResource(R.string.ironman_shop_buy_blocked),
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            } else {
                TabRow(selectedTabIndex = pagerState.currentPage) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick  = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text     = { Text(stringResource(R.string.btn_buy)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick  = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text     = { Text(stringResource(R.string.btn_sell)) },
                    )
                }
            }

            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text  = stringResource(R.string.label_coins),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text  = state.coins.formatCoins(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when {
                    state.ironman || page == 1 -> SellList(
                        inventory          = state.inventory,
                        equipped           = state.equipped,
                        lockedItems        = state.lockedItems,
                        context            = context,
                        compactNumbers     = state.compactNumbers,
                        keepOneOfEach      = state.keepOneOfEach,
                        onKeepOneChange    = viewModel::setKeepOneOfEach,
                        priceFor           = viewModel::sellPriceFor,
                        categoryFor        = viewModel::sellCategoryFor,
                        onSell             = { key -> viewModel.openSell(key, GameStrings.itemName(context, key)) },
                        onToggleLock       = viewModel::toggleItemLock,
                        onSellJunk         = viewModel::previewSellJunk,
                        onSellOldEquipment = viewModel::previewSellOldEquipment,
                        receipts           = state.bulkSellReceipts,
                    )
                    else -> BuyList(
                        entries            = viewModel.buyEntries.filter {
                            it.mercantileLevelRequired <= state.mercantileLevel && viewModel.isBuyEntryEligible(it, state)
                        },
                        coins              = state.coins,
                        xpBoostActive      = state.xpBoostActive,
                        inventory          = state.inventory,
                        compactNumbers     = state.compactNumbers,
                        discountedPriceFor = viewModel::discountedPrice,
                        onBuy              = viewModel::openBuy,
                    )
                }
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
            ScaledSheetContent {
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

    state.pendingBulkSell?.let { preview ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissBulkSell,
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            ScaledSheetContent {
            BulkSellSheet(
                preview        = preview,
                compactNumbers = state.compactNumbers,
                onConfirm      = viewModel::confirmBulkSell,
                onDismiss      = viewModel::dismissBulkSell,
            )
            }
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
    inventory: Map<String, Int>,
    compactNumbers: Boolean = false,
    discountedPriceFor: (ShopEntry) -> Int,
    onBuy: (ShopEntry) -> Unit,
) {
    val context = LocalContext.current
    val grouped = remember(entries) { entries.groupBy { it.categoryName } }

    LazyColumn(Modifier.fillMaxSize()) {
        grouped.forEach { (category, categoryEntries) ->
            item(key = "hdr_$category") { ShopSectionHeader(localizedCategory(context, category)) }
            items(categoryEntries, key = { it.key }) { entry ->
                val discounted = discountedPriceFor(entry)
                val hasDiscount = discounted < entry.price
                val canAfford  = coins >= discounted
                val isXpBoost  = entry.key == ShopViewModel.XP_BOOST_KEY
                val owned      = inventory[entry.key] ?: 0
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
                                text       = if (isXpBoost) entry.displayName
                                             else GameStrings.itemName(context, entry.key),
                                style      = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color      = if (canAfford) MaterialTheme.colorScheme.onSurface
                                             else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            )
                            if (isXpBoost && xpBoostActive) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text       = stringResource(R.string.shop_active),
                                    style      = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color      = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        val desc = GameStrings.itemDesc(context, entry.key)
                            .takeIf { it.isNotBlank() } ?: entry.description
                        if (desc.isNotBlank()) {
                            Text(
                                text  = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = if (canAfford) 1f else 0.38f,
                                ),
                            )
                        }
                        if (!isXpBoost) {
                            Text(
                                text  = stringResource(R.string.shop_qty_in_inv, owned.formatQuantity(compactNumbers)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text       = stringResource(R.string.shop_total_amount, discounted.toLong().formatCoins()),
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (canAfford) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                        )
                        if (hasDiscount) {
                            Text(
                                text           = stringResource(R.string.shop_total_amount, entry.price.toLong().formatCoins()),
                                style          = MaterialTheme.typography.bodySmall,
                                textDecoration = TextDecoration.LineThrough,
                                color          = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
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

private enum class SellOrder(val labelRes: Int) {
    DEFAULT(R.string.shop_order_default),
    HIGHEST_AMOUNT(R.string.shop_order_highest_amount),
    HIGHEST_PRICE(R.string.shop_order_highest_price),
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SellList(
    inventory: Map<String, Int>,
    equipped: Map<String, String?>,
    lockedItems: Set<String>,
    context: Context,
    compactNumbers: Boolean = false,
    keepOneOfEach: Boolean = false,
    priceFor: (String) -> Int,
    categoryFor: (String) -> String,
    onSell: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    onSellJunk: () -> Unit,
    onSellOldEquipment: () -> Unit,
    onKeepOneChange: (Boolean) -> Unit,
    receipts: List<BulkSellReceipt> = emptyList(),
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedOrder by remember { mutableStateOf(SellOrder.DEFAULT) }
    var showReceipts by remember { mutableStateOf(false) }

    if (showReceipts) {
        AlertDialog(
            onDismissRequest = { showReceipts = false },
            confirmButton    = {
                TextButton(onClick = { showReceipts = false }) { Text(stringResource(R.string.home_got_it)) }
            },
            title = { Text(stringResource(R.string.shop_recent_bulk_sales)) },
            text  = {
                if (receipts.isEmpty()) {
                    Text(stringResource(R.string.shop_recent_bulk_sales_empty))
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        receipts.forEachIndexed { index, receipt ->
                            if (index > 0) Spacer(Modifier.height(12.dp))
                            Text(
                                text  = DateUtils.getRelativeTimeSpanString(receipt.atMs).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            receipt.items.entries
                                .sortedBy { GameStrings.itemName(context, it.key) }
                                .forEach { (key, qty) ->
                                    Text(
                                        text  = stringResource(R.string.format_item_quantity, GameStrings.itemName(context, key), qty),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            Text(
                                text  = stringResource(R.string.reward_part_coins_plain, receipt.coins.formatCoins()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            },
        )
    }

    val grouped = remember(inventory, query, selectedCategory, selectedOrder) {
        val comparator = when (selectedOrder) {
            SellOrder.DEFAULT -> compareBy<Triple<String, Int, String>> { it.third }
            SellOrder.HIGHEST_AMOUNT -> compareByDescending<Triple<String, Int, String>> { it.second }
            SellOrder.HIGHEST_PRICE -> compareByDescending<Triple<String, Int, String>> { priceFor(it.first) }
        }
        inventory.entries
            .filter { it.key != "coins" }
            .map { Triple(it.key, it.value, GameStrings.itemName(context, it.key)) }
            .filter { (_, _, name) -> query.isBlank() || name.contains(query.trim(), ignoreCase = true) }
            .groupBy { (key, _, _) -> categoryFor(key) }
            .filterKeys { selectedCategory == null || it == selectedCategory }
            .entries
            .sortedBy { SELL_CATEGORY_ORDER.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            .map { (category, entries) ->
                category to entries.sortedWith(comparator.thenBy { it.third }.thenBy { it.first })
            }
    }
    val presentCategories = remember(inventory) {
        inventory.keys.filter { it != "coins" }.map(categoryFor).distinct()
            .sortedBy { SELL_CATEGORY_ORDER.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
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
                ) { Text(stringResource(R.string.shop_sell_junk)) }
                OutlinedButton(
                    onClick  = onSellOldEquipment,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.shop_sell_old_gear)) }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text     = stringResource(R.string.shop_keep_one_toggle),
                    style    = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked         = keepOneOfEach,
                    onCheckedChange = onKeepOneChange,
                )
            }
            TextButton(
                onClick  = { showReceipts = true },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text(stringResource(R.string.shop_recent_bulk_sales)) }
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it },
                placeholder   = { Text(stringResource(R.string.shop_search_hint)) },
                singleLine    = true,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick  = { selectedCategory = null },
                    label    = { Text(stringResource(R.string.shop_filter_all)) },
                )
                presentCategories.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick  = { selectedCategory = if (selectedCategory == category) null else category },
                        label    = { Text(localizedCategory(context, category)) },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SellOrder.entries.forEach { order ->
                    FilterChip(
                        selected = selectedOrder == order,
                        onClick = { selectedOrder = order },
                        label = { Text(stringResource(order.labelRes)) },
                    )
                }
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
                item(key = "sell_hdr_$category") { ShopSectionHeader(localizedCategory(context, category)) }
                items(entries, key = { it.first }) { (key, qty, _) ->
                    val sellPrice  = priceFor(key)
                    val isEquipped = equipped.values.any { it == key }
                    val isLocked   = key in lockedItems
                    val lockedAlpha = if (isLocked) 0.5f else 1f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick     = { onSell(key) },
                                onLongClick = { onToggleLock(key) },
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            FlowRow(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text       = buildString {
                                        if (isLocked) append("🔒 ")
                                        append(GameStrings.itemName(context, key))
                                    },
                                    style      = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color      = MaterialTheme.colorScheme.onSurface.copy(alpha = lockedAlpha),
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
                                text  = stringResource(R.string.shop_qty_in_inv, qty.formatQuantity(compactNumbers)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text       = stringResource(R.string.shop_price_each, sellPrice.toLong().formatCoins()),
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.primary.copy(alpha = lockedAlpha),
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
    val context = LocalContext.current
    val qty   = transaction.qty
    val total = transaction.priceEach.toLong() * qty
    var textValue by remember { mutableStateOf(qty.toString()) }
    LaunchedEffect(qty) { if (textValue.toIntOrNull() != qty) textValue = qty.toString() }
    val localizedName = GameStrings.itemName(context, transaction.key)
        .takeIf { it.isNotBlank() } ?: transaction.displayName

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
        Text(
            text       = if (transaction.isBuy) stringResource(R.string.shop_buy_prefix, localizedName)
                         else stringResource(R.string.shop_sell_prefix, localizedName),
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = stringResource(R.string.shop_price_each_long, transaction.priceEach.toLong().formatCoins()),
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
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.crafting_decrease))
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
                    modifier   = Modifier.width(130.dp),
                )
                IconButton(onClick = onPlus, enabled = qty < transaction.maxQty) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.crafting_increase))
                }
            }
            Spacer(Modifier.height(8.dp))
            QtyQuickButtons(qty, transaction.maxQty) { onSetQty(it) }
            Spacer(Modifier.height(8.dp))
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
            Text(
                text       = stringResource(R.string.shop_total_amount, total.formatCoins()),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary,
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

// ---------------------------------------------------------------------------
// Bulk sell confirmation sheet
// ---------------------------------------------------------------------------

@Composable
private fun BulkSellSheet(
    preview: BulkSellPreview,
    compactNumbers: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text     = stringResource(preview.titleRes),
            style    = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        LazyColumn(
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            items(preview.items, key = { it.key }) { item ->
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text     = GameStrings.itemName(LocalContext.current, item.key),
                        style    = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text  = "×${item.qty.formatQuantity(compactNumbers)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    Text(
                        text       = item.total.formatCoins(),
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider()
            }
        }
        HorizontalDivider(thickness = 2.dp, modifier = Modifier.padding(vertical = 8.dp))
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text  = stringResource(R.string.shop_bulk_sell_total),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text       = preview.totalCoins.formatCoins(),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary,
            )
        }
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.btn_cancel))
            }
            Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.shop_bulk_sell_confirm))
            }
        }
    }
}

@Composable
private fun ShopSectionHeader(title: String) {
    Text(
        text     = title.uppercase(),
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}
