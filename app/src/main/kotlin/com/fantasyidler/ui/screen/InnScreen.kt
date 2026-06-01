package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.model.WorkerTier
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.DailyFoodItem
import com.fantasyidler.ui.viewmodel.InnViewModel
import com.fantasyidler.util.formatCoins

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InnScreen(
    onBack: () -> Unit = {},
    onNavigateToWorkerSkills: (slot: Int) -> Unit = {},
    viewModel: InnViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var buyDialogFood by remember { mutableStateOf<DailyFoodItem?>(null) }

    LaunchedEffect(state.navigateToWorkerSkillsSlot) {
        if (state.navigateToWorkerSkillsSlot != 0) {
            val slot = state.navigateToWorkerSkillsSlot
            viewModel.navigationHandled()
            onNavigateToWorkerSkills(slot)
        }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.snackbarConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inn_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Column(
                modifier            = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text  = stringResource(R.string.inn_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.dailyFoods.isNotEmpty()) {
                DailyMenuSection(
                    foods = state.dailyFoods,
                    coins = state.coins,
                    onBuy = { food -> buyDialogFood = food },
                )
            }

            buyDialogFood?.let { food ->
                BuyFoodDialog(
                    food      = food,
                    coins     = state.coins,
                    onConfirm = { qty ->
                        viewModel.buyFood(food.key, food.price, qty)
                        buyDialogFood = null
                    },
                    onDismiss = { buyDialogFood = null },
                )
            }

            // ── Slot 1: Long Laborer ─────────────────────────────────────
            HorizontalDivider()
            Text(
                text  = stringResource(R.string.inn_slot1_header),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.hiredWorker != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text       = stringResource(R.string.inn_worker_active),
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.padding(16.dp),
                        color      = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            TierCard(
                tierLabel    = stringResource(R.string.worker_long_laborer),
                workerName   = state.longLaborerName,
                description  = stringResource(R.string.inn_long_laborer_desc),
                cost         = WorkerTier.LONG_LABORER.hireCost,
                playerCoins  = state.coins,
                workerActive = state.hiredWorker != null,
                onHire       = { viewModel.hire(WorkerTier.LONG_LABORER) },
            )

            // ── Slot 2: Skilled Worker ───────────────────────────────────
            HorizontalDivider()
            Text(
                text  = stringResource(R.string.inn_slot2_header),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.hiredWorker2 != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text       = stringResource(R.string.inn_worker_active),
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.padding(16.dp),
                        color      = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            TierCard(
                tierLabel    = stringResource(R.string.worker_apprentice),
                workerName   = state.apprenticeName,
                description  = stringResource(R.string.inn_apprentice_desc),
                cost         = WorkerTier.APPRENTICE.hireCost,
                playerCoins  = state.coins,
                workerActive = state.hiredWorker2 != null,
                onHire       = { viewModel.hire(WorkerTier.APPRENTICE) },
            )

            TierCard(
                tierLabel    = stringResource(R.string.worker_journeyman),
                workerName   = state.journeymanName,
                description  = stringResource(R.string.inn_journeyman_desc),
                cost         = WorkerTier.JOURNEYMAN.hireCost,
                playerCoins  = state.coins,
                workerActive = state.hiredWorker2 != null,
                onHire       = { viewModel.hire(WorkerTier.JOURNEYMAN) },
            )

            TierCard(
                tierLabel    = stringResource(R.string.worker_master),
                workerName   = state.masterName,
                description  = stringResource(R.string.inn_master_desc),
                cost         = WorkerTier.MASTER.hireCost,
                playerCoins  = state.coins,
                workerActive = state.hiredWorker2 != null,
                onHire       = { viewModel.hire(WorkerTier.MASTER) },
            )

            HorizontalDivider()
            Text(
                text  = stringResource(R.string.inn_gathering_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text  = stringResource(R.string.inn_crafting_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DailyMenuSection(
    foods: List<DailyFoodItem>,
    coins: Long,
    onBuy: (DailyFoodItem) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text       = stringResource(R.string.inn_daily_menu),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text  = stringResource(R.string.inn_daily_resets),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            foods.forEachIndexed { index, food ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                FoodRow(food = food, coins = coins, onBuy = { onBuy(food) })
            }
        }
    }
}

@Composable
private fun FoodRow(
    food: DailyFoodItem,
    coins: Long,
    onBuy: () -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = food.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text  = stringResource(R.string.inn_food_heal, food.healValue),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text  = stringResource(R.string.inn_hire_cost, food.price.toLong().formatCoins()),
            style = MaterialTheme.typography.bodyMedium,
            color = GoldPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Button(
            onClick  = onBuy,
            enabled  = coins >= food.price,
        ) {
            Text(stringResource(R.string.btn_buy))
        }
    }
}

@Composable
private fun BuyFoodDialog(
    food: DailyFoodItem,
    coins: Long,
    onConfirm: (qty: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var qtyText by remember(food.key) { mutableStateOf("1") }
    val maxQty = (coins / food.price).toInt().coerceAtLeast(1)
    val qty = qtyText.toIntOrNull()?.coerceIn(1, maxQty) ?: 1
    val total = qty.toLong() * food.price

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.displayName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text  = stringResource(R.string.inn_food_heal, food.healValue),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { qtyText = (qty - 1).toString() },
                        enabled = qty > 1,
                    ) { Text("-", style = MaterialTheme.typography.titleMedium) }
                    OutlinedTextField(
                        value          = qtyText,
                        onValueChange  = { qtyText = it.filter { c -> c.isDigit() }.take(4) },
                        modifier       = Modifier.width(72.dp),
                        textStyle      = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                        singleLine     = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    TextButton(
                        onClick = { qtyText = (qty + 1).toString() },
                        enabled = qty < maxQty,
                    ) { Text("+", style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text       = stringResource(R.string.inn_buy_total, total.formatCoins()),
                        style      = MaterialTheme.typography.bodyMedium,
                        color      = GoldPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(qty) }) {
                Text(stringResource(R.string.btn_buy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
    )
}

@Composable
private fun TierCard(
    tierLabel: String,
    workerName: String,
    description: String,
    cost: Long,
    playerCoins: Long,
    workerActive: Boolean,
    onHire: () -> Unit,
) {
    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text       = tierLabel,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (workerName.isNotEmpty()) {
                    Text(
                        text  = workerName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GoldPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text  = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text  = stringResource(R.string.inn_hire_cost, cost.formatCoins()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GoldPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(
                    onClick  = onHire,
                    enabled  = !workerActive && playerCoins >= cost,
                ) {
                    Text(stringResource(R.string.inn_hire))
                }
            }
        }
    }
}
