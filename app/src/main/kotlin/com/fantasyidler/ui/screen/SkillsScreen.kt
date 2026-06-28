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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    onNavigateToSlayer: () -> Unit = {},
    onNavigateToBoneAltar: () -> Unit = {},
    viewModel: SkillsViewModel       = hiltViewModel(),
    craftingViewModel: CraftingViewModel = hiltViewModel(),
    expeditionsViewModel: ExpeditionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val craftSnackState by craftingViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            try { snackbarHostState.showSnackbar(msg) }
            finally { viewModel.snackbarConsumed() }
        }
    }

    LaunchedEffect(craftSnackState.snackbarMessage) {
        craftSnackState.snackbarMessage?.let { msg ->
            try { snackbarHostState.showSnackbar(msg) }
            finally { craftingViewModel.snackbarConsumed() }
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

        val pagerState = rememberPagerState(pageCount = { 2 })
        val scope = rememberCoroutineScope()
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick  = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text     = { Text(stringResource(R.string.nav_skills)) },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick  = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text     = { Text(stringResource(R.string.nav_expeditions)) },
                )
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                if (page == 1) {
                    ExpeditionsScreen(viewModel = expeditionsViewModel, showTitle = false)
                } else {
                    SkillsTabContent(
                        state                 = state,
                        viewModel             = viewModel,
                        context               = context,
                        onNavigateToSlayer    = onNavigateToSlayer,
                        onNavigateToBoneAltar = onNavigateToBoneAltar,
                    )
                }
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
                    currentXp         = state.skillXp[Skills.MINING] ?: 0L,
                    efficiency        = state.miningEfficiency,
                    xpBonusMult       = state.xpBonusMult,
                    onSelect          = { oreKey -> viewModel.startMiningSession(oreKey) },
                )
                is SheetState.Woodcutting -> WoodcuttingSheet(
                    trees             = sheet.trees,
                    isStarting        = state.startingSession,
                    hasActiveSession  = state.anySessionActive,
                    isQueueFull       = state.queueSize >= 3,
                    sessionDurationMs = state.sessionDurationMs,
                    currentXp         = state.skillXp[Skills.WOODCUTTING] ?: 0L,
                    efficiency        = state.woodcuttingEfficiency,
                    xpBonusMult       = state.xpBonusMult,
                    onSelect          = { treeKey -> viewModel.startWoodcuttingSession(treeKey) },
                )
                is SheetState.Fishing -> FishingSheet(
                    fish              = sheet.fish,
                    isStarting        = state.startingSession,
                    hasActiveSession  = state.anySessionActive,
                    isQueueFull       = state.queueSize >= 3,
                    sessionDurationMs = state.sessionDurationMs,
                    currentXp         = state.skillXp[Skills.FISHING] ?: 0L,
                    efficiency        = state.fishingEfficiency,
                    xpBonusMult       = state.xpBonusMult,
                    onSelect          = { fishKey -> viewModel.startFishingSession(fishKey) },
                )
                is SheetState.Agility -> AgilitySheet(
                    courses           = sheet.courses,
                    isStarting        = state.startingSession,
                    hasActiveSession  = state.anySessionActive,
                    isQueueFull       = state.queueSize >= 3,
                    sessionDurationMs = state.sessionDurationMs,
                    currentXp         = state.skillXp[Skills.AGILITY] ?: 0L,
                    xpBonusMult       = state.xpBonusMult,
                    onSelect          = { courseKey -> viewModel.startAgilitySession(courseKey) },
                )
                is SheetState.Firemaking -> FiremakingSheet(
                    availableLogs     = sheet.availableLogs,
                    inventory         = state.inventory,
                    currentXp         = state.skillXp[Skills.FIREMAKING] ?: 0L,
                    isStarting        = state.startingSession,
                    hasActiveSession  = state.anySessionActive,
                    isQueueFull       = state.queueSize >= 3,
                    sessionDurationMs = state.sessionDurationMs,
                    onStart           = { logKey, qty -> viewModel.startFiremakingSession(logKey, qty) },
                    context           = context,
                    questFills        = sheet.questFills,
                )
                is SheetState.Runecrafting -> RunecraftingSheet(
                    sheet             = sheet,
                    inventory         = state.inventory,
                    isStarting        = state.startingSession,
                    hasActiveSession  = state.anySessionActive,
                    isQueueFull       = state.queueSize >= 3,
                    sessionDurationMs = state.sessionDurationMs,
                    onStart           = { runeKey, qty, ashKey -> viewModel.startRunecraftingSession(runeKey, qty, ashKey) },
                    currentXp         = state.skillXp[Skills.RUNECRAFTING] ?: 0L,
                    questFills        = sheet.questFills,
                )
                is SheetState.Prayer -> PrayerSheet(
                    availableBones        = sheet.availableBones,
                    inventory             = sheet.inventory,
                    prayerLevel           = state.skillLevels[Skills.PRAYER] ?: 1,
                    currentXp             = state.skillXp[Skills.PRAYER] ?: 0L,
                    isStarting            = state.startingSession,
                    hasActiveSession      = state.anySessionActive,
                    isQueueFull           = state.queueSize >= 3,
                    sessionDurationMs     = state.sessionDurationMs,
                    onStart               = viewModel::startPrayerSession,
                    onNavigateToBoneAltar = {
                        viewModel.dismissSheet()
                        onNavigateToBoneAltar()
                    },
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
                is SheetState.Thieving -> ThievingSheet(
                    npcs              = sheet.npcs,
                    thievingLevel     = state.skillLevels[com.fantasyidler.data.model.Skills.THIEVING] ?: 1,
                    currentXp         = state.skillXp[com.fantasyidler.data.model.Skills.THIEVING] ?: 0L,
                    isStarting        = state.startingSession,
                    hasActiveSession  = state.anySessionActive,
                    isQueueFull       = state.queueSize >= 3,
                    sessionDurationMs = state.sessionDurationMs,
                    context           = context,
                    onSelect          = { npcKey -> viewModel.startThievingSession(npcKey) },
                )
                SheetState.Mercantile -> MercantileSheetContent(onDismiss = viewModel::dismissSheet)
                SheetState.Farming   -> FarmingSheetContent(onDismiss = viewModel::dismissSheet)
                SheetState.ComingSoon -> ComingSoonSheet()
            }
        }
    }

    state.petFoundName?.let { petName ->
        AlertDialog(
            onDismissRequest = viewModel::petDialogConsumed,
            title = { Text(stringResource(R.string.pet_found_title)) },
            text  = { Text(stringResource(R.string.home_found_pet, petName)) },
            confirmButton = {
                TextButton(onClick = viewModel::petDialogConsumed) {
                    Text(stringResource(R.string.btn_close))
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Skills tab content (page 0 of the Skills/Expeditions pager)
// ---------------------------------------------------------------------------

@Composable
private fun SkillsTabContent(
    state: SkillsUiState,
    viewModel: SkillsViewModel,
    context: android.content.Context,
    onNavigateToSlayer: () -> Unit = {},
    onNavigateToBoneAltar: () -> Unit = {},
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        state.activeSession?.let { session ->
            item {
                ActiveSessionBanner(
                    skillName     = GameStrings.skillName(context, session.skillName),
                    activityLabel = when (session.skillName) {
                        "combat"     -> GameStrings.dungeonName(context, session.activityKey)
                        "boss"       -> GameStrings.bossName(context, session.activityKey)
                        "expedition" -> GameStrings.skillingDungeonName(context, session.activityKey, session.activityKey.toTitleCase())
                        else         -> GameStrings.itemName(context, session.activityKey)
                    }.takeIf { session.activityKey.isNotEmpty() },
                    endsAt        = session.endsAt,
                    completed     = session.completed,
                    onCollect     = viewModel::collectSession,
                    onAbandon     = viewModel::abandonSession,
                    onDebugFinish = viewModel::debugFinishSession,
                )
            }
        }

        item { SectionHeader(stringResource(R.string.label_gathering_skills)) }
        items(Skills.GATHERING.filter { it != Skills.AGILITY }) { key ->
            val efficiency = when (key) {
                Skills.MINING      -> state.miningEfficiency
                Skills.WOODCUTTING -> state.woodcuttingEfficiency
                Skills.FISHING     -> state.fishingEfficiency
                Skills.FARMING     -> state.farmingEfficiency
                else               -> 1.0f
            }
            SkillRow(
                skillKey       = key,
                level          = state.skillLevels[key] ?: 1,
                xp             = state.skillXp[key] ?: 0L,
                isActive       = state.activeSession?.skillName == key && state.activeSession?.completed == false,
                onClick        = { viewModel.onSkillTapped(key) },
                toolEfficiency = efficiency,
                prestigeLevel  = state.skillPrestige[key] ?: 0,
                onPrestige     = { viewModel.prestigeSkill(key) },
                cropsReady     = if (key == Skills.FARMING) state.cropsReadyCount else 0,
            )
        }

        item { SectionHeader(stringResource(R.string.label_crafting_skills)) }
        items(Skills.CRAFTING_SKILLS) { key ->
            SkillRow(
                skillKey      = key,
                level         = state.skillLevels[key] ?: 1,
                xp            = state.skillXp[key] ?: 0L,
                isActive      = state.activeSession?.skillName == key && state.activeSession?.completed == false,
                onClick       = { viewModel.onSkillTapped(key) },
                prestigeLevel = state.skillPrestige[key] ?: 0,
                onPrestige    = { viewModel.prestigeSkill(key) },
            )
        }

        item { SectionHeader(stringResource(R.string.label_support_skills)) }
        items(Skills.SUPPORT + listOf(Skills.AGILITY)) { key ->
            SkillRow(
                skillKey      = key,
                level         = state.skillLevels[key] ?: 1,
                xp            = state.skillXp[key] ?: 0L,
                isActive      = state.activeSession?.skillName == key && state.activeSession?.completed == false,
                onClick       = { viewModel.onSkillTapped(key) },
                prestigeLevel = state.skillPrestige[key] ?: 0,
                onPrestige    = { viewModel.prestigeSkill(key) },
            )
        }

        item { SectionHeader(stringResource(R.string.label_combat)) }
        item {
            SkillRow(
                skillKey      = Skills.SLAYER,
                level         = state.skillLevels[Skills.SLAYER] ?: 1,
                xp            = state.skillXp[Skills.SLAYER] ?: 0L,
                isActive      = false,
                onClick       = onNavigateToSlayer,
                prestigeLevel = state.skillPrestige[Skills.SLAYER] ?: 0,
                onPrestige    = { viewModel.prestigeSkill(Skills.SLAYER) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Active session banner
// ---------------------------------------------------------------------------

@Composable
private fun ActiveSessionBanner(
    skillName: String,
    activityLabel: String?,
    endsAt: Long,
    completed: Boolean,
    onCollect: () -> Unit,
    onAbandon: () -> Unit,
    onDebugFinish: () -> Unit = {},
) {
    // Tick every second so the countdown stays live.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showAbandonConfirm by remember { mutableStateOf(false) }
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
                    if (activityLabel != null) {
                        append(" — ")
                        append(activityLabel)
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
                    TextButton(onClick = { showAbandonConfirm = true }) {
                        Text(stringResource(R.string.btn_abandon_session))
                    }

                    if (showAbandonConfirm) {
                        AlertDialog(
                            onDismissRequest = { showAbandonConfirm = false },
                            title = { Text(stringResource(R.string.session_abandon_title)) },
                            text  = { Text(stringResource(R.string.session_abandon_body)) },
                            confirmButton = {
                                TextButton(onClick = { showAbandonConfirm = false; onAbandon() }) {
                                    Text(stringResource(R.string.btn_confirm))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showAbandonConfirm = false }) {
                                    Text(stringResource(R.string.btn_cancel))
                                }
                            },
                        )
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
internal fun SkillRow(
    skillKey: String,
    level: Int,
    xp: Long,
    isActive: Boolean,
    onClick: () -> Unit,
    toolEfficiency: Float = 1.0f,
    prestigeLevel: Int = 0,
    onPrestige: (() -> Unit)? = null,
    cropsReady: Int = 0,
) {
    val context  = LocalContext.current
    val name     = GameStrings.skillName(context, skillKey)
    val emoji    = GameStrings.skillEmoji(skillKey)
    val progress = xpProgressFraction(xp)
    var showPrestigeConfirm by remember { mutableStateOf(false) }

    if (showPrestigeConfirm) {
        val nextPrestige = prestigeLevel + 1
        AlertDialog(
            onDismissRequest = { showPrestigeConfirm = false },
            title = { Text(stringResource(R.string.prestige_confirm_title, name)) },
            text  = { Text(stringResource(R.string.prestige_confirm_message_xp, name, nextPrestige * 10)) },
            confirmButton = {
                TextButton(onClick = {
                    showPrestigeConfirm = false
                    onPrestige?.invoke()
                }) { Text(stringResource(R.string.prestige)) }
            },
            dismissButton = {
                TextButton(onClick = { showPrestigeConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }

    Column(Modifier.fillMaxWidth()) {
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
                if (cropsReady > 0) {
                    Badge(modifier = Modifier.align(Alignment.TopEnd))
                }
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
                        val xpText = if (xpToNextLevel(xp) > 0L)
                            "${xp.formatXp()} / ${nextLevelThreshold(xp).formatXp()} XP"
                        else
                            "${xp.formatXp()} XP"
                        Text(
                            text  = xpText,
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

        // Prestige section: stars and button, outside the clickable row
        if (prestigeLevel > 0 || (onPrestige != null && level >= 99)) {
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(start = 72.dp, end = 16.dp, bottom = 6.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text  = "★".repeat(prestigeLevel) + "☆".repeat((3 - prestigeLevel).coerceAtLeast(0)),
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldPrimary,
                )
                when {
                    onPrestige != null && level >= 99 && prestigeLevel < 3 -> {
                        TextButton(onClick = { showPrestigeConfirm = true }) {
                            Text(
                                text  = stringResource(R.string.prestige),
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldPrimary,
                            )
                        }
                    }
                    prestigeLevel >= 3 -> {
                        Text(
                            text  = stringResource(R.string.prestige_max),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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

private fun projectedXpLabel(currentXp: Long, xpGain: Long): String {
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

@Composable
internal fun AgilitySheet(
    courses: Map<String, AgilityCourseData>,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    currentXp: Long = 0L,
    xpBonusMult: Float = 1f,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val currentAgilityLevel = XpTable.levelForXp(currentXp)
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
                    val xpGain = (SkillSimulator.estimateAgilityXp(course.xpPerSuccess, course.levelRequired, currentAgilityLevel) * xpBonusMult).toLong()
                    ActivityRow(
                        name             = course.displayName,
                        detail           = context.getString(R.string.skills_agility_course_detail, course.levelRequired, course.xpPerSuccess),
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
internal fun FiremakingSheet(
    availableLogs: Map<String, LogData>,
    inventory: Map<String, Int>,
    currentXp: Long,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    onStart: (logKey: String, qty: Int) -> Unit,
    context: android.content.Context,
    craftLimit: Int = Int.MAX_VALUE,
    questFills: Map<String, List<QuestFillSuggestion>> = emptyMap(),
) {
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val selectedLog = selectedKey?.let { availableLogs[it] }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
    ) {
        if (selectedLog == null) {
            Text(
                text     = stringResource(R.string.skill_firemaking_name),
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Text(
                text     = stringResource(R.string.skill_firemaking_desc),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            HorizontalDivider()
            if (availableLogs.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.skills_no_logs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    availableLogs.entries.sortedBy { it.value.levelRequired }.forEach { (key, log) ->
                        val ashName = GameStrings.itemName(context, when (key) {
                            "oak_log" -> "oak_ashes"; "willow_log" -> "willow_ashes"
                            "maple_log" -> "maple_ashes"; "yew_log" -> "yew_ashes"
                            "magic_log" -> "magic_ashes"; "redwood_log" -> "redwood_ashes"
                            else -> "ashes"
                        })
                        Row(
                            modifier          = Modifier.fillMaxWidth().clickable { selectedKey = key }.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(GameStrings.itemName(context, key), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text  = stringResource(R.string.firemaking_burns_to, ashName) + "  •  ${log.xpPerLog} XP",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text  = "${inventory[key] ?: 0} ${stringResource(R.string.firemaking_logs_in_inventory)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        } else {
            val key      = selectedKey!!
            val maxQty   = (inventory[key] ?: 0).coerceAtMost(craftLimit)
            var qty      by remember(key) { androidx.compose.runtime.mutableIntStateOf(maxQty.coerceAtLeast(1)) }
            var textValue by remember(key) { mutableStateOf(maxQty.coerceAtLeast(1).toString()) }
            val totalXp = selectedLog.xpPerLog * qty

            TextButton(onClick = { selectedKey = null }, modifier = Modifier.padding(start = 4.dp)) {
                Text(stringResource(R.string.btn_back_arrow))
            }
            Text(
                text     = GameStrings.itemName(context, key),
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                text     = "${maxQty} ${stringResource(R.string.firemaking_logs_in_inventory)}",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.IconButton(onClick = { if (qty > 1) { qty--; textValue = qty.toString() } }, enabled = qty > 1) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Remove, contentDescription = null)
                }
                androidx.compose.material3.OutlinedTextField(
                    value         = textValue,
                    onValueChange = { new ->
                        val f = new.filter { it.isDigit() }
                        textValue = f
                        f.toIntOrNull()?.coerceIn(1, maxQty.coerceAtLeast(1))?.let { qty = it }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    singleLine    = true,
                    modifier      = Modifier.width(130.dp),
                    textStyle     = MaterialTheme.typography.bodyLarge.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                )
                androidx.compose.material3.IconButton(onClick = { if (qty < maxQty) { qty++; textValue = qty.toString() } }, enabled = qty < maxQty) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Add, contentDescription = null)
                }
            }
            QtyQuickButtons(qty, maxQty) { qty = it; textValue = it.toString() }
            QuestFillRow(questFills[key] ?: emptyList(), qty, maxQty) { qty = it; textValue = it.toString() }
            Spacer(Modifier.height(8.dp))
            Text(
                text       = projectedXpLabel(currentXp, totalXp.toLong()),
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
            val enabled = !isStarting && !(!hasActiveSession && false) && (hasActiveSession || !isQueueFull.not()) && maxQty > 0
            Button(
                onClick  = { onStart(key, qty) },
                enabled  = !isStarting && maxQty > 0 && (!hasActiveSession || !isQueueFull),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) { Text(stringResource(R.string.firemaking_burn)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Prayer sheet
// ---------------------------------------------------------------------------

@Composable
internal fun PrayerSheet(
    availableBones: Map<String, BoneData>,
    inventory: Map<String, Int>,
    prayerLevel: Int,
    currentXp: Long = 0L,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    onStart: (boneKey: String, qty: Int) -> Unit,
    onNavigateToBoneAltar: () -> Unit = {},
    tierMaxQty: Int = Int.MAX_VALUE,
) {
    val context = LocalContext.current
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val selectedBone = selectedKey?.let { availableBones[it] }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(bottom = 32.dp),
    ) {
        Text(
            text     = stringResource(R.string.label_prayer),
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
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = stringResource(R.string.skills_prayer_desc, prayerLevel),
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
                TextButton(onClick = onNavigateToBoneAltar) {
                    Text(stringResource(R.string.bone_altar_open))
                }
            }
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
                            Text(GameStrings.itemName(context, key), style = MaterialTheme.typography.bodyLarge)
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
            val inventoryMax = inventory[selectedKey] ?: 0
            val maxQty = minOf(inventoryMax, tierMaxQty)
            var qty by remember(selectedKey) { androidx.compose.runtime.mutableIntStateOf(maxQty.coerceAtLeast(1)) }
            var textValue by remember(selectedKey) { mutableStateOf(maxQty.coerceAtLeast(1).toString()) }

            TextButton(
                onClick  = { selectedKey = null },
                modifier = Modifier.padding(start = 4.dp),
            ) { Text(stringResource(R.string.btn_back_arrow)) }

            Text(
                text     = GameStrings.itemName(context, selectedKey ?: ""),
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                text     = stringResource(R.string.skills_bone_selected, selectedBone.xpPerBone.toInt(), inventoryMax),
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
                        val parsed   = filtered.toIntOrNull()
                        if (parsed != null) {
                            val clamped = parsed.coerceIn(1, maxQty.coerceAtLeast(1))
                            qty = clamped; textValue = clamped.toString()
                        } else {
                            textValue = filtered
                        }
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
                    modifier     = Modifier.width(130.dp),
                )

                IconButton(
                    onClick  = { qty = (qty + 1).coerceAtMost(maxQty.coerceAtLeast(1)); textValue = qty.toString() },
                    enabled  = qty < maxQty,
                ) { Icon(Icons.Filled.Add, contentDescription = "Increase") }
            }
            Spacer(Modifier.height(8.dp))
            QtyQuickButtons(qty, maxQty) { v -> qty = v; textValue = v.toString() }
            Spacer(Modifier.height(8.dp))

            Text(
                text     = projectedXpLabel(currentXp, (qty * selectedBone.xpPerBone).toLong()),
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
internal fun RunecraftingSheet(
    sheet: SheetState.Runecrafting,
    inventory: Map<String, Int> = emptyMap(),
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    onStart: (String, Int, String?) -> Unit,
    currentXp: Long = 0L,
    tierMaxQty: Int = Int.MAX_VALUE,
    questFills: Map<String, List<QuestFillSuggestion>> = emptyMap(),
) {
    val context = LocalContext.current
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val selectedRune = selectedKey?.let { sheet.availableRunes[it] }
    var selectedAshKey by remember { mutableStateOf<String?>(null) }

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
                            Text(GameStrings.itemName(context, key), style = MaterialTheme.typography.bodyLarge)
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
            val inventoryMax = sheet.essenceQty
            val maxQty = minOf(inventoryMax, tierMaxQty)
            var qty by remember(selectedKey) { androidx.compose.runtime.mutableIntStateOf(maxQty.coerceAtLeast(1)) }
            var textValue by remember(selectedKey) { mutableStateOf(maxQty.coerceAtLeast(1).toString()) }

            TextButton(
                onClick  = { selectedKey = null },
                modifier = Modifier.padding(start = 4.dp),
            ) { Text(stringResource(R.string.btn_back_arrow)) }

            Text(
                text     = GameStrings.itemName(context, selectedKey ?: ""),
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                text     = stringResource(R.string.skills_rune_selected, selectedRune.xpPerRune.toInt(), inventoryMax),
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
                        val parsed   = filtered.toIntOrNull()
                        if (parsed != null) {
                            val clamped = parsed.coerceIn(1, maxQty.coerceAtLeast(1))
                            qty = clamped; textValue = clamped.toString()
                        } else {
                            textValue = filtered
                        }
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
                    modifier   = Modifier.width(130.dp),
                )

                IconButton(
                    onClick  = { qty = (qty + 1).coerceAtMost(maxQty.coerceAtLeast(1)); textValue = qty.toString() },
                    enabled  = qty < maxQty,
                ) { Icon(Icons.Filled.Add, contentDescription = "Increase") }
            }
            Spacer(Modifier.height(8.dp))
            QtyQuickButtons(qty, maxQty) { v -> qty = v; textValue = v.toString() }
            QuestFillRow(questFills[selectedKey ?: ""] ?: emptyList(), qty, maxQty) { v -> qty = v; textValue = v.toString() }
            Spacer(Modifier.height(8.dp))

            Text(
                text       = projectedXpLabel(currentXp, (qty * selectedRune.xpPerRune).toLong()),
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

            val ashTiers = listOf("ashes","oak_ashes","willow_ashes","maple_ashes","yew_ashes","magic_ashes","redwood_ashes")
            val availableAshes = ashTiers.filter { (inventory[it] ?: 0) >= (qty + 9) / 10 }
            if (availableAshes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.catalyst_optional), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(4.dp))
                val rcLevel = XpTable.levelForXp(currentXp)
                val rcBase = when {
                    rcLevel >= 75 -> 3
                    rcLevel >= 50 -> 2
                    else          -> 1
                }
                (listOf(null) + availableAshes).forEach { ashKey ->
                    val totalRunes = rcBase + when (ashKey) {
                        "ashes"         -> 1
                        "oak_ashes"     -> 2
                        "willow_ashes"  -> 3
                        "maple_ashes"   -> 4
                        "yew_ashes"     -> 5
                        "magic_ashes"   -> 6
                        "redwood_ashes" -> 7
                        else            -> 0
                    }
                    Row(
                        modifier          = Modifier.fillMaxWidth().clickable { selectedAshKey = ashKey }.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text  = if (ashKey == null) stringResource(R.string.catalyst_none) else GameStrings.itemName(context, ashKey),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedAshKey == ashKey) GoldPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selectedAshKey == ashKey) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        if (ashKey != null) Text(stringResource(R.string.catalyst_rune_bonus, totalRunes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Button(
                onClick  = { onStart(selectedKey!!, qty, selectedAshKey) },
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
        Skills.SMITHING      -> craftingViewModel.smithingRecipes
        Skills.COOKING       -> craftingViewModel.cookingRecipes
        Skills.FLETCHING     -> craftingViewModel.fletchingRecipes
        Skills.HERBLORE      -> craftingViewModel.herbloreRecipes
        Skills.CONSTRUCTION  -> craftingViewModel.constructionRecipes
        else                 -> craftingViewModel.jewelleryRecipes
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
        categoryFiltered.map { it.tier }.filter { it.isNotEmpty() }.distinct()
            .sortedBy { tier -> categoryFiltered.filter { it.tier == tier }.minOf { it.levelRequired } }
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
            onSetAsh          = if (selected.skillName == Skills.HERBLORE) craftingViewModel::setHerbloreAsh else null,
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
                                val newCat = if (selectedCategory == cat) null else cat
                                val newTiers = (if (newCat == null) allRecipes else allRecipes.filter { it.category == newCat })
                                    .map { it.tier }.filter { it.isNotEmpty() }.distinct()
                                selectedCategory = newCat
                                if (selectedTier != null && selectedTier !in newTiers) selectedTier = null
                            },
                            label     = { Text(GameStrings.craftingCategory(context, cat)) },
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
                            label     = { Text(GameStrings.craftingTier(context, tier)) },
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
                text       = GameStrings.itemName(context, recipe.outputKey),
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = if (enabled) MaterialTheme.colorScheme.onSurface else dim,
            )
            if (recipe.outputQty > 1) {
                Text(
                    text  = context.getString(R.string.crafting_per_craft, recipe.outputQty),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) MaterialTheme.colorScheme.primary else dim,
                )
            }
            val matText = recipe.materials.entries.joinToString("  ") { (item, qty) ->
                "${GameStrings.itemName(context, item)} ${craftState.inventory[item] ?: 0}/$qty"
            }
            Text(
                text  = matText,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else dim,
            )
            recipe.outputCombatStyle?.let { style ->
                Text(
                    text  = "${context.getString(R.string.label_combat_style)}: ${style.replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else dim,
                )
            }
            val statParts = buildList {
                if (recipe.outputAttackBonus   > 0) add("+${recipe.outputAttackBonus} ${context.getString(R.string.profile_stat_atk)}")
                if (recipe.outputStrengthBonus > 0) add("+${recipe.outputStrengthBonus} ${context.getString(R.string.profile_stat_str)}")
                if (recipe.outputDefenseBonus  > 0) add("+${recipe.outputDefenseBonus} ${context.getString(R.string.profile_stat_def)}")
                if (recipe.outputHealingValue  > 0) add(context.getString(R.string.combat_heals_hp, recipe.outputHealingValue))
                if (recipe.outputDamage        > 0) add("+${recipe.outputDamage} ${context.getString(R.string.combat_log_dmg)}")
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
                        "+$bonus ${GameStrings.skillName(context, stat)}"
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
                    text  = stringResource(R.string.label_lv, recipe.levelRequired),
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
                    text  = context.getString(R.string.crafting_no_mats),
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
    onSetAsh: ((String?) -> Unit)? = null,
    onCraft: () -> Unit,
    onBack: () -> Unit,
) {
    val qty     = state.craftQuantity
    val max     = state.maxCraftable(recipe)
    val totalXp = recipe.xpPerItem * qty
    var textValue by remember(qty) { mutableStateOf(qty.toString()) }
    val isHerblore = recipe.skillName == Skills.HERBLORE

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.btn_back_arrow)) }
        Text(
            text       = GameStrings.itemName(context, recipe.outputKey),
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
                    text  = context.getString(R.string.crafting_needed_have, needed, have),
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
                modifier   = Modifier.width(130.dp),
            )
            IconButton(onClick = { onSetQuantity(qty + 1) }, enabled = qty < max) {
                Icon(Icons.Filled.Add, contentDescription = "Increase")
            }
        }
        Spacer(Modifier.height(8.dp))
        QtyQuickButtons(qty, max) { onSetQuantity(it) }
        QuestFillRow(state.questFills, qty, max, onSetQuantity)
        Spacer(Modifier.height(8.dp))
        Text(
            text       = projectedXpLabel(state.skillXp[recipe.skillName] ?: 0L, totalXp.toLong()),
            style      = MaterialTheme.typography.bodyMedium,
            color      = GoldPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        if (sessionDurationMs > 0) {
            Text(
                text     = "~${(qty.toLong() * (sessionDurationMs / 60)).formatDurationMs()}",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        if (isHerblore && onSetAsh != null) {
            val ashTiers = listOf("ashes","oak_ashes","willow_ashes","maple_ashes","yew_ashes","magic_ashes","redwood_ashes")
            val availableAshes = ashTiers.filter { (state.inventory[it] ?: 0) >= qty }
            if (availableAshes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.catalyst_optional), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                val selectedAsh = state.herbloreAshKey
                (listOf(null) + availableAshes).forEach { ashKey ->
                    Row(
                        modifier          = Modifier.fillMaxWidth().clickable { onSetAsh(ashKey) }.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text  = if (ashKey == null) stringResource(R.string.catalyst_none) else GameStrings.itemName(context, ashKey),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedAsh == ashKey) GoldPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selectedAsh == ashKey) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        if (ashKey != null) {
                            Text(
                                text  = "×${state.inventory[ashKey] ?: 0}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (selectedAsh != null) {
                    Text(stringResource(R.string.catalyst_enhanced_output), style = MaterialTheme.typography.labelSmall, color = GoldPrimary)
                }
            }
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

// ---------------------------------------------------------------------------
// Thieving sheet
// ---------------------------------------------------------------------------

@Composable
internal fun ThievingSheet(
    npcs: Map<String, ThievingNpcData>,
    thievingLevel: Int,
    currentXp: Long,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    context: android.content.Context,
    onSelect: (String) -> Unit,
) {
    var selectedKey by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            text     = stringResource(R.string.label_choose_activity),
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            text     = stringResource(R.string.skill_thieving_desc),
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
            npcs.values
                .sortedBy { it.levelRequired }
                .forEach { npc ->
                    val successChance = ((0.40 + (thievingLevel - npc.levelRequired) * 0.02)
                        .coerceIn(0.10, 0.95) * 100).toInt()
                    val xpGain = npc.baseXp.toLong()
                    ActivityRow(
                        name             = GameStrings.thievingNpcName(context, npc.key),
                        detail           = stringResource(
                            R.string.thieving_npc_detail,
                            npc.levelRequired,
                            npc.baseXp,
                            successChance,
                        ),
                        projectedLabel   = projectedXpLabel(currentXp, xpGain),
                        isStarting       = isStarting,
                        hasActiveSession = hasActiveSession,
                        isQueueFull      = isQueueFull,
                        onClick          = { selectedKey = npc.key },
                    )
                }
        }
    }
    selectedKey?.let { key ->
        val npc = npcs[key] ?: return@let
        val successChance = ((0.40 + (thievingLevel - npc.levelRequired) * 0.02)
            .coerceIn(0.10, 0.95) * 100).toInt()
        ActivityDetailDialog(
            name             = GameStrings.thievingNpcName(context, npc.key),
            detail           = stringResource(
                R.string.thieving_npc_detail,
                npc.levelRequired,
                npc.baseXp,
                successChance,
            ),
            description      = stringResource(
                R.string.thieving_npc_coins,
                npc.coinsMin,
                npc.coinsMax,
            ),
            hasActiveSession = hasActiveSession,
            isQueueFull      = isQueueFull,
            onConfirm        = { onSelect(key) },
            onDismiss        = { selectedKey = null },
        )
    }
}
