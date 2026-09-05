package com.fantasyidler.ui.screen.skills


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.data.json.BoneData
import com.fantasyidler.data.model.Skills
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.style.TextAlign
import com.fantasyidler.ui.screen.QtyQuickButtons
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatDurationMs
import com.fantasyidler.ui.viewmodel.QuestFillSuggestion
import com.fantasyidler.ui.viewmodel.QuestIndicator
import kotlin.collections.get


@Composable
internal fun PrayerSheet(
    guildDailyButton: (@Composable () -> Unit)? = null,
    /** Host-owned back interceptor: set while the quantity page is open so the system back button steps back to the item list (issue #1330). */
    backStep: MutableState<(() -> Unit)?>? = null,
    availableBones: Map<String, BoneData>,
    inventory: Map<String, Int>,
    prayerLevel: Int,
    currentXp: Long = 0L,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    onStart: (boneKey: String, qty: Int) -> Unit,
    onNavigateToBoneAltar: () -> Unit = {},
    tierMaxQty: Int = Int.MAX_VALUE,
    questFills: List<QuestFillSuggestion> = emptyList(),
    activeQuests: Map<String, List<QuestIndicator>> = emptyMap(),
) {
    val context = LocalContext.current
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val boneScrollState = rememberScrollState()
    if (backStep != null) {
        DisposableEffect(selectedKey) {
            backStep.value = if (selectedKey != null) ({ selectedKey = null }) else null
            onDispose { backStep.value = null }
        }
    }
    // Dialog-based sheets (material3 1.3+) deliver back presses to in-content handlers;
    // the host's onDismissRequest interception covers the older popup-based sheet.
    BackHandler(enabled = selectedKey != null) { selectedKey = null }
    val selectedBone = selectedKey?.let { availableBones[it] }
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
    ) {
        if (selectedBone == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(boneScrollState),
            ) {
                Text(
                    text     = stringResource(R.string.label_prayer),
                    style    = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                Text(
                    text     = stringResource(R.string.skill_prayer_desc),
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                )
                guildDailyButton?.invoke()
                HorizontalDivider()

                // ── Bone selection ───────────────────────────────────────────
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text     = stringResource(R.string.skills_prayer_desc, prayerLevel),
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    )
                    TextButton(onClick = onNavigateToBoneAltar) {
                        Text(stringResource(R.string.bone_altar_open))
                    }
                }
                if (availableBones.isEmpty()) {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text  = stringResource(R.string.skills_no_bones),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    availableBones.forEach { (key, bone) ->
                        val qty = inventory[key] ?: 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedKey = key }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(GameStrings.itemName(context, key), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                                    val questIndicators = activeQuests["${Skills.PRAYER}:$key"] ?: emptyList()
                                    if (questIndicators.isNotEmpty()) {
                                        QuestIndicatorIcons(questIndicators)
                                    }
                                }
                                Text(
                                    text  = stringResource(R.string.skills_xp_each, bone.xpPerBone.toInt()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text  = stringResource(R.string.crafting_owned, qty),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (qty > 0) MaterialTheme.colorScheme.primary else dim,
                                )
                                if (isQueueFull) {
                                    Text(
                                        text = context.getString(R.string.snackbar_queue_full),
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
            // ── Quantity picker ──────────────────────────────────────────
            val inventoryMax = inventory[selectedKey] ?: 0
            val maxQty = minOf(inventoryMax, tierMaxQty)
            var qty by remember(selectedKey) { mutableIntStateOf(maxQty.coerceAtLeast(1)) }
            var textValue by remember(selectedKey) { mutableStateOf(maxQty.coerceAtLeast(1).toString()) }

            TextButton(
                onClick  = { selectedKey = null },
                modifier = Modifier.padding(start = 4.dp),
            ) { Text(stringResource(R.string.btn_back_arrow)) }

            Text(
                text     = GameStrings.itemName(context, selectedKey ?: ""),
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                text     = stringResource(R.string.skills_bone_selected, selectedBone.xpPerBone.toInt(), inventoryMax),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick  = { qty = (qty - 1).coerceAtLeast(1); textValue = qty.toString() },
                    enabled  = qty > 1,
                ) { Icon(Icons.Filled.Remove, contentDescription = "Decrease") }

                OutlinedTextField(
                    value         = textValue,
                    onValueChange = { new ->
                        val filtered = new.filter { it.isDigit() }
                        val parsed   = filtered.toIntOrNull()
                        if (parsed != null) {
                            val clamped = parsed.coerceIn(1, maxQty.coerceAtLeast(1))
                            qty = clamped; textValue = clamped.toString()
                        } else {
                            textValue = filtered
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction    = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        val parsed = textValue.toIntOrNull()?.coerceIn(1, maxQty.coerceAtLeast(1)) ?: 1
                        qty = parsed; textValue = parsed.toString()
                    }),
                    textStyle    = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign  = TextAlign.Center,
                    ),
                    singleLine   = true,
                    modifier     = Modifier.width(130.dp),
                )

                IconButton(
                    onClick  = { qty = (qty + 1).coerceAtMost(maxQty.coerceAtLeast(1)); textValue = qty.toString() },
                    enabled  = qty < maxQty,
                ) { Icon(Icons.Filled.Add, contentDescription = "Increase") }
            }
            Spacer(Modifier.height(8.dp))
            QtyQuickButtons(qty, maxQty) { v -> qty = v; textValue = v.toString() }
            // Ashes give Prayer XP but never count toward prayer quests (issue #1207).
            if (!selectedBone.isAsh) {
                QuestFillRow(questFills, qty, maxQty, modifier = Modifier.padding(horizontal = 16.dp)) { v -> qty = v; textValue = v.toString() }
            }
            Spacer(Modifier.height(8.dp))

            Text(
                text     = projectedXpLabel(currentXp, (qty * selectedBone.xpPerBone).toLong()),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (sessionDurationMs > 0) {
                Text(
                    text     = "~${(qty.toLong() * (sessionDurationMs / 60)).formatDurationMs(context)}",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }

            Button(
                onClick  = { onStart(selectedKey!!, qty) },
                enabled  = !isStarting && qty > 0 && maxQty > 0 && !isQueueFull,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                when {
                    isStarting  -> CircularProgressIndicator(Modifier.size(20.dp))
                    isQueueFull -> Text(stringResource(R.string.snackbar_queue_full))
                    hasActiveSession -> Text(stringResource(R.string.skills_add_to_queue))
                    else        -> Text(stringResource(R.string.btn_start_burying))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Runecrafting sheet
// ---------------------------------------------------------------------------