package com.fantasyidler.ui.screen

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.model.Skills
import com.fantasyidler.simulator.XpTable
import com.fantasyidler.data.model.WorkerTier
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.CraftableRecipe
import com.fantasyidler.ui.viewmodel.SheetState
import com.fantasyidler.ui.viewmodel.WorkerSkillsUiState
import com.fantasyidler.ui.viewmodel.WorkerSkillsViewModel
import com.fantasyidler.ui.viewmodel.xpProgressFraction
import com.fantasyidler.ui.viewmodel.nextLevelThreshold
import com.fantasyidler.ui.viewmodel.xpToNextLevel
import com.fantasyidler.ui.viewmodel.levelDisplay
import com.fantasyidler.ui.theme.ScaledSheetContent
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatDurationMs
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.toCountdown
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerSkillsScreen(
    initialSlot: Int = 1,
    onBack: () -> Unit = {},
    viewModel: WorkerSkillsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.setSelectedSlot(initialSlot) }

    AppBannerEffect(state.snackbarMessage, viewModel::snackbarConsumed)

    val tierLabel = when (state.currentWorker?.tier) {
        WorkerTier.LONG_LABORER -> stringResource(R.string.worker_long_laborer)
        WorkerTier.APPRENTICE   -> stringResource(R.string.worker_apprentice)
        WorkerTier.JOURNEYMAN   -> stringResource(R.string.worker_journeyman)
        WorkerTier.MASTER       -> stringResource(R.string.worker_master)
        null                    -> ""
    }
    val workerName = state.currentWorker?.dailyName ?: ""
    val screenTitle = if (workerName.isNotEmpty())
        stringResource(R.string.worker_skills_title, workerName)
    else
        stringResource(R.string.worker_skills_title_nav)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text(screenTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
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
            // Slot selector (only shown when both workers are hired)
            val w1 = state.hiredWorker
            val w2 = state.hiredWorker2
            if (w1 != null && w2 != null) {
                item {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = state.selectedSlot == 1,
                            onClick  = { viewModel.setSelectedSlot(1) },
                            label    = {
                                Text(w1.dailyName.ifEmpty {
                                    stringResource(R.string.worker_long_laborer)
                                })
                            },
                        )
                        FilterChip(
                            selected = state.selectedSlot == 2,
                            onClick  = { viewModel.setSelectedSlot(2) },
                            label    = {
                                Text(w2.dailyName.ifEmpty {
                                    stringResource(R.string.inn_slot2_header)
                                })
                            },
                        )
                    }
                }
            }

            // Tier badge
            if (tierLabel.isNotEmpty()) {
                item {
                    Text(
                        text  = stringResource(R.string.inn_worker_tier, tierLabel),
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            // Active worker session banner
            state.currentSession?.let { session ->
                item {
                    WorkerActiveSessionBanner(
                        session       = session,
                        showEndTime   = state.showSessionEndTime,
                        assignedItems = state.currentSessionAssignedItems,
                    )
                }
            }

            // Gathering skills (no farming, no agility)
            item { SectionHeader(stringResource(R.string.label_gathering_skills)) }
            items(Skills.GATHERING.filter { it != Skills.FARMING && it != Skills.AGILITY }) { key ->
                val efficiency = when (key) {
                    Skills.MINING      -> state.miningEfficiency
                    Skills.WOODCUTTING -> state.woodcuttingEfficiency
                    Skills.FISHING     -> state.fishingEfficiency
                    else               -> 1.0f
                }
                SkillRow(
                    skillKey       = key,
                    level          = state.skillLevels[key] ?: 1,
                    xp             = state.skillXp[key] ?: 0L,
                    isActive       = state.currentSession?.skillName == key && state.currentSession?.completed == false,
                    onClick        = { viewModel.onSkillTapped(key) },
                    toolEfficiency = efficiency,
                )
            }

            // Crafting skills
            item { SectionHeader(stringResource(R.string.label_crafting_skills)) }
            items(Skills.CRAFTING_SKILLS) { key ->
                SkillRow(
                    skillKey = key,
                    level    = state.skillLevels[key] ?: 1,
                    xp       = state.skillXp[key] ?: 0L,
                    isActive = state.currentSession?.skillName == key && state.currentSession?.completed == false,
                    onClick  = { viewModel.onSkillTapped(key) },
                )
            }

            // Support skills
            item { SectionHeader(stringResource(R.string.label_support_skills)) }
            item {
                SkillRow(
                    skillKey = Skills.AGILITY,
                    level    = state.skillLevels[Skills.AGILITY] ?: 1,
                    xp       = state.skillXp[Skills.AGILITY] ?: 0L,
                    isActive = state.currentSession?.skillName == Skills.AGILITY && state.currentSession?.completed == false,
                    onClick  = { viewModel.onSkillTapped(Skills.AGILITY) },
                )
            }

            // Prayer
            item { SectionHeader(stringResource(R.string.label_prayer)) }
            item {
                SkillRow(
                    skillKey = Skills.PRAYER,
                    level    = state.skillLevels[Skills.PRAYER] ?: 1,
                    xp       = state.skillXp[Skills.PRAYER] ?: 0L,
                    isActive = state.currentSession?.skillName == Skills.PRAYER && state.currentSession?.completed == false,
                    onClick  = { viewModel.onSkillTapped(Skills.PRAYER) },
                )
            }
        }
    }

    // Activity selection bottom sheet
    state.sheetSkill?.let { sheet ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissSheet,
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            ScaledSheetContent {
            // For workers: always pass hasActiveSession=true so button says "Add to Queue",
            // and isQueueFull=state.workerQueueFull.
            val isQueueFull = state.workerQueueFull
            when (sheet) {
                is SheetState.Mining -> MiningSheet(
                    ores              = sheet.ores,
                    isStarting        = false,
                    hasActiveSession  = true,
                    isQueueFull       = isQueueFull,
                    sessionDurationMs = state.gatheringDurationMs,
                    currentXp         = state.skillXp[Skills.MINING] ?: 0L,
                    efficiency        = state.miningEfficiency,
                    onSelect          = { viewModel.startMiningSession(it) },
                )
                is SheetState.Woodcutting -> WoodcuttingSheet(
                    trees             = sheet.trees,
                    isStarting        = false,
                    hasActiveSession  = true,
                    isQueueFull       = isQueueFull,
                    sessionDurationMs = state.gatheringDurationMs,
                    currentXp         = state.skillXp[Skills.WOODCUTTING] ?: 0L,
                    efficiency        = state.woodcuttingEfficiency,
                    onSelect          = { viewModel.startWoodcuttingSession(it) },
                )
                is SheetState.Fishing -> FishingSheet(
                    fish              = sheet.fish,
                    isStarting        = false,
                    hasActiveSession  = true,
                    isQueueFull       = isQueueFull,
                    sessionDurationMs = state.gatheringDurationMs,
                    currentXp         = state.skillXp[Skills.FISHING] ?: 0L,
                    efficiency        = state.fishingEfficiency,
                    onSelect          = { viewModel.startFishingSession(it) },
                )
                is SheetState.Agility -> AgilitySheet(
                    courses           = sheet.courses,
                    isStarting        = false,
                    hasActiveSession  = true,
                    isQueueFull       = isQueueFull,
                    sessionDurationMs = state.gatheringDurationMs,
                    currentXp         = state.skillXp[Skills.AGILITY] ?: 0L,
                    onSelect          = { viewModel.startAgilitySession(it) },
                )
                is SheetState.Firemaking -> FiremakingSheet(
                    availableLogs     = sheet.availableLogs,
                    inventory         = state.inventory,
                    currentXp         = state.skillXp[Skills.FIREMAKING] ?: 0L,
                    isStarting        = false,
                    hasActiveSession  = true,
                    isQueueFull       = isQueueFull,
                    sessionDurationMs = state.sessionDurationMs,
                    onStart           = { logKey, qty -> viewModel.startFiremakingSession(logKey, qty) },
                    context           = context,
                    craftLimit        = state.maxCraftQty,
                )
                is SheetState.Runecrafting -> RunecraftingSheet(
                    sheet             = sheet,
                    inventory         = state.inventory,
                    isStarting        = false,
                    hasActiveSession  = true,
                    isQueueFull       = isQueueFull,
                    sessionDurationMs = state.sessionDurationMs,
                    onStart           = { runeKey, qty, ashKey -> viewModel.startRunecraftingSession(runeKey, qty, ashKey) },
                    currentXp         = state.skillXp[Skills.RUNECRAFTING] ?: 0L,
                    tierMaxQty        = state.maxCraftQty,
                )
                is SheetState.Prayer -> PrayerSheet(
                    availableBones    = sheet.availableBones,
                    inventory         = sheet.inventory,
                    prayerLevel       = state.skillLevels[Skills.PRAYER] ?: 1,
                    currentXp         = state.skillXp[Skills.PRAYER] ?: 0L,
                    isStarting        = false,
                    hasActiveSession  = true,
                    isQueueFull       = isQueueFull,
                    sessionDurationMs = state.sessionDurationMs,
                    onStart           = viewModel::startPrayerSession,
                    tierMaxQty        = state.maxCraftQty,
                )
                is SheetState.Crafting -> {
                    WorkerCraftSkillSheet(
                        skillName  = sheet.skillName,
                        state      = state,
                        viewModel  = viewModel,
                        isQueueFull = isQueueFull,
                        context    = context,
                        onDismiss  = viewModel::dismissSheet,
                    )
                }
                SheetState.Mercantile -> {}
                SheetState.Farming   -> {}
                is SheetState.Thieving -> ThievingSheet(
                    npcs              = sheet.npcs,
                    thievingLevel     = state.skillLevels[Skills.THIEVING] ?: 1,
                    currentXp         = state.skillXp[Skills.THIEVING] ?: 0L,
                    isStarting        = false,
                    hasActiveSession  = true,
                    isQueueFull       = isQueueFull,
                    sessionDurationMs = state.gatheringDurationMs,
                    context           = context,
                    onSelect          = { viewModel.startThievingSession(it) },
                )
                SheetState.ComingSoon -> ComingSoonSheet()
            }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Worker active session banner (read-only timer, managed from HomeScreen)
// ---------------------------------------------------------------------------

@Composable
private fun WorkerActiveSessionBanner(
    session: com.fantasyidler.data.model.SkillSession,
    showEndTime: Boolean = true,
    assignedItems: Map<String, Int> = emptyMap(),
) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val endsAt = session.endsAt
    LaunchedEffect(endsAt) {
        while (System.currentTimeMillis() < endsAt) {
            delay(1_000L)
            now = System.currentTimeMillis()
        }
    }
    val isDone = session.completed || now >= endsAt
    val skillLabel = GameStrings.skillName(context, session.skillName)
    val activityLabel = session.activityKey
        .replace('_', ' ')
        .replaceFirstChar { it.uppercase() }
        .takeIf { session.activityKey.isNotEmpty() }

    Surface(
        color    = if (isDone) MaterialTheme.colorScheme.primaryContainer
                   else MaterialTheme.colorScheme.secondaryContainer,
        shape    = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text  = if (isDone) stringResource(R.string.worker_session_complete)
                        else stringResource(R.string.worker_session_active),
                style = MaterialTheme.typography.labelMedium,
                color = if (isDone) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(skillLabel)
                    if (activityLabel != null) append(" — $activityLabel")
                },
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = if (isDone) MaterialTheme.colorScheme.onPrimaryContainer
                             else MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (!isDone) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = remember(now, showEndTime) { endsAt.toCountdown(context, showEndTime) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            if (assignedItems.isNotEmpty()) {
                val assignedTemplate = stringResource(R.string.worker_session_assigned)
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = assignedItems.entries.joinToString("  ") { (key, qty) ->
                        assignedTemplate.format("%,d".format(qty), GameStrings.itemName(context, key))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDone) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text  = stringResource(R.string.worker_manage_from_home),
                style = MaterialTheme.typography.bodySmall,
                color = (if (isDone) MaterialTheme.colorScheme.onPrimaryContainer
                         else MaterialTheme.colorScheme.onSecondaryContainer).copy(alpha = 0.7f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Worker queue banner
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Worker craft skill sheet
// ---------------------------------------------------------------------------

@Composable
private fun WorkerCraftSkillSheet(
    skillName: String,
    state: WorkerSkillsUiState,
    viewModel: WorkerSkillsViewModel,
    isQueueFull: Boolean,
    context: android.content.Context,
    onDismiss: () -> Unit,
) {
    val allRecipes: List<CraftableRecipe> = when (skillName) {
        Skills.SMITHING      -> viewModel.smithingRecipes
        Skills.COOKING       -> viewModel.cookingRecipes
        Skills.FLETCHING     -> viewModel.fletchingRecipes
        Skills.HERBLORE      -> viewModel.herbloreRecipes
        Skills.CONSTRUCTION  -> viewModel.constructionRecipes
        else                 -> viewModel.jewelleryRecipes
    }

    var onlyCraftable    by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedTier     by remember { mutableStateOf<String?>(null) }

    val categories = remember(allRecipes) {
        allRecipes.map { it.category }.filter { it.isNotEmpty() }.distinct().sorted()
    }
    val categoryFiltered = if (selectedCategory == null) allRecipes
                           else allRecipes.filter { it.category == selectedCategory }
    val tiers = remember(categoryFiltered) {
        categoryFiltered.map { it.tier }.filter { it.isNotEmpty() }.distinct().sorted()
    }
    val recipes = categoryFiltered
        .filter { selectedTier == null || it.tier == selectedTier }
        .let { list ->
            if (onlyCraftable) list.filter { workerMeetsLevel(state, it) && state.maxCraftable(it) > 0 }
            else list
        }

    val selected = state.selectedRecipe

    if (selected != null) {
        WorkerCraftQuantityContent(
            recipe        = selected,
            state         = state,
            isQueueFull   = isQueueFull,
            context       = context,
            onSetQuantity = { viewModel.setQuantity(it, minOf(state.maxCraftable(selected), state.maxCraftQty)) },
            onSetAsh      = if (selected.skillName == Skills.HERBLORE) viewModel::setHerbloreAsh else null,
            onCraft       = viewModel::craft,
            onBack        = viewModel::dismissRecipe,
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text     = GameStrings.skillName(context, skillName),
                    style    = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text  = stringResource(R.string.skills_only_craftable),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked         = onlyCraftable,
                    onCheckedChange = { onlyCraftable = it },
                )
            }
            HorizontalDivider()
            Text(
                text     = GameStrings.skillDesc(context, skillName),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            )
            if (categories.size > 1) {
                Row(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick  = { selectedCategory = null; selectedTier = null },
                        label    = { Text(stringResource(R.string.skills_filter_all)) },
                    )
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick  = {
                                val newCat = if (selectedCategory == cat) null else cat
                                val newTiers = (if (newCat == null) allRecipes else allRecipes.filter { it.category == newCat })
                                    .map { it.tier }.filter { it.isNotEmpty() }.distinct()
                                selectedCategory = newCat
                                if (selectedTier != null && selectedTier !in newTiers) selectedTier = null
                            },
                            label    = { Text(cat) },
                        )
                    }
                }
            }
            if (tiers.size > 1) {
                Row(
                    modifier            = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedTier == null,
                        onClick  = { selectedTier = null },
                        label    = { Text(stringResource(R.string.skills_filter_all)) },
                    )
                    tiers.forEach { tier ->
                        FilterChip(
                            selected = selectedTier == tier,
                            onClick  = { selectedTier = if (selectedTier == tier) null else tier },
                            label    = { Text(tier) },
                        )
                    }
                }
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                items(recipes) { recipe ->
                    WorkerCraftRecipeRow(
                        recipe  = recipe,
                        state   = state,
                        context = context,
                        onTap   = { viewModel.openRecipe(recipe) },
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

private fun workerMeetsLevel(state: WorkerSkillsUiState, recipe: CraftableRecipe): Boolean =
    (state.skillLevels[recipe.skillName] ?: 1) >= recipe.levelRequired

@Composable
private fun WorkerCraftRecipeRow(
    recipe: CraftableRecipe,
    state: WorkerSkillsUiState,
    context: android.content.Context,
    onTap: () -> Unit,
) {
    val meetsLvl = workerMeetsLevel(state, recipe)
    val canMake  = state.maxCraftable(recipe)
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
                text       = GameStrings.itemName(context, recipe.outputKey),
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = if (enabled) MaterialTheme.colorScheme.onSurface else dim,
            )
            if (recipe.outputQty > 1) {
                Text(
                    text  = "×${recipe.outputQty} per craft",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) MaterialTheme.colorScheme.primary else dim,
                )
            }
            val matText = recipe.materials.entries.joinToString("  ") { (item, qty) ->
                "${GameStrings.itemName(context, item)} ${state.inventory[item] ?: 0}/$qty"
            }
            Text(
                text  = matText,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else dim,
            )
            recipe.outputCombatStyle?.let { style ->
                Text(
                    text  = "${context.getString(R.string.label_combat_style)}: ${style.replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else dim,
                )
            }
            val statParts = buildList {
                if (recipe.outputAttackBonus   > 0) add("+${recipe.outputAttackBonus} Atk")
                if (recipe.outputStrengthBonus > 0) add("+${recipe.outputStrengthBonus} Str")
                if (recipe.outputDefenseBonus  > 0) add("+${recipe.outputDefenseBonus} Def")
                if (recipe.outputHealingValue  > 0) add("Heals ${recipe.outputHealingValue} HP")
                if (recipe.outputDamage        > 0) add("+${recipe.outputDamage} Ranged Str")
            }
            if (statParts.isNotEmpty()) {
                Text(
                    text  = statParts.joinToString("  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else dim,
                )
            }
            if (recipe.effects.isNotEmpty()) {
                Text(
                    text  = recipe.effects.entries.joinToString("  ") { (stat, bonus) ->
                        "+$bonus ${stat.replaceFirstChar { it.uppercase() }}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) MaterialTheme.colorScheme.primary else dim,
                )
            }
            if (recipe.outputRequirements.isNotEmpty()) {
                recipe.outputRequirements.forEach { (skill, lvl) ->
                    val have       = state.skillLevels[skill] ?: 1
                    val skillLabel = GameStrings.skillName(context, skill)
                    Text(
                        text  = stringResource(R.string.skills_req_with_have, lvl, skillLabel, have, skillLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (have >= lvl) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
            }
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
                    text  = stringResource(R.string.worker_no_mats),
                    style = MaterialTheme.typography.labelSmall,
                    color = dim,
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun WorkerCraftQuantityContent(
    recipe: CraftableRecipe,
    state: WorkerSkillsUiState,
    isQueueFull: Boolean,
    context: android.content.Context,
    onSetQuantity: (Int) -> Unit,
    onSetAsh: ((String?) -> Unit)?,
    onCraft: () -> Unit,
    onBack: () -> Unit,
) {
    val qty      = state.craftQuantity
    val max      = minOf(state.maxCraftable(recipe), state.maxCraftQty)
    val totalXp  = recipe.xpPerItem * qty
    var textValue by remember(qty) { mutableStateOf(qty.toString()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.btn_back_arrow)) }
        Text(
            text       = GameStrings.itemName(context, recipe.outputKey),
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
                    val parsed   = filtered.toIntOrNull()
                    if (parsed != null) {
                        val clamped = parsed.coerceIn(1, max.coerceAtLeast(1))
                        textValue = clamped.toString()
                        onSetQuantity(clamped)
                    } else {
                        textValue = filtered
                    }
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
        Spacer(Modifier.height(8.dp))
        QtyQuickButtons(qty, max) { onSetQuantity(it) }
        Spacer(Modifier.height(8.dp))
        Text(
            text     = projectedXpLabel(state.skillXp[recipe.skillName] ?: 0L, totalXp.toLong()),
            style    = MaterialTheme.typography.bodySmall,
            color    = GoldPrimary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (state.sessionDurationMs > 0) {
            Text(
                text     = "~${(qty.toLong() * (state.sessionDurationMs / 60)).formatDurationMs()}",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        if (onSetAsh != null) {
            val ashTiers = listOf("ashes","oak_ashes","willow_ashes","maple_ashes","yew_ashes","magic_ashes","redwood_ashes")
            val availableAshes = ashTiers.filter { (state.inventory[it] ?: 0) >= qty }
            if (availableAshes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.catalyst_optional), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                val selectedAsh = state.herbloreAshKey
                (listOf(null) + availableAshes).forEach { ashKey ->
                    Row(
                        modifier              = Modifier.fillMaxWidth().clickable { onSetAsh(ashKey) }.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(
                            text       = if (ashKey == null) stringResource(R.string.catalyst_none) else GameStrings.itemName(context, ashKey),
                            style      = MaterialTheme.typography.bodyMedium,
                            color      = if (selectedAsh == ashKey) GoldPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (selectedAsh == ashKey) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        if (ashKey != null) {
                            Text(
                                text  = "×${state.inventory[ashKey] ?: 0}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (state.herbloreAshKey != null) {
                    Text(stringResource(R.string.catalyst_enhanced_output), style = MaterialTheme.typography.labelSmall, color = GoldPrimary)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick  = onCraft,
            enabled  = !isQueueFull && max > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.skills_add_to_queue))
        }
    }
}

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
