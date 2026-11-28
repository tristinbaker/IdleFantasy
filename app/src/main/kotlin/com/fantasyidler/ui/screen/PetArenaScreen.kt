package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.simulator.PetBattle
import com.fantasyidler.ui.theme.LocalTabSwipeEnabled
import com.fantasyidler.ui.viewmodel.ArenaActiveUi
import com.fantasyidler.ui.viewmodel.ArenaPetUi
import com.fantasyidler.ui.viewmodel.ArenaTreatUi
import com.fantasyidler.ui.viewmodel.PetArenaUiState
import com.fantasyidler.ui.viewmodel.PetArenaViewModel
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatDurationMs
import com.fantasyidler.util.formatXp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetArenaScreen(
    onBack: () -> Unit = {},
    viewModel: PetArenaViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    AppBannerEffect(state.snackbarMessage, viewModel::snackbarConsumed)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.pet_arena_title))
                        if (state.trainers.isNotEmpty()) Text(
                            text  = stringResource(R.string.pet_arena_badges, state.badgeCount, state.trainers.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isLoading       -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.pets.isEmpty()  -> CenteredNote(stringResource(R.string.pet_arena_locked_title), stringResource(R.string.pet_arena_locked_desc))
                state.needsStarter    -> StarterPicker(state.pets, viewModel::chooseStarter)
                else                  -> ArenaTabs(state, viewModel)
            }
        }
    }
}

@Composable
private fun CenteredNote(title: String, body: String) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StarterPicker(pets: List<ArenaPetUi>, onChoose: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(stringResource(R.string.pet_arena_starter_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.pet_arena_starter_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.pet_arena_types_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(pets, key = { it.id }) { pet ->
            PetCard(pet) {
                Button(onClick = { onChoose(pet.id) }) { Text(stringResource(R.string.pet_arena_choose)) }
            }
        }
    }
}

@Composable
private fun ArenaTabs(state: PetArenaUiState, viewModel: PetArenaViewModel) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    Column {
        TabRow(selectedTabIndex = pagerState.currentPage) {
            Tab(selected = pagerState.currentPage == 0, onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text(stringResource(R.string.pet_arena_tab_battle)) })
            Tab(selected = pagerState.currentPage == 1, onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text(stringResource(R.string.pet_arena_tab_pets)) })
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), userScrollEnabled = LocalTabSwipeEnabled.current) { page ->
            when (page) {
                0    -> BattleTab(state, viewModel)
                else -> PetsTab(state, viewModel)
            }
        }
    }
}

@Composable
private fun BattleTab(state: PetArenaUiState, viewModel: PetArenaViewModel) {
    val context = LocalContext.current
    val idle = state.active == null && state.team.isNotEmpty()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.active?.let { active -> item(key = "active") { ActiveBattleCard(active, viewModel) } }
        if (state.team.isEmpty()) item(key = "noteam") {
            Text(stringResource(R.string.pet_arena_no_team), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        item(key = "hint") {
            Text(stringResource(R.string.pet_arena_types_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item(key = "zones_hdr") { SectionTitle(stringResource(R.string.pet_arena_zones)) }
        items(state.zones, key = { "zone_${it.zone.id}" }) { z ->
            ArenaRow(
                emoji    = z.zone.emoji,
                title    = GameStrings.arenaZoneName(context, z.zone.id),
                subtitle = if (z.unlocked) stringResource(R.string.pet_arena_zone_levels, z.zone.levelMin, z.zone.levelMax)
                           else stringResource(R.string.pet_arena_zone_badges_needed, z.zone.badgesRequired),
            ) {
                Button(onClick = { viewModel.startZone(z.zone.id) }, enabled = idle && z.unlocked) {
                    Text(stringResource(R.string.pet_arena_battle))
                }
            }
        }
        item(key = "trainers_hdr") { SectionTitle(stringResource(R.string.pet_arena_trainers)) }
        items(state.trainers, key = { "trainer_${it.trainer.id}" }) { t ->
            val info = stringResource(R.string.pet_arena_trainer_info, t.trainer.level, t.trainer.team.size)
            ArenaRow(
                emoji    = t.trainer.emoji,
                title    = GameStrings.arenaTrainerName(context, t.trainer.id),
                subtitle = when {
                    t.beaten   -> "${t.trainer.badgeEmoji} " + stringResource(R.string.pet_arena_badge_earned, GameStrings.arenaBadgeName(context, t.trainer.id))
                    t.unlocked -> "$info\n${t.trainer.badgeEmoji} ${GameStrings.arenaBadgeName(context, t.trainer.id)}"
                    else       -> stringResource(R.string.pet_arena_trainer_locked)
                },
            ) {
                OutlinedButton(onClick = { viewModel.startTrainer(t.trainer.id) }, enabled = idle && t.unlocked) {
                    Text(stringResource(R.string.pet_arena_challenge))
                }
            }
        }
    }
}

@Composable
private fun ActiveBattleCard(active: ArenaActiveUi, viewModel: PetArenaViewModel) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active.endsAt, active.completed) {
        while (!active.completed) {
            now = System.currentTimeMillis()
            if (now >= active.endsAt) { viewModel.refreshCompletion(); break }
            delay(1_000)
        }
    }
    val opponent = active.trainerId?.let { GameStrings.arenaTrainerName(context, it) }
    val place = active.zoneId?.let { GameStrings.arenaZoneName(context, it) }
    val minute = ((now - active.startedAt) / 60_000L).toInt()
    val winsSoFar = if (active.completed) active.winsByMinute.lastOrNull() ?: 0
                    else active.winsByMinute.getOrNull(minute - 1) ?: 0
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text       = if (opponent != null) stringResource(R.string.pet_arena_active_trainer, opponent)
                             else stringResource(R.string.pet_arena_active_zone, place ?: ""),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (active.trainerId == null) Text(stringResource(R.string.pet_arena_wins_so_far, winsSoFar), style = MaterialTheme.typography.bodyMedium)
            if (active.completed) {
                Text(stringResource(R.string.pet_arena_finished), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Button(onClick = viewModel::collect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pet_arena_collect)) }
            } else {
                val total = (active.endsAt - active.startedAt).coerceAtLeast(1L)
                LinearProgressIndicator(
                    gapSize           = 0.dp,
                    drawStopIndicator = {},
                    progress          = { ((now - active.startedAt).toFloat() / total).coerceIn(0f, 1f) },
                    modifier          = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                )
                Text(
                    text  = stringResource(R.string.pet_arena_ends_in, (active.endsAt - now).coerceAtLeast(0L).formatDurationMs(context)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ArenaRow(emoji: String, title: String, subtitle: String, action: @Composable () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            action()
        }
    }
}

@Composable
private fun PetsTab(state: PetArenaUiState, viewModel: PetArenaViewModel) {
    var feeding by remember { mutableStateOf<ArenaPetUi?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "team_hdr") {
            Text(stringResource(R.string.pet_arena_team_header, state.team.size, PetBattle.MAX_TEAM), style = MaterialTheme.typography.titleSmall)
        }
        items(state.pets, key = { it.id }) { pet ->
            PetCard(pet) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (pet.onTeam) OutlinedButton(onClick = { viewModel.toggleTeam(pet.id) }) { Text(stringResource(R.string.pet_arena_remove_from_team)) }
                    else Button(onClick = { viewModel.toggleTeam(pet.id) }) { Text(stringResource(R.string.pet_arena_add_to_team)) }
                    if (pet.level < PetBattle.MAX_LEVEL) TextButton(onClick = { feeding = pet }) { Text(stringResource(R.string.pet_arena_feed)) }
                }
            }
        }
    }
    feeding?.let { pet ->
        FeedDialog(pet, state.treats, onFeed = { key, qty -> viewModel.feed(pet.id, key, qty); feeding = null }, onDismiss = { feeding = null })
    }
}

@Composable
private fun typeName(type: String): String = stringResource(when (type) {
    "ranged" -> R.string.pet_arena_type_ranged
    "magic"  -> R.string.pet_arena_type_magic
    else     -> R.string.pet_arena_type_melee
})

@Composable
private fun rarityName(rarity: String): String = stringResource(when (rarity) {
    "uncommon"  -> R.string.pet_arena_rarity_uncommon
    "rare"      -> R.string.pet_arena_rarity_rare
    "epic"      -> R.string.pet_arena_rarity_epic
    "legendary" -> R.string.pet_arena_rarity_legendary
    else        -> R.string.pet_arena_rarity_common
})

@Composable
private fun PetCard(pet: ArenaPetUi, action: @Composable () -> Unit) {
    val context = LocalContext.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(pet.emoji, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.size(48.dp), textAlign = TextAlign.Center)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.pet_arena_name_level, pet.name, pet.level), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("${typeName(pet.type)}  ${rarityName(pet.rarity)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.pet_arena_stats, pet.attack, pet.defense, pet.hp), style = MaterialTheme.typography.bodySmall)
                if (pet.boostPercent > 0) {
                    val boost = stringResource(if (pet.effectType == "coin_boost") R.string.format_coin_boost_percent else R.string.format_xp_boost_percent, pet.boostPercent)
                    val scope = when (pet.boostedSkill) {
                        "all", "coins" -> if (pet.boostedSkill == "all") stringResource(R.string.bonus_all_skills) else ""
                        else           -> GameStrings.skillName(context, pet.boostedSkill)
                    }
                    Text(if (scope.isEmpty()) boost else "$boost ($scope)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(
                    gapSize           = 0.dp,
                    drawStopIndicator = {},
                    progress          = { pet.levelProgress },
                    modifier          = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
                Text(
                    text  = if (pet.level >= PetBattle.MAX_LEVEL) stringResource(R.string.pet_arena_max_level)
                            else stringResource(R.string.pet_arena_xp_to_next, pet.xpToNext.formatXp()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            action()
        }
    }
}

@Composable
private fun FeedDialog(pet: ArenaPetUi, treats: List<ArenaTreatUi>, onFeed: (String, Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val owned = treats.filter { it.owned > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text(stringResource(R.string.pet_arena_feed_title, pet.name)) },
        text             = {
            if (owned.isEmpty()) Text(stringResource(R.string.pet_arena_feed_none))
            else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (t in owned) {
                    Column {
                        Text(stringResource(R.string.pet_arena_treat_line, GameStrings.itemName(context, t.key), t.owned, t.xpEach.formatXp()),
                            style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onFeed(t.key, 1) }) { Text(stringResource(R.string.pet_arena_feed_one)) }
                            if (t.owned >= 10) TextButton(onClick = { onFeed(t.key, 10) }) { Text(stringResource(R.string.pet_arena_feed_ten)) }
                            TextButton(onClick = { onFeed(t.key, t.owned) }) { Text(stringResource(R.string.pet_arena_feed_all)) }
                        }
                    }
                }
            }
        },
        confirmButton    = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_close)) } },
    )
}
