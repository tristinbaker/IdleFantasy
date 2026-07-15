package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Context
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import com.fantasyidler.R
import com.fantasyidler.data.json.DailyQuestTemplate
import com.fantasyidler.data.json.QuestData
import com.fantasyidler.repository.DailyQuestWithProgress
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.QuestWithProgress
import com.fantasyidler.ui.viewmodel.QuestsViewModel
import com.fantasyidler.repository.WeeklyQuestWithProgress
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp

private val TAB_GROUPS = listOf("Timed", "Gathering", "Crafting", "Combat", "Special")

@Composable
private fun tabGroupLabel(group: String): String = when (group) {
    "Gathering" -> stringResource(R.string.label_gathering_skills)
    "Crafting"  -> stringResource(R.string.label_crafting_skills)
    "Combat"    -> stringResource(R.string.label_combat)
    "Special"   -> stringResource(R.string.label_special)
    "Timed"     -> stringResource(R.string.label_timed)
    "Daily"     -> stringResource(R.string.label_daily)
    "Weekly"    -> stringResource(R.string.label_weekly)
    else        -> group
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestsScreen(
    viewModel: QuestsViewModel = hiltViewModel(),
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
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_quests)) },
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
                            checked = state.hideCompleted,
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
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val pagerState = rememberPagerState(pageCount = { TAB_GROUPS.size })
        val scope = rememberCoroutineScope()

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ScrollableTabRow(selectedTabIndex = pagerState.currentPage, edgePadding = 0.dp) {
                TAB_GROUPS.forEachIndexed { index, group ->
                    val claimableInGroup = if (group == "Timed") {
                        state.dailyQuests.count { it.progress >= it.template.amount && !it.claimed } +
                        state.weeklyQuests.count { it.progress >= it.template.amount && !it.claimed }
                    } else {
                        (state.questsByGroup[group] ?: emptyList()).count { it.isClaimable }
                    }
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick  = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            val baseLabel = tabGroupLabel(group)
                            val label = if (claimableInGroup > 0) "$baseLabel ($claimableInGroup)" else baseLabel
                            Text(
                                text  = label,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                val currentGroup = TAB_GROUPS[page]
                if (currentGroup == "Timed") {
                    TimedQuestsContent(
                        dailyQuests         = state.dailyQuests,
                        weeklyQuests        = state.weeklyQuests,
                        nextDailyReset      = state.nextDailyReset,
                        nextWeeklyReset     = state.nextWeeklyReset,
                        hideCompleted       = state.hideCompleted,
                        weeklyBonusClaimed  = state.weeklyBonusClaimed,
                        onClaimDailyQuest   = { viewModel.claimDailyQuest(it) },
                        onClaimWeeklyQuest  = { viewModel.claimWeeklyQuest(it) },
                        onClaimWeeklyBonus  = { viewModel.claimWeeklyBonus() },
                    )
                } else {
                    val quests = state.questsByGroup[currentGroup] ?: emptyList()
                    if (quests.isEmpty()) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text  = stringResource(R.string.quests_none_in_category),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(quests, key = { it.quest.id }) { questWithProgress ->
                                QuestRow(
                                    questWithProgress = questWithProgress,
                                    onClaimReward     = { viewModel.claimReward(questWithProgress.quest.id) },
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Timed quests container (Daily + Weekly sub-tabs)
// ---------------------------------------------------------------------------

@Composable
private fun TimedQuestsContent(
    dailyQuests: List<DailyQuestWithProgress>,
    weeklyQuests: List<WeeklyQuestWithProgress>,
    nextDailyReset: Long,
    nextWeeklyReset: Long,
    hideCompleted: Boolean,
    weeklyBonusClaimed: Boolean = false,
    onClaimDailyQuest: (String) -> Unit,
    onClaimWeeklyQuest: (String) -> Unit,
    onClaimWeeklyBonus: () -> Unit,
) {
    val dailyLabel  = stringResource(R.string.label_daily)
    val weeklyLabel = stringResource(R.string.label_weekly)
    var selectedSubTab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedSubTab) {
            listOf(dailyLabel, weeklyLabel).forEachIndexed { index, title ->
                val claimable = when (index) {
                    0 -> dailyQuests.count { it.progress >= it.template.amount && !it.claimed }
                    1 -> weeklyQuests.count { it.progress >= it.template.amount && !it.claimed }
                    else -> 0
                }
                Tab(
                    selected = selectedSubTab == index,
                    onClick  = { selectedSubTab = index },
                    text = {
                        val label = if (claimable > 0) "$title ($claimable)" else title
                        Text(text = label, style = MaterialTheme.typography.labelMedium)
                    },
                )
            }
        }
        when (selectedSubTab) {
            0 -> DailyQuestsContent(
                quests        = dailyQuests,
                nextReset     = nextDailyReset,
                hideCompleted = hideCompleted,
                onClaimQuest  = onClaimDailyQuest,
            )
            1 -> WeeklyQuestsContent(
                quests             = weeklyQuests,
                nextReset          = nextWeeklyReset,
                hideCompleted      = hideCompleted,
                weeklyBonusClaimed = weeklyBonusClaimed,
                onClaimQuest       = onClaimWeeklyQuest,
                onClaimBonus       = onClaimWeeklyBonus,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Daily quests content
// ---------------------------------------------------------------------------

@Composable
private fun DailyQuestsContent(
    quests: List<DailyQuestWithProgress>,
    nextReset: Long,
    hideCompleted: Boolean = false,
    onClaimQuest: (String) -> Unit,
) {
    val visibleQuests = if (hideCompleted) quests.filter { !it.claimed } else quests
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                text     = stringResource(R.string.label_daily_info),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            HorizontalDivider()
        }
        if (visibleQuests.isEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
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
            items(visibleQuests, key = { it.template.id }) { q ->
                DailyQuestCard(quest = q, onClaim = { onClaimQuest(q.template.id) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        item {
            Text(
                text     = stringResource(R.string.label_daily_reset),
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Weekly quests content
// ---------------------------------------------------------------------------

@Composable
private fun WeeklyQuestsContent(
    quests: List<WeeklyQuestWithProgress>,
    nextReset: Long,
    hideCompleted: Boolean = false,
    weeklyBonusClaimed: Boolean = false,
    onClaimQuest: (String) -> Unit,
    onClaimBonus: () -> Unit,
) {
    val visibleQuests = if (hideCompleted) quests.filter { !it.claimed } else quests
    val allQuestsClaimed = quests.isNotEmpty() && quests.all { it.claimed }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(
                text     = stringResource(R.string.label_weekly_info),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            HorizontalDivider()
        }
        if (visibleQuests.isEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
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
            items(visibleQuests, key = { it.template.id }) { q ->
                WeeklyQuestCard(quest = q, onClaim = { onClaimQuest(q.template.id) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        if (allQuestsClaimed && !weeklyBonusClaimed) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(R.string.weekly_bonus_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = GoldPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onClaimBonus, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.weekly_bonus_claim))
                    }
                }
            }
        }
        item {
            Text(
                text     = stringResource(R.string.label_weekly_reset),
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

private fun buildDailyObjective(context: Context, template: DailyQuestTemplate): String {
    val verbResId = when (template.skill) {
        "mining"      -> R.string.daily_verb_mining
        "fishing"     -> R.string.daily_verb_fishing
        "woodcutting" -> R.string.daily_verb_woodcutting
        "smithing"    -> R.string.daily_verb_smithing
        "cooking"     -> R.string.daily_verb_cooking
        "combat"      -> R.string.daily_verb_combat
        else          -> return template.description
    }
    val verb = context.getString(verbResId)
    val item = GameStrings.itemName(context, template.target)
    return "$verb ${template.amount} $item."
}

private fun buildQuestObjective(context: Context, quest: QuestData): String {
    fun verb(skill: String): String {
        val id = context.resources.getIdentifier("daily_verb_$skill", "string", context.packageName)
        return if (id != 0) context.getString(id) else skill.replace('_', ' ')
    }
    return when (quest.type) {
        "gather", "gather_any" -> context.getString(
            R.string.quest_obj_gather,
            verb(quest.skill), quest.amount, GameStrings.itemName(context, quest.target),
        )
        "craft", "craft_any" -> context.getString(
            R.string.quest_obj_craft,
            verb(quest.skill), quest.amount, GameStrings.itemName(context, quest.target),
        )
        "prayer"     -> context.getString(R.string.quest_obj_prayer, quest.amount)
        "kill"       -> context.getString(R.string.quest_obj_kill, quest.amount)
        "kill_enemy" -> context.getString(R.string.quest_obj_kill_enemy, quest.amount, GameStrings.enemyName(context, quest.target))
        "dungeon"    -> if (quest.amount == 1)
            context.getString(R.string.quest_obj_dungeon_once, GameStrings.dungeonName(context, quest.target))
        else
            context.getString(R.string.quest_obj_dungeon, GameStrings.dungeonName(context, quest.target), quest.amount)
        "boss"       -> if (quest.amount == 1)
            context.getString(R.string.quest_obj_boss_once, GameStrings.bossName(context, quest.target))
        else
            context.getString(R.string.quest_obj_boss, GameStrings.bossName(context, quest.target), quest.amount)
        "dungeon_melee_only"  -> context.getString(R.string.quest_obj_dungeon_melee_only, GameStrings.dungeonName(context, quest.target))
        "dungeon_ranged_only" -> context.getString(R.string.quest_obj_dungeon_ranged_only, GameStrings.dungeonName(context, quest.target))
        "dungeon_magic_only"  -> context.getString(R.string.quest_obj_dungeon_magic_only, GameStrings.dungeonName(context, quest.target))
        "dungeon_no_food"     -> context.getString(R.string.quest_obj_dungeon_no_food, GameStrings.dungeonName(context, quest.target))
        "collect"             -> context.getString(R.string.quest_obj_collect, quest.amount, GameStrings.itemName(context, quest.target))
        "slayer_task"         -> context.getString(R.string.quest_obj_slayer_task, quest.amount)
        else                  -> quest.description
    }
}

@Composable
private fun DailyQuestCard(
    quest: DailyQuestWithProgress,
    onClaim: () -> Unit,
) {
    val context    = LocalContext.current
    val isComplete = quest.progress >= quest.template.amount
    val isClaimed  = quest.claimed
    val name      = GameStrings.questName(context, quest.template.id, quest.template.displayName)
    val objective = GameStrings.questObjective(context, quest.template.id)
        .takeIf { it.isNotBlank() }
        ?: buildDailyObjective(context, quest.template)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text       = name,
            style      = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text  = objective,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!isClaimed) {
            Spacer(Modifier.height(8.dp))
            val fraction = (quest.progress.toFloat() / quest.template.amount.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color    = GoldPrimary,
            )
            Spacer(Modifier.height(4.dp))
            val displayProgress = quest.progress.coerceAtMost(quest.template.amount)
            Text(
                text  = "$displayProgress / ${quest.template.amount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isClaimed) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.label_completed),
                    tint               = Color(0xFF4CAF50),
                    modifier           = Modifier
                        .height(18.dp)
                        .width(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text       = stringResource(R.string.label_completed),
                    style      = MaterialTheme.typography.labelMedium,
                    color      = Color(0xFF4CAF50),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else if (isComplete) {
            Spacer(Modifier.height(6.dp))
            Text(
                text  = stringResource(R.string.label_daily_reward),
                style = MaterialTheme.typography.labelSmall,
                color = GoldPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick  = onClaim,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.label_claim_reward))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Quest row
// ---------------------------------------------------------------------------

@Composable
private fun QuestRow(
    questWithProgress: QuestWithProgress,
    onClaimReward: () -> Unit,
) {
    val context = LocalContext.current
    val quest   = questWithProgress.quest
    val dimColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    val notStarted = questWithProgress.progress == 0 && !questWithProgress.completed
    val titleColor = if (notStarted) dimColor else MaterialTheme.colorScheme.onSurface
    val descColor  = if (notStarted) dimColor else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // Title
        val displayName = GameStrings.questName(context, quest.id, quest.name)
        Text(
            text       = displayName,
            style      = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color      = titleColor,
        )

        // Description / objective
        val objective = GameStrings.questObjective(context, quest.id).takeIf { it.isNotBlank() }
            ?: buildQuestObjective(context, quest)
        if (objective.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text  = objective,
                style = MaterialTheme.typography.bodySmall,
                color = descColor,
            )
        }

        // Progress bar + text (only when in-progress or claimable)
        if (!questWithProgress.completed && questWithProgress.progress > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress  = { questWithProgress.progressFraction },
                modifier  = Modifier.fillMaxWidth(),
                color     = GoldPrimary,
            )
            Spacer(Modifier.height(4.dp))
            val displayProgress = questWithProgress.progress.coerceAtMost(quest.amount)
            Text(
                text  = "$displayProgress / ${quest.amount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Completed state
        if (questWithProgress.completed) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector      = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.label_completed),
                    tint             = Color(0xFF4CAF50),
                    modifier         = Modifier
                        .height(18.dp)
                        .width(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text  = stringResource(R.string.label_completed),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Claimable state
        if (questWithProgress.isClaimable) {
            Spacer(Modifier.height(10.dp))
            val rewards = quest.rewards
            val rewardParts = buildList {
                if (rewards.xp > 0) add("+${rewards.xp.toLong().formatXp()} XP")
                if (rewards.coins > 0) add("${rewards.coins.toLong().formatCoins()} coins")
                rewards.items.forEach { (itemKey, qty) ->
                    add("${GameStrings.itemName(context, itemKey)} ×$qty")
                }
            }
            if (rewardParts.isNotEmpty()) {
                Text(
                    text  = rewardParts.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldPrimary,
                )
                Spacer(Modifier.height(6.dp))
            }
            Button(
                onClick  = onClaimReward,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.label_claim_reward))
            }
        }
    }
}

@Composable
private fun WeeklyQuestCard(
    quest: WeeklyQuestWithProgress,
    onClaim: () -> Unit,
) {
    val context    = LocalContext.current
    val isComplete = quest.progress >= quest.template.amount
    val isClaimed  = quest.claimed
    val name      = GameStrings.questName(context, quest.template.id, quest.template.displayName)
    val objective = GameStrings.questDesc(context, quest.template.id)
        .takeIf { it.isNotBlank() }
        ?: quest.template.description

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text       = name,
            style      = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text  = objective,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!isClaimed) {
            Spacer(Modifier.height(8.dp))
            val fraction = (quest.progress.toFloat() / quest.template.amount.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color    = GoldPrimary,
            )
            Spacer(Modifier.height(4.dp))
            val displayProgress = quest.progress.coerceAtMost(quest.template.amount)
            Text(
                text  = "$displayProgress / ${quest.template.amount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isClaimed) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.label_completed),
                    tint               = Color(0xFF4CAF50),
                    modifier           = Modifier
                        .height(18.dp)
                        .width(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text       = stringResource(R.string.label_completed),
                    style      = MaterialTheme.typography.labelMedium,
                    color      = Color(0xFF4CAF50),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else if (isComplete) {
            Spacer(Modifier.height(6.dp))
            Button(
                onClick  = onClaim,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.label_claim_reward))
            }
        }
    }
}
