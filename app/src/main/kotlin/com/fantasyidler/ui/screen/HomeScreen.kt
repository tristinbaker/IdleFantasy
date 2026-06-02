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
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import com.fantasyidler.data.model.RecentSession
import com.fantasyidler.util.toTitleCase
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.data.model.HiredWorker
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.WorkerTier
import com.fantasyidler.data.json.BlessingType
import com.fantasyidler.repository.ChurchRepository
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.HomeViewModel
import com.fantasyidler.ui.viewmodel.SessionSummary
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.ui.viewmodel.totalLevelFrom
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.formatDurationMs
import com.fantasyidler.util.toCountdown
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToShop: () -> Unit = {},
    onNavigateToInn: () -> Unit = {},
    onNavigateToWorkerSkills: () -> Unit = {},
    onNavigateToGuildHall: () -> Unit = {},
    onNavigateToChurch: () -> Unit = {},
    onNavigateToSlayer: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state            by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRecentLog by remember { mutableStateOf(false) }
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
                            text  = stringResource(R.string.home_died_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    if (summary.boostWasActive) {
                        Text(
                            text  = stringResource(R.string.home_xp_boost_was_active),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    if (summary.xpLines.isNotEmpty()) {
                        SummarySection(stringResource(R.string.label_xp_gained))
                        summary.xpLines.forEachIndexed { i, (skill, label) ->
                            val bonus = summary.xpLineBonuses.getOrNull(i) ?: 0L
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(skill, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    if (bonus > 0L) {
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text  = "(+${bonus.formatXp()})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GoldPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    } else if (summary.totalXpLabel.isNotEmpty()) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text     = stringResource(R.string.label_xp_gained),
                                style    = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(summary.totalXpLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                if (summary.totalXpLabelBonus > 0L) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text  = "(+${summary.totalXpLabelBonus.formatXp()})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GoldPrimary,
                                    )
                                }
                            }
                        }
                    }
                    if (summary.killLines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        SummarySection(stringResource(R.string.label_kills))
                        summary.killLines.forEach { (enemy, kills) -> SummaryRow(enemy, kills) }
                    }
                    if (summary.itemLines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        SummarySection(stringResource(R.string.home_loot))
                        summary.itemLines.forEach { (item, qty) -> SummaryRow(item, qty) }
                    }
                    if (summary.coinsGained > 0) {
                        SummaryRow("Coins", "+${summary.coinsGained.formatCoins()}")
                    }
                    if (summary.coinBlessingBonus > 0) {
                        Text(
                            text  = stringResource(R.string.church_blessing_bonus, summary.coinBlessingBonus.formatCoins()),
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldPrimary,
                        )
                    }
                    if (summary.foodConsumedLines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        SummarySection(stringResource(R.string.home_food_consumed))
                        summary.foodConsumedLines.forEach { (food, qty) -> SummaryRow(food, qty) }
                    }
                    if (summary.boneBuriedLines.isNotEmpty()) {
                        summary.boneBuriedLines.forEach { (label, qty) -> SummaryRow(label, qty) }
                    }
                    if (summary.noteLines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        summary.noteLines.forEach { note ->
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                color = GoldPrimary,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                    summary.unlockMessage?.let { msg ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::summaryConsumed) {
                    Text(stringResource(R.string.btn_close))
                }
            },
        )
    }

    state.workerSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = viewModel::workerSummaryConsumed,
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
                    if (summary.boostWasActive) {
                        Text(
                            text       = stringResource(R.string.home_xp_boost_was_active),
                            style      = MaterialTheme.typography.labelSmall,
                            color      = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    if (summary.xpLines.isNotEmpty()) {
                        SummarySection(stringResource(R.string.label_xp_gained))
                        summary.xpLines.forEachIndexed { i, (skill, label) ->
                            val bonus = summary.xpLineBonuses.getOrNull(i) ?: 0L
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(skill, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    if (bonus > 0L) {
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text  = "(+${bonus.formatXp()})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GoldPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    } else if (summary.totalXpLabel.isNotEmpty()) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text     = stringResource(R.string.label_xp_gained),
                                style    = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(summary.totalXpLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                if (summary.totalXpLabelBonus > 0L) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text  = "(+${summary.totalXpLabelBonus.formatXp()})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GoldPrimary,
                                    )
                                }
                            }
                        }
                    }
                    if (summary.killLines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        SummarySection(stringResource(R.string.label_kills))
                        summary.killLines.forEach { (enemy, kills) -> SummaryRow(enemy, kills) }
                    }
                    if (summary.itemLines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        SummarySection(stringResource(R.string.home_loot))
                        summary.itemLines.forEach { (item, qty) -> SummaryRow(item, qty) }
                    }
                    if (summary.coinsGained > 0) {
                        SummaryRow("Coins", "+${summary.coinsGained.formatCoins()}")
                    }
                    if (summary.coinBlessingBonus > 0) {
                        Text(
                            text  = stringResource(R.string.church_blessing_bonus, summary.coinBlessingBonus.formatCoins()),
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldPrimary,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::workerSummaryConsumed) {
                    Text(stringResource(R.string.btn_close))
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
            val sections = remember(changelogText) {
                val versionRegex = Regex("^v\\d+\\..*")
                val result = mutableListOf<Pair<String, String>>() // version → body
                var currentVersion = ""
                val bodyLines = mutableListOf<String>()
                for (line in changelogText.lines()) {
                    if (line.matches(versionRegex)) {
                        if (currentVersion.isNotEmpty()) result += currentVersion to bodyLines.joinToString("\n").trim()
                        currentVersion = line
                        bodyLines.clear()
                    } else {
                        bodyLines += line
                    }
                }
                if (currentVersion.isNotEmpty()) result += currentVersion to bodyLines.joinToString("\n").trim()
                result
            }
            AlertDialog(
                onDismissRequest = viewModel::dismissWhatsNew,
                title = { Text(stringResource(R.string.home_whats_new)) },
                text  = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        sections.forEachIndexed { i, (version, body) ->
                            if (i > 0) Spacer(Modifier.height(12.dp))
                            val isCurrent = version == "v${BuildConfig.VERSION_NAME}"
                            Text(
                                text = if (isCurrent) "$version (current)" else version,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissWhatsNew) { Text(stringResource(R.string.home_got_it)) }
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

    if (showRecentLog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showRecentLog = false },
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            RecentSessionsSheet(
                sessions  = state.recentSessions,
                onDismiss = { showRecentLog = false },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title   = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.isLoading && state.showRecentActivityLog) {
                FloatingActionButton(onClick = { showRecentLog = true }) {
                    Icon(Icons.Filled.Assignment, contentDescription = "Recent activity")
                }
            }
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
                text       = stringResource(R.string.home_welcome, state.characterName.ifBlank { stringResource(R.string.home_adventurer) }),
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

                    val blessingActive = state.activeBlessingKey.isNotEmpty() && state.activeBlessingRemainingMs > 0
                    if (blessingActive) {
                        val context = LocalContext.current
                        val nameResId = context.resources.getIdentifier(
                            "blessing_${state.activeBlessingKey}_name", "string", context.packageName,
                        )
                        val blessingName = if (nameResId != 0) stringResource(nameResId) else state.activeBlessingKey
                        val blessingData = ChurchRepository.ALL_BLESSINGS.firstOrNull { it.key == state.activeBlessingKey }
                        val boostDesc = blessingData?.let { b ->
                            when (b.type) {
                                BlessingType.XP      -> "${b.magnitude}x XP"
                                BlessingType.DEFENSE -> "+${b.magnitude.toInt()} DEF"
                                BlessingType.COINS   -> "+${(b.magnitude * 100).toInt()}% coins"
                            }
                        }
                        val timeLeft = state.activeBlessingRemainingMs.formatDurationMs()
                        val blessingText = if (boostDesc != null) "$blessingName ($boostDesc) - $timeLeft"
                                          else "$blessingName - $timeLeft"
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector        = Icons.Filled.Star,
                                contentDescription = null,
                                tint               = GoldPrimary,
                                modifier           = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text  = blessingText,
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldPrimary,
                            )
                        }
                    }

                    if (state.xpBoostRemainingMs > 0) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector        = Icons.Filled.Star,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.tertiary,
                                modifier           = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text  = stringResource(R.string.home_xp_boost_active, state.xpBoostRemainingMs.formatDurationMs()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
            }

            // ── Town card ───────────────────────────────────────────────
            var townExpanded by remember { mutableStateOf(false) }
            Surface(
                shape    = RoundedCornerShape(16.dp),
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().clickable { townExpanded = !townExpanded },
            ) {
                Row(
                    modifier          = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = stringResource(R.string.home_town_title),
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text  = stringResource(R.string.home_town_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector        = if (townExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (townExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple(Icons.Filled.ShoppingCart, stringResource(R.string.label_shop), stringResource(R.string.label_shop_desc)) to onNavigateToShop,
                        Triple(Icons.Filled.Person, stringResource(R.string.inn_title), stringResource(R.string.inn_card_desc)) to onNavigateToInn,
                        Triple(Icons.Filled.Group, stringResource(R.string.guild_hall_title), stringResource(R.string.guild_hall_desc)) to onNavigateToGuildHall,
                        Triple(Icons.Filled.Star, stringResource(R.string.church_title), stringResource(R.string.church_desc)) to onNavigateToChurch,
                        Triple(Icons.Filled.Shield, stringResource(R.string.slayer_title), stringResource(R.string.slayer_card_desc)) to onNavigateToSlayer,
                    ).forEachIndexed { i, (info, action) ->
                        val (icon, title, desc) = info
                        val iconTint = if (i == 3 && state.activeBlessingKey.isNotEmpty() && state.activeBlessingRemainingMs > 0)
                            GoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        Surface(
                            shape    = RoundedCornerShape(12.dp),
                            color    = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().clickable { action() },
                        ) {
                            Row(
                                modifier          = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector        = icon,
                                    contentDescription = null,
                                    tint               = iconTint,
                                    modifier           = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text       = title,
                                    style      = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier   = Modifier.weight(1f),
                                )
                                Text(
                                    text  = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // ── Active session card ──────────────────────────────────────
            val session = state.activeSession
            if (session != null) {
                if (!session.completed) {
                    LaunchedEffect(session.sessionId) {
                        val remaining = session.endsAt - System.currentTimeMillis()
                        if (remaining > 0) delay(remaining)
                        viewModel.onSessionExpiredLocally(session.sessionId)
                    }
                }
                HomeSessionCard(
                    session       = session,
                    context       = context,
                    onRepeat      = viewModel::repeatActiveSession,
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

            // ── Collect button ───────────────────────────────────────────
            if (state.pendingCollectCount > 0) {
                val n = state.pendingCollectCount
                Button(
                    onClick  = viewModel::collectSession,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(pluralStringResource(R.plurals.plural_collect_sessions, n, n))
                }
            }

            // ── Queue card ───────────────────────────────────────────────
            if (state.sessionQueue.isNotEmpty()) {
                QueueCard(
                    queue       = state.sessionQueue,
                    queueEndsAt = state.queueEndsAt,
                    context     = context,
                    onRemove    = viewModel::removeFromQueue,
                    onMove      = viewModel::moveQueueItem,
                )
            }

            // ── Worker session cards ─────────────────────────────────────
            val workerSession = state.workerSession
            val hiredWorker   = state.hiredWorker
            if (hiredWorker != null) {
                if (workerSession != null && !workerSession.completed) {
                    LaunchedEffect(workerSession.sessionId) {
                        val remaining = workerSession.endsAt - System.currentTimeMillis()
                        if (remaining > 0) delay(remaining)
                        viewModel.onWorkerSessionExpiredLocally(workerSession.sessionId)
                    }
                }
                WorkerSessionCard(
                    slot                     = 1,
                    hiredWorker              = hiredWorker,
                    session                  = workerSession,
                    pendingCollect           = state.workerPendingCollect1,
                    context                  = context,
                    onCollect                = viewModel::collectWorkerSession,
                    onDismiss                = { viewModel.dismissWorker(1) },
                    onDebugFinish            = { viewModel.debugFinishWorkerSession(1) },
                    onNavigateToWorkerSkills = onNavigateToWorkerSkills,
                )
            }
            val hiredWorker2   = state.hiredWorker2
            val workerSession2 = state.workerSession2
            if (hiredWorker2 != null) {
                if (workerSession2 != null && !workerSession2.completed) {
                    LaunchedEffect(workerSession2.sessionId) {
                        val remaining = workerSession2.endsAt - System.currentTimeMillis()
                        if (remaining > 0) delay(remaining)
                        viewModel.onWorkerSessionExpiredLocally(workerSession2.sessionId)
                    }
                }
                WorkerSessionCard(
                    slot                     = 2,
                    hiredWorker              = hiredWorker2,
                    session                  = workerSession2,
                    pendingCollect           = state.workerPendingCollect2,
                    context                  = context,
                    onCollect                = viewModel::collectWorkerSession,
                    onDismiss                = { viewModel.dismissWorker(2) },
                    onDebugFinish            = { viewModel.debugFinishWorkerSession(2) },
                    onNavigateToWorkerSkills = onNavigateToWorkerSkills,
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
    onRepeat: () -> Unit,
    onAbandon: () -> Unit,
    onDebugFinish: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showAbandonConfirm by remember { mutableStateOf(false) }
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

            if (!isDone) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRepeat) {
                        Text(stringResource(R.string.btn_repeat_action))
                    }
                    OutlinedButton(onClick = { showAbandonConfirm = true }) {
                        Text(stringResource(R.string.btn_abandon))
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
    queueEndsAt: Long,
    context: android.content.Context,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text  = stringResource(R.string.home_up_next, queue.size),
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
                    val labelExpedition = stringResource(R.string.nav_expeditions)
                    val labelDungeon    = stringResource(R.string.label_dungeon)
                    val labelBoss       = stringResource(R.string.label_boss)
                    val (prefix, suffix) = when (action.skillName) {
                        "expedition" -> labelExpedition to action.skillDisplayName
                        "combat"     -> labelDungeon    to action.skillDisplayName
                        "boss"       -> labelBoss       to action.skillDisplayName
                        "farming"    -> action.skillDisplayName to null
                        else         -> action.skillDisplayName to action.activityKey
                            .replace('_', ' ')
                            .replaceFirstChar { it.uppercase() }
                            .takeIf { action.activityKey.isNotEmpty() }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text  = "$emoji $prefix${if (suffix != null) " — $suffix" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (queue.size > 1) {
                        IconButton(
                            onClick  = { onMove(index, index - 1) },
                            enabled  = index > 0,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector        = Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Move up",
                                modifier           = Modifier.size(16.dp),
                                tint               = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant
                                                     else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            )
                        }
                        IconButton(
                            onClick  = { onMove(index, index + 1) },
                            enabled  = index < queue.size - 1,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector        = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Move down",
                                modifier           = Modifier.size(16.dp),
                                tint               = if (index < queue.size - 1) MaterialTheme.colorScheme.onSurfaceVariant
                                                     else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            )
                        }
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
            if (queueEndsAt > 0L) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                )
                val remaining = (queueEndsAt - System.currentTimeMillis()).coerceAtLeast(0L)
                Text(
                    text  = stringResource(R.string.home_queue_ends_in, remaining.formatDurationMs()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Worker section
// ---------------------------------------------------------------------------

@Composable
private fun WorkerSessionCard(
    slot: Int,
    hiredWorker: HiredWorker,
    session: SkillSession?,
    pendingCollect: Boolean,
    context: android.content.Context,
    onCollect: () -> Unit,
    onDismiss: () -> Unit,
    onDebugFinish: () -> Unit,
    onNavigateToWorkerSkills: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDismissConfirm by remember { mutableStateOf(false) }
    val endsAt = session?.endsAt ?: 0L
    LaunchedEffect(endsAt) {
        if (endsAt > 0) {
            while (System.currentTimeMillis() < endsAt) {
                now = System.currentTimeMillis()
                delay(1_000L)
            }
            now = System.currentTimeMillis()
        }
    }
    val isDone = pendingCollect || (session != null && (session.completed || (endsAt > 0 && now >= endsAt)))

    val tierLabel = when (hiredWorker.tier) {
        WorkerTier.LONG_LABORER -> stringResource(R.string.worker_long_laborer)
        WorkerTier.APPRENTICE   -> stringResource(R.string.worker_apprentice)
        WorkerTier.JOURNEYMAN   -> stringResource(R.string.worker_journeyman)
        WorkerTier.MASTER       -> stringResource(R.string.worker_master)
    }

    if (showDismissConfirm) {
        AlertDialog(
            onDismissRequest = { showDismissConfirm = false },
            title = { Text(stringResource(R.string.worker_dismiss_confirm_title)) },
            text  = { Text(stringResource(R.string.worker_dismiss_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { showDismissConfirm = false; onDismiss() }) {
                    Text(stringResource(R.string.btn_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDismissConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }

    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = if (isDone) MaterialTheme.colorScheme.primaryContainer
                   else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text  = stringResource(R.string.worker_card_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isDone) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text       = "$tierLabel · ${hiredWorker.dailyName}",
                    style      = MaterialTheme.typography.labelMedium,
                    color      = GoldPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(4.dp))
            if (session != null) {
                val skillLabel = when (session.skillName) {
                    "combat" -> context.getString(R.string.label_combat)
                    else     -> GameStrings.skillName(context, session.skillName)
                }
                val skillEmoji    = GameStrings.skillEmoji(session.skillName)
                val activityLabel = session.activityKey
                    .replace('_', ' ')
                    .replaceFirstChar { it.uppercase() }
                    .takeIf { session.activityKey.isNotEmpty() }
                Text(
                    text       = buildString {
                        append("$skillEmoji $skillLabel")
                        if (activityLabel != null) append(" — $activityLabel")
                    },
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = if (isDone) MaterialTheme.colorScheme.onPrimaryContainer
                                 else MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (!isDone && endsAt > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text       = remember(now) { endsAt.toCountdown() },
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            } else {
                Text(
                    text  = stringResource(R.string.worker_no_active_session),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (isDone) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = stringResource(R.string.worker_session_complete),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isDone) {
                    Button(onClick = onCollect) {
                        Text(stringResource(R.string.worker_collect_btn))
                    }
                }
                if (!isDone && session == null) {
                    Button(onClick = onNavigateToWorkerSkills) {
                        Text(stringResource(R.string.worker_add_sessions))
                    }
                }
                OutlinedButton(onClick = { showDismissConfirm = true }) {
                    Text(stringResource(R.string.worker_dismiss_btn))
                }
                if (BuildConfig.DEBUG && session != null && !isDone) {
                    TextButton(onClick = onDebugFinish) {
                        Text("[Debug] Finish Worker")
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

@Composable
private fun RecentSessionsSheet(
    sessions: List<RecentSession>,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
        Text(
            text       = "Recent Activity",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        if (sessions.isEmpty()) {
            Text(
                text  = "No sessions completed yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            sessions.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text  = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(28.dp),
                    )
                    Text(
                        text     = entry.skillName.toTitleCase(),
                        style    = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text  = entry.activityDisplayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (index < sessions.lastIndex) HorizontalDivider()
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.btn_cancel))
        }
    }
}
