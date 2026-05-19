package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.json.SpellData
import com.fantasyidler.data.model.Skills
import com.fantasyidler.ui.components.EmptyState
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant
import com.fantasyidler.ui.components.foundation.ChunkyDialog
import com.fantasyidler.ui.screen.combat.CombatSelectionList
import com.fantasyidler.ui.screen.combat.CombatSessionBanner
import com.fantasyidler.ui.screen.combat.CombatSkillsTab
import com.fantasyidler.ui.screen.combat.sheets.BossInfoSheet
import com.fantasyidler.ui.screen.combat.sheets.CombatResultSheet
import com.fantasyidler.ui.screen.combat.sheets.DungeonInfoSheet
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.ui.viewmodel.CombatSessionResult
import com.fantasyidler.ui.viewmodel.CombatUiState
import com.fantasyidler.ui.viewmodel.CombatViewModel

/**
 * Combat screen — thin orchestrator over the [com.fantasyidler.ui.screen.combat]
 * sub-package. Owns:
 *  - state hoisting from [CombatViewModel],
 *  - the top-level tab switch (active session vs. selection vs. skills),
 *  - launching the boss / dungeon / result sheets,
 *  - top-level loading / empty / error scaffolding,
 *  - the no-food confirmation [ChunkyDialog].
 *
 * Every UI chunk past that boundary lives in its own file in
 * `ui/screen/combat/` (siblings) or `ui/screen/combat/sheets/`.
 */
@Composable
fun CombatScreen(
    viewModel: CombatViewModel = hiltViewModel(),
) {
    val state             by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.snackbarConsumed()
        }
    }

    CombatScreenContent(
        state             = state,
        snackbarHostState = snackbarHostState,
        dungeons          = viewModel.dungeonList,
        bosses            = viewModel.bossList,
        enemies           = viewModel.enemyMap,
        foodHealValues    = viewModel.foodHealValues,
        availableSpells   = { viewModel.availableSpells(it) },
        onSelectDungeon   = viewModel::selectDungeon,
        onSelectBoss      = viewModel::selectBoss,
        onSelectPotion    = viewModel::selectPotion,
        onSelectSpell     = viewModel::selectSpell,
        onStartDungeon    = { viewModel.startDungeonSession(it) },
        onStartBoss       = { viewModel.startBossSession(it) },
        onCollect         = viewModel::collectSession,
        onAbandon         = viewModel::abandonSession,
        onDebugFinish     = viewModel::debugFinishSession,
        onResultConsumed  = viewModel::resultConsumed,
        onConfirmNoFood   = viewModel::confirmStartWithoutFood,
        onDismissNoFood   = viewModel::dismissNoFoodWarning,
    )
}

@Composable
private fun CombatScreenContent(
    state: CombatUiState,
    snackbarHostState: SnackbarHostState,
    dungeons: List<DungeonData>,
    bosses: List<BossData>,
    enemies: Map<String, EnemyData>,
    foodHealValues: Map<String, Int>,
    availableSpells: (Map<String, Int>) -> List<SpellData>,
    onSelectDungeon: (DungeonData?) -> Unit,
    onSelectBoss: (BossData?) -> Unit,
    onSelectPotion: (String?) -> Unit,
    onSelectSpell: (SpellData) -> Unit,
    onStartDungeon: (String) -> Unit,
    onStartBoss: (String) -> Unit,
    onCollect: () -> Unit,
    onAbandon: () -> Unit,
    onDebugFinish: () -> Unit,
    onResultConsumed: () -> Unit,
    onConfirmNoFood: () -> Unit,
    onDismissNoFood: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            state.combatSession != null -> ActiveSessionTabs(
                state          = state,
                dungeons       = dungeons,
                bosses         = bosses,
                enemies        = enemies,
                foodHealValues = foodHealValues,
                onSelectDungeon = onSelectDungeon,
                onSelectBoss    = onSelectBoss,
                onCollect       = onCollect,
                onAbandon       = onAbandon,
                onDebugFinish   = onDebugFinish,
                modifier        = Modifier.padding(padding),
            )
            else -> NoSessionTabs(
                state           = state,
                dungeons        = dungeons,
                bosses          = bosses,
                onSelectDungeon = onSelectDungeon,
                onSelectBoss    = onSelectBoss,
                modifier        = Modifier.padding(padding),
            )
        }
    }

    state.selectedBoss?.let { boss ->
        BossInfoSheet(
            boss              = boss,
            skillLevels       = state.skillLevels,
            availablePotions  = state.availablePotions,
            selectedPotionKey = state.selectedPotionKey,
            isStarting        = state.startingSession,
            onPotionSelected  = onSelectPotion,
            onStart           = { onStartBoss(boss.id) },
            onDismiss         = { onSelectBoss(null) },
        )
    }

    state.selectedDungeon?.let { dungeon ->
        DungeonInfoSheet(
            dungeon           = dungeon,
            skillLevels       = state.skillLevels,
            equippedWeapon    = state.equippedWeapon,
            inventory         = state.inventory,
            availableSpells   = availableSpells(state.skillLevels),
            selectedSpell     = state.selectedSpell,
            availablePotions  = state.availablePotions,
            selectedPotionKey = state.selectedPotionKey,
            isStarting        = state.startingSession,
            onSpellSelected   = onSelectSpell,
            onPotionSelected  = onSelectPotion,
            onStart           = { onStartDungeon(dungeon.name) },
            onDismiss         = { onSelectDungeon(null) },
        )
    }

    state.combatResult?.let { result ->
        CombatResultSheet(result = result, onDismiss = onResultConsumed)
    }

    if (state.noFoodWarningPending) {
        ChunkyDialog(
            title            = stringResource(R.string.combat_no_food_title),
            onDismissRequest = onDismissNoFood,
            body             = { Text(stringResource(R.string.combat_no_food_message)) },
            actions = {
                ChunkyButton(
                    text    = stringResource(R.string.btn_cancel),
                    onClick = onDismissNoFood,
                    variant = ChunkyButtonVariant.Secondary,
                )
                ChunkyButton(
                    text    = stringResource(R.string.combat_no_food_start_anyway),
                    onClick = onConfirmNoFood,
                    variant = ChunkyButtonVariant.Destructive,
                )
            },
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    val tokens = LocalFantasyTokens.current
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = tokens.colors.primary)
            Text(
                text     = stringResource(R.string.combat_loading),
                style    = tokens.typography.bodyLarge,
                color    = tokens.colors.onSurfaceMuted,
                modifier = Modifier.padding(top = tokens.spacing.m),
            )
        }
    }
}

@Composable
private fun ActiveSessionTabs(
    state: CombatUiState,
    dungeons: List<DungeonData>,
    bosses: List<BossData>,
    enemies: Map<String, EnemyData>,
    foodHealValues: Map<String, Int>,
    onSelectDungeon: (DungeonData?) -> Unit,
    onSelectBoss: (BossData?) -> Unit,
    onCollect: () -> Unit,
    onAbandon: () -> Unit,
    onDebugFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = state.combatSession ?: return
    var activeTab by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = activeTab) {
            Tab(
                selected = activeTab == 0,
                onClick  = { activeTab = 0 },
                text     = { Text(stringResource(R.string.combat_log_label)) },
            )
            Tab(
                selected = activeTab == 1,
                onClick  = { activeTab = 1 },
                text     = { Text(stringResource(R.string.label_dungeons_tab)) },
            )
        }
        when (activeTab) {
            0 -> CombatSessionBanner(
                session        = session,
                dungeons       = dungeons,
                bosses         = bosses,
                enemies        = enemies,
                skillLevels    = state.skillLevels,
                attackBonus    = state.totalAttackBonus,
                strengthBonus  = state.totalStrengthBonus,
                defenseBonus   = state.totalDefenseBonus,
                equippedFood   = state.equippedFood,
                foodHealValues = foodHealValues,
                onCollect      = onCollect,
                onAbandon      = onAbandon,
                onDebugFinish  = onDebugFinish,
            )
            1 -> CombatSelectionList(
                dungeons        = dungeons,
                bosses          = bosses,
                skillLevels     = state.skillLevels,
                survivalRatings = state.dungeonSurvivalRatings,
                onDungeon       = onSelectDungeon,
                onBoss          = onSelectBoss,
            )
        }
    }
}

@Composable
private fun NoSessionTabs(
    state: CombatUiState,
    dungeons: List<DungeonData>,
    bosses: List<BossData>,
    onSelectDungeon: (DungeonData?) -> Unit,
    onSelectBoss: (BossData?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick  = { selectedTab = 0 },
                text     = { Text(stringResource(R.string.label_dungeons_tab)) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick  = { selectedTab = 1 },
                text     = { Text(stringResource(R.string.label_skills)) },
            )
        }
        when (selectedTab) {
            0 -> {
                if (dungeons.isEmpty() && bosses.isEmpty()) {
                    EmptyState(
                        title       = stringResource(R.string.combat_empty_selection_title),
                        description = stringResource(R.string.combat_empty_selection_description),
                    )
                } else {
                    CombatSelectionList(
                        dungeons        = dungeons,
                        bosses          = bosses,
                        skillLevels     = state.skillLevels,
                        survivalRatings = state.dungeonSurvivalRatings,
                        onDungeon       = onSelectDungeon,
                        onBoss          = onSelectBoss,
                    )
                }
            }
            1 -> {
                if (state.skillLevels.isEmpty()) {
                    EmptyState(
                        title       = stringResource(R.string.combat_empty_skills_title),
                        description = stringResource(R.string.combat_empty_skills_description),
                    )
                } else {
                    CombatSkillsTab(
                        skillLevels        = state.skillLevels,
                        skillXp            = state.skillXp,
                        totalAttackBonus   = state.totalAttackBonus,
                        totalStrengthBonus = state.totalStrengthBonus,
                        totalDefenseBonus  = state.totalDefenseBonus,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private val previewSkillLevels = mapOf(
    Skills.ATTACK to 42, Skills.STRENGTH to 35, Skills.DEFENSE to 31,
    Skills.RANGED to 18, Skills.MAGIC to 12, Skills.HITPOINTS to 36, Skills.PRAYER to 8,
)

private val previewSkillXp = mapOf(
    Skills.ATTACK to 100_000L, Skills.STRENGTH to 60_000L, Skills.DEFENSE to 45_000L,
    Skills.RANGED to 5_000L, Skills.MAGIC to 1_500L, Skills.HITPOINTS to 65_000L, Skills.PRAYER to 500L,
)

@PreviewLightDark
@Composable
private fun PreviewCombatScreenLoading() {
    FantasyPreviewSurface {
        CombatScreenContent(
            state             = CombatUiState(isLoading = true),
            snackbarHostState = remember { SnackbarHostState() },
            dungeons          = emptyList(),
            bosses            = emptyList(),
            enemies           = emptyMap(),
            foodHealValues    = emptyMap(),
            availableSpells   = { emptyList() },
            onSelectDungeon   = {},
            onSelectBoss      = {},
            onSelectPotion    = {},
            onSelectSpell     = {},
            onStartDungeon    = {},
            onStartBoss       = {},
            onCollect         = {},
            onAbandon         = {},
            onDebugFinish     = {},
            onResultConsumed  = {},
            onConfirmNoFood   = {},
            onDismissNoFood   = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewCombatScreenEmpty() {
    FantasyPreviewSurface {
        CombatScreenContent(
            state             = CombatUiState(isLoading = false),
            snackbarHostState = remember { SnackbarHostState() },
            dungeons          = emptyList(),
            bosses            = emptyList(),
            enemies           = emptyMap(),
            foodHealValues    = emptyMap(),
            availableSpells   = { emptyList() },
            onSelectDungeon   = {},
            onSelectBoss      = {},
            onSelectPotion    = {},
            onSelectSpell     = {},
            onStartDungeon    = {},
            onStartBoss       = {},
            onCollect         = {},
            onAbandon         = {},
            onDebugFinish     = {},
            onResultConsumed  = {},
            onConfirmNoFood   = {},
            onDismissNoFood   = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewCombatScreenSkillsTab() {
    FantasyPreviewSurface {
        CombatScreenContent(
            state             = CombatUiState(
                isLoading          = false,
                skillLevels        = previewSkillLevels,
                skillXp            = previewSkillXp,
                totalAttackBonus   = 12,
                totalStrengthBonus = 10,
                totalDefenseBonus  = 8,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            dungeons          = emptyList(),
            bosses            = emptyList(),
            enemies           = emptyMap(),
            foodHealValues    = emptyMap(),
            availableSpells   = { emptyList() },
            onSelectDungeon   = {},
            onSelectBoss      = {},
            onSelectPotion    = {},
            onSelectSpell     = {},
            onStartDungeon    = {},
            onStartBoss       = {},
            onCollect         = {},
            onAbandon         = {},
            onDebugFinish     = {},
            onResultConsumed  = {},
            onConfirmNoFood   = {},
            onDismissNoFood   = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewCombatScreenNoFoodDialog() {
    FantasyPreviewSurface {
        CombatScreenContent(
            state             = CombatUiState(
                isLoading            = false,
                noFoodWarningPending = true,
                skillLevels          = previewSkillLevels,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            dungeons          = emptyList(),
            bosses            = emptyList(),
            enemies           = emptyMap(),
            foodHealValues    = emptyMap(),
            availableSpells   = { emptyList() },
            onSelectDungeon   = {},
            onSelectBoss      = {},
            onSelectPotion    = {},
            onSelectSpell     = {},
            onStartDungeon    = {},
            onStartBoss       = {},
            onCollect         = {},
            onAbandon         = {},
            onDebugFinish     = {},
            onResultConsumed  = {},
            onConfirmNoFood   = {},
            onDismissNoFood   = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewCombatScreenResultOverlay() {
    FantasyPreviewSurface {
        CombatScreenContent(
            state             = CombatUiState(
                isLoading    = false,
                skillLevels  = previewSkillLevels,
                combatResult = CombatSessionResult(
                    dungeonDisplayName = "Dark Cave",
                    xpPerSkill         = mapOf(Skills.ATTACK to 2400L, Skills.STRENGTH to 1800L),
                    itemsGained        = mapOf("bones" to 8, "copper_ore" to 3),
                    coinsGained        = 412L,
                    won                = true,
                ),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            dungeons          = emptyList(),
            bosses            = emptyList(),
            enemies           = emptyMap(),
            foodHealValues    = emptyMap(),
            availableSpells   = { emptyList() },
            onSelectDungeon   = {},
            onSelectBoss      = {},
            onSelectPotion    = {},
            onSelectSpell     = {},
            onStartDungeon    = {},
            onStartBoss       = {},
            onCollect         = {},
            onAbandon         = {},
            onDebugFinish     = {},
            onResultConsumed  = {},
            onConfirmNoFood   = {},
            onDismissNoFood   = {},
        )
    }
}

