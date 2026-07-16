package com.fantasyidler.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import com.fantasyidler.data.model.RecentSession
import com.fantasyidler.util.drawableByName
import com.fantasyidler.util.toTitleCase
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.HiredWorker
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.WorkerTier
import com.fantasyidler.data.json.BlessingType
import com.fantasyidler.repository.ChurchRepository
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.ui.viewmodel.totalLevelFrom
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.HomeViewModel
import com.fantasyidler.ui.viewmodel.SessionSummary
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp
import com.fantasyidler.simulator.XpTable
import com.fantasyidler.util.formatDurationMs
import com.fantasyidler.util.toCountdown
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ElevatedCard
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToShop: () -> Unit = {},
    onNavigateToInn: () -> Unit = {},
    onNavigateToWorkerSkills: (Int) -> Unit = {},
    onNavigateToGuildHall: () -> Unit = {},
    onNavigateToChurch: () -> Unit = {},
    onNavigateToSlayer: () -> Unit = {},
    onNavigateToBuilder: () -> Unit = {},
    onNavigateToCarnival: () -> Unit = {},
    onNavigateToSeasonalEvent: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state            by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRecentLog by remember { mutableStateOf(false) }
    val context           = LocalContext.current

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { msg ->
            try { snackbarHostState.showSnackbar(msg) }
            finally { viewModel.snackbarConsumed() }
        }
    }

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
                            val total = summary.xpLineValues.getOrNull(i) ?: 0L
                            val breakdown = xpBreakdownText(total, bonus, summary.boostWasActive)
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(skill, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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
                                val breakdown = xpBreakdownText(summary.totalXpValue, summary.totalXpLabelBonus, summary.boostWasActive)
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
                    if (summary.killLines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        SummarySection(stringResource(R.string.label_kills))
                        summary.killLines.forEach { (enemy, kills) -> SummaryRow(enemy, kills) }
                    }
                    if (summary.itemLines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        SummarySection(stringResource(R.string.home_loot))
                        summary.itemLines.forEach { (item, qty) ->
                            val isRare = item in summary.rareItems
                            SummaryRow(
                                label = if (isRare) "🌟 $item" else item,
                                value = qty,
                                labelColor = if (isRare) GoldPrimary else MaterialTheme.colorScheme.onSurface,
                                valueColor = if (isRare) GoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isRare) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                    if (summary.coinsGained > 0) {
                        SummaryRow(stringResource(R.string.label_coins), "+${summary.coinsGained.formatCoins()}")
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
                    if (summary.arrowsConsumedLines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        SummarySection(stringResource(R.string.label_arrows_consumed))
                        summary.arrowsConsumedLines.forEach { (name, qty) -> SummaryRow(name, qty) }
                    }
                    if (summary.arrowsReclaimedLines.isNotEmpty()) {
                        SummarySection(stringResource(R.string.label_arrows_reclaimed))
                        summary.arrowsReclaimedLines.forEach { (name, qty) -> SummaryRow(name, qty) }
                    }
                    if (summary.runesConsumedLines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        SummarySection(stringResource(R.string.label_runes_consumed))
                        summary.runesConsumedLines.forEach { (name, qty) -> SummaryRow(name, qty) }
                    }
                    if (summary.runesReclaimedLines.isNotEmpty()) {
                        SummarySection(stringResource(R.string.label_runes_reclaimed))
                        summary.runesReclaimedLines.forEach { (name, qty) -> SummaryRow(name, qty) }
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
                            val total = summary.xpLineValues.getOrNull(i) ?: 0L
                            val breakdown = xpBreakdownText(total, bonus, summary.boostWasActive)
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(skill, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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
                                val breakdown = xpBreakdownText(summary.totalXpValue, summary.totalXpLabelBonus, summary.boostWasActive)
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
                    if (summary.killLines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        SummarySection(stringResource(R.string.label_kills))
                        summary.killLines.forEach { (enemy, kills) -> SummaryRow(enemy, kills) }
                    }
                    if (summary.itemLines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        SummarySection(stringResource(R.string.home_loot))
                        summary.itemLines.forEach { (item, qty) ->
                            val isRare = item in summary.rareItems
                            SummaryRow(
                                label = if (isRare) "🌟 $item" else item,
                                value = qty,
                                labelColor = if (isRare) GoldPrimary else MaterialTheme.colorScheme.onSurface,
                                valueColor = if (isRare) GoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isRare) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                    if (summary.coinsGained > 0) {
                        SummaryRow(stringResource(R.string.label_coins), "+${summary.coinsGained.formatCoins()}")
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

    if (state.journalSheetOpen) {
        val journalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissJournal,
            sheetState       = journalSheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            JournalSheet(
                notes  = state.playerNotes,
                onSave = { text ->
                    viewModel.updateNotes(text)
                    viewModel.dismissJournal()
                },
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top), // Responsible for top bar padding
        topBar = {
            TopAppBar(
                title   = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (!state.isLoading && state.showRecentActivityLog) {
                        IconButton(onClick = { showRecentLog = true }) {
                            Icon(Icons.Filled.History, contentDescription = stringResource(R.string.label_recent_activity))
                        }
                    }
                    if (!state.isLoading && state.showJournalButton) {
                        IconButton(onClick = viewModel::openJournal) {
                            Icon(Icons.Filled.EditNote, contentDescription = stringResource(R.string.label_journal))
                        }
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text  = stringResource(R.string.home_welcome_greeting),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val baseName = state.characterName.ifBlank { stringResource(R.string.home_adventurer) }
                    val titleName = state.titleName
                    Text(
                        text       = if (titleName == null) stringResource(R.string.home_welcome_name, baseName) else baseName,
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (titleName != null) {
                        Text(
                            text       = stringResource(R.string.home_welcome_title, titleName),
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (state.showCharacterViewer) {
                    CharacterSprite(
                        race       = state.characterRace.ifBlank { "human" },
                        skinTone   = state.characterSkinTone,
                        hairStyle  = state.characterHairStyle,
                        hairColor  = state.characterHairColor,
                        eyeStyle   = state.characterEyeStyle,
                        beardStyle = state.characterBeardStyle,
                        beardColor = state.characterBeardColor,
                        modifier   = Modifier.height(100.dp).aspectRatio(64f / 36f),
                    )
                }
            }

            if (state.showStatsCard) {
                Spacer(Modifier.height(8.dp))
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
            }

            // ── Town grid ───────────────────────────────────────────────
            val churchTint = if (state.activeBlessingKey.isNotEmpty() && state.activeBlessingRemainingMs > 0)
                GoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            val townGridRows: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TownGridCard(Icons.Filled.ShoppingCart, stringResource(R.string.label_shop),       onClick = onNavigateToShop,      modifier = Modifier.weight(1f))
                        TownGridCard(Icons.Filled.Person,        stringResource(R.string.inn_title),        onClick = onNavigateToInn,       modifier = Modifier.weight(1f))
                        TownGridCard(Icons.Filled.Group,         stringResource(R.string.guild_hall_title), onClick = onNavigateToGuildHall, modifier = Modifier.weight(1f), badgeCount = state.guildClaimableCount)
                        TownGridCard(Icons.Filled.Star,          stringResource(R.string.church_title),     onClick = onNavigateToChurch,    modifier = Modifier.weight(1f), iconTint = churchTint)
                    }
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Spacer(Modifier.weight(0.5f))
                        TownGridCard(Icons.Filled.Assignment,  stringResource(R.string.builder_title),  onClick = onNavigateToBuilder,  modifier = Modifier.weight(1f))
                        TownGridCard(Icons.Filled.Shield,      stringResource(R.string.slayer_title),   onClick = onNavigateToSlayer,   modifier = Modifier.weight(1f))
                        TownGridCard(Icons.Filled.Celebration, stringResource(R.string.carnival_title), onClick = onNavigateToCarnival, modifier = Modifier.weight(1f))
                        Spacer(Modifier.weight(0.5f))
                    }
                }
            }
            if (state.collapsibleTownGrid) {
                Surface(
                    shape    = RoundedCornerShape(16.dp),
                    color    = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleTownGridExpanded() },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text(
                                text  = stringResource(R.string.home_town_title),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                imageVector        = if (state.townGridExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AnimatedVisibility(visible = state.townGridExpanded) {
                            Column(Modifier.padding(top = 12.dp)) { townGridRows() }
                        }
                    }
                }
            } else {
                townGridRows()
            }

            // ── Seasonal Event row ────────────────────────────────────────
            if (state.showSeasonalEvents) state.activeSeasonalEvent?.let { event ->
                val eventComplete = event.tokens >= event.goal
                Surface(
                    shape    = RoundedCornerShape(16.dp),
                    color    = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = !eventComplete,
                    onClick  = onNavigateToSeasonalEvent,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val bannerId = event.bannerIcon?.let { LocalContext.current.drawableByName(it) }
                        if (bannerId != null) {
                            Image(
                                painter            = painterResource(bannerId),
                                contentDescription = null,
                                modifier           = Modifier.height(40.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text       = event.displayName,
                                    style      = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text  = if (eventComplete) stringResource(R.string.seasonal_event_complete)
                                            else stringResource(R.string.seasonal_event_token_progress, event.tokens, event.goal),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (eventComplete) GoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (eventComplete) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (event.tokens.toFloat() / event.goal).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            )
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
                    session        = session,
                    context        = context,
                    skillXp        = state.skillXp,
                    sessionXpGain  = state.activeSessionXpGain,
                    onRepeat       = viewModel::repeatActiveSession,
                    onAbandon      = viewModel::abandonSession,
                    onDebugFinish  = viewModel::debugFinishSession,
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
                    queue               = state.sessionQueue,
                    maxQueueSize        = state.maxQueueSize,
                    queueEndsAt         = state.queueEndsAt,
                    context             = context,
                    skillXp             = state.skillXp,
                    activeSessionSkill  = state.activeSession?.skillName ?: "",
                    activeSessionXpGain = state.activeSessionXpGain,
                    towerCurrentFloor   = state.towerCurrentFloor,
                    onRemove            = viewModel::removeFromQueue,
                    onMove              = viewModel::moveQueueItem,
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
                    skillXp                  = state.skillXp,
                    sessionXpGain            = state.workerSessionXpGain,
                    onCollect                = viewModel::collectWorkerSession,
                    onDismiss                = { viewModel.dismissWorker(1) },
                    onDebugFinish            = { viewModel.debugFinishWorkerSession(1) },
                    onNavigateToWorkerSkills = { onNavigateToWorkerSkills(1) },
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
                    skillXp                  = state.skillXp,
                    sessionXpGain            = state.workerSession2XpGain,
                    onCollect                = viewModel::collectWorkerSession,
                    onDismiss                = { viewModel.dismissWorker(2) },
                    onDebugFinish            = { viewModel.debugFinishWorkerSession(2) },
                    onNavigateToWorkerSkills = { onNavigateToWorkerSkills(2) },
                )
            }
        }
    }
}

@Composable
private fun TownGridCard(
    icon: ImageVector,
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    badgeCount: Int = 0,
) {
    ElevatedCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (badgeCount > 0) {
                BadgedBox(badge = { Badge { Text("$badgeCount") } }) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
                }
            } else {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text      = name,
                style     = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
            )
        }
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

