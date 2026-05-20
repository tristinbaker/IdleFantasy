package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.ui.components.EmptyState
import com.fantasyidler.ui.components.foundation.BigStepper
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant
import com.fantasyidler.ui.components.foundation.ChunkyCard
import com.fantasyidler.ui.components.foundation.ChunkySheet
import com.fantasyidler.ui.components.foundation.ClaimBadge
import com.fantasyidler.ui.components.foundation.EntityIconDisk
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.ui.viewmodel.CraftableRecipe
import com.fantasyidler.ui.viewmodel.CraftingUiState
import com.fantasyidler.ui.viewmodel.CraftingViewModel
import com.fantasyidler.util.GameStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CraftingScreen(
    viewModel: CraftingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.snackbarConsumed()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val recipes = when (selectedTab) {
            0 -> viewModel.smithingRecipes
            1 -> viewModel.cookingRecipes
            2 -> viewModel.fletchingRecipes
            3 -> viewModel.jewelleryRecipes
            else -> viewModel.herbloreRecipes
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            CraftingTabRow(selectedTab) { selectedTab = it }
            when {
                state.isLoading -> CraftingLoading()
                recipes.isEmpty() -> CraftingEmpty()
                else -> RecipeList(
                    recipes = recipes,
                    state   = state,
                    context = context,
                    onTap   = viewModel::openRecipe,
                )
            }
        }
    }

    state.selectedRecipe?.let { recipe ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ChunkySheet(
            onDismissRequest = viewModel::dismissRecipe,
            sheetState       = sheetState,
        ) {
            RecipeDetailSheet(
                recipe        = recipe,
                state         = state,
                context       = context,
                onSetQuantity = { viewModel.setQuantity(it, state.maxCraftable(recipe)) },
                onCraft       = viewModel::craft,
                onDismiss     = viewModel::dismissRecipe,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Tabs
// ---------------------------------------------------------------------------

@Composable
private fun CraftingTabRow(selectedTab: Int, onSelect: (Int) -> Unit) {
    val tokens = LocalFantasyTokens.current
    TabRow(
        selectedTabIndex = selectedTab,
        containerColor   = tokens.colors.surface,
        contentColor     = tokens.colors.primary,
    ) {
        listOf(
            stringResource(R.string.skill_smithing_name),
            stringResource(R.string.skill_cooking_name),
            stringResource(R.string.skill_fletching_name),
            stringResource(R.string.label_jewellery),
            stringResource(R.string.skill_herblore_name),
        ).forEachIndexed { index, title ->
            Tab(
                selected = selectedTab == index,
                onClick  = { onSelect(index) },
                text     = {
                    Text(
                        text  = title,
                        style = tokens.typography.labelSmall,
                    )
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Recipe list
// ---------------------------------------------------------------------------

@Composable
private fun RecipeList(
    recipes: List<CraftableRecipe>,
    state: CraftingUiState,
    context: android.content.Context,
    onTap: (CraftableRecipe) -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = tokens.spacing.l, vertical = tokens.spacing.m + tokens.spacing.s),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.m + tokens.spacing.xs),
    ) {
        items(recipes, key = { it.key }) { recipe ->
            RecipeRow(
                recipe  = recipe,
                state   = state,
                context = context,
                onTap   = { onTap(recipe) },
            )
        }
        item { Spacer(Modifier.height(tokens.spacing.l)) }
    }
}

@Composable
private fun RecipeRow(
    recipe: CraftableRecipe,
    state: CraftingUiState,
    context: android.content.Context,
    onTap: () -> Unit,
) {
    val tokens      = LocalFantasyTokens.current
    val meetsLevel  = state.meetsLevel(recipe)
    val canMake     = state.maxCraftable(recipe)
    val enabled     = meetsLevel && canMake > 0
    val dimColor    = tokens.colors.onSurface.copy(alpha = 0.38f)

    ChunkyCard(
        onClick   = onTap,
        enabled   = enabled,
        highlight = canMake > 5,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityIconDisk(
                entityId           = recipe.outputKey,
                contentDescription = recipe.displayName,
                size               = tokens.spacing.xxl + tokens.spacing.m + tokens.spacing.xs,
            )
            Spacer(Modifier.width(tokens.spacing.m + tokens.spacing.s))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = recipe.displayName,
                        style      = tokens.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color      = if (enabled) tokens.colors.onSurface else dimColor,
                    )
                    if (recipe.outputQty > 1) {
                        Text(
                            text  = " ×${recipe.outputQty}",
                            style = tokens.typography.labelSmall,
                            color = tokens.colors.onSurfaceMuted,
                        )
                    }
                }
                val matText = recipe.materials.entries.joinToString("  ") { (item, qty) ->
                    val have = state.inventory[item] ?: 0
                    "${GameStrings.itemName(context, item)} $have/$qty"
                }
                Text(
                    text  = matText,
                    style = tokens.typography.bodyMedium,
                    color = if (enabled) tokens.colors.onSurfaceMuted else dimColor,
                )
                if (recipe.effects.isNotEmpty()) {
                    val effectsText = recipe.effects.entries.joinToString("  ") { (stat, bonus) ->
                        "+$bonus ${stat.replaceFirstChar { it.uppercase() }}"
                    }
                    Text(
                        text  = effectsText,
                        style = tokens.typography.labelSmall,
                        color = if (enabled) tokens.colors.primary else dimColor,
                    )
                }
            }
            Spacer(Modifier.width(tokens.spacing.m))
            Column(horizontalAlignment = Alignment.End) {
                when {
                    !meetsLevel -> Text(
                        text       = stringResource(R.string.crafting_required_level, recipe.levelRequired),
                        style      = tokens.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color      = dimColor,
                    )
                    canMake > 0 -> {
                        ClaimBadge(text = "×$canMake", pulse = canMake > 5)
                        Spacer(Modifier.height(tokens.spacing.xs))
                        Text(
                            text  = stringResource(R.string.crafting_xp_per_item, recipe.xpPerItem.toInt()),
                            style = tokens.typography.labelSmall,
                            color = tokens.colors.onSurfaceMuted,
                        )
                    }
                    else -> Text(
                        text  = stringResource(R.string.crafting_no_mats),
                        style = tokens.typography.labelSmall,
                        color = dimColor,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Recipe detail sheet (quantity + craft action)
// ---------------------------------------------------------------------------

@Composable
private fun RecipeDetailSheet(
    recipe: CraftableRecipe,
    state: CraftingUiState,
    context: android.content.Context,
    onSetQuantity: (Int) -> Unit,
    onCraft: () -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens  = LocalFantasyTokens.current
    val qty     = state.craftQuantity
    val max     = state.maxCraftable(recipe).coerceAtLeast(1)
    val totalXp = recipe.xpPerItem * qty

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = tokens.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EntityIconDisk(
            entityId           = recipe.outputKey,
            contentDescription = recipe.displayName,
            size               = tokens.spacing.xxl + tokens.spacing.xxl + tokens.spacing.m,
        )
        Spacer(Modifier.height(tokens.spacing.m + tokens.spacing.s))
        Text(
            text       = recipe.displayName,
            style      = tokens.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color      = tokens.colors.onSurface,
        )
        if (recipe.outputQty > 1) {
            Text(
                text  = stringResource(R.string.crafting_produces, recipe.outputQty * qty),
                style = tokens.typography.bodyMedium,
                color = tokens.colors.onSurfaceMuted,
            )
        }
        Spacer(Modifier.height(tokens.spacing.l))

        SectionLabel(stringResource(R.string.label_ingredients))
        recipe.materials.forEach { (item, perItem) ->
            val needed = perItem * qty
            val have   = state.inventory[item] ?: 0
            Row(
                modifier              = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text  = GameStrings.itemName(context, item),
                    style = tokens.typography.bodyMedium,
                    color = tokens.colors.onSurface,
                )
                Text(
                    text       = stringResource(R.string.crafting_needed_have, needed, have),
                    style      = tokens.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (have >= needed) tokens.colors.onSurface else tokens.colors.error,
                )
            }
        }

        if (recipe.effects.isNotEmpty()) {
            Spacer(Modifier.height(tokens.spacing.m + tokens.spacing.s))
            SectionLabel(stringResource(R.string.crafting_effects))
            recipe.effects.forEach { (stat, bonus) ->
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(vertical = tokens.spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text  = stat.replaceFirstChar { it.uppercase() },
                        style = tokens.typography.bodyMedium,
                        color = tokens.colors.onSurface,
                    )
                    Text(
                        text       = "+$bonus",
                        style      = tokens.typography.bodyMedium,
                        color      = tokens.colors.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(tokens.spacing.l + tokens.spacing.s))

        BigStepper(
            value         = qty,
            onValueChange = onSetQuantity,
            minValue      = 1,
            maxValue      = max,
            onMax         = { onSetQuantity(max) },
            onMin         = { onSetQuantity(1) },
        )

        Spacer(Modifier.height(tokens.spacing.m + tokens.spacing.s))
        Text(
            text       = stringResource(R.string.crafting_xp_total, totalXp.toInt()),
            style      = tokens.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color      = tokens.colors.primary,
        )

        Spacer(Modifier.height(tokens.spacing.l + tokens.spacing.s))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.m + tokens.spacing.s),
        ) {
            ChunkyButton(
                text     = stringResource(R.string.btn_cancel),
                onClick  = onDismiss,
                variant  = ChunkyButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
            ChunkyButton(
                text     = stringResource(R.string.crafting_btn_craft_total, qty),
                onClick  = onCraft,
                variant  = ChunkyButtonVariant.Primary,
                enabled  = max > 0,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val tokens = LocalFantasyTokens.current
    Text(
        text       = text,
        style      = tokens.typography.labelSmall,
        color      = tokens.colors.onSurfaceMuted,
        fontWeight = FontWeight.Bold,
        modifier   = Modifier
            .fillMaxWidth()
            .padding(top = tokens.spacing.xs),
    )
}

// ---------------------------------------------------------------------------
// State containers
// ---------------------------------------------------------------------------

@Composable
private fun CraftingLoading() {
    val tokens = LocalFantasyTokens.current
    Box(
        modifier         = Modifier.fillMaxSize().padding(tokens.spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = tokens.colors.primary)
    }
}

@Composable
private fun CraftingEmpty() {
    EmptyState(
        title       = stringResource(R.string.crafting_empty_title),
        description = stringResource(R.string.crafting_empty_desc),
    )
}

@Composable
private fun CraftingError(message: String) {
    val tokens = LocalFantasyTokens.current
    Surface(
        color    = tokens.colors.error.copy(alpha = 0.12f),
        shape    = tokens.shapes.card,
        modifier = Modifier
            .fillMaxWidth()
            .padding(tokens.spacing.l),
    ) {
        Column(
            modifier            = Modifier.padding(tokens.spacing.l),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.s),
        ) {
            Text(
                text       = stringResource(R.string.crafting_error_title),
                style      = tokens.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = tokens.colors.error,
            )
            Text(
                text  = message,
                style = tokens.typography.bodyMedium,
                color = tokens.colors.onSurface,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@PreviewLightDark
@Composable
private fun PreviewCraftingLoading() {
    FantasyPreviewSurface {
        Box(modifier = Modifier.size(LocalFantasyTokens.current.spacing.xxl * 6)) {
            CraftingLoading()
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewCraftingEmpty() {
    FantasyPreviewSurface { CraftingEmpty() }
}

@PreviewLightDark
@Composable
private fun PreviewCraftingError() {
    FantasyPreviewSurface { CraftingError("Recipe data missing.") }
}

@PreviewLightDark
@Composable
private fun PreviewRecipeRow() {
    FantasyPreviewSurface {
        val recipe = CraftableRecipe(
            key           = "iron_sword",
            displayName   = "Iron Sword",
            levelRequired = 5,
            materials     = mapOf("iron_bar" to 2),
            outputKey     = "iron_sword",
            outputQty     = 1,
            xpPerItem     = 50.0,
            skillName     = "smithing",
        )
        val state = CraftingUiState(
            smithingLevel = 10,
            inventory     = mapOf("iron_bar" to 12),
            effectiveInventory = mapOf("iron_bar" to 12),
            isLoading     = false,
        )
        RecipeRow(
            recipe  = recipe,
            state   = state,
            context = androidx.compose.ui.platform.LocalContext.current,
            onTap   = {},
        )
    }
}
