package com.fantasyidler.ui.screen.profile

import com.fantasyidler.ui.theme.LocalTabSwipeEnabled
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.ui.components.PlayerStatsBar
import com.fantasyidler.ui.screen.AppBannerEffect
import com.fantasyidler.ui.screen.CharacterCustomizationSheet
import com.fantasyidler.ui.screen.CharacterSetupSheet
import com.fantasyidler.ui.screen.DebugModifyAmountSheet
import com.fantasyidler.ui.screen.EquipPickerSheet
import com.fantasyidler.ui.screen.EquipmentTab
import com.fantasyidler.ui.screen.TitleOption
import com.fantasyidler.ui.theme.ScaledSheetContent
import com.fantasyidler.ui.viewmodel.AchievementsViewModel
import com.fantasyidler.ui.viewmodel.profile.ArmoryViewModel
import com.fantasyidler.ui.viewmodel.profile.BestiaryViewModel
import com.fantasyidler.ui.viewmodel.TitleCatalog
import com.fantasyidler.ui.viewmodel.InventoryViewModel
import com.fantasyidler.ui.viewmodel.SettingsViewModel
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.util.GameStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel:           InventoryViewModel    = hiltViewModel(),
    achievementsVm:      AchievementsViewModel = hiltViewModel(),
    bestiaryVm:          BestiaryViewModel     = hiltViewModel(),
    armoryVm:            ArmoryViewModel       = hiltViewModel(),
    settingsVm:          SettingsViewModel     = hiltViewModel(),
    onNavigateToCombat:  () -> Unit            = {},
    onNavigateToPrestige: (String) -> Unit      = {},
) {
    val state         by viewModel.uiState.collectAsState()
    val achState      by achievementsVm.uiState.collectAsState()
    val profileLayout by settingsVm.profileLayout.collectAsState()
    val context   = LocalContext.current
    AppBannerEffect(state.snackbarMessage, viewModel::snackbarConsumed)
    val tabs = listOf(
        stringResource(R.string.label_skills),
        stringResource(R.string.label_inventory),
        stringResource(R.string.label_equipment),
        stringResource(R.string.label_pets),
        stringResource(R.string.label_achievements),
        stringResource(R.string.label_notes),
        stringResource(R.string.label_bestiary),
        stringResource(R.string.armory_tab),
        stringResource(R.string.tab_bonuses),
        stringResource(R.string.label_banners),
    )
    var selectedTab  by rememberSaveable { mutableIntStateOf(0) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var showAddItemSheet by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar       = { TopAppBar(title = { Text(stringResource(R.string.nav_profile)) }) },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Character identity header ────────────────────────────────
            Surface(
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text       = state.displayName.ifBlank { stringResource(R.string.profile_unnamed) },
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (state.ironman) {
                            Text(
                                text       = stringResource(R.string.ironman_badge),
                                style      = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        // Race/gender are stored as canonical English values (or free text for
                        // custom genders); map the known ones back to their localised labels.
                        val raceLabel = GameStrings.raceName(context, state.characterRace.lowercase())
                        val genderLabel = GameStrings.genderName(context, state.characterGender.lowercase())
                        val subtitle = buildString {
                            if (raceLabel.isNotBlank()) append(raceLabel)
                            if (raceLabel.isNotBlank() && genderLabel.isNotBlank()) append(" • ")
                            if (genderLabel.isNotBlank()) append(genderLabel)
                        }
                        if (subtitle.isNotBlank()) {
                            Text(
                                text  = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { showAppearanceSheet = true }) {
                        Icon(
                            imageVector        = Icons.Filled.Person,
                            contentDescription = stringResource(R.string.appearance_title),
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { showEditSheet = true }) {
                        Icon(
                            imageVector        = Icons.Filled.Edit,
                            contentDescription = "Edit character",
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Surface(
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                PlayerStatsBar(
                    context                   = context,
                    combatLevel               = combatLevelFrom(state.skillLevels),
                    totalLevel                = state.totalLevel,
                    coins                     = state.coins,
                    compactNumbers            = state.compactNumbers,
                    activeBlessingKey         = state.activeBlessingKey,
                    allBlessings              = state.allBlessings,
                    prayerCapeMult            = state.prayerCapeMult,
                    activeBlessingRemainingMs = (state.activeBlessingExpiresAt - System.currentTimeMillis()).coerceAtLeast(0L),
                    xpBoostRemainingMs        = if (state.ironman) 0L else (state.xpBoostExpiresAt - System.currentTimeMillis()).coerceAtLeast(0L),
                    prestigeBoostsRemainingMs = state.prestigeXpBoosts
                        .mapValues { (it.value - System.currentTimeMillis()).coerceAtLeast(0L) }
                        .filterValues { it > 0L },
                    modifier                  = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                )
            }

            val tabContent: @Composable (Int) -> Unit = { tab ->
                when (tab) {
                    0    -> SkillsTab(
                        skillLevels    = state.skillLevels,
                        skillXp        = state.skillXp,
                        context        = context,
                        viewModel      = viewModel,
                        skillPrestige  = state.skillPrestige,
                        prestigeUnspent = state.prestigeUnspentBySkill,
                        onOpenPrestige  = onNavigateToPrestige,
                        elderIsleUnlocked = state.elderIsleUnlocked,
                        elderSkillLevels  = state.elderSkillLevels,
                        elderSkillXp      = state.elderSkillXp,
                    )
                    1    -> InventoryTab(state.inventory, context, viewModel::categoryFor, viewModel::openAncientTreasures) { showAddItemSheet = true }
                    2    -> EquipmentTab(
                        equipped           = state.equipped,
                        allEquipment       = state.resolvedEquipment(viewModel.allEquipment),
                        heirloomXp         = state.heirloomXp,
                        context            = context,
                        onSlotTap          = viewModel::openSlotPicker,
                        onUnequip          = viewModel::unequip,
                        onEquipBestTools   = viewModel::equipBestTools,
                        onNavigateToCombat = onNavigateToCombat,
                    )
                    3    -> PetsTab(allPets = viewModel.allPets, ownedPetIds = state.ownedPetIds, elderIsleUnlocked = state.elderIsleUnlocked)
                    4    -> AchievementsTab(achState.byGroup, achState.unlockedCount, achState.totalCount)
                    5    -> NotesTab(
                        skillingDungeons     = viewModel.allSkillingDungeons,
                        skillingDungeonNotes = state.skillingDungeonNotes,
                        unlockedDungeons     = state.unlockedDungeons,
                    )
                    7    -> ArmoryTab(viewModel = armoryVm)
                    8    -> BonusesTab(state, viewModel.allEquipment, viewModel.allPets)
                    9    -> BannersTab(state.seasonalBanners)
                    else -> BestiaryTab(viewModel = bestiaryVm)
                }
            }

            if (profileLayout == "tabs") {
                TabsLayout(
                    tabs        = tabs,
                    selectedTab = selectedTab,
                    onTabSelect = { selectedTab = it },
                    modifier    = Modifier.weight(1f),
                    content     = tabContent,
                )
            } else {
                RailLayout(
                    tabs        = tabs,
                    selectedTab = selectedTab,
                    onTabSelect = { selectedTab = it },
                    modifier    = Modifier.weight(1f),
                    content     = tabContent,
                )
            }
        }
    }

    // Equip-picker sheet
    state.pickingSlot?.let { slot ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissSlotPicker,
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            ScaledSheetContent {
                EquipPickerSheet(
                    slot       = slot,
                    candidates = state.candidatesFor(slot, state.resolvedEquipment(viewModel.allEquipment)),
                    context    = context,
                    heirloomXp = state.heirloomXp,
                    onEquip    = { itemKey -> viewModel.equip(itemKey, slot) },
                    onDismiss  = viewModel::dismissSlotPicker,
                )
            }
        }
    }

    // Character appearance sheet
    if (showAppearanceSheet) {
        CharacterCustomizationSheet(
            race              = state.characterRace,
            ironman           = state.ironman,
            ironmanRaceLocked = state.ironmanRaceLocked,
            raceChangeTokens  = state.raceChangeTokens,
            raceCooldownRemainingMs = (state.raceLastChangedAt +
                PlayerRepository.RACE_CHANGE_COOLDOWN_MS -
                System.currentTimeMillis()).coerceAtLeast(0L),
            coins             = state.coins,
            raceProficiencies = viewModel.raceProficiencies,
            initialSkin       = state.characterSkinTone,
            initialHair       = state.characterHairStyle,
            initialHairColor  = state.characterHairColor,
            initialEye        = state.characterEyeStyle,
            initialBeard      = state.characterBeardStyle,
            initialBeardColor = state.characterBeardColor,
            onSave            = { skin, hair, hairColor, eye, beard, beardColor, race, useToken ->
                viewModel.saveAppearance(skin, hair, hairColor, eye, beard, beardColor, race, useToken)
                showAppearanceSheet = false
            },
            onDismiss         = { showAppearanceSheet = false },
        )
    }

    // Character edit sheet
    if (showEditSheet) {
        val staticTitleOptions = TitleCatalog.ALL.map { t ->
            TitleOption(
                id          = t.id,
                name        = stringResource(t.nameRes),
                requirement = stringResource(t.requirementRes),
                unlocked    = t.id in state.unlockedTitles,
            )
        }
        // One Title per event series: "Champion of Gloomharvest" is the same title whichever
        // year earned it. Banners are newest-first, so this prefers the equipped run, then the
        // newest earned run, then the newest locked one.
        val seasonalTitleOptions = state.seasonalBanners.groupBy { it.series }.values.map { runs ->
            runs.firstOrNull { "seasonal_${it.eventId}" == state.equippedTitle }
                ?: runs.firstOrNull { it.earned }
                ?: runs.first()
        }.map { b ->
            TitleOption(
                id          = "seasonal_${b.eventId}",
                name        = stringResource(R.string.title_seasonal_champion_of, b.titleName),
                requirement = stringResource(R.string.title_seasonal_requirement, b.titleName),
                unlocked    = b.earned,
            )
        }
        CharacterSetupSheet(
            raceProficiencies = viewModel.raceProficiencies,
            isFirstTime      = false,
            initialName      = state.characterName,
            initialGender    = state.characterGender,
            initialRace      = state.characterRace,
            titles           = staticTitleOptions + seasonalTitleOptions,
            equippedTitleId  = state.equippedTitle,
            onEquipTitle     = viewModel::equipTitle,
            onSave        = { name, gender, race, _ ->
                viewModel.saveCharacterProfile(name, gender, race)
                showEditSheet = false
            },
            onDismiss     = { showEditSheet = false },
        )
    }

    if (BuildConfig.DEBUG && showAddItemSheet) {
        DebugModifyAmountSheet(
            onAddAmount    = { itemId, amount ->
                viewModel.debugAddItem(itemId, amount.toInt())
                showAddItemSheet = false
            },
            onSetAmount    = { itemId, amount ->
                viewModel.debugSetItem(itemId, amount.toInt())
                showAddItemSheet = false
            },
            onRemoveAmount = { itemId, amount ->
                viewModel.debugRemoveItem(itemId, amount.toInt())
                showAddItemSheet = false
            },
            onDismiss      = { showAddItemSheet = false },
        )
    }
}

// ---------------------------------------------------------------------------
// Profile layout composables
// ---------------------------------------------------------------------------

@Composable
private fun RailLayout(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    Row(modifier) {
        Column(
            Modifier
                .width(110.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .verticalScroll(rememberScrollState()),
        ) {
            tabs.forEachIndexed { index, title ->
                val selected = selectedTab == index
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTabSelect(index) }
                        .then(
                            if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                            else Modifier
                        )
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                ) {
                    Text(
                        text       = title,
                        style      = MaterialTheme.typography.bodySmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color      = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                     else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            content(selectedTab)
        }
    }
}

@Composable
private fun TabsLayout(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = selectedTab) { tabs.size }
    val scope = rememberCoroutineScope()
    // Keeps the hoisted selectedTab in sync with swipes, so switching to the
    // rail layout lands on the same tab.
    LaunchedEffect(pagerState.currentPage) { onTabSelect(pagerState.currentPage) }
    Column(modifier) {
        ScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 0.dp) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick  = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text     = { Text(title) },
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxSize(), userScrollEnabled = LocalTabSwipeEnabled.current) { page ->
            content(page)
        }
    }
}

