package com.fantasyidler.ui.screen.skills


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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.data.model.Skills
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import com.fantasyidler.ui.viewmodel.SheetState
import com.fantasyidler.simulator.XpTable
import com.fantasyidler.ui.screen.QtyQuickButtons
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatDurationMs
import com.fantasyidler.ui.viewmodel.QuestFillSuggestion
import com.fantasyidler.ui.viewmodel.QuestIndicator


@Composable
internal fun RunecraftingSheet(
    guildDailyButton: (@Composable () -> Unit)? = null,
    /** Host-owned back interceptor: set while the quantity page is open so the system back button steps back to the item list (issue #1330). */
    backStep: MutableState<(() -> Unit)?>? = null,
    sheet: SheetState.Runecrafting,
    inventory: Map<String, Int> = emptyMap(),
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    onStart: (String, Int, String?) -> Unit,
    currentXp: Long = 0L,
    tierMaxQty: Int = Int.MAX_VALUE,
    questFills: Map<String, List<QuestFillSuggestion>> = emptyMap(),
    activeQuests: Map<String, List<QuestIndicator>> = emptyMap(),
) {
    val context = LocalContext.current
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val runeScrollState = rememberScrollState()
    if (backStep != null) {
        DisposableEffect(selectedKey) {
            backStep.value = if (selectedKey != null) ({ selectedKey = null }) else null
            onDispose { backStep.value = null }
        }
    }
    // Dialog-based sheets (material3 1.3+) deliver back presses to in-content handlers;
    // the host's onDismissRequest interception covers the older popup-based sheet.
    BackHandler(enabled = selectedKey != null) { selectedKey = null }
    val selectedRune = selectedKey?.let { sheet.availableRunes[it] }
    var selectedAshKey by remember { mutableStateOf<String?>(null) }
    val dim = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
    ) {
        if (selectedRune == null) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(runeScrollState),
            ) {
                // ── Rune type selection ──────────────────────────────────────
                Text(
                    text     = stringResource(R.string.skill_runecrafting_name),
                    style    = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                Text(
                    text     = stringResource(R.string.skill_runecrafting_desc),
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                )
                guildDailyButton?.invoke()
                Text(
                    text     = stringResource(R.string.skills_essence_qty, sheet.essenceQty),
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                HorizontalDivider()
                if (sheet.availableRunes.isEmpty()) {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text  = stringResource(R.string.skills_no_runes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (sheet.essenceQty == 0) {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text  = stringResource(R.string.skills_no_essence),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    sheet.availableRunes.forEach { (key, rune) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedKey = key }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(GameStrings.itemName(context, key), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                                    val questIndicators = activeQuests["${Skills.RUNECRAFTING}:$key"] ?: emptyList()
                                    if (questIndicators.isNotEmpty()) {
                                        QuestIndicatorIcons(questIndicators)
                                    }
                                }
                                val runeOwned = inventory[key] ?: 0
                                Text(
                                    text  = stringResource(R.string.skills_rune_desc, rune.xpPerRune.toInt(), rune.levelRequired),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text  = stringResource(R.string.crafting_owned, runeOwned),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (runeOwned > 0) MaterialTheme.colorScheme.primary else dim,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = " ", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text  = stringResource(R.string.btn_select),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (isQueueFull) {
                                    Text(
                                        text  = stringResource(R.string.snackbar_queue_full),
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
            val detailScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(detailScrollState)
                    .imePadding(),
            ) {
                // ── Quantity picker ──────────────────────────────────────────
            val inventoryMax = sheet.essenceQty
            val maxQty = minOf(inventoryMax, tierMaxQty)
            var qty by remember(selectedKey) { mutableIntStateOf(maxQty.coerceAtLeast(1)) }
            var textValue by remember(selectedKey) { mutableStateOf(maxQty.coerceAtLeast(1).toString()) }

            TextButton(
                onClick  = { selectedKey = null },
                modifier = Modifier.padding(start = 4.dp),
            ) { Text(stringResource(R.string.btn_back_arrow)) }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text  = GameStrings.itemName(context, selectedKey ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                )
                val ownedRune = selectedKey?.let { inventory[it] } ?: 0
                Text(
                    text     = stringResource(R.string.skills_rune_selected, selectedRune.xpPerRune.toInt(), inventoryMax),
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                Text(
                    text     = stringResource(R.string.crafting_owned, ownedRune),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = if (ownedRune > 0) MaterialTheme.colorScheme.primary else dim,
                    modifier = Modifier.padding(vertical = 4.dp),
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
                        textStyle  = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            textAlign  = TextAlign.Center,
                        ),
                        singleLine = true,
                        modifier   = Modifier.width(130.dp),
                    )

                    IconButton(
                        onClick  = { qty = (qty + 1).coerceAtMost(maxQty.coerceAtLeast(1)); textValue = qty.toString() },
                        enabled  = qty < maxQty,
                    ) { Icon(Icons.Filled.Add, contentDescription = "Increase") }
                }
                Spacer(Modifier.height(8.dp))
                QtyQuickButtons(qty, maxQty) { v -> qty = v; textValue = v.toString() }
                QuestFillRow(questFills[selectedKey ?: ""] ?: emptyList(), qty, maxQty) { v -> qty = v; textValue = v.toString() }
                Spacer(Modifier.height(8.dp))

                Text(
                    text       = projectedXpLabel(currentXp, (qty * selectedRune.xpPerRune).toLong()),
                    style      = MaterialTheme.typography.bodyMedium,
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.padding(vertical = 8.dp),
                )
                if (sessionDurationMs > 0) {
                    Text(
                        text     = "~${(qty.toLong() * (sessionDurationMs / 60)).formatDurationMs(context)}",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            val ashTiers = listOf("ashes","oak_ashes","willow_ashes","maple_ashes","yew_ashes","magic_ashes","redwood_ashes")
            val ownedAshes = ashTiers.filter { (inventory[it] ?: 0) > 0 }
            val ashNeeded = (qty + 9) / 10
            LaunchedEffect(ashNeeded, inventory) {
                selectedAshKey?.let { if ((inventory[it] ?: 0) < ashNeeded) selectedAshKey = null }
            }
            if (ownedAshes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.catalyst_optional), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(4.dp))
                val rcLevel = XpTable.levelForXp(currentXp)
                val rcBase = when {
                    rcLevel >= 75 -> 3
                    rcLevel >= 50 -> 2
                    else          -> 1
                }
                (listOf(null) + ownedAshes).forEach { ashKey ->
                    val totalRunes = rcBase + when (ashKey) {
                        "ashes"         -> 1
                        "oak_ashes"     -> 2
                        "willow_ashes"  -> 3
                        "maple_ashes"   -> 4
                        "yew_ashes"     -> 5
                        "magic_ashes"   -> 6
                        "redwood_ashes" -> 7
                        else            -> 0
                    }
                    val owned      = ashKey?.let { inventory[it] ?: 0 } ?: 0
                    val affordable = ashKey == null || owned >= ashNeeded
                    Row(
                        modifier          = Modifier.fillMaxWidth().clickable(enabled = affordable) { selectedAshKey = ashKey }.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text  = if (ashKey == null) stringResource(R.string.catalyst_none) else GameStrings.itemName(context, ashKey),
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                !affordable              -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                selectedAshKey == ashKey -> MaterialTheme.colorScheme.primary
                                else                     -> MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = if (selectedAshKey == ashKey) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        if (ashKey != null) {
                            if (affordable) {
                                Text(stringResource(R.string.catalyst_rune_bonus, totalRunes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("$owned / $ashNeeded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Button(
                onClick  = { onStart(selectedKey!!, qty, selectedAshKey) },
                enabled  = !isStarting && qty > 0 && maxQty > 0 && !isQueueFull,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                when {
                    isStarting  -> CircularProgressIndicator(Modifier.size(20.dp))
                    isQueueFull -> Text(stringResource(R.string.snackbar_queue_full))
                    hasActiveSession -> Text(stringResource(R.string.skills_add_to_queue))
                    else        -> Text(stringResource(R.string.btn_start_crafting))
                }
            }
        }
    }
}
}

// ---------------------------------------------------------------------------
// Session result sheet
// ---------------------------------------------------------------------------

