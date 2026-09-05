package com.fantasyidler.ui.screen.skills


import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.data.model.Skills
import com.fantasyidler.ui.screen.QtyQuickButtons
import com.fantasyidler.ui.viewmodel.CraftableRecipe
import com.fantasyidler.ui.viewmodel.CraftingUiState
import com.fantasyidler.ui.viewmodel.CraftingViewModel
import com.fantasyidler.ui.viewmodel.QuestFillSuggestion
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatDurationMs
import kotlin.collections.forEach


@Composable
internal fun CraftSkillSheet(
    guildDailyButton: (@Composable () -> Unit)? = null,
    /** Host-owned back interceptor: set while the quantity page is open so the system back button steps back to the recipe list (issue #1330). */
    backStep: MutableState<(() -> Unit)?>? = null,
    skillName: String,
    craftState: CraftingUiState,
    craftingViewModel: CraftingViewModel,
    hasActiveSession: Boolean,
    sessionDurationMs: Long,
    context: Context,
    onDismiss: () -> Unit,
) {
    val allRecipes: List<CraftableRecipe> = when (skillName) {
        Skills.SMITHING      -> craftingViewModel.smithingRecipes
        Skills.COOKING       -> craftingViewModel.cookingRecipes
        Skills.FLETCHING     -> craftingViewModel.fletchingRecipes
        Skills.HERBLORE      -> craftingViewModel.herbloreRecipes
        Skills.CONSTRUCTION  -> craftingViewModel.constructionRecipes
        else                 -> craftingViewModel.jewelleryRecipes
    }.filter { it.key !in craftState.hiddenRecipeKeys }

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

    val recipeListState = rememberLazyListState()
    LaunchedEffect(selectedCategory, selectedTier, onlyCraftable) {
        recipeListState.scrollToItem(0)
    }
    val selected = craftState.selectedRecipe

    if (backStep != null) {
        DisposableEffect(selected) {
            backStep.value = if (selected != null) ({ craftingViewModel.dismissRecipe() }) else null
            onDispose { backStep.value = null }
        }
    }
    // Dialog-based sheets (material3 1.3+) deliver back presses to in-content handlers;
    // the host's onDismissRequest interception covers the older popup-based sheet.
    BackHandler(enabled = selected != null) { craftingViewModel.dismissRecipe() }

    if (selected != null) {
        CraftQuantityContent(
            recipe            = selected,
            state             = craftState,
            hasActiveSession  = hasActiveSession,
            sessionDurationMs = sessionDurationMs,
            context           = context,
            onSetAsh          = if (selected.skillName == Skills.HERBLORE) craftingViewModel::setHerbloreAsh else null,
            onCraft           = { qty ->
                craftingViewModel.craft(selected, qty, if (selected.skillName == Skills.HERBLORE) craftState.herbloreAshKey else null)
            },
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
            guildDailyButton?.invoke()
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
            LazyColumn(state = recipeListState, modifier = Modifier.fillMaxWidth()) {
                items(recipes, key = { it.key }) { recipe ->
                    CraftRecipeRow(
                        recipe     = recipe,
                        craftState = craftState,
                        context    = context,
                        onTap      = { craftingViewModel.openRecipe(recipe) },
                    )
                }
                item(key = "bottom_spacer") { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun CraftRecipeRow(
    recipe: CraftableRecipe,
    craftState: CraftingUiState,
    context: Context,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = GameStrings.itemName(context, recipe.outputKey),
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color      = if (enabled) MaterialTheme.colorScheme.onSurface else dim,
                    modifier   = Modifier.weight(1f, fill = false),
                )
                val questIndicators = craftState.recipeQuests[recipe.outputKey] ?: emptyList()
                if (questIndicators.isNotEmpty()) {
                    QuestIndicatorIcons(questIndicators)
                }
            }
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
            // Ash-catalyst brews produce enhanced_* variants; count them too, same as
            // quest/guild/event tallies (issue #1201).
            val ownedQty = (craftState.inventory[recipe.outputKey] ?: 0) +
                (craftState.inventory["enhanced_${recipe.outputKey}"] ?: 0)
            Text(
                text  = stringResource(R.string.crafting_owned, ownedQty),
                style = MaterialTheme.typography.labelSmall,
                color = if (ownedQty > 0) MaterialTheme.colorScheme.primary else dim,
            )
            recipe.outputCombatStyle?.let { style ->

                Text(
                    text  = "${context.getString(R.string.label_combat_style)}: ${GameStrings.skillName(context, style)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else dim,
                )
            }
            val statParts = buildList {
                if (recipe.outputAttackBonus   > 0) add("+${recipe.outputAttackBonus} ${context.getString(R.string.profile_stat_atk)}")
                if (recipe.outputStrengthBonus > 0) add("+${recipe.outputStrengthBonus} ${context.getString(R.string.profile_stat_str)}")
                if (recipe.outputDefenseBonus  > 0) add("+${recipe.outputDefenseBonus} ${context.getString(R.string.profile_stat_def)}")
                if (recipe.outputHealingValue  > 0) add(context.getString(R.string.combat_heals_hp, recipe.outputHealingValue))
                if (recipe.outputDamage        > 0) add("+${recipe.outputDamage} ${context.getString(R.string.armory_stat_ranged_str)}")
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
                        color      = MaterialTheme.colorScheme.primary,
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
            if (craftState.isQueueFull) {
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

@Composable
private fun CraftQuantityContent(
    recipe: CraftableRecipe,
    state: CraftingUiState,
    hasActiveSession: Boolean,
    sessionDurationMs: Long,
    context: Context,
    onSetAsh: ((String?) -> Unit)? = null,
    onCraft: (Int) -> Unit,
    onBack: () -> Unit,
) {
    // Quantity is sheet-local state: pushing it through the ViewModel re-ran the
    // full player-state combine (JSON decodes + quest scans) on every keystroke (issue #1310).
    val max     = state.maxCraftable(recipe)
    var quantity by remember(recipe) { mutableIntStateOf(max.coerceAtLeast(1)) }
    val qty     = quantity.coerceIn(1, max.coerceAtLeast(1))
    val totalXp = recipe.xpPerItem * qty * state.craftXpMult
    var textValue by remember(recipe) { mutableStateOf(qty.toString()) }
    val isHerblore = recipe.skillName == Skills.HERBLORE
    fun setQuantity(value: Int) {
        quantity  = value.coerceIn(1, max.coerceAtLeast(1))
        textValue = quantity.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.btn_back_arrow)) }
        Text(
            text       = GameStrings.itemName(context, recipe.outputKey),
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        val ownedQty = state.inventory[recipe.outputKey] ?: 0
        Text(
            text  = stringResource(R.string.crafting_owned_detail, ownedQty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
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
            IconButton(onClick = { setQuantity(qty - 1) }, enabled = qty > 1) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease")
            }
            OutlinedTextField(
                value         = textValue,
                onValueChange = { new ->
                    val filtered = new.filter { it.isDigit() }
                    textValue = filtered
                    filtered.toIntOrNull()?.let { quantity = it.coerceIn(1, max.coerceAtLeast(1)) }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction    = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val parsed = textValue.toIntOrNull()?.coerceIn(1, max.coerceAtLeast(1)) ?: 1
                        setQuantity(parsed)
                    },
                ),
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                ),
                singleLine = true,
                modifier   = Modifier.width(130.dp),
            )
            IconButton(onClick = { setQuantity(qty + 1) }, enabled = qty < max) {
                Icon(Icons.Filled.Add, contentDescription = "Increase")
            }
        }
        Spacer(Modifier.height(8.dp))
        QtyQuickButtons(qty, max) { setQuantity(it) }
        QuestFillRow(state.questFills, qty, max, onSet = { setQuantity(it) })
        Spacer(Modifier.height(8.dp))
        Text(
            text       = projectedXpLabel(state.skillXp[recipe.skillName] ?: 0L, totalXp.toLong()),
            style      = MaterialTheme.typography.bodyMedium,
            color      = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        val perItemMs = state.craftPerItemMs.takeIf { it > 0 } ?: (sessionDurationMs / 60)
        if (perItemMs > 0) {
            Text(
                text     = "~${(qty.toLong() * perItemMs).formatDurationMs(context)}",
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
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clickable { onSetAsh(ashKey) }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text  = if (ashKey == null) stringResource(R.string.catalyst_none) else GameStrings.itemName(context, ashKey),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedAsh == ashKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
                    Text(stringResource(R.string.catalyst_enhanced_output), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onCraft(qty) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isQueueFull,
        ) {
            Text(
                when {
                    state.isQueueFull -> stringResource(R.string.snackbar_queue_full)
                    hasActiveSession -> stringResource(R.string.skills_add_to_queue)
                    else -> stringResource(R.string.btn_craft)
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuestFillRow(
    fills: List<QuestFillSuggestion>,
    qty: Int,
    max: Int,
    modifier: Modifier = Modifier,
    onSet: (Int) -> Unit,
) {
    if (fills.isEmpty()) return
    Column(modifier = modifier) {
        Spacer(Modifier.height(8.dp))
        Text(
            text  = stringResource(R.string.crafting_quest_targets),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement   = Arrangement.spacedBy(4.dp),
        ) {
            fills.forEach { fill ->
                SuggestionChip(
                    onClick = { onSet(fill.qty.coerceIn(1, max.coerceAtLeast(1))) },
                    label   = { Text("${fill.qty} (${fill.label})") },
                    enabled = fill.qty <= max && qty != fill.qty,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Thieving sheet
// ---------------------------------------------------------------------------

