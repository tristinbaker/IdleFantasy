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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.Skills
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.theme.SuccessGreen
import com.fantasyidler.ui.viewmodel.CombatSessionResult
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

private fun xpBreakdownText(total: Long, bonus: Long, boostWasActive: Boolean): String? {
    if (bonus <= 0L) return null
    val afterBoost = total - bonus
    if (afterBoost <= 0L) return null
    val base = afterBoost / (if (boostWasActive) 2L else 1L)
    val blessMult = total.toDouble() / afterBoost
    val blessStr = "%.2f".format(blessMult).trimEnd('0').trimEnd('.')
    return if (boostWasActive) "(${base.formatXp()} × 2 × $blessStr)"
           else "(${base.formatXp()} × $blessStr)"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CombatScreen(
    viewModel:    CombatViewModel    = hiltViewModel(),
    inventoryVm:  InventoryViewModel = hiltViewModel(),
    startOnGear:  Boolean            = false,
) {
    val state            by viewModel.uiState.collectAsState()
    val invState         by inventoryVm.uiState.collectAsState()
    val context           = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val visibleDungeons   = remember(state.unlockedDungeons) {
        viewModel.dungeonList.filter { !it.loreUnlockOnly || it.name in state.unlockedDungeons }
    }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.snackbarConsumed()
        }
    }

    Scaffold(
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val combatSession = state.combatSession
        if (combatSession != null) {
            val pagerState = rememberPagerState(initialPage = if (startOnGear) 2 else 0, pageCount = { 4 })
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
                            attackBonus    = state.totalAttackBonus,
                            strengthBonus  = state.totalStrengthBonus,
                            defenseBonus   = state.totalDefenseBonus,
                            equippedFood   = state.equippedFood,
                            foodHealValues = viewModel.foodHealValues,
                            onCollect      = viewModel::collectSession,
                            onAbandon      = viewModel::abandonSession,
                            onDebugFinish  = viewModel::debugFinishSession,
                        )
                        1 -> CombatSelectionList(
                            dungeons         = visibleDungeons,
                            bosses           = viewModel.bossList,
                            skillLevels      = state.skillLevels,
                            survivalRatings  = state.dungeonSurvivalRatings,
                            dungeonRuns      = state.dungeonRuns,
                            unlockedDungeons = state.unlockedDungeons,
                            onDungeon        = viewModel::selectDungeon,
                            onBoss           = viewModel::selectBoss,
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
            val pagerState = rememberPagerState(initialPage = if (startOnGear) 1 else 0, pageCount = { 3 })
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
                            dungeons         = visibleDungeons,
                            bosses           = viewModel.bossList,
                            skillLevels      = state.skillLevels,
                            survivalRatings  = state.dungeonSurvivalRatings,
                            dungeonRuns      = state.dungeonRuns,
                            unlockedDungeons = state.unlockedDungeons,
                            onDungeon        = viewModel::selectDungeon,
                            onBoss           = viewModel::selectBoss,
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
            EquipPickerSheet(
                slot       = slot,
                candidates = invState.candidatesFor(slot, inventoryVm.allEquipment),
                context    = context,
                onEquip    = { itemKey -> inventoryVm.equip(itemKey, slot) },
                onDismiss  = inventoryVm::dismissSlotPicker,
            )
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
                onWeaponSlotSelected = viewModel::selectWeaponSlot,
                onSpellSelected      = viewModel::selectSpell,
                onArrowSelected      = viewModel::selectArrow,
                onPotionSelected     = viewModel::selectPotion,
                onStart              = { viewModel.startBossSession(boss.id) },
                onDismiss            = { viewModel.selectBoss(null) },
            )
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
                onWeaponSlotSelected = viewModel::selectWeaponSlot,
                onSpellSelected      = viewModel::selectSpell,
                onPotionSelected     = viewModel::selectPotion,
                onArrowSelected      = viewModel::selectArrow,
                onStart              = { viewModel.startDungeonSession(dungeon.name) },
                onDismiss            = { viewModel.selectDungeon(null) },
            )
        }
    }

    // Combat result sheet
    state.combatResult?.let { result ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::resultConsumed,
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            CombatResultSheet(
                result    = result,
                onDismiss = viewModel::resultConsumed,
            )
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
    unlockedDungeons: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    onDungeon: (DungeonData) -> Unit,
    onBoss: (BossData) -> Unit,
) {
    val combatLvl = combatLevel(skillLevels)

    LazyColumn(modifier.fillMaxSize()) {
        item { CombatSectionHeader(stringResource(R.string.label_dungeons_tab)) }
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
                onTap     = { onSlotTap(slot) },
                onUnequip = { onUnequip(slot) },
            )
        }
        item { SlotSectionHeader(stringResource(R.string.profile_combat_gear)) }
        items(EquipSlot.ARMOR_SLOTS) { slot ->
            EquipSlotRow(
                slotName  = slotDisplayName(context, slot),
                itemKey   = equipped[slot],
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
private fun DungeonRow(
    dungeon: DungeonData,
    unlocked: Boolean,
    survivalRating: CombatSimulator.SurvivalRating? = null,
    runCount: Int = 0,
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

// ---------------------------------------------------------------------------
// Active session banner
// ---------------------------------------------------------------------------

private data class CombatLogEntry(
    val isPlayer: Boolean,
    val damage: Int,
    val enemyName: String,
    val isKill: Boolean = false,
)

@Composable
private fun CombatSessionBanner(
    session: SkillSession,
    dungeons: List<DungeonData>,
    bosses: List<BossData>,
    enemies: Map<String, EnemyData>,
    skillLevels: Map<String, Int>,
    attackBonus: Int,
    strengthBonus: Int,
    defenseBonus: Int,
    equippedFood: Map<String, Int>,
    foodHealValues: Map<String, Int>,
    modifier: Modifier = Modifier,
    onCollect: () -> Unit,
    onAbandon: () -> Unit,
    onDebugFinish: () -> Unit,
) {
    val context = LocalContext.current
    val dungeonName = dungeons.firstOrNull { it.name == session.activityKey }
        ?.let { GameStrings.dungeonName(context, it.name) }
        ?: bosses.firstOrNull { it.id == session.activityKey }?.let { "${it.emoji} ${GameStrings.bossName(context, it.id)}" }
        ?: session.activityKey

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showAbandonConfirm by remember { mutableStateOf(false) }
    val endsAt = session.endsAt
    LaunchedEffect(endsAt) {
        while (System.currentTimeMillis() < endsAt) {
            now = System.currentTimeMillis()
            delay(500L)
        }
        now = System.currentTimeMillis()
    }

    val isDone = session.completed || now >= endsAt

    // Decode frames once per session
    val frames = remember(session.sessionId) {
        runCatching { Json.decodeFromString<List<SessionFrame>>(session.frames) }.getOrElse { emptyList() }
    }
    val frameCount = if (session.skillName == "boss")
        (bosses.firstOrNull { it.id == session.activityKey }?.durationMinutes ?: 60).coerceAtLeast(1)
    else 60
    val perFrameMs = ((session.endsAt - session.startedAt) / frameCount.toLong()).coerceAtLeast(1L)
    val currentFrameIdx = remember(now) {
        ((now - session.startedAt) / perFrameMs).toInt()
            .coerceIn(0, (frames.size - 1).coerceAtLeast(0))
    }
    val currentFrame = frames.getOrNull(currentFrameIdx)

    val currentEnemyKey: String? = remember(currentFrameIdx) {
        currentFrame?.enemyKey?.takeIf { it.isNotEmpty() }
            ?: frames.take(currentFrameIdx + 1)
                .lastOrNull { it.killsByEnemy.isNotEmpty() }
                ?.killsByEnemy?.keys?.firstOrNull()
    }
    val currentEnemy = currentEnemyKey?.let { enemies[it] }

    val isBoss = session.skillName == "boss"
    val attackSpeedMs = 2_400L
    val frameStartMs  = session.startedAt + currentFrameIdx.toLong() * perFrameMs
    val maxTick = (currentFrame?.playerHits?.size?.minus(1) ?: 0).coerceAtLeast(0)
    val tickInFrame = if (!isDone) ((now - frameStartMs) / attackSpeedMs).toInt().coerceIn(0, maxTick) else maxTick

    val killsSoFar: Map<String, Int> = remember(currentFrameIdx, tickInFrame) {
        val acc = frames.take(currentFrameIdx).fold(mutableMapOf<String, Int>()) { a, f ->
            f.killsByEnemy.forEach { (k, v) -> a[k] = (a[k] ?: 0) + v }
            a
        }
        val f = frames.getOrNull(currentFrameIdx)
        if (f != null && !isBoss) {
            val enemy = enemies[f.enemyKey]
            if (enemy != null && f.playerHits.isNotEmpty()) {
                var hp = enemy.hp
                var kills = 0
                for (dmg in f.playerHits.take(tickInFrame + 1)) {
                    hp -= dmg
                    if (hp <= 0) { kills++; hp = enemy.hp }
                }
                if (kills > 0) acc[f.enemyKey] = (acc[f.enemyKey] ?: 0) + kills
            }
        }
        acc
    }

    val foodConsumedSoFar: Map<String, Int> = remember(currentFrameIdx) {
        frames.take(currentFrameIdx).fold(mutableMapOf()) { acc, f ->
            f.foodConsumed.forEach { (k, v) -> acc[k] = (acc[k] ?: 0) + v }
            acc
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text  = if (isDone) stringResource(R.string.label_session_complete)
                    else stringResource(R.string.label_session_in_progress),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (isDone) GoldPrimary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = dungeonName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (!isDone) {
            Text(
                text       = remember(now) { endsAt.toCountdown() },
                style      = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary,
            )

            if (session.skillName == "combat" || session.skillName == "boss") {
                val context = LocalContext.current
                val currentBoss = if (isBoss) bosses.firstOrNull { it.id == session.activityKey } else null
                val divColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)

                // Live player HP (per-tick if hit data exists, else per-frame fallback)
                val maxHp = (skillLevels[Skills.HITPOINTS] ?: 1) * 10
                val currentPlayerHp = if (currentFrame?.enemyHits?.isNotEmpty() == true) {
                    val base = frames.getOrNull(currentFrameIdx - 1)?.hpAfter ?: maxHp
                    (base - currentFrame.enemyHits.take(tickInFrame + 1).sum()).coerceAtLeast(0)
                } else {
                    frames.getOrNull(currentFrameIdx - 1)?.hpAfter ?: maxHp
                }

                // Live enemy HP (cumulative for boss, per-enemy reset for dungeon)
                val currentEnemyHp = when {
                    currentBoss != null -> {
                        val prevDmg = frames.take(currentFrameIdx).sumOf { it.playerHits.sum() }
                        val curDmg = currentFrame?.playerHits?.take(tickInFrame + 1)?.sum() ?: 0
                        (currentBoss.hp - prevDmg - curDmg).coerceAtLeast(0)
                    }
                    currentEnemy != null && currentFrame?.playerHits?.isNotEmpty() == true -> {
                        var hp = currentEnemy.hp
                        for (dmg in currentFrame.playerHits.take(tickInFrame + 1)) {
                            hp -= dmg
                            if (hp <= 0) hp = currentEnemy.hp
                        }
                        hp.coerceAtLeast(0)
                    }
                    else -> currentEnemy?.hp ?: 0
                }

                // Combat log: last 8 entries (interleaved per tick)
                val combatLog = remember(currentFrameIdx, tickInFrame) {
                    buildList<CombatLogEntry> {
                        for (i in 0 until currentFrameIdx) {
                            val f = frames.getOrNull(i) ?: break
                            val eName = bosses.firstOrNull { it.id == f.enemyKey }?.let { GameStrings.bossName(context, it.id) }
                                ?: enemies[f.enemyKey]?.let { GameStrings.enemyName(context, f.enemyKey) } ?: f.enemyKey
                            val enemyHp = if (!isBoss) enemies[f.enemyKey]?.hp ?: Int.MAX_VALUE else Int.MAX_VALUE
                            var hp = enemyHp
                            for (t in 0 until maxOf(f.playerHits.size, f.enemyHits.size)) {
                                f.playerHits.getOrNull(t)?.let { dmg ->
                                    add(CombatLogEntry(true, dmg, eName))
                                    hp -= dmg
                                    if (hp <= 0) { add(CombatLogEntry(false, 0, eName, isKill = true)); hp = enemyHp }
                                }
                                f.enemyHits.getOrNull(t)?.let { add(CombatLogEntry(false, it, eName)) }
                            }
                        }
                        val f = frames.getOrNull(currentFrameIdx) ?: return@buildList
                        val eName = bosses.firstOrNull { it.id == f.enemyKey }?.let { GameStrings.bossName(context, it.id) }
                            ?: enemies[f.enemyKey]?.let { GameStrings.enemyName(context, f.enemyKey) } ?: f.enemyKey
                        val enemyHp = if (!isBoss) enemies[f.enemyKey]?.hp ?: Int.MAX_VALUE else Int.MAX_VALUE
                        var hp = enemyHp
                        for (t in 0..tickInFrame) {
                            f.playerHits.getOrNull(t)?.let { dmg ->
                                add(CombatLogEntry(true, dmg, eName))
                                hp -= dmg
                                if (hp <= 0) { add(CombatLogEntry(false, 0, eName, isKill = true)); hp = enemyHp }
                            }
                            f.enemyHits.getOrNull(t)?.let { add(CombatLogEntry(false, it, eName)) }
                        }
                    }.takeLast(8)
                }

                // Drops and XP from completed frames
                val dropsSoFar = remember(currentFrameIdx) {
                    frames.take(currentFrameIdx).fold(mutableMapOf<String, Int>()) { acc, f ->
                        f.items.forEach { (k, v) -> acc[k] = (acc[k] ?: 0) + v }
                        acc
                    }
                }
                val xpSoFar = remember(currentFrameIdx) {
                    frames.take(currentFrameIdx).fold(mutableMapOf<String, Long>()) { acc, f ->
                        f.xpBySkill.forEach { (k, v) -> acc[k] = (acc[k] ?: 0L) + v }
                        acc
                    }
                }

                // Food remaining (equipped qty minus consumed so far in session)
                val foodRemaining = equippedFood.mapValues { (key, qty) ->
                    (qty - (foodConsumedSoFar[key] ?: 0)).coerceAtLeast(0)
                }.filter { (_, qty) -> qty > 0 }

                Spacer(Modifier.height(16.dp))
                Surface(
                    shape    = RoundedCornerShape(12.dp),
                    color    = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {

                        // ── Enemy ──────────────────────────────────────────
                        if (currentBoss != null) {
                            Text(
                                text       = "${currentBoss.emoji} ${GameStrings.bossName(context, currentBoss.id)}",
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress  = { if (currentBoss.hp > 0) currentEnemyHp / currentBoss.hp.toFloat() else 0f },
                                modifier  = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color     = MaterialTheme.colorScheme.error,
                                trackColor = MaterialTheme.colorScheme.errorContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text  = "${stringResource(R.string.label_hp)} $currentEnemyHp/${currentBoss.hp}  ${stringResource(R.string.combat_atk)} ${currentBoss.combatStats.attackLevel}  ${stringResource(R.string.combat_str)} ${currentBoss.combatStats.strengthLevel}  ${stringResource(R.string.combat_def)} ${currentBoss.combatStats.defenseLevel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        } else if (currentEnemy != null) {
                            Text(
                                text       = GameStrings.enemyName(context, currentEnemy.name),
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress  = { if (currentEnemy.hp > 0) currentEnemyHp / currentEnemy.hp.toFloat() else 0f },
                                modifier  = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color     = MaterialTheme.colorScheme.error,
                                trackColor = MaterialTheme.colorScheme.errorContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text  = "${stringResource(R.string.label_hp)} $currentEnemyHp/${currentEnemy.hp}  ${stringResource(R.string.combat_atk)} ${currentEnemy.combatStats.attackLevel}  ${stringResource(R.string.combat_str)} ${currentEnemy.combatStats.strengthLevel}  ${stringResource(R.string.combat_def)} ${currentEnemy.combatStats.defenseLevel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        } else {
                            Text(
                                text  = stringResource(R.string.combat_fighting),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }

                        // ── Player HP + gear ───────────────────────────────
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                        val hpPct   = currentPlayerHp * 100 / maxHp
                        val hpColor = when {
                            hpPct >= 50 -> Color(0xFF4CAF50)
                            hpPct >= 20 -> Color(0xFFFFC107)
                            else        -> MaterialTheme.colorScheme.error
                        }
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text(
                                text       = "${stringResource(R.string.label_hp)}: $currentPlayerHp / $maxHp",
                                style      = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color      = hpColor,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress  = { if (maxHp > 0) currentPlayerHp / maxHp.toFloat() else 0f },
                            modifier  = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color     = hpColor,
                            trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f),
                        )
                        val atkLabel = stringResource(R.string.combat_atk)
                        val strLabel = stringResource(R.string.combat_str)
                        val defLabel = stringResource(R.string.combat_def)
                        val bonusParts = buildList {
                            if (attackBonus   != 0) add("+$attackBonus $atkLabel")
                            if (strengthBonus != 0) add("+$strengthBonus $strLabel")
                            if (defenseBonus  != 0) add("+$defenseBonus $defLabel")
                        }
                        if (bonusParts.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text  = bonusParts.joinToString("  "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }

                        // ── Equipped food ──────────────────────────────────
                        if (equippedFood.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                            Text(
                                text  = stringResource(R.string.label_food),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(2.dp))
                            for ((key, startQty) in equippedFood) {
                                val remaining = (startQty - (foodConsumedSoFar[key] ?: 0)).coerceAtLeast(0)
                                val heal      = foodHealValues[key] ?: 0
                                val name      = key.replace('_', ' ').replaceFirstChar { it.uppercase() }
                                Text(
                                    text  = "$name ×$remaining (${stringResource(R.string.combat_heals_hp, heal)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (remaining > 0)
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f),
                                )
                            }
                        }

                        // ── Kills ──────────────────────────────────────────
                        if (killsSoFar.isNotEmpty()) {
                            val defeatedSoFar = stringResource(R.string.combat_defeated_so_far)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                            Text(
                                text  = killsSoFar.entries
                                    .sortedByDescending { it.value }
                                    .joinToString(", ") { (k, v) ->
                                        "$v ${bosses.firstOrNull { it.id == k }?.let { GameStrings.bossName(context, it.id) } ?: enemies[k]?.let { GameStrings.enemyName(context, k) } ?: k}"
                                    }
                                    + " $defeatedSoFar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }

                        // ── Drops so far ───────────────────────────────────
                        if (dropsSoFar.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                            Text(
                                text  = stringResource(R.string.label_drops),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text  = dropsSoFar.entries
                                    .sortedByDescending { it.value }
                                    .joinToString("  ") { (k, v) ->
                                        "${GameStrings.itemName(context, k)} ×$v"
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }

                        // ── XP so far ──────────────────────────────────────
                        if (xpSoFar.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                            Text(
                                text  = stringResource(R.string.label_xp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(2.dp))
                            val xpSkillOrder = listOf(
                                Skills.ATTACK, Skills.STRENGTH, Skills.DEFENSE,
                                Skills.RANGED, Skills.MAGIC, Skills.HITPOINTS,
                            )
                            Text(
                                text  = xpSkillOrder
                                    .mapNotNull { skill -> xpSoFar[skill]?.let { skill to it } }
                                    .joinToString("  ") { (skill, xp) ->
                                        "${GameStrings.skillName(context, skill).take(3).uppercase()} +${xp.formatXp()}"
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }

                        // ── Combat log ─────────────────────────────────────
                        if (combatLog.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                            Text(
                                text  = stringResource(R.string.combat_log_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(2.dp))
                            Column {
                                for (entry in combatLog) {
                                    if (entry.isKill) {
                                        Text(
                                            text  = stringResource(R.string.combat_log_kill, entry.enemyName),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = GoldPrimary,
                                        )
                                    } else if (entry.isPlayer) {
                                        val color = if (entry.damage > 0) Color(0xFF4CAF50)
                                                    else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.45f)
                                        val text = if (entry.damage > 0)
                                            stringResource(R.string.combat_log_player_hit, entry.enemyName, entry.damage)
                                        else
                                            stringResource(R.string.combat_log_player_miss, entry.enemyName)
                                        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
                                    } else {
                                        val color = if (entry.damage > 0) MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.45f)
                                        val text = if (entry.damage > 0)
                                            stringResource(R.string.combat_log_enemy_hit, entry.enemyName, entry.damage)
                                        else
                                            stringResource(R.string.combat_log_enemy_miss, entry.enemyName)
                                        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        if (isDone) {
            Button(
                onClick  = onCollect,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.btn_collect_results))
            }
            Spacer(Modifier.height(8.dp))
        }

        if (!isDone) {
            OutlinedButton(
                onClick  = { showAbandonConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.btn_abandon_session))
            }
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

        if (BuildConfig.DEBUG && !isDone) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDebugFinish) {
                Text("[Debug] Finish Now")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Dungeon info / start sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DungeonInfoSheet(
    dungeon: DungeonData,
    skillLevels: Map<String, Int>,
    equippedWeapon: EquipmentData?,
    equippedWeapons: Map<String, EquipmentData>,
    selectedWeaponSlot: String?,
    inventory: Map<String, Int>,
    availableSpells: List<SpellData>,
    selectedSpell: SpellData?,
    availablePotions: Map<String, Int>,
    potionEffects: Map<String, Map<String, Int>>,
    selectedPotionKey: String?,
    selectedArrowKey: String?,
    isStarting: Boolean,
    onWeaponSlotSelected: (String) -> Unit,
    onSpellSelected: (SpellData) -> Unit,
    onPotionSelected: (String?) -> Unit,
    onArrowSelected: (String?) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context    = LocalContext.current
    val combatLvl  = combatLevel(skillLevels)
    val canEnter   = combatLvl >= dungeon.recommendedLevel - UNLOCK_TOLERANCE
    val combatStyle = when (equippedWeapon?.combatStyle) {
        "ranged"   -> "ranged"
        "magic"    -> "magic"
        "strength" -> "strength"
        else       -> "attack"
    }
    val styleLabel = GameStrings.skillName(context, combatStyle)
    val canStart   = canEnter && !isStarting &&
        (combatStyle != "magic" || selectedSpell != null)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
        Column(modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())) {
        Text(
            text       = GameStrings.dungeonName(context, dungeon.name),
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text  = GameStrings.dungeonDesc(context, dungeon.name).takeIf { it.isNotBlank() } ?: dungeon.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        // Level and combat style rows
        StatRow(label = stringResource(R.string.combat_rec_level),
            value = dungeon.recommendedLevel.toString(),
            valueColor = if (canEnter) GoldPrimary else MaterialTheme.colorScheme.error)
        StatRow(label = stringResource(R.string.combat_your_level), value = combatLvl.toString())
        StatRow(label = stringResource(R.string.label_combat_style), value = styleLabel, valueColor = GoldPrimary)

        // Ranged: arrow picker
        if (combatStyle == "ranged") {
            val availableArrows = ARROW_TIERS.filter { (inventory[it] ?: 0) > 0 }
            Text(
                text  = stringResource(R.string.combat_label_arrow),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            val arrowOptions = listOf(null) + availableArrows
            arrowOptions.forEach { key ->
                val isSelected = selectedArrowKey == key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onArrowSelected(key) }
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = if (key == null) stringResource(R.string.combat_arrow_auto)
                                         else GameStrings.itemName(context, key),
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color      = if (isSelected) GoldPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (key != null) {
                        Text(
                            text  = "\u00d7${inventory[key]}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isSelected) {
                        Text("\u2713", style = MaterialTheme.typography.bodyMedium,
                            color = GoldPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(12.dp))

        // Enemy spawn list
        if (dungeon.enemySpawns.isNotEmpty()) {
            Text(
                text  = stringResource(R.string.combat_enemies),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            dungeon.enemySpawns.forEach { spawn ->
                Text(
                    text  = "• ${GameStrings.itemName(context, spawn.enemy)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // Weapon picker (show when 2+ weapon slots are occupied, or always if any weapon is equipped)
        if (equippedWeapons.isNotEmpty()) {
            Text(
                text  = "Weapon",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                equippedWeapons.forEach { (slot, weaponData) ->
                    val isSelected = slot == (selectedWeaponSlot
                        ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equippedWeapons.containsKey(it) })
                    FilterChip(
                        selected = isSelected,
                        onClick  = { onWeaponSlotSelected(slot) },
                        label    = {
                            Column {
                                Text(
                                    text  = GameStrings.itemName(context, weaponData.name),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                weaponData.combatStyle?.let { style ->
                                    Text(
                                        text  = style.replaceFirstChar { it.titlecase() },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Magic: spell picker
        if (combatStyle == "magic") {
            Text(
                text  = stringResource(R.string.label_spell),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            if (availableSpells.isEmpty()) {
                Text(
                    text  = stringResource(R.string.combat_no_spells),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                var onlyCastable by remember { mutableStateOf(true) }
                val displaySpells = if (onlyCastable)
                    availableSpells.filter { spell ->
                        equippedWeapon?.infiniteRunes == spell.runeType ||
                        (inventory[spell.runeType] ?: 0) >= spell.runeCost
                    }
                else
                    availableSpells
                Row(modifier = Modifier.padding(bottom = 4.dp)) {
                    FilterChip(
                        selected = onlyCastable,
                        onClick  = { onlyCastable = !onlyCastable },
                        label    = { Text(stringResource(R.string.combat_only_castable)) },
                    )
                }
                displaySpells.forEach { spell ->
                    val isSelected = selectedSpell?.name == spell.name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSpellSelected(spell) }
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text       = GameStrings.spellName(context, spell.name),
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color      = if (isSelected) GoldPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text  = "${spell.runeCost}\u00d7 ${GameStrings.itemName(context, spell.runeType)}  \u2022  ${stringResource(R.string.combat_max_hit)} ${spell.maxHit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val infinite = equippedWeapon?.infiniteRunes == spell.runeType
                            if (!infinite) {
                                val held = inventory[spell.runeType] ?: 0
                                Text(
                                    text  = stringResource(R.string.combat_you_have_runes, held, GameStrings.itemName(context, spell.runeType)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (held >= spell.runeCost) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (isSelected) {
                            Text(
                                text  = "\u2713",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GoldPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        // Potion picker
        if (availablePotions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text  = "Potion",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            val potionOptions = listOf(null) + availablePotions.keys.toList()
            potionOptions.forEach { key ->
                val isSelected = selectedPotionKey == key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPotionSelected(key) }
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = if (key == null) stringResource(R.string.combat_no_potion)
                                         else GameStrings.itemName(context, key),
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color      = if (isSelected) GoldPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                        if (key != null) {
                            val effectStr = potionEffects[key]?.entries
                                ?.joinToString(", ") { (stat, bonus) -> "+$bonus ${stat.toTitleCase()}" }
                            if (effectStr != null) {
                                Text(
                                    text  = "($effectStr)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (key != null) {
                        Text(
                            text  = "×${availablePotions[key]}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isSelected) {
                        Text("✓", style = MaterialTheme.typography.bodyMedium,
                            color = GoldPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        } // end scrollable content

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.btn_cancel))
            }
            Button(
                onClick  = onStart,
                modifier = Modifier.weight(1f),
                enabled  = canStart,
            ) {
                if (isStarting) CircularProgressIndicator(
                    modifier  = Modifier.height(20.dp).width(20.dp),
                    strokeWidth = 2.dp,
                )
                else Text(stringResource(R.string.btn_enter_dungeon))
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
) {
    val resolvedColor = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface
                        else valueColor
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold, color = resolvedColor)
    }
}

// ---------------------------------------------------------------------------
// Boss info / start sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BossInfoSheet(
    boss: BossData,
    skillLevels: Map<String, Int>,
    equippedWeapon: EquipmentData?,
    equippedWeapons: Map<String, EquipmentData>,
    selectedWeaponSlot: String?,
    inventory: Map<String, Int>,
    availableSpells: List<SpellData>,
    selectedSpell: SpellData?,
    availablePotions: Map<String, Int>,
    potionEffects: Map<String, Map<String, Int>>,
    selectedPotionKey: String?,
    selectedArrowKey: String?,
    isStarting: Boolean,
    onWeaponSlotSelected: (String) -> Unit,
    onSpellSelected: (SpellData) -> Unit,
    onArrowSelected: (String?) -> Unit,
    onPotionSelected: (String?) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context   = LocalContext.current
    val combatLvl = combatLevel(skillLevels)
    val canFight  = combatLvl >= boss.combatLevelRequired
    val combatStyle = when (equippedWeapon?.combatStyle) {
        "ranged"   -> "ranged"
        "magic"    -> "magic"
        "strength" -> "strength"
        else       -> "attack"
    }
    val styleLabel = GameStrings.skillName(context, combatStyle)
    val canStart = canFight && !isStarting &&
        (combatStyle != "magic" || selectedSpell != null)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
        Column(modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())) {
        Text(
            text       = "${boss.emoji} ${GameStrings.bossName(context, boss.id)}",
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text  = GameStrings.bossDesc(context, boss.id).takeIf { it.isNotBlank() } ?: boss.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.combat_req_level), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text       = boss.combatLevelRequired.toString(),
                style      = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color      = if (canFight) GoldPrimary else MaterialTheme.colorScheme.error,
            )
        }
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.combat_your_level), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(combatLvl.toString(), style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold)
        }
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.combat_duration), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.combat_duration_min, boss.durationMinutes), style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold)
        }
        StatRow(label = stringResource(R.string.label_combat_style), value = styleLabel, valueColor = GoldPrimary)

        // Weapon picker
        if (equippedWeapons.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text  = "Weapon",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                equippedWeapons.forEach { (slot, weaponData) ->
                    val isSelected = slot == (selectedWeaponSlot
                        ?: EquipSlot.WEAPON_SLOTS.firstOrNull { equippedWeapons.containsKey(it) })
                    FilterChip(
                        selected = isSelected,
                        onClick  = { onWeaponSlotSelected(slot) },
                        label    = {
                            Column {
                                Text(
                                    text  = GameStrings.itemName(context, weaponData.name),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                weaponData.combatStyle?.let { style ->
                                    Text(
                                        text  = style.replaceFirstChar { it.titlecase() },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }

        // Ranged: arrow picker
        if (combatStyle == "ranged") {
            val availableArrows = ARROW_TIERS.filter { (inventory[it] ?: 0) > 0 }
            Spacer(Modifier.height(12.dp))
            Text(
                text  = stringResource(R.string.combat_label_arrow),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            val arrowOptions = listOf(null) + availableArrows
            arrowOptions.forEach { key ->
                val isSelected = selectedArrowKey == key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onArrowSelected(key) }
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = if (key == null) stringResource(R.string.combat_arrow_auto)
                                         else GameStrings.itemName(context, key),
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color      = if (isSelected) GoldPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (key != null) {
                        Text(
                            text  = "×${inventory[key]}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isSelected) {
                        Text("✓", style = MaterialTheme.typography.bodyMedium,
                            color = GoldPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Magic: spell picker
        if (combatStyle == "magic") {
            Spacer(Modifier.height(12.dp))
            Text(
                text  = stringResource(R.string.label_spell),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            if (availableSpells.isEmpty()) {
                Text(
                    text  = stringResource(R.string.combat_no_spells),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                var onlyCastable by remember { mutableStateOf(true) }
                val displaySpells = if (onlyCastable)
                    availableSpells.filter { spell ->
                        equippedWeapon?.infiniteRunes == spell.runeType ||
                        (inventory[spell.runeType] ?: 0) >= spell.runeCost
                    }
                else availableSpells
                Row(modifier = Modifier.padding(bottom = 4.dp)) {
                    FilterChip(
                        selected = onlyCastable,
                        onClick  = { onlyCastable = !onlyCastable },
                        label    = { Text(stringResource(R.string.combat_only_castable)) },
                    )
                }
                displaySpells.forEach { spell ->
                    val isSelected = selectedSpell?.name == spell.name
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSpellSelected(spell) }
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text       = GameStrings.spellName(context, spell.name),
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color      = if (isSelected) GoldPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text  = "${spell.runeCost}× ${GameStrings.itemName(context, spell.runeType)}  •  ${stringResource(R.string.combat_max_hit)} ${spell.maxHit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val infinite = equippedWeapon?.infiniteRunes == spell.runeType
                            if (!infinite) {
                                val held = inventory[spell.runeType] ?: 0
                                Text(
                                    text  = stringResource(R.string.combat_you_have_runes, held, GameStrings.itemName(context, spell.runeType)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (held >= spell.runeCost) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (isSelected) {
                            Text("✓", style = MaterialTheme.typography.bodyMedium,
                                color = GoldPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (boss.xpRewards.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.combat_xp_on_victory), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            for ((skill, xp) in boss.xpRewards) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(GameStrings.skillName(context, skill),
                        style = MaterialTheme.typography.bodySmall)
                    Text("+$xp ${stringResource(R.string.label_xp)}", style = MaterialTheme.typography.bodySmall,
                        color = GoldPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Potion picker
        if (availablePotions.isNotEmpty()) {
            val context = LocalContext.current
            Spacer(Modifier.height(12.dp))
            Text(
                text  = "Potion",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            val potionOptions = listOf(null) + availablePotions.keys.toList()
            potionOptions.forEach { key ->
                val isSelected = selectedPotionKey == key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPotionSelected(key) }
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = if (key == null) stringResource(R.string.combat_no_potion)
                                         else GameStrings.itemName(context, key),
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color      = if (isSelected) GoldPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                        if (key != null) {
                            val effectStr = potionEffects[key]?.entries
                                ?.joinToString(", ") { (stat, bonus) -> "+$bonus ${stat.toTitleCase()}" }
                            if (effectStr != null) {
                                Text(
                                    text  = "($effectStr)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (key != null) {
                        Text(
                            text  = "×${availablePotions[key]}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isSelected) {
                        Text("✓", style = MaterialTheme.typography.bodyMedium,
                            color = GoldPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        } // end scrollable content

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.btn_cancel))
            }
            Button(
                onClick  = onStart,
                modifier = Modifier.weight(1f),
                enabled  = canStart,
            ) {
                if (isStarting) CircularProgressIndicator(
                    modifier    = Modifier.height(20.dp).width(20.dp),
                    strokeWidth = 2.dp,
                ) else Text(stringResource(R.string.btn_fight))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Combat result sheet
// ---------------------------------------------------------------------------

@Composable
private fun CombatResultSheet(
    result: CombatSessionResult,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
        if (!result.won) {
            Text(
                text       = stringResource(R.string.combat_you_died),
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(2.dp))
        }
        Text(
            text       = result.dungeonDisplayName,
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text  = if (result.won) stringResource(R.string.label_session_results)
                    else stringResource(R.string.combat_died_reward),
            style = MaterialTheme.typography.bodySmall,
            color = if (result.won) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))

        // XP per skill
        if (result.xpPerSkill.isNotEmpty()) {
            Text(
                text  = if (result.won) stringResource(R.string.label_xp_gained) else stringResource(R.string.combat_xp_consolation),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val orderedSkills = listOf(
                Skills.ATTACK, Skills.STRENGTH, Skills.DEFENSE,
                Skills.RANGED, Skills.MAGIC, Skills.HITPOINTS, Skills.PRAYER,
            )
            for (skill in orderedSkills) {
                val xp = result.xpPerSkill[skill] ?: continue
                if (xp <= 0L) continue
                val bonus = result.xpBlessingBonusBySkill[skill] ?: 0L
                val breakdown = xpBreakdownText(xp, bonus, result.boostWasActive)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text  = GameStrings.skillName(context, skill),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text       = "+${xp.formatXp()} ${stringResource(R.string.label_xp)}",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = GoldPrimary,
                        )
                        if (breakdown != null) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text  = breakdown,
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldPrimary,
                            )
                        }
                    }
                }
            }
            if (result.boostWasActive) {
                Text(
                    text  = stringResource(R.string.home_xp_boost_was_active),
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldPrimary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // Coins gained
        if (result.coinsGained > 0L) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text  = stringResource(R.string.label_coins),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text       = "+${result.coinsGained.formatCoins()}",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = GoldPrimary,
                )
            }
            if (result.coinBlessingBonus > 0L) {
                Text(
                    text  = stringResource(R.string.church_blessing_bonus, result.coinBlessingBonus.formatCoins()),
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldPrimary,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // Items gained
        if (result.itemsGained.isNotEmpty()) {
            Text(
                text  = stringResource(R.string.label_items_collected),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for ((item, qty) in result.itemsGained) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text  = GameStrings.itemName(context, item),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text  = "×$qty",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Kills
        if (result.killsByEnemy.isNotEmpty()) {
            Text(
                text  = stringResource(R.string.label_kills),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for ((enemy, qty) in result.killsByEnemy.entries.sortedByDescending { it.value }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text  = GameStrings.enemyName(context, enemy),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text  = "×$qty",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Food consumed
        if (result.foodConsumed.isNotEmpty()) {
            Text(
                text  = stringResource(R.string.label_food),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for ((food, qty) in result.foodConsumed) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = GameStrings.itemName(context, food), style = MaterialTheme.typography.bodyMedium)
                    Text(text = "×$qty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Arrows consumed / reclaimed
        if (result.arrowsConsumed.isNotEmpty()) {
            Text(text = stringResource(R.string.label_arrows_consumed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for ((arrow, qty) in result.arrowsConsumed) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = GameStrings.itemName(context, arrow), style = MaterialTheme.typography.bodyMedium)
                    Text(text = "×$qty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (result.arrowsReclaimed.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(text = stringResource(R.string.label_arrows_reclaimed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for ((arrow, qty) in result.arrowsReclaimed) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = GameStrings.itemName(context, arrow), style = MaterialTheme.typography.bodyMedium)
                        Text(text = "+$qty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Runes consumed / reclaimed
        if (result.runesConsumed.isNotEmpty()) {
            Text(text = stringResource(R.string.label_runes_consumed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for ((rune, qty) in result.runesConsumed) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = GameStrings.itemName(context, rune), style = MaterialTheme.typography.bodyMedium)
                    Text(text = "×$qty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (result.runesReclaimed.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(text = stringResource(R.string.label_runes_reclaimed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for ((rune, qty) in result.runesReclaimed) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = GameStrings.itemName(context, rune), style = MaterialTheme.typography.bodyMedium)
                        Text(text = "+$qty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.btn_close))
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Simplified OSRS combat level formula using melee stats only. */
private fun combatLevel(levels: Map<String, Int>): Int {
    val attack  = levels[Skills.ATTACK]    ?: 1
    val strength = levels[Skills.STRENGTH] ?: 1
    val defence  = levels[Skills.DEFENSE]  ?: 1
    val hp       = levels[Skills.HITPOINTS] ?: 1
    return (((attack + strength) * 0.325) + (defence + hp) * 0.25).toInt().coerceAtLeast(1)
}

/** Dungeons within this many levels of the recommendation are still enterable. */
private const val UNLOCK_TOLERANCE = 5

/** Arrow tiers from best to worst — mirrors CombatViewModel.ARROW_TIERS. */
private val ARROW_TIERS = listOf(
    "runite_arrow", "adamantite_arrow", "mithril_arrow",
    "steel_arrow", "iron_arrow", "bronze_arrow",
)
