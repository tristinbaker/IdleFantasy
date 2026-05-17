package com.fantasyidler.ui.screen

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.json.CropData
import com.fantasyidler.data.model.FarmingPatch
import com.fantasyidler.simulator.XpTable
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.FarmingUiState
import com.fantasyidler.ui.viewmodel.FarmingViewModel
import com.fantasyidler.ui.viewmodel.remainingMs
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatDurationMs
import com.fantasyidler.util.formatXp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmingScreen(
    onBack: () -> Unit,
    viewModel: FarmingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

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
                    Text(stringResource(R.string.skill_farming_name))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = padding,
            modifier        = Modifier.fillMaxSize(),
        ) {
            // XP bar
            item { FarmingXpBar(state) }

            // Patch cards
            val patches = state.patches.associateBy { it.patchNumber }
            items(state.patchCount) { index ->
                val patchNumber = index + 1
                val patch       = patches[patchNumber]
                PatchCard(
                    patchNumber = patchNumber,
                    patch       = patch,
                    crops       = state.availableCrops.associateBy { it.id },
                    now         = state.now,
                    onPlant     = { viewModel.openPlantSheet(patchNumber) },
                    onHarvest   = { viewModel.harvestPatch(patchNumber) },
                    onClear     = { viewModel.clearPatch(patchNumber) },
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    // Plant crop bottom sheet
    state.plantingPatchNumber?.let { patchNum ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::closePlantSheet,
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            PlantSheet(
                crops     = state.availableCrops,
                inventory = state.inventory,
                onPlant   = { crop -> viewModel.plantCrop(patchNum, crop) },
                onDismiss = viewModel::closePlantSheet,
            )
        }
    }

    // Harvest result dialog
    state.harvestResult?.let { result ->
        AlertDialog(
            onDismissRequest = viewModel::harvestResultConsumed,
            title = { Text(stringResource(R.string.farming_harvested, result.cropName)) },
            text = {
                Column {
                    Text(
                        text  = "+${result.xpGained.formatXp()} XP",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GoldPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (result.itemsGained.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        result.itemsGained.forEach { (key, qty) ->
                            Text(
                                text  = "${GameStrings.cropName(context, key)}: ×$qty",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::harvestResultConsumed) {
                    Text(stringResource(R.string.btn_close))
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// XP bar
// ---------------------------------------------------------------------------

@Composable
private fun FarmingXpBar(state: FarmingUiState) {
    val level    = state.farmingLevel
    val xp       = state.farmingXp
    val progress = XpTable.progressFraction(xp)

    Surface(
        color    = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape    = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier             = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text       = "🌱 " + stringResource(R.string.farming_title, level),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text  = "${xp.formatXp()} XP",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color    = GoldPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = stringResource(R.string.farming_patches, state.patchCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Patch card
// ---------------------------------------------------------------------------

@Composable
private fun PatchCard(
    patchNumber: Int,
    patch: FarmingPatch?,
    crops: Map<String, CropData>,
    now: Long,
    onPlant: () -> Unit,
    onHarvest: () -> Unit,
    onClear: () -> Unit,
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    val isEmpty   = patch == null || patch.cropType == null
    val cropData  = patch?.cropType?.let { crops[it] }
    val remaining = if (isEmpty) Long.MAX_VALUE else patch!!.remainingMs(crops, now)
    val isReady   = !isEmpty && remaining <= 0
    val isGrowing = !isEmpty && !isReady

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text       = stringResource(R.string.format_patch_number, patchNumber),
                style      = MaterialTheme.typography.labelMedium,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))

            when {
                isEmpty -> {
                    // Empty
                    Text(
                        text  = stringResource(R.string.label_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onPlant, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.btn_plant))
                    }
                }

                isGrowing -> {
                    // Growing
                    val growthMs  = cropData?.growthTimeMs ?: 1L
                    val elapsed   = growthMs - remaining
                    val progress  = (elapsed.toFloat() / growthMs).coerceIn(0f, 1f)
                    Text(
                        text       = "${cropData?.emoji ?: "🌱"} ${cropData?.displayName ?: patch.cropType}",
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color    = GoldPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = "Ready in ${remaining.formatDurationMs()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick  = { showClearConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.btn_clear))
                    }
                }

                isReady -> {
                    // Ready
                    Text(
                        text       = "${cropData?.emoji ?: "🌾"} ${cropData?.displayName ?: patch.cropType}",
                        style      = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = stringResource(R.string.label_ready_to_harvest),
                        style = MaterialTheme.typography.bodySmall,
                        color = GoldPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick  = onHarvest,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.btn_harvest))
                        }
                        OutlinedButton(
                            onClick  = { showClearConfirm = true },
                            modifier = Modifier.weight(1f),
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Text(stringResource(R.string.btn_clear))
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.farming_clear_patch)) },
            text  = { Text(stringResource(R.string.farming_clear_desc)) },
            confirmButton = {
                Button(
                    onClick = { showClearConfirm = false; onClear() },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.btn_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Plant sheet
// ---------------------------------------------------------------------------

@Composable
private fun PlantSheet(
    crops: List<CropData>,
    inventory: Map<String, Int>,
    onPlant: (CropData) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
    ) {
        Text(
            text     = stringResource(R.string.btn_plant),
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider()
        Column(Modifier.verticalScroll(rememberScrollState())) {
            crops.forEach { crop ->
                val seedCount = inventory[crop.seedName] ?: 0
                val enabled   = seedCount > 0
                Row(
                    modifier             = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text  = "${crop.emoji} ${crop.displayName}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (enabled) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        )
                        Text(
                            text  = "Lv. ${crop.levelRequired}  •  ${crop.growthTimeHours}h  •  ${crop.harvestXp} XP/crop",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text  = "Seeds: $seedCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (enabled) GoldPrimary else MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick  = { onPlant(crop); onDismiss() },
                        enabled  = enabled,
                    ) {
                        Text(stringResource(R.string.btn_plant))
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}
