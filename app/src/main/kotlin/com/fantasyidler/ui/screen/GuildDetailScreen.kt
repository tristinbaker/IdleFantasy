package com.fantasyidler.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.json.GuildDailyTemplate
import com.fantasyidler.data.json.GuildQuestData
import com.fantasyidler.repository.GuildDailyWithProgress
import com.fantasyidler.repository.GuildQuestWithProgress
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.GuildDetailViewModel
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins

@Composable
private fun localizedQuestDesc(type: String, target: String, amount: Int, guild: String): String {
    val context = LocalContext.current
    val guildName = guildDisplayName(guild)
    val itemName  = if (target.isNotEmpty() && target != "any") GameStrings.itemName(context, target) else ""
    val combatStyle = when (guild) {
        "warriors" -> stringResource(R.string.guild_combat_melee)
        "archers"  -> stringResource(R.string.guild_combat_ranged)
        "mages"    -> stringResource(R.string.guild_combat_magic)
        else       -> guild
    }
    val gatherVerb = run {
        val id = context.resources.getIdentifier("daily_verb_$guild", "string", context.packageName)
        if (id != 0) context.getString(id) else context.getString(R.string.daily_verb_mining)
    }
    return when (type) {
        "gather"     -> context.getString(R.string.guild_quest_desc_gather, gatherVerb, amount, itemName, guildName)
        "craft"      -> stringResource(R.string.guild_quest_desc_craft, amount, itemName, guildName)
        "kill"       -> stringResource(R.string.guild_quest_desc_kill, amount, combatStyle)
        "prayer"     -> stringResource(R.string.guild_quest_desc_prayer, amount, guildName)
        "sessions"   -> stringResource(R.string.guild_quest_desc_sessions, amount, GameStrings.skillName(context, target), guildName)
        "trade"      -> stringResource(R.string.guild_quest_desc_trade, amount, guildName)
        "earn_coins"  -> stringResource(R.string.guild_quest_desc_earn_coins, amount.toLong().formatCoins(), guildName)
        "pickpocket"  -> stringResource(R.string.guild_quest_desc_pickpocket, amount, GameStrings.thievingNpcName(context, target), guildName)
        else          -> ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuildDetailScreen(
    onBack: () -> Unit = {},
    viewModel: GuildDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it, withDismissAction = true)
            viewModel.snackbarConsumed()
        }
    }

    val guildName = guildDisplayName(state.guildKey)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(guildName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier
                            .clickable { viewModel.toggleHideCompleted() }
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text  = stringResource(R.string.label_hide_completed),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(Modifier.width(4.dp))
                        Switch(
                            checked         = state.hideCompleted,
                            onCheckedChange = { viewModel.toggleHideCompleted() },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val pagerState     = rememberPagerState(pageCount = { 2 })
        val scope          = rememberCoroutineScope()
        val claimableQuests = state.quests.count { !it.completed && it.progress >= it.effectiveAmount && state.guildLevel >= it.quest.guildLevelRequired }
        val claimableDailies = state.dailies.count { !it.claimed && it.progress >= it.template.amount }

        Column(Modifier.fillMaxSize().padding(padding)) {
            GuildRepHeader(
                level                     = state.guildLevel,
                repInLevel                = state.repInLevel,
                repForLevel               = state.repForLevel,
                allCurrentLevelQuestsDone = state.allCurrentLevelQuestsDone,
                questGateBlocked          = state.questGateBlocked,
            )

            TabRow(selectedTabIndex = pagerState.currentPage) {
                val questLabel = stringResource(R.string.guild_tab_quests).let {
                    if (claimableQuests > 0) "$it ($claimableQuests)" else it
                }
                val dailyLabel = stringResource(R.string.guild_tab_dailies).let {
                    if (claimableDailies > 0) "$it ($claimableDailies)" else it
                }
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick  = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text     = { Text(questLabel, style = MaterialTheme.typography.labelMedium) },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick  = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text     = { Text(dailyLabel, style = MaterialTheme.typography.labelMedium) },
                )
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> GuildQuestsTab(
                        quests        = state.quests,
                        guildLevel    = state.guildLevel,
                        inventory     = state.inventory,
                        hideCompleted = state.hideCompleted,
                        onClaim       = { viewModel.claimGuildQuest(it) },
                        onContribute  = { viewModel.contributeFarmingQuest(it) },
                    )
                    else -> GuildDailiesTab(
                        dailies       = state.dailies,
                        nextResetMs   = state.nextResetMs,
                        inventory     = state.inventory,
                        hideCompleted = state.hideCompleted,
                        onClaim       = { viewModel.claimGuildDaily(it) },
                        onContribute  = { viewModel.contributeFarmingDaily(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GuildRepHeader(
    level: Int,
    repInLevel: Long,
    repForLevel: Long,
    allCurrentLevelQuestsDone: Boolean,
    questGateBlocked: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        val levelText = if (level >= 10) stringResource(R.string.guild_level_max)
                        else stringResource(R.string.guild_level_label, level)
        Text(
            text       = levelText,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (level < 10) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (repInLevel.toFloat() / repForLevel.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color    = if (questGateBlocked) MaterialTheme.colorScheme.error else GoldPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text  = stringResource(R.string.guild_rep_label, repInLevel, repForLevel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                questGateBlocked -> {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = stringResource(R.string.guild_quest_gate_blocked),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                allCurrentLevelQuestsDone && repInLevel < repForLevel -> {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = stringResource(R.string.guild_do_dailies_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldPrimary,
                    )
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun GuildQuestsTab(
    quests: List<GuildQuestWithProgress>,
    guildLevel: Int,
    inventory: Map<String, Int>,
    hideCompleted: Boolean = false,
    onClaim: (String) -> Unit,
    onContribute: (String) -> Unit,
) {
    val visibleQuests = if (hideCompleted) quests.filter { !it.completed } else quests
    LazyColumn(Modifier.fillMaxSize()) {
        if (visibleQuests.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = stringResource(R.string.quests_none_in_category),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(visibleQuests, key = { it.quest.id }) { qwp ->
                GuildQuestRow(
                    qwp          = qwp,
                    guildLevel   = guildLevel,
                    inventoryQty = if (qwp.quest.guild == "farming" && qwp.quest.type == "gather")
                        inventory[qwp.quest.target] ?: 0 else 0,
                    onClaim      = { onClaim(qwp.quest.id) },
                    onContribute = { onContribute(qwp.quest.id) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun GuildQuestRow(
    qwp: GuildQuestWithProgress,
    guildLevel: Int,
    inventoryQty: Int = 0,
    onClaim: () -> Unit,
    onContribute: () -> Unit = {},
) {
    val locked   = guildLevel < qwp.quest.guildLevelRequired
    val dimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (locked) {
                Icon(
                    imageVector        = Icons.Filled.Lock,
                    contentDescription = null,
                    tint               = dimColor,
                    modifier           = Modifier.padding(end = 6.dp).height(16.dp).width(16.dp),
                )
            }
            Text(
                text       = GameStrings.questName(LocalContext.current, qwp.quest.id, qwp.quest.name),
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color      = if (locked) dimColor else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text  = localizedQuestDesc(qwp.quest.type, qwp.quest.target, qwp.effectiveAmount, qwp.quest.guild),
            style = MaterialTheme.typography.bodySmall,
            color = if (locked) dimColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (locked) {
            Spacer(Modifier.height(4.dp))
            Text(
                text  = stringResource(R.string.guild_locked_hint, qwp.quest.guildLevelRequired),
                style = MaterialTheme.typography.labelSmall,
                color = dimColor,
            )
            return@Column
        }

        if (qwp.completed) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.label_completed),
                    tint               = Color(0xFF4CAF50),
                    modifier           = Modifier.height(18.dp).width(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text       = stringResource(R.string.label_completed),
                    style      = MaterialTheme.typography.labelMedium,
                    color      = Color(0xFF4CAF50),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Spacer(Modifier.height(8.dp))
            val fraction = (qwp.progress.toFloat() / qwp.effectiveAmount.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color    = GoldPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = "${qwp.progress} / ${qwp.effectiveAmount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (qwp.progress >= qwp.effectiveAmount) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onClaim, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.label_claim_reward))
                }
            } else if (inventoryQty > 0) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onContribute, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.guild_contribute_inventory, inventoryQty))
                }
            }
        }
    }
}

@Composable
private fun GuildDailiesTab(
    dailies: List<GuildDailyWithProgress>,
    nextResetMs: Long,
    inventory: Map<String, Int>,
    hideCompleted: Boolean = false,
    onClaim: (String) -> Unit,
    onContribute: (String) -> Unit,
) {
    val visibleDailies = if (hideCompleted) dailies.filter { !it.claimed } else dailies
    LazyColumn(Modifier.fillMaxSize()) {
        if (visibleDailies.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = stringResource(R.string.guild_no_dailies),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(visibleDailies, key = { it.template.id }) { dwp ->
                GuildDailyCard(
                    dwp          = dwp,
                    inventoryQty = if (dwp.template.guild == "farming" && dwp.template.type == "gather")
                        inventory[dwp.template.target] ?: 0 else 0,
                    onClaim      = { onClaim(dwp.template.id) },
                    onContribute = { onContribute(dwp.template.id) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        item {
            Text(
                text     = stringResource(R.string.guild_daily_resets_in),
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun GuildDailyCard(
    dwp: GuildDailyWithProgress,
    inventoryQty: Int = 0,
    onClaim: () -> Unit,
    onContribute: () -> Unit = {},
) {
    val isComplete = dwp.progress >= dwp.template.amount
    val isClaimed  = dwp.claimed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text       = GameStrings.questName(LocalContext.current, dwp.template.id, dwp.template.name),
            style      = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text  = localizedQuestDesc(dwp.template.type, dwp.template.target, dwp.template.amount, dwp.template.guild),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!isClaimed) {
            Spacer(Modifier.height(8.dp))
            val fraction = (dwp.progress.toFloat() / dwp.template.amount.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color    = GoldPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = if (dwp.template.type == "earn_coins") {
                    "${dwp.progress.coerceAtMost(dwp.template.amount).toLong().formatCoins()} / ${dwp.template.amount.toLong().formatCoins()}"
                } else {
                    "${dwp.progress.coerceAtMost(dwp.template.amount)} / ${dwp.template.amount}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            isClaimed -> {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector        = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.label_completed),
                        tint               = Color(0xFF4CAF50),
                        modifier           = Modifier.height(18.dp).width(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text       = stringResource(R.string.label_completed),
                        style      = MaterialTheme.typography.labelMedium,
                        color      = Color(0xFF4CAF50),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            isComplete -> {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onClaim, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.label_claim_reward))
                }
            }
            else -> {
                if (inventoryQty > 0) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onContribute, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.guild_contribute_inventory, inventoryQty))
                    }
                }
            }
        }
    }
}
