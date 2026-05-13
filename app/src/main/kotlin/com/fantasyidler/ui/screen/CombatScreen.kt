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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.SpellData
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.Skills
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.CombatSessionResult
import com.fantasyidler.ui.viewmodel.CombatViewModel
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.ui.viewmodel.xpProgressFraction
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.toCountdown
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CombatScreen(
    viewModel: CombatViewModel = hiltViewModel(),
) {
    val state            by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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
                            text       = "Combat Lvl ${combatLevelFrom(state.skillLevels)}",
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
            CombatSessionBanner(
                session        = combatSession,
                dungeons       = viewModel.dungeonList,
                bosses         = viewModel.bossList,
                enemies        = viewModel.enemyMap,
                skillLevels    = state.skillLevels,
                attackBonus    = state.totalAttackBonus,
                strengthBonus  = state.totalStrengthBonus,
                defenseBonus   = state.totalDefenseBonus,
                equippedFood   = state.equippedFood,
                foodHealValues = viewModel.foodHealValues,
                modifier       = Modifier.padding(padding),
                onCollect      = viewModel::collectSession,
                onAbandon      = viewModel::abandonSession,
                onDebugFinish  = viewModel::debugFinishSession,
            )
        } else {
            var selectedTab by remember { mutableIntStateOf(0) }
            Column(Modifier.padding(padding)) {
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
                    0 -> CombatSelectionList(
                        dungeons         = viewModel.dungeonList,
                        bosses           = viewModel.bossList,
                        skillLevels      = state.skillLevels,
                        survivalRatings  = state.dungeonSurvivalRatings,
                        onDungeon        = viewModel::selectDungeon,
                        onBoss           = viewModel::selectBoss,
                    )
                    1 -> CombatSkillsTab(
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

    // Boss info / confirm sheet
    state.selectedBoss?.let { boss ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectBoss(null) },
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            BossInfoSheet(
                boss        = boss,
                skillLevels = state.skillLevels,
                isStarting  = state.startingSession,
                onStart     = { viewModel.startBossSession(boss.id) },
                onDismiss   = { viewModel.selectBoss(null) },
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
                dungeon         = dungeon,
                skillLevels     = state.skillLevels,
                equippedWeapon  = state.equippedWeapon,
                inventory       = state.inventory,
                availableSpells = viewModel.availableSpells(state.skillLevels),
                selectedSpell   = state.selectedSpell,
                isStarting      = state.startingSession,
                onSpellSelected = viewModel::selectSpell,
                onStart         = { viewModel.startDungeonSession(dungeon.name) },
                onDismiss       = { viewModel.selectDungeon(null) },
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
            title = { Text("No food equipped") },
            text  = { Text("You have no food equipped. Without food you may die quickly and lose most of your rewards. Start anyway?") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmStartWithoutFood) {
                    Text("Start anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissNoFoodWarning) {
                    Text("Cancel")
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
    modifier: Modifier = Modifier,
    onDungeon: (DungeonData) -> Unit,
    onBoss: (BossData) -> Unit,
) {
    val combatLvl = combatLevel(skillLevels)

    LazyColumn(modifier.fillMaxSize()) {
        item { CombatSectionHeader("Dungeons") }
        items(dungeons) { dungeon ->
            DungeonRow(
                dungeon        = dungeon,
                unlocked       = combatLvl >= dungeon.recommendedLevel - UNLOCK_TOLERANCE,
                survivalRating = survivalRatings[dungeon.name],
                onTap          = { onDungeon(dungeon) },
            )
        }
        item { CombatSectionHeader("Solo Bosses") }
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
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(COMBAT_SKILLS) { key ->
            val gearBonus = when (key) {
                Skills.ATTACK  -> totalAttackBonus
                Skills.STRENGTH -> totalStrengthBonus
                Skills.DEFENSE -> totalDefenseBonus
                else           -> 0
            }
            CombatSkillRow(
                skillKey  = key,
                level     = skillLevels[key] ?: 1,
                xp        = skillXp[key]     ?: 0L,
                gearBonus = gearBonus,
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
) {
    val context  = LocalContext.current
    val name     = GameStrings.skillName(context, skillKey)
    val emoji    = GameStrings.skillEmoji(skillKey)
    val progress = xpProgressFraction(xp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                            text  = "+$gearBonus gear",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text  = "${xp.formatXp()} XP",
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
    val dimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = unlocked, onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = boss.emoji,
            style    = MaterialTheme.typography.titleLarge,
            modifier = Modifier.width(36.dp),
            color    = if (unlocked) MaterialTheme.colorScheme.onSurface else dimColor,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text       = boss.displayName,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = if (unlocked) MaterialTheme.colorScheme.onSurface else dimColor,
            )
            Text(
                text     = boss.description,
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
    onTap: () -> Unit,
) {
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
                text       = dungeon.displayName,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = if (unlocked) MaterialTheme.colorScheme.onSurface else dimColor,
            )
            Text(
                text     = dungeon.description,
                style    = MaterialTheme.typography.bodySmall,
                color    = if (unlocked) MaterialTheme.colorScheme.onSurfaceVariant
                           else dimColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (unlocked && survivalRating != null) {
                val (ratingText, ratingColor) = when (survivalRating) {
                    CombatSimulator.SurvivalRating.LIKELY   -> "Looks manageable" to MaterialTheme.colorScheme.primary
                    CombatSimulator.SurvivalRating.RISKY    -> "Risky with current setup" to MaterialTheme.colorScheme.tertiary
                    CombatSimulator.SurvivalRating.UNLIKELY -> "Likely to die" to MaterialTheme.colorScheme.error
                }
                Text(
                    text  = ratingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = ratingColor,
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

private data class CombatLogEntry(val isPlayer: Boolean, val damage: Int, val enemyName: String)

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
    val dungeonName = dungeons.firstOrNull { it.name == session.activityKey }?.displayName
        ?: bosses.firstOrNull { it.id == session.activityKey }?.let { "${it.emoji} ${it.displayName}" }
        ?: session.activityKey

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
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
    val perFrameMs = ((session.endsAt - session.startedAt) / 60L).coerceAtLeast(1L)
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

    val killsSoFar: Map<String, Int> = remember(currentFrameIdx) {
        frames.take(currentFrameIdx).fold(mutableMapOf()) { acc, f ->
            f.killsByEnemy.forEach { (k, v) -> acc[k] = (acc[k] ?: 0) + v }
            acc
        }
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

            if (session.skillName == "combat") {
                val context = LocalContext.current
                val divColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)

                // Per-tick state
                val attackSpeedMs = 2_400L
                val frameStartMs  = session.startedAt + currentFrameIdx.toLong() * perFrameMs
                val maxTick = (currentFrame?.playerHits?.size?.minus(1) ?: 0).coerceAtLeast(0)
                val tickInFrame = ((now - frameStartMs) / attackSpeedMs)
                    .toInt().coerceIn(0, maxTick)

                // Live player HP (per-tick if hit data exists, else per-frame fallback)
                val maxHp = (skillLevels[Skills.HITPOINTS] ?: 1) * 10
                val currentPlayerHp = if (currentFrame?.enemyHits?.isNotEmpty() == true) {
                    val base = frames.getOrNull(currentFrameIdx - 1)?.hpAfter ?: maxHp
                    (base - currentFrame.enemyHits.take(tickInFrame + 1).sum()).coerceAtLeast(0)
                } else {
                    frames.getOrNull(currentFrameIdx - 1)?.hpAfter ?: maxHp
                }

                // Live enemy HP (replay player hits to track kills within frame)
                val currentEnemyHp = if (currentEnemy != null && currentFrame?.playerHits?.isNotEmpty() == true) {
                    var hp = currentEnemy.hp
                    for (dmg in currentFrame.playerHits.take(tickInFrame + 1)) {
                        hp -= dmg
                        if (hp <= 0) hp = currentEnemy.hp
                    }
                    hp.coerceAtLeast(0)
                } else currentEnemy?.hp ?: 0

                // Combat log: last 8 entries (interleaved per tick)
                val combatLog = remember(currentFrameIdx, tickInFrame) {
                    buildList<CombatLogEntry> {
                        for (i in 0 until currentFrameIdx) {
                            val f = frames.getOrNull(i) ?: break
                            val eName = enemies[f.enemyKey]?.displayName ?: f.enemyKey
                            for (t in 0 until maxOf(f.playerHits.size, f.enemyHits.size)) {
                                f.playerHits.getOrNull(t)?.let { add(CombatLogEntry(true, it, eName)) }
                                f.enemyHits.getOrNull(t)?.let { add(CombatLogEntry(false, it, eName)) }
                            }
                        }
                        val f = frames.getOrNull(currentFrameIdx) ?: return@buildList
                        val eName = enemies[f.enemyKey]?.displayName ?: f.enemyKey
                        for (t in 0..tickInFrame) {
                            f.playerHits.getOrNull(t)?.let { add(CombatLogEntry(true, it, eName)) }
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
                        if (currentEnemy != null) {
                            Text(
                                text       = currentEnemy.displayName,
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
                                text  = "HP $currentEnemyHp/${currentEnemy.hp}  ATK ${currentEnemy.combatStats.attackLevel}  STR ${currentEnemy.combatStats.strengthLevel}  DEF ${currentEnemy.combatStats.defenseLevel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        } else {
                            Text(
                                text  = "Fighting…",
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
                                text       = "HP: $currentPlayerHp / $maxHp",
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
                        val bonusParts = buildList {
                            if (attackBonus   != 0) add("+$attackBonus ATK")
                            if (strengthBonus != 0) add("+$strengthBonus STR")
                            if (defenseBonus  != 0) add("+$defenseBonus DEF")
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
                                text  = "Food",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(2.dp))
                            for ((key, startQty) in equippedFood) {
                                val remaining = (startQty - (foodConsumedSoFar[key] ?: 0)).coerceAtLeast(0)
                                val heal      = foodHealValues[key] ?: 0
                                val name      = key.replace('_', ' ').replaceFirstChar { it.uppercase() }
                                Text(
                                    text  = "$name ×$remaining (heals $heal HP)",
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
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                            Text(
                                text  = killsSoFar.entries
                                    .sortedByDescending { it.value }
                                    .joinToString(", ") { (k, v) -> "$v ${enemies[k]?.displayName ?: k}" }
                                    + " defeated so far.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }

                        // ── Drops so far ───────────────────────────────────
                        if (dropsSoFar.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                            Text(
                                text  = "Drops",
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
                                text  = "XP",
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
                                text  = "Combat log",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(2.dp))
                            Column {
                                for (entry in combatLog) {
                                    val (arrow, dmgText, color) = if (entry.isPlayer) {
                                        val c = if (entry.damage > 0) Color(0xFF4CAF50)
                                                else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.45f)
                                        Triple(
                                            "→",
                                            if (entry.damage > 0) "You hit ${entry.enemyName}: ${entry.damage} dmg"
                                            else "You missed ${entry.enemyName}",
                                            c,
                                        )
                                    } else {
                                        val c = if (entry.damage > 0) MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.45f)
                                        Triple(
                                            "←",
                                            if (entry.damage > 0) "${entry.enemyName} hit you: ${entry.damage} dmg"
                                            else "${entry.enemyName} missed",
                                            c,
                                        )
                                    }
                                    Text(
                                        text  = "$arrow $dmgText",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = color,
                                    )
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

        OutlinedButton(
            onClick  = onAbandon,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.btn_abandon_session))
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

@Composable
private fun DungeonInfoSheet(
    dungeon: DungeonData,
    skillLevels: Map<String, Int>,
    equippedWeapon: EquipmentData?,
    inventory: Map<String, Int>,
    availableSpells: List<SpellData>,
    selectedSpell: SpellData?,
    isStarting: Boolean,
    onSpellSelected: (SpellData) -> Unit,
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
    val styleLabel = combatStyle.replaceFirstChar { it.titlecase() }
    val canStart   = canEnter && !isStarting &&
        (combatStyle != "magic" || selectedSpell != null)

    // Best arrow for ranged
    val bestArrow = ARROW_TIERS.firstOrNull { (inventory[it] ?: 0) > 0 }

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
            text       = dungeon.displayName,
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text  = dungeon.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        // Level and combat style rows
        StatRow(label = "Recommended combat level",
            value = dungeon.recommendedLevel.toString(),
            valueColor = if (canEnter) GoldPrimary else MaterialTheme.colorScheme.error)
        StatRow(label = "Your combat level", value = combatLvl.toString())
        StatRow(label = "Combat style", value = styleLabel, valueColor = GoldPrimary)

        // Ranged: arrow info
        if (combatStyle == "ranged") {
            val arrowText = if (bestArrow != null)
                "${GameStrings.itemName(context, bestArrow)} \u00d7${inventory[bestArrow]}"
            else "None (no strength bonus)"
            StatRow(label = "Best arrow", value = arrowText)
        }

        Spacer(Modifier.height(12.dp))

        // Enemy spawn list
        if (dungeon.enemySpawns.isNotEmpty()) {
            Text(
                text  = "Enemies",
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

        // Magic: spell picker
        if (combatStyle == "magic") {
            Text(
                text  = "Spell",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            if (availableSpells.isEmpty()) {
                Text(
                    text  = "No spells available at your magic level.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                availableSpells.forEach { spell ->
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
                                text       = spell.displayName,
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color      = if (isSelected) GoldPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text  = "${spell.runeCost}\u00d7 ${GameStrings.itemName(context, spell.runeType)}  \u2022  max hit ${spell.maxHit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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

@Composable
private fun BossInfoSheet(
    boss: BossData,
    skillLevels: Map<String, Int>,
    isStarting: Boolean,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val combatLvl = combatLevel(skillLevels)
    val canFight  = combatLvl >= boss.combatLevelRequired

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
        Text(
            text       = "${boss.emoji} ${boss.displayName}",
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text  = boss.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Required combat level", style = MaterialTheme.typography.bodySmall,
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
            Text("Your combat level", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(combatLvl.toString(), style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold)
        }
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Duration", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${boss.durationMinutes} min", style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold)
        }

        if (boss.xpRewards.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("XP on victory", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val context = LocalContext.current
            for ((skill, xp) in boss.xpRewards) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(GameStrings.skillName(context, skill),
                        style = MaterialTheme.typography.bodySmall)
                    Text("+$xp XP", style = MaterialTheme.typography.bodySmall,
                        color = GoldPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.btn_cancel))
            }
            Button(
                onClick  = onStart,
                modifier = Modifier.weight(1f),
                enabled  = canFight && !isStarting,
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
                text       = "You Died!",
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
                    else "You received 10% of the rewards.",
            style = MaterialTheme.typography.bodySmall,
            color = if (result.won) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))

        // XP per skill
        if (result.xpPerSkill.isNotEmpty()) {
            Text(
                text  = if (result.won) stringResource(R.string.label_xp_gained) else "XP (consolation)",
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text  = GameStrings.skillName(context, skill),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text       = "+${xp.formatXp()} XP",
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = GoldPrimary,
                    )
                }
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
