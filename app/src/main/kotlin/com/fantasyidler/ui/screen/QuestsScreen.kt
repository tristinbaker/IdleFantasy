package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.json.QuestData
import com.fantasyidler.data.json.QuestRewards
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant
import com.fantasyidler.ui.components.foundation.ChunkyCard
import com.fantasyidler.ui.components.foundation.ClaimBadge
import com.fantasyidler.ui.components.foundation.ClaimStamp
import com.fantasyidler.ui.components.foundation.IconDisk
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.ui.viewmodel.QuestWithProgress
import com.fantasyidler.ui.viewmodel.QuestsViewModel
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp

/**
 * Canonical category-group keys used by [QuestsViewModel.questsByGroup]. The
 * display labels for the tab strip live in the `quest_category_titles`
 * string-array; this list is the parallel **key** sequence the screen uses
 * to index back into the map. Adding or reordering a category means editing
 * both lists in lock-step.
 */
private val QUEST_GROUP_KEYS: List<String> = listOf(
    "Gathering",
    "Crafting",
    "Combat",
    "Special",
)

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
    val tokens = LocalFantasyTokens.current

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
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = tokens.colors.primary)
                    Spacer(Modifier.height(tokens.spacing.l))
                    Text(
                        text  = stringResource(R.string.quests_loading),
                        style = tokens.typography.bodyMedium,
                        color = tokens.colors.onSurfaceMuted,
                    )
                }
            }
            return@Scaffold
        }

        var selectedTab by remember { mutableIntStateOf(0) }
        val tabTitles   = stringArrayResource(R.array.quest_category_titles)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            QuestsTabRow(
                tabTitles    = tabTitles,
                selectedTab  = selectedTab,
                onTabSelect  = { selectedTab = it },
                claimableInGroup = { idx ->
                    val key = QUEST_GROUP_KEYS.getOrNull(idx) ?: return@QuestsTabRow 0
                    state.questsByGroup[key]?.count { it.isClaimable } ?: 0
                },
            )

            val currentGroup = QUEST_GROUP_KEYS.getOrNull(selectedTab) ?: QUEST_GROUP_KEYS.first()
            val quests = state.questsByGroup[currentGroup] ?: emptyList()

            QuestsList(
                quests       = quests,
                groupEmoji   = groupEmoji(currentGroup),
                onClaim      = viewModel::claimReward,
            )
        }
    }
}

@Composable
private fun QuestsTabRow(
    tabTitles: Array<String>,
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    claimableInGroup: (Int) -> Int,
) {
    val tokens = LocalFantasyTokens.current
    TabRow(selectedTabIndex = selectedTab) {
        tabTitles.forEachIndexed { index, baseLabel ->
            val claimable = claimableInGroup(index)
            val displayLabel = if (claimable > 0)
                stringResource(R.string.quests_tab_with_count, baseLabel, claimable)
            else baseLabel
            val tabCd = stringResource(R.string.cd_quest_category, baseLabel)
            Tab(
                selected = selectedTab == index,
                onClick  = { onTabSelect(index) },
                modifier = Modifier
                    .defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.l)
                    .semantics { contentDescription = tabCd },
                text = {
                    Text(
                        text  = displayLabel,
                        style = tokens.typography.labelSmall,
                    )
                },
            )
        }
    }
}

@Composable
private fun QuestsList(
    quests: List<QuestWithProgress>,
    groupEmoji: String,
    onClaim: (String) -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    if (quests.isEmpty()) {
        Box(
            modifier         = Modifier
                .fillMaxSize()
                .padding(tokens.spacing.xxl),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = stringResource(R.string.quests_none_in_category),
                style = tokens.typography.bodyLarge,
                color = tokens.colors.onSurfaceMuted,
            )
        }
        return
    }
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(tokens.spacing.l),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.m + tokens.spacing.xs),
    ) {
        items(quests, key = { it.quest.id }) { q ->
            QuestCard(
                questWithProgress = q,
                groupEmoji        = groupEmoji,
                onClaimReward     = { onClaim(q.quest.id) },
            )
        }
    }
}

@Composable
private fun QuestCard(
    questWithProgress: QuestWithProgress,
    groupEmoji: String,
    onClaimReward: () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    val context = LocalContext.current
    val quest   = questWithProgress.quest
    val notStarted = questWithProgress.progress == 0 && !questWithProgress.completed

    ChunkyCard(highlight = questWithProgress.isClaimable) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconDisk(emoji = groupEmoji, size = tokens.spacing.xxl + tokens.spacing.m)
                Spacer(Modifier.width(tokens.spacing.m + tokens.spacing.s))
                Column(modifier = Modifier.weight(1f)) {
                    val displayName = GameStrings.questName(context, quest.id)
                        .takeIf { it.isNotBlank() } ?: quest.name
                    Text(
                        text       = displayName,
                        style      = tokens.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color      = if (notStarted) tokens.colors.onSurface.copy(alpha = 0.6f)
                                     else tokens.colors.onSurface,
                    )
                    if (quest.description.isNotBlank()) {
                        Spacer(Modifier.height(tokens.spacing.xs))
                        Text(
                            text  = quest.description,
                            style = tokens.typography.bodyMedium,
                            color = tokens.colors.onSurfaceMuted,
                        )
                    }
                }
                Spacer(Modifier.width(tokens.spacing.m))
                when {
                    questWithProgress.isClaimable -> ClaimBadge(text = "Claim")
                    questWithProgress.completed   -> ClaimStamp(text = stringResource(R.string.label_completed))
                    else                          -> {}
                }
            }

            if (!questWithProgress.completed && questWithProgress.progress > 0) {
                Spacer(Modifier.height(tokens.spacing.m + tokens.spacing.s))
                LinearProgressIndicator(
                    progress = { questWithProgress.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tokens.spacing.m),
                    color    = tokens.colors.primary,
                )
                Spacer(Modifier.height(tokens.spacing.s))
                val displayProgress = questWithProgress.progress.coerceAtMost(quest.amount)
                Text(
                    text  = stringResource(R.string.quests_progress_count, displayProgress, quest.amount),
                    style = tokens.typography.labelSmall,
                    color = tokens.colors.onSurfaceMuted,
                )
            }

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
                    Spacer(Modifier.height(tokens.spacing.m + tokens.spacing.xs))
                    Text(
                        text       = rewardParts.joinToString(" · "),
                        style      = tokens.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = tokens.colors.primary,
                    )
                }
                Spacer(Modifier.height(tokens.spacing.m + tokens.spacing.xs))
                ChunkyButton(
                    text     = stringResource(R.string.label_claim_reward),
                    onClick  = onClaimReward,
                    variant  = ChunkyButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private fun sampleQuestInProgress(): QuestWithProgress = QuestWithProgress(
    quest = QuestData(
        id = "q_iron_ore", name = "Iron Resolve", skill = "mining", tier = 2,
        type = "gather", target = "iron_ore", amount = 50,
        description = "Mine 50 iron ore for the smiths' guild.",
        rewards = QuestRewards(coins = 100, xp = 250),
    ),
    progress = 12, completed = false, prereqCompleted = true,
)

private fun sampleQuestClaimable(): QuestWithProgress = QuestWithProgress(
    quest = QuestData(
        id = "q_copper_ore", name = "Copper Caper", skill = "mining", tier = 1,
        type = "gather", target = "copper_ore", amount = 25,
        description = "Mine 25 copper ore.",
        rewards = QuestRewards(coins = 50, xp = 100, items = mapOf("bronze_pickaxe" to 1)),
    ),
    progress = 25, completed = false, prereqCompleted = true,
)

private fun sampleQuestCompleted(): QuestWithProgress = QuestWithProgress(
    quest = QuestData(
        id = "q_done", name = "Apprentice Miner", skill = "mining", tier = 1,
        type = "gather", target = "tin_ore", amount = 10,
        description = "Already done — left as a trophy.",
        rewards = QuestRewards(coins = 25, xp = 50),
    ),
    progress = 10, completed = true, prereqCompleted = true,
)

@PreviewLightDark
@Composable
private fun PreviewQuestCardInProgress() {
    FantasyPreviewSurface {
        QuestCard(
            questWithProgress = sampleQuestInProgress(),
            groupEmoji        = "⛏",
            onClaimReward     = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewQuestCardClaimable() {
    FantasyPreviewSurface {
        QuestCard(
            questWithProgress = sampleQuestClaimable(),
            groupEmoji        = "⛏",
            onClaimReward     = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewQuestCardCompleted() {
    FantasyPreviewSurface {
        QuestCard(
            questWithProgress = sampleQuestCompleted(),
            groupEmoji        = "⛏",
            onClaimReward     = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewQuestsListEmpty() {
    FantasyPreviewSurface {
        QuestsList(quests = emptyList(), groupEmoji = "⚔", onClaim = {})
    }
}

@PreviewLightDark
@Composable
private fun PreviewQuestsLoading() {
    FantasyPreviewSurface {
        Box(
            modifier         = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val tokens = LocalFantasyTokens.current
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = tokens.colors.primary)
                Spacer(Modifier.height(tokens.spacing.l))
                Text(
                    text  = stringResource(R.string.quests_loading),
                    style = tokens.typography.bodyMedium,
                    color = tokens.colors.onSurfaceMuted,
                )
            }
        }
    }
}
