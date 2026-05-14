package com.fantasyidler.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
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
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.HomeViewModel
import com.fantasyidler.ui.viewmodel.SessionSummary
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.ui.viewmodel.totalLevelFrom
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.toCountdown
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToShop: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state            by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context           = LocalContext.current

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.snackbarConsumed()
        }
    }

    // Session summary dialog
    state.sessionSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = viewModel::summaryConsumed,
            title = {
                Text(
                    text       = summary.title,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(
                    modifier            = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (summary.died) {
                        Text(
                            text  = "You died and lost most of your gains.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    if (summary.boostWasActive) {
                        Text(
                            text  = "2× XP Boost was active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    if (summary.xpLines.isNotEmpty()) {
                        SummarySection("XP Gained")
                        summary.xpLines.forEach { (skill, label) -> SummaryRow(skill, label) }
                    } else if (summary.totalXpLabel.isNotEmpty()) {
                        SummaryRow("XP Gained", summary.totalXpLabel)
                    }
                    if (summary.killLines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        SummarySection("Kills")
                        summary.killLines.forEach { (enemy, kills) -> SummaryRow(enemy, kills) }
                    }
                    if (summary.itemLines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        SummarySection("Loot")
                        summary.itemLines.forEach { (item, qty) -> SummaryRow(item, qty) }
                    }
                    if (summary.coinsGained > 0) {
                        SummaryRow("Coins", "+${summary.coinsGained.formatCoins()}")
                    }
                    if (summary.foodConsumedLines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        SummarySection("Food Consumed")
                        summary.foodConsumedLines.forEach { (food, qty) -> SummaryRow(food, qty) }
                    }
                    if (summary.boneBuriedLabel.isNotEmpty()) {
                        SummaryRow("Bones buried", summary.boneBuriedLabel)
                    }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::summaryConsumed) {
                    Text("Close")
                }
            },
        )
    }

    if (!state.isLoading && state.showWhatsNew) {
        val context = LocalContext.current
        val changelogText = remember {
            runCatching { context.assets.open("changelog.txt").bufferedReader().readText().trim() }.getOrElse { "" }
        }
        if (changelogText.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = viewModel::dismissWhatsNew,
                title = { Text("What's New") },
                text  = {
                    Text(
                        text  = changelogText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissWhatsNew) { Text("Got it") }
                },
            )
        }
    }

    if (!state.isLoading && !state.characterSetupDone) {
        CharacterSetupSheet(
            isFirstTime = true,
            onSave      = { name, gender, race -> viewModel.saveCharacterProfile(name, gender, race) },
            onDismiss   = viewModel::dismissCharacterSetup,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title   = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Column(
                modifier            = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Greeting ────────────────────────────────────────────────
            Text(
                text       = "Welcome back, ${state.characterName.ifBlank { "Adventurer" }}!",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            // ── Stats card ──────────────────────────────────────────────
            Surface(
                shape  = RoundedCornerShape(16.dp),
                color  = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text  = stringResource(R.string.label_player_stats),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StatItem(
                            label = stringResource(R.string.label_combat_level),
                            value = combatLevelFrom(state.skillLevels).toString(),
                        )
                        StatItem(
                            label = stringResource(R.string.label_total_level),
                            value = totalLevelFrom(state.skillLevels).toString(),
                        )
                        StatItem(
                            label = stringResource(R.string.label_coins),
                            value = state.coins.formatCoins(),
                            valueColor = GoldPrimary,
                        )
                    }
                }
            }

            // ── Shop card ───────────────────────────────────────────────
            Surface(
                shape    = RoundedCornerShape(16.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToShop() },
            ) {
                Row(
                    modifier          = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector        = Icons.Filled.ShoppingCart,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier           = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text       = stringResource(R.string.label_shop),
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text  = stringResource(R.string.label_shop_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Active session card ──────────────────────────────────────
            val session = state.activeSession
            if (session != null) {
                HomeSessionCard(
                    session       = session,
                    context       = context,
                    onCollect     = viewModel::collectSession,
                    onAbandon     = viewModel::abandonSession,
                    onDebugFinish = viewModel::debugFinishSession,
                )
            } else {
                Surface(
                    shape    = RoundedCornerShape(16.dp),
                    color    = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text  = stringResource(R.string.label_no_active_session),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text  = stringResource(R.string.label_no_session_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Queue card ───────────────────────────────────────────────
            if (state.sessionQueue.isNotEmpty()) {
                QueueCard(
                    queue    = state.sessionQueue,
                    context  = context,
                    onRemove = viewModel::removeFromQueue,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Session card
// ---------------------------------------------------------------------------

@Composable
private fun HomeSessionCard(
    session: SkillSession,
    context: android.content.Context,
    onCollect: () -> Unit,
    onAbandon: () -> Unit,
    onDebugFinish: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val endsAt = session.endsAt
    LaunchedEffect(endsAt) {
        while (System.currentTimeMillis() < endsAt) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
        now = System.currentTimeMillis()
    }

    val isDone = session.completed || now >= endsAt

    val skillLabel = when (session.skillName) {
        "combat" -> context.getString(R.string.label_combat)
        else     -> GameStrings.skillName(context, session.skillName)
    }
    val skillEmoji = GameStrings.skillEmoji(session.skillName)
    val activityLabel = session.activityKey
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase() }
        .takeIf { session.activityKey.isNotEmpty() }

    Surface(
        shape  = RoundedCornerShape(16.dp),
        color  = if (isDone) MaterialTheme.colorScheme.primaryContainer
                 else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text  = if (isDone) stringResource(R.string.label_session_complete)
                        else stringResource(R.string.label_session_active),
                style = MaterialTheme.typography.labelMedium,
                color = if (isDone) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append("$skillEmoji $skillLabel")
                    if (activityLabel != null) append(" — $activityLabel")
                },
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = if (isDone) MaterialTheme.colorScheme.onPrimaryContainer
                             else MaterialTheme.colorScheme.onSecondaryContainer,
            )

            if (!isDone) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text  = remember(now) { endsAt.toCountdown() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))

            if (isDone) {
                Button(onClick = onCollect, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.btn_collect_results))
                }
                Spacer(Modifier.height(4.dp))
            }

            Row {
                OutlinedButton(onClick = onAbandon) {
                    Text(stringResource(R.string.btn_abandon))
                }
                if (BuildConfig.DEBUG && !isDone) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onDebugFinish) {
                        Text("[Debug] Finish Now")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Queue card
// ---------------------------------------------------------------------------

@Composable
private fun QueueCard(
    queue: List<QueuedAction>,
    context: android.content.Context,
    onRemove: (Int) -> Unit,
) {
    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text  = "Up Next (${queue.size}/3)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            queue.forEachIndexed { index, action ->
                if (index > 0) HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                )
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val emoji = GameStrings.skillEmoji(action.skillName)
                    val activityLabel = action.activityKey
                        .replace('_', ' ')
                        .replaceFirstChar { it.uppercase() }
                        .takeIf { action.activityKey.isNotEmpty() }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text  = "$emoji ${action.skillDisplayName}${if (activityLabel != null) " — $activityLabel" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    IconButton(
                        onClick  = { onRemove(index) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Close,
                            contentDescription = "Remove from queue",
                            modifier           = Modifier.size(16.dp),
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
private fun SummarySection(title: String) {
    Text(
        text  = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text       = value,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text  = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
