package com.fantasyidler.ui.screen.skills


import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.data.json.LogData
import com.fantasyidler.data.model.Skills
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.fantasyidler.ui.screen.QtyQuickButtons
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatDurationMs
import com.fantasyidler.ui.viewmodel.QuestFillSuggestion
import com.fantasyidler.ui.viewmodel.QuestIndicator


@Composable
internal fun FiremakingSheet(
    guildDailyButton: (@Composable () -> Unit)? = null,
    /** Host-owned back interceptor: set while the quantity page is open so the system back button steps back to the item list (issue #1330). */
    backStep: MutableState<(() -> Unit)?>? = null,
    availableLogs: Map<String, LogData>,
    inventory: Map<String, Int>,
    currentXp: Long,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    perLogMs: Map<String, Long> = emptyMap(),
    onStart: (logKey: String, qty: Int) -> Unit,
    context: Context,
    craftLimit: Int = Int.MAX_VALUE,
    questFills: Map<String, List<QuestFillSuggestion>> = emptyMap(),
    activeQuests: Map<String, List<QuestIndicator>> = emptyMap(),
) {
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val logScrollState = rememberScrollState()
    if (backStep != null) {
        DisposableEffect(selectedKey) {
            backStep.value = if (selectedKey != null) ({ selectedKey = null }) else null
            onDispose { backStep.value = null }
        }
    }
    // Dialog-based sheets (material3 1.3+) deliver back presses to in-content handlers;
    // the host's onDismissRequest interception covers the older popup-based sheet.
    BackHandler(enabled = selectedKey != null) { selectedKey = null }
    val selectedLog = selectedKey?.let { availableLogs[it] }
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
    ) {
        if (selectedLog == null) {
            Text(
                text     = stringResource(R.string.skill_firemaking_name),
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Text(
                text     = stringResource(R.string.skill_firemaking_desc),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            guildDailyButton?.invoke()
            HorizontalDivider()
            if (availableLogs.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.skills_no_logs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(Modifier.verticalScroll(logScrollState).imePadding()) {
                    availableLogs.entries.sortedBy { it.value.levelRequired }.forEach { (key, log) ->
                        val ashKey = when (key) {
                            "oak_log" -> "oak_ashes"; "willow_log" -> "willow_ashes"
                            "maple_log" -> "maple_ashes"; "yew_log" -> "yew_ashes"
                            "magic_log" -> "magic_ashes"; "redwood_log" -> "redwood_ashes"
                            else -> "ashes"
                        }
                        val ashName = GameStrings.itemName(context, ashKey)
                        val logsOwned = inventory[key] ?: 0
                        val rowAlpha = if (logsOwned > 0) 1f else 0.38f
                        Row(
                            modifier          = Modifier.fillMaxWidth().clickable(enabled = logsOwned > 0) { selectedKey = key }.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(GameStrings.itemName(context, key), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = rowAlpha), modifier = Modifier.weight(1f, fill = false))
                                    val questIndicators = activeQuests["${Skills.FIREMAKING}:$ashKey"] ?: emptyList()
                                    if (questIndicators.isNotEmpty()) {
                                        QuestIndicatorIcons(questIndicators)
                                    }
                                }
                                val ashOwned = inventory[ashKey] ?: 0
                                Text(
                                    text  = stringResource(R.string.firemaking_burns_to, ashName) + "  •  ${log.xpPerLog} XP",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = rowAlpha),
                                )
                                Text(
                                    text  = stringResource(R.string.firemaking_ashes_owned, ashOwned),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (ashOwned > 0) MaterialTheme.colorScheme.primary else dim,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = " ", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = "$logsOwned ${stringResource(R.string.firemaking_logs_in_inventory)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = rowAlpha),
                                )
                                if (isQueueFull) {
                                    Text(
                                        text = stringResource(R.string.snackbar_queue_full),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = dim,
                                    )
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        } else {
            val key      = selectedKey!!
            val ashKey   = when (key) {
                "oak_log" -> "oak_ashes"; "willow_log" -> "willow_ashes"
                "maple_log" -> "maple_ashes"; "yew_log" -> "yew_ashes"
                "magic_log" -> "magic_ashes"; "redwood_log" -> "redwood_ashes"
                else -> "ashes"
            }
            val ashOwned = inventory[ashKey] ?: 0
            val maxQty   = (inventory[key] ?: 0).coerceAtMost(craftLimit)
            var qty      by remember(key) { mutableIntStateOf(maxQty.coerceAtLeast(1)) }
            var textValue by remember(key) { mutableStateOf(maxQty.coerceAtLeast(1).toString()) }
            val totalXp = selectedLog.xpPerLog * qty
            val detailScrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(detailScrollState)
                    .imePadding(),
            ) {
                TextButton(onClick = { selectedKey = null }, modifier = Modifier.padding(start = 4.dp)) {
                    Text(stringResource(R.string.btn_back_arrow))
                }
                Text(
                    text     = GameStrings.itemName(context, key),
                    style    = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Text(
                    text     = "${maxQty} ${stringResource(R.string.firemaking_logs_in_inventory)}",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Text(
                    text     = stringResource(R.string.firemaking_ashes_owned, ashOwned),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = if (ashOwned > 0) MaterialTheme.colorScheme.primary else dim,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (qty > 1) { qty--; textValue = qty.toString() } }, enabled = qty > 1) {
                        Icon(Icons.Filled.Remove, contentDescription = null)
                    }
                    OutlinedTextField(
                        value         = textValue,
                        onValueChange = { new ->
                            val f = new.filter { it.isDigit() }
                            textValue = f
                            f.toIntOrNull()?.coerceIn(1, maxQty.coerceAtLeast(1))?.let { qty = it }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine    = true,
                        modifier      = Modifier.width(130.dp),
                        textStyle     = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                    )
                    IconButton(onClick = { if (qty < maxQty) { qty++; textValue = qty.toString() } }, enabled = qty < maxQty) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    }
                }
                QtyQuickButtons(qty, maxQty) { qty = it; textValue = it.toString() }
                QuestFillRow(questFills[key] ?: emptyList(), qty, maxQty, modifier = Modifier.padding(horizontal = 16.dp)) { qty = it; textValue = it.toString() }
                Spacer(Modifier.height(8.dp))
                Text(
                    text       = projectedXpLabel(currentXp, totalXp.toLong()),
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                val logPerMs = perLogMs[key]?.takeIf { it > 0 } ?: (sessionDurationMs / 60)
                if (logPerMs > 0) {
                    Text(
                        text     = "~${(qty.toLong() * logPerMs).formatDurationMs(context)}",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                }
                Button(
                    onClick  = { onStart(key, qty); selectedKey = null },
                    enabled  = !isStarting && maxQty > 0,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) { Text(stringResource(R.string.firemaking_burn)) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Prayer sheet
// ---------------------------------------------------------------------------