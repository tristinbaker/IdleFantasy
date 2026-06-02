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
import com.fantasyidler.R
import com.fantasyidler.repository.DailyQuestWithProgress
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.QuestWithProgress
import com.fantasyidler.ui.viewmodel.QuestsViewModel
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp

private val TAB_GROUPS = listOf("Daily", "Gathering", "Crafting", "Combat", "Special")

@Composable
private fun tabGroupLabel(group: String): String = when (group) {
    "Gathering" -> stringResource(R.string.label_gathering_skills)
    "Crafting"  -> stringResource(R.string.label_crafting_skills)
    "Combat"    -> stringResource(R.string.label_combat)
    "Special"   -> stringResource(R.string.label_special)
    "Daily"     -> stringResource(R.string.label_daily)
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
            snackbarHostState.showSnackbar(it)
            viewModel.snackbarConsumed()
        }
    }

    Scaffold(
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
                    val claimableInGroup = if (group == "Daily") {
                        state.dailyQuests.count { it.progress >= it.template.amount && !it.claimed }
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
                if (currentGroup == "Daily") {
                    DailyQuestsContent(
                        quests        = state.dailyQuests,
                        nextReset     = state.nextDailyReset,
                        hideCompleted = state.hideCompleted,
                        onClaimQuest  = { viewModel.claimDailyQuest(it) },
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

@Composable
private fun DailyQuestCard(
    quest: DailyQuestWithProgress,
    onClaim: () -> Unit,
) {
    val isComplete = quest.progress >= quest.template.amount
    val isClaimed  = quest.claimed

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text       = quest.template.displayName,
            style      = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text  = quest.template.description,
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
            ?: quest.description
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
