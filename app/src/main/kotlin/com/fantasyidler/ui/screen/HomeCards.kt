package com.fantasyidler.ui.screen

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.painterResource
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
import com.fantasyidler.ui.viewmodel.HomeViewModel
import com.fantasyidler.ui.viewmodel.SessionSummary
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.ui.viewmodel.totalLevelFrom
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp
import com.fantasyidler.simulator.XpTable
import com.fantasyidler.util.formatDurationMs
import com.fantasyidler.util.toClockTime
import com.fantasyidler.util.toCountdown
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign

internal fun xpBreakdownText(total: Long, bonus: Long, boostFactor: Long): String? {
    if (bonus <= 0L && boostFactor <= 1L) return null
    val afterBoost = total - bonus
    if (afterBoost <= 0L) return null
    val base = afterBoost / boostFactor.coerceAtLeast(1L)
    val blessMult = total.toDouble() / afterBoost
    val factors = buildList {
        if (boostFactor > 1L) add("$boostFactor")
        if (bonus > 0L) add("%.2f".format(blessMult).trimEnd('0').trimEnd('.'))
    }
    if (factors.isEmpty()) return null
    return "(${base.formatXp()} × ${factors.joinToString(" × ")})"
}

@Composable
internal fun HomeSessionCard(
    session: SkillSession,
    context: Context,
    skillXp: Map<String, Long>,
    sessionXpGain: Long,
    showEndTime: Boolean = true,
    bossEmoji: String? = null,
    bossDurationMinutes: Int? = null,
    repeatIndex: Int = 0,
    repeatTotal: Int = 0,
    assignedItems: Map<String, Int> = emptyMap(),
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
    val skillEmoji = bossEmoji ?: GameStrings.skillEmoji(session.skillName)
    val activityLabel = when (session.skillName) {
        "combat"      -> GameStrings.dungeonName(context, session.activityKey)
        "boss"        -> GameStrings.bossName(context, session.activityKey)
        "carnival"    -> GameStrings.carnivalGameName(context, session.activityKey)
        "expedition"  -> GameStrings.skillingDungeonName(context, session.activityKey, session.activityKey.toTitleCase())
        "mercantile"  -> GameStrings.tradeRouteName(context, session.activityKey)
        "agility"     -> GameStrings.agilityCourse(context, session.activityKey)
        "woodcutting" -> GameStrings.treeName(context, session.activityKey)
        "thieving"    -> GameStrings.thievingNpcName(context, session.activityKey)
        "tower"      -> context.getString(R.string.tower_title) + ": " + context.getString(
            R.string.tower_floor_label,
            session.activityKey.removePrefix("tower_floor_").toIntOrNull() ?: 0,
        )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                val titleColor = if (isDone) MaterialTheme.colorScheme.onPrimaryContainer
                                 else MaterialTheme.colorScheme.onSecondaryContainer
                val iconRes = GameStrings.skillIconRes(session.skillName)
                if (session.skillName == "boss") {
                    BossIcon(
                        bossId        = session.activityKey,
                        modifier      = Modifier.size(20.dp),
                        fallbackEmoji = skillEmoji,
                    )
                    Spacer(Modifier.width(6.dp))
                } else if (iconRes != null) {
                    Image(
                        painter            = painterResource(iconRes),
                        contentDescription = null,
                        modifier           = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                } else {
                    Text(
                        text  = "$skillEmoji ",
                        style = MaterialTheme.typography.titleMedium,
                        color = titleColor,
                    )
                }
                Text(
                    text = buildString {
                        append(skillLabel)
                        if (activityLabel != null) append(" — $activityLabel")
                    },
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = titleColor,
                )
            }

            if ((session.skillName == "boss" || session.skillName == "combat") && repeatTotal > 1) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text       = if (session.skillName == "boss") stringResource(R.string.combat_fight_progress, repeatIndex.coerceAtLeast(1), repeatTotal)
                                 else stringResource(R.string.combat_run_progress, repeatIndex.coerceAtLeast(1), repeatTotal),
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.primary,
                )
            }

            if (!isDone) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text  = remember(now, showEndTime) {
                        if (session.skillName == "boss" && bossDurationMinutes != null)
                            bossFightCountdown(context, session.startedAt, endsAt, bossDurationMinutes, now, showEndTime)
                        else endsAt.toCountdown(context, showEndTime)
                    },
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
                            append("+${sessionXpGain.formatXp()} XP  →  ${context.getString(R.string.label_lv, levelAfter)}")
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
                if (assignedItems.isNotEmpty()) {
                    val assignedTemplate = stringResource(R.string.worker_session_assigned)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text  = assignedItems.entries.joinToString("  ") { (key, qty) ->
                            assignedTemplate.format("%,d".format(qty), GameStrings.itemName(context, key))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))

            if (!isDone) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onRepeat,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.btn_repeat_action))
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = { showAbandonConfirm = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
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
                }
                if (BuildConfig.DEBUG) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    maxQueueSize: Int,
    queueEndsAt: Long,
    context: Context,
    skillXp: Map<String, Long>,
    activeSessionSkill: String,
    activeSessionXpGain: Long,
    towerCurrentFloor: Int,
    showEndTime: Boolean = true,
    bossEmoji: (String) -> String? = { null },
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
                text  = stringResource(R.string.home_up_next, queue.size, maxQueueSize),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val queueProjections: List<String?> = remember(queue, skillXp, activeSessionSkill, activeSessionXpGain) {
                val cumul = skillXp.toMutableMap()
                if (activeSessionSkill.isNotEmpty() && activeSessionXpGain > 0L)
                    cumul[activeSessionSkill] = (cumul[activeSessionSkill] ?: 0L) + activeSessionXpGain
                queue.map { a ->
                    val duration = if (a.estimatedDurationMs > 0L) a.estimatedDurationMs.formatDurationMs(context) else null
                    val xpPart = if (a.estimatedXpGain <= 0L) null
                    else {
                        val startXp    = cumul[a.skillName] ?: 0L
                        val endXp      = startXp + a.estimatedXpGain
                        cumul[a.skillName] = endXp
                        val levelBefore = XpTable.levelForXp(startXp)
                        val levelAfter  = XpTable.levelForXp(endXp)
                        val levelGain   = levelAfter - levelBefore
                        val pct         = (XpTable.progressFraction(endXp) * 100).toInt()
                        when (a.skillName) {
                            "combat", "boss" -> buildString {
                                append("+${a.estimatedXpGain.formatXp()} XP")
                            }
                            else -> buildString {
                                append("+${a.estimatedXpGain.formatXp()} XP  →  ${context.getString(R.string.label_lv, levelAfter)}")
                                if (levelGain > 0) append(" (+$levelGain, $pct%)")
                                else append(" ($pct%)")
                            }
                        }
                    }
                    when {
                        xpPart != null && duration != null -> "$xpPart  •  $duration"
                        xpPart != null                     -> xpPart
                        else                               -> duration
                    }
                }
            }
            // Tower queue labels are never trusted as-stored (the real floor is always
            // recomputed at execution time), so predict them live from current progress
            // instead of showing whatever floor number was guessed when each was queued.
            val towerFloorLabels: List<Int?> = remember(queue, towerCurrentFloor, activeSessionSkill) {
                var next = towerCurrentFloor + if (activeSessionSkill == "tower") 1 else 0
                queue.map { a -> if (a.skillName != "tower") null else { next += 1; next } }
            }
            val haptic = LocalHapticFeedback.current
            var draggingIndex by remember { mutableIntStateOf(-1) }
            var dragOffsetY by remember { mutableFloatStateOf(0f) }
            val rowHeights = remember { mutableStateMapOf<Int, Int>() }
            // A completed session can consume a queue entry mid-drag; abandon the drag rather
            // than dropping against stale indices.
            LaunchedEffect(queue.size) { draggingIndex = -1; dragOffsetY = 0f }

            fun dropTargetIndex(from: Int, offset: Float): Int {
                var target = from
                var remaining = offset
                while (remaining < 0 && target > 0) {
                    val h = rowHeights[target - 1] ?: break
                    if (remaining <= -h / 2f) { remaining += h; target-- } else break
                }
                while (remaining > 0 && target < queue.size - 1) {
                    val h = rowHeights[target + 1] ?: break
                    if (remaining >= h / 2f) { remaining -= h; target++ } else break
                }
                return target
            }

            queue.forEachIndexed { index, action ->
                if (index > 0) HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                )
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { rowHeights[index] = it.size.height }
                        .zIndex(if (index == draggingIndex) 1f else 0f)
                        .graphicsLayer {
                            if (index == draggingIndex) {
                                translationY    = dragOffsetY
                                shadowElevation = 8f
                            }
                        }
                        .then(
                            if (index == draggingIndex)
                                Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            else Modifier
                        )
                        .pointerInput(queue.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    draggingIndex = index
                                    dragOffsetY   = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                },
                                onDragEnd = {
                                    val target = dropTargetIndex(index, dragOffsetY)
                                    if (target != index) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onMove(index, target)
                                    }
                                    draggingIndex = -1
                                    dragOffsetY   = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = -1
                                    dragOffsetY   = 0f
                                },
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val emoji = GameStrings.skillEmoji(action.skillName)
                    val labelExpedition = stringResource(R.string.nav_expeditions)
                    val labelDungeon    = stringResource(R.string.label_dungeon)
                    val labelBoss       = stringResource(R.string.label_boss)
                    val (prefix, suffix) = when (action.skillName) {
                        "expedition"  -> labelExpedition to GameStrings.skillingDungeonName(context, action.activityKey, action.skillDisplayName)
                        "combat"      -> labelDungeon    to GameStrings.dungeonName(context, action.activityKey)
                        "boss"        -> labelBoss       to GameStrings.bossName(context, action.activityKey)
                        "farming"     -> action.skillDisplayName to null
                        "tower"       -> stringResource(R.string.tower_title) to stringResource(R.string.tower_floor_label, towerFloorLabels.getOrNull(index) ?: 0)
                        "mercantile"  -> GameStrings.skillName(context, action.skillName) to GameStrings.tradeRouteName(context, action.activityKey)
                        "agility"     -> GameStrings.skillName(context, action.skillName) to GameStrings.agilityCourse(context, action.activityKey)
                        "woodcutting" -> GameStrings.skillName(context, action.skillName) to GameStrings.treeName(context, action.activityKey)
                        "thieving"    -> GameStrings.skillName(context, action.skillName) to GameStrings.thievingNpcName(context, action.activityKey)
                        "carnival"    -> GameStrings.skillName(context, action.skillName) to GameStrings.carnivalGameName(context, action.activityKey)
                        else         -> GameStrings.skillName(context, action.skillName) to
                            GameStrings.itemName(context, action.activityKey)
                                .takeIf { action.activityKey.isNotEmpty() }
                    }
                    // Every row gets the same leading 20dp icon slot (issue #1391): boss art for
                    // bosses, the skill drawable when one exists, and the emoji otherwise (tower).
                    val iconRes = GameStrings.skillIconRes(action.skillName)
                    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                        when {
                            action.skillName == "boss" -> BossIcon(
                                bossId        = action.activityKey,
                                modifier      = Modifier.size(20.dp),
                                // skillEmoji("boss") is the 🎮 fallback; prefer the boss's own
                                // emoji for bosses without sprite art (issue #1614).
                                fallbackEmoji = bossEmoji(action.activityKey) ?: emoji,
                            )
                            iconRes != null -> Image(
                                painter            = painterResource(iconRes),
                                contentDescription = null,
                                modifier           = Modifier.size(20.dp),
                            )
                            else -> Text(emoji, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text  = "$prefix${if (suffix != null) " — $suffix" else ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        val subtitle: String? = when {
                            action.skillName == "combat" || action.skillName == "boss" -> {
                                val style = action.weaponSlot
                                    ?.let { EquipSlot.combatStyleForSlot(it) }
                                    ?: "attack"
                                val styleLabel = when (style) {
                                    "attack"   -> stringResource(R.string.label_attack)
                                    "strength" -> stringResource(R.string.label_strength)
                                    "ranged"   -> stringResource(R.string.label_ranged)
                                    "magic"    -> stringResource(R.string.label_magic)
                                    else       -> null
                                }
                                if (action.repeatCount > 1) {
                                    val countLabel = if (action.skillName == "boss")
                                        stringResource(R.string.combat_fight_count_suffix, action.repeatCount)
                                    else
                                        stringResource(R.string.combat_run_count_suffix, action.repeatCount)
                                    if (styleLabel != null) "$styleLabel • $countLabel" else countLabel
                                } else styleLabel
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
                            onClick  = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMove(index, index - 1)
                            },
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
                            onClick  = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMove(index, index + 1)
                            },
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
                val queueEndsText = if (showEndTime) {
                    "${stringResource(R.string.home_queue_ends_in, remaining.formatDurationMs(context))} (${queueEndsAt.toClockTime(context)})"
                } else {
                    stringResource(R.string.home_queue_ends_in, remaining.formatDurationMs(context))
                }
                Text(
                    text  = queueEndsText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkerSessionCard(
    slot: Int,
    hiredWorker: HiredWorker,
    session: SkillSession?,
    pendingCollect: Boolean,
    context: Context,
    skillXp: Map<String, Long>,
    sessionXpGain: Long,
    showEndTime: Boolean = true,
    assignedItems: Map<String, Int> = emptyMap(),
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
                    color      = MaterialTheme.colorScheme.primary,
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
                    "carnival" -> GameStrings.carnivalGameName(context, session.activityKey)
                    else     -> GameStrings.itemName(context, session.activityKey)
                }.takeIf { session.activityKey.isNotEmpty() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val titleColor = if (isDone) MaterialTheme.colorScheme.onPrimaryContainer
                                     else MaterialTheme.colorScheme.onSecondaryContainer
                    val iconRes = GameStrings.skillIconRes(session.skillName)
                    if (iconRes != null) {
                        Image(
                            painter            = painterResource(iconRes),
                            contentDescription = null,
                            modifier           = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    } else {
                        Text(
                            text  = "$skillEmoji ",
                            style = MaterialTheme.typography.titleMedium,
                            color = titleColor,
                        )
                    }
                    Text(
                        text       = buildString {
                            append(skillLabel)
                            if (activityLabel != null) append(" — $activityLabel")
                        },
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = titleColor,
                    )
                }
                if (!isDone && endsAt > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text       = remember(now, showEndTime) { endsAt.toCountdown(context, showEndTime) },
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
                    if (assignedItems.isNotEmpty()) {
                        val assignedTemplate = stringResource(R.string.worker_session_assigned)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text  = assignedItems.entries.joinToString("  ") { (key, qty) ->
                                assignedTemplate.format("%,d".format(qty), GameStrings.itemName(context, key))
                            },
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(4.dp),
            ) {
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
                // Hidden while a finished session awaits collection: dismissing there
                // abandons the uncollected rewards on a single confirm (issue #1202).
                if (!isDone) {
                    OutlinedButton(
                        onClick = { showDismissConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.worker_dismiss_btn))
                    }
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
internal fun SummaryRow(
    label: String,
    value: String,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = labelColor, fontWeight = fontWeight, modifier = Modifier.weight(1f))
        Text(
            text       = value,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = if (fontWeight == FontWeight.Bold) FontWeight.Bold else FontWeight.SemiBold,
            color      = valueColor,
        )
    }
}

/** Compact single-line label+value pair, e.g. for a thin horizontal stats bar. */
@Composable
internal fun StatInline(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text       = value,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color      = valueColor,
        )
    }
}

@Composable
internal fun RecentSessionsSheet(
    sessions: List<RecentSession>,
    bossEmoji: (String) -> String? = { null },
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
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
                    Row(
                        modifier          = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val iconRes = GameStrings.skillIconRes(entry.skillName)
                        when {
                            // Boss rows: sprite art, or the boss's own emoji when it has no art;
                            // skillEmoji("boss") is the generic gamepad fallback (issue #1633).
                            entry.skillName == "boss" -> {
                                BossIcon(
                                    bossId        = entry.activityKey,
                                    modifier      = Modifier.size(18.dp),
                                    fallbackEmoji = bossEmoji(entry.activityKey) ?: GameStrings.skillEmoji(entry.skillName),
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            iconRes != null -> {
                                Image(
                                    painter            = painterResource(iconRes),
                                    contentDescription = null,
                                    modifier           = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            else -> Text(
                                text  = "${GameStrings.skillEmoji(entry.skillName)} ",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            text  = GameStrings.skillName(context, entry.skillName),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
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

@Composable
internal fun JournalSheet(
    notes: String,
    onSave: (String) -> Unit,
) {
    var text by remember(notes) { mutableStateOf(notes) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
        Text(
            text       = stringResource(R.string.label_journal),
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = stringResource(R.string.journal_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value         = text,
            onValueChange = { text = it },
            modifier      = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            placeholder   = { Text(stringResource(R.string.journal_placeholder)) },
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick  = { onSave(text) },
            enabled  = text != notes,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.btn_save))
        }
    }
}
