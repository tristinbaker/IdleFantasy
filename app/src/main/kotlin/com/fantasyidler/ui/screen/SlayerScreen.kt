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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.model.Skills
import com.fantasyidler.data.model.SlayerTask
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.PendingLamp
import com.fantasyidler.ui.viewmodel.SlayerViewModel
import com.fantasyidler.ui.viewmodel.xpProgressFraction
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
                title = { Text(stringResource(R.string.slayer_title)) },
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
    Surface(
        shape    = RoundedCornerShape(12.dp),
        color    = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text       = task.displayName,
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
                    val name  = skillKey.split('_')
                        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
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
