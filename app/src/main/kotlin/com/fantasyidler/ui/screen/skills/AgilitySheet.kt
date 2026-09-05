package com.fantasyidler.ui.screen.skills


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.data.json.AgilityCourseData
import com.fantasyidler.data.model.Skills
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.simulator.XpTable
import com.fantasyidler.util.GameStrings
import com.fantasyidler.ui.viewmodel.QuestIndicator


@Composable
internal fun AgilitySheet(
    guildDailyButton: (@Composable () -> Unit)? = null,
    courses: Map<String, AgilityCourseData>,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    currentXp: Long = 0L,
    efficiency: Float = 1f,
    petBoostPct: Int = 0,
    xpBonusMult: Float = 1f,
    activeQuests: Map<String, List<QuestIndicator>> = emptyMap(),
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val currentAgilityLevel = XpTable.levelForXp(currentXp)
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            text     = stringResource(R.string.label_choose_activity),
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            text     = stringResource(R.string.skill_agility_desc),
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
        Column(Modifier.verticalScroll(scrollState)) {
            courses.entries
                .sortedBy { it.value.levelRequired }
                .forEach { (key, course) ->
                    val xpGain = (SkillSimulator.estimateAgilityXp(course.xpPerSuccess, course.levelRequired, currentAgilityLevel, efficiency) * (1 + petBoostPct / 100f) * xpBonusMult).toLong()
                    ActivityRow(
                        name = GameStrings.agilityCourse(context, key),
                        detail = context.getString(
                            R.string.skills_agility_course_detail,
                            course.levelRequired,
                            course.xpPerSuccess
                        ),
                        projectedLabel = projectedXpLabel(currentXp, xpGain),
                        isStarting = isStarting,
                        hasActiveSession = hasActiveSession,
                        isQueueFull = isQueueFull,
                        questIndicators = activeQuests["${Skills.AGILITY}:$key"] ?: emptyList(),
                        onClick = { selectedKey = key },
                    )
                }
        }
    }
    selectedKey?.let { key ->
        val course = courses[key] ?: return@let
        ActivityDetailDialog(
            name = GameStrings.agilityCourse(context, key),
            detail = context.getString(
                R.string.skills_agility_course_detail,
                course.levelRequired,
                course.xpPerSuccess
            ),
            description = GameStrings.agilityCourseDesc(context, key),
            hasActiveSession = hasActiveSession,
            isQueueFull = isQueueFull,
            onConfirm = { onSelect(key) },
            onDismiss = { selectedKey = null },
        )
    }
}

// ---------------------------------------------------------------------------
// Firemaking sheet
// ---------------------------------------------------------------------------

