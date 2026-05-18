package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.ui.components.ChunkyCard
import com.fantasyidler.ui.components.ClaimBadge
import com.fantasyidler.ui.components.ClaimStamp
import com.fantasyidler.ui.components.IconDisk
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.QuestWithProgress
import com.fantasyidler.ui.viewmodel.QuestsViewModel
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp

private val TAB_GROUPS = listOf("Gathering", "Crafting", "Combat", "Special")

@Composable
private fun tabGroupLabel(group: String): String = when (group) {
    "Gathering" -> stringResource(R.string.label_gathering_skills)
    "Crafting"  -> stringResource(R.string.label_crafting_skills)
    "Combat"    -> stringResource(R.string.label_combat)
    "Special"   -> stringResource(R.string.label_special)
    else        -> group
}

private fun groupEmoji(group: String): String = when (group) {
    "Gathering" -> "⛏"
    "Crafting"  -> "🔨"
    "Combat"    -> "⚔"
    "Special"   -> "⭐"
    else        -> "📜"
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

        var selectedTab by remember { mutableIntStateOf(0) }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                TAB_GROUPS.forEachIndexed { index, group ->
                    val groupQuests = state.questsByGroup[group] ?: emptyList()
                    val claimableInGroup = groupQuests.count { it.isClaimable }
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
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

            val currentGroup = TAB_GROUPS[selectedTab]
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
                LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(quests, key = { it.quest.id }) { q ->
                        QuestCard(
                            questWithProgress = q,
                            groupEmoji        = groupEmoji(currentGroup),
                            onClaimReward     = { viewModel.claimReward(q.quest.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestCard(
    questWithProgress: QuestWithProgress,
    groupEmoji: String,
    onClaimReward: () -> Unit,
) {
    val context = LocalContext.current
    val quest   = questWithProgress.quest
    val notStarted = questWithProgress.progress == 0 && !questWithProgress.completed

    ChunkyCard(highlight = questWithProgress.isClaimable) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconDisk(emoji = groupEmoji, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                val displayName = GameStrings.questName(context, quest.id)
                    .takeIf { it.isNotBlank() } ?: quest.name
                Text(
                    text       = displayName,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = if (notStarted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                 else MaterialTheme.colorScheme.onSurface,
                )
                if (quest.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text  = quest.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            when {
                questWithProgress.isClaimable -> ClaimBadge(text = "Claim")
                questWithProgress.completed   -> ClaimStamp(text = stringResource(R.string.label_completed))
                else -> {}
            }
        }

        // In-progress meter
        if (!questWithProgress.completed && questWithProgress.progress > 0) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { questWithProgress.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = GoldPrimary,
            )
            Spacer(Modifier.height(4.dp))
            val displayProgress = questWithProgress.progress.coerceAtMost(quest.amount)
            Text(
                text  = "$displayProgress / ${quest.amount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Reward summary + claim CTA
        if (questWithProgress.isClaimable) {
            val rewards = quest.rewards
            val rewardParts = buildList {
                if (rewards.xp > 0) add("+${rewards.xp.toLong().formatXp()} XP")
                if (rewards.coins > 0) add("${rewards.coins.toLong().formatCoins()} coins")
                rewards.items.forEach { (itemKey, qty) ->
                    add("${GameStrings.itemName(context, itemKey)} ×$qty")
                }
            }
            if (rewardParts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text  = rewardParts.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = GoldPrimary,
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick  = onClaimReward,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.label_claim_reward))
            }
        }
    }
}
