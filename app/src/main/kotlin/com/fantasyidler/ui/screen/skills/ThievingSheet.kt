package com.fantasyidler.ui.screen.skills

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.data.json.ThievingNpcData
import com.fantasyidler.data.model.Skills
import com.fantasyidler.util.GameStrings
import com.fantasyidler.ui.viewmodel.QuestIndicator


@Composable
internal fun ThievingSheet(
    guildDailyButton: (@Composable () -> Unit)? = null,
    npcs: Map<String, ThievingNpcData>,
    thievingLevel: Int,
    currentXp: Long,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    context: Context,
    efficiency: Float = 1f,
    petBoostPct: Int = 0,
    xpBonusMult: Float = 1f,
    successBonus: Double = 0.0,
    activeQuests: Map<String, List<QuestIndicator>> = emptyMap(),
    onSelect: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            text     = stringResource(R.string.label_choose_activity),
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            text     = stringResource(R.string.skill_thieving_desc),
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        )
        guildDailyButton?.invoke()
        if (sessionDurationMs > 0) {
            Text(
                text     = stringResource(R.string.skills_session_duration, sessionDurationMs / 60_000),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        HorizontalDivider()
        Column(Modifier.verticalScroll(scrollState, flingBehavior = rememberTapFriendlyFlingBehavior())) {
            npcs.values
                .sortedBy { it.levelRequired }
                .forEach { npc ->
                    val chance = (0.40 + (thievingLevel - npc.levelRequired) * 0.02 * efficiency + successBonus).coerceIn(0.10, 0.98)
                    val successChance = (chance * 100).toInt()
                    val petBoostedXp = if (petBoostPct > 0) (npc.baseXp * (1.0 + petBoostPct / 100.0)).toInt() else npc.baseXp
                    val xpGain = (60.0 * (chance / (2.0 - chance)) * petBoostedXp * xpBonusMult).toLong()
                    ActivityRow(
                        name             = GameStrings.thievingNpcName(context, npc.key),
                        detail           = stringResource(
                            R.string.thieving_npc_detail,
                            npc.levelRequired,
                            npc.baseXp,
                            successChance,
                        ),
                        projectedLabel   = projectedXpLabel(currentXp, xpGain),
                        isStarting       = isStarting,
                        hasActiveSession = hasActiveSession,
                        isQueueFull      = isQueueFull,
                        questIndicators  = activeQuests["${Skills.THIEVING}:${npc.key}"] ?: emptyList(),
                        onClick          = { onSelect(npc.key) },
                    )
                }
        }
    }
}
