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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
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
import com.fantasyidler.data.json.AgilityCourseData
import com.fantasyidler.data.json.BoneData
import com.fantasyidler.data.json.LogData
import com.fantasyidler.data.json.OreData
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
import com.fantasyidler.ui.viewmodel.SessionResult
import com.fantasyidler.ui.viewmodel.SheetState
import com.fantasyidler.ui.viewmodel.levelDisplay
import com.fantasyidler.ui.viewmodel.SkillsUiState
import com.fantasyidler.ui.viewmodel.SkillsViewModel
import com.fantasyidler.ui.viewmodel.xpProgressFraction
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.toCountdown
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    viewModel: SkillsViewModel  = hiltViewModel(),
    craftingViewModel: CraftingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.snackbarConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.nav_skills)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Active session banner
            state.activeSession?.let { session ->
                item {
                    ActiveSessionBanner(
                        skillName      = GameStrings.skillName(context, session.skillName),
                        activityKey    = session.activityKey,
                        endsAt         = session.endsAt,
                        completed      = session.completed,
                        onCollect      = viewModel::collectSession,
                        onAbandon      = viewModel::abandonSession,
                        onDebugFinish  = viewModel::debugFinishSession,
                    )
                }
            }

            // Gathering skills
            item { SectionHeader(stringResource(R.string.label_gathering_skills)) }
            items(Skills.GATHERING) { key ->
                SkillRow(
                    skillKey = key,
                    level    = state.skillLevels[key] ?: 1,
                    xp       = state.skillXp[key] ?: 0L,
                    isActive = state.activeSession?.skillName == key && state.activeSession?.completed == false,
                    onClick  = { viewModel.onSkillTapped(key) },
                )
            }

            // Crafting skills
            item { SectionHeader(stringResource(R.string.label_crafting_skills)) }
            items(Skills.CRAFTING_SKILLS) { key ->
                SkillRow(
                    skillKey = key,
                    level    = state.skillLevels[key] ?: 1,
                    xp       = state.skillXp[key] ?: 0L,
                    isActive = state.activeSession?.skillName == key && state.activeSession?.completed == false,
                    onClick  = { viewModel.onSkillTapped(key) },
                )
            }

            // Prayer
            item { SectionHeader(stringResource(R.string.label_prayer)) }
            item {
                SkillRow(
                    skillKey = Skills.PRAYER,
                    level    = state.skillLevels[Skills.PRAYER] ?: 1,
                    xp       = state.skillXp[Skills.PRAYER] ?: 0L,
                    isActive = state.activeSession?.skillName == Skills.PRAYER && state.activeSession?.completed == false,
                    onClick  = { viewModel.onSkillTapped(Skills.PRAYER) },
                )
            }
        }
    }

    // Session result bottom sheet
    state.sessionResult?.let { result ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::resultConsumed,
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            SessionResultSheet(
                result    = result,
                context   = context,
                onDismiss = viewModel::resultConsumed,
            )
        }
    }

    // Activity selection bottom sheet
    state.sheetSkill?.let { sheet ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.dismissSheet()
                craftingViewModel.dismissRecipe()
            },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            when (sheet) {
                is SheetState.Mining -> MiningSheet(
                    ores       = sheet.ores,
                    isStarting = state.startingSession,
                    onSelect   = { oreKey -> viewModel.startMiningSession(oreKey) },
                )
                is SheetState.Woodcutting -> WoodcuttingSheet(
                    trees      = sheet.trees,
                    isStarting = state.startingSession,
                    onSelect   = { treeKey -> viewModel.startWoodcuttingSession(treeKey) },
                )
                SheetState.Fishing -> FishingSheet(
                    state      = state,
                    isStarting = state.startingSession,
                    onStart    = viewModel::startFishingSession,
                )
                is SheetState.Agility -> AgilitySheet(
                    courses    = sheet.courses,
                    isStarting = state.startingSession,
                    onSelect   = { courseKey -> viewModel.startAgilitySession(courseKey) },
                )
                is SheetState.Firemaking -> FiremakingSheet(
                    availableLogs = sheet.availableLogs,
                    isStarting    = state.startingSession,
                    onSelect      = { logKey -> viewModel.startFiremakingSession(logKey) },
                    context       = context,
                )
                SheetState.Runecrafting -> RunecraftingSheet(
                    state      = state,
                    isStarting = state.startingSession,
                    onStart    = viewModel::startRunecraftingSession,
                )
                is SheetState.Prayer -> PrayerSheet(
                    availableBones = sheet.availableBones,
                    inventory      = sheet.inventory,
                    prayerLevel    = state.skillLevels[Skills.PRAYER] ?: 1,
                    isStarting     = state.startingSession,
                    onStart        = viewModel::startPrayerSession,
                )
                is SheetState.Crafting -> {
                    val craftState by craftingViewModel.uiState.collectAsState()
                    CraftSkillSheet(
                        skillName         = sheet.skillName,
                        craftState        = craftState,
                        craftingViewModel = craftingViewModel,
                        context           = context,
                        onDismiss         = {
                            viewModel.dismissSheet()
                            craftingViewModel.dismissRecipe()
                        },
                    )
                }
                SheetState.ComingSoon -> ComingSoonSheet()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Active session banner
// ---------------------------------------------------------------------------

@Composable
private fun ActiveSessionBanner(
    skillName: String,
    activityKey: String,
    endsAt: Long,
    completed: Boolean,
    onCollect: () -> Unit,
    onAbandon: () -> Unit,
    onDebugFinish: () -> Unit = {},
) {
    // Tick every second so the countdown stays live.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (System.currentTimeMillis() < endsAt) {
            delay(1_000L)
            now = System.currentTimeMillis()
        }
    }

    Surface(
        color    = MaterialTheme.colorScheme.primaryContainer,
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text  = if (completed) stringResource(R.string.label_session_complete)
                        else stringResource(R.string.label_session_active),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(skillName)
                    if (activityKey.isNotEmpty()) {
                        append(" — ")
                        append(activityKey.replace('_', ' ').replaceFirstChar { it.uppercase() })
                    }
                },
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(8.dp))
            if (!completed) {
                Text(
                    text  = remember(now) { endsAt.toCountdown() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = onAbandon) {
                        Text(stringResource(R.string.btn_abandon_session))
                    }
                    if (BuildConfig.DEBUG) {
                        TextButton(onClick = onDebugFinish) {
                            Text("[Debug] Finish Now")
                        }
                    }
                }
            } else {
                Button(onClick = onCollect, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.btn_collect_results))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Skill row
// ---------------------------------------------------------------------------

@Composable
private fun SkillRow(
    skillKey: String,
    level: Int,
    xp: Long,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val context  = LocalContext.current
    val name     = GameStrings.skillName(context, skillKey)
    val progress = xpProgressFraction(xp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Level badge
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) GoldPrimary
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = level.toString(),
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color      = if (isActive) MaterialTheme.colorScheme.surface
                             else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(
                modifier             = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text       = name,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (isActive) {
                    Text(
                        text  = stringResource(R.string.label_training),
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldPrimary,
                    )
                } else {
                    Text(
                        text  = "${xp.formatXp()} XP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color    = GoldPrimary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Section header
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String) {
    Column {
        HorizontalDivider()
        Text(
            text     = title.uppercase(Locale.getDefault()),
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Activity selection sheets
// ---------------------------------------------------------------------------

@Composable
private fun MiningSheet(
    ores: Map<String, OreData>,
    isStarting: Boolean,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            text     = stringResource(R.string.label_choose_activity),
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider()
        ores.entries
            .sortedBy { it.value.levelRequired }
            .forEach { (key, ore) ->
                ActivityRow(
                    name       = GameStrings.itemName(context, key),
                    detail     = "Lv. ${ore.levelRequired}  •  ${ore.xpPerOre} XP/ore",
                    isStarting = isStarting,
                    onClick    = { onSelect(key) },
                )
            }
    }
}

@Composable
private fun WoodcuttingSheet(
    trees: Map<String, TreeData>,
    isStarting: Boolean,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            text     = stringResource(R.string.label_choose_activity),
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider()
        trees.entries
            .sortedBy { it.value.levelRequired }
            .forEach { (key, tree) ->
                ActivityRow(
                    name       = GameStrings.itemName(context, tree.logName),
                    detail     = "Lv. ${tree.levelRequired}  •  ${tree.xpPerLog} XP/log",
                    isStarting = isStarting,
                    onClick    = { onSelect(key) },
                )
            }
    }
}

@Composable
private fun FishingSheet(
    state: SkillsUiState,
    isStarting: Boolean,
    onStart: () -> Unit,
) {
    val fishLevel = state.skillLevels[Skills.FISHING] ?: 1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text  = stringResource(R.string.skill_fishing_name),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = "Level $fishLevel  •  Level-appropriate catches",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick  = onStart,
            enabled  = !isStarting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isStarting) {
                CircularProgressIndicator(Modifier.size(20.dp))
            } else {
                Text(stringResource(R.string.btn_start_session))
            }
        }
    }
}

@Composable
private fun ComingSoonSheet() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text  = stringResource(R.string.label_coming_soon),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActivityRow(
    name: String,
    detail: String,
    isStarting: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isStarting, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text  = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isStarting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        } else {
            Text(
                text  = stringResource(R.string.btn_start_session),
                style = MaterialTheme.typography.labelMedium,
                color = GoldPrimary,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ---------------------------------------------------------------------------
// Agility sheet
// ---------------------------------------------------------------------------

@Composable
private fun AgilitySheet(
    courses: Map<String, AgilityCourseData>,
    isStarting: Boolean,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            text     = stringResource(R.string.label_choose_activity),
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider()
        courses.entries
            .sortedBy { it.value.levelRequired }
            .forEach { (key, course) ->
                ActivityRow(
                    name       = course.displayName,
                    detail     = "Lv. ${course.levelRequired}  •  ${course.xpPerSuccess} XP/lap",
                    isStarting = isStarting,
                    onClick    = { onSelect(key) },
                )
            }
    }
}

// ---------------------------------------------------------------------------
// Firemaking sheet
// ---------------------------------------------------------------------------

@Composable
private fun FiremakingSheet(
    availableLogs: Map<String, LogData>,
    isStarting: Boolean,
    onSelect: (String) -> Unit,
    context: android.content.Context,
) {
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            text     = stringResource(R.string.label_choose_activity),
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider()
        if (availableLogs.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = "No logs in inventory",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            availableLogs.entries
                .sortedBy { it.value.levelRequired }
                .forEach { (key, log) ->
                    ActivityRow(
                        name       = GameStrings.itemName(context, key),
                        detail     = "Lv. ${log.levelRequired}  •  ${log.xpPerLog} XP/log",
                        isStarting = isStarting,
                        onClick    = { onSelect(key) },
                    )
                }
        }
    }
}

// ---------------------------------------------------------------------------
// Prayer sheet
// ---------------------------------------------------------------------------

@Composable
private fun PrayerSheet(
    availableBones: Map<String, BoneData>,
    inventory: Map<String, Int>,
    prayerLevel: Int,
    isStarting: Boolean,
    onStart: (boneKey: String, qty: Int) -> Unit,
) {
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val selectedBone = selectedKey?.let { availableBones[it] }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
    ) {
        Text(
            text     = "Prayer",
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider()

        if (selectedBone == null) {
            // ── Bone selection ───────────────────────────────────────────
            Text(
                text     = "Level $prayerLevel  •  Select bones to bury",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (availableBones.isEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = "No bones in inventory",
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
                            Text(bone.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text  = "${bone.xpPerBone.toInt()} XP each  •  $qty in inventory",
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
            val maxQty = inventory[selectedKey] ?: 0
            var qty by remember(selectedKey) { androidx.compose.runtime.mutableIntStateOf(maxQty.coerceAtLeast(1)) }
            var textValue by remember(selectedKey) { mutableStateOf(maxQty.coerceAtLeast(1).toString()) }

            TextButton(
                onClick  = { selectedKey = null },
                modifier = Modifier.padding(start = 4.dp),
            ) { Text(stringResource(R.string.btn_back_arrow)) }

            Text(
                text     = selectedBone.displayName,
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                text     = "${selectedBone.xpPerBone.toInt()} XP each  •  $maxQty available",
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
                        textValue = filtered
                        filtered.toIntOrNull()?.let { qty = it.coerceIn(1, maxQty.coerceAtLeast(1)) }
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
                    modifier     = Modifier.width(90.dp),
                )

                IconButton(
                    onClick  = { qty = (qty + 1).coerceAtMost(maxQty.coerceAtLeast(1)); textValue = qty.toString() },
                    enabled  = qty < maxQty,
                ) { Icon(Icons.Filled.Add, contentDescription = "Increase") }
            }

            Text(
                text     = "+${(qty * selectedBone.xpPerBone).toInt()} XP total",
                style    = MaterialTheme.typography.bodyMedium,
                color    = GoldPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Button(
                onClick  = { onStart(selectedKey!!, qty) },
                enabled  = !isStarting && qty > 0 && maxQty > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                if (isStarting) CircularProgressIndicator(Modifier.size(20.dp))
                else Text(stringResource(R.string.btn_start_burying))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Runecrafting sheet
// ---------------------------------------------------------------------------

@Composable
private fun RunecraftingSheet(
    state: SkillsUiState,
    isStarting: Boolean,
    onStart: () -> Unit,
) {
    val rcLevel = state.skillLevels[com.fantasyidler.data.model.Skills.RUNECRAFTING] ?: 1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text  = stringResource(R.string.skill_runecrafting_name),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = "Level $rcLevel  •  Level-appropriate runes",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick  = onStart,
            enabled  = !isStarting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isStarting) {
                CircularProgressIndicator(Modifier.size(20.dp))
            } else {
                Text(stringResource(R.string.btn_start_session))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Session result sheet
// ---------------------------------------------------------------------------

@Composable
private fun SessionResultSheet(
    result: SessionResult,
    context: android.content.Context,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
        Text(
            text     = stringResource(R.string.label_session_results),
            style    = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text  = GameStrings.skillName(context, result.skillName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        // XP gained
        ResultRow(
            label = stringResource(R.string.label_xp_gained),
            value = "+${result.xpGained.formatXp()} XP",
            valueColor = GoldPrimary,
        )

        // Level ups
        if (result.levelUps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text  = stringResource(R.string.label_level_ups),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            result.levelUps.forEach { lvl ->
                Text(
                    text  = "  Level $lvl reached!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GoldPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Items collected
        if (result.itemsGained.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text  = stringResource(R.string.label_items_collected),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            result.itemsGained.entries
                .sortedByDescending { it.value }
                .forEach { (key, qty) ->
                    ResultRow(
                        label      = GameStrings.itemName(context, key),
                        value      = "×$qty",
                        valueColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.btn_close))
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
// Crafting skill sheet (Smithing / Cooking / Fletching / Jewelry)
// Shown inline when tapping a crafting skill row on the Skills screen.
// ---------------------------------------------------------------------------

@Composable
private fun CraftSkillSheet(
    skillName: String,
    craftState: CraftingUiState,
    craftingViewModel: CraftingViewModel,
    context: android.content.Context,
    onDismiss: () -> Unit,
) {
    val recipes: List<CraftableRecipe> = when (skillName) {
        Skills.SMITHING  -> craftingViewModel.smithingRecipes
        Skills.COOKING   -> craftingViewModel.cookingRecipes
        Skills.FLETCHING -> craftingViewModel.fletchingRecipes
        else             -> craftingViewModel.jewelleryRecipes
    }

    val selected = craftState.selectedRecipe

    if (selected != null) {
        CraftQuantityContent(
            recipe  = selected,
            state   = craftState,
            context = context,
            onSetQuantity = { craftingViewModel.setQuantity(it, craftState.maxCraftable(selected)) },
            onCraft = craftingViewModel::craft,
            onBack  = craftingViewModel::dismissRecipe,
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Text(
                text     = GameStrings.skillName(context, skillName),
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxWidth()) {
                items(recipes) { recipe ->
                    CraftRecipeRow(
                        recipe    = recipe,
                        craftState = craftState,
                        context   = context,
                        onTap     = { craftingViewModel.openRecipe(recipe) },
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun CraftRecipeRow(
    recipe: CraftableRecipe,
    craftState: CraftingUiState,
    context: android.content.Context,
    onTap: () -> Unit,
) {
    val meetsLvl = craftState.meetsLevel(recipe)
    val canMake  = craftState.maxCraftable(recipe)
    val enabled  = meetsLvl && canMake > 0
    val dim      = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text       = recipe.displayName,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = if (enabled) MaterialTheme.colorScheme.onSurface else dim,
            )
            val matText = recipe.materials.entries.joinToString("  ") { (item, qty) ->
                "${GameStrings.itemName(context, item)} ${craftState.inventory[item] ?: 0}/$qty"
            }
            Text(
                text  = matText,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else dim,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            when {
                !meetsLvl  -> Text(
                    text  = "Lv. ${recipe.levelRequired}",
                    style = MaterialTheme.typography.labelSmall,
                    color = dim,
                )
                canMake > 0 -> {
                    Text(
                        text       = "×$canMake",
                        style      = MaterialTheme.typography.labelMedium,
                        color      = GoldPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text  = "${recipe.xpPerItem.toInt()} XP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> Text(
                    text  = "No mats",
                    style = MaterialTheme.typography.labelSmall,
                    color = dim,
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun CraftQuantityContent(
    recipe: CraftableRecipe,
    state: CraftingUiState,
    context: android.content.Context,
    onSetQuantity: (Int) -> Unit,
    onCraft: () -> Unit,
    onBack: () -> Unit,
) {
    val qty     = state.craftQuantity
    val max     = state.maxCraftable(recipe)
    val totalXp = recipe.xpPerItem * qty
    var textValue by remember(qty) { mutableStateOf(qty.toString()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Back") }
        Text(
            text       = recipe.displayName,
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))

        Text(
            text  = stringResource(R.string.label_ingredients),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        recipe.materials.forEach { (item, perItem) ->
            val needed = perItem * qty
            val have   = state.inventory[item] ?: 0
            Row(
                modifier              = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(GameStrings.itemName(context, item), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text  = "$needed (have $have)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (have >= needed) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onSetQuantity(qty - 1) }, enabled = qty > 1) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease")
            }
            OutlinedTextField(
                value         = textValue,
                onValueChange = { new ->
                    val filtered = new.filter { it.isDigit() }
                    textValue = filtered
                    filtered.toIntOrNull()?.let { onSetQuantity(it) }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction    = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val parsed = textValue.toIntOrNull()?.coerceIn(1, max.coerceAtLeast(1)) ?: 1
                        onSetQuantity(parsed)
                        textValue = parsed.toString()
                    },
                ),
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                ),
                singleLine = true,
                modifier   = Modifier.width(90.dp),
            )
            IconButton(onClick = { onSetQuantity(qty + 1) }, enabled = qty < max) {
                Icon(Icons.Filled.Add, contentDescription = "Increase")
            }
        }
        Text(
            text     = "+${totalXp.toInt()} XP total",
            style    = MaterialTheme.typography.bodySmall,
            color    = GoldPrimary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCraft, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.btn_craft))
        }
    }
}
