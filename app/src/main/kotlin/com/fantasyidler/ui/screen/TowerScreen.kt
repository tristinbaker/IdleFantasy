package com.fantasyidler.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.SpellData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.ui.viewmodel.TowerMilestone
import com.fantasyidler.ui.viewmodel.TowerViewModel
import com.fantasyidler.util.GameStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TowerScreen(
    viewModel: TowerViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state            by viewModel.uiState.collectAsState()

    AppBannerEffect(state.snackbarMessage, viewModel::snackbarConsumed)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
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
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val listState  = rememberLazyListState()
        val hasSession = state.towerSession != null
        // Starting a session collapses the pickers in the header card, which would
        // otherwise strand the scroll position partway down the milestone list (issue #1259).
        LaunchedEffect(hasSession) {
            if (hasSession) listState.animateScrollToItem(0)
        }

        LazyColumn(
            state          = listState,
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
                    isQueueFull          = state.isQueueFull,
                    equippedWeapons      = state.equippedWeapons,
                    selectedWeaponSlot   = state.selectedWeaponSlot,
                    onWeaponSlotSelected = viewModel::selectWeaponSlot,
                    selectedArrowKey     = state.selectedArrowKey,
                    onArrowSelected      = viewModel::selectArrow,
                    selectedSpell        = state.selectedSpell,
                    availableSpells      = state.availableSpells,
                    onSpellSelected      = viewModel::selectSpell,
                    magicLevel           = state.magicLevel,
                    inventory            = state.inventory,
                    selectedPotionKey    = state.selectedPotionKey,
                    availablePotions     = state.availablePotions,
                    potionEffects        = viewModel.potionEffects,
                    onPotionSelected     = viewModel::selectPotion,
                    onStart              = viewModel::startFloor,
                    onCollect            = viewModel::collectFloor,
                    debugOnAdvance       = viewModel::debugAdvanceTower,
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
    isQueueFull:          Boolean,
    equippedWeapons:      Map<String, EquipmentData>,
    selectedWeaponSlot:   String?,
    onWeaponSlotSelected: (String) -> Unit,
    selectedArrowKey:     String?,
    onArrowSelected:      (String?) -> Unit,
    selectedSpell:        SpellData?,
    availableSpells:      List<SpellData>,
    onSpellSelected:      (SpellData?) -> Unit,
    magicLevel:           Int,
    inventory:            Map<String, Int>,
    selectedPotionKey:    String?,
    availablePotions:     Map<String, Int>,
    potionEffects:        Map<String, Map<String, Int>>,
    onPotionSelected:     (String?) -> Unit,
    onStart:              () -> Unit,
    onCollect:            () -> Unit,
    debugOnAdvance:       () -> Unit,
) {
    val context = LocalContext.current
    val effectiveWeaponSlot = selectedWeaponSlot
        ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equippedWeapons.containsKey(it) }
    val combatStyle = equippedWeapons[effectiveWeaponSlot]?.combatStyle
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
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                // Stat scaling only starts past floor 100 (lower floors get harder via
                // tougher enemy types); a permanent +0% chip reads as a bug (issue #1049).
                if (currentFloor > 0 && enemyStrengthPct > 0) {
                    SuggestionChip(
                        onClick = {},
                        label   = { Text(stringResource(R.string.tower_enemy_strength, "+$enemyStrengthPct")) },
                    )
                }
            }

            // Weapon picker — selection applies to the next floor attempt
            if (equippedWeapons.isNotEmpty()) {
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
                        FilterChip(
                            selected = slot == effectiveWeaponSlot,
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

            // Arrow picker — ranged combat style only
            if (combatStyle == "ranged") {
                Spacer(Modifier.height(12.dp))
                ArrowLoadoutPicker(
                    selectedArrowKey  = selectedArrowKey,
                    inventory         = inventory,
                    context           = context,
                    onArrowSelected   = onArrowSelected,
                )
            }

            // Spell picker — magic combat style only
            if (combatStyle == "magic") {
                Spacer(Modifier.height(12.dp))
                SpellLoadoutPicker(
                    selectedSpell     = selectedSpell,
                    availableSpells   = availableSpells,
                    magicLevel        = magicLevel,
                    inventory         = inventory,
                    equippedWeapon    = equippedWeapons[effectiveWeaponSlot],
                    context           = context,
                    onSpellSelected   = onSpellSelected,
                )
            }

            // Potion picker
            if (availablePotions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                PotionLoadoutPicker(
                    availablePotions  = availablePotions,
                    potionEffects     = potionEffects,
                    selectedPotionKey = selectedPotionKey,
                    context           = context,
                    onPotionSelected  = onPotionSelected,
                )
            }

            Spacer(Modifier.height(12.dp))

            when {
                sessionDone -> Button(
                    onClick  = onCollect,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.tower_collect_prompt))
                }

                else        -> {
                    val queueFullMessage = stringResource(R.string.snackbar_queue_full)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick  = onStart,
                            enabled  = !startingSession && !isQueueFull,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.tower_start_btn, nextFloorToQueue))
                        }
                        if (isQueueFull) {
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

            if (BuildConfig.DEBUG) {
                TextButton(onClick = debugOnAdvance) {
                    Text("[Debug] Advance floor")
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
    val context  = LocalContext.current
    // No-arg descriptions bypass formatting: they may hold a literal % (e.g. "+1% tower XP")
    // that String.format would choke on.
    val description = if (milestone.itemKeys.isEmpty()) {
        stringResource(milestone.descriptionRes)
    } else {
        stringResource(
            milestone.descriptionRes,
            *milestone.itemKeys.map { GameStrings.itemName(context, it) }.toTypedArray(),
        )
    }

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
                color      = if (unlocked) MaterialTheme.colorScheme.primary else dimColor,
            )
            Text(
                text  = description,
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
