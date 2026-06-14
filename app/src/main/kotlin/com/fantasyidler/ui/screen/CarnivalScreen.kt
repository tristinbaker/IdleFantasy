package com.fantasyidler.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.json.CarnivalPrize
import com.fantasyidler.data.model.Skills
import com.fantasyidler.simulator.CarnivalSimulator
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.ActiveGameState
import com.fantasyidler.ui.viewmodel.CarnivalViewModel
import com.fantasyidler.util.GameStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private val POTION_COLORS = listOf(
    Color(0xFF4CAF50), // green
    Color(0xFF2196F3), // blue
    Color(0xFFF44336), // red
    Color(0xFF9C27B0), // purple
)

private val POTION_NAMES = listOf("Green", "Blue", "Red", "Purple")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarnivalScreen(
    onBack: () -> Unit = {},
    viewModel: CarnivalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.snackbarConsumed()
        }
    }

    state.pendingLampPrizeKey?.let { prizeKey ->
        val prize = viewModel.prizesMap[prizeKey]
        if (prize != null) {
            LampSkillPickerDialog(
                xpAmount        = prize.xpAmount,
                skillLevels     = state.skillLevels,
                onSkillSelected = viewModel::redeemLamp,
                onDismiss       = viewModel::dismissLampPicker,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.carnival_title))
                        Text(
                            text  = stringResource(R.string.carnival_ticket_balance, state.ticketBalance),
                            style = MaterialTheme.typography.bodySmall,
                            color = GoldPrimary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val pagerState    = rememberPagerState(pageCount = { 3 })
        val coroutineScope = rememberCoroutineScope()

        Column(modifier = Modifier.padding(innerPadding)) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick  = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text     = { Text(stringResource(R.string.carnival_idle_tab)) },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick  = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text     = { Text(stringResource(R.string.carnival_active_tab)) },
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick  = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                    text     = { Text(stringResource(R.string.carnival_shop_tab)) },
                )
            }

            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> IdleGamesTab(state.skillLevels, state.queueSize, viewModel)
                    1 -> ActiveGamesTab(state, viewModel)
                    2 -> PrizeShopTab(state, viewModel)
                }
            }
        }
    }
}

// ── Idle Games ─────────────────────────────────────────────────────────────────

private data class IdleGameDef(
    val activityKey: String,
    val titleRes: Int,
    val descRes: Int,
    val skillKey: String,
)

private val IDLE_GAMES = listOf(
    IdleGameDef("archery_range",         R.string.carnival_archery_range,         R.string.carnival_idle_desc_archery,    Skills.RANGED),
    IdleGameDef("strongman_competition", R.string.carnival_strongman_competition, R.string.carnival_idle_desc_strongman,  Skills.STRENGTH),
    IdleGameDef("wizards_duel",          R.string.carnival_wizards_duel,          R.string.carnival_idle_desc_wizard,     Skills.MAGIC),
    IdleGameDef("fishing_derby",         R.string.carnival_fishing_derby,         R.string.carnival_idle_desc_fishing,    Skills.FISHING),
)

@Composable
private fun IdleGamesTab(
    skillLevels: Map<String, Int>,
    queueSize: Int,
    viewModel: CarnivalViewModel,
) {
    val context = LocalContext.current
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text  = stringResource(R.string.carnival_idle_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IDLE_GAMES.forEach { game ->
            val skillLevel  = skillLevels[game.skillKey] ?: 1
            val minTickets  = (60 * (0.15 + (1 - 1) * (0.20 / 98.0))).toInt()
            val maxTickets  = (60 * (0.15 + (99 - 1) * (0.20 / 98.0))).toInt()
            val myTickets   = (60 * (0.15 + (skillLevel - 1) * (0.20 / 98.0))).toInt()
            Surface(
                shape    = RoundedCornerShape(12.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text       = stringResource(game.titleRes),
                                style      = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text  = stringResource(game.descRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text  = stringResource(R.string.carnival_skill_level, GameStrings.skillName(context, game.skillKey), skillLevel),
                                style = MaterialTheme.typography.bodySmall,
                                color = GoldPrimary,
                            )
                            Text(
                                text  = stringResource(R.string.carnival_ticket_yield, myTickets),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick  = { viewModel.queueIdleGame(game.activityKey, context.getString(game.titleRes)) },
                        enabled  = queueSize < 3,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.carnival_queue))
                    }
                }
            }
        }
    }
}

// ── Active Games ───────────────────────────────────────────────────────────────

@Composable
private fun ActiveGamesTab(
    state: com.fantasyidler.ui.viewmodel.CarnivalUiState,
    viewModel: CarnivalViewModel,
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RingTossCard(state.ringTossState, viewModel)
        HammerStrikeCard(state.hammerStrikeState, viewModel)
        PotionSequenceCard(state.potionSequenceState, viewModel)
        ItemAppraisalCard(state.itemAppraisalState, state.currentAppraisalPair, viewModel)
    }
}

@Composable
private fun GameCard(
    titleRes: Int,
    descRes: Int,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(descRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun CooldownRow(gameKey: String, resumesAtMs: Long, viewModel: CarnivalViewModel) {
    var remainingMs by remember { mutableStateOf(resumesAtMs - System.currentTimeMillis()) }
    LaunchedEffect(resumesAtMs) {
        while (remainingMs > 0) {
            delay(1_000L)
            remainingMs = resumesAtMs - System.currentTimeMillis()
            if (remainingMs <= 0) {
                viewModel.clearCooldownIfExpired(gameKey)
            }
        }
    }
    val totalMinutes = (remainingMs / 60_000L).coerceAtLeast(0L)
    val hours   = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    Text(
        text  = stringResource(R.string.carnival_cooldown, hours, minutes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RingTossCard(gameState: ActiveGameState, viewModel: CarnivalViewModel) {
    GameCard(R.string.carnival_ring_toss, R.string.carnival_active_ring_desc) {
        when (gameState) {
            is ActiveGameState.Ready -> {
                Button(onClick = viewModel::startRingToss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.carnival_play))
                }
            }
            is ActiveGameState.TimingActive -> {
                var position by remember { mutableFloatStateOf(0f) }
                var direction by remember { mutableIntStateOf(1) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(16L)
                        position += direction * 0.008f
                        if (position >= 1f) { position = 1f; direction = -1 }
                        if (position <= 0f) { position = 0f; direction = 1 }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                        // Target zone highlight
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f - 0.45f)
                                .align(Alignment.CenterStart)
                                .padding(start = (0.45f * 1f * 1f).dp) // approximate
                                .height(24.dp)
                                .background(GoldPrimary.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        )
                        LinearProgressIndicator(
                            progress         = { position },
                            modifier         = Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(4.dp)),
                            color            = MaterialTheme.colorScheme.primary,
                            trackColor       = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        // Target zone overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.10f)
                                    .align(Alignment.CenterStart)
                                    .padding(start = (0f).dp)
                                    .height(24.dp)
                                    .background(Color.Transparent)
                            )
                        }
                    }
                    // Show target zone as a separate indicator
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text  = stringResource(R.string.carnival_ring_target_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = GoldPrimary,
                        )
                    }
                    Button(
                        onClick  = { viewModel.submitRingToss(position) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.carnival_throw))
                    }
                }
            }
            is ActiveGameState.OnCooldown -> CooldownRow("ring_toss", gameState.resumesAtMs, viewModel)
            else -> {}
        }
    }
}

@Composable
private fun HammerStrikeCard(gameState: ActiveGameState, viewModel: CarnivalViewModel) {
    GameCard(R.string.carnival_hammer_strike, R.string.carnival_active_hammer_desc) {
        when (gameState) {
            is ActiveGameState.Ready -> {
                Button(onClick = viewModel::startHammerStrike, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.carnival_play))
                }
            }
            is ActiveGameState.TimingActive -> {
                var power by remember { mutableFloatStateOf(0f) }
                var rising by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(16L)
                        power += if (rising) 0.012f else -0.006f
                        if (power >= 1f) { power = 1f; rising = false }
                        if (power <= 0f) { power = 0f; rising = true }
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text  = stringResource(R.string.carnival_power_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress   = { power },
                        modifier   = Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(4.dp)),
                        color      = when {
                            power >= 0.80f -> Color(0xFFF44336)
                            power >= 0.50f -> GoldPrimary
                            else           -> MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Text(stringResource(R.string.carnival_hammer_miss_zone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.carnival_hammer_good_zone), style = MaterialTheme.typography.bodySmall, color = GoldPrimary)
                        Text(stringResource(R.string.carnival_hammer_perfect_zone), style = MaterialTheme.typography.bodySmall, color = Color(0xFFF44336))
                    }
                    Button(
                        onClick  = { viewModel.submitHammerStrike(power) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.carnival_strike))
                    }
                }
            }
            is ActiveGameState.OnCooldown -> CooldownRow("hammer_strike", gameState.resumesAtMs, viewModel)
            else -> {}
        }
    }
}

@Composable
private fun PotionSequenceCard(gameState: ActiveGameState, viewModel: CarnivalViewModel) {
    GameCard(R.string.carnival_potion_sequence, R.string.carnival_active_sequence_desc) {
        when (gameState) {
            is ActiveGameState.Ready -> {
                Button(onClick = viewModel::startPotionSequence, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.carnival_play))
                }
            }
            is ActiveGameState.SequenceShowing -> {
                val seq     = gameState.sequence
                val current = gameState.currentIndex
                LaunchedEffect(current) {
                    delay(1_000L)
                    viewModel.advancePotionSequence()
                }
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.carnival_sequence_watch), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        seq.forEachIndexed { i, colorIdx ->
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (i == current) POTION_COLORS[colorIdx] else POTION_COLORS[colorIdx].copy(alpha = 0.3f))
                                    .border(if (i == current) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (i == current) {
                                    Text(
                                        text  = POTION_NAMES[colorIdx].first().toString(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            is ActiveGameState.SequenceInput -> {
                val inputCount = gameState.userInput.size
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text  = stringResource(R.string.carnival_sequence_input, inputCount + 1, gameState.sequence.size),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        POTION_COLORS.forEachIndexed { colorIdx, color ->
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Surface(
                                    onClick = { viewModel.submitPotionInput(colorIdx) },
                                    color = Color.Transparent,
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text  = POTION_NAMES[colorIdx].first().toString(),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            is ActiveGameState.OnCooldown -> CooldownRow("potion_sequence", gameState.resumesAtMs, viewModel)
            else -> {}
        }
    }
}

@Composable
private fun ItemAppraisalCard(
    gameState: ActiveGameState,
    pair: com.fantasyidler.ui.viewmodel.AppraisalPair?,
    viewModel: CarnivalViewModel,
) {
    GameCard(R.string.carnival_item_appraisal, R.string.carnival_active_appraisal_desc) {
        when (gameState) {
            is ActiveGameState.Ready -> {
                Button(onClick = viewModel::startItemAppraisal, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.carnival_play))
                }
            }
            is ActiveGameState.AppraisalPlaying -> {
                if (pair != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text       = stringResource(R.string.carnival_appraisal_which),
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign  = TextAlign.Center,
                            modifier   = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick  = { viewModel.submitAppraisalAnswer(true) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(pair.itemA, textAlign = TextAlign.Center)
                            }
                            OutlinedButton(
                                onClick  = { viewModel.submitAppraisalAnswer(false) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(pair.itemB, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
            is ActiveGameState.OnCooldown -> CooldownRow("item_appraisal", gameState.resumesAtMs, viewModel)
            else -> {}
        }
    }
}

// ── Prize Shop ─────────────────────────────────────────────────────────────────

@Composable
private fun PrizeShopTab(
    state: com.fantasyidler.ui.viewmodel.CarnivalUiState,
    viewModel: CarnivalViewModel,
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        viewModel.prizes.forEach { prize ->
            PrizeRow(
                prize      = prize,
                tickets    = state.ticketBalance,
                alreadyOwned = prize.key in state.ownedPrizeKeys,
                equipData  = if (prize.type == "equipment") viewModel.gameData.equipment[prize.key] else null,
                onRedeem   = { viewModel.redeem(prize.key) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun PrizeRow(
    prize: CarnivalPrize,
    tickets: Int,
    alreadyOwned: Boolean,
    equipData: com.fantasyidler.data.json.EquipmentData?,
    onRedeem: () -> Unit,
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = prize.displayName,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (equipData != null) {
                val statParts = buildList {
                    if (equipData.attackBonus   > 0) add("ATK +${equipData.attackBonus}")
                    if (equipData.strengthBonus > 0) add("STR +${equipData.strengthBonus}")
                    if (equipData.defenseBonus  > 0) add("DEF +${equipData.defenseBonus}")
                    if ((equipData.capeBonus) > 0f)  add("XP +${(equipData.capeBonus * 100).toInt()}%")
                }
                if (statParts.isNotEmpty()) {
                    Text(
                        text  = statParts.joinToString("  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = GoldPrimary,
                    )
                }
                val reqs = equipData.requirements
                if (reqs.isNotEmpty()) {
                    Text(
                        text  = stringResource(R.string.slayer_requires, reqs.entries.joinToString { (s, l) -> "${s.replaceFirstChar { it.uppercase() }} $l" }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text  = prize.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier            = Modifier.padding(start = 8.dp),
        ) {
            Text(
                text       = stringResource(R.string.carnival_ticket_cost, prize.ticketCost),
                style      = MaterialTheme.typography.bodyMedium,
                color      = GoldPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick  = onRedeem,
                enabled  = !alreadyOwned && tickets >= prize.ticketCost,
            ) {
                Text(
                    if (alreadyOwned) stringResource(R.string.slayer_owned)
                    else stringResource(R.string.carnival_redeem)
                )
            }
        }
    }
}

// ── Lamp skill picker ──────────────────────────────────────────────────────────

@Composable
private fun LampSkillPickerDialog(
    xpAmount: Long,
    skillLevels: Map<String, Int>,
    onSkillSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.slayer_lamp_pick_skill)) },
        text = {
            Column(
                modifier            = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Skills.ALL.forEach { skillKey ->
                    val level = skillLevels[skillKey] ?: 1
                    val name  = GameStrings.skillName(context, skillKey)
                    Surface(
                        onClick  = { onSkillSelected(skillKey) },
                        shape    = RoundedCornerShape(8.dp),
                        color    = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text  = stringResource(R.string.slayer_level_label, level),
                                style = MaterialTheme.typography.bodySmall,
                                color = GoldPrimary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } },
    )
}
