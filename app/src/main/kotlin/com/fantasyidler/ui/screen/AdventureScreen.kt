package com.fantasyidler.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.ui.components.DungeonCard
import com.fantasyidler.ui.components.EntityIcon
import com.fantasyidler.ui.components.PrimaryButton
import com.fantasyidler.ui.components.SectionHeader
import com.fantasyidler.ui.motion.pressScale
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.CombatViewModel
import com.fantasyidler.ui.viewmodel.HomeViewModel
import com.fantasyidler.ui.viewmodel.QuestWithProgress
import com.fantasyidler.ui.viewmodel.QuestsViewModel
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.util.GameStrings

/**
 * The Adventure hub. Single-column scrolling layout per
 * docs/UI_REDESIGN_PROPOSAL.md §4.3. Tells the player what to do next instead
 * of what they already have. Replaces the old Home dashboard.
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (combatState.isLoading || questsState.isLoading) {
            Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val topQuest = pickTopQuest(questsState.questsByGroup.values.flatten())
        val recommendedDungeon = pickRecommendedDungeon(
            combatLevel = combatLevelFrom(combatState.skillLevels),
            dungeons    = combatVm.dungeonList,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text       = stringResource(R.string.nav_adventure),
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            SectionHeader(stringResource(R.string.adv_continue_quest))
            ContinueQuestCard(quest = topQuest, onTap = onOpenQuests)

            SectionHeader(stringResource(R.string.adv_recommended_dungeon))
            if (recommendedDungeon != null) {
                val rating = combatState.dungeonSurvivalRatings[recommendedDungeon.name]
                RecommendedDungeonCard(
                    dungeon       = recommendedDungeon,
                    survivalRating = rating,
                    onEnter       = { onEnterDungeon(recommendedDungeon) },
                )
            }

            SectionHeader(stringResource(R.string.adv_more))
            AdventureRow(
                icon  = Icons.Filled.MenuBook,
                title = stringResource(R.string.adv_daily_quests),
                badge = questsState.claimableCount.takeIf { it > 0 }?.toString(),
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

            if (globalState.sessionQueue.isNotEmpty()) {
                SectionHeader(stringResource(R.string.home_up_next, globalState.sessionQueue.size))
                QueueCard(
                    queue    = globalState.sessionQueue,
                    onRemove = globalVm::removeFromQueue,
                )
            }
        }
    }
}

@Composable
private fun QueueCard(
    queue: List<QueuedAction>,
    onRemove: (Int) -> Unit,
) {
    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            queue.forEachIndexed { index, action ->
                if (index > 0) HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                )
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val emoji = GameStrings.skillEmoji(action.skillName)
                    val activityLabel = action.activityKey
                        .replace('_', ' ')
                        .replaceFirstChar { it.uppercase() }
                        .takeIf { action.activityKey.isNotEmpty() }
                    Text(
                        text       = "$emoji ${action.skillDisplayName}${if (activityLabel != null) " — $activityLabel" else ""}",
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier   = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(
                            imageVector        = Icons.Filled.Close,
                            contentDescription = "Remove from queue",
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        border   = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.22f)),
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onTap,
            ),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (quest == null) {
                Text(
                    text  = stringResource(R.string.adv_no_active_quest),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text       = quest.quest.name,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = quest.quest.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { quest.progressFraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "${quest.progress} / ${quest.quest.amount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        border   = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            DungeonCard(
                dungeon        = dungeon,
                unlocked       = true,
                onTap          = onEnter,
                survivalRating = survivalRating,
                modifier       = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text     = stringResource(R.string.adv_enter_dungeon),
                onClick  = onEnter,
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
    val interactionSource = remember { MutableInteractionSource() }
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Surface(
        shape    = RoundedCornerShape(14.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onTap,
            ),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Gold-tinted icon disk — matches the GoldPrimary accent used by
            // section headers and badges. Reads as "leather-bound game UI"
            // rather than the previous flat-emoji look.
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = GoldPrimary.copy(alpha = if (enabled) 0.18f else 0.08f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector        = icon,
                        contentDescription = null,
                        tint               = if (enabled) GoldPrimary else dim,
                        modifier           = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text       = title,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color      = if (enabled) MaterialTheme.colorScheme.onSurface else dim,
                modifier   = Modifier.weight(1f),
            )
            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GoldPrimary.copy(alpha = 0.22f),
                ) {
                    Text(
                        text       = badge,
                        modifier   = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color      = GoldPrimary,
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint               = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else dim,
                modifier           = Modifier.size(20.dp),
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
 * level. Ties break toward easier (lower-level) dungeon.
 */
private fun pickRecommendedDungeon(
    combatLevel: Int,
    dungeons: List<DungeonData>,
): DungeonData? = dungeons.minByOrNull {
    val diff = (it.recommendedLevel - combatLevel).let { d -> if (d == 0) 0 else d }
    Math.abs(diff) * 2 + maxOf(0, it.recommendedLevel - combatLevel)
}
