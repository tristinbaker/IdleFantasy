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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.json.SeasonalMinigameConfig
import com.fantasyidler.repository.SeasonalBountyTaskWithProgress
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.SeasonalEventViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonalEventScreen(
    viewModel: SeasonalEventViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToExpedition: (String) -> Unit = {},
    onNavigateToBoss: (String) -> Unit = {},
    onNavigateToSkills: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it, withDismissAction = true)
            viewModel.snackbarConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.event?.displayName ?: stringResource(R.string.seasonal_event_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val event = state.event
        if (event == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text  = stringResource(R.string.seasonal_event_none),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text  = stringResource(R.string.seasonal_event_token_progress, state.tokens, event.tokenGoal),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (state.tokens.toFloat() / event.tokenGoal).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    )
                }
            }

            if ("bounty" in event.pillars) {
                SectionCard(title = stringResource(R.string.seasonal_bounty_board_title)) {
                    state.bountyTasks.forEachIndexed { index, taskProgress ->
                        BountyTaskRow(
                            taskProgress = taskProgress,
                            onClaim      = { viewModel.claimBountyTask(taskProgress.task.id) },
                            onGo         = {
                                if (taskProgress.task.type == "kill") event.expeditionDungeonKey?.let(onNavigateToExpedition)
                                else onNavigateToSkills()
                            },
                        )
                        if (index != state.bountyTasks.lastIndex) HorizontalDivider()
                    }
                }
            }

            if ("expedition" in event.pillars && event.expeditionDungeonKey != null) {
                SectionCard(title = stringResource(R.string.seasonal_expedition_title)) {
                    Text(viewModel.dungeonDisplayName(event.expeditionDungeonKey), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onNavigateToExpedition(event.expeditionDungeonKey) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.seasonal_go_to_combat))
                    }
                }
            }

            if ("boss" in event.pillars && event.bossKey != null) {
                SectionCard(title = stringResource(R.string.seasonal_boss_title)) {
                    Text(viewModel.bossDisplayName(event.bossKey), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onNavigateToBoss(event.bossKey) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.seasonal_go_to_combat))
                    }
                }
            }

            val minigame = event.minigame
            if ("minigame" in event.pillars && minigame != null) {
                SectionCard(title = minigame.displayName) {
                    val now = System.currentTimeMillis()
                    if (state.minigameCooldownAt > now) {
                        MinigameCooldownRow(state.minigameCooldownAt)
                    } else {
                        BonfireRhythmGame(
                            config   = minigame,
                            onSubmit = viewModel::submitMinigameAttempt,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun BountyTaskRow(
    taskProgress: SeasonalBountyTaskWithProgress,
    onClaim: () -> Unit,
    onGo: () -> Unit,
) {
    val task = taskProgress.task
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(task.displayName, style = MaterialTheme.typography.bodyMedium)
            Text(
                text  = task.hint,
                style = MaterialTheme.typography.labelSmall,
                color = GoldPrimary,
            )
            Text(
                text  = "${taskProgress.progress}/${task.amount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            taskProgress.claimed -> Text(
                text  = stringResource(R.string.seasonal_claimed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            taskProgress.progress >= task.amount -> Button(onClick = onClaim) { Text(stringResource(R.string.seasonal_claim)) }
            else -> TextButton(onClick = onGo) { Text(stringResource(R.string.seasonal_go)) }
        }
    }
}

@Composable
private fun MinigameCooldownRow(resumesAtMs: Long) {
    var remainingMs by remember { mutableLongStateOf(resumesAtMs - System.currentTimeMillis()) }
    LaunchedEffect(resumesAtMs) {
        while (remainingMs > 0) {
            delay(1_000L)
            remainingMs = resumesAtMs - System.currentTimeMillis()
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

/**
 * A whack-a-mole reflex minigame: over [SeasonalMinigameConfig.rounds] rounds, an ember lights
 * up in a random hole and the player must tap it before [SeasonalMinigameConfig.visibleMs]
 * elapses. Landing enough hits wins a token; falling short is a real failure — either way the
 * cooldown starts once the round set finishes.
 */
@Composable
private fun BonfireRhythmGame(
    config: SeasonalMinigameConfig,
    onSubmit: (Boolean) -> Unit,
) {
    var isPlaying by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    var hits by remember { mutableIntStateOf(0) }
    var litHole by remember { mutableIntStateOf(-1) }

    Text(
        text  = stringResource(R.string.seasonal_minigame_hint, config.hitsRequired, config.rounds),
        style = MaterialTheme.typography.bodySmall,
        color = GoldPrimary,
    )
    Spacer(Modifier.height(8.dp))

    if (isPlaying) {
        Text(
            text  = stringResource(R.string.seasonal_minigame_round, round + 1, config.rounds, hits),
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(8.dp))
    }

    val columns = 3
    val rows = (config.holeCount + columns - 1) / columns
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0 until columns) {
                    val index = r * columns + c
                    if (index >= config.holeCount) continue
                    val isLit = isPlaying && litHole == index
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (isLit) GoldPrimary else MaterialTheme.colorScheme.surface)
                            .clickable(enabled = isPlaying) { if (litHole == index) litHole = -1 },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isLit) Text("🔥", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    if (!isPlaying) {
        Button(
            onClick  = { isPlaying = true; round = 0; hits = 0 },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.seasonal_tap))
        }
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var localHits = 0
        for (r in 0 until config.rounds) {
            round = r
            litHole = kotlin.random.Random.nextInt(config.holeCount)
            var elapsed = 0L
            var hitThisRound = false
            while (elapsed < config.visibleMs) {
                delay(30L)
                elapsed += 30L
                if (litHole == -1) { hitThisRound = true; break }
            }
            if (hitThisRound) { localHits++; hits = localHits }
            litHole = -1
            delay(150L)
        }
        isPlaying = false
        onSubmit(localHits >= config.hitsRequired)
    }
}
