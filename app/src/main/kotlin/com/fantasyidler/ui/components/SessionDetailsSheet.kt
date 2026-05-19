package com.fantasyidler.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.fantasyidler.R
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant
import com.fantasyidler.ui.components.foundation.ChunkySheet
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.toCountdown
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

/**
 * Bottom-sheet opened by tapping the active-session pill in the top HUD.
 * Shows the in-progress session with a live countdown, plus a list of every
 * completed session waiting to be claimed (one Claim button per row, with a
 * Claim-all button at the top). Queued sessions appear read-only at the
 * bottom so the player knows what's coming next.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailsSheet(
    activeSession: SkillSession?,
    completedSessions: List<SkillSession>,
    sessionQueue: List<QueuedAction>,
    json: Json,
    onDismiss: () -> Unit,
    onClaim: (sessionId: String?) -> Unit,
) {
    val tokens   = LocalFantasyTokens.current
    val context  = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ChunkySheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.m),
        ) {
            Text(
                text       = stringResource(R.string.session_sheet_title),
                style      = tokens.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = tokens.colors.onSurface,
            )

            if (activeSession == null && completedSessions.isEmpty() && sessionQueue.isEmpty()) {
                Text(
                    text  = stringResource(R.string.session_sheet_no_session),
                    style = tokens.typography.bodyMedium,
                    color = tokens.colors.onSurfaceMuted,
                )
            }

            if (activeSession != null && !activeSession.completed) {
                ActiveSessionBlock(
                    session = activeSession,
                    json    = json,
                    context = context,
                )
            }

            if (completedSessions.isNotEmpty()) {
                SectionHeader(stringResource(R.string.session_sheet_section_completed))
                Text(
                    text  = stringResource(R.string.session_sheet_completed_count, completedSessions.size),
                    style = tokens.typography.labelSmall,
                    color = tokens.colors.onSurfaceMuted,
                )
                if (completedSessions.size > 1) {
                    ChunkyButton(
                        text     = stringResource(R.string.session_sheet_btn_claim_all),
                        onClick  = { onClaim(null) },
                        variant  = ChunkyButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                completedSessions.forEachIndexed { index, session ->
                    if (index > 0) HorizontalDivider(
                        modifier = Modifier.padding(vertical = tokens.spacing.xs),
                        color    = tokens.colors.border.copy(alpha = 0.18f),
                    )
                    CompletedSessionRow(
                        session = session,
                        json    = json,
                        context = context,
                        onClaim = { onClaim(session.sessionId) },
                    )
                }
            }

            if (sessionQueue.isNotEmpty()) {
                SectionHeader(stringResource(R.string.session_sheet_section_queue))
                sessionQueue.forEach { action ->
                    QueuedSessionRow(action = action)
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionBlock(
    session: SkillSession,
    json: Json,
    context: android.content.Context,
) {
    val tokens = LocalFantasyTokens.current
    val emoji = GameStrings.skillEmoji(session.skillName)
    val skillLabel = if (session.skillName == "combat") "Combat"
                     else GameStrings.skillName(context, session.skillName)
    val activityLabel = session.activityKey
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase() }
        .takeIf { session.activityKey.isNotEmpty() }

    val endsAt = session.endsAt
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (System.currentTimeMillis() < endsAt) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
        now = System.currentTimeMillis()
    }

    val total    = (endsAt - session.startedAt).coerceAtLeast(1L)
    val elapsed  = (now - session.startedAt).coerceIn(0L, total)
    val progress = elapsed.toFloat() / total.toFloat()

    SectionHeader(stringResource(R.string.session_sheet_section_current))
    Text(
        text       = "$emoji $skillLabel${if (activityLabel != null) " — $activityLabel" else ""}",
        style      = tokens.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color      = tokens.colors.onSurface,
    )
    Spacer(Modifier.height(tokens.spacing.xs))
    Text(
        text  = stringResource(R.string.session_sheet_remaining, endsAt.toCountdown()),
        style = tokens.typography.bodyMedium,
        color = tokens.colors.onSurfaceMuted,
    )
    Spacer(Modifier.height(tokens.spacing.s))
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(tokens.spacing.m),
        color    = tokens.colors.primary,
    )
    Spacer(Modifier.height(tokens.spacing.xs))
    SessionRewardSummary(session = session, json = json)
}

@Composable
private fun CompletedSessionRow(
    session: SkillSession,
    json: Json,
    context: android.content.Context,
    onClaim: () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    val emoji = GameStrings.skillEmoji(session.skillName)
    val skillLabel = if (session.skillName == "combat") "Combat"
                     else GameStrings.skillName(context, session.skillName)
    val activityLabel = session.activityKey
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase() }
        .takeIf { session.activityKey.isNotEmpty() }

    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs),
        verticalAlignment     = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = "$emoji $skillLabel${if (activityLabel != null) " — $activityLabel" else ""}",
                style      = tokens.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = tokens.colors.onSurface,
            )
            SessionRewardSummary(session = session, json = json, compact = true)
        }
        Spacer(Modifier.width(tokens.spacing.s))
        ChunkyButton(
            text    = stringResource(R.string.session_sheet_btn_claim),
            onClick = onClaim,
            variant = ChunkyButtonVariant.Primary,
        )
    }
}

@Composable
private fun QueuedSessionRow(action: QueuedAction) {
    val tokens = LocalFantasyTokens.current
    val emoji = GameStrings.skillEmoji(action.skillName)
    val activityLabel = action.activityKey
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase() }
        .takeIf { action.activityKey.isNotEmpty() }
    Text(
        text     = "$emoji ${action.skillDisplayName}${if (activityLabel != null) " — $activityLabel" else ""}",
        modifier = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs),
        style    = tokens.typography.bodyMedium,
        color    = tokens.colors.onSurfaceMuted,
    )
}

@Composable
private fun SessionRewardSummary(
    session: SkillSession,
    json: Json,
    compact: Boolean = false,
) {
    val tokens = LocalFantasyTokens.current
    val frames = remember(session.sessionId) {
        runCatching { json.decodeFromString<List<SessionFrame>>(session.frames) }
            .getOrElse { emptyList() }
    }
    if (frames.isEmpty()) return

    val totalXp = frames.sumOf { it.xpGain.toLong() }
    val items = buildMap<String, Int> {
        frames.forEach { frame ->
            frame.items.forEach { (k, v) -> merge(k, v) { a, b -> a + b } }
        }
    }
    val itemSummary = items.entries
        .sortedByDescending { it.value }
        .take(if (compact) 2 else 4)
        .joinToString("  ") { (k, v) -> "${k.replace('_', ' ')} ×$v" }

    Text(
        text  = buildString {
            if (totalXp > 0) append("+${totalXp.formatXp()} XP")
            if (itemSummary.isNotBlank()) {
                if (isNotEmpty()) append("  ·  ")
                append(itemSummary)
            }
        },
        style = tokens.typography.labelSmall,
        color = tokens.colors.onSurfaceMuted,
    )
}

@Composable
private fun SectionHeader(label: String) {
    val tokens = LocalFantasyTokens.current
    Text(
        text       = label,
        style      = tokens.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color      = tokens.colors.primary,
    )
}
