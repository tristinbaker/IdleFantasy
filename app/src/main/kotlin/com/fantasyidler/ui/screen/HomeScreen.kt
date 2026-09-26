package com.fantasyidler.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.dropUnlessResumed
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.ui.components.PlayerStatsBar
import com.fantasyidler.ui.theme.ScaledSheetContent
import com.fantasyidler.ui.viewmodel.HomeViewModel
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.data.model.ElderSkills
import com.fantasyidler.ui.viewmodel.totalLevelFrom
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.drawableByName
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatDurationMs
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSaveSlots: () -> Unit = {},
    onNavigateToShop: () -> Unit = {},
    onNavigateToInn: () -> Unit = {},
    onNavigateToWorkerSkills: (Int) -> Unit = {},
    onNavigateToGuildHall: () -> Unit = {},
    onNavigateToChurch: () -> Unit = {},
    onNavigateToMonument: () -> Unit = {},
    onNavigateToSlayer: () -> Unit = {},
    onNavigateToBuilder: () -> Unit = {},
    onNavigateToHouse: () -> Unit = {},
    onNavigateToCarnival: () -> Unit = {},
    onNavigateToSeasonalEvent: () -> Unit = {},
    onNavigateToElderIsle: () -> Unit = {},
    onNavigateToElderIsleShop: () -> Unit = {},
    onNavigateToElderArmorMaster: () -> Unit = {},
    onNavigateToLoreMaster: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state            by viewModel.uiState.collectAsState()
    var showRecentLog by remember { mutableStateOf(false) }
    val context           = LocalContext.current

    AppBannerEffect(state.snackbarMessage, viewModel::snackbarConsumed)

    // First-arrival splash on Elder Isle: brief scene-set + a nudge toward the right
    // first tab. Persists a flag so subsequent landings don't nag.
    if (state.showIsleWelcome) {
        AlertDialog(
            onDismissRequest = viewModel::dismissIsleWelcome,
            title = { Text(stringResource(R.string.elder_isle_welcome_title), fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    text     = stringResource(R.string.elder_isle_welcome_body),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissIsleWelcome) {
                    Text(stringResource(R.string.elder_isle_welcome_cta))
                }
            },
        )
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
                            val breakdown = xpBreakdownText(total, bonus, summary.xpLineBoostFactors.getOrNull(i) ?: 1L)
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
                                            color = MaterialTheme.colorScheme.primary,
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
                                val breakdown = xpBreakdownText(summary.totalXpValue, summary.totalXpLabelBonus, summary.totalXpBoostFactor)
                                if (breakdown != null) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text  = breakdown,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                    if (summary.killLines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        SummarySection(stringResource(R.string.label_kills))
                        summary.killLines.forEach { (enemy, kills) -> SummaryRow(GameStrings.enemyName(context, enemy), kills) }
                    }
                    if (summary.itemLines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        SummarySection(stringResource(R.string.home_loot))
                        summary.itemLines.forEach { (item, qty) ->
                            val isRare = item in summary.rareItems
                            SummaryRow(
                                label = if (isRare) "🌟 $item" else item,
                                value = qty,
                                labelColor = if (isRare) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                valueColor = if (isRare) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = MaterialTheme.colorScheme.primary,
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
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.primary,
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
                            val breakdown = xpBreakdownText(total, bonus, summary.xpLineBoostFactors.getOrNull(i) ?: 1L)
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
                                            color = MaterialTheme.colorScheme.primary,
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
                                val breakdown = xpBreakdownText(summary.totalXpValue, summary.totalXpLabelBonus, summary.totalXpBoostFactor)
                                if (breakdown != null) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text  = breakdown,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                    if (summary.killLines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        SummarySection(stringResource(R.string.label_kills))
                        summary.killLines.forEach { (enemy, kills) -> SummaryRow(GameStrings.enemyName(context, enemy), kills) }
                    }
                    if (summary.itemLines.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        SummarySection(stringResource(R.string.home_loot))
                        summary.itemLines.forEach { (item, qty) ->
                            val isRare = item in summary.rareItems
                            SummaryRow(
                                label = if (isRare) "🌟 $item" else item,
                                value = qty,
                                labelColor = if (isRare) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                valueColor = if (isRare) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = MaterialTheme.colorScheme.primary,
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
            raceProficiencies = viewModel.raceProficiencies,
            isFirstTime       = true,
            showIronmanOption = true,
            onSave            = { name, gender, race, ironman -> viewModel.saveCharacterProfile(name, gender, race, ironman) },
            onDismiss         = viewModel::dismissCharacterSetup,
        )
    }

    if (showRecentLog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showRecentLog = false },
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            ScaledSheetContent {
            RecentSessionsSheet(
                sessions  = state.recentSessions,
                bossEmoji = viewModel::bossEmoji,
                onDismiss = { showRecentLog = false },
            )
            }
        }
    }

    if (state.journalSheetOpen) {
        val journalSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissJournal,
            sheetState       = journalSheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            ScaledSheetContent {
            JournalSheet(
                notes  = state.playerNotes,
                onSave = { text ->
                    viewModel.updateNotes(text)
                    viewModel.dismissJournal()
                },
            )
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top), // Responsible for top bar padding
        topBar = {
            TopAppBar(
                title   = { Text(stringResource(if (state.onElderIsle) R.string.home_title_elder_isle else R.string.app_name)) },
                actions = {
                    // dropUnlessResumed: ignore ghost taps during nav transitions (issue #1345)
                    if (!state.isLoading && state.showCharacterSwitch) {
                        IconButton(onClick = dropUnlessResumed { onNavigateToSaveSlots() }) {
                            Icon(Icons.Filled.SwitchAccount, contentDescription = stringResource(R.string.label_switch_character))
                        }
                    }
                    if (!state.isLoading && state.showRecentActivityLog) {
                        IconButton(onClick = dropUnlessResumed { showRecentLog = true }) {
                            Icon(Icons.Filled.History, contentDescription = stringResource(R.string.label_recent_activity))
                        }
                    }
                    if (!state.isLoading && state.showJournalButton) {
                        IconButton(onClick = dropUnlessResumed { viewModel.openJournal() }) {
                            Icon(Icons.Filled.EditNote, contentDescription = stringResource(R.string.label_journal))
                        }
                    }
                    IconButton(onClick = dropUnlessResumed { onNavigateToSettings() }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
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
                    if (state.ironman) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector        = Icons.Filled.Shield,
                                contentDescription = null,
                                modifier           = Modifier.size(14.dp),
                                tint               = MaterialTheme.colorScheme.tertiary,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text       = stringResource(R.string.ironman_badge),
                                style      = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.tertiary,
                            )
                        }
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

            // ── Isle currencies row (right under greeting; only on isle) ──
            if (state.onElderIsle) {
                Surface(
                    shape    = RoundedCornerShape(16.dp),
                    color    = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IsleStatChip("Ancient Sigils", state.inventory["ancient_sigil"] ?: 0, 40)
                        IsleStatChip("Elder Essence",  state.inventory["elder_essence"] ?: 0)
                        IsleStatChip("Elder Bones",    state.inventory["elder_bone"]    ?: 0)
                    }
                }
            }

            // ── Stats bar ───────────────────────────────────────────────
            if (state.showStatsBar) {
                Surface(
                    shape    = RoundedCornerShape(16.dp),
                    color    = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PlayerStatsBar(
                        context                    = context,
                        combatLevel                = combatLevelFrom(state.skillLevels),
                        totalLevel                 = if (state.onElderIsle) state.skillLevels.filterKeys { it in ElderSkills.ALL }.values.sum()
                                                     else totalLevelFrom(state.skillLevels),
                        coins                      = state.coins,
                        activeBlessingKey          = state.activeBlessingKey,
                        allBlessings               = state.allBlessings,
                        prayerCapeMult             = state.prayerCapeMult,
                        activeBlessingRemainingMs  = state.activeBlessingRemainingMs,
                        xpBoostRemainingMs         = state.xpBoostRemainingMs,
                        prestigeBoostsRemainingMs  = state.prestigeBoostsRemainingMs,
                        modifier                   = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }

            // ── Town grid (or isle grid) ───────────────────────────────
            val churchTint = if (state.activeBlessingKey.isNotEmpty() && state.activeBlessingRemainingMs > 0)
                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            val monumentTouchDotVisible = state.showMonumentTouchIndicator && state.monumentTouchAvailable
            val townGridRows: @Composable () -> Unit = {
                if (state.onElderIsle) {
                    // Isle-flavored grid: mirrors mainland town-grid card styling; 4 cards.
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TownGridCard(Icons.Filled.ShoppingCart,          stringResource(R.string.elder_isle_shop_title),          onClick = onNavigateToElderIsleShop,          modifier = Modifier.weight(1f))
                            TownGridCard(Icons.Filled.Shield,                stringResource(R.string.elder_isle_armor_master_title), onClick = onNavigateToElderArmorMaster,       modifier = Modifier.weight(1f))
                        }
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TownGridCard(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.elder_isle_lore_master_title),   onClick = onNavigateToLoreMaster,             modifier = Modifier.weight(1f))
                            TownGridCard(Icons.Filled.Explore,               stringResource(R.string.elder_isle_return),              onClick = viewModel::toggleElderIsleLocation, modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TownGridCard(Icons.Filled.ShoppingCart, stringResource(R.string.label_shop),       onClick = onNavigateToShop,      modifier = Modifier.weight(1f))
                            TownGridCard(Icons.Filled.Person,        stringResource(R.string.inn_title),        onClick = onNavigateToInn,       modifier = Modifier.weight(1f))
                            TownGridCard(Icons.Filled.Group,         stringResource(R.string.guild_hall_title), onClick = onNavigateToGuildHall, modifier = Modifier.weight(1f), badgeCount = state.guildClaimableCount)
                        }
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TownGridCard(Icons.Filled.Star,                    stringResource(R.string.church_title),   onClick = onNavigateToChurch,   modifier = Modifier.weight(1f), iconTint = churchTint)
                            TownGridCard(Icons.AutoMirrored.Filled.Assignment, stringResource(R.string.builder_title),  onClick = onNavigateToBuilder,  modifier = Modifier.weight(1f))
                            TownGridCard(Icons.Filled.Shield,                  stringResource(R.string.slayer_title),   onClick = onNavigateToSlayer,   modifier = Modifier.weight(1f))
                        }
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TownGridCard(Icons.Filled.Celebration,    stringResource(R.string.carnival_title), onClick = onNavigateToCarnival, modifier = Modifier.weight(1f))
                            TownGridCard(Icons.Filled.AccountBalance, stringResource(R.string.monument_title), onClick = onNavigateToMonument, modifier = Modifier.weight(1f), showDot = monumentTouchDotVisible)
                            TownGridCard(Icons.Filled.Home,           stringResource(R.string.house_title),    onClick = onNavigateToHouse,    modifier = Modifier.weight(1f))
                        }
                        // Show the Set Sail button as soon as the Dock is built, even before
                        // the Sea Serpent falls. Pre-kill tap surfaces the "defeat the Sea
                        // Serpent" snackbar via toggleElderIsleLocation, so the requirement is
                        // discoverable from Home instead of hidden.
                        if (state.dockBuilt) {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TownGridCard(
                                    Icons.Filled.Explore,
                                    stringResource(R.string.elder_isle_set_sail),
                                    onClick = viewModel::toggleElderIsleLocation,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
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

            // ── Seasonal Event row (hidden on isle — mainland-only content) ──
            if (!state.onElderIsle && state.showSeasonalEvents) state.activeSeasonalEvent?.let { event ->
                val eventComplete = event.tokens >= event.goal
                Surface(
                    shape    = RoundedCornerShape(16.dp),
                    color    = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
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
                                    text       = GameStrings.seasonalEventName(LocalContext.current, event.id, event.displayName),
                                    style      = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text  = if (eventComplete) stringResource(R.string.seasonal_event_complete)
                                            else stringResource(R.string.seasonal_event_token_progress, event.tokens, event.goal),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (eventComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (eventComplete) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                                progress = { (event.tokens.toFloat() / event.goal).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text  = stringResource(
                                    R.string.format_time_remaining,
                                    (event.endMs - System.currentTimeMillis()).coerceAtLeast(0).formatDurationMs(LocalContext.current),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    showEndTime    = state.showSessionEndTime,
                    bossEmoji      = if (session.skillName == "boss") viewModel.bossEmoji(session.activityKey) else null,
                    bossDurationMinutes = if (session.skillName == "boss") viewModel.bossDurationMinutes(session.activityKey) else null,
                    repeatIndex    = if (session.skillName == "boss") state.activeBossRepeatIndex else state.activeDungeonRepeatIndex,
                    repeatTotal    = if (session.skillName == "boss") state.activeBossRepeatTotal else state.activeDungeonRepeatTotal,
                    assignedItems  = state.activeSessionAssignedItems,
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
                    enabled  = !state.isCollecting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isCollecting) {
                        CircularProgressIndicator(
                            modifier    = Modifier.height(20.dp).width(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(pluralStringResource(R.plurals.plural_collect_sessions, n, n))
                    }
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
                    showEndTime         = state.showSessionEndTime,
                    bossEmoji           = viewModel::bossEmoji,
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
                    showEndTime              = state.showSessionEndTime,
                    assignedItems            = state.workerSessionAssignedItems,
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
                    showEndTime              = state.showSessionEndTime,
                    assignedItems            = state.workerSession2AssignedItems,
                    onCollect                = viewModel::collectWorkerSession,
                    onDismiss                = { viewModel.dismissWorker(2) },
                    onDebugFinish            = { viewModel.debugFinishWorkerSession(2) },
                    onNavigateToWorkerSkills = { onNavigateToWorkerSkills(2) },
                )
            }
        }
    }
}

/**
 * Isle mission tracker: elder armor crafting progress + main story acts. Slots into the
 * mainland Home layout as an isle-only extra section, so the surrounding chrome
 * (greeting, session banner, stats bar) stays consistent across mainland and isle.
 */
@Composable
private fun IsleMissionTracker(inventory: Map<String, Int>) {
    val elderPieces = listOf(
        "elder_helm"        to "Elder Helm",
        "elder_platebody"   to "Elder Platebody",
        "elder_platelegs"   to "Elder Platelegs",
        "elder_boots"       to "Elder Boots",
        "elder_cape"        to "Elder Cape",
        "elder_shield"      to "Elder Shield",
        "elder_signet_ring" to "Elder Ring",
        "elder_amulet"      to "Elder Amulet",
    )
    val ownedPieces  = elderPieces.count { (inventory[it.first] ?: 0) >= 1 }
    val setComplete  = ownedPieces == elderPieces.size
    val lastElderKilled = (inventory["ancient_signet"] ?: 0) >= 1
    val actsCompleted = if (lastElderKilled) 4 else 0
    val sigilCount = inventory["ancient_sigil"] ?: 0
    val essence    = inventory["elder_essence"] ?: 0
    val bones      = inventory["elder_bone"] ?: 0

    // Elder set progress card
    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = "Elder Set Progress",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.weight(1f),
                )
                Text(
                    text  = "$ownedPieces / ${elderPieces.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { ownedPieces / elderPieces.size.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
            Spacer(Modifier.height(12.dp))
            elderPieces.forEach { (key, name) ->
                val owned = (inventory[key] ?: 0) >= 1
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(
                        text  = if (owned) "✓" else "•",
                        color = if (owned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(20.dp),
                    )
                    Text(
                        text  = name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (owned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text  = if (owned) "Crafted" else "5 Sigils + mats",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (setComplete) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text  = "Full set assembled. Challenge the Last Elder from the Combat tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    // Story arc board
    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = "The Isle Story",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.weight(1f),
                )
                Text(
                    text  = "$actsCompleted / 4 acts",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            IsleActRow("I",   "The Landing",        "Beach & Cliffs — meet Rowan.",                    actsCompleted >= 1)
            IsleActRow("II",  "The Buried Library", "Ancient Forest — piece together the story.",     actsCompleted >= 2)
            IsleActRow("III", "The Sealing Site",   "Volcano Peak — climb to the failing seal.",      actsCompleted >= 3)
            IsleActRow("IV",  "The Last Elder",     "Abyssal Depths — face the last Elder.",          actsCompleted >= 4)
        }
    }

    // Isle currencies row
    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IsleStatChip("Ancient Sigils", sigilCount, 40)
            IsleStatChip("Elder Essence", essence)
            IsleStatChip("Elder Bones", bones)
        }
    }
}

/** LEGACY — no longer referenced from HomeScreen but kept while other call sites are pruned. */
@Suppress("unused")
@Composable
private fun IsleHomeContent(
    onReturnToMainland: () -> Unit,
    onNavigateToShop: () -> Unit,
    modifier: Modifier = Modifier,
    homeVm: com.fantasyidler.ui.viewmodel.HomeViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val homeState by homeVm.uiState.collectAsState()
    val inv       = homeState.inventory
    val sigilCount = inv["ancient_sigil"] ?: 0
    val essence    = inv["elder_essence"] ?: 0
    val bones      = inv["elder_bone"] ?: 0

    // Elder BIS tracker — 8 pieces, "owned" = at least one in inventory.
    val elderPieces = listOf(
        "elder_helm"        to "Elder Helm",
        "elder_platebody"   to "Elder Platebody",
        "elder_platelegs"   to "Elder Platelegs",
        "elder_boots"       to "Elder Boots",
        "elder_cape"        to "Elder Cape",
        "elder_shield"      to "Elder Shield",
        "elder_signet_ring" to "Elder Ring",
        "elder_amulet"      to "Elder Amulet",
    )
    val ownedPieces  = elderPieces.count { (inv[it.first] ?: 0) >= 1 }
    val setComplete  = ownedPieces == elderPieces.size

    // Story arc — Act IV completes when Last Elder drops the Ancient Signet. Placeholder
    // until per-act quest tracking lands.
    val lastElderKilled = (inv["ancient_signet"] ?: 0) >= 1
    val actsCompleted = if (lastElderKilled) 4 else 0

    Column(
        modifier            = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text       = stringResource(R.string.elder_isle_title),
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text  = "Tabs at the bottom go to elder skills, dungeons, quests, and profile. Return to the mainland when done.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ── Elder armor tracker ──────────────────────────────────────
        Surface(
            shape    = MaterialTheme.shapes.medium,
            color    = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = "Elder Set Progress",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.weight(1f),
                    )
                    Text(
                        text  = "$ownedPieces / ${elderPieces.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { ownedPieces / elderPieces.size.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
                Spacer(Modifier.height(12.dp))
                elderPieces.forEach { (key, name) ->
                    val owned = (inv[key] ?: 0) >= 1
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(
                            text  = if (owned) "✓" else "•",
                            color = if (owned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(20.dp),
                        )
                        Text(
                            text  = name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (owned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text  = if (owned) "Crafted" else "5 Sigils + mats",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (setComplete) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text  = "Full set assembled. Challenge the Last Elder from the Combat tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // ── Story arc board ──────────────────────────────────────────
        Surface(
            shape    = MaterialTheme.shapes.medium,
            color    = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = "The Isle Story",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.weight(1f),
                    )
                    Text(
                        text  = "$actsCompleted / 4 acts",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text  = "The Elders mastered eternal life through the Grand Ritual and were sealed by their own creation. Retrace their path across four acts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                IsleActRow("I",   "The Landing",        "Beach & Cliffs — meet Rowan.",                                   actsCompleted >= 1)
                IsleActRow("II",  "The Buried Library", "Ancient Forest — piece together the Elders' story.",             actsCompleted >= 2)
                IsleActRow("III", "The Sealing Site",   "Volcano Peak — climb to the failing seal.",                      actsCompleted >= 3)
                IsleActRow("IV",  "The Last Elder",     "Abyssal Depths — face the last corrupted Elder.",                actsCompleted >= 4)
                Spacer(Modifier.height(6.dp))
                Text(
                    text  = "Full quest chain lands in a later build. For now, the Last Elder kill unlocks the final act.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── Currencies row ──────────────────────────────────────────
        Surface(
            shape    = MaterialTheme.shapes.medium,
            color    = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IsleStatChip("Ancient Sigils", sigilCount, 40)
                IsleStatChip("Elder Essence", essence)
                IsleStatChip("Elder Bones", bones)
            }
        }

        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.OutlinedButton(
            onClick  = onNavigateToShop,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.elder_isle_shop_button))
        }
        androidx.compose.material3.Button(
            onClick  = onReturnToMainland,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.elder_isle_return))
        }
    }
}

@Composable
private fun IsleActRow(roman: String, title: String, subtitle: String, done: Boolean) {
    Row(
        modifier            = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment   = Alignment.CenterVertically,
    ) {
        Text(
            text       = roman,
            style      = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color      = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier   = Modifier.width(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text  = if (done) "✓" else "•",
            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IsleStatChip(label: String, value: Int, target: Int = 0) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text  = if (target > 0) "$value / $target" else "$value",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IsleZoneCard(title: String, body: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text  = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text  = stringResource(R.string.elder_isle_zone_locked),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    showDot: Boolean = false,
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
            } else if (showDot) {
                BadgedBox(badge = { Badge() }) {
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
