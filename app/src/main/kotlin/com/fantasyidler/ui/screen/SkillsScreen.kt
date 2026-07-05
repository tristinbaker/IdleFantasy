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
                    questFills            = sheet.questFills,
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
                petBoostPct    = state.petBoostBySkill[key] ?: 0,
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
                petBoostPct   = state.petBoostBySkill[key] ?: 0,
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
                petBoostPct   = state.petBoostBySkill[key] ?: 0,
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
                petBoostPct   = state.petBoostBySkill[Skills.SLAYER] ?: 0,
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
    petBoostPct: Int = 0,
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
                if (petBoostPct > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text  = stringResource(R.string.skills_pet_bonus, petBoostPct),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
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

