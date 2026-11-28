package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Surface
import com.fantasyidler.ui.viewmodel.ArenaUltimateUi
import com.fantasyidler.ui.viewmodel.ArenaZoneUi
import com.fantasyidler.ui.viewmodel.ArenaTrainerUi
import com.fantasyidler.util.formatCoins
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
import androidx.compose.ui.graphics.Color
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
    onWatch: () -> Unit = {},
    viewModel: PetArenaViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    AppBannerEffect(state.snackbarMessage, viewModel::snackbarConsumed)
    state.result?.let { PetArenaResultDialog(it, viewModel.petEmoji, viewModel.petData, viewModel::resultConsumed) }

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
                else                  -> ArenaTabs(state, viewModel, onWatch)
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
private fun ArenaTabs(state: PetArenaUiState, viewModel: PetArenaViewModel, onWatch: () -> Unit) {
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
                0    -> BattleTab(state, viewModel, onWatch)
                else -> PetsTab(state, viewModel)
            }
        }
    }
}

@Composable
private fun BattleTab(state: PetArenaUiState, viewModel: PetArenaViewModel, onWatch: () -> Unit) {
    val context = LocalContext.current
    val idle = state.active == null && state.team.isNotEmpty()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.active?.let { active -> item(key = "active") { ActiveBattleCard(active, viewModel, onWatch) } }
        if (state.team.isEmpty()) item(key = "noteam") {
            Text(stringResource(R.string.pet_arena_no_team), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        item(key = "hint") {
            Text(stringResource(R.string.pet_arena_types_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.pet_arena_risk_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item(key = "zones_hdr") { SectionTitle(stringResource(R.string.pet_arena_zones)) }
        items(state.zones, key = { "zone_${it.zone.id}" }) { z ->
            ZoneCard(z, canStart = idle) { viewModel.startZone(z.zone.id) }
        }
        item(key = "trainers_hdr") { SectionTitle(stringResource(R.string.pet_arena_trainers)) }
        items(state.trainers, key = { "trainer_${it.trainer.id}" }) { t ->
            TrainerCard(t, canStart = idle) { viewModel.startTrainer(t.trainer.id) }
        }
        state.ultimate?.let { u ->
            item(key = "ultimate_hdr") { SectionTitle(stringResource(R.string.pet_arena_ultimate)) }
            item(key = "ultimate") { UltimateCard(u, canStart = state.active == null, onStart = viewModel::startUltimate) }
        }
    }
}

@Composable
private fun ActiveBattleCard(active: ArenaActiveUi, viewModel: PetArenaViewModel, onWatch: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active.endsAt, active.completed) {
        while (!active.completed) {
            now = System.currentTimeMillis()
            if (now >= active.endsAt) { viewModel.refreshCompletion(); break }
            delay(1_000)
        }
    }
    val opponent = active.ultimateId?.let { GameStrings.arenaCreatureName(context, it) }
        ?: active.trainerId?.let { GameStrings.arenaTrainerName(context, it) }
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
            if (active.trainerId == null && active.ultimateId == null) Text(stringResource(R.string.pet_arena_wins_so_far, winsSoFar), style = MaterialTheme.typography.bodyMedium)
            if (active.completed) {
                Text(stringResource(R.string.pet_arena_finished), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Button(onClick = viewModel::collect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pet_arena_collect)) }
                OutlinedButton(onClick = onWatch, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pet_battle_watch_end)) }
            } else {
                Button(onClick = onWatch, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pet_battle_watch)) }
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

/** "25%" for a per-win drop chance, with a decimal below 10%. */
private fun chanceText(chance: Double): String =
    if (chance >= 0.1) "${(chance * 100).toInt()}%" else String.format(java.util.Locale.getDefault(), "%.1f%%", chance * 100)

@Composable
private fun CreatureChip(emoji: String, name: String, type: String, level: Int? = null) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text     = buildString {
                append("$emoji $name")
                if (level != null) append("  ${stringResource(R.string.pet_battle_level, level)}")
                append("  ${typeName(type).uppercase()}")
            },
            style    = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZoneCard(z: ArenaZoneUi, canStart: Boolean, onStart: () -> Unit) {
    val context = LocalContext.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(z.zone.emoji, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.width(40.dp))
                Column(Modifier.weight(1f)) {
                    Text(GameStrings.arenaZoneName(context, z.zone.id), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        text  = if (z.unlocked) stringResource(R.string.pet_arena_zone_levels, z.zone.levelMin, z.zone.levelMax)
                                else stringResource(R.string.pet_arena_zone_badges_needed, z.zone.badgesRequired),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onStart, enabled = canStart && z.unlocked) { Text(stringResource(R.string.pet_arena_battle)) }
            }
            Text(stringResource(R.string.pet_arena_zone_creatures), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in z.creatures) CreatureChip(c.emoji, GameStrings.arenaCreatureName(context, c.id), c.battleType)
            }
            Text(stringResource(R.string.pet_arena_zone_drops), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            val coins = z.zone.loot.firstOrNull { it.item == "coins" }
            if (coins != null) Text(stringResource(R.string.pet_arena_zone_coins, coins.quantityMin, coins.quantityMax), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (drop in z.zone.loot.filter { it.item != "coins" }.sortedByDescending { it.chance }) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            text     = "${GameStrings.itemName(context, drop.item)}  ${chanceText(drop.chance)}",
                            style    = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            z.zone.loot.filter { it.item != "coins" }.minByOrNull { it.chance }?.let { prize ->
                Text(stringResource(R.string.pet_arena_zone_alpha, GameStrings.itemName(context, prize.item)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrainerCard(t: ArenaTrainerUi, canStart: Boolean, onStart: () -> Unit) {
    val context = LocalContext.current
    val badge = "${t.trainer.badgeEmoji} ${GameStrings.arenaBadgeName(context, t.trainer.id)}"
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.trainer.emoji, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.width(40.dp))
                Column(Modifier.weight(1f)) {
                    Text(GameStrings.arenaTrainerName(context, t.trainer.id), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        text  = when {
                            t.beaten   -> stringResource(R.string.pet_arena_badge_earned, badge)
                            t.unlocked -> badge
                            else       -> stringResource(R.string.pet_arena_trainer_locked)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onStart, enabled = canStart && t.unlocked) { Text(stringResource(R.string.pet_arena_challenge)) }
            }
            Text(stringResource(R.string.pet_arena_trainer_team), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in t.creatures) CreatureChip(c.emoji, GameStrings.arenaCreatureName(context, c.id), c.battleType, t.trainer.level)
            }
            val reward = buildList {
                if (t.trainer.rewardCoins > 0) add(stringResource(R.string.pet_arena_results_coins, t.trainer.rewardCoins.formatCoins()))
                for ((key, qty) in t.trainer.rewardItems) add("${"%,d".format(qty)} ${GameStrings.itemName(context, key)}")
            }.joinToString(", ")
            Text(
                text  = if (t.beaten) stringResource(R.string.pet_arena_trainer_rematch) else stringResource(R.string.pet_arena_trainer_reward, reward),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun UltimateCard(u: ArenaUltimateUi, canStart: Boolean, onStart: () -> Unit) {
    val context = LocalContext.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(u.ultimate.emoji, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.width(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(GameStrings.arenaCreatureName(context, u.ultimate.id), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.pet_arena_ultimate_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onStart, enabled = canStart && u.unlocked) { Text(stringResource(R.string.pet_arena_challenge)) }
            }
            if (!u.unlocked) {
                Text(stringResource(R.string.pet_arena_ultimate_locked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text(stringResource(R.string.pet_arena_ultimate_desc), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.pet_arena_ultimate_reward), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            Text(
                text  = stringResource(R.string.pet_arena_ultimate_team, u.challengeTeam.size, u.ultimate.teamSize) +
                    if (u.wins > 0) "   " + stringResource(R.string.pet_arena_ultimate_wins, u.wins) else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
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
    // Pets rest back to full over time; tick so HP bars and countdowns keep up.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "team_hdr") {
            Text(stringResource(R.string.pet_arena_team_header, state.team.size, PetBattle.MAX_TEAM), style = MaterialTheme.typography.titleSmall)
            state.ultimate?.takeIf { it.unlocked }?.let { u ->
                Text(stringResource(R.string.pet_arena_ultimate_team, u.challengeTeam.size, u.ultimate.teamSize), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.pet_arena_boost_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.pet_arena_rest_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(state.pets, key = { it.id }) { pet ->
            PetCard(pet, viewModel.petData, now) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (pet.onTeam) OutlinedButton(onClick = { viewModel.toggleTeam(pet.id) }) { Text(stringResource(R.string.pet_arena_remove_from_team)) }
                    else Button(onClick = { viewModel.toggleTeam(pet.id) }) { Text(stringResource(R.string.pet_arena_add_to_team)) }
                    if (state.ultimate?.unlocked == true) {
                        TextButton(onClick = { viewModel.toggleChallenge(pet.id) }) {
                            Text(stringResource(if (pet.onChallenge) R.string.pet_arena_challenge_remove else R.string.pet_arena_challenge_add))
                        }
                    }
                    TextButton(onClick = { feeding = pet }) { Text(stringResource(R.string.pet_arena_feed)) }
                }
            }
        }
    }
    feeding?.let { pet ->
        FeedDialog(pet, PetBattle.restedFraction(pet.hpFraction, pet.hpAt, now), state.treats,
            onFeed = { key, qty -> viewModel.feed(pet.id, key, qty); feeding = null }, onDismiss = { feeding = null })
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
    "mythic"    -> R.string.pet_arena_rarity_mythic
    else        -> R.string.pet_arena_rarity_common
})

@Composable
private fun PetCard(
    pet: ArenaPetUi,
    petData: Map<String, com.fantasyidler.data.json.PetData> = emptyMap(),
    now: Long = System.currentTimeMillis(),
    action: @Composable () -> Unit,
) {
    val context = LocalContext.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(pet.emoji, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.size(48.dp), textAlign = TextAlign.Center)
            Column(Modifier.weight(1f).padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.pet_arena_name_level, pet.name, pet.level), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("${typeName(pet.type)}  ${rarityName(pet.rarity)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.pet_arena_stats, pet.attack, pet.defense, pet.hp), style = MaterialTheme.typography.bodySmall)
                // Wounds from the last battle, resting back to full.
                val fraction = PetBattle.restedFraction(pet.hpFraction, pet.hpAt, now)
                val hpNow = (fraction * pet.hp).toInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pet_battle_hp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    LinearProgressIndicator(
                        gapSize           = 0.dp,
                        drawStopIndicator = {},
                        progress          = { fraction.toFloat() },
                        color             = when {
                            fraction > 0.5 -> Color(0xFF4CAF50)
                            fraction > 0.2 -> Color(0xFFFFC107)
                            else           -> Color(0xFFF44336)
                        },
                        modifier          = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("$hpNow / ${pet.hp}", style = MaterialTheme.typography.labelSmall)
                }
                when {
                    hpNow <= 0     -> Text(stringResource(R.string.pet_arena_knocked_out, PetBattle.msToFullRest(fraction).formatDurationMs(context)),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    fraction < 1.0 -> Text(stringResource(R.string.pet_arena_resting, PetBattle.msToFullRest(fraction).formatDurationMs(context)),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (pet.wellFed > 0) Text(stringResource(R.string.pet_arena_well_fed, pet.wellFed), style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                val data = petData[pet.id]
                if (pet.boostPercent > 0 && data != null) {
                    val now = petBoostText(context, data, pet.boostPercent)
                    Text(
                        text  = if (pet.level >= PetBattle.MAX_LEVEL) now
                                else stringResource(R.string.pet_arena_boost_growth, now, petBoostText(context, data, PetBattle.boostPercent(data.boostPercent, PetBattle.MAX_XP))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
private fun FeedDialog(pet: ArenaPetUi, fraction: Double, treats: List<ArenaTreatUi>, onFeed: (String, Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val owned = treats.filter { it.owned > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text(stringResource(R.string.pet_arena_feed_title, pet.name)) },
        text             = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.pet_arena_feed_hp, (fraction * 100).toInt()), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (owned.isEmpty()) Text(stringResource(R.string.pet_arena_feed_none))
                for (t in owned) {
                    Column {
                        Text(
                            text  = stringResource(R.string.pet_arena_treat_line, GameStrings.itemName(context, t.key), t.owned,
                                (t.treat.heal * 100).toInt(), t.treat.wellFed),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onFeed(t.key, 1) }) { Text(stringResource(R.string.pet_arena_feed_one)) }
                            val toFull = minOf(t.owned, PetBattle.treatsToHeal(fraction, t.treat))
                            if (fraction < 1.0 && toFull > 1) TextButton(onClick = { onFeed(t.key, toFull) }) {
                                Text(stringResource(R.string.pet_arena_feed_to_full, toFull))
                            }
                        }
                    }
                }
            }
        },
        confirmButton    = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_close)) } },
    )
}
