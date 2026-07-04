package com.fantasyidler.ui.screen

import android.widget.Toast
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.json.SkillingDungeonData
import com.fantasyidler.data.json.ConstructionRecipe
import com.fantasyidler.data.json.ThievingNpcData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.Achievement
import com.fantasyidler.ui.viewmodel.AchievementsViewModel
import com.fantasyidler.ui.viewmodel.ArmoryViewModel
import com.fantasyidler.ui.viewmodel.BestiaryViewModel
import com.fantasyidler.ui.viewmodel.InventoryCategory
import com.fantasyidler.ui.viewmodel.InventoryViewModel
import com.fantasyidler.ui.viewmodel.SettingsViewModel
import com.fantasyidler.ui.viewmodel.slotDisplayName
import com.fantasyidler.ui.viewmodel.xpProgressFraction
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.stringByName
import com.fantasyidler.util.toTitleCase

private val SKILL_CATEGORY_GROUPS: List<Pair<Int, List<String>>> = listOf(
    R.string.label_gathering      to listOf("mining", "fishing", "woodcutting", "farming", "thieving"),
    R.string.label_crafting       to listOf("smithing", "cooking", "fletching", "crafting", "runecrafting", "herblore", "firemaking", "construction"),
    R.string.label_support_skills to listOf("prayer", "mercantile", "agility", "slayer"),
    R.string.label_combat         to listOf("attack", "strength", "defense", "ranged", "magic", "hitpoints"),
)

private data class UnlockMilestone(val level: Int, val description: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel:           InventoryViewModel    = hiltViewModel(),
    achievementsVm:      AchievementsViewModel = hiltViewModel(),
    bestiaryVm:          BestiaryViewModel     = hiltViewModel(),
    armoryVm:            ArmoryViewModel       = hiltViewModel(),
    settingsVm:          SettingsViewModel     = hiltViewModel(),
    onNavigateToCombat:  () -> Unit            = {},
) {
    val state         by viewModel.uiState.collectAsState()
    val achState      by achievementsVm.uiState.collectAsState()
    val profileLayout by settingsVm.profileLayout.collectAsState()
    val context   = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it, withDismissAction = true)
            viewModel.snackbarConsumed()
        }
    }
    val tabs = listOf(
        stringResource(R.string.label_skills),
        stringResource(R.string.label_inventory),
        stringResource(R.string.label_equipment),
        stringResource(R.string.label_pets),
        stringResource(R.string.label_achievements),
        stringResource(R.string.label_notes),
        stringResource(R.string.label_bestiary),
        stringResource(R.string.armory_tab),
        stringResource(R.string.tab_bonuses),
        stringResource(R.string.label_banners),
    )
    var selectedTab  by remember { mutableIntStateOf(0) }
    var showEditSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar       = { TopAppBar(title = { Text(stringResource(R.string.nav_profile)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            CoinsBanner(state.coins)

            // ── Character identity header ────────────────────────────────
            Surface(
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text       = state.characterName.ifBlank { stringResource(R.string.profile_unnamed) },
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        val subtitle = buildString {
                            if (state.characterRace.isNotBlank()) append(state.characterRace)
                            if (state.characterRace.isNotBlank() && state.characterGender.isNotBlank()) append(" • ")
                            if (state.characterGender.isNotBlank()) append(state.characterGender)
                        }
                        if (subtitle.isNotBlank()) {
                            Text(
                                text  = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { showEditSheet = true }) {
                        Icon(
                            imageVector        = Icons.Filled.Edit,
                            contentDescription = "Edit character",
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Surface(
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text  = stringResource(R.string.label_total_level),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text       = state.totalLevel.toString(),
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            HorizontalDivider()

            val tabContent: @Composable (Int) -> Unit = { tab ->
                when (tab) {
                    0    -> SkillsTab(state.skillLevels, state.skillXp, context, viewModel)
                    1    -> InventoryTab(state.inventory, context, viewModel::categoryFor)
                    2    -> EquipmentTab(
                        equipped           = state.equipped,
                        context            = context,
                        onSlotTap          = viewModel::openSlotPicker,
                        onUnequip          = viewModel::unequip,
                        onNavigateToCombat = onNavigateToCombat,
                    )
                    3    -> PetsTab(allPets = viewModel.allPets, ownedPetIds = state.ownedPetIds)
                    4    -> AchievementsTab(achState.byGroup, achState.unlockedCount, achState.totalCount)
                    5    -> NotesTab(
                        skillingDungeons     = viewModel.allSkillingDungeons,
                        skillingDungeonNotes = state.skillingDungeonNotes,
                        unlockedDungeons     = state.unlockedDungeons,
                    )
                    7    -> ArmoryTab(viewModel = armoryVm)
                    8    -> BonusesTab(state, viewModel.allEquipment, viewModel.allPets)
                    9    -> BannersTab(state.seasonalBanners)
                    else -> BestiaryTab(viewModel = bestiaryVm)
                }
            }

            if (profileLayout == "tabs") {
                TabsLayout(
                    tabs        = tabs,
                    selectedTab = selectedTab,
                    onTabSelect = { selectedTab = it },
                    modifier    = Modifier.weight(1f),
                    content     = tabContent,
                )
            } else {
                RailLayout(
                    tabs        = tabs,
                    selectedTab = selectedTab,
                    onTabSelect = { selectedTab = it },
                    modifier    = Modifier.weight(1f),
                    content     = tabContent,
                )
            }
        }
    }

    // Equip-picker sheet
    state.pickingSlot?.let { slot ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissSlotPicker,
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            EquipPickerSheet(
                slot      = slot,
                candidates = state.candidatesFor(slot, viewModel.allEquipment),
                context   = context,
                onEquip   = { itemKey -> viewModel.equip(itemKey, slot) },
                onDismiss = viewModel::dismissSlotPicker,
            )
        }
    }

    // Character edit sheet
    if (showEditSheet) {
        CharacterSetupSheet(
            isFirstTime   = false,
            initialName   = state.characterName,
            initialGender = state.characterGender,
            initialRace   = state.characterRace,
            onSave        = { name, gender, race ->
                viewModel.saveCharacterProfile(name, gender, race)
                showEditSheet = false
            },
            onDismiss     = { showEditSheet = false },
        )
    }

}

// ---------------------------------------------------------------------------
// Profile layout composables
// ---------------------------------------------------------------------------

@Composable
private fun RailLayout(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    Row(modifier) {
        Column(
            Modifier
                .width(110.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .verticalScroll(rememberScrollState()),
        ) {
            tabs.forEachIndexed { index, title ->
                val selected = selectedTab == index
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTabSelect(index) }
                        .then(
                            if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                            else Modifier
                        )
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                ) {
                    Text(
                        text       = title,
                        style      = MaterialTheme.typography.bodySmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color      = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                     else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
        Box(Modifier.weight(1f).fillMaxHeight()) {
            content(selectedTab)
        }
    }
}

@Composable
private fun TabsLayout(
    tabs: List<String>,
    selectedTab: Int,
    onTabSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    Column(modifier) {
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick  = { onTabSelect(index) },
                    text     = { Text(title) },
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxSize()) {
            content(selectedTab)
        }
    }
}

// ---------------------------------------------------------------------------
// Coins banner (also used by SkillsScreen result sheet)
// ---------------------------------------------------------------------------

@Composable
fun CoinsBanner(coins: Long) {
    Surface(
        color    = GoldPrimary.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text  = stringResource(R.string.label_coins),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text       = coins.formatCoins(),
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = GoldPrimary,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Skills tab
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillsTab(
    skillLevels: Map<String, Int>,
    skillXp: Map<String, Long>,
    context: android.content.Context,
    viewModel: InventoryViewModel,
) {
    var selectedSkill by remember { mutableStateOf<String?>(null) }
    val milestones = remember(selectedSkill) {
        selectedSkill?.let { buildUnlockMilestones(it, viewModel, context) } ?: emptyList()
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        for ((categoryRes, skills) in SKILL_CATEGORY_GROUPS) {
            item(key = "hdr_$categoryRes") {
                SlotSectionHeader(stringResource(categoryRes))
            }
            val rows = skills.chunked(3)
            rows.forEachIndexed { rowIdx, rowSkills ->
                item(key = "${categoryRes}_row_$rowIdx") {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowSkills.forEach { key ->
                            SkillGridCard(
                                skillKey = key,
                                level    = skillLevels[key] ?: 1,
                                xp       = skillXp[key] ?: 0L,
                                context  = context,
                                onClick  = { selectedSkill = key },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - rowSkills.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    selectedSkill?.let { key ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedSkill = null },
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            SkillUnlockSheet(
                skillKey   = key,
                level      = skillLevels[key] ?: 1,
                context    = context,
                milestones = milestones,
            )
        }
    }
}

@Composable
private fun CircularSkillProgress(level: Int, progressFraction: Float, modifier: Modifier = Modifier) {
    val gold      = GoldPrimary
    val track     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val textStyle = MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.Bold,
        color      = onSurface,
    )
    val measurer = rememberTextMeasurer()
    Canvas(modifier = modifier.size(56.dp)) {
        val stroke  = 5.dp.toPx()
        val inset   = stroke / 2f
        val rect    = Rect(inset, inset, size.width - inset, size.height - inset)
        drawArc(
            color      = track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter  = false,
            topLeft    = rect.topLeft,
            size       = rect.size,
            style      = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (progressFraction > 0f) {
            drawArc(
                color      = gold,
                startAngle = -90f,
                sweepAngle = progressFraction * 360f,
                useCenter  = false,
                topLeft    = rect.topLeft,
                size       = rect.size,
                style      = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        val measured = measurer.measure(level.toString(), textStyle)
        drawText(
            textLayoutResult = measured,
            topLeft = androidx.compose.ui.geometry.Offset(
                x = (size.width  - measured.size.width)  / 2f,
                y = (size.height - measured.size.height) / 2f,
            ),
        )
    }
}

@Composable
private fun SkillGridCard(
    skillKey: String,
    level: Int,
    xp: Long,
    context: android.content.Context,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = xpProgressFraction(xp)
    ElevatedCard(modifier = modifier.clickable { onClick() }) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text  = GameStrings.skillEmoji(skillKey),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text     = GameStrings.skillName(context, skillKey),
                style    = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            CircularSkillProgress(level = level, progressFraction = progress)
        }
    }
}

@Composable
private fun SkillUnlockSheet(
    skillKey: String,
    level: Int,
    context: android.content.Context,
    milestones: List<UnlockMilestone>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text  = GameStrings.skillEmoji(skillKey),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text       = GameStrings.skillName(context, skillKey),
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text  = stringResource(R.string.guild_level_label, level),
                    style = MaterialTheme.typography.bodySmall,
                    color = GoldPrimary,
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        milestones.forEach { milestone ->
            val unlocked = level >= milestone.level
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text     = stringResource(R.string.label_lv, milestone.level),
                    style    = MaterialTheme.typography.labelMedium,
                    color    = if (unlocked) GoldPrimary
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.width(44.dp),
                )
                Text(
                    text     = milestone.description,
                    style    = MaterialTheme.typography.bodySmall,
                    color    = if (unlocked) MaterialTheme.colorScheme.onSurface
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.weight(1f),
                )
                if (unlocked) {
                    Text(
                        text  = "✓",
                        color = GoldPrimary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

private fun buildUnlockMilestones(skillKey: String, vm: InventoryViewModel, context: android.content.Context): List<UnlockMilestone> =
    when (skillKey) {
        "mining" ->
            vm.ores.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, ore) -> UnlockMilestone(ore.levelRequired, GameStrings.itemName(context, key)) }

        "fishing" ->
            vm.fish.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, fish) -> UnlockMilestone(fish.levelRequired, GameStrings.itemName(context, key)) }

        "woodcutting" ->
            vm.trees.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, tree) -> UnlockMilestone(tree.levelRequired, GameStrings.itemName(context, key)) }

        "farming" ->
            vm.crops.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, crop) -> UnlockMilestone(crop.levelRequired, GameStrings.cropName(context, key)) }

        "firemaking" ->
            vm.logs.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, log) -> UnlockMilestone(log.levelRequired, GameStrings.itemName(context, key)) }

        "agility" ->
            vm.agilityCourses.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, course) ->
                    UnlockMilestone(course.levelRequired, context.stringByName("agility_${key}_name") ?: course.displayName)
                }

        "smithing" ->
            vm.smithingRecipes.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, recipe) -> UnlockMilestone(recipe.levelRequired, GameStrings.itemName(context, key)) }

        "cooking" ->
            vm.cookingRecipes.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, recipe) -> UnlockMilestone(recipe.levelRequired, GameStrings.itemName(context, key)) }

        "fletching" ->
            vm.fletchingRecipes.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, recipe) -> UnlockMilestone(recipe.levelRequired, GameStrings.itemName(context, key)) }

        "crafting" ->
            vm.craftingRecipes.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, recipe) -> UnlockMilestone(recipe.levelRequired, GameStrings.itemName(context, key)) }

        "runecrafting" ->
            vm.runes.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, rune) -> UnlockMilestone(rune.levelRequired, GameStrings.itemName(context, key)) }

        "herblore" ->
            vm.herbloreRecipes.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, recipe) -> UnlockMilestone(recipe.levelRequired, GameStrings.itemName(context, key)) }

        "attack", "strength", "ranged", "magic" ->
            vm.allEquipment.entries
                .filter { it.value.requirements.containsKey(skillKey) }
                .sortedBy { it.value.requirements[skillKey] ?: 0 }
                .map { (key, item) -> UnlockMilestone(item.requirements[skillKey]!!, GameStrings.itemName(context, key)) }

        "defense" ->
            vm.allEquipment.entries
                .filter { it.value.requirements.containsKey("defense") }
                .sortedBy { it.value.requirements["defense"] ?: 0 }
                .map { (key, item) -> UnlockMilestone(item.requirements["defense"]!!, GameStrings.itemName(context, key)) }

        "hitpoints" -> listOf(
            UnlockMilestone(1,  context.getString(R.string.label_hp_passive)),
            UnlockMilestone(10, context.getString(R.string.label_hp_scales)),
            UnlockMilestone(99, context.getString(R.string.label_hp_max, 99)),
        )

        "prayer" ->
            vm.bones.entries
                .sortedBy { it.value.xpPerBone }
                .mapIndexed { i, (key, bone) ->
                    UnlockMilestone(
                        level       = (i * 7 + 1).coerceAtMost(99),
                        description = context.getString(R.string.label_xp_per_bone, GameStrings.itemName(context, key), bone.xpPerBone.toInt()),
                    )
                }

        "thieving" ->
            vm.thievingNpcs.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, npc) -> UnlockMilestone(npc.levelRequired, GameStrings.thievingNpcName(context, key)) }

        "construction" ->
            vm.constructionRecipes.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, recipe) -> UnlockMilestone(recipe.levelRequired, GameStrings.itemName(context, key)) }

        "mercantile" ->
            vm.tradeRoutes
                .sortedBy { it.levelRequired }
                .map { UnlockMilestone(it.levelRequired, GameStrings.tradeRouteName(context, it.id, it.displayName)) }

        "slayer" ->
            vm.slayerTaskData.entries
                .sortedBy { it.value.slayerLevel }
                .distinctBy { it.value.slayerLevel }
                .map { (key, task) ->
                    UnlockMilestone(task.slayerLevel, context.getString(R.string.label_xp_per_kill, GameStrings.enemyName(context, key), task.xpPerKill))
                }

        else -> emptyList()
    }

// ---------------------------------------------------------------------------
// Inventory tab
// ---------------------------------------------------------------------------

@Composable
private fun InventoryTab(
    inventory: Map<String, Int>,
    context: android.content.Context,
    categoryFor: (String) -> InventoryCategory,
) {
    var sortAlpha by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<InventoryCategory?>(null) }

    val allGroups: List<Pair<InventoryCategory, List<Map.Entry<String, Int>>>> =
        remember(inventory, sortAlpha) {
            val grouped = inventory.entries.groupBy { categoryFor(it.key) }
            InventoryCategory.values().mapNotNull { cat ->
                val items = grouped[cat] ?: return@mapNotNull null
                val sorted = if (sortAlpha)
                    items.sortedBy { GameStrings.itemName(context, it.key) }
                else
                    items.sortedByDescending { it.value }
                cat to sorted
            }
        }

    val groups = remember(allGroups, selectedCategory) {
        if (selectedCategory == null) allGroups
        else allGroups.filter { (cat, _) -> cat == selectedCategory }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier              = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !sortAlpha,
                onClick  = { sortAlpha = false },
                label    = { Text(stringResource(R.string.inventory_sort_quantity), style = MaterialTheme.typography.labelSmall) },
            )
            FilterChip(
                selected = sortAlpha,
                onClick  = { sortAlpha = true },
                label    = { Text(stringResource(R.string.inventory_sort_az), style = MaterialTheme.typography.labelSmall) },
            )
        }
        Row(
            modifier              = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick  = { selectedCategory = null },
                label    = { Text(stringResource(R.string.inventory_cat_filter_all), style = MaterialTheme.typography.labelSmall) },
            )
            allGroups.forEach { (cat, _) ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick  = { selectedCategory = if (selectedCategory == cat) null else cat },
                    label    = { Text(categoryLabel(cat), style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        if (groups.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = stringResource(R.string.label_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                for ((cat, catItems) in groups) {
                    item(key = cat.name) {
                        Text(
                            text     = categoryLabel(cat),
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                    items(catItems, key = { it.key }) { entry ->
                        InventoryRow(name = GameStrings.itemName(context, entry.key), qty = entry.value)
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun categoryLabel(cat: InventoryCategory): String = stringResource(when (cat) {
    InventoryCategory.WEAPONS      -> R.string.inventory_cat_weapons
    InventoryCategory.ARMOUR       -> R.string.inventory_cat_armour
    InventoryCategory.TOOLS        -> R.string.inventory_cat_tools
    InventoryCategory.FOOD         -> R.string.inventory_cat_food
    InventoryCategory.RAW_FOOD     -> R.string.inventory_cat_raw_food
    InventoryCategory.POTIONS      -> R.string.inventory_cat_potions
    InventoryCategory.AMMUNITION   -> R.string.inventory_cat_ammunition
    InventoryCategory.ORES         -> R.string.inventory_cat_ores
    InventoryCategory.CONSTRUCTION -> R.string.inventory_cat_construction
    InventoryCategory.SEEDS        -> R.string.inventory_cat_seeds
    InventoryCategory.MATERIALS    -> R.string.inventory_cat_materials
    InventoryCategory.OTHER        -> R.string.inventory_cat_other
})

@Composable
private fun InventoryRow(name: String, qty: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium)
        Text(
            text       = "×$qty",
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ---------------------------------------------------------------------------
// Achievements tab
// ---------------------------------------------------------------------------

@Composable
private fun BannersTab(banners: List<com.fantasyidler.data.model.SeasonalBannerEarned>) {
    if (banners.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text  = stringResource(R.string.profile_banners_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val dateFormat = remember { java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(banners.sortedByDescending { it.completedAtMs }, key = { it.eventId }) { banner ->
            Surface(
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(banner.displayText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text  = stringResource(R.string.profile_banners_earned_on, dateFormat.format(java.util.Date(banner.completedAtMs))),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun AchievementsTab(
    byGroup: Map<String, List<Achievement>>,
    unlockedCount: Int,
    totalCount: Int,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Surface(
                color    = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text  = stringResource(R.string.label_achievements),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text       = "$unlockedCount / $totalCount",
                        style      = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color      = GoldPrimary,
                    )
                }
            }
        }
        byGroup.forEach { (group, achievements) ->
            item(key = "hdr_$group") {
                val groupLabel = when (group) {
                    "Levelling"  -> stringResource(R.string.achievement_group_levelling)
                    "Combat"     -> stringResource(R.string.achievement_group_combat)
                    "Quests"     -> stringResource(R.string.achievement_group_quests)
                    "Collection" -> stringResource(R.string.achievement_group_collection)
                    else         -> group
                }
                SlotSectionHeader(groupLabel)
            }
            items(achievements, key = { it.id }) { ach ->
                AchievementRow(ach)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun AchievementRow(ach: Achievement) {
    val alpha = if (ach.isUnlocked) 1f else 0.35f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier         = Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = ach.emoji,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text       = ach.name,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text  = ach.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        if (ach.isUnlocked) {
            Text(
                text  = "✓",
                style = MaterialTheme.typography.titleMedium,
                color = GoldPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

// ---------------------------------------------------------------------------
// Pets tab
// ---------------------------------------------------------------------------

@Composable
private fun PetsTab(
    allPets: Map<String, com.fantasyidler.data.json.PetData>,
    ownedPetIds: Set<String>,
) {
    if (allPets.isEmpty()) {
        Box(
            modifier         = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = stringResource(R.string.profile_no_pets),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        val owned  = allPets.values.filter { it.id in ownedPetIds }
        val locked = allPets.values.filter { it.id !in ownedPetIds }

        if (owned.isNotEmpty()) {
            item { SlotSectionHeader(stringResource(R.string.profile_pet_collected)) }
            items(owned, key = { it.id }) { pet ->
                PetRow(pet = pet, owned = true)
            }
        }
        if (locked.isNotEmpty()) {
            item { SlotSectionHeader(stringResource(R.string.profile_pet_not_found)) }
            items(locked, key = { it.id }) { pet ->
                PetRow(pet = pet, owned = false)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PetRow(pet: com.fantasyidler.data.json.PetData, owned: Boolean) {
    val alpha = if (owned) 1f else 0.38f
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (owned) Modifier.clickable {
                val messages = context.resources.getStringArray(R.array.profile_pet_happy_messages)
                Toast.makeText(context, String.format(messages.random(), pet.displayName), Toast.LENGTH_SHORT).show()
            } else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier         = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text  = pet.emoji,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text       = pet.displayName,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text  = pet.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        Text(
            text  = stringResource(R.string.format_xp_boost_percent, pet.boostPercent),
            style = MaterialTheme.typography.labelMedium,
            color = (if (owned) GoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                .copy(alpha = alpha),
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
// ---------------------------------------------------------------------------
// Notes tab
// ---------------------------------------------------------------------------

@Composable
private fun NotesTab(
    skillingDungeons: Map<String, SkillingDungeonData>,
    skillingDungeonNotes: Map<String, Int>,
    unlockedDungeons: List<String>,
) {
    val SKILL_ORDER = listOf("mining", "woodcutting", "fishing", "agility", "thieving")
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        SKILL_ORDER.forEach { skill ->
            val dungeons = skillingDungeons.entries
                .filter { (_, d) -> d.skill == skill }
                .sortedBy { (_, d) -> d.levelRequired }
            if (dungeons.isNotEmpty()) {
                item {
                    Text(
                        text = GameStrings.skillName(LocalContext.current, skill),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                dungeons.forEach { (key, dungeon) ->
                    val notesFound = skillingDungeonNotes[key] ?: 0
                    if (notesFound > 0) {
                        item(key = key) {
                            DungeonNotesCard(
                                dungeon = dungeon,
                                notesFound = notesFound,
                                combatDungeonUnlocked = unlockedDungeons.contains(dungeon.unlockDungeon),
                            )
                        }
                    } else {
                        item(key = "$key-locked") {
                            Text(
                                text = "${dungeon.displayName}: ???",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DungeonNotesCard(
    dungeon: SkillingDungeonData,
    notesFound: Int,
    combatDungeonUnlocked: Boolean,
) {
    androidx.compose.material3.ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = dungeon.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            val revealedNotes = dungeon.noteTexts.take(notesFound.coerceAtMost(dungeon.noteTexts.size))
            revealedNotes.forEachIndexed { index, text ->
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GoldPrimary,
                        modifier = Modifier.width(20.dp),
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val remaining = dungeon.noteThreshold - notesFound
            if (remaining > 0) {
                repeat(remaining) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "???",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    )
                }
            }
            if (combatDungeonUnlocked) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.expedition_dungeon_unlocked),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

