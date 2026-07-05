package com.fantasyidler.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import com.fantasyidler.data.model.RecentSession
import com.fantasyidler.util.toTitleCase
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.HiredWorker
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.WorkerTier
import com.fantasyidler.data.json.BlessingType
import com.fantasyidler.repository.ChurchRepository
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.HomeViewModel
import com.fantasyidler.ui.viewmodel.SessionSummary
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.ui.viewmodel.totalLevelFrom
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp
import com.fantasyidler.simulator.XpTable
import com.fantasyidler.util.formatDurationMs
import com.fantasyidler.util.toCountdown
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ElevatedCard
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign

internal fun xpBreakdownText(total: Long, bonus: Long, boostWasActive: Boolean): String? {
    if (bonus <= 0L) return null
    val afterBoost = total - bonus
    if (afterBoost <= 0L) return null
    val base = afterBoost / (if (boostWasActive) 2L else 1L)
    val blessMult = total.toDouble() / afterBoost
    val blessStr = "%.2f".format(blessMult).trimEnd('0').trimEnd('.')
    return if (boostWasActive) "(${base.formatXp()} × 2 × $blessStr)"
           else "(${base.formatXp()} × $blessStr)"
}

@Composable
internal fun HomeSessionCard(
    session: SkillSession,
    context: android.content.Context,
    skillXp: Map<String, Long>,
    sessionXpGain: Long,
    onRepeat: () -> Unit,
    onAbandon: () -> Unit,
    onDebugFinish: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showAbandonConfirm by remember { mutableStateOf(false) }
    val endsAt = session.endsAt
    LaunchedEffect(endsAt) {
        while (System.currentTimeMillis() < endsAt) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
        now = System.currentTimeMillis()
    }

    val isDone = session.completed || now >= endsAt

    val skillLabel = when (session.skillName) {
        "combat" -> context.getString(R.string.label_combat)
        else     -> GameStrings.skillName(context, session.skillName)
    }
    val skillEmoji = GameStrings.skillEmoji(session.skillName)
    val activityLabel = when (session.skillName) {
        "combat"     -> GameStrings.dungeonName(context, session.activityKey)
        "boss"       -> GameStrings.bossName(context, session.activityKey)
        "expedition" -> GameStrings.skillingDungeonName(context, session.activityKey, session.activityKey.toTitleCase())
        else         -> GameStrings.itemName(context, session.activityKey)
    }.takeIf { session.activityKey.isNotEmpty() }

    Surface(
        shape  = RoundedCornerShape(16.dp),
        color  = if (isDone) MaterialTheme.colorScheme.primaryContainer
                 else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text  = if (isDone) stringResource(R.string.label_session_complete)
                        else stringResource(R.string.label_session_active),
                style = MaterialTheme.typography.labelMedium,
                color = if (isDone) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append("$skillEmoji $skillLabel")
                    if (activityLabel != null) append(" — $activityLabel")
                },
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = if (isDone) MaterialTheme.colorScheme.onPrimaryContainer
                             else MaterialTheme.colorScheme.onSecondaryContainer,
            )

            if (!isDone) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text  = remember(now) { endsAt.toCountdown() },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                val xpLineText = remember(session.skillName, skillXp, sessionXpGain) {
                    if (sessionXpGain <= 0L) null
                    else {
                        val startXp    = skillXp[session.skillName] ?: 0L
                        val endXp      = startXp + sessionXpGain
                        val levelBefore = XpTable.levelForXp(startXp)
                        val levelAfter  = XpTable.levelForXp(endXp)
                        val levelGain   = levelAfter - levelBefore
                        val pct         = (XpTable.progressFraction(endXp) * 100).toInt()
                        buildString {
                            append("+${sessionXpGain.formatXp()} XP  →  Lv $levelAfter")
                            if (levelGain > 0) append(" (+$levelGain, $pct%)")
                            else append(" ($pct%)")
                        }
                    }
                }
                if (xpLineText != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text  = xpLineText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))

            if (!isDone) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRepeat) {
                        Text(stringResource(R.string.btn_repeat_action))
                    }
                    OutlinedButton(onClick = { showAbandonConfirm = true }) {
                        Text(stringResource(R.string.btn_abandon))
                    }

                    if (showAbandonConfirm) {
                        AlertDialog(
                            onDismissRequest = { showAbandonConfirm = false },
                            title = { Text(stringResource(R.string.session_abandon_title)) },
                            text  = { Text(stringResource(R.string.session_abandon_body)) },
                            confirmButton = {
                                TextButton(onClick = { showAbandonConfirm = false; onAbandon() }) {
                                    Text(stringResource(R.string.btn_confirm))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showAbandonConfirm = false }) {
                                    Text(stringResource(R.string.btn_cancel))
                                }
                            },
                        )
                    }
                    if (BuildConfig.DEBUG) {
                        TextButton(onClick = onDebugFinish) {
                            Text("[Debug] Finish Now")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun QueueCard(
    queue: List<QueuedAction>,
    queueEndsAt: Long,
    context: android.content.Context,
    skillXp: Map<String, Long>,
    activeSessionSkill: String,
    activeSessionXpGain: Long,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text  = stringResource(R.string.home_up_next, queue.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val queueProjections: List<String?> = remember(queue, skillXp, activeSessionSkill, activeSessionXpGain) {
                val cumul = skillXp.toMutableMap()
                if (activeSessionSkill.isNotEmpty() && activeSessionXpGain > 0L)
                    cumul[activeSessionSkill] = (cumul[activeSessionSkill] ?: 0L) + activeSessionXpGain
                queue.map { a ->
                    if (a.estimatedXpGain <= 0L) null
                    else {
                        val startXp    = cumul[a.skillName] ?: 0L
                        val endXp      = startXp + a.estimatedXpGain
                        cumul[a.skillName] = endXp
                        val levelBefore = XpTable.levelForXp(startXp)
                        val levelAfter  = XpTable.levelForXp(endXp)
                        val levelGain   = levelAfter - levelBefore
                        val pct         = (XpTable.progressFraction(endXp) * 100).toInt()
                        buildString {
                            append("+${a.estimatedXpGain.formatXp()} XP  →  Lv $levelAfter")
                            if (levelGain > 0) append(" (+$levelGain, $pct%)")
                            else append(" ($pct%)")
                        }
                    }
                }
            }
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
                    val labelExpedition = stringResource(R.string.nav_expeditions)
                    val labelDungeon    = stringResource(R.string.label_dungeon)
                    val labelBoss       = stringResource(R.string.label_boss)
                    val (prefix, suffix) = when (action.skillName) {
                        "expedition" -> labelExpedition to GameStrings.skillingDungeonName(context, action.activityKey, action.skillDisplayName)
                        "combat"     -> labelDungeon    to GameStrings.dungeonName(context, action.activityKey)
                        "boss"       -> labelBoss       to GameStrings.bossName(context, action.activityKey)
                        "farming"    -> action.skillDisplayName to null
                        else         -> GameStrings.skillName(context, action.skillName) to
                            GameStrings.itemName(context, action.activityKey)
                                .takeIf { action.activityKey.isNotEmpty() }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text  = "$emoji $prefix${if (suffix != null) " — $suffix" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        val subtitle: String? = when {
                            action.skillName == "combat" || action.skillName == "boss" -> {
                                val style = action.weaponSlot
                                    ?.let { EquipSlot.combatStyleForSlot(it) }
                                    ?: "attack"
                                when (style) {
                                    "attack"   -> stringResource(R.string.label_attack)
                                    "strength" -> stringResource(R.string.label_strength)
                                    "ranged"   -> stringResource(R.string.label_ranged)
                                    "magic"    -> stringResource(R.string.label_magic)
                                    else       -> null
                                }
                            }
                            action.outputQty > 0 -> stringResource(R.string.queue_item_qty_with_output, action.qty, action.outputQty)
                            action.qty > 0 -> stringResource(R.string.queue_item_qty, action.qty)
                            else -> null
                        }
                        if (subtitle != null) {
                            Text(
                                text  = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val projLine = queueProjections.getOrNull(index)
                        if (projLine != null) {
                            Text(
                                text  = projLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                    if (queue.size > 1) {
                        IconButton(
                            onClick  = { onMove(index, index - 1) },
                            enabled  = index > 0,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector        = Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Move up",
                                modifier           = Modifier.size(16.dp),
                                tint               = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant
                                                     else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            )
                        }
                        IconButton(
                            onClick  = { onMove(index, index + 1) },
                            enabled  = index < queue.size - 1,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector        = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Move down",
                                modifier           = Modifier.size(16.dp),
                                tint               = if (index < queue.size - 1) MaterialTheme.colorScheme.onSurfaceVariant
                                                     else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            )
                        }
                    }
                    IconButton(
                        onClick  = { onRemove(index) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Close,
                            contentDescription = "Remove from queue",
                            modifier           = Modifier.size(16.dp),
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (queueEndsAt > 0L) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                )
                val remaining = (queueEndsAt - System.currentTimeMillis()).coerceAtLeast(0L)
                Text(
                    text  = stringResource(R.string.home_queue_ends_in, remaining.formatDurationMs()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun WorkerSessionCard(
    slot: Int,
    hiredWorker: HiredWorker,
    session: SkillSession?,
    pendingCollect: Boolean,
    context: android.content.Context,
    skillXp: Map<String, Long>,
    sessionXpGain: Long,
    onCollect: () -> Unit,
    onDismiss: () -> Unit,
    onDebugFinish: () -> Unit,
    onNavigateToWorkerSkills: (Int) -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDismissConfirm by remember { mutableStateOf(false) }
    val endsAt = session?.endsAt ?: 0L
    LaunchedEffect(endsAt) {
        if (endsAt > 0) {
            while (System.currentTimeMillis() < endsAt) {
                now = System.currentTimeMillis()
                delay(1_000L)
            }
            now = System.currentTimeMillis()
        }
    }
    val isDone = pendingCollect || (session != null && (session.completed || (endsAt > 0 && now >= endsAt)))

    val tierLabel = when (hiredWorker.tier) {
        WorkerTier.LONG_LABORER -> stringResource(R.string.worker_long_laborer)
        WorkerTier.APPRENTICE   -> stringResource(R.string.worker_apprentice)
        WorkerTier.JOURNEYMAN   -> stringResource(R.string.worker_journeyman)
        WorkerTier.MASTER       -> stringResource(R.string.worker_master)
    }

    if (showDismissConfirm) {
        AlertDialog(
            onDismissRequest = { showDismissConfirm = false },
            title = { Text(stringResource(R.string.worker_dismiss_confirm_title)) },
            text  = { Text(stringResource(R.string.worker_dismiss_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { showDismissConfirm = false; onDismiss() }) {
                    Text(stringResource(R.string.btn_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDismissConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }

    Surface(
        shape    = RoundedCornerShape(16.dp),
        color    = if (isDone) MaterialTheme.colorScheme.primaryContainer
                   else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Text(
                    text  = stringResource(R.string.worker_card_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isDone) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text       = "$tierLabel · ${hiredWorker.dailyName}",
                    style      = MaterialTheme.typography.labelMedium,
                    color      = GoldPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(4.dp))
            if (session != null) {
                val skillLabel = when (session.skillName) {
                    "combat" -> context.getString(R.string.label_combat)
                    else     -> GameStrings.skillName(context, session.skillName)
                }
                val skillEmoji    = GameStrings.skillEmoji(session.skillName)
                val activityLabel = when (session.skillName) {
                    "combat" -> GameStrings.dungeonName(context, session.activityKey)
                    "boss"   -> GameStrings.bossName(context, session.activityKey)
                    else     -> GameStrings.itemName(context, session.activityKey)
                }.takeIf { session.activityKey.isNotEmpty() }
                Text(
                    text       = buildString {
                        append("$skillEmoji $skillLabel")
                        if (activityLabel != null) append(" — $activityLabel")
                    },
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = if (isDone) MaterialTheme.colorScheme.onPrimaryContainer
                                 else MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (!isDone && endsAt > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text       = remember(now) { endsAt.toCountdown() },
                        style      = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    val xpLineText = remember(session.skillName, skillXp, sessionXpGain) {
                        if (sessionXpGain <= 0L) null
                        else {
                            val startXp    = skillXp[session.skillName] ?: 0L
                            val endXp      = startXp + sessionXpGain
                            val levelBefore = XpTable.levelForXp(startXp)
                            val levelAfter  = XpTable.levelForXp(endXp)
                            val levelGain   = levelAfter - levelBefore
                            val pct         = (XpTable.progressFraction(endXp) * 100).toInt()
                            buildString {
                                append("+${sessionXpGain.formatXp()} XP  →  Lv $levelAfter")
                                if (levelGain > 0) append(" (+$levelGain, $pct%)")
                                else append(" ($pct%)")
                            }
                        }
                    }
                    if (xpLineText != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text  = xpLineText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            } else {
                Text(
                    text  = stringResource(R.string.worker_no_active_session),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (isDone) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = stringResource(R.string.worker_session_complete),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isDone) {
                    Button(onClick = onCollect) {
                        Text(stringResource(R.string.worker_collect_btn))
                    }
                }
                if (!isDone && session == null) {
                    Button(onClick = { onNavigateToWorkerSkills(slot) }) {
                        Text(stringResource(R.string.worker_add_sessions))
                    }
                }
                OutlinedButton(onClick = { showDismissConfirm = true }) {
                    Text(stringResource(R.string.worker_dismiss_btn))
                }
                if (BuildConfig.DEBUG && session != null && !isDone) {
                    TextButton(onClick = onDebugFinish) {
                        Text("[Debug] Finish Worker")
                    }
                }
            }
        }
    }
}

@Composable
internal fun SummarySection(title: String) {
    Text(
        text  = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun SummaryRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text       = value,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun StatItem(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text  = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun RecentSessionsSheet(
    sessions: List<RecentSession>,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
        Text(
            text       = stringResource(R.string.label_recent_activity),
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        if (sessions.isEmpty()) {
            Text(
                text  = stringResource(R.string.label_no_sessions_yet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            sessions.forEachIndexed { index, entry ->
                val activityDisplay = if (entry.activityKey.isNotEmpty()) {
                    when (entry.skillName) {
                        "boss"       -> GameStrings.bossName(context, entry.activityKey)
                        "combat"     -> GameStrings.dungeonName(context, entry.activityKey)
                        "expedition" -> GameStrings.skillingDungeonName(context, entry.activityKey, entry.activityKey.toTitleCase())
                        else         -> GameStrings.itemName(context, entry.activityKey)
                    }
                } else {
                    entry.activityDisplayName
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text  = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(28.dp),
                    )
                    Text(
                        text     = GameStrings.skillName(context, entry.skillName),
                        style    = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text  = activityDisplay,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (index < sessions.lastIndex) HorizontalDivider()
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.btn_cancel))
        }
    }
}
