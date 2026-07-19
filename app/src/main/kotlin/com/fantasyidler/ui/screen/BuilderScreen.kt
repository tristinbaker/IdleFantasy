package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.ui.viewmodel.BuilderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuilderScreen(
    onBack: () -> Unit = {},
    viewModel: BuilderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    ToastMessageEffect(state.snackbarMessage, viewModel::snackbarConsumed)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.builder_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.padding(horizontal = 16.dp)) {
            item {
                Spacer(Modifier.height(12.dp))
                BuildingUpgradeCard(
                    buildingKey       = "inn",
                    currentTier       = state.innTier,
                    buildingDef       = viewModel.gameData.townBuildings["inn"],
                    townRepository    = viewModel.townRepo,
                    constructionLevel = state.constructionLevel,
                    coins             = state.coins,
                    inventory         = state.inventory,
                    onUpgrade         = { viewModel.upgrade("inn") },
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                BuildingUpgradeCard(
                    buildingKey       = "guild_hall",
                    currentTier       = state.guildHallTier,
                    buildingDef       = viewModel.gameData.townBuildings["guild_hall"],
                    townRepository    = viewModel.townRepo,
                    constructionLevel = state.constructionLevel,
                    coins             = state.coins,
                    inventory         = state.inventory,
                    onUpgrade         = { viewModel.upgrade("guild_hall") },
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                BuildingUpgradeCard(
                    buildingKey       = "church",
                    currentTier       = state.churchTier,
                    buildingDef       = viewModel.gameData.townBuildings["church"],
                    townRepository    = viewModel.townRepo,
                    constructionLevel = state.constructionLevel,
                    coins             = state.coins,
                    inventory         = state.inventory,
                    onUpgrade         = { viewModel.upgrade("church") },
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                BuildingUpgradeCard(
                    buildingKey       = "fairgrounds",
                    currentTier       = state.fairgroundsTier,
                    buildingDef       = viewModel.gameData.townBuildings["fairgrounds"],
                    townRepository    = viewModel.townRepo,
                    constructionLevel = state.constructionLevel,
                    coins             = state.coins,
                    inventory         = state.inventory,
                    onUpgrade         = { viewModel.upgrade("fairgrounds") },
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                BuildingUpgradeCard(
                    buildingKey       = "garden",
                    currentTier       = state.gardenTier,
                    buildingDef       = viewModel.gameData.townBuildings["garden"],
                    townRepository    = viewModel.townRepo,
                    constructionLevel = state.constructionLevel,
                    coins             = state.coins,
                    inventory         = state.inventory,
                    onUpgrade         = { viewModel.upgrade("garden") },
                )
            }
            item {
                Spacer(Modifier.height(16.dp))
                BuildingUpgradeCard(
                    buildingKey       = "queue_master",
                    currentTier       = state.queueMasterTier,
                    buildingDef       = viewModel.gameData.townBuildings["queue_master"],
                    townRepository    = viewModel.townRepo,
                    constructionLevel = state.constructionLevel,
                    coins             = state.coins,
                    inventory         = state.inventory,
                    onUpgrade         = { viewModel.upgrade("queue_master") },
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
