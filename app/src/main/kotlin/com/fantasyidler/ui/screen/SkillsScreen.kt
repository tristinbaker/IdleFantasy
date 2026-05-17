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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.runtime.setValue
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
import com.fantasyidler.data.json.AgilityCourseData
import com.fantasyidler.data.json.BoneData
import com.fantasyidler.data.json.LogData
import com.fantasyidler.data.json.OreData
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
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatDurationMs
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.toCountdown
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    onNavigateToFarming: () -> Unit = {},
    viewModel: SkillsViewModel  = hiltViewModel(),
    craftingViewModel: CraftingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val craftSnackState by craftingViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.snackbarConsumed()
        }
    }

    LaunchedEffect(craftSnackState.snackbarMessage) {
        craftSnackState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            craftingViewModel.snackbarConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_skills)) })
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
            modifier = Modifier.fillMaxSize(),
        ) {
            // Active session banner
            state.activeSession?.let { session ->
                item {
                    ActiveSessionBanner(
                        skillName      = GameStrings.skillName(context, session.skillName),
                        activityKey    = session.activityKey,
                        endsAt         = session.endsAt,
                        completed      = session.completed,
                        onCollect      = viewModel::collectSession,
                        onAbandon      = viewModel::abandonSession,
                        onDebugFinish  = viewModel::debugFinishSession,
                    )
                }
            }

            // Gathering skills
            item { SectionHeader(stringResource(R.string.label_gathering_skills)) }
            items(Skills.GATHERING) { key ->
                val efficiency = when (key) {
                    Skills.MINING      -> state.miningEfficiency
                    Skills.WOODCUTTING -> state.woodcuttingEfficiency
                    Skills.FISHING     -> state.fishingEfficiency
                    else               -> 1.0f
                }
                SkillRow(
                    skillKey       = key,
                    level          = state.skillLevels[key] ?: 1,
                    xp             = state.skillXp[key] ?: 0L,
                    isActive       = state.activeSession?.skillName == key && state.activeSession?.completed == false,
                    onClick        = {
                        if (key == Skills.FARMING) onNavigateToFarming()
                        else viewModel.onSkillTapped(key)
                    },
                    toolEfficiency = efficiency,
                )
            }

            // Crafting skills
            item { SectionHeader(stringResource(R.string.label_crafting_skills)) }
            items(Skills.CRAFTING_SKILLS) { key ->
                SkillRow(
                    skillKey = key,
                    level    = state.skillLevels[key] ?: 1,
                    xp       = state.skillXp[key] ?: 0L,
                    isActive = state.activeSession?.skillName == key && state.activeSession?.completed == false,
                    onClick  = { viewModel.onSkillTapped(key) },
                )
            }

            // Prayer
            item { SectionHeader(stringResource(R.string.label_prayer)) }
            item {
                SkillRow(
                    skillKey = Skills.PRAYER,
                    level    = state.skillLevels[Skills.PRAYER] ?: 1,
                    xp       = state.skillXp[Skills.PRAYER] ?: 0L,
                    isActive = state.activeSession?.skillName == Skills.PRAYER && state.activeSession?.completed == false,
                    onClick  = { viewModel.onSkillTapped(Skills.PRAYER) },
                )
            }
        }
    }

    // Session result bottom sheet
    state.sessionResult?.let { result ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::resultConsumed,
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            SessionResultSheet(
                result    = result,
                context   = context,
                onDismiss = viewModel::resultConsumed,
            )
        }
    }

    // Activity selection bottom sheet
    state.sheetSkill?.let { sheet ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.dismissSheet()
                craftingViewModel.dismissRecipe()
            },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            when (sheet) {
                is SheetState.Mining -> MiningSheet(
                    ores              = sheet.ores,
                    isStarting        = state.startingSession,
                    hasActiveSession  = state.anySessionActive,
                    isQueueFull       = state.queueSize >= 3,
                    sessionDurationMs = state.sessionDurationMs,
                    onSelect          = { oreKey -> viewModel.startMiningSession(oreKey) },
                )
                is SheetState.Woodcutting -> WoodcuttingSheet(
                    trees             = sheet.trees,
                    isStarting        = state.startingSession,
                    hasActiveSession  = state.anySessionActive,
                    isQueueFull       = state.queueSize >= 3,
                    sessionDurationMs = state.sessionDurationMs,
                    onSelect          = { treeKey -> viewModel.startWoodcuttingSession(treeKey) },
                )
                SheetState.Fishing -> FishingSheet(
                    state            = state,
                    isStarting       = state.startingSession,
                    hasActiveSession = state.anySessionActive,
                    isQueueFull      = state.queueSize >= 3,
                    onStart          = viewModel::startFishingSession,
                )
                is SheetState.Agility -> AgilitySheet(
                    courses           = sheet.courses,
                    isStarting        = state.startingSession,
                    hasActiveSession  = state.anySessionActive,
                    isQueueFull       = state.queueSize >= 3,
                    sessionDurationMs = state.sessionDurationMs,
                    onSelect          = { courseKey -> viewModel.startAgilitySession(courseKey) },
                )
                is SheetState.Firemaking -> FiremakingSheet(
                    availableLogs     = sheet.availableLogs,
                    isStarting        = state.startingSession,
                    hasActiveSession  = state.anySessionActive,
                    isQueueFull       = state.queueSize >= 3,
                    sessionDurationMs = state.sessionDurationMs,
                    onSelect          = { logKey -> viewModel.startFiremakingSession(logKey) },
                    context           = context,
                )
                is SheetState.Runecrafting -> RunecraftingSheet(
                    sheet             = sheet,
                    isStarting        = state.startingSession,
                    hasActiveSession  = state.anySessionActive,
                    isQueueFull       = state.queueSize >= 3,
                    sessionDurationMs = state.sessionDurationMs,
                    onStart           = viewModel::startRunecraftingSession,
                )
                is SheetState.Prayer -> PrayerSheet(
                    availableBones    = sheet.availableBones,
                    inventory         = sheet.inventory,
                    prayerLevel       = state.skillLevels[Skills.PRAYER] ?: 1,
                    isStarting        = state.startingSession,
                    hasActiveSession  = state.anySessionActive,
                    isQueueFull       = state.queueSize >= 3,
                    sessionDurationMs = state.sessionDurationMs,
                    onStart           = viewModel::startPrayerSession,
                )
                is SheetState.Crafting -> {
                    val craftState by craftingViewModel.uiState.collectAsState()
                    CraftSkillSheet(
                        skillName         = sheet.skillName,
                        craftState        = craftState,
                        craftingViewModel = craftingViewModel,
                        hasActiveSession  = state.anySessionActive,
                        isQueueFull       = state.queueSize >= 3,
                        sessionDurationMs = state.sessionDurationMs,
                        context           = context,
                        onDismiss         = {
                            viewModel.dismissSheet()
                            craftingViewModel.dismissRecipe()
                        },
                    )
                }
                SheetState.ComingSoon -> ComingSoonSheet()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Active session banner
// ---------------------------------------------------------------------------

@Composable
private fun ActiveSessionBanner(
    skillName: String,
    activityKey: String,
    endsAt: Long,
    completed: Boolean,
    onCollect: () -> Unit,
    onAbandon: () -> Unit,
    onDebugFinish: () -> Unit = {},
) {
    // Tick every second so the countdown stays live.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (System.currentTimeMillis() < endsAt) {
            delay(1_000L)
            now = System.currentTimeMillis()
        }
    }

    Surface(
        color    = MaterialTheme.colorScheme.primaryContainer,
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text  = if (completed) stringResource(R.string.label_session_complete)
                        else stringResource(R.string.label_session_active),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(skillName)
                    if (activityKey.isNotEmpty()) {
                        append(" — ")
                        append(activityKey.replace('_', ' ').replaceFirstChar { it.uppercase() })
                    }
                },
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            if (!completed) {
                Text(
                    text  = remember(now) { endsAt.toCountdown() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = onAbandon) {
                        Text(stringResource(R.string.btn_abandon_session))
                    }
                    if (BuildConfig.DEBUG) {
                        TextButton(onClick = onDebugFinish) {
                            Text("[Debug] Finish Now")
                        }
                    }
                }
            } else {
                Button(onClick = onCollect, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.btn_collect_results))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Skill row
// ---------------------------------------------------------------------------

@Composable
private fun SkillRow(
    skillKey: String,
    level: Int,
    xp: Long,
    isActive: Boolean,
    onClick: () -> Unit,
    toolEfficiency: Float = 1.0f,
) {
    val context  = LocalContext.current
    val name     = GameStrings.skillName(context, skillKey)
    val emoji    = GameStrings.skillEmoji(skillKey)
    val progress = xpProgressFraction(xp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Emoji badge with level overlay
        Box(modifier = Modifier.size(44.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) GoldPrimary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = emoji,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text       = level.toString(),
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface,
                modifier   = Modifier
                    .align(Alignment.BottomEnd)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape,
                    )
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(
                modifier             = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text       = name,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (isActive) {
                    Text(
                        text  = stringResource(R.string.label_training),
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldPrimary,
                    )
                } else {
                    Text(
                        text  = "${xp.formatXp()} XP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color    = GoldPrimary,
            )
            if (toolEfficiency > 1.0f) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = stringResource(R.string.skills_tool_bonus, "%.2f".format(toolEfficiency)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Section header
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String) {
    Column {
        HorizontalDivider()
        Text(
            text     = title.uppercase(Locale.getDefault()),
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Activity selection sheets
// ---------------------------------------------------------------------------

@Composable
private fun MiningSheet(
    ores: Map<String, OreData>,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
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
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        HorizontalDivider()
        Column(Modifier.verticalScroll(rememberScrollState())) {
            ores.entries
                .sortedBy { it.value.levelRequired }
                .forEach { (key, ore) ->
                    ActivityRow(
                        name             = GameStrings.itemName(context, key),
                        detail           = stringResource(R.string.skills_level_req_xp, ore.levelRequired, ore.xpPerOre),
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
private fun WoodcuttingSheet(
    trees: Map<String, TreeData>,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
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
                    ActivityRow(
                        name             = GameStrings.itemName(context, tree.logName),
                        detail           = stringResource(R.string.skills_log_desc, tree.levelRequired, tree.xpPerLog),
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
private fun FishingSheet(
    state: SkillsUiState,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    onStart: () -> Unit,
) {
    val fishLevel = state.skillLevels[Skills.FISHING] ?: 1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text  = stringResource(R.string.skill_fishing_name),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = stringResource(R.string.skill_fishing_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = stringResource(R.string.skills_fishing_desc, fishLevel),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.sessionDurationMs > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                text  = stringResource(R.string.skills_session_duration, state.sessionDurationMs / 60_000),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick  = onStart,
            enabled  = !isStarting && !(hasActiveSession && isQueueFull),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isStarting) {
                CircularProgressIndicator(Modifier.size(20.dp))
            } else {
                Text(if (hasActiveSession) stringResource(R.string.skills_add_to_queue) else stringResource(R.string.btn_start_session))
            }
        }
    }
}

@Composable
private fun ComingSoonSheet() {
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
private fun ActivityRow(
    name: String,
    detail: String,
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
        Column {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text  = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun ActivityDetailDialog(
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

@Composable
private fun AgilitySheet(
    courses: Map<String, AgilityCourseData>,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
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
            text     = stringResource(R.string.skill_agility_desc),
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
            courses.entries
                .sortedBy { it.value.levelRequired }
                .forEach { (key, course) ->
                    ActivityRow(
                        name             = course.displayName,
                        detail           = "Lv. ${course.levelRequired}  •  ${course.xpPerSuccess} XP/lap",
                        isStarting       = isStarting,
                        hasActiveSession = hasActiveSession,
                        isQueueFull      = isQueueFull,
                        onClick          = { selectedKey = key },
                    )
                }
        }
    }
    selectedKey?.let { key ->
        val course = courses[key] ?: return@let
        ActivityDetailDialog(
            name             = course.displayName,
            detail           = "Lv. ${course.levelRequired}  •  ${course.xpPerSuccess} XP/lap",
            description      = GameStrings.agilityCourseDesc(context, key),
            hasActiveSession = hasActiveSession,
            isQueueFull      = isQueueFull,
            onConfirm        = { onSelect(key) },
            onDismiss        = { selectedKey = null },
        )
    }
}

// ---------------------------------------------------------------------------
// Firemaking sheet
// ---------------------------------------------------------------------------

@Composable
private fun FiremakingSheet(
    availableLogs: Map<String, LogData>,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    onSelect: (String) -> Unit,
    context: android.content.Context,
) {
    var selectedKey by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            text     = stringResource(R.string.label_choose_activity),
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            text     = stringResource(R.string.skill_firemaking_desc),
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
        if (availableLogs.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = stringResource(R.string.skills_no_logs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                availableLogs.entries
                    .sortedBy { it.value.levelRequired }
                    .forEach { (key, log) ->
                        ActivityRow(
                            name             = GameStrings.itemName(context, key),
                            detail           = stringResource(R.string.skills_log_desc, log.levelRequired, log.xpPerLog),
                            isStarting       = isStarting,
                            hasActiveSession = hasActiveSession,
                            isQueueFull      = isQueueFull,
                            onClick          = { selectedKey = key },
                        )
                    }
            }
        }
    }
    selectedKey?.let { key ->
        val log = availableLogs[key] ?: return@let
        ActivityDetailDialog(
            name             = GameStrings.itemName(context, key),
            detail           = stringResource(R.string.skills_log_desc, log.levelRequired, log.xpPerLog),
            description      = GameStrings.itemDesc(context, key),
            hasActiveSession = hasActiveSession,
            isQueueFull      = isQueueFull,
            onConfirm        = { onSelect(key) },
            onDismiss        = { selectedKey = null },
        )
    }
}

// ---------------------------------------------------------------------------
// Prayer sheet
// ---------------------------------------------------------------------------

@Composable
private fun PrayerSheet(
    availableBones: Map<String, BoneData>,
    inventory: Map<String, Int>,
    prayerLevel: Int,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    onStart: (boneKey: String, qty: Int) -> Unit,
) {
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val selectedBone = selectedKey?.let { availableBones[it] }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
    ) {
        Text(
            text     = "Prayer",
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            text     = stringResource(R.string.skill_prayer_desc),
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        )
        HorizontalDivider()

        if (selectedBone == null) {
            // ── Bone selection ───────────────────────────────────────────
            Text(
                text     = stringResource(R.string.skills_prayer_desc, prayerLevel),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (availableBones.isEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = stringResource(R.string.skills_no_bones),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                availableBones.forEach { (key, bone) ->
                    val qty = inventory[key] ?: 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedKey = key }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(bone.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text  = stringResource(R.string.skills_bone_qty, bone.xpPerBone.toInt(), qty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(stringResource(R.string.btn_select), style = MaterialTheme.typography.labelMedium, color = GoldPrimary)
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        } else {
            // ── Quantity picker ──────────────────────────────────────────
            val maxQty = inventory[selectedKey] ?: 0
            var qty by remember(selectedKey) { androidx.compose.runtime.mutableIntStateOf(maxQty.coerceAtLeast(1)) }
            var textValue by remember(selectedKey) { mutableStateOf(maxQty.coerceAtLeast(1).toString()) }

            TextButton(
                onClick  = { selectedKey = null },
                modifier = Modifier.padding(start = 4.dp),
            ) { Text(stringResource(R.string.btn_back_arrow)) }

            Text(
                text     = selectedBone.displayName,
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                text     = stringResource(R.string.skills_bone_selected, selectedBone.xpPerBone.toInt(), maxQty),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick  = { qty = (qty - 1).coerceAtLeast(1); textValue = qty.toString() },
                    enabled  = qty > 1,
                ) { Icon(Icons.Filled.Remove, contentDescription = "Decrease") }

                OutlinedTextField(
                    value         = textValue,
                    onValueChange = { new ->
                        val filtered = new.filter { it.isDigit() }
                        textValue = filtered
                        filtered.toIntOrNull()?.let { qty = it.coerceIn(1, maxQty.coerceAtLeast(1)) }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction    = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        val parsed = textValue.toIntOrNull()?.coerceIn(1, maxQty.coerceAtLeast(1)) ?: 1
                        qty = parsed; textValue = parsed.toString()
                    }),
                    textStyle    = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign  = TextAlign.Center,
                    ),
                    singleLine   = true,
                    modifier     = Modifier.width(90.dp),
                )

                IconButton(
                    onClick  = { qty = (qty + 1).coerceAtMost(maxQty.coerceAtLeast(1)); textValue = qty.toString() },
                    enabled  = qty < maxQty,
                ) { Icon(Icons.Filled.Add, contentDescription = "Increase") }
            }

            Text(
                text     = stringResource(R.string.skills_xp_total, (qty * selectedBone.xpPerBone).toInt()),
                style    = MaterialTheme.typography.bodyMedium,
                color    = GoldPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (sessionDurationMs > 0) {
                Text(
                    text     = "~${(qty.toLong() * (sessionDurationMs / 60)).formatDurationMs()}",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }

            Button(
                onClick  = { onStart(selectedKey!!, qty) },
                enabled  = !isStarting && qty > 0 && maxQty > 0 && !(hasActiveSession && isQueueFull),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                if (isStarting) CircularProgressIndicator(Modifier.size(20.dp))
                else Text(if (hasActiveSession) stringResource(R.string.skills_add_to_queue) else stringResource(R.string.btn_start_burying))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Runecrafting sheet
// ---------------------------------------------------------------------------

@Composable
private fun RunecraftingSheet(
    sheet: SheetState.Runecrafting,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    onStart: (String, Int) -> Unit,
) {
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val selectedRune = selectedKey?.let { sheet.availableRunes[it] }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
    ) {
        if (selectedRune == null) {
            // ── Rune type selection ──────────────────────────────────────
            Text(
                text     = stringResource(R.string.skill_runecrafting_name),
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Text(
                text     = stringResource(R.string.skill_runecrafting_desc),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            )
            Text(
                text     = stringResource(R.string.skills_essence_qty, sheet.essenceQty),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            HorizontalDivider()
            if (sheet.availableRunes.isEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = stringResource(R.string.skills_no_runes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (sheet.essenceQty == 0) {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = stringResource(R.string.skills_no_essence),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                sheet.availableRunes.forEach { (key, rune) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedKey = key }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(rune.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text  = stringResource(R.string.skills_rune_desc, rune.xpPerRune.toInt(), rune.levelRequired),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text  = stringResource(R.string.btn_select),
                            style = MaterialTheme.typography.labelMedium,
                            color = GoldPrimary,
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        } else {
            // ── Quantity picker ──────────────────────────────────────────
            val maxQty = sheet.essenceQty
            var qty by remember(selectedKey) { androidx.compose.runtime.mutableIntStateOf(maxQty.coerceAtLeast(1)) }
            var textValue by remember(selectedKey) { mutableStateOf(maxQty.coerceAtLeast(1).toString()) }

            TextButton(
                onClick  = { selectedKey = null },
                modifier = Modifier.padding(start = 4.dp),
            ) { Text(stringResource(R.string.btn_back_arrow)) }

            Text(
                text     = selectedRune.displayName,
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                text     = stringResource(R.string.skills_rune_selected, selectedRune.xpPerRune.toInt(), maxQty),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick  = { qty = (qty - 1).coerceAtLeast(1); textValue = qty.toString() },
                    enabled  = qty > 1,
                ) { Icon(Icons.Filled.Remove, contentDescription = "Decrease") }

                OutlinedTextField(
                    value         = textValue,
                    onValueChange = { new ->
                        val filtered = new.filter { it.isDigit() }
                        textValue = filtered
                        filtered.toIntOrNull()?.let { qty = it.coerceIn(1, maxQty.coerceAtLeast(1)) }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction    = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        val parsed = textValue.toIntOrNull()?.coerceIn(1, maxQty.coerceAtLeast(1)) ?: 1
                        qty = parsed; textValue = parsed.toString()
                    }),
                    textStyle  = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign  = TextAlign.Center,
                    ),
                    singleLine = true,
                    modifier   = Modifier.width(90.dp),
                )

                IconButton(
                    onClick  = { qty = (qty + 1).coerceAtMost(maxQty.coerceAtLeast(1)); textValue = qty.toString() },
                    enabled  = qty < maxQty,
                ) { Icon(Icons.Filled.Add, contentDescription = "Increase") }
            }

            Text(
                text       = stringResource(R.string.skills_xp_total, (qty * selectedRune.xpPerRune).toInt()),
                style      = MaterialTheme.typography.bodyMedium,
                color      = GoldPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (sessionDurationMs > 0) {
                Text(
                    text     = "~${(qty.toLong() * (sessionDurationMs / 60)).formatDurationMs()}",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }

            Button(
                onClick  = { onStart(selectedKey!!, qty) },
                enabled  = !isStarting && qty > 0 && maxQty > 0 && !(hasActiveSession && isQueueFull),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                if (isStarting) CircularProgressIndicator(Modifier.size(20.dp))
                else Text(if (hasActiveSession) stringResource(R.string.skills_add_to_queue) else stringResource(R.string.btn_start_crafting))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Session result sheet
// ---------------------------------------------------------------------------

@Composable
private fun SessionResultSheet(
    result: SessionResult,
    context: android.content.Context,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
        Text(
            text     = stringResource(R.string.label_session_results),
            style    = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text  = GameStrings.skillName(context, result.skillName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        // XP gained
        ResultRow(
            label = stringResource(R.string.label_xp_gained),
            value = "+${result.xpGained.formatXp()} XP",
            valueColor = GoldPrimary,
        )

        // Level ups
        if (result.levelUps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text  = stringResource(R.string.label_level_ups),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            result.levelUps.forEach { lvl ->
                Text(
                    text  = "  " + stringResource(R.string.skills_level_reached, lvl),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GoldPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Items collected
        if (result.itemsGained.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text  = stringResource(R.string.label_items_collected),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            result.itemsGained.entries
                .sortedByDescending { it.value }
                .forEach { (key, qty) ->
                    ResultRow(
                        label      = GameStrings.itemName(context, key),
                        value      = "×$qty",
                        valueColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.btn_close))
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
// Crafting skill sheet (Smithing / Cooking / Fletching / Jewelry)
// Shown inline when tapping a crafting skill row on the Skills screen.
// ---------------------------------------------------------------------------

@Composable
private fun CraftSkillSheet(
    skillName: String,
    craftState: CraftingUiState,
    craftingViewModel: CraftingViewModel,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    context: android.content.Context,
    onDismiss: () -> Unit,
) {
    val allRecipes: List<CraftableRecipe> = when (skillName) {
        Skills.SMITHING  -> craftingViewModel.smithingRecipes
        Skills.COOKING   -> craftingViewModel.cookingRecipes
        Skills.FLETCHING -> craftingViewModel.fletchingRecipes
        Skills.HERBLORE  -> craftingViewModel.herbloreRecipes
        else             -> craftingViewModel.jewelleryRecipes
    }

    var onlyCraftable    by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedTier     by remember { mutableStateOf<String?>(null) }

    val categories = remember(allRecipes) {
        allRecipes.map { it.category }.filter { it.isNotEmpty() }.distinct().sorted()
    }
    val categoryFiltered = if (selectedCategory == null) allRecipes
                           else allRecipes.filter { it.category == selectedCategory }
    val tiers = remember(categoryFiltered) {
        categoryFiltered.map { it.tier }.filter { it.isNotEmpty() }.distinct().sorted()
    }
    val recipes = categoryFiltered
        .filter { selectedTier == null || it.tier == selectedTier }
        .let { list ->
            if (onlyCraftable) list.filter { craftState.meetsLevel(it) && craftState.maxCraftable(it) > 0 }
            else list
        }

    val selected = craftState.selectedRecipe

    if (selected != null) {
        CraftQuantityContent(
            recipe            = selected,
            state             = craftState,
            hasActiveSession  = hasActiveSession,
            isQueueFull       = isQueueFull,
            sessionDurationMs = sessionDurationMs,
            context           = context,
            onSetQuantity     = { craftingViewModel.setQuantity(it, craftState.maxCraftable(selected)) },
            onCraft           = craftingViewModel::craft,
            onBack            = craftingViewModel::dismissRecipe,
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = GameStrings.skillName(context, skillName),
                    style    = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text  = stringResource(R.string.skills_only_craftable),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked         = onlyCraftable,
                    onCheckedChange = { onlyCraftable = it },
                )
            }
            HorizontalDivider()
            Text(
                text     = GameStrings.skillDesc(context, skillName),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )
            if (categories.size > 1) {
                Row(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected  = selectedCategory == null,
                        onClick   = { selectedCategory = null; selectedTier = null },
                        label     = { Text(stringResource(R.string.skills_filter_all)) },
                    )
                    categories.forEach { cat ->
                        FilterChip(
                            selected  = selectedCategory == cat,
                            onClick   = {
                                selectedCategory = if (selectedCategory == cat) null else cat
                                selectedTier = null
                            },
                            label     = { Text(cat) },
                        )
                    }
                }
            }
            if (tiers.size > 1) {
                Row(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected  = selectedTier == null,
                        onClick   = { selectedTier = null },
                        label     = { Text(stringResource(R.string.skills_filter_all)) },
                    )
                    tiers.forEach { tier ->
                        FilterChip(
                            selected  = selectedTier == tier,
                            onClick   = { selectedTier = if (selectedTier == tier) null else tier },
                            label     = { Text(tier) },
                        )
                    }
                }
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                items(recipes) { recipe ->
                    CraftRecipeRow(
                        recipe     = recipe,
                        craftState = craftState,
                        context    = context,
                        onTap      = { craftingViewModel.openRecipe(recipe) },
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun CraftRecipeRow(
    recipe: CraftableRecipe,
    craftState: CraftingUiState,
    context: android.content.Context,
    onTap: () -> Unit,
) {
    val meetsLvl = craftState.meetsLevel(recipe)
    val canMake  = craftState.maxCraftable(recipe)
    val enabled  = meetsLvl && canMake > 0
    val dim      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text       = recipe.displayName,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = if (enabled) MaterialTheme.colorScheme.onSurface else dim,
            )
            val matText = recipe.materials.entries.joinToString("  ") { (item, qty) ->
                "${GameStrings.itemName(context, item)} ${craftState.inventory[item] ?: 0}/$qty"
            }
            Text(
                text  = matText,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else dim,
            )
            val statParts = buildList {
                if (recipe.outputAttackBonus   > 0) add("+${recipe.outputAttackBonus} Atk")
                if (recipe.outputStrengthBonus > 0) add("+${recipe.outputStrengthBonus} Str")
                if (recipe.outputDefenseBonus  > 0) add("+${recipe.outputDefenseBonus} Def")
                if (recipe.outputHealingValue  > 0) add("Heals ${recipe.outputHealingValue} HP")
                if (recipe.outputDamage        > 0) add("+${recipe.outputDamage} dmg")
            }
            if (statParts.isNotEmpty()) {
                Text(
                    text  = statParts.joinToString("  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else dim,
                )
            }
            if (recipe.effects.isNotEmpty()) {
                Text(
                    text  = recipe.effects.entries.joinToString("  ") { (stat, bonus) ->
                        "+$bonus ${stat.replaceFirstChar { it.uppercase() }}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) MaterialTheme.colorScheme.primary else dim,
                )
            }
            if (recipe.outputRequirements.isNotEmpty()) {
                recipe.outputRequirements.forEach { (skill, lvl) ->
                    val have       = craftState.skillLevels[skill] ?: 1
                    val skillLabel = GameStrings.skillName(context, skill)
                    Text(
                        text  = stringResource(R.string.skills_req_with_have, lvl, skillLabel, have, skillLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (have >= lvl) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            when {
                !meetsLvl  -> Text(
                    text  = "Lv. ${recipe.levelRequired}",
                    style = MaterialTheme.typography.labelSmall,
                    color = dim,
                )
                canMake > 0 -> {
                    Text(
                        text       = "×$canMake",
                        style      = MaterialTheme.typography.labelMedium,
                        color      = GoldPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text  = "${recipe.xpPerItem.toInt()} XP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> Text(
                    text  = "No mats",
                    style = MaterialTheme.typography.labelSmall,
                    color = dim,
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun CraftQuantityContent(
    recipe: CraftableRecipe,
    state: CraftingUiState,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    context: android.content.Context,
    onSetQuantity: (Int) -> Unit,
    onCraft: () -> Unit,
    onBack: () -> Unit,
) {
    val qty     = state.craftQuantity
    val max     = state.maxCraftable(recipe)
    val totalXp = recipe.xpPerItem * qty
    var textValue by remember(qty) { mutableStateOf(qty.toString()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.btn_back_arrow)) }
        Text(
            text       = recipe.displayName,
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))

        Text(
            text  = stringResource(R.string.label_ingredients),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        recipe.materials.forEach { (item, perItem) ->
            val needed = perItem * qty
            val have   = state.inventory[item] ?: 0
            Row(
                modifier              = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(GameStrings.itemName(context, item), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text  = "$needed (have $have)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (have >= needed) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onSetQuantity(qty - 1) }, enabled = qty > 1) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease")
            }
            OutlinedTextField(
                value         = textValue,
                onValueChange = { new ->
                    val filtered = new.filter { it.isDigit() }
                    textValue = filtered
                    filtered.toIntOrNull()?.let { onSetQuantity(it) }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction    = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val parsed = textValue.toIntOrNull()?.coerceIn(1, max.coerceAtLeast(1)) ?: 1
                        onSetQuantity(parsed)
                        textValue = parsed.toString()
                    },
                ),
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                ),
                singleLine = true,
                modifier   = Modifier.width(90.dp),
            )
            IconButton(onClick = { onSetQuantity(qty + 1) }, enabled = qty < max) {
                Icon(Icons.Filled.Add, contentDescription = "Increase")
            }
        }
        Text(
            text     = stringResource(R.string.skills_xp_total, totalXp.toInt()),
            style    = MaterialTheme.typography.bodySmall,
            color    = GoldPrimary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (sessionDurationMs > 0) {
            Text(
                text     = "~${(qty.toLong() * (sessionDurationMs / 60)).formatDurationMs()}",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick  = onCraft,
            enabled  = !(hasActiveSession && isQueueFull),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (hasActiveSession) stringResource(R.string.skills_add_to_queue) else stringResource(R.string.btn_craft))
        }
    }
}
