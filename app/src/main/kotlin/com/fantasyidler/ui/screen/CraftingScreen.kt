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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
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
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_crafting)) }) },
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
                recipes   = recipes,
                state     = state,
                context   = context,
                onTap     = viewModel::openRecipe,
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
    LazyColumn(Modifier.fillMaxSize()) {
        items(recipes) { recipe ->
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
                    text       = recipe.displayName,
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color      = if (enabled) MaterialTheme.colorScheme.onSurface else dimColor,
                )
                if (recipe.outputQty > 1) {
                    Text(
                        text     = " ×${recipe.outputQty}",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Materials row
            val matText = recipe.materials.entries.joinToString("  ") { (item, qty) ->
                val have = state.inventory[item] ?: 0
                "${GameStrings.itemName(context, item)} $have/$qty"
            }
            Text(
                text  = matText,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else dimColor,
            )
            // Effects row (herblore only)
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

        Column(horizontalAlignment = Alignment.End) {
            when {
                !meetsLevel -> Text(
                    text  = "Lv. ${recipe.levelRequired}",
                    style = MaterialTheme.typography.labelSmall,
                    color = dimColor,
                )
                canMake > 0 -> {
                    Text(
                        text  = "×$canMake",
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldPrimary,
                        fontWeight = FontWeight.Bold,
                    )
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
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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
    val max     = state.maxCraftable(recipe)
    val totalXp = recipe.xpPerItem * qty
    var textValue by remember(qty) { mutableStateOf(qty.toString()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
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
                    text  = stringResource(R.string.crafting_needed_have, needed, have),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (have >= needed) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error,
                )
            }
        }

        // Effects (herblore only)
        if (recipe.effects.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text  = stringResource(R.string.crafting_effects),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            recipe.effects.forEach { (stat, bonus) ->
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stat.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text  = "+$bonus",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Quantity picker
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onSetQuantity(qty - 1) }, enabled = qty > 1) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.crafting_decrease))
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
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.crafting_increase))
            }
        }

        Text(
            text     = stringResource(R.string.crafting_xp_total, totalXp.toInt()),
            style    = MaterialTheme.typography.bodySmall,
            color    = GoldPrimary,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.btn_cancel))
            }
            Button(onClick = onCraft, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.btn_craft))
            }
        }
    }
}
