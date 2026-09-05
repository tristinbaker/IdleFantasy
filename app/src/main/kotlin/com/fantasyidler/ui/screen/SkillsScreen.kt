package com.fantasyidler.ui.screen

import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.activity.compose.BackHandler
import androidx.compose.material3.ModalBottomSheetProperties
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.dropUnlessResumed
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.ui.viewmodel.ExpeditionsViewModel
import com.fantasyidler.data.model.Skills
import com.fantasyidler.ui.theme.ScaledSheetContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.fantasyidler.ui.screen.MercantileSheetContent
import com.fantasyidler.ui.viewmodel.CraftingViewModel
import com.fantasyidler.ui.viewmodel.SheetQuestSource
import com.fantasyidler.ui.viewmodel.SheetQuestSummary
import com.fantasyidler.ui.viewmodel.SheetState
import com.fantasyidler.ui.viewmodel.SkillsUiState
import com.fantasyidler.ui.viewmodel.SkillsViewModel
import com.fantasyidler.ui.viewmodel.xpProgressFraction
import com.fantasyidler.ui.viewmodel.nextLevelThreshold
import com.fantasyidler.ui.viewmodel.xpToNextLevel
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.toTitleCase
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.toCountdown
import java.util.Locale
import com.fantasyidler.ui.viewmodel.QuestCategory
import com.fantasyidler.ui.viewmodel.QuestIndicator

private val NON_COMBAT_PRESTIGE_SKILLS = Skills.GATHERING + Skills.CRAFTING_SKILLS + Skills.SUPPORT + listOf(Skills.SLAYER)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    openSkill: String? = null,
    onNavigateToSlayer: () -> Unit = {},
    onNavigateToBoneAltar: () -> Unit = {},
    onNavigateToPrestige: (String) -> Unit = {},
    viewModel: SkillsViewModel       = hiltViewModel(),
    craftingViewModel: CraftingViewModel = hiltViewModel(),
    expeditionsViewModel: ExpeditionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val craftSnackState by craftingViewModel.uiState.collectAsState()
    val context = LocalContext.current

    AppBannerEffect(state.snackbarMessage, viewModel::snackbarConsumed)
    AppBannerEffect(craftSnackState.snackbarMessage, craftingViewModel::snackbarConsumed)

    // onSkillTapped reads uiState.value synchronously, so deep-linking here must wait for isLoading to show correct skill level.
    var openedInitialSkill by remember { mutableStateOf(false) }
    LaunchedEffect(openSkill, state.isLoading) {
        if (openSkill != null && !state.isLoading && !openedInitialSkill) {
            openedInitialSkill = true
            viewModel.onSkillTapped(openSkill)
        }
    }

    var showLegend by remember { mutableStateOf(false) }
    if (showLegend) {
        AlertDialog(
            onDismissRequest = { showLegend = false },
            title = { Text(stringResource(R.string.quest_legend_title)) },
            text  = {
                val seasonalEmoji = state.seasonalEventEmoji ?: QuestCategory.SEASONAL.emoji
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        QuestCategory.DAILY.emoji       to R.string.quest_legend_daily,
                        QuestCategory.WEEKLY.emoji      to R.string.quest_legend_weekly,
                        seasonalEmoji                   to R.string.quest_legend_seasonal,
                        QuestCategory.GUILD_DAILY.emoji to R.string.quest_legend_guild_daily,
                        QuestCategory.GUILD.emoji       to R.string.quest_legend_guild,
                        QuestCategory.MAIN.emoji        to R.string.quest_legend_quest,
                    ).forEach { (emoji, labelRes) ->
                        Text("$emoji  ${stringResource(labelRes)}")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = stringResource(R.string.quest_legend_notes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLegend = false }) { Text(stringResource(R.string.btn_close)) }
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title   = { Text(stringResource(R.string.nav_skills)) },
                actions = {
                    // dropUnlessResumed: ignore ghost taps that land on this screen while it is
                    // fading out of a nav transition (issue #1345 — overlaps Home's settings gear)
                    IconButton(onClick = dropUnlessResumed { showLegend = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.quest_legend_title))
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

        var savedPage by rememberSaveable { mutableIntStateOf(0) }
        val pagerState = rememberPagerState(initialPage = savedPage, pageCount = { 2 })
        LaunchedEffect(Unit) {
            if (pagerState.currentPage != savedPage) pagerState.scrollToPage(savedPage)
        }
        LaunchedEffect(pagerState.currentPage) { savedPage = pagerState.currentPage }
        val scope = rememberCoroutineScope()
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                val prestigeReadyCount = if (!state.showPrestigeNotifications) 0
                    else NON_COMBAT_PRESTIGE_SKILLS.count { it in state.prestigeReadySkills }
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick  = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text     = {
                        Text(
                            if (prestigeReadyCount > 0)
                                stringResource(R.string.tab_label_with_count, stringResource(R.string.nav_skills), prestigeReadyCount)
                            else
                                stringResource(R.string.nav_skills)
                        )
                    },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick  = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text     = { Text(stringResource(R.string.nav_expeditions)) },
                )
            }
            val skillsListState = rememberLazyListState()
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                if (page == 1) {
                    ExpeditionsScreen(viewModel = expeditionsViewModel, showTitle = false)
                } else {
                    SkillsTabContent(
                        state                 = state,
                        viewModel             = viewModel,
                        context               = context,
                        listState             = skillsListState,
                        onNavigateToSlayer    = onNavigateToSlayer,
                        onNavigateToBoneAltar = onNavigateToBoneAltar,
                        onNavigateToPrestige  = onNavigateToPrestige,
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
        onNavigateToPrestige  = onNavigateToPrestige,
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
    onNavigateToPrestige: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    state.sheetSkill?.let { sheet ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // Sheets with an internal quantity page register how to step back one level here, so
        // the system back button matches the in-sheet back link instead of closing the whole
        // sheet (issue #1330). Back press and scrim tap fire onDismissRequest while the sheet
        // is still visible; a swipe-down has already settled hidden and always closes.
        val sheetBackStep = remember { mutableStateOf<(() -> Unit)?>(null) }
        ModalBottomSheet(
            onDismissRequest = {
                val stepBack = sheetBackStep.value
                if (sheetState.isVisible && stepBack != null) {
                    stepBack()
                } else {
                    viewModel.dismissSheet()
                    craftingViewModel.dismissRecipe()
                }
            },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        ) {
            // With shouldDismissOnBackPress = false the sheet's own callback never consumes back
            // (the predictive-back gesture used to settle the sheet hidden and close it whole,
            // issue #1469), so this handler owns every back press: step back one level when a
            // sheet registered an inner page, close the whole sheet otherwise.
            BackHandler {
                val stepBack = sheetBackStep.value
                if (stepBack != null) stepBack() else {
                    viewModel.dismissSheet()
                    craftingViewModel.dismissRecipe()
                }
            }
            // Rendered by each sheet under its skill description; only the sheets without
            // a description header (Mercantile, Farming) show it above their content.
            val dailyBanner: @Composable () -> Unit = {
                GuildDailySheetBanner(
                    sheet             = sheet,
                    guildDailies      = state.sheetQuests,
                    onOpenPrestige    = { skill ->
                        viewModel.dismissSheet()
                        craftingViewModel.dismissRecipe()
                        onNavigateToPrestige(skill)
                    },
                    onQueueDaily      = { daily ->
                        val remaining = (daily.amount - daily.progress).coerceAtLeast(1)
                        val craftGuilds = setOf(
                            Skills.SMITHING, Skills.COOKING, Skills.FLETCHING,
                            Skills.CRAFTING, Skills.HERBLORE, Skills.CONSTRUCTION,
                        )
                        if (daily.type == "craft" && daily.guild in craftGuilds) {
                            craftingViewModel.queueCraftForDaily(daily.target, remaining)
                        } else {
                            viewModel.queueDailySession(daily)
                        }
                    },
                )
            }
            if (sheet is SheetState.Mercantile || sheet is SheetState.Farming) {
                ScaledSheetContent { dailyBanner() }
            }
            ScaledSheetContent {
                when (sheet) {
                    is SheetState.Mining -> MiningSheet(
                        guildDailyButton  = dailyBanner,
                        ores              = sheet.ores,
                        isStarting        = state.startingSession,
                        hasActiveSession  = state.anySessionActive,
                        isQueueFull       = state.queueSize >= state.maxQueueSize,
                        sessionDurationMs = state.sessionDurationMs,
                        currentXp         = state.skillXp[Skills.MINING] ?: 0L,
                        efficiency        = state.miningEfficiency,
                        petBoostPct       = state.petBoosts[Skills.MINING] ?: 0,
                        xpBonusMult       = state.xpBonusMult,
                        activeQuests      = state.activeQuests,
                        inventory         = state.inventory,
                        onSelect          = { oreKey -> viewModel.startMiningSession(oreKey) },
                    )
                    is SheetState.Woodcutting -> WoodcuttingSheet(
                        guildDailyButton  = dailyBanner,
                        trees             = sheet.trees,
                        isStarting        = state.startingSession,
                        hasActiveSession  = state.anySessionActive,
                        isQueueFull       = state.queueSize >= state.maxQueueSize,
                        sessionDurationMs = state.sessionDurationMs,
                        currentXp         = state.skillXp[Skills.WOODCUTTING] ?: 0L,
                        efficiency        = state.woodcuttingEfficiency,
                        petBoostPct       = state.petBoosts[Skills.WOODCUTTING] ?: 0,
                        xpBonusMult       = state.xpBonusMult,
                        activeQuests      = state.activeQuests,
                        inventory         = state.inventory,
                        onSelect          = { treeKey -> viewModel.startWoodcuttingSession(treeKey) },
                    )
                    is SheetState.Fishing -> FishingSheet(
                        guildDailyButton  = dailyBanner,
                        fish              = sheet.fish,
                        isStarting        = state.startingSession,
                        hasActiveSession  = state.anySessionActive,
                        isQueueFull       = state.queueSize >= state.maxQueueSize,
                        sessionDurationMs = state.sessionDurationMs,
                        currentXp         = state.skillXp[Skills.FISHING] ?: 0L,
                        efficiency        = state.fishingEfficiency,
                        petBoostPct       = state.petBoosts[Skills.FISHING] ?: 0,
                        xpBonusMult       = state.xpBonusMult,
                        activeQuests      = state.activeQuests,
                        inventory         = state.inventory,
                        onSelect          = { fishKey -> viewModel.startFishingSession(fishKey) },
                    )
                    is SheetState.Agility -> AgilitySheet(
                        guildDailyButton  = dailyBanner,
                        courses           = sheet.courses,
                        isStarting        = state.startingSession,
                        hasActiveSession  = state.anySessionActive,
                        isQueueFull       = state.queueSize >= state.maxQueueSize,
                        sessionDurationMs = state.sessionDurationMs,
                        currentXp         = state.skillXp[Skills.AGILITY] ?: 0L,
                        efficiency        = state.agilityEfficiency,
                        petBoostPct       = state.petBoosts[Skills.AGILITY] ?: 0,
                        xpBonusMult       = state.xpBonusMult,
                        activeQuests      = state.activeQuests,
                        onSelect          = { courseKey -> viewModel.startAgilitySession(courseKey) },
                    )
                    is SheetState.Firemaking -> FiremakingSheet(
                        guildDailyButton  = dailyBanner,
                        backStep          = sheetBackStep,
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
                        guildDailyButton  = dailyBanner,
                        backStep          = sheetBackStep,
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
                        guildDailyButton  = dailyBanner,
                        backStep          = sheetBackStep,
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
                            guildDailyButton  = dailyBanner,
                            backStep          = sheetBackStep,
                            skillName         = sheet.skillName,
                            craftState        = craftState,
                            craftingViewModel = craftingViewModel,
                            hasActiveSession  = state.anySessionActive,
                            sessionDurationMs = state.sessionDurationMs,
                            context           = context,
                            onDismiss         = {
                                viewModel.dismissSheet()
                                craftingViewModel.dismissRecipe()
                            },
                        )
                    }
                    is SheetState.Thieving -> ThievingSheet(
                        guildDailyButton  = dailyBanner,
                        npcs              = sheet.npcs,
                        thievingLevel     = state.skillLevels[Skills.THIEVING] ?: 1,
                        currentXp         = state.skillXp[Skills.THIEVING] ?: 0L,
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

/**
 * Compact guild-daily status line shown above a skill sheet's activity list, so the
 * player can see whether this skill still has a daily worth doing without leaving
 * for the Guild Hall. Hidden while the skill's guild is locked (no daily assigned).
 */
/** Quest types the sheet's quick-add "+" can turn into a queued session. */
private fun SheetQuestSummary.canQueue(): Boolean = when {
    type == "gather" && guild in setOf(Skills.MINING, Skills.WOODCUTTING, Skills.FISHING) -> true
    type == "pickpocket"                       -> true
    type == "sessions" && guild == Skills.AGILITY -> true
    type == "craft"                            -> true
    else                                       -> false
}

@Composable
private fun GuildDailySheetBanner(
    sheet: SheetState,
    guildDailies: Map<String, List<SheetQuestSummary>>,
    onOpenPrestige: (String) -> Unit,
    onQueueDaily: (SheetQuestSummary) -> Unit,
) {
    val skillKey = when (sheet) {
        is SheetState.Mining       -> Skills.MINING
        is SheetState.Woodcutting  -> Skills.WOODCUTTING
        is SheetState.Fishing      -> Skills.FISHING
        is SheetState.Agility      -> Skills.AGILITY
        is SheetState.Firemaking   -> Skills.FIREMAKING
        is SheetState.Runecrafting -> Skills.RUNECRAFTING
        is SheetState.Prayer       -> Skills.PRAYER
        is SheetState.Crafting     -> sheet.skillName
        is SheetState.Thieving     -> Skills.THIEVING
        SheetState.Mercantile      -> Skills.MERCANTILE
        SheetState.Farming         -> Skills.FARMING
        SheetState.ComingSoon      -> null
    } ?: return
    val quests = guildDailies[skillKey] ?: emptyList()
    val context = LocalContext.current
    val guildMaxed = quests.any { it.source == SheetQuestSource.GUILD && it.guildMaxed }
    val anyOpen = quests.any { !it.claimed && !(it.source == SheetQuestSource.GUILD && it.guildMaxed) }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        val sections = listOf(
            SheetQuestSource.GUILD    to R.string.guild_daily_button,
            SheetQuestSource.DAILY    to R.string.label_daily,
            SheetQuestSource.WEEKLY   to R.string.label_weekly,
            SheetQuestSource.SEASONAL to R.string.seasonal_bounty_board_title,
        ).mapNotNull { (source, labelRes) ->
            quests.filter { it.source == source }.takeIf { it.isNotEmpty() }?.let { labelRes to it }
        }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.nav_quests)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    sections.forEachIndexed { sectionIndex, (labelRes, sectionQuests) ->
                        if (sectionIndex > 0) {
                            Spacer(Modifier.height(12.dp))
                        }
                        Text(
                            text       = stringResource(labelRes),
                            style      = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        sectionQuests.forEachIndexed { index, quest ->
                            if (index > 0) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider()
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text       = when (quest.source) {
                                            SheetQuestSource.SEASONAL -> GameStrings.seasonalBountyName(context, quest.questId, quest.questName)
                                            else                      -> GameStrings.questName(context, quest.questId, quest.questName)
                                        },
                                        style      = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text  = when (quest.source) {
                                            SheetQuestSource.GUILD    -> localizedQuestDesc(quest.type, quest.target, quest.amount, quest.guild)
                                            SheetQuestSource.DAILY    -> buildDailyObjective(context, quest.guild, quest.target, quest.amount, quest.description)
                                            SheetQuestSource.WEEKLY   -> GameStrings.questDesc(context, quest.questId)
                                                .takeIf { it.isNotBlank() } ?: quest.description
                                            SheetQuestSource.SEASONAL -> GameStrings.seasonalBountyHint(context, quest.questId, quest.description)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text  = when {
                                            quest.claimed                  -> stringResource(R.string.guild_daily_banner_claimed)
                                            quest.progress >= quest.amount -> stringResource(R.string.guild_daily_banner_claimable)
                                            else                           -> "${quest.progress} / ${quest.amount}"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (!quest.claimed && quest.progress < quest.amount && quest.canQueue() && quest.meetsLevel) {
                                    IconButton(onClick = {
                                        onQueueDaily(quest)
                                        showDialog = false
                                    }) {
                                        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                    if (guildMaxed) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text  = stringResource(R.string.guild_daily_rank_maxed),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            },
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showDialog = true }, enabled = quests.isNotEmpty()) {
                Text(
                    text  = stringResource(R.string.nav_quests),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (anyOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (guildMaxed) {
                Text(
                    text     = stringResource(R.string.guild_daily_rank_maxed),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        TextButton(onClick = { onOpenPrestige(skillKey) }) {
            Text(
                text  = stringResource(R.string.prestige_skill_tree),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
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
    context: Context,
    listState: LazyListState = rememberLazyListState(),
    onNavigateToSlayer: () -> Unit = {},
    onNavigateToBoneAltar: () -> Unit = {},
    onNavigateToPrestige: (String) -> Unit = {},
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        state.activeSession?.let { session ->
            item(key = "active_session") {
                ActiveSessionBanner(
                    skillName     = GameStrings.skillName(context, session.skillName),
                    activityLabel = when (session.skillName) {
                        "combat"      -> GameStrings.dungeonName(context, session.activityKey)
                        "boss"        -> GameStrings.bossName(context, session.activityKey)
                        "expedition"  -> GameStrings.skillingDungeonName(context, session.activityKey, session.activityKey.toTitleCase())
                        "mercantile"  -> GameStrings.tradeRouteName(context, session.activityKey)
                        "agility"     -> GameStrings.agilityCourse(context, session.activityKey)
                        "woodcutting" -> GameStrings.treeName(context, session.activityKey)
                        "thieving"    -> GameStrings.thievingNpcName(context, session.activityKey)
                        "tower"       -> context.getString(R.string.tower_title) + ": " + context.getString(
                            R.string.tower_floor_label,
                            session.activityKey.removePrefix("tower_floor_").toIntOrNull() ?: 0,
                        )
                        else          -> GameStrings.itemName(context, session.activityKey)
                    }.takeIf { session.activityKey.isNotEmpty() },
                    endsAt        = session.endsAt,
                    completed     = session.completed,
                    showEndTime   = state.showSessionEndTime,
                    onAbandon     = viewModel::abandonSession,
                    onDebugFinish = viewModel::debugFinishSession,
                )
            }
        }

        item(key = "header_gathering") { SectionHeader(stringResource(R.string.label_gathering_skills)) }
        items(Skills.GATHERING.filter { it != Skills.AGILITY }, key = { "gather_$it" }) { key ->
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
                onOpenPrestige = { onNavigateToPrestige(key) },
                cropsReady     = if (key == Skills.FARMING) state.cropsReadyCount else 0,
                questIndicators = state.timedQuestsBySkill[key] ?: emptyList(),
            )
        }

        item(key = "header_crafting") { SectionHeader(stringResource(R.string.label_crafting_skills)) }
        items(Skills.CRAFTING_SKILLS, key = { "craft_$it" }) { key ->
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
                onOpenPrestige = { onNavigateToPrestige(key) },
                questIndicators = state.timedQuestsBySkill[key] ?: emptyList(),
            )
        }

        item(key = "header_support") { SectionHeader(stringResource(R.string.label_support_skills)) }
        items(Skills.SUPPORT + listOf(Skills.AGILITY), key = { "support_$it" }) { key ->
            SkillRow(
                skillKey       = key,
                level          = state.skillLevels[key] ?: 1,
                xp             = state.skillXp[key] ?: 0L,
                isActive       = state.activeSession?.skillName == key && state.activeSession?.completed == false,
                onClick        = { viewModel.onSkillTapped(key) },
                toolEfficiency = if (key == Skills.AGILITY) state.agilityEfficiency else 1.0f,
                petBoostPct    = state.petBoostBySkill[key] ?: 0,
                prestigeLevel  = state.skillPrestige[key] ?: 0,
                onOpenPrestige = { onNavigateToPrestige(key) },
                questIndicators = state.timedQuestsBySkill[key] ?: emptyList(),
            )
        }

        item(key = "header_combat") { SectionHeader(stringResource(R.string.label_combat)) }
        item(key = "combat_${Skills.SLAYER}") {
            SkillRow(
                skillKey      = Skills.SLAYER,
                level         = state.skillLevels[Skills.SLAYER] ?: 1,
                xp            = state.skillXp[Skills.SLAYER] ?: 0L,
                isActive      = false,
                onClick       = onNavigateToSlayer,
                petBoostPct   = state.petBoostBySkill[Skills.SLAYER] ?: 0,
                prestigeLevel = state.skillPrestige[Skills.SLAYER] ?: 0,
                onOpenPrestige = { onNavigateToPrestige(Skills.SLAYER) },
                questIndicators = state.timedQuestsBySkill[Skills.SLAYER] ?: emptyList(),
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
    onOpenPrestige: (() -> Unit)? = null,
    cropsReady: Int = 0,
    /** Timed quest indicators (daily/weekly/guild daily) shown next to the skill name. */
    questIndicators: List<QuestIndicator> = emptyList(),
) {
    val context  = LocalContext.current
    val name     = GameStrings.skillName(context, skillKey)
    val emoji    = GameStrings.skillEmoji(skillKey)
    val progress = xpProgressFraction(xp)

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
                            if (isActive) MaterialTheme.colorScheme.primary
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.weight(1f, fill = false),
                    ) {
                        Text(
                            text       = name,
                            style      = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier   = Modifier.weight(1f, fill = false),
                        )
                        QuestIndicatorIcons(questIndicators)
                    }
                    if (isActive) {
                        Text(
                            text  = stringResource(R.string.label_training),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
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
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color    = MaterialTheme.colorScheme.primary,
                )
                if (toolEfficiency > 1.0f || petBoostPct > 0) {
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth()) {
                        if (toolEfficiency > 1.0f) {
                            Text(
                                text     = stringResource(R.string.skills_tool_bonus, "%.2f".format(toolEfficiency)),
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.CenterStart),
                            )
                        }
                        if (petBoostPct > 0) {
                            Text(
                                text     = stringResource(R.string.skills_pet_bonus, petBoostPct),
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.align(Alignment.CenterEnd),
                            )
                        }
                    }
                }
                if (prestigeLevel > 0 || (onOpenPrestige != null && level >= 99)) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text  = "★×$prestigeLevel",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (onOpenPrestige != null) {
                            Text(
                                text     = stringResource(R.string.prestige),
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(role = Role.Button) { onOpenPrestige() }
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
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

