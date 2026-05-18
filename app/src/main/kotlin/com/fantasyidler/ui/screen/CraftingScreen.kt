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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.ui.components.BigStepper
import com.fantasyidler.ui.components.ChunkyCard
import com.fantasyidler.ui.components.ClaimBadge
import com.fantasyidler.ui.components.EntityIconDisk
import com.fantasyidler.ui.theme.GoldPrimary
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
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                listOf(
                    stringResource(R.string.skill_smithing_name),
                    stringResource(R.string.skill_cooking_name),
                    stringResource(R.string.skill_fletching_name),
                    stringResource(R.string.label_jewellery),
                    stringResource(R.string.skill_herblore_name),
                ).forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick  = { selectedTab = index },
                        text     = { Text(title) },
                    )
                }
            }

            val recipes = when (selectedTab) {
                0 -> viewModel.smithingRecipes
                1 -> viewModel.cookingRecipes
                2 -> viewModel.fletchingRecipes
                3 -> viewModel.jewelleryRecipes
                else -> viewModel.herbloreRecipes
            }

            RecipeList(
                recipes = recipes,
                state   = state,
                context = context,
                onTap   = viewModel::openRecipe,
            )
        }
    }

    // Craft quantity sheet
    state.selectedRecipe?.let { recipe ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissRecipe,
            sheetState       = sheetState,
            dragHandle       = { BottomSheetDefaults.DragHandle() },
        ) {
            CraftSheet(
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
// Recipe list
// ---------------------------------------------------------------------------

@Composable
private fun RecipeList(
    recipes: List<CraftableRecipe>,
    state: CraftingUiState,
    context: android.content.Context,
    onTap: (CraftableRecipe) -> Unit,
) {
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(recipes, key = { it.key }) { recipe ->
            RecipeRow(
                recipe  = recipe,
                state   = state,
                context = context,
                onTap   = { onTap(recipe) },
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun RecipeRow(
    recipe: CraftableRecipe,
    state: CraftingUiState,
    context: android.content.Context,
    onTap: () -> Unit,
) {
    val meetsLevel  = state.meetsLevel(recipe)
    val canMake     = state.maxCraftable(recipe)
    val enabled     = meetsLevel && canMake > 0
    val dimColor    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    ChunkyCard(
        onClick   = onTap,
        enabled   = enabled,
        highlight = canMake > 5,  // visually distinguish recipes you can stockpile
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityIconDisk(
                entityId           = recipe.outputKey,
                contentDescription = recipe.displayName,
                size               = 44.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = recipe.displayName,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = if (enabled) MaterialTheme.colorScheme.onSurface else dimColor,
                    )
                    if (recipe.outputQty > 1) {
                        Text(
                            text  = " ×${recipe.outputQty}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val matText = recipe.materials.entries.joinToString("  ") { (item, qty) ->
                    val have = state.inventory[item] ?: 0
                    "${GameStrings.itemName(context, item)} $have/$qty"
                }
                Text(
                    text  = matText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else dimColor,
                )
                if (recipe.effects.isNotEmpty()) {
                    val effectsText = recipe.effects.entries.joinToString("  ") { (stat, bonus) ->
                        "+$bonus ${stat.replaceFirstChar { it.uppercase() }}"
                    }
                    Text(
                        text  = effectsText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (enabled) MaterialTheme.colorScheme.primary else dimColor,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                when {
                    !meetsLevel -> Text(
                        text  = "Lv. ${recipe.levelRequired}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = dimColor,
                    )
                    canMake > 0 -> {
                        ClaimBadge(text = "×$canMake", pulse = canMake > 5)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text  = "${recipe.xpPerItem.toInt()} XP",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> Text(
                        text  = stringResource(R.string.crafting_no_mats),
                        style = MaterialTheme.typography.labelSmall,
                        color = dimColor,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Craft quantity sheet
// ---------------------------------------------------------------------------

@Composable
private fun CraftSheet(
    recipe: CraftableRecipe,
    state: CraftingUiState,
    context: android.content.Context,
    onSetQuantity: (Int) -> Unit,
    onCraft: () -> Unit,
    onDismiss: () -> Unit,
) {
    val qty     = state.craftQuantity
    val max     = state.maxCraftable(recipe).coerceAtLeast(1)
    val totalXp = recipe.xpPerItem * qty

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EntityIconDisk(
            entityId           = recipe.outputKey,
            contentDescription = recipe.displayName,
            size               = 72.dp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text       = recipe.displayName,
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        if (recipe.outputQty > 1) {
            Text(
                text  = stringResource(R.string.crafting_produces, recipe.outputQty * qty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))

        // Materials needed
        Text(
            text       = stringResource(R.string.label_ingredients),
            style      = MaterialTheme.typography.labelMedium,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier   = Modifier.align(Alignment.Start),
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
                    text  = stringResource(R.string.crafting_needed_have, needed, have),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (have >= needed) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error,
                )
            }
        }

        if (recipe.effects.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text     = stringResource(R.string.crafting_effects),
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start),
            )
            recipe.effects.forEach { (stat, bonus) ->
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stat.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text       = "+$bonus",
                        style      = MaterialTheme.typography.bodyMedium,
                        color      = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        BigStepper(
            value         = qty,
            onValueChange = onSetQuantity,
            minValue      = 1,
            maxValue      = max,
            onMax         = { onSetQuantity(max) },
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text  = stringResource(R.string.crafting_xp_total, totalXp.toInt()),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = GoldPrimary,
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.btn_cancel))
            }
            Button(onClick = onCraft, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.btn_craft))
            }
        }
    }
}
