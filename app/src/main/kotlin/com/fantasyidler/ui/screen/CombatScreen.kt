package com.fantasyidler.ui.screen

import android.R as AndroidR
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.repository.MercenaryRepository
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.CookingRecipe
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.SpellData
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import com.fantasyidler.data.model.DungeonRunStats
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.Skills
import com.fantasyidler.ui.theme.ScaledSheetContent
import com.fantasyidler.ui.viewmodel.CombatViewModel
import com.fantasyidler.ui.viewmodel.InventoryViewModel
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.ui.viewmodel.nextLevelThreshold
import com.fantasyidler.ui.viewmodel.xpProgressFraction
import com.fantasyidler.ui.viewmodel.xpToMaxLevel
import com.fantasyidler.ui.viewmodel.xpToNextLevel
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatXp


enum class CombatTabName {
    DUNGEONS,
    GEAR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CombatScreen(
    viewModel:          CombatViewModel    = hiltViewModel(),
    inventoryVm:        InventoryViewModel = hiltViewModel(),
    startingPage:       CombatTabName?     = null,
    initialDungeonKey:  String?            = null,
    initialBossKey:     String?            = null,
    onNavigateToTower:  () -> Unit         = {},
    onNavigateToPrestige: (String) -> Unit = {},
) {
    val state            by viewModel.uiState.collectAsState()
    val invState         by inventoryVm.uiState.collectAsState()
    val context           = LocalContext.current
    var showMercCamp     by remember { mutableStateOf(false) }
    val visibleDungeons   = remember(state.unlockedDungeons, viewModel.dungeonList) {
        viewModel.dungeonList.filter { !it.loreUnlockOnly || it.name in state.unlockedDungeons }
    }
    LaunchedEffect(initialDungeonKey, initialBossKey) {
        initialDungeonKey?.let { key -> viewModel.dungeonList.firstOrNull { it.name == key }?.let(viewModel::selectDungeon) }
        initialBossKey?.let { key -> viewModel.bossList(state.monumentComplete).firstOrNull { it.id == key }?.let(viewModel::selectBoss) }
    }

    AppBannerEffect(state.snackbarMessage, viewModel::snackbarConsumed)

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

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title   = { Text(stringResource(R.string.nav_combat)) },
                actions = {
                    if (!state.isLoading) {
                        Text(
                            text       = "${stringResource(R.string.combat_level_label)} ${combatLevelFrom(state.skillLevels)}",
                            style      = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.primary,
                            modifier   = Modifier.padding(end = 16.dp),
                        )
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

        val combatSession = state.combatSession
        val skillsPrestigeReadyCount = if (!state.showPrestigeNotifications) 0
            else COMBAT_SKILLS.count { it in state.prestigeReadySkills }
        val skillsTabLabel = if (skillsPrestigeReadyCount > 0)
            stringResource(R.string.tab_label_with_count, stringResource(R.string.label_skills), skillsPrestigeReadyCount)
        else
            stringResource(R.string.label_skills)
        if (combatSession != null) {
            var savedPage by rememberSaveable {
                mutableIntStateOf(when (startingPage) {
                    CombatTabName.GEAR -> 2
                    CombatTabName.DUNGEONS -> 1
                    else -> 0
                })
            }
            val pagerState = rememberPagerState(initialPage = savedPage, pageCount = { 4 })
            LaunchedEffect(Unit) {
                if (pagerState.currentPage != savedPage) pagerState.scrollToPage(savedPage)
            }
            LaunchedEffect(pagerState.currentPage) { savedPage = pagerState.currentPage }
            val scope = rememberCoroutineScope()
            Column(Modifier.padding(padding).fillMaxSize()) {
                ScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 0.dp) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick  = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text     = { Text(stringResource(R.string.combat_log_label)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick  = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text     = { Text(stringResource(R.string.label_dungeons_tab)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 2,
                        onClick  = { scope.launch { pagerState.animateScrollToPage(2) } },
                        text     = { Text(stringResource(R.string.label_combat_equipment)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 3,
                        onClick  = { scope.launch { pagerState.animateScrollToPage(3) } },
                        text     = { Text(skillsTabLabel) },
                    )
                }
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    when (page) {
                        0 -> CombatSessionBanner(
                            session        = combatSession,
                            dungeons       = visibleDungeons,
                            // Raid bosses included: the banner resolves the boss's name,
                            // emoji, and HP panel from this list.
                            bosses         = viewModel.bossList(state.monumentComplete) + viewModel.raidBossList(),
                            hiredMercs     = state.hiredMercs,
                            enemies        = viewModel.enemyMap,
                            skillLevels    = state.skillLevels,
                            hpPrestigeBonus = state.combatPrestigeBonus[Skills.HITPOINTS] ?: 0,
                            towerHpBonus   = state.towerHpBonus,
                            attackBonus    = state.totalAttackBonus,
                            strengthBonus  = state.totalStrengthBonus,
                            defenseBonus   = state.totalDefenseBonus,
                            equippedFood   = state.equippedFood,
                            foodHealValues = viewModel.foodHealValues,
                            foodEatOrder   = invState.foodEatOrder,
                            showEndTime    = state.showSessionEndTime,
                            repeatIndex    = if (combatSession.skillName == "boss") state.activeBossRepeatIndex else state.activeDungeonRepeatIndex,
                            repeatTotal    = if (combatSession.skillName == "boss") state.activeBossRepeatTotal else state.activeDungeonRepeatTotal,
                            onAbandon      = viewModel::abandonSession,
                            onDebugFinish  = viewModel::debugFinishSession,
                        )
                        1 -> CombatSelectionList(
                            dungeons            = visibleDungeons,
                            bosses              = viewModel.bossList(state.monumentComplete),
                            skillLevels         = state.skillLevels,
                            survivalRatings     = state.dungeonSurvivalRatings,
                            dungeonRuns         = state.dungeonRuns,
                            dungeonLastRunStats = state.dungeonLastRunStats,
                            unlockedDungeons    = state.unlockedDungeons,
                            towerBestFloor      = state.towerBestFloor,
                            bossKillCounts      = state.bossKillCounts,
                            isQueueFull         = state.isQueueFull,
                            raidBosses          = viewModel.raidBossList(),
                            hiredMercCount      = state.hiredMercs.size,
                            maxParty            = MercenaryRepository.MAX_PARTY,
                            onDungeon           = viewModel::selectDungeon,
                            onBoss              = viewModel::selectBoss,
                            onTower             = onNavigateToTower,
                            onOpenMercCamp      = { showMercCamp = true },
                        )
                        2 -> CombatGearTab(
                            equipped       = invState.equipped,
                            inventory      = invState.inventory,
                            equippedFood   = invState.equippedFood,
                            foodHealValues = inventoryVm.foodHealValues,
                            cookingRecipes = inventoryVm.cookingRecipes,
                            allEquipment   = invState.resolvedEquipment(inventoryVm.allEquipment),
                            heirloomXp     = invState.heirloomXp,
                            context        = context,
                            totalAttack    = state.totalAttack,
                            totalStrength  = state.totalStrength,
                            totalDefense   = state.totalDefense,
                            activeWeaponSlot    = state.selectedWeaponSlot,
                            foodEatThresholdPct = invState.foodEatThresholdPct,
                            foodEatOrder        = invState.foodEatOrder,
                            availableSpells  = viewModel.availableSpells(),
                            magicLevel       = state.skillLevels[Skills.MAGIC] ?: 1,
                            selectedArrowKey = state.selectedArrowKey,
                            selectedSpell    = state.selectedSpell,
                            onSlotTap      = inventoryVm::openSlotPicker,
                            onUnequip      = inventoryVm::unequip,
                            onEquipBest    = inventoryVm::equipBestGear,
                            onEquipFood    = inventoryVm::equipFood,
                            onUnequipFood  = inventoryVm::unequipFood,
                            onSelectStyle  = viewModel::selectWeaponSlot,
                            onArrowSelected = viewModel::selectArrow,
                            onSpellSelected = viewModel::selectSpell,
                            onFoodThresholdChanged = inventoryVm::setFoodEatThresholdPct,
                            onFoodOrderChanged     = inventoryVm::setFoodEatOrder,
                        )
                        else -> CombatSkillsTab(
                            skillLevels         = state.skillLevels,
                            skillXp             = state.skillXp,
                            totalAttackBonus    = state.totalAttackBonus,
                            totalStrengthBonus  = state.totalStrengthBonus,
                            totalDefenseBonus   = state.totalDefenseBonus,
                            skillPrestigeLevels = state.skillPrestigeLevels,
                            combatPrestigeBonus = state.combatPrestigeBonus,
                            prestigeMaxedSkills = state.prestigeMaxedSkills,
                            onOpenPrestige      = onNavigateToPrestige,
                        )
                    }
                }
            }
        } else {
            var savedPage by rememberSaveable {
                mutableIntStateOf(when (startingPage) {
                    CombatTabName.GEAR -> 1
                    else -> 0
                })
            }
            val pagerState = rememberPagerState(initialPage = savedPage, pageCount = { 3 })
            LaunchedEffect(Unit) {
                if (pagerState.currentPage != savedPage) pagerState.scrollToPage(savedPage)
            }
            LaunchedEffect(pagerState.currentPage) { savedPage = pagerState.currentPage }
            val scope = rememberCoroutineScope()
            Column(Modifier.padding(padding).fillMaxSize()) {
                TabRow(selectedTabIndex = pagerState.currentPage) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick  = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text     = { Text(stringResource(R.string.label_dungeons_tab)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick  = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text     = { Text(stringResource(R.string.label_combat_equipment)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 2,
                        onClick  = { scope.launch { pagerState.animateScrollToPage(2) } },
                        text     = { Text(skillsTabLabel) },
                    )
                }
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    when (page) {
                        0 -> CombatSelectionList(
                            dungeons            = visibleDungeons,
                            bosses              = viewModel.bossList(state.monumentComplete),
                            skillLevels         = state.skillLevels,
                            survivalRatings     = state.dungeonSurvivalRatings,
                            dungeonRuns         = state.dungeonRuns,
                            dungeonLastRunStats = state.dungeonLastRunStats,
                            unlockedDungeons    = state.unlockedDungeons,
                            towerBestFloor      = state.towerBestFloor,
                            bossKillCounts      = state.bossKillCounts,
                            isQueueFull         = state.isQueueFull,
                            raidBosses          = viewModel.raidBossList(),
                            hiredMercCount      = state.hiredMercs.size,
                            maxParty            = MercenaryRepository.MAX_PARTY,
                            onDungeon           = viewModel::selectDungeon,
                            onBoss              = viewModel::selectBoss,
                            onTower             = onNavigateToTower,
                            onOpenMercCamp      = { showMercCamp = true },
                        )
                        1 -> CombatGearTab(
                            equipped       = invState.equipped,
                            inventory      = invState.inventory,
                            equippedFood   = invState.equippedFood,
                            foodHealValues = inventoryVm.foodHealValues,
                            cookingRecipes = inventoryVm.cookingRecipes,
                            allEquipment   = invState.resolvedEquipment(inventoryVm.allEquipment),
                            heirloomXp     = invState.heirloomXp,
                            context        = context,
                            totalAttack    = state.totalAttack,
                            totalStrength  = state.totalStrength,
                            totalDefense   = state.totalDefense,
                            activeWeaponSlot    = state.selectedWeaponSlot,
                            foodEatThresholdPct = invState.foodEatThresholdPct,
                            foodEatOrder        = invState.foodEatOrder,
                            availableSpells  = viewModel.availableSpells(),
                            magicLevel       = state.skillLevels[Skills.MAGIC] ?: 1,
                            selectedArrowKey = state.selectedArrowKey,
                            selectedSpell    = state.selectedSpell,
                            onSlotTap      = inventoryVm::openSlotPicker,
                            onUnequip      = inventoryVm::unequip,
                            onEquipBest    = inventoryVm::equipBestGear,
                            onEquipFood    = inventoryVm::equipFood,
                            onUnequipFood  = inventoryVm::unequipFood,
                            onSelectStyle  = viewModel::selectWeaponSlot,
                            onArrowSelected = viewModel::selectArrow,
                            onSpellSelected = viewModel::selectSpell,
                            onFoodThresholdChanged = inventoryVm::setFoodEatThresholdPct,
                            onFoodOrderChanged     = inventoryVm::setFoodEatOrder,
                        )
                        else -> CombatSkillsTab(
                            skillLevels         = state.skillLevels,
                            skillXp             = state.skillXp,
                            totalAttackBonus    = state.totalAttackBonus,
                            totalStrengthBonus  = state.totalStrengthBonus,
                            totalDefenseBonus   = state.totalDefenseBonus,
                            skillPrestigeLevels = state.skillPrestigeLevels,
                            combatPrestigeBonus = state.combatPrestigeBonus,
                            prestigeMaxedSkills = state.prestigeMaxedSkills,
                            onOpenPrestige      = onNavigateToPrestige,
                        )
                    }
                }
            }
        }
    }

    // Gear equip-picker sheet
    invState.pickingSlot?.let { slot ->
        val gearSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = inventoryVm::dismissSlotPicker,
            sheetState       = gearSheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            ScaledSheetContent {
            EquipPickerSheet(
                slot       = slot,
                candidates = invState.candidatesFor(slot, invState.resolvedEquipment(inventoryVm.allEquipment)),
                context    = context,
                heirloomXp = invState.heirloomXp,
                onEquip    = { itemKey -> inventoryVm.equip(itemKey, slot) },
                onDismiss  = inventoryVm::dismissSlotPicker,
            )
            }
        }
    }


    // Mercenary camp sheet (raid hiring)
    if (showMercCamp) {
        val mercSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showMercCamp = false },
            sheetState       = mercSheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            ScaledSheetContent {
                MercenaryCampSheet(
                    pool           = state.mercPool,
                    hiredMercs     = state.hiredMercs,
                    maxParty       = MercenaryRepository.MAX_PARTY,
                    onHire         = viewModel::hireMercenary,
                    onDismissMerc  = viewModel::dismissMercenary,
                )
            }
        }
    }

    // Boss info / confirm sheet
    state.selectedBoss?.let { boss ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectBoss(null) },
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            ScaledSheetContent {
            BossInfoSheet(
                boss                 = boss,
                skillLevels          = state.skillLevels,
                equippedWeapon       = state.equippedWeapon,
                equippedWeapons      = state.equippedWeapons,
                selectedWeaponSlot   = state.selectedWeaponSlot,
                selectedSpell        = state.selectedSpell,
                availablePotions     = state.availablePotions,
                potionEffects        = viewModel.potionEffects,
                selectedPotionKey    = state.selectedPotionKey,
                isStarting           = state.startingSession,
                isQueueFull          = state.isQueueFull,
                repeatCount          = state.selectedBossRepeatCount,
                fullCoinKillsLeft    = state.bossFullCoinKillsLeft,
                hiredMercs           = state.hiredMercs,
                onOpenMercCamp       = { showMercCamp = true },
                onWeaponSlotSelected = viewModel::selectWeaponSlot,
                onPotionSelected     = viewModel::selectPotion,
                onRepeatCountChanged = viewModel::selectBossRepeatCount,
                onStart              = { viewModel.startBossSession(boss.id) },
                onDismiss            = { viewModel.selectBoss(null) },
            )
            }
        }
    }

    // Dungeon info / confirm sheet
    state.selectedDungeon?.let { dungeon ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectDungeon(null) },
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            ScaledSheetContent {
            DungeonInfoSheet(
                dungeon              = dungeon,
                skillLevels          = state.skillLevels,
                equippedWeapon       = state.equippedWeapon,
                equippedWeapons      = state.equippedWeapons,
                selectedWeaponSlot   = state.selectedWeaponSlot,
                selectedSpell        = state.selectedSpell,
                availablePotions     = state.availablePotions,
                potionEffects        = viewModel.potionEffects,
                selectedPotionKey    = state.selectedPotionKey,
                isStarting           = state.startingSession,
                isQueueFull          = state.isQueueFull,
                repeatCount          = state.selectedDungeonRepeatCount,
                enemies              = viewModel.enemyMap,
                onWeaponSlotSelected = viewModel::selectWeaponSlot,
                onPotionSelected     = viewModel::selectPotion,
                onRepeatCountChanged = viewModel::selectDungeonRepeatCount,
                onStart              = { viewModel.startDungeonSession(dungeon.name) },
                onDismiss            = { viewModel.selectDungeon(null) },
            )
            }
        }
    }


    // No-food warning dialog
    if (state.noFoodWarningPending) {
        AlertDialog(
            onDismissRequest = viewModel::dismissNoFoodWarning,
            title = { Text(stringResource(R.string.combat_no_food_title)) },
            text  = { Text(stringResource(R.string.combat_no_food_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmStartWithoutFood) {
                    Text(stringResource(R.string.combat_start_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissNoFoodWarning) {
                    Text(stringResource(AndroidR.string.cancel))
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Combined dungeon + boss selection list
// ---------------------------------------------------------------------------

@Composable
private fun CombatSelectionList(
    dungeons: List<DungeonData>,
    bosses: List<BossData>,
    skillLevels: Map<String, Int>,
    survivalRatings: Map<String, CombatSimulator.SurvivalRating> = emptyMap(),
    dungeonRuns: Map<String, Int> = emptyMap(),
    dungeonLastRunStats: Map<String, DungeonRunStats> = emptyMap(),
    unlockedDungeons: List<String> = emptyList(),
    towerBestFloor: Int = 0,
    bossKillCounts: Map<String, Int> = emptyMap(),
    isQueueFull: Boolean = false,
    raidBosses: List<BossData> = emptyList(),
    hiredMercCount: Int = 0,
    maxParty: Int = 3,
    modifier: Modifier = Modifier,
    onDungeon: (DungeonData) -> Unit,
    onBoss: (BossData) -> Unit,
    onTower: () -> Unit = {},
    onOpenMercCamp: () -> Unit = {},
) {
    val combatLvl = combatLevelFrom(skillLevels)

    LazyColumn(modifier.fillMaxSize()) {
        item { CombatSectionHeader(stringResource(R.string.label_dungeons_tab)) }
        item { TowerEntryRow(bestFloor = towerBestFloor, isQueueFull = isQueueFull, onTap = onTower) }
        items(dungeons) { dungeon ->
            // Lore dungeons need discovery on top of the level gate, not instead of it,
            // or a prestiged player keeps access far below the requirement (issue #1542).
            val discovered = !dungeon.loreUnlockOnly || unlockedDungeons.contains(dungeon.name)
            val unlocked = discovered && combatLvl >= dungeon.recommendedLevel - UNLOCK_TOLERANCE
            DungeonRow(
                dungeon        = dungeon,
                unlocked       = unlocked,
                isQueueFull     = isQueueFull,
                survivalRating = survivalRatings[dungeon.name],
                runCount       = dungeonRuns[dungeon.name] ?: 0,
                lastRunStats   = dungeonLastRunStats[dungeon.name],
                onTap          = { onDungeon(dungeon) },
                loreLockedHint = if (!discovered)
                    dungeon.loreHint ?: stringResource(R.string.expedition_discover_hint) else null,
            )
        }
        item { CombatSectionHeader(stringResource(R.string.combat_solo_bosses)) }
        items(bosses) { boss ->
            BossRow(
                boss     = boss,
                unlocked = combatLvl >= boss.combatLevelRequired,
                runCount = bossKillCounts[boss.id] ?: 0,
                onTap    = { onBoss(boss) },
                isQueueFull = isQueueFull,
            )
        }
        if (raidBosses.isNotEmpty()) {
            item { CombatSectionHeader(stringResource(R.string.raid_section_title)) }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        text  = stringResource(R.string.raid_section_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text       = stringResource(R.string.raid_party_status, hiredMercCount, maxParty),
                            style      = MaterialTheme.typography.labelLarge,
                            color      = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier   = Modifier.weight(1f),
                        )
                        TextButton(onClick = onOpenMercCamp) {
                            Text(stringResource(R.string.merc_camp_open))
                        }
                    }
                }
            }
            items(raidBosses) { boss ->
                BossRow(
                    boss     = boss,
                    // Raid levels are flavor, not a gate; hiring mercenaries is the real bar.
                    unlocked = true,
                    runCount = bossKillCounts[boss.id] ?: 0,
                    onTap    = { onBoss(boss) },
                    isQueueFull = isQueueFull,
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ---------------------------------------------------------------------------
// Combat gear tab
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CombatGearTab(
    equipped: Map<String, String?>,
    inventory: Map<String, Int>,
    equippedFood: Map<String, Int>,
    foodHealValues: Map<String, Int>,
    cookingRecipes: Map<String, CookingRecipe>,
    allEquipment: Map<String, EquipmentData>,
    heirloomXp: Map<String, Long>,
    context: Context,
    totalAttack: Int,
    totalStrength: Int,
    totalDefense: Int,
    activeWeaponSlot: String?,
    foodEatThresholdPct: Int,
    foodEatOrder: String,
    availableSpells: List<SpellData>,
    magicLevel: Int,
    selectedArrowKey: String?,
    selectedSpell: SpellData?,
    onSlotTap: (String) -> Unit,
    onUnequip: (String) -> Unit,
    onEquipBest: () -> Unit,
    onEquipFood: (String) -> Unit,
    onUnequipFood: (String) -> Unit,
    onSelectStyle: (String) -> Unit,
    onArrowSelected: (String?) -> Unit,
    onSpellSelected: (SpellData?) -> Unit,
    onFoodThresholdChanged: (Int) -> Unit,
    onFoodOrderChanged: (String) -> Unit,
) {
    val cookedItemKeys = remember(cookingRecipes) {
        cookingRecipes.values.map { it.cookedItem }.toSet()
    }
    val foodInInventory = remember(inventory, cookedItemKeys, foodHealValues, foodEatOrder) {
        inventory.filterKeys { it in cookedItemKeys }.entries
            .sortedBy {
                when (foodEatOrder) {
                    "descending" -> -(foodHealValues[it.key] ?: 0)
                    "ascending" -> foodHealValues[it.key] ?: 0
                    "least_quantity" -> it.value
                    else -> 0
                }
            }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { SlotSectionHeader(stringResource(R.string.profile_combat_style)) }
        item {
            Row(
                modifier              = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EquipSlot.WEAPON_SLOTS.forEach { slot ->
                    val style = EquipSlot.combatStyleForSlot(slot)!!
                    val iconRes = GameStrings.skillIconRes(style)
                    FilterChip(
                        selected = activeWeaponSlot == slot,
                        onClick  = { onSelectStyle(slot) },
                        label    = { Text(GameStrings.skillName(context, style)) },
                        leadingIcon = if (iconRes != null) {
                            {
                                Image(
                                    painter = painterResource(iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else null,
                    )
                }
            }
        }
        item {
            Text(
                text = "${stringResource(R.string.combat_atk)} $totalAttack  " +
                    "${stringResource(R.string.combat_str)} $totalStrength  " +
                    "${stringResource(R.string.combat_def)} $totalDefense",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        // Only the active style's own weapon is shown/selectable here — never another
        // style's weapon, since each style has its own separate weapon slot.
        item {
            val weaponSlot = activeWeaponSlot ?: EquipSlot.WEAPON_ATK
            EquipSlotRow(
                slotName  = GameStrings.slotName(context, weaponSlot),
                itemKey   = equipped[weaponSlot],
                xpLabel   = weaponXpLabel(allEquipment[equipped[weaponSlot]]?.combatStyle, context),
                equipment = allEquipment[equipped[weaponSlot]],
                heirloomXp = heirloomXp,
                onTap     = { onSlotTap(weaponSlot) },
                onUnequip = { onUnequip(weaponSlot) },
            )
        }
        val loadoutStyle = EquipSlot.combatStyleForSlot(activeWeaponSlot ?: "")
        if (loadoutStyle == "ranged" || loadoutStyle == "magic") {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (loadoutStyle == "ranged") {
                        ArrowLoadoutPicker(
                            selectedArrowKey = selectedArrowKey,
                            inventory        = inventory,
                            context          = context,
                            onArrowSelected  = onArrowSelected,
                        )
                    } else {
                        val weaponSlot = activeWeaponSlot ?: EquipSlot.WEAPON_ATK
                        SpellLoadoutPicker(
                            selectedSpell   = selectedSpell,
                            availableSpells = availableSpells,
                            magicLevel      = magicLevel,
                            inventory       = inventory,
                            equippedWeapon  = allEquipment[equipped[weaponSlot]],
                            context         = context,
                            onSpellSelected = onSpellSelected,
                        )
                    }
                }
            }
        }
        item { SlotSectionHeader(stringResource(R.string.profile_combat_gear)) }
        items(EquipSlot.ARMOR_SLOTS) { slot ->
            EquipSlotRow(
                slotName  = GameStrings.slotName(context, slot),
                itemKey   = equipped[slot],
                equipment = allEquipment[equipped[slot]],
                heirloomXp = heirloomXp,
                onTap     = { onSlotTap(slot) },
                onUnequip = { onUnequip(slot) },
            )
        }
        item {
            Button(
                onClick  = onEquipBest,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.profile_equip_best))
            }
        }
        item { SlotSectionHeader(stringResource(R.string.profile_food_dungeon)) }
        if (foodInInventory.isEmpty()) {
            item {
                Text(
                    text     = stringResource(R.string.profile_no_food),
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        } else {
            items(foodInInventory, key = { "food_${it.key}" }) { (key, qty) ->
                FoodRow(
                    itemKey    = key,
                    qty        = qty,
                    healValue  = foodHealValues[key] ?: 0,
                    isEquipped = key in equippedFood,
                    context    = context,
                    onEquip    = { onEquipFood(key) },
                    onUnequip  = { onUnequipFood(key) },
                )
            }
        }
        item { SlotSectionHeader(stringResource(R.string.profile_food_threshold)) }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = { onFoodThresholdChanged(foodEatThresholdPct - 10) }) {
                    Icon(Icons.Filled.Remove, contentDescription = null)
                }
                Text(
                    text      = "$foodEatThresholdPct%",
                    style     = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.width(56.dp),
                )
                IconButton(onClick = { onFoodThresholdChanged(foodEatThresholdPct + 10) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                }
            }
        }
        if (foodEatThresholdPct <= 30) {
            item {
                Text(
                    text     = stringResource(R.string.combat_eat_threshold_warning),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        item { SlotSectionHeader(stringResource(R.string.profile_food_order)) }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                FoodOrderPicker(foodEatOrder, onFoodOrderChanged)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ---------------------------------------------------------------------------
// Combat skills tab
// ---------------------------------------------------------------------------

private val COMBAT_SKILLS = listOf(
    Skills.ATTACK, Skills.STRENGTH, Skills.DEFENSE,
    Skills.RANGED, Skills.MAGIC, Skills.HITPOINTS,
)

@Composable
private fun CombatSkillsTab(
    skillLevels: Map<String, Int>,
    skillXp: Map<String, Long>,
    totalAttackBonus: Int,
    totalStrengthBonus: Int,
    totalDefenseBonus: Int,
    skillPrestigeLevels: Map<String, Int> = emptyMap(),
    combatPrestigeBonus: Map<String, Int> = emptyMap(),
    prestigeMaxedSkills: Set<String> = emptySet(),
    onOpenPrestige: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var tappedSkill by remember { mutableStateOf<String?>(null) }

    tappedSkill?.let { key ->
        AlertDialog(
            onDismissRequest = { tappedSkill = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(GameStrings.skillName(context, key))
                    TextButton(onClick = { tappedSkill = null; onOpenPrestige(key) }) {
                        Text(stringResource(R.string.prestige_skill_tree))
                    }
                }
            },
            text  = { Text(GameStrings.skillDesc(context, key)) },
            confirmButton = {
                TextButton(onClick = { tappedSkill = null }) {
                    Text(stringResource(R.string.btn_close))
                }
            },
        )
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(COMBAT_SKILLS) { key ->
            val gearBonus = when (key) {
                Skills.ATTACK   -> totalAttackBonus
                Skills.STRENGTH -> totalStrengthBonus
                Skills.DEFENSE  -> totalDefenseBonus
                else            -> 0
            }
            CombatSkillRow(
                skillKey      = key,
                level         = skillLevels[key] ?: 1,
                xp            = skillXp[key]     ?: 0L,
                gearBonus     = gearBonus,
                prestigeLevel = skillPrestigeLevels[key] ?: 0,
                prestigeBonus = combatPrestigeBonus[key] ?: 0,
                isPrestigeMaxed = key in prestigeMaxedSkills,
                onOpenPrestige = onOpenPrestige.let { cb -> { cb(key) } },
                onClick       = { tappedSkill = key },
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CombatSkillRow(
    skillKey: String,
    level: Int,
    xp: Long,
    gearBonus: Int = 0,
    prestigeLevel: Int = 0,
    prestigeBonus: Int = 0,
    isPrestigeMaxed: Boolean = false,
    onOpenPrestige: (() -> Unit)? = null,
    onClick: () -> Unit = {},
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
        Box(modifier = Modifier.size(44.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
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
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text       = name,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier   = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                val remainingToCap = xpToMaxLevel(xp)
                val xpText = when {
                    remainingToCap in 1 until 100_000L ->
                        stringResource(R.string.xp_to_99, remainingToCap.formatXp())
                    xpToNextLevel(xp) > 0L ->
                        "${xp.formatXp()} / ${nextLevelThreshold(xp).formatXp()} XP"
                    else -> "${xp.formatXp()} ${stringResource(R.string.label_xp)}"
                }
                Text(
                    text  = xpText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                color            = MaterialTheme.colorScheme.primary,
                trackColor       = MaterialTheme.colorScheme.surfaceVariant,
            )
            if (gearBonus > 0 || prestigeBonus > 0) {
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (gearBonus > 0) {
                        Text(
                            text  = stringResource(R.string.combat_gear_bonus, gearBonus),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (prestigeBonus > 0) {
                        Text(
                            text  = stringResource(R.string.combat_prestige_bonus, prestigeBonus),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (prestigeLevel > 0 || (onOpenPrestige != null && level >= 99)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text  = if (isPrestigeMaxed) stringResource(R.string.skills_prestige_max, prestigeLevel)
                                else "★×$prestigeLevel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (onOpenPrestige != null) {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            TextButton(
                                onClick = { onOpenPrestige() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(24.dp),
                            ) {
                                Text(
                                    text  = stringResource(R.string.prestige),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun CombatSectionHeader(title: String) {
    Text(
        text     = title.uppercase(),
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun BossRow(
    boss: BossData,
    unlocked: Boolean,
    isQueueFull: Boolean,
    runCount: Int = 0,
    onTap: () -> Unit,
) {
    val context  = LocalContext.current
    val dimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = unlocked, onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier         = Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            BossIcon(
                bossId        = boss.id,
                modifier      = Modifier
                    .size(36.dp)
                    .then(if (unlocked) Modifier else Modifier.alpha(0.38f)),
                fallbackEmoji = boss.emoji,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text       = GameStrings.bossName(context, boss.id),
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = if (unlocked) MaterialTheme.colorScheme.onSurface else dimColor,
            )
            Text(
                text     = GameStrings.bossDesc(context, boss.id).takeIf { it.isNotBlank() } ?: boss.description,
                style    = MaterialTheme.typography.bodySmall,
                color    = if (unlocked) MaterialTheme.colorScheme.onSurfaceVariant else dimColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (runCount > 0) {
                Text(
                    text  = stringResource(R.string.combat_dungeon_runs, runCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (unlocked) MaterialTheme.colorScheme.onSurfaceVariant else dimColor,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text       = "Lv. ${boss.combatLevelRequired}",
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color      = if (unlocked) MaterialTheme.colorScheme.primary else dimColor,
            )
            if (isQueueFull) {
                Text(
                    text = stringResource(R.string.snackbar_queue_full),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun TowerEntryRow(
    bestFloor: Int,
    isQueueFull: Boolean,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier         = Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🏰", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text       = stringResource(R.string.tower_title),
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text     = stringResource(R.string.tower_entry_card_desc),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (bestFloor > 0) {
                    Text(
                        text       = stringResource(R.string.tower_best_floor, bestFloor),
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.primary,
                    )
            }
            if (isQueueFull) {
                Text(
                    text  = stringResource(R.string.snackbar_queue_full),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun DungeonRow(
    dungeon: DungeonData,
    unlocked: Boolean,
    isQueueFull: Boolean,
    survivalRating: CombatSimulator.SurvivalRating? = null,
    runCount: Int = 0,
    lastRunStats: DungeonRunStats? = null,
    loreLockedHint: String? = null,
    onTap: () -> Unit,
) {
    val context  = LocalContext.current
    val dimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = unlocked, onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text       = GameStrings.dungeonName(context, dungeon.name),
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = if (unlocked) MaterialTheme.colorScheme.onSurface else dimColor,
            )
            Text(
                text     = GameStrings.dungeonDesc(context, dungeon.name).takeIf { it.isNotBlank() } ?: dungeon.description,
                style    = MaterialTheme.typography.bodySmall,
                color    = if (unlocked) MaterialTheme.colorScheme.onSurfaceVariant
                           else dimColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (unlocked && survivalRating != null) {
                val (ratingText, ratingColor) = when (survivalRating) {
                    CombatSimulator.SurvivalRating.LIKELY   -> stringResource(R.string.combat_difficulty_likely)   to MaterialTheme.colorScheme.tertiary
                    CombatSimulator.SurvivalRating.RISKY    -> stringResource(R.string.combat_difficulty_risky)    to MaterialTheme.colorScheme.secondary
                    CombatSimulator.SurvivalRating.UNLIKELY -> stringResource(R.string.combat_difficulty_unlikely) to MaterialTheme.colorScheme.error
                }
                Text(
                    text  = ratingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = ratingColor,
                )
            }
            if (runCount > 0) {
                Text(
                    text  = stringResource(R.string.combat_dungeon_runs, runCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (unlocked) MaterialTheme.colorScheme.onSurfaceVariant else dimColor,
                )
            }
            if (unlocked && lastRunStats != null) {
                val lastRunText = if (lastRunStats.survived)
                    stringResource(R.string.combat_last_run, lastRunStats.killCount, lastRunStats.foodConsumed)
                else
                    stringResource(R.string.combat_last_run_died, lastRunStats.killCount)
                Text(
                    text  = lastRunText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (lastRunStats.survived) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error,
                )
            }
            if (!unlocked && loreLockedHint != null) {
                Text(
                    text  = loreLockedHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = dimColor,
                    fontStyle = FontStyle.Italic,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Lv. ${dungeon.recommendedLevel}",
                style = MaterialTheme.typography.labelMedium,
                color = if (unlocked) MaterialTheme.colorScheme.primary else dimColor,
                fontWeight = FontWeight.SemiBold,
            )
            if (isQueueFull) {
                Text(
                    text = stringResource(R.string.snackbar_queue_full),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

/** Dungeons within this many levels of the recommendation are still enterable. */
internal const val UNLOCK_TOLERANCE = 5
