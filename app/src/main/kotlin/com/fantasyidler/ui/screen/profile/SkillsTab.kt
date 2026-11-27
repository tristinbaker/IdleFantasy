package com.fantasyidler.ui.screen.profile

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.data.model.ElderContent
import com.fantasyidler.ui.screen.DebugModifyAmountSheet
import com.fantasyidler.ui.screen.SlotSectionHeader
import com.fantasyidler.ui.theme.ScaledSheetContent
import com.fantasyidler.ui.viewmodel.InventoryViewModel
import com.fantasyidler.ui.viewmodel.xpProgressFraction
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.stringByName

private val SKILL_CATEGORY_GROUPS: List<Pair<Int, List<String>>> = listOf(
    R.string.label_gathering      to listOf("mining", "fishing", "woodcutting", "farming", "thieving"),
    R.string.label_crafting       to listOf("smithing", "cooking", "fletching", "crafting", "runecrafting", "herblore", "firemaking", "construction"),
    R.string.label_support_skills to listOf("prayer", "mercantile", "agility"),
    R.string.label_combat         to listOf("attack", "strength", "defense", "ranged", "magic", "hitpoints", "slayer"),
)

/** Elder-skill layout for the Profile Skills tab: only the skills that exist on the isle. */
private val ELDER_SKILL_CATEGORY_GROUPS: List<Pair<Int, List<String>>> = listOf(
    R.string.label_gathering       to listOf("mining", "fishing", "woodcutting"),
    R.string.label_crafting        to listOf("smithing", "cooking"),
    R.string.label_support_skills  to listOf("agility"),
    R.string.label_combat          to listOf("attack", "strength", "defense", "ranged", "magic", "hitpoints"),
)

private data class UnlockMilestone(val level: Int, val description: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SkillsTab(
    skillLevels: Map<String, Int>,
    skillXp: Map<String, Long>,
    context: Context,
    viewModel: InventoryViewModel,
    skillPrestige: Map<String, Int> = emptyMap(),
    prestigeUnspent: Map<String, Int> = emptyMap(),
    onOpenPrestige: (String) -> Unit = {},
    elderIsleUnlocked: Boolean = false,
    elderSkillLevels: Map<String, Int> = emptyMap(),
    elderSkillXp: Map<String, Long> = emptyMap(),
) {
    var selectedSkill by remember { mutableStateOf<String?>(null) }
    var debugSelectedSkill by remember { mutableStateOf("") }
    var debugSelectedXp by remember { mutableLongStateOf(0L) }
    var debugShowAddXpSheet by remember { mutableStateOf(false) }

    // Sub-tab: Mainland vs Elder skill sheet. Only shows the toggle once isle is unlocked.
    var showElder by rememberSaveable { mutableStateOf(false) }
    val effectiveLevels = if (showElder) skillLevels.mapValues { elderSkillLevels[it.key] ?: 1 } else skillLevels
    val effectiveXp     = if (showElder) skillXp.mapValues { elderSkillXp[it.key] ?: 0L }    else skillXp
    val groups          = if (showElder) ELDER_SKILL_CATEGORY_GROUPS else SKILL_CATEGORY_GROUPS
    val showPrestige    = !showElder

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (elderIsleUnlocked) {
            item(key = "mainland_elder_tabs") {
                TabRow(selectedTabIndex = if (showElder) 1 else 0) {
                    Tab(
                        selected = !showElder,
                        onClick = { showElder = false },
                        text = { Text(stringResource(R.string.label_mainland)) },
                    )
                    Tab(
                        selected = showElder,
                        onClick = { showElder = true },
                        text = { Text(stringResource(R.string.label_elder)) },
                    )
                }
            }
        }
        for ((categoryRes, skills) in groups) {
            item(key = "hdr_$categoryRes") {
                SlotSectionHeader(stringResource(categoryRes))
            }
            val rows = skills.chunked(3)
            rows.forEachIndexed { rowIdx, rowSkills ->
                item(key = "${categoryRes}_row_$rowIdx") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowSkills.forEach { key ->
                            SkillGridCard(
                                skillKey = key,
                                level = effectiveLevels[key] ?: 1,
                                xp = effectiveXp[key] ?: 0L,
                                context = context,
                                onClick = { selectedSkill = key },
                                modifier = Modifier.weight(1f),
                            ) {
                                TextButton(onClick = {
                                    debugShowAddXpSheet = true
                                    debugSelectedSkill = key
                                    debugSelectedXp = skillXp[key] ?: 0L
                                }) {
                                    Text("[Debug] Set XP")
                                }
                            }
                        }
                        repeat(3 - rowSkills.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    selectedSkill?.let { key ->
        // Recompute milestones per-open so the mainland vs Elder toggle steers which
        // activities/drops populate the sheet. Elder tab hides prestige (no isle
        // prestige system), so the "View tree" button drops off too.
        val sheetMilestones =
            remember(key, showElder) { buildUnlockMilestones(key, viewModel, context, showElder) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedSkill = null },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            ScaledSheetContent {
                SkillUnlockSheet(
                    skillKey = key,
                    level = effectiveLevels[key] ?: 1,
                    context = context,
                    milestones = sheetMilestones,
                    prestigeCount = if (showElder) 0 else skillPrestige[key] ?: 0,
                    unspentPoints = if (showElder) 0 else prestigeUnspent[key] ?: 0,
                    onOpenPrestige = if (showElder) null else { ->
                        selectedSkill = null; onOpenPrestige(key)
                    },
                )
            }
        }
    }

    if (BuildConfig.DEBUG && debugShowAddXpSheet) {
        DebugModifyAmountSheet(
            initialId = debugSelectedSkill,
            initialAmount = debugSelectedXp.toInt().toString(),
            onAddAmount = { skill, xp ->
                viewModel.debugAddXp(skill, xp)
                debugShowAddXpSheet = false
            },
            onSetAmount = { skill, xp ->
                viewModel.debugSetXp(skill, xp)
                debugShowAddXpSheet = false
            },
            onRemoveAmount = { skill, xp ->
                viewModel.debugRemoveXp(skill, xp)
                debugShowAddXpSheet = false
            },
            onDismiss = { debugShowAddXpSheet = false }
        )
    }
}

@Composable
private fun CircularSkillProgress(level: Int, progressFraction: Float, modifier: Modifier = Modifier.Companion) {
    val gold      = MaterialTheme.colorScheme.primary
    val track     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val textStyle = MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.Bold,
        color      = onSurface,
    )
    val measurer = rememberTextMeasurer()
    Canvas(modifier = modifier.size(56.dp)) {
        val stroke = 5.dp.toPx()
        val inset = stroke / 2f
        val rect = Rect(inset, inset, size.width - inset, size.height - inset)
        drawArc(
            color = track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = rect.topLeft,
            size = rect.size,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (progressFraction > 0f) {
            drawArc(
                color = gold,
                startAngle = -90f,
                sweepAngle = progressFraction * 360f,
                useCenter = false,
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        val measured = measurer.measure(level.toString(), textStyle)
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                x = (size.width - measured.size.width) / 2f,
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
    context: Context,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.Companion,
    debugButton: @Composable () -> Unit,
) {
    val progress = xpProgressFraction(xp)
    ElevatedCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val iconRes = GameStrings.skillIconRes(skillKey)
            if (iconRes != null) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            } else {
                Text(
                    text = GameStrings.skillEmoji(skillKey),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = GameStrings.skillName(context, skillKey),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            CircularSkillProgress(level = level, progressFraction = progress)
            if (BuildConfig.DEBUG) {
                debugButton()
            }
        }
    }
}

@Composable
private fun SkillUnlockSheet(
    skillKey: String,
    level: Int,
    context: Context,
    milestones: List<UnlockMilestone>,
    prestigeCount: Int = 0,
    unspentPoints: Int = 0,
    onOpenPrestige: (() -> Unit)? = null,
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
            val iconRes = GameStrings.skillIconRes(skillKey)
            if (iconRes != null) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
            } else {
                Text(
                    text = GameStrings.skillEmoji(skillKey),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = GameStrings.skillName(context, skillKey),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.guild_level_label, level),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (onOpenPrestige != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.prestige_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (prestigeCount > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "★×$prestigeCount",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (unspentPoints > 0) {
                        Text(
                            text = stringResource(
                                R.string.prestige_points_available,
                                unspentPoints
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                TextButton(onClick = onOpenPrestige) {
                    Text(stringResource(R.string.prestige_open_tree))
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        milestones.forEach { milestone ->
            val unlocked = level >= milestone.level
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = stringResource(R.string.label_lv, milestone.level),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unlocked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.width(44.dp),
                )
                Text(
                    text = milestone.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unlocked) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.weight(1f),
                )
                if (unlocked) {
                    Text(
                        text = "✓",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

private fun buildUnlockMilestones(skillKey: String, vm: InventoryViewModel, context: Context, isElder: Boolean = false): List<UnlockMilestone> {
    // Mainland sheet filters elder activities out; elder sheet keeps only isle activities.
    // Skills without any elder counterpart (farming, firemaking, thieving, etc.) return
    // empty on the elder tab.
    fun <V> Map<String, V>.filterByIsle(elderSet: Set<String>): Map<String, V> =
        filter { (k, _) -> if (isElder) k in elderSet else k !in elderSet }
    return when (skillKey) {
        "mining" ->
            vm.ores
                .filterByIsle(ElderContent.ORES)
                .entries
                .sortedBy { it.value.levelRequired }
                .map { (key, ore) -> UnlockMilestone(ore.levelRequired, GameStrings.itemName(context, key)) }

        "fishing" ->
            vm.fish
                .filterByIsle(ElderContent.FISH)
                .entries
                .sortedBy { it.value.levelRequired }
                .map { (key, fish) -> UnlockMilestone(fish.levelRequired, GameStrings.itemName(context, key)) }

        "woodcutting" ->
            vm.trees
                .filterByIsle(ElderContent.TREES)
                .entries
                .sortedBy { it.value.levelRequired }
                .map { (key, tree) -> UnlockMilestone(tree.levelRequired, GameStrings.treeName(context, key, tree.displayName)) }

        "farming" ->
            if (isElder) emptyList() else vm.crops.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, crop) -> UnlockMilestone(crop.levelRequired, GameStrings.cropName(context, key)) }

        "firemaking" ->
            if (isElder) emptyList() else vm.logs.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, log) -> UnlockMilestone(log.levelRequired, GameStrings.itemName(context, key)) }

        "agility" ->
            vm.agilityCourses
                .filterByIsle(ElderContent.AGILITY_COURSES)
                .entries
                .sortedBy { it.value.levelRequired }
                .map { (key, course) ->
                    UnlockMilestone(course.levelRequired, context.stringByName("agility_${key}_name") ?: course.displayName)
                }

        "smithing" ->
            vm.smithingRecipes
                .filterByIsle(ElderContent.SMITHING_RECIPES)
                .entries
                .sortedBy { it.value.levelRequired }
                .map { (key, recipe) -> UnlockMilestone(recipe.levelRequired, GameStrings.itemName(context, key)) }

        "cooking" ->
            vm.cookingRecipes
                .filterByIsle(ElderContent.COOKING_RECIPES)
                .entries
                .sortedBy { it.value.levelRequired }
                .map { (key, recipe) -> UnlockMilestone(recipe.levelRequired, GameStrings.itemName(context, key)) }

        "fletching" ->
            if (isElder) emptyList() else vm.fletchingRecipes.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, recipe) -> UnlockMilestone(recipe.levelRequired, GameStrings.itemName(context, key)) }

        "crafting" ->
            if (isElder) emptyList() else vm.craftingRecipes.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, recipe) -> UnlockMilestone(recipe.levelRequired, GameStrings.itemName(context, key)) }

        "runecrafting" ->
            if (isElder) emptyList() else vm.runes.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, rune) -> UnlockMilestone(rune.levelRequired, GameStrings.itemName(context, key)) }

        "herblore" ->
            if (isElder) emptyList() else vm.herbloreRecipes.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, recipe) -> UnlockMilestone(recipe.levelRequired, GameStrings.itemName(context, key)) }

        "attack", "strength", "ranged", "magic" ->
            if (isElder) emptyList() else vm.allEquipment.entries
                .filter { it.value.requirements.containsKey(skillKey) }
                .sortedBy { it.value.requirements[skillKey] ?: 0 }
                .map { (key, item) -> UnlockMilestone(item.requirements[skillKey]!!, GameStrings.itemName(context, key)) }

        "defense" ->
            if (isElder) emptyList() else vm.allEquipment.entries
                .filter { it.value.requirements.containsKey("defense") }
                .sortedBy { it.value.requirements["defense"] ?: 0 }
                .map { (key, item) -> UnlockMilestone(item.requirements["defense"]!!, GameStrings.itemName(context, key)) }

        "hitpoints" -> listOf(
            UnlockMilestone(1,  context.getString(R.string.label_hp_passive)),
            UnlockMilestone(10, context.getString(R.string.label_hp_scales)),
            UnlockMilestone(99, context.getString(R.string.label_hp_max, 99)),
        )

        "prayer" ->
            if (isElder) emptyList() else vm.bones.entries
                .sortedBy { it.value.xpPerBone }
                .mapIndexed { i, (key, bone) ->
                    UnlockMilestone(
                        level       = (i * 7 + 1).coerceAtMost(99),
                        description = context.getString(R.string.label_xp_per_bone, GameStrings.itemName(context, key), bone.xpPerBone.toInt()),
                    )
                }

        "thieving" ->
            if (isElder) emptyList() else vm.thievingNpcs.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, npc) -> UnlockMilestone(npc.levelRequired, GameStrings.thievingNpcName(context, key)) }

        "construction" ->
            if (isElder) emptyList() else vm.constructionRecipes.entries
                .sortedBy { it.value.levelRequired }
                .map { (key, recipe) -> UnlockMilestone(recipe.levelRequired, GameStrings.itemName(context, key)) }

        "mercantile" ->
            if (isElder) emptyList() else vm.tradeRoutes
                .sortedBy { it.levelRequired }
                .map { UnlockMilestone(it.levelRequired, GameStrings.tradeRouteName(context, it.id, it.displayName)) }

        "slayer" ->
            if (isElder) emptyList() else vm.slayerTaskData.entries
                .sortedBy { it.value.slayerLevel }
                .distinctBy { it.value.slayerLevel }
                .map { (key, task) ->
                    UnlockMilestone(task.slayerLevel, context.getString(R.string.label_xp_per_kill, GameStrings.enemyName(context, key), task.xpPerKill))
                }

        else -> emptyList()
    }
}