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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import com.fantasyidler.R
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.model.Skills
import com.fantasyidler.data.model.SlayerTask
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.util.GameStrings
import com.fantasyidler.ui.viewmodel.PendingLamp
import com.fantasyidler.ui.viewmodel.SlayerViewModel
import com.fantasyidler.ui.viewmodel.xpProgressFraction
import com.fantasyidler.ui.theme.ScaledSheetContent
import com.fantasyidler.util.formatXp

private data class ShopItem(
    val key: String,
    val labelRes: Int,
    val descRes: Int,
    val cost: Int,
    val isEquipment: Boolean,
    val xpAmount: Long = 0L,
)

private val SHOP_ITEMS = listOf(
    ShopItem("xp_lamp_small",      R.string.slayer_lamp_small,      R.string.slayer_lamp_small_desc,  cost = 50,  isEquipment = false, xpAmount = 10_000L),
    ShopItem("xp_lamp_large",      R.string.slayer_lamp_large,      R.string.slayer_lamp_large_desc,  cost = 200, isEquipment = false, xpAmount = 50_000L),
    ShopItem("slayer_helm",        R.string.item_slayer_helm_name,  R.string.item_slayer_helm_desc,   cost = 400, isEquipment = true),
    ShopItem("abyssal_whip",       R.string.item_abyssal_whip_name, R.string.item_abyssal_whip_desc,  cost = 300, isEquipment = true),
    ShopItem("slayer_platebody",   R.string.item_slayer_platebody_name, R.string.item_slayer_platebody_desc, cost = 350, isEquipment = true),
    ShopItem("slayer_platelegs",   R.string.item_slayer_platelegs_name, R.string.item_slayer_platelegs_desc, cost = 250, isEquipment = true),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlayerScreen(
    onBack: () -> Unit = {},
    viewModel: SlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    AppBannerEffect(state.snackbarMessage, viewModel::snackbarConsumed)

    state.pendingSlayerDungeonKey?.let { dungeonKey ->
        val context = LocalContext.current
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val dungeonName = viewModel.gameData.dungeons[dungeonKey]?.displayName ?: dungeonKey
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissSlayerDungeonPicker,
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            ScaledSheetContent {
            SlayerWeaponPickerSheet(
                dungeonName      = dungeonName,
                equippedWeapons  = state.slayerEquippedWeapons,
                selectedSlot     = state.slayerSelectedWeaponSlot
                    ?: state.slayerEquippedWeapons.keys.firstOrNull(),
                onWeaponSelected = viewModel::selectSlayerWeapon,
                onConfirm        = viewModel::confirmSlayerDungeonQueue,
                onDismiss        = viewModel::dismissSlayerDungeonPicker,
                context          = context,
            )
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.slayer_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
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
            // ── Skill header ──────────────────────────────────────────────
            SlayerSkillHeader(
                level        = state.slayerLevel,
                xp           = state.slayerXp,
                slayerPoints = state.slayerPoints,
            )

            HorizontalDivider()

            // ── Active task ───────────────────────────────────────────────
            Text(
                text  = stringResource(R.string.slayer_active_task),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val currentTask = state.activeTask
            if (currentTask != null) {
                TaskCard(task = currentTask, dungeons = state.taskDungeons)
                if (state.taskDungeons.isNotEmpty() && !state.taskIsStuck) {
                    OutlinedButton(
                        onClick  = viewModel::queueTaskDungeon,
                        enabled  = state.queueSize < state.maxQueueSize,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.slayer_add_dungeon_to_queue))
                    }
                }
                if (state.taskIsStuck) {
                    Surface(
                        shape    = RoundedCornerShape(12.dp),
                        color    = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text  = stringResource(R.string.slayer_task_stuck),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick  = viewModel::rerollStuckTask,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.slayer_reroll_free))
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape    = RoundedCornerShape(12.dp),
                    color    = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text     = stringResource(R.string.slayer_no_task),
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick  = viewModel::getNewTask,
                    enabled  = state.activeTask == null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.slayer_get_task))
                }
                OutlinedButton(
                    onClick  = viewModel::skipTask,
                    enabled  = state.activeTask != null && state.slayerPoints >= 30,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.slayer_skip_task))
                }
            }

            HorizontalDivider()

            // ── Foretell ──────────────────────────────────────────────────
            Text(
                text  = stringResource(R.string.slayer_foretell_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ForetellSection(
                foretelledTasks      = state.foretelledTasks,
                nextCostUnits        = state.nextForetelCostUnits,
                inventory            = state.inventory,
                queueSize            = state.queueSize,
                maxQueueSize         = state.maxQueueSize,
                onForetell           = viewModel::foretelTask,
                onQueueTask          = viewModel::queueForetelledTaskDungeon,
            )

            HorizontalDivider()

            // ── Slayer Shop ───────────────────────────────────────────────
            Text(
                text  = stringResource(R.string.slayer_shop_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Surface(
                shape    = RoundedCornerShape(16.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SHOP_ITEMS.forEach { item ->
                        val alreadyOwned = item.isEquipment && (state.inventory[item.key] ?: 0) > 0
                        val equipData = if (item.isEquipment) viewModel.shopEquipment[item.key] else null
                        ShopRow(
                            item         = item,
                            points       = state.slayerPoints,
                            alreadyOwned = alreadyOwned,
                            equipData    = equipData,
                            onBuy        = {
                                if (item.isEquipment) viewModel.buyEquipment(item.key, item.cost)
                                else viewModel.showLampPicker(item.xpAmount, item.cost)
                            },
                        )
                    }
                }
            }

            Text(
                text  = stringResource(R.string.slayer_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val pendingLamp = state.pendingLamp
        if (pendingLamp != null) {
            LampSkillPickerDialog(
                pendingLamp     = pendingLamp,
                skillLevels     = state.skillLevels,
                onSkillSelected = viewModel::selectLampSkill,
                onDismiss       = viewModel::dismissLampPicker,
            )
        }
    }
}

@Composable
private fun SlayerSkillHeader(
    level: Int,
    xp: Long,
    slayerPoints: Int,
) {
    val progress = xpProgressFraction(xp)
    Surface(
        shape    = RoundedCornerShape(12.dp),
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
                    text       = stringResource(R.string.skill_slayer_name),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text  = stringResource(R.string.slayer_level_label, level),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GoldPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress    = { progress },
                modifier    = Modifier.fillMaxWidth(),
                trackColor  = MaterialTheme.colorScheme.surface,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text  = stringResource(R.string.slayer_xp_label, xp.formatXp()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text  = stringResource(R.string.slayer_points, slayerPoints),
                    style = MaterialTheme.typography.bodySmall,
                    color = GoldPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TaskCard(task: SlayerTask, dungeons: List<String>) {
    val context = LocalContext.current
    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text       = GameStrings.enemyName(context, task.enemyKey),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (dungeons.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = stringResource(R.string.slayer_found_in, dungeons.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress   = { task.killsCompleted.toFloat() / task.targetKills.toFloat() },
                modifier   = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text  = stringResource(R.string.slayer_kills_remaining, task.killsCompleted, task.targetKills),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text  = stringResource(R.string.slayer_xp_per_kill, task.xpPerKill),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text  = stringResource(R.string.slayer_points_on_complete, task.taskPoints),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun ForetellSection(
    foretelledTasks: List<SlayerTask>,
    nextCostUnits: Int,
    inventory: Map<String, Int>,
    queueSize: Int,
    maxQueueSize: Int,
    onForetell: () -> Unit,
    onQueueTask: (SlayerTask) -> Unit,
) {
    val context = LocalContext.current
    val totalBones = (inventory["bones"] ?: 0) +
        (inventory["big_bones"] ?: 0) * 2 +
        (inventory["giant_bones"] ?: 0) * 4 +
        (inventory["dragon_bone"] ?: 0) * 8
    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text  = stringResource(R.string.slayer_foretell_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (foretelledTasks.isEmpty()) {
                Text(
                    text  = stringResource(R.string.slayer_foretell_queue_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                foretelledTasks.forEachIndexed { i, task ->
                    Row(
                        modifier             = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment    = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text  = stringResource(R.string.slayer_foretell_slot, i + 1, GameStrings.enemyName(context, task.enemyKey)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text  = stringResource(R.string.slayer_kills_remaining, task.killsCompleted, task.targetKills),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick  = { onQueueTask(task) },
                            enabled  = queueSize < maxQueueSize,
                        ) {
                            Text(stringResource(R.string.slayer_foretell_queue_btn))
                        }
                    }
                }
            }
            if (foretelledTasks.size < 3) {
                Button(
                    onClick  = onForetell,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.slayer_foretell_btn, nextCostUnits))
                }
            } else {
                Text(
                    text  = stringResource(R.string.slayer_foretell_queue_full),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ShopRow(
    item: ShopItem,
    points: Int,
    alreadyOwned: Boolean,
    equipData: EquipmentData?,
    onBuy: () -> Unit,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = stringResource(item.labelRes),
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (equipData != null) {
                val statParts = buildList {
                    if (equipData.attackBonus   > 0) add("ATK +${equipData.attackBonus}")
                    if (equipData.strengthBonus > 0) add("STR +${equipData.strengthBonus}")
                    if (equipData.defenseBonus  > 0) add("DEF +${equipData.defenseBonus}")
                }
                if (statParts.isNotEmpty()) {
                    Text(
                        text  = statParts.joinToString("  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = GoldPrimary,
                    )
                }
                val reqs = equipData.requirements
                if (reqs.isNotEmpty()) {
                    val reqText = reqs.entries.joinToString(", ") { (skill, lvl) ->
                        "${skill.replaceFirstChar { it.uppercase() }} $lvl"
                    }
                    Text(
                        text  = stringResource(R.string.slayer_requires, reqText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text  = stringResource(item.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier            = Modifier.padding(start = 8.dp),
        ) {
            Text(
                text       = stringResource(R.string.slayer_shop_cost, item.cost),
                style      = MaterialTheme.typography.bodyMedium,
                color      = GoldPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick  = onBuy,
                enabled  = !alreadyOwned && points >= item.cost,
            ) {
                Text(
                    if (alreadyOwned) stringResource(R.string.slayer_owned)
                    else stringResource(R.string.btn_buy)
                )
            }
        }
    }
}

@Composable
private fun LampSkillPickerDialog(
    pendingLamp: PendingLamp,
    skillLevels: Map<String, Int>,
    onSkillSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.slayer_lamp_pick_skill)) },
        text = {
            Column(
                modifier            = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Skills.ALL.forEach { skillKey ->
                    val level = skillLevels[skillKey] ?: 1
                    val name  = GameStrings.skillName(context, skillKey)
                    Surface(
                        onClick  = { onSkillSelected(skillKey) },
                        shape    = RoundedCornerShape(8.dp),
                        color    = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text  = stringResource(R.string.slayer_level_label, level),
                                style = MaterialTheme.typography.bodySmall,
                                color = GoldPrimary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SlayerWeaponPickerSheet(
    dungeonName: String,
    equippedWeapons: Map<String, EquipmentData>,
    selectedSlot: String?,
    onWeaponSelected: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    context: android.content.Context,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
        Text(
            text     = stringResource(R.string.label_weapon),
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Text(
            text  = stringResource(R.string.slayer_add_dungeon_to_queue) + ": $dungeonName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            equippedWeapons.forEach { (slot, weaponData) ->
                val isSelected = slot == selectedSlot
                FilterChip(
                    selected = isSelected,
                    onClick  = { onWeaponSelected(slot) },
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
        Spacer(Modifier.height(16.dp))
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.slayer_add_dungeon_to_queue))
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.btn_cancel))
        }
    }
}
