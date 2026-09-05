package com.fantasyidler.ui.screen.skills


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.data.json.FishData
import com.fantasyidler.data.json.OreData
import com.fantasyidler.data.json.TreeData
import com.fantasyidler.data.model.Skills
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.simulator.XpTable
import com.fantasyidler.ui.screen.AppBannerCenter
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatXp
import com.fantasyidler.ui.viewmodel.QuestIndicator


@Composable
internal fun MiningSheet(
    guildDailyButton: (@Composable () -> Unit)? = null,
    ores: Map<String, OreData>,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    currentXp: Long = 0L,
    efficiency: Float = 1f,
    petBoostPct: Int = 0,
    xpBonusMult: Float = 1f,
    activeQuests: Map<String, List<QuestIndicator>> = emptyMap(),
    inventory: Map<String, Int> = emptyMap(),
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            text     = stringResource(R.string.label_choose_activity),
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            text     = stringResource(R.string.skill_mining_desc),
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        )
        guildDailyButton?.invoke()
        if (sessionDurationMs > 0) {
            Text(
                text     = stringResource(R.string.skills_session_duration, sessionDurationMs / 60_000),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 2.dp),
            )
            Text(
                text     = stringResource(R.string.skill_mining_qty_estimate, SkillSimulator.estimateGatheringQty(efficiency)),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        HorizontalDivider()
        Column(Modifier.verticalScroll(scrollState)) {
            ores.entries
                .sortedBy { it.value.levelRequired }
                .forEach { (key, ore) ->
                    val xpGain = (SkillSimulator.estimateGatheringXp(ore.xpPerOre, efficiency * xpBonusMult) * (1 + petBoostPct / 100.0)).toLong()
                    ActivityRow(
                        name             = GameStrings.itemName(context, key),
                        detail           = stringResource(R.string.skills_level_req_xp, ore.levelRequired, ore.xpPerOre),
                        projectedLabel   = projectedXpLabel(currentXp, xpGain),
                        isStarting       = isStarting,
                        hasActiveSession = hasActiveSession,
                        isQueueFull      = isQueueFull,
                        questIndicators  = activeQuests["${Skills.MINING}:$key"] ?: emptyList(),
                        ownedQty         = inventory[key] ?: 0,
                        onClick          = { selectedKey = key },
                    )
                }
        }
    }
    selectedKey?.let { key ->
        val ore = ores[key] ?: return@let
        ActivityDetailDialog(
            name             = GameStrings.itemName(context, key),
            detail           = stringResource(R.string.skills_level_req_xp, ore.levelRequired, ore.xpPerOre),
            description      = GameStrings.itemDesc(context, key),
            hasActiveSession = hasActiveSession,
            isQueueFull      = isQueueFull,
            onConfirm        = { onSelect(key) },
            onDismiss        = { selectedKey = null },
        )
    }
}

@Composable
internal fun WoodcuttingSheet(
    guildDailyButton: (@Composable () -> Unit)? = null,
    trees: Map<String, TreeData>,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    currentXp: Long = 0L,
    efficiency: Float = 1f,
    petBoostPct: Int = 0,
    xpBonusMult: Float = 1f,
    activeQuests: Map<String, List<QuestIndicator>> = emptyMap(),
    inventory: Map<String, Int> = emptyMap(),
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            text     = stringResource(R.string.label_choose_activity),
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            text     = stringResource(R.string.skill_woodcutting_desc),
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        )
        guildDailyButton?.invoke()
        if (sessionDurationMs > 0) {
            Text(
                text     = stringResource(R.string.skills_session_duration, sessionDurationMs / 60_000),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        HorizontalDivider()
        Column(Modifier.verticalScroll(scrollState)) {
            trees.entries
                .sortedBy { it.value.levelRequired }
                .forEach { (key, tree) ->
                    val xpGain = (SkillSimulator.estimateGatheringXp(tree.xpPerLog, efficiency * xpBonusMult) * (1 + petBoostPct / 100.0)).toLong()
                    ActivityRow(
                        name             = GameStrings.itemName(context, tree.logName),
                        detail           = stringResource(R.string.skills_log_desc, tree.levelRequired, tree.xpPerLog),
                        projectedLabel   = projectedXpLabel(currentXp, xpGain),
                        isStarting       = isStarting,
                        hasActiveSession = hasActiveSession,
                        isQueueFull      = isQueueFull,
                        questIndicators  = activeQuests["${Skills.WOODCUTTING}:${tree.logName}"] ?: emptyList(),
                        ownedQty         = inventory[tree.logName] ?: 0,
                        onClick          = { selectedKey = key },
                    )
                }
        }
    }
    selectedKey?.let { key ->
        val tree = trees[key] ?: return@let
        ActivityDetailDialog(
            name             = GameStrings.itemName(context, tree.logName),
            detail           = stringResource(R.string.skills_log_desc, tree.levelRequired, tree.xpPerLog),
            description      = GameStrings.itemDesc(context, tree.logName),
            hasActiveSession = hasActiveSession,
            isQueueFull      = isQueueFull,
            onConfirm        = { onSelect(key) },
            onDismiss        = { selectedKey = null },
        )
    }
}

@Composable
internal fun FishingSheet(
    guildDailyButton: (@Composable () -> Unit)? = null,
    fish: Map<String, FishData>,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    currentXp: Long = 0L,
    efficiency: Float = 1f,
    petBoostPct: Int = 0,
    xpBonusMult: Float = 1f,
    activeQuests: Map<String, List<QuestIndicator>> = emptyMap(),
    inventory: Map<String, Int> = emptyMap(),
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            text     = stringResource(R.string.label_choose_activity),
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            text     = stringResource(R.string.skill_fishing_desc),
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        )
        guildDailyButton?.invoke()
        if (sessionDurationMs > 0) {
            Text(
                text     = stringResource(R.string.skills_session_duration, sessionDurationMs / 60_000),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        HorizontalDivider()
        Column(Modifier.verticalScroll(scrollState)) {
            fish.entries
                .sortedBy { it.value.levelRequired }
                .forEach { (key, f) ->
                    val xpGain = (SkillSimulator.estimateGatheringXp(f.xpPerCatch, efficiency * xpBonusMult) * (1 + petBoostPct / 100.0)).toLong()
                    ActivityRow(
                        name             = GameStrings.itemName(context, key),
                        detail           = stringResource(R.string.skills_fish_desc, f.levelRequired, f.xpPerCatch),
                        projectedLabel   = projectedXpLabel(currentXp, xpGain),
                        isStarting       = isStarting,
                        hasActiveSession = hasActiveSession,
                        isQueueFull      = isQueueFull,
                        questIndicators  = activeQuests["${Skills.FISHING}:$key"] ?: emptyList(),
                        ownedQty         = inventory[key] ?: 0,
                        onClick          = { selectedKey = key },
                    )
                }
        }
    }
    selectedKey?.let { key ->
        val f = fish[key] ?: return@let
        ActivityDetailDialog(
            name             = GameStrings.itemName(context, key),
            detail           = stringResource(R.string.skills_fish_desc, f.levelRequired, f.xpPerCatch),
            description      = GameStrings.itemDesc(context, key),
            hasActiveSession = hasActiveSession,
            isQueueFull      = isQueueFull,
            onConfirm        = { onSelect(key) },
            onDismiss        = { selectedKey = null },
        )
    }
}


@Composable
internal fun ComingSoonSheet() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = stringResource(R.string.label_coming_soon),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun projectedXpLabel(currentXp: Long, xpGain: Long): String {
    val currentLevel  = XpTable.levelForXp(currentXp)
    val projectedLevel = XpTable.levelForXp(currentXp + xpGain)
    return if (projectedLevel > currentLevel)
        stringResource(R.string.skills_projected_xp_level, xpGain.formatXp(), projectedLevel)
    else
        "+${xpGain.formatXp()} XP"
}

@Composable
internal fun ActivityRow(
    name: String,
    detail: String,
    projectedLabel: String? = null,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    questIndicators: List<QuestIndicator> = emptyList(),
    ownedQty: Int? = null,
    onClick: () -> Unit,
) {
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val queueFullMessage = stringResource(R.string.snackbar_queue_full)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = !isStarting,
                onClick = {
                    if (isQueueFull) {
                        AppBannerCenter.enqueue(queueFullMessage)
                    } else {
                        onClick()
                    }
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                if (questIndicators.isNotEmpty()) {
                    QuestIndicatorIcons(questIndicators)
                }
            }
            Text(
                text  = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ownedQty != null) {
                Text(
                    text  = stringResource(R.string.crafting_owned, ownedQty),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ownedQty > 0) MaterialTheme.colorScheme.primary else dim,
                )
            }
            if (projectedLabel != null) {
                Text(
                    text  = projectedLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (isStarting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            Text(
                text  = when {
                    isQueueFull      -> stringResource(R.string.snackbar_queue_full)
                    hasActiveSession -> stringResource(R.string.skills_add_to_queue)
                    else             -> stringResource(R.string.btn_start_session)
                },
                style = MaterialTheme.typography.labelMedium,
                color = if(isQueueFull) dim else MaterialTheme.colorScheme.primary,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
internal fun ActivityDetailDialog(
    name: String,
    detail: String,
    description: String,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (description.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            val queueFullMessage = stringResource(R.string.snackbar_queue_full)
            Button(
                onClick = {
                    if (isQueueFull) {
                        AppBannerCenter.enqueue(queueFullMessage)
                    } else {
                        onConfirm(); onDismiss()
                    }
                },
            ) {
               Text(if (hasActiveSession) stringResource(R.string.skills_add_queue_short) else stringResource(R.string.btn_start_session))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        },
    )
}

// ---------------------------------------------------------------------------
// Agility sheet
// ---------------------------------------------------------------------------

