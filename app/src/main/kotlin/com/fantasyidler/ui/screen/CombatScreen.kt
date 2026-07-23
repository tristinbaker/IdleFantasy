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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.CookingRecipe
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.SpellData
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.Skills
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.theme.ScaledSheetContent
import com.fantasyidler.ui.theme.SuccessGreen
import com.fantasyidler.ui.viewmodel.CombatViewModel
import com.fantasyidler.ui.viewmodel.InventoryViewModel
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.ui.viewmodel.slotDisplayName
import com.fantasyidler.ui.viewmodel.xpProgressFraction
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.toCountdown
import com.fantasyidler.util.toTitleCase
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CombatScreen(
    viewModel:          CombatViewModel    = hiltViewModel(),
    inventoryVm:        InventoryViewModel = hiltViewModel(),
    startOnGear:        Boolean            = false,
    initialDungeonKey:  String?            = null,
    initialBossKey:     String?            = null,
    onNavigateToTower:  () -> Unit         = {},
) {
    val state            by viewModel.uiState.collectAsState()
    val invState         by inventoryVm.uiState.collectAsState()
    val context           = LocalContext.current
    val visibleDungeons   = remember(state.unlockedDungeons) {
        viewModel.dungeonList.filter { !it.loreUnlockOnly || it.name in state.unlockedDungeons }
    }

    LaunchedEffect(initialDungeonKey, initialBossKey) {
        initialDungeonKey?.let { key -> viewModel.dungeonList.firstOrNull { it.name == key }?.let(viewModel::selectDungeon) }
        initialBossKey?.let { key -> viewModel.bossList.firstOrNull { it.id == key }?.let(viewModel::selectBoss) }
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
                            color      = GoldPrimary,
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
        if (combatSession != null) {
            var savedPage by rememberSaveable { mutableIntStateOf(if (startOnGear) 2 else 0) }
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
                        text     = { Text(stringResource(R.string.label_equipment)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 3,
                        onClick  = { scope.launch { pagerState.animateScrollToPage(3) } },
                        text     = { Text(stringResource(R.string.label_skills)) },
                    )
                }
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    when (page) {
                        0 -> CombatSessionBanner(
                            session        = combatSession,
                            dungeons       = visibleDungeons,
                            bosses         = viewModel.bossList,
                            enemies        = viewModel.enemyMap,
                            skillLevels    = state.skillLevels,
                            skillPrestige  = state.skillPrestige,
                            attackBonus    = state.totalAttackBonus,
                            strengthBonus  = state.totalStrengthBonus,
                            defenseBonus   = state.totalDefenseBonus,
                            equippedFood   = state.equippedFood,
                            foodHealValues = viewModel.foodHealValues,
                            showEndTime    = state.showSessionEndTime,
                            repeatIndex    = state.activeBossRepeatIndex,
                            repeatTotal    = state.activeBossRepeatTotal,
                            onAbandon      = viewModel::abandonSession,
                            onDebugFinish  = viewModel::debugFinishSession,
                        )
                        1 -> CombatSelectionList(
                            dungeons            = visibleDungeons,
                            bosses              = viewModel.bossList,
                            skillLevels         = state.skillLevels,
                            survivalRatings     = state.dungeonSurvivalRatings,
                            dungeonRuns         = state.dungeonRuns,
                            dungeonLastRunStats = state.dungeonLastRunStats,
                            unlockedDungeons    = state.unlockedDungeons,
                            towerBestFloor      = state.towerBestFloor,
                            bossKillCounts      = state.bossKillCounts,
                            onDungeon           = viewModel::selectDungeon,
                            onBoss              = viewModel::selectBoss,
                            onTower             = onNavigateToTower,
                        )
                        2 -> CombatGearTab(
                            equipped       = invState.equipped,
                            inventory      = invState.inventory,
                            equippedFood   = invState.equippedFood,
                            foodHealValues = inventoryVm.foodHealValues,
                            cookingRecipes = inventoryVm.cookingRecipes,
                            allEquipment   = inventoryVm.allEquipment,
                            context        = context,
                            onSlotTap      = inventoryVm::openSlotPicker,
                            onUnequip      = inventoryVm::unequip,
                            onEquipBest    = inventoryVm::equipBestGear,
                            onEquipFood    = inventoryVm::equipFood,
                            onUnequipFood  = inventoryVm::unequipFood,
                        )
                        else -> CombatSkillsTab(
                            skillLevels        = state.skillLevels,
                            skillXp            = state.skillXp,
                            totalAttackBonus   = state.totalAttackBonus,
                            totalStrengthBonus = state.totalStrengthBonus,
                            totalDefenseBonus  = state.totalDefenseBonus,
                            skillPrestige      = state.skillPrestige,
                            onPrestige         = viewModel::prestigeSkill,
                        )
                    }
                }
            }
        } else {
            var savedPage by rememberSaveable { mutableIntStateOf(if (startOnGear) 1 else 0) }
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
                        text     = { Text(stringResource(R.string.label_equipment)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 2,
                        onClick  = { scope.launch { pagerState.animateScrollToPage(2) } },
                        text     = { Text(stringResource(R.string.label_skills)) },
                    )
                }
                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    when (page) {
                        0 -> CombatSelectionList(
                            dungeons            = visibleDungeons,
                            bosses              = viewModel.bossList,
                            skillLevels         = state.skillLevels,
                            survivalRatings     = state.dungeonSurvivalRatings,
                            dungeonRuns         = state.dungeonRuns,
                            dungeonLastRunStats = state.dungeonLastRunStats,
                            unlockedDungeons    = state.unlockedDungeons,
                            towerBestFloor      = state.towerBestFloor,
                            bossKillCounts      = state.bossKillCounts,
                            onDungeon           = viewModel::selectDungeon,
                            onBoss              = viewModel::selectBoss,
                            onTower             = onNavigateToTower,
                        )
                        1 -> CombatGearTab(
                            equipped       = invState.equipped,
                            inventory      = invState.inventory,
                            equippedFood   = invState.equippedFood,
                            foodHealValues = inventoryVm.foodHealValues,
                            cookingRecipes = inventoryVm.cookingRecipes,
                            allEquipment   = inventoryVm.allEquipment,
                            context        = context,
                            onSlotTap      = inventoryVm::openSlotPicker,
                            onUnequip      = inventoryVm::unequip,
                            onEquipBest    = inventoryVm::equipBestGear,
                            onEquipFood    = inventoryVm::equipFood,
                            onUnequipFood  = inventoryVm::unequipFood,
                        )
                        else -> CombatSkillsTab(
                            skillLevels        = state.skillLevels,
                            skillXp            = state.skillXp,
                            totalAttackBonus   = state.totalAttackBonus,
                            totalStrengthBonus = state.totalStrengthBonus,
                            totalDefenseBonus  = state.totalDefenseBonus,
                            skillPrestige      = state.skillPrestige,
                            onPrestige         = viewModel::prestigeSkill,
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
                candidates = invState.candidatesFor(slot, inventoryVm.allEquipment),
                context    = context,
                onEquip    = { itemKey -> inventoryVm.equip(itemKey, slot) },
                onDismiss  = inventoryVm::dismissSlotPicker,
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
                inventory            = state.inventory,
                availableSpells      = viewModel.availableSpells(state.skillLevels),
                selectedSpell        = state.selectedSpell,
                availablePotions     = state.availablePotions,
                potionEffects        = viewModel.potionEffects,
                selectedPotionKey    = state.selectedPotionKey,
                selectedArrowKey     = state.selectedArrowKey,
                isStarting           = state.startingSession,
                repeatCount          = state.selectedBossRepeatCount,
                onWeaponSlotSelected = viewModel::selectWeaponSlot,
                onSpellSelected      = viewModel::selectSpell,
                onArrowSelected      = viewModel::selectArrow,
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
                inventory            = state.inventory,
                availableSpells      = viewModel.availableSpells(state.skillLevels),
                selectedSpell        = state.selectedSpell,
                availablePotions     = state.availablePotions,
                potionEffects        = viewModel.potionEffects,
                selectedPotionKey    = state.selectedPotionKey,
                selectedArrowKey     = state.selectedArrowKey,
                isStarting           = state.startingSession,
                enemies              = viewModel.enemyMap,
                onWeaponSlotSelected = viewModel::selectWeaponSlot,
                onSpellSelected      = viewModel::selectSpell,
                onPotionSelected     = viewModel::selectPotion,
                onArrowSelected      = viewModel::selectArrow,
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
                    Text(stringResource(android.R.string.cancel))
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
    dungeonLastRunStats: Map<String, com.fantasyidler.data.model.DungeonRunStats> = emptyMap(),
    unlockedDungeons: List<String> = emptyList(),
    towerBestFloor: Int = 0,
    bossKillCounts: Map<String, Int> = emptyMap(),
    modifier: Modifier = Modifier,
    onDungeon: (DungeonData) -> Unit,
    onBoss: (BossData) -> Unit,
    onTower: () -> Unit = {},
) {
    val combatLvl = combatLevel(skillLevels)

    LazyColumn(modifier.fillMaxSize()) {
        item { CombatSectionHeader(stringResource(R.string.label_dungeons_tab)) }
        item { TowerEntryRow(bestFloor = towerBestFloor, onTap = onTower) }
        items(dungeons) { dungeon ->
            val unlocked = if (dungeon.loreUnlockOnly) {
                unlockedDungeons.contains(dungeon.name)
            } else {
                combatLvl >= dungeon.recommendedLevel - UNLOCK_TOLERANCE
            }
            DungeonRow(
                dungeon        = dungeon,
                unlocked       = unlocked,
                survivalRating = survivalRatings[dungeon.name],
                runCount       = dungeonRuns[dungeon.name] ?: 0,
                lastRunStats   = dungeonLastRunStats[dungeon.name],
                onTap          = { onDungeon(dungeon) },
                loreLockedHint = if (dungeon.loreUnlockOnly && !unlocked)
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
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ---------------------------------------------------------------------------
// Combat gear tab
// ---------------------------------------------------------------------------

@Composable
private fun CombatGearTab(
    equipped: Map<String, String?>,
    inventory: Map<String, Int>,
    equippedFood: Map<String, Int>,
    foodHealValues: Map<String, Int>,
    cookingRecipes: Map<String, CookingRecipe>,
    allEquipment: Map<String, EquipmentData>,
    context: android.content.Context,
    onSlotTap: (String) -> Unit,
    onUnequip: (String) -> Unit,
    onEquipBest: () -> Unit,
    onEquipFood: (String) -> Unit,
    onUnequipFood: (String) -> Unit,
) {
    val cookedItemKeys = remember(cookingRecipes) {
        cookingRecipes.values.map { it.cookedItem }.toSet()
    }
    val foodInInventory = remember(inventory, cookedItemKeys) {
        inventory.filterKeys { it in cookedItemKeys }.entries.toList()
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
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
        item {
            Button(
                onClick  = onEquipBest,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.profile_equip_best))
            }
        }
        item { SlotSectionHeader(stringResource(R.string.profile_weapons)) }
        items(EquipSlot.WEAPON_SLOTS) { slot ->
            EquipSlotRow(
                slotName  = slotDisplayName(context, slot),
                itemKey   = equipped[slot],
                xpLabel   = weaponXpLabel(allEquipment[equipped[slot]]?.combatStyle, context),
                equipment = allEquipment[equipped[slot]],
                onTap     = { onSlotTap(slot) },
                onUnequip = { onUnequip(slot) },
            )
        }
        item { SlotSectionHeader(stringResource(R.string.profile_combat_gear)) }
        items(EquipSlot.ARMOR_SLOTS) { slot ->
            EquipSlotRow(
                slotName  = slotDisplayName(context, slot),
                itemKey   = equipped[slot],
                equipment = allEquipment[equipped[slot]],
                onTap     = { onSlotTap(slot) },
                onUnequip = { onUnequip(slot) },
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ---------------------------------------------------------------------------
// Combat skills tab
// ---------------------------------------------------------------------------

private val COMBAT_SKILLS = listOf(
    Skills.ATTACK, Skills.STRENGTH, Skills.DEFENSE,
    Skills.RANGED, Skills.MAGIC, Skills.HITPOINTS, Skills.PRAYER,
)

@Composable
private fun CombatSkillsTab(
    skillLevels: Map<String, Int>,
    skillXp: Map<String, Long>,
    totalAttackBonus: Int,
    totalStrengthBonus: Int,
    totalDefenseBonus: Int,
    skillPrestige: Map<String, Int> = emptyMap(),
    onPrestige: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var tappedSkill by remember { mutableStateOf<String?>(null) }

    tappedSkill?.let { key ->
        AlertDialog(
            onDismissRequest = { tappedSkill = null },
            title = { Text(GameStrings.skillName(context, key)) },
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
                prestigeLevel = skillPrestige[key] ?: 0,
                onPrestige    = if (key != Skills.PRAYER) ({ onPrestige(key) }) else null,
                onClick       = { tappedSkill = key },
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun CombatSkillRow(
    skillKey: String,
    level: Int,
    xp: Long,
    gearBonus: Int = 0,
    prestigeLevel: Int = 0,
    onPrestige: (() -> Unit)? = null,
    onClick: () -> Unit = {},
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
            text  = { Text(stringResource(R.string.prestige_confirm_message_stat, name, nextPrestige * 5)) },
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
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (gearBonus > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text  = stringResource(R.string.combat_gear_bonus, gearBonus),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (skillKey != Skills.PRAYER && prestigeLevel > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text  = stringResource(R.string.combat_prestige_bonus, prestigeLevel * 5),
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldPrimary,
                        )
                    }
                }
                Text(
                    text  = "${xp.formatXp()} ${stringResource(R.string.label_xp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color            = GoldPrimary,
                trackColor       = MaterialTheme.colorScheme.surfaceVariant,
            )
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
            Text(
                text  = boss.emoji,
                style = MaterialTheme.typography.titleLarge,
                color = if (unlocked) MaterialTheme.colorScheme.onSurface else dimColor,
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
        Text(
            text       = "Lv. ${boss.combatLevelRequired}",
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color      = if (unlocked) GoldPrimary else dimColor,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun TowerEntryRow(
    bestFloor: Int,
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
        if (bestFloor > 0) {
            Spacer(Modifier.width(12.dp))
            Text(
                text       = stringResource(R.string.tower_best_floor, bestFloor),
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color      = GoldPrimary,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun DungeonRow(
    dungeon: DungeonData,
    unlocked: Boolean,
    survivalRating: CombatSimulator.SurvivalRating? = null,
    runCount: Int = 0,
    lastRunStats: com.fantasyidler.data.model.DungeonRunStats? = null,
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
                    CombatSimulator.SurvivalRating.LIKELY   -> stringResource(R.string.combat_difficulty_likely)   to SuccessGreen
                    CombatSimulator.SurvivalRating.RISKY    -> stringResource(R.string.combat_difficulty_risky)    to MaterialTheme.colorScheme.tertiary
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
        Text(
            text  = "Lv. ${dungeon.recommendedLevel}",
            style = MaterialTheme.typography.labelMedium,
            color = if (unlocked) GoldPrimary else dimColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

/** Dungeons within this many levels of the recommendation are still enterable. */
internal const val UNLOCK_TOLERANCE = 5

/** Arrow tiers from best to worst — mirrors CombatViewModel.ARROW_TIERS. */
internal val ARROW_TIERS = listOf(
    "runite_arrow", "adamantite_arrow", "mithril_arrow",
    "steel_arrow", "iron_arrow", "bronze_arrow",
)
