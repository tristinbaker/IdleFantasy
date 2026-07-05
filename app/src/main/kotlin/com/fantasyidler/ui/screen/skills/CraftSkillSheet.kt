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
import com.fantasyidler.ui.viewmodel.SessionResult
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


@Composable
internal fun CraftSkillSheet(
    skillName: String,
    craftState: CraftingUiState,
    craftingViewModel: CraftingViewModel,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    context: android.content.Context,
    onDismiss: () -> Unit,
) {
    val allRecipes: List<CraftableRecipe> = when (skillName) {
        Skills.SMITHING      -> craftingViewModel.smithingRecipes
        Skills.COOKING       -> craftingViewModel.cookingRecipes
        Skills.FLETCHING     -> craftingViewModel.fletchingRecipes
        Skills.HERBLORE      -> craftingViewModel.herbloreRecipes
        Skills.CONSTRUCTION  -> craftingViewModel.constructionRecipes
        else                 -> craftingViewModel.jewelleryRecipes
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
        categoryFiltered.map { it.tier }.filter { it.isNotEmpty() }.distinct()
            .sortedBy { tier -> categoryFiltered.filter { it.tier == tier }.minOf { it.levelRequired } }
    }
    val recipes = categoryFiltered
        .filter { selectedTier == null || it.tier == selectedTier }
        .let { list ->
            if (onlyCraftable) list.filter { craftState.meetsLevel(it) && craftState.maxCraftable(it) > 0 }
            else list
        }

    val selected = craftState.selectedRecipe

    if (selected != null) {
        CraftQuantityContent(
            recipe            = selected,
            state             = craftState,
            hasActiveSession  = hasActiveSession,
            isQueueFull       = isQueueFull,
            sessionDurationMs = sessionDurationMs,
            context           = context,
            onSetQuantity     = { craftingViewModel.setQuantity(it, craftState.maxCraftable(selected)) },
            onSetAsh          = if (selected.skillName == Skills.HERBLORE) craftingViewModel::setHerbloreAsh else null,
            onCraft           = craftingViewModel::craft,
            onBack            = craftingViewModel::dismissRecipe,
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
                        selected  = selectedCategory == null,
                        onClick   = { selectedCategory = null; selectedTier = null },
                        label     = { Text(stringResource(R.string.skills_filter_all)) },
                    )
                    categories.forEach { cat ->
                        FilterChip(
                            selected  = selectedCategory == cat,
                            onClick   = {
                                val newCat = if (selectedCategory == cat) null else cat
                                val newTiers = (if (newCat == null) allRecipes else allRecipes.filter { it.category == newCat })
                                    .map { it.tier }.filter { it.isNotEmpty() }.distinct()
                                selectedCategory = newCat
                                if (selectedTier != null && selectedTier !in newTiers) selectedTier = null
                            },
                            label     = { Text(GameStrings.craftingCategory(context, cat)) },
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
                        selected  = selectedTier == null,
                        onClick   = { selectedTier = null },
                        label     = { Text(stringResource(R.string.skills_filter_all)) },
                    )
                    tiers.forEach { tier ->
                        FilterChip(
                            selected  = selectedTier == tier,
                            onClick   = { selectedTier = if (selectedTier == tier) null else tier },
                            label     = { Text(GameStrings.craftingTier(context, tier)) },
                        )
                    }
                }
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                items(recipes) { recipe ->
                    CraftRecipeRow(
                        recipe     = recipe,
                        craftState = craftState,
                        context    = context,
                        onTap      = { craftingViewModel.openRecipe(recipe) },
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
                text       = GameStrings.itemName(context, recipe.outputKey),
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = if (enabled) MaterialTheme.colorScheme.onSurface else dim,
            )
            if (recipe.outputQty > 1) {
                Text(
                    text  = context.getString(R.string.crafting_per_craft, recipe.outputQty),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) MaterialTheme.colorScheme.primary else dim,
                )
            }
            val matText = recipe.materials.entries.joinToString("  ") { (item, qty) ->
                "${GameStrings.itemName(context, item)} ${craftState.inventory[item] ?: 0}/$qty"
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
                if (recipe.outputAttackBonus   > 0) add("+${recipe.outputAttackBonus} ${context.getString(R.string.profile_stat_atk)}")
                if (recipe.outputStrengthBonus > 0) add("+${recipe.outputStrengthBonus} ${context.getString(R.string.profile_stat_str)}")
                if (recipe.outputDefenseBonus  > 0) add("+${recipe.outputDefenseBonus} ${context.getString(R.string.profile_stat_def)}")
                if (recipe.outputHealingValue  > 0) add(context.getString(R.string.combat_heals_hp, recipe.outputHealingValue))
                if (recipe.outputDamage        > 0) add("+${recipe.outputDamage} ${context.getString(R.string.combat_log_dmg)}")
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
                        "+$bonus ${GameStrings.skillName(context, stat)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) MaterialTheme.colorScheme.primary else dim,
                )
            }
            if (recipe.outputRequirements.isNotEmpty()) {
                recipe.outputRequirements.forEach { (skill, lvl) ->
                    val have       = craftState.skillLevels[skill] ?: 1
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
                    text  = stringResource(R.string.label_lv, recipe.levelRequired),
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
                    text  = context.getString(R.string.crafting_no_mats),
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
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    context: android.content.Context,
    onSetQuantity: (Int) -> Unit,
    onSetAsh: ((String?) -> Unit)? = null,
    onCraft: () -> Unit,
    onBack: () -> Unit,
) {
    val qty     = state.craftQuantity
    val max     = state.maxCraftable(recipe)
    val totalXp = recipe.xpPerItem * qty
    var textValue by remember(qty) { mutableStateOf(qty.toString()) }
    val isHerblore = recipe.skillName == Skills.HERBLORE

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
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
                    text  = context.getString(R.string.crafting_needed_have, needed, have),
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
                modifier   = Modifier.width(130.dp),
            )
            IconButton(onClick = { onSetQuantity(qty + 1) }, enabled = qty < max) {
                Icon(Icons.Filled.Add, contentDescription = "Increase")
            }
        }
        Spacer(Modifier.height(8.dp))
        QtyQuickButtons(qty, max) { onSetQuantity(it) }
        QuestFillRow(state.questFills, qty, max, onSet = onSetQuantity)
        Spacer(Modifier.height(8.dp))
        Text(
            text       = projectedXpLabel(state.skillXp[recipe.skillName] ?: 0L, totalXp.toLong()),
            style      = MaterialTheme.typography.bodyMedium,
            color      = GoldPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        if (sessionDurationMs > 0) {
            Text(
                text     = "~${(qty.toLong() * (sessionDurationMs / 60)).formatDurationMs()}",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        if (isHerblore && onSetAsh != null) {
            val ashTiers = listOf("ashes","oak_ashes","willow_ashes","maple_ashes","yew_ashes","magic_ashes","redwood_ashes")
            val availableAshes = ashTiers.filter { (state.inventory[it] ?: 0) >= qty }
            if (availableAshes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.catalyst_optional), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                val selectedAsh = state.herbloreAshKey
                (listOf(null) + availableAshes).forEach { ashKey ->
                    Row(
                        modifier          = Modifier.fillMaxWidth().clickable { onSetAsh(ashKey) }.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text  = if (ashKey == null) stringResource(R.string.catalyst_none) else GameStrings.itemName(context, ashKey),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedAsh == ashKey) GoldPrimary else MaterialTheme.colorScheme.onSurface,
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
                if (selectedAsh != null) {
                    Text(stringResource(R.string.catalyst_enhanced_output), style = MaterialTheme.typography.labelSmall, color = GoldPrimary)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick  = onCraft,
            enabled  = !(hasActiveSession && isQueueFull),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (hasActiveSession) stringResource(R.string.skills_add_to_queue) else stringResource(R.string.btn_craft))
        }
    }
}

// ---------------------------------------------------------------------------
// Thieving sheet
// ---------------------------------------------------------------------------

