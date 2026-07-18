package com.fantasyidler.ui.screen


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.ui.viewmodel.ExpeditionsViewModel
import com.fantasyidler.data.json.AgilityCourseData
import com.fantasyidler.data.json.BoneData
import com.fantasyidler.data.json.FishData
import com.fantasyidler.data.json.LogData
import com.fantasyidler.data.json.OreData
import com.fantasyidler.data.json.ThievingNpcData
import com.fantasyidler.data.json.TreeData
import com.fantasyidler.data.model.Skills
import com.fantasyidler.ui.theme.GoldPrimary
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.style.TextAlign
import com.fantasyidler.ui.viewmodel.CraftableRecipe
import com.fantasyidler.ui.viewmodel.CraftingUiState
import com.fantasyidler.ui.viewmodel.CraftingViewModel
import com.fantasyidler.ui.viewmodel.SheetState
import com.fantasyidler.ui.viewmodel.levelDisplay
import com.fantasyidler.ui.viewmodel.SkillsUiState
import com.fantasyidler.ui.viewmodel.SkillsViewModel
import com.fantasyidler.ui.viewmodel.xpProgressFraction
import com.fantasyidler.ui.viewmodel.nextLevelThreshold
import com.fantasyidler.ui.viewmodel.xpToNextLevel
import com.fantasyidler.simulator.SkillSimulator
import com.fantasyidler.simulator.XpTable
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.toTitleCase
import com.fantasyidler.util.formatDurationMs
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.toCountdown
import java.util.Locale
import com.fantasyidler.ui.viewmodel.QuestFillSuggestion
import com.fantasyidler.ui.viewmodel.QuestCategory
import com.fantasyidler.ui.viewmodel.QuestIndicator
import androidx.compose.ui.draw.alpha


@Composable
internal fun PrayerSheet(
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
    val selectedBone = selectedKey?.let { availableBones[it] }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(bottom = 32.dp),
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
        HorizontalDivider()

        if (selectedBone == null) {
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
                                Text(GameStrings.itemName(context, key), style = MaterialTheme.typography.bodyLarge)
                                val questIndicators = activeQuests["${Skills.PRAYER}:$key"] ?: emptyList()
                                if (questIndicators.isNotEmpty()) {
                                    val categories = questIndicators.groupBy { it.category }
                                    val sortedCategories = categories.entries.sortedBy { it.key }
                                    sortedCategories.forEach { (category, indicators) ->
                                        val emoji = if (category == QuestCategory.DAILY) "⏰" else "📜"
                                        val isCompletable = indicators.any { it.isCompletable }
                                        val alpha = if (isCompletable) 1.0f else 0.38f
                                        Text(
                                            text     = " $emoji",
                                            style    = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.alpha(alpha),
                                        )
                                    }
                                }
                            }
                            Text(
                                text  = stringResource(R.string.skills_bone_qty, bone.xpPerBone.toInt(), qty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(stringResource(R.string.btn_select), style = MaterialTheme.typography.labelMedium, color = GoldPrimary)
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        } else {
            // ── Quantity picker ──────────────────────────────────────────
            val inventoryMax = inventory[selectedKey] ?: 0
            val maxQty = minOf(inventoryMax, tierMaxQty)
            var qty by remember(selectedKey) { androidx.compose.runtime.mutableIntStateOf(maxQty.coerceAtLeast(1)) }
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
            QuestFillRow(questFills, qty, maxQty, modifier = Modifier.padding(horizontal = 16.dp)) { v -> qty = v; textValue = v.toString() }
            Spacer(Modifier.height(8.dp))

            Text(
                text     = projectedXpLabel(currentXp, (qty * selectedBone.xpPerBone).toLong()),
                style    = MaterialTheme.typography.bodyMedium,
                color    = GoldPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (sessionDurationMs > 0) {
                Text(
                    text     = "~${(qty.toLong() * (sessionDurationMs / 60)).formatDurationMs()}",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }

            Button(
                onClick  = { onStart(selectedKey!!, qty) },
                enabled  = !isStarting && qty > 0 && maxQty > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                if (isStarting) CircularProgressIndicator(Modifier.size(20.dp))
                else Text(if (hasActiveSession) stringResource(R.string.skills_add_to_queue) else stringResource(R.string.btn_start_burying))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Runecrafting sheet
// ---------------------------------------------------------------------------

