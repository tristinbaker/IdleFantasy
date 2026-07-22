package com.fantasyidler.ui.screen

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.fantasyidler.ui.theme.ScaledSheetContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.style.TextAlign
import com.fantasyidler.ui.viewmodel.CraftableRecipe
import com.fantasyidler.ui.viewmodel.CraftingUiState
import com.fantasyidler.ui.viewmodel.CraftingViewModel
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
    val context = LocalContext.current

    AppBannerEffect(state.snackbarMessage, viewModel::snackbarConsumed)
    AppBannerEffect(craftSnackState.snackbarMessage, craftingViewModel::snackbarConsumed)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_skills)) })
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        var savedPage by rememberSaveable { mutableIntStateOf(0) }
        val pagerState = rememberPagerState(initialPage = savedPage, pageCount = { 2 })
        LaunchedEffect(Unit) {
            if (pagerState.currentPage != savedPage) pagerState.scrollToPage(savedPage)
        }
        LaunchedEffect(pagerState.currentPage) { savedPage = pagerState.currentPage }
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


    // Activity selection bottom sheet
    SkillActivitySheet(
        viewModel             = viewModel,
        craftingViewModel     = craftingViewModel,
        onNavigateToBoneAltar = onNavigateToBoneAltar,
    )

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

/**
 * Renders the active [SkillsUiState.sheetSkill] as a modal bottom sheet, if any.
 * Shared between [SkillsScreen] and [SeasonalEventScreen] so a Bounty Board "Go" tap
 * can open the same activity picker inline, without navigating to a different screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillActivitySheet(
    viewModel: SkillsViewModel,
    craftingViewModel: CraftingViewModel,
    onNavigateToBoneAltar: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
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
            ScaledSheetContent {
            // Scrolling a list to its top/bottom and continuing the gesture otherwise leaks
            // the leftover motion into the sheet's own drag-to-dismiss handling, closing it
            // mid-scroll (issue #1123). Swallowing that residual delta here still leaves the
            // drag handle itself free to dismiss the sheet on a deliberate swipe.
            val swallowResidualScroll = remember {
                object : NestedScrollConnection {
                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
                }
            }
            Box(Modifier.fillMaxSize().nestedScroll(swallowResidualScroll)) {
                when (sheet) {
                    is SheetState.Mining -> MiningSheet(
                        ores              = sheet.ores,
                        isStarting        = state.startingSession,
                        hasActiveSession  = state.anySessionActive,
                        isQueueFull       = state.queueSize >= state.maxQueueSize,
                        sessionDurationMs = state.sessionDurationMs,
                        currentXp         = state.skillXp[Skills.MINING] ?: 0L,
                        efficiency        = state.miningEfficiency,
                        xpBonusMult       = state.xpBonusMult,
                        activeQuests      = state.activeQuests,
                        onSelect          = { oreKey -> viewModel.startMiningSession(oreKey) },
                    )
                    is SheetState.Woodcutting -> WoodcuttingSheet(
                        trees             = sheet.trees,
                        isStarting        = state.startingSession,
                        hasActiveSession  = state.anySessionActive,
                        isQueueFull       = state.queueSize >= state.maxQueueSize,
                        sessionDurationMs = state.sessionDurationMs,
                        currentXp         = state.skillXp[Skills.WOODCUTTING] ?: 0L,
                        efficiency        = state.woodcuttingEfficiency,
                        xpBonusMult       = state.xpBonusMult,
                        activeQuests      = state.activeQuests,
                        onSelect          = { treeKey -> viewModel.startWoodcuttingSession(treeKey) },
                    )
                    is SheetState.Fishing -> FishingSheet(
                        fish              = sheet.fish,
                        isStarting        = state.startingSession,
                        hasActiveSession  = state.anySessionActive,
                        isQueueFull       = state.queueSize >= state.maxQueueSize,
                        sessionDurationMs = state.sessionDurationMs,
                        currentXp         = state.skillXp[Skills.FISHING] ?: 0L,
                        efficiency        = state.fishingEfficiency,
                        xpBonusMult       = state.xpBonusMult,
                        activeQuests      = state.activeQuests,
                        onSelect          = { fishKey -> viewModel.startFishingSession(fishKey) },
                    )
                    is SheetState.Agility -> AgilitySheet(
                        courses           = sheet.courses,
                        isStarting        = state.startingSession,
                        hasActiveSession  = state.anySessionActive,
                        isQueueFull       = state.queueSize >= state.maxQueueSize,
                        sessionDurationMs = state.sessionDurationMs,
                        currentXp         = state.skillXp[Skills.AGILITY] ?: 0L,
                        xpBonusMult       = state.xpBonusMult,
                        activeQuests      = state.activeQuests,
                        onSelect          = { courseKey -> viewModel.startAgilitySession(courseKey) },
                    )
                    is SheetState.Firemaking -> FiremakingSheet(
                        availableLogs     = sheet.availableLogs,
                        inventory         = state.inventory,
                        currentXp         = state.skillXp[Skills.FIREMAKING] ?: 0L,
                        isStarting        = state.startingSession,
                        hasActiveSession  = state.anySessionActive,
                        isQueueFull       = state.queueSize >= state.maxQueueSize,
                        sessionDurationMs = state.sessionDurationMs,
                        perLogMs          = state.firemakingPerLogMs,
                        onStart           = { logKey, qty -> viewModel.startFiremakingSession(logKey, qty) },
                        context           = context,
                        questFills        = sheet.questFills,
                        activeQuests      = state.activeQuests,
                    )
                    is SheetState.Runecrafting -> RunecraftingSheet(
                        sheet             = sheet,
                        inventory         = state.inventory,
                        isStarting        = state.startingSession,
                        hasActiveSession  = state.anySessionActive,
                        isQueueFull       = state.queueSize >= state.maxQueueSize,
                        sessionDurationMs = state.sessionDurationMs,
                        onStart           = { runeKey, qty, ashKey -> viewModel.startRunecraftingSession(runeKey, qty, ashKey) },
                        currentXp         = state.skillXp[Skills.RUNECRAFTING] ?: 0L,
                        questFills        = sheet.questFills,
                        activeQuests      = state.activeQuests,
                    )
                    is SheetState.Prayer -> PrayerSheet(
                        availableBones        = sheet.availableBones,
                        inventory             = sheet.inventory,
                        prayerLevel           = state.skillLevels[Skills.PRAYER] ?: 1,
                        currentXp             = state.skillXp[Skills.PRAYER] ?: 0L,
                        isStarting            = state.startingSession,
                        hasActiveSession      = state.anySessionActive,
                        isQueueFull           = state.queueSize >= state.maxQueueSize,
                        sessionDurationMs     = state.sessionDurationMs,
                        onStart               = viewModel::startPrayerSession,
                        onNavigateToBoneAltar = {
                            viewModel.dismissSheet()
                            onNavigateToBoneAltar()
                        },
                        questFills            = sheet.questFills,
                        activeQuests          = state.activeQuests,
                    )
                    is SheetState.Crafting -> {
                        val craftState by craftingViewModel.uiState.collectAsState()
                        CraftSkillSheet(
                            skillName         = sheet.skillName,
                            craftState        = craftState,
                            craftingViewModel = craftingViewModel,
                            hasActiveSession  = state.anySessionActive,
                            isQueueFull       = state.queueSize >= state.maxQueueSize,
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
                        isQueueFull       = state.queueSize >= state.maxQueueSize,
                        sessionDurationMs = state.sessionDurationMs,
                        context           = context,
                        activeQuests      = state.activeQuests,
                        onSelect          = { npcKey -> viewModel.startThievingSession(npcKey) },
                    )
                    SheetState.Mercantile -> MercantileSheetContent(onDismiss = viewModel::dismissSheet)
                    SheetState.Farming   -> FarmingSheetContent(onDismiss = viewModel::dismissSheet)
                    SheetState.ComingSoon -> ComingSoonSheet()
                }
            }
            }
        }
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
                    showEndTime   = state.showSessionEndTime,
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
                Skills.THIEVING    -> state.thievingEfficiency
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
            val craftEfficiency = when (key) {
                Skills.SMITHING   -> state.smithingEfficiency
                Skills.FIREMAKING -> state.firemakingEfficiency
                Skills.COOKING    -> state.cookingEfficiency
                else              -> 1.0f
            }
            SkillRow(
                skillKey       = key,
                level          = state.skillLevels[key] ?: 1,
                xp             = state.skillXp[key] ?: 0L,
                isActive       = state.activeSession?.skillName == key && state.activeSession?.completed == false,
                onClick        = { viewModel.onSkillTapped(key) },
                toolEfficiency = craftEfficiency,
                petBoostPct    = state.petBoostBySkill[key] ?: 0,
                prestigeLevel  = state.skillPrestige[key] ?: 0,
                onPrestige     = { viewModel.prestigeSkill(key) },
            )
        }

        item { SectionHeader(stringResource(R.string.label_support_skills)) }
        items(Skills.SUPPORT + listOf(Skills.AGILITY)) { key ->
            SkillRow(
                skillKey       = key,
                level          = state.skillLevels[key] ?: 1,
                xp             = state.skillXp[key] ?: 0L,
                isActive       = state.activeSession?.skillName == key && state.activeSession?.completed == false,
                onClick        = { viewModel.onSkillTapped(key) },
                toolEfficiency = if (key == Skills.AGILITY) state.agilityEfficiency else 1.0f,
                petBoostPct    = state.petBoostBySkill[key] ?: 0,
                prestigeLevel  = state.skillPrestige[key] ?: 0,
                onPrestige     = { viewModel.prestigeSkill(key) },
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
    showEndTime: Boolean = true,
    onAbandon: () -> Unit,
    onDebugFinish: () -> Unit = {},
) {
    val context = LocalContext.current
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
                    text  = remember(now, showEndTime) { endsAt.toCountdown(context, showEndTime) },
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
                Text(
                    text  = stringResource(R.string.worker_manage_from_home),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
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
        val message = when (skillKey) {
            Skills.AGILITY -> {
                val currentMinutes = (SkillSimulator.sessionDurationMs(99, prestigeLevel) / 60_000L).toInt()
                val nextMinutes    = (SkillSimulator.sessionDurationMs(99, nextPrestige) / 60_000L).toInt()
                stringResource(R.string.prestige_confirm_message_agility, name, nextPrestige * 10, nextMinutes, currentMinutes)
            }
            Skills.MERCANTILE -> stringResource(R.string.prestige_confirm_message_mercantile, name, nextPrestige * 10)
            else -> stringResource(R.string.prestige_confirm_message_xp, name, nextPrestige * 10)
        }
        AlertDialog(
            onDismissRequest = { showPrestigeConfirm = false },
            title = { Text(stringResource(R.string.prestige_confirm_title, name)) },
            text  = { Text(message) },
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
            // Icon badge with level overlay
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
                    val iconRes = GameStrings.skillIconRes(skillKey)
                    if (iconRes != null) {
                        Image(
                            painter            = painterResource(iconRes),
                            contentDescription = null,
                            modifier           = Modifier.size(28.dp),
                        )
                    } else {
                        Text(
                            text  = emoji,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
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


// ---------------------------------------------------------------------------
// Crafting skill sheet (Smithing / Cooking / Fletching / Jewelry)
// Shown inline when tapping a crafting skill row on the Skills screen.
// ---------------------------------------------------------------------------

