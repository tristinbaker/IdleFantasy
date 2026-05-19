package com.fantasyidler.ui.screen

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemySpawn
import com.fantasyidler.data.json.QuestData
import com.fantasyidler.data.json.QuestRewards
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.ui.components.DungeonCard
import com.fantasyidler.ui.components.SectionHeader
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant
import com.fantasyidler.ui.components.foundation.ChunkyCard
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.ui.viewmodel.CombatViewModel
import com.fantasyidler.ui.viewmodel.HomeViewModel
import com.fantasyidler.ui.viewmodel.QuestWithProgress
import com.fantasyidler.ui.viewmodel.QuestsViewModel
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.util.GameStrings

/**
 * The Adventure hub. Single-column scrolling layout per docs/UI_REDESIGN_PROPOSAL.md §4.3.
 * Tells the player what to do next instead of what they already have. Every card is a
 * [ChunkyCard]; every row meets the 48dp tap-target threshold; every literal style value
 * is sourced from [LocalFantasyTokens].
 */
@Composable
fun AdventureScreen(
    onOpenQuests: () -> Unit = {},
    onOpenAchievements: () -> Unit = {},
    onEnterDungeon: (DungeonData) -> Unit = {},
    questsVm: QuestsViewModel = hiltViewModel(),
    combatVm: CombatViewModel = hiltViewModel(),
    globalVm: HomeViewModel = hiltViewModel(),
) {
    val questsState by questsVm.uiState.collectAsState()
    val combatState by combatVm.uiState.collectAsState()
    val globalState by globalVm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val tokens = LocalFantasyTokens.current

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (combatState.isLoading || questsState.isLoading) {
            Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = tokens.colors.primary)
                    Spacer(Modifier.height(tokens.spacing.l))
                    Text(
                        text  = stringResource(R.string.adv_loading),
                        style = tokens.typography.bodyMedium,
                        color = tokens.colors.onSurfaceMuted,
                    )
                }
            }
            return@Scaffold
        }

        val topQuest = pickTopQuest(questsState.questsByGroup.values.flatten())
        val recommendedDungeon = pickRecommendedDungeon(
            combatLevel = combatLevelFrom(combatState.skillLevels),
            dungeons    = combatVm.dungeonList,
        )

        AdventureBody(
            padding             = padding,
            topQuest            = topQuest,
            recommendedDungeon  = recommendedDungeon,
            survivalRating      = recommendedDungeon?.let { combatState.dungeonSurvivalRatings[it.name] },
            claimableQuestCount = questsState.claimableCount,
            sessionQueue        = globalState.sessionQueue,
            onOpenQuests        = onOpenQuests,
            onOpenAchievements  = onOpenAchievements,
            onEnterDungeon      = onEnterDungeon,
            onRemoveFromQueue   = globalVm::removeFromQueue,
        )
    }
}

@Composable
private fun AdventureBody(
    padding: androidx.compose.foundation.layout.PaddingValues,
    topQuest: QuestWithProgress?,
    recommendedDungeon: DungeonData?,
    survivalRating: CombatSimulator.SurvivalRating?,
    claimableQuestCount: Int,
    sessionQueue: List<QueuedAction>,
    onOpenQuests: () -> Unit,
    onOpenAchievements: () -> Unit,
    onEnterDungeon: (DungeonData) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(tokens.spacing.l),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.l),
    ) {
        Text(
            text       = stringResource(R.string.nav_adventure),
            style      = tokens.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color      = tokens.colors.onSurface,
        )

        SectionHeader(stringResource(R.string.adv_continue_quest))
        ContinueQuestCard(quest = topQuest, onTap = onOpenQuests)

        SectionHeader(stringResource(R.string.adv_recommended_dungeon))
        if (recommendedDungeon != null) {
            RecommendedDungeonCard(
                dungeon        = recommendedDungeon,
                survivalRating = survivalRating,
                onEnter        = { onEnterDungeon(recommendedDungeon) },
            )
        } else {
            EmptyRecommendedDungeonCard()
        }

        SectionHeader(stringResource(R.string.adv_more))
        AdventureRow(
            icon  = Icons.Filled.MenuBook,
            title = stringResource(R.string.adv_daily_quests),
            badge = claimableQuestCount.takeIf { it > 0 }?.toString(),
            onTap = onOpenQuests,
        )
        AdventureRow(
            icon  = Icons.Filled.EmojiEvents,
            title = stringResource(R.string.adv_achievements),
            badge = null,
            onTap = onOpenAchievements,
        )
        AdventureRow(
            icon    = Icons.Filled.Storefront,
            title   = stringResource(R.string.adv_marketplace_soon),
            badge   = null,
            onTap   = {},
            enabled = false,
        )

        if (sessionQueue.isNotEmpty()) {
            SectionHeader(stringResource(R.string.home_up_next, sessionQueue.size))
            QueueCard(queue = sessionQueue, onRemove = onRemoveFromQueue)
        }
    }
}

@Composable
private fun QueueCard(
    queue: List<QueuedAction>,
    onRemove: (Int) -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    val cardCd = stringResource(R.string.cd_queue_card)
    ChunkyCard(
        modifier       = Modifier.semantics { contentDescription = cardCd },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(tokens.spacing.l),
    ) {
        Column {
            queue.forEachIndexed { index, action ->
                if (index > 0) HorizontalDivider(
                    modifier = Modifier.padding(vertical = tokens.spacing.s),
                    color    = tokens.colors.border.copy(alpha = 0.18f),
                )
                val emoji = GameStrings.skillEmoji(action.skillName)
                val activityLabel = action.activityKey
                    .replace('_', ' ')
                    .replaceFirstChar { it.uppercase() }
                    .takeIf { action.activityKey.isNotEmpty() }
                val rowLabel = "$emoji ${action.skillDisplayName}${if (activityLabel != null) " — $activityLabel" else ""}"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text       = rowLabel,
                        style      = tokens.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color      = tokens.colors.onSurface,
                        modifier   = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick  = { onRemove(index) },
                        modifier = Modifier.size(tokens.spacing.xxl + tokens.spacing.l),
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_queue_remove, rowLabel),
                            tint               = tokens.colors.onSurfaceMuted,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

@Composable
private fun ContinueQuestCard(
    quest: QuestWithProgress?,
    onTap: () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    ChunkyCard(
        onClick   = onTap,
        highlight = quest?.isClaimable == true,
    ) {
        Column {
            if (quest == null) {
                Text(
                    text  = stringResource(R.string.adv_no_active_quest),
                    style = tokens.typography.bodyMedium,
                    color = tokens.colors.onSurfaceMuted,
                )
            } else {
                Text(
                    text       = quest.quest.name,
                    style      = tokens.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = tokens.colors.onSurface,
                )
                Spacer(Modifier.height(tokens.spacing.s))
                Text(
                    text  = quest.quest.description,
                    style = tokens.typography.bodyMedium,
                    color = tokens.colors.onSurfaceMuted,
                )
                Spacer(Modifier.height(tokens.spacing.m + tokens.spacing.xs))
                LinearProgressIndicator(
                    progress = { quest.progressFraction },
                    modifier = Modifier.fillMaxWidth().height(tokens.spacing.m),
                    color    = tokens.colors.primary,
                )
                Spacer(Modifier.height(tokens.spacing.s))
                Text(
                    text  = "${quest.progress} / ${quest.quest.amount}",
                    style = tokens.typography.labelSmall,
                    color = tokens.colors.onSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun RecommendedDungeonCard(
    dungeon: DungeonData,
    survivalRating: CombatSimulator.SurvivalRating?,
    onEnter: () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    ChunkyCard(
        highlight      = true,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(tokens.spacing.l),
    ) {
        Column {
            DungeonCard(
                dungeon        = dungeon,
                unlocked       = true,
                onTap          = onEnter,
                survivalRating = survivalRating,
                modifier       = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(tokens.spacing.m))
            ChunkyButton(
                text     = stringResource(R.string.adv_enter_dungeon),
                onClick  = onEnter,
                variant  = ChunkyButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EmptyRecommendedDungeonCard() {
    val tokens = LocalFantasyTokens.current
    ChunkyCard {
        Text(
            text  = stringResource(R.string.adv_no_recommended_dungeon),
            style = tokens.typography.bodyMedium,
            color = tokens.colors.onSurfaceMuted,
        )
    }
}

@Composable
private fun AdventureRow(
    icon: ImageVector,
    title: String,
    badge: String?,
    onTap: () -> Unit,
    enabled: Boolean = true,
) {
    val tokens = LocalFantasyTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val dim = tokens.colors.onSurface.copy(alpha = 0.38f)
    Surface(
        shape    = tokens.shapes.card,
        color    = tokens.colors.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.l)
            .clickable(
                interactionSource = interactionSource,
                indication        = LocalIndication.current,
                enabled           = enabled,
                onClick           = onTap,
            ),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m + tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape    = tokens.shapes.chip,
                color    = tokens.colors.primary.copy(alpha = if (enabled) 0.18f else 0.08f),
                modifier = Modifier.size(tokens.spacing.xxl + tokens.spacing.s),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector        = icon,
                        contentDescription = null,
                        tint               = if (enabled) tokens.colors.primary else dim,
                        modifier           = Modifier.size(tokens.spacing.l + tokens.spacing.s),
                    )
                }
            }
            Spacer(Modifier.width(tokens.spacing.m + tokens.spacing.s))
            Text(
                text       = title,
                style      = tokens.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color      = if (enabled) tokens.colors.onSurface else dim,
                modifier   = Modifier.weight(1f),
            )
            if (badge != null) {
                Surface(
                    shape = tokens.shapes.chip,
                    color = tokens.colors.primary.copy(alpha = 0.22f),
                ) {
                    Text(
                        text       = badge,
                        modifier   = Modifier.padding(horizontal = tokens.spacing.m + tokens.spacing.xs, vertical = tokens.spacing.xs),
                        style      = tokens.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color      = tokens.colors.primary,
                    )
                }
                Spacer(Modifier.width(tokens.spacing.m))
            }
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint               = if (enabled) tokens.colors.onSurfaceMuted else dim,
                modifier           = Modifier.size(tokens.spacing.l + tokens.spacing.s),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Selection helpers (pure)
// ---------------------------------------------------------------------------

/**
 * Pick the quest to show in the "Continue your quest" slot.
 * Priority: claimable > in-progress with most progress > first uncompleted.
 */
private fun pickTopQuest(quests: List<QuestWithProgress>): QuestWithProgress? {
    val claimable = quests.firstOrNull { it.isClaimable }
    if (claimable != null) return claimable
    val inProgress = quests
        .filter { !it.completed && it.prereqCompleted && it.progress > 0 }
        .maxByOrNull { it.progressFraction }
    if (inProgress != null) return inProgress
    return quests.firstOrNull { !it.completed && it.prereqCompleted }
}

/**
 * Pick the dungeon whose recommended_level is closest to the player's combat
 * level. Ties break toward the easier (lower-level) dungeon.
 */
private fun pickRecommendedDungeon(
    combatLevel: Int,
    dungeons: List<DungeonData>,
): DungeonData? = dungeons.minByOrNull {
    val diff = (it.recommendedLevel - combatLevel).let { d -> if (d == 0) 0 else d }
    kotlin.math.abs(diff) * 2 + maxOf(0, it.recommendedLevel - combatLevel)
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private fun sampleQuest(): QuestWithProgress = QuestWithProgress(
    quest = QuestData(
        id = "q_iron_ore", name = "Iron Resolve", skill = "mining", tier = 2,
        type = "gather", target = "iron_ore", amount = 50,
        description = "Mine 50 iron ore for the smiths' guild.",
        rewards = QuestRewards(coins = 100, xp = 250),
    ),
    progress = 12, completed = false, prereqCompleted = true,
)

private fun sampleDungeon(): DungeonData = DungeonData(
    name = "spider_cave", displayName = "Spider Cave",
    description = "A cobwebbed pit. Bring antitoxin.",
    recommendedLevel = 18, encounterRate = 1.0,
    enemySpawns = listOf(EnemySpawn(enemy = "giant_spider", weight = 1)),
)

@PreviewLightDark
@Composable
private fun PreviewAdventureBody() {
    FantasyPreviewSurface {
        AdventureBody(
            padding             = androidx.compose.foundation.layout.PaddingValues(),
            topQuest            = sampleQuest(),
            recommendedDungeon  = sampleDungeon(),
            survivalRating      = CombatSimulator.SurvivalRating.LIKELY,
            claimableQuestCount = 2,
            sessionQueue        = emptyList(),
            onOpenQuests        = {},
            onOpenAchievements  = {},
            onEnterDungeon      = {},
            onRemoveFromQueue   = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewAdventureBodyEmpty() {
    FantasyPreviewSurface {
        AdventureBody(
            padding             = androidx.compose.foundation.layout.PaddingValues(),
            topQuest            = null,
            recommendedDungeon  = null,
            survivalRating      = null,
            claimableQuestCount = 0,
            sessionQueue        = emptyList(),
            onOpenQuests        = {},
            onOpenAchievements  = {},
            onEnterDungeon      = {},
            onRemoveFromQueue   = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewAdventureLoading() {
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
                    text  = stringResource(R.string.adv_loading),
                    style = tokens.typography.bodyMedium,
                    color = tokens.colors.onSurfaceMuted,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewAdventureQueueCard() {
    FantasyPreviewSurface {
        QueueCard(
            queue = listOf(
                QueuedAction(skillName = "mining", activityKey = "copper_ore", skillDisplayName = "Mining"),
                QueuedAction(skillName = "fishing", activityKey = "raw_shrimp", skillDisplayName = "Fishing"),
            ),
            onRemove = {},
        )
    }
}
