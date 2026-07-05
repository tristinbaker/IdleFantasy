package com.fantasyidler.ui.screen


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.ui.viewmodel.ExpeditionsViewModel
import com.fantasyidler.data.json.AgilityCourseData
import com.fantasyidler.data.json.BoneData
import com.fantasyidler.data.json.FishData
import com.fantasyidler.data.json.LogData
import com.fantasyidler.data.json.OreData
import com.fantasyidler.data.json.ThievingNpcData
import com.fantasyidler.data.json.TreeData
import com.fantasyidler.data.model.Skills
import com.fantasyidler.ui.theme.GoldPrimary
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.style.TextAlign
import com.fantasyidler.ui.viewmodel.CraftableRecipe
import com.fantasyidler.ui.viewmodel.CraftingUiState
import com.fantasyidler.ui.viewmodel.CraftingViewModel
import com.fantasyidler.ui.viewmodel.SessionResult
import com.fantasyidler.ui.viewmodel.SheetState
import com.fantasyidler.ui.viewmodel.levelDisplay
import com.fantasyidler.ui.viewmodel.SkillsUiState
import com.fantasyidler.ui.viewmodel.SkillsViewModel
import com.fantasyidler.ui.viewmodel.xpProgressFraction
import com.fantasyidler.ui.viewmodel.nextLevelThreshold
import com.fantasyidler.ui.viewmodel.xpToNextLevel
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.simulator.XpTable
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.toTitleCase
import com.fantasyidler.util.formatDurationMs
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.toCountdown
import java.util.Locale
import com.fantasyidler.ui.viewmodel.QuestFillSuggestion


@Composable
internal fun MiningSheet(
    ores: Map<String, OreData>,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    currentXp: Long = 0L,
    efficiency: Float = 1f,
    xpBonusMult: Float = 1f,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    var selectedKey by remember { mutableStateOf<String?>(null) }
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
        Column(Modifier.verticalScroll(rememberScrollState())) {
            ores.entries
                .sortedBy { it.value.levelRequired }
                .forEach { (key, ore) ->
                    val xpGain = SkillSimulator.estimateGatheringXp(ore.xpPerOre, efficiency * xpBonusMult)
                    ActivityRow(
                        name             = GameStrings.itemName(context, key),
                        detail           = stringResource(R.string.skills_level_req_xp, ore.levelRequired, ore.xpPerOre),
                        projectedLabel   = projectedXpLabel(currentXp, xpGain),
                        isStarting       = isStarting,
                        hasActiveSession = hasActiveSession,
                        isQueueFull      = isQueueFull,
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
    trees: Map<String, TreeData>,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    currentXp: Long = 0L,
    efficiency: Float = 1f,
    xpBonusMult: Float = 1f,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    var selectedKey by remember { mutableStateOf<String?>(null) }
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
        if (sessionDurationMs > 0) {
            Text(
                text     = stringResource(R.string.skills_session_duration, sessionDurationMs / 60_000),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        HorizontalDivider()
        Column(Modifier.verticalScroll(rememberScrollState())) {
            trees.entries
                .sortedBy { it.value.levelRequired }
                .forEach { (key, tree) ->
                    val xpGain = SkillSimulator.estimateGatheringXp(tree.xpPerLog, efficiency * xpBonusMult)
                    ActivityRow(
                        name             = GameStrings.itemName(context, tree.logName),
                        detail           = stringResource(R.string.skills_log_desc, tree.levelRequired, tree.xpPerLog),
                        projectedLabel   = projectedXpLabel(currentXp, xpGain),
                        isStarting       = isStarting,
                        hasActiveSession = hasActiveSession,
                        isQueueFull      = isQueueFull,
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
    fish: Map<String, FishData>,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    currentXp: Long = 0L,
    efficiency: Float = 1f,
    xpBonusMult: Float = 1f,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    var selectedKey by remember { mutableStateOf<String?>(null) }
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
        if (sessionDurationMs > 0) {
            Text(
                text     = stringResource(R.string.skills_session_duration, sessionDurationMs / 60_000),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        HorizontalDivider()
        Column(Modifier.verticalScroll(rememberScrollState())) {
            fish.entries
                .sortedBy { it.value.levelRequired }
                .forEach { (key, f) ->
                    val xpGain = SkillSimulator.estimateGatheringXp(f.xpPerCatch, efficiency * xpBonusMult)
                    ActivityRow(
                        name             = GameStrings.itemName(context, key),
                        detail           = stringResource(R.string.skills_fish_desc, f.levelRequired, f.xpPerCatch),
                        projectedLabel   = projectedXpLabel(currentXp, xpGain),
                        isStarting       = isStarting,
                        hasActiveSession = hasActiveSession,
                        isQueueFull      = isQueueFull,
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

internal fun projectedXpLabel(currentXp: Long, xpGain: Long): String {
    val currentLevel  = XpTable.levelForXp(currentXp)
    val projectedLevel = XpTable.levelForXp(currentXp + xpGain)
    return if (projectedLevel > currentLevel)
        "+${xpGain.formatXp()} XP → Level $projectedLevel"
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
    onClick: () -> Unit,
) {
    val queueBlocked = hasActiveSession && isQueueFull
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isStarting && !queueBlocked, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text  = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                text  = if (hasActiveSession) stringResource(R.string.skills_add_to_queue) else stringResource(R.string.btn_start_session),
                style = MaterialTheme.typography.labelMedium,
                color = if (queueBlocked) MaterialTheme.colorScheme.onSurfaceVariant else GoldPrimary,
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
    val queueBlocked = hasActiveSession && isQueueFull
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
            Button(
                onClick = { onConfirm(); onDismiss() },
                enabled = !queueBlocked,
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

