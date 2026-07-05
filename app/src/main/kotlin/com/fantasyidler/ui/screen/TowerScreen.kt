package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
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
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.ui.viewmodel.TowerMilestone
import com.fantasyidler.ui.viewmodel.TowerViewModel
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.util.GameStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TowerScreen(
    viewModel: TowerViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state            by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            try { snackbarHostState.showSnackbar(msg) }
            finally { viewModel.snackbarConsumed() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tower_title)) },
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier       = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                TowerHeaderCard(
                    currentFloor         = state.currentFloor,
                    nextFloorToQueue     = state.nextFloorToQueue,
                    enemyStrengthPct     = state.enemyStrengthPct,
                    bestFloor            = state.bestFloor,
                    hasSession           = state.towerSession != null,
                    sessionDone          = state.towerSession?.completed == true,
                    startingSession      = state.startingSession,
                    equippedWeapons      = state.equippedWeapons,
                    selectedWeaponSlot   = state.selectedWeaponSlot,
                    onWeaponSlotSelected = viewModel::selectWeaponSlot,
                    onStart              = viewModel::startFloor,
                    onCollect            = viewModel::collectFloor,
                )
            }

            item {
                Text(
                    text       = stringResource(R.string.tower_milestones),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            items(TowerViewModel.MILESTONES) { milestone ->
                MilestoneRow(
                    milestone  = milestone,
                    bestFloor  = state.bestFloor,
                    claimed    = milestone.floor in state.claimedMilestones,
                    claimable  = milestone.floor in state.claimableMilestones,
                    onClaim    = { viewModel.claimMilestone(milestone.floor) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TowerHeaderCard(
    currentFloor:         Int,
    nextFloorToQueue:     Int,
    enemyStrengthPct:     Int,
    bestFloor:            Int,
    hasSession:           Boolean,
    sessionDone:          Boolean,
    startingSession:      Boolean,
    equippedWeapons:      Map<String, EquipmentData>,
    selectedWeaponSlot:   String?,
    onWeaponSlotSelected: (String) -> Unit,
    onStart:              () -> Unit,
    onCollect:            () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text       = stringResource(R.string.tower_floor_label, currentFloor + 1),
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (bestFloor > 0) {
                        Text(
                            text  = stringResource(R.string.tower_best_floor, bestFloor),
                            style = MaterialTheme.typography.bodyMedium,
                            color = GoldPrimary,
                        )
                    }
                }
                if (currentFloor > 0) {
                    SuggestionChip(
                        onClick = {},
                        label   = { Text(stringResource(R.string.tower_enemy_strength, "+$enemyStrengthPct")) },
                    )
                }
            }

            // Weapon picker — only when no active session
            if (!hasSession && equippedWeapons.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text  = stringResource(R.string.label_weapon),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(4.dp),
                ) {
                    equippedWeapons.forEach { (slot, weaponData) ->
                        val effectiveSelected = selectedWeaponSlot
                            ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equippedWeapons.containsKey(it) }
                        FilterChip(
                            selected = slot == effectiveSelected,
                            onClick  = { onWeaponSlotSelected(slot) },
                            label    = {
                                Column {
                                    Text(
                                        text  = GameStrings.itemName(context, weaponData.name),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    weaponData.combatStyle?.let { style ->
                                        Text(
                                            text  = style.replaceFirstChar { it.titlecase() },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                sessionDone -> Button(
                    onClick  = onCollect,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.tower_collect_prompt))
                }
                hasSession  -> OutlinedButton(
                    onClick  = {},
                    enabled  = false,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.tower_floor_label, currentFloor + 1))
                }
                else        -> Button(
                    onClick  = onStart,
                    enabled  = !startingSession,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.tower_start_btn, nextFloorToQueue))
                }
            }
        }
    }
}

@Composable
private fun MilestoneRow(
    milestone: TowerMilestone,
    bestFloor: Int,
    claimed:   Boolean,
    claimable: Boolean,
    onClaim:   () -> Unit,
) {
    val unlocked = bestFloor >= milestone.floor
    val dimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text       = stringResource(R.string.tower_floor_label, milestone.floor),
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color      = if (unlocked) GoldPrimary else dimColor,
            )
            Text(
                text  = milestone.description,
                style = MaterialTheme.typography.bodySmall,
                color = if (unlocked) MaterialTheme.colorScheme.onSurface else dimColor,
            )
        }
        when {
            claimed   -> SuggestionChip(
                onClick = {},
                label   = { Text(stringResource(R.string.tower_milestone_claimed)) },
            )
            claimable -> Button(onClick = onClaim) {
                Text(stringResource(R.string.tower_milestone_claim))
            }
            else      -> SuggestionChip(
                onClick = {},
                enabled = false,
                label   = { Text(stringResource(R.string.tower_floor_label, milestone.floor)) },
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
