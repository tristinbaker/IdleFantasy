package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.perks.PerkCategory
import com.fantasyidler.data.perks.PerkCatalog
import com.fantasyidler.data.perks.PerkDef
import com.fantasyidler.data.perks.PerkRepository
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant
import com.fantasyidler.ui.components.foundation.ChunkyCard
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.ui.viewmodel.PerksViewModel

@Composable
fun PerksScreen(
    onBack: () -> Unit = {},
    viewModel: PerksViewModel = hiltViewModel(),
) {
    val state             by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.snackbarConsumed()
        }
    }

    PerksScreenContent(
        state             = state,
        snackbarHostState = snackbarHostState,
        onCategorySelect  = viewModel::selectCategory,
        onPurchase        = viewModel::purchase,
        onBack            = onBack,
    )
}

@Composable
private fun PerksScreenContent(
    state: PerksViewModel.UiState,
    snackbarHostState: SnackbarHostState,
    onCategorySelect: (PerkCategory) -> Unit,
    onPurchase: (String) -> Unit,
    onBack: () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    val categories = PerkCategory.values().toList()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text       = stringResource(R.string.perks_title),
                style      = tokens.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color      = tokens.colors.onSurface,
                modifier   = Modifier.padding(
                    start = tokens.spacing.l,
                    end   = tokens.spacing.l,
                    top   = tokens.spacing.l,
                ),
            )
            Text(
                text     = stringResource(R.string.perks_subtitle),
                style    = tokens.typography.bodyMedium,
                color    = tokens.colors.onSurfaceMuted,
                modifier = Modifier.padding(horizontal = tokens.spacing.l),
            )
            Spacer(Modifier.height(tokens.spacing.s))
            val selectedIdx = categories.indexOf(state.selectedCategory).coerceAtLeast(0)
            ScrollableTabRow(selectedTabIndex = selectedIdx) {
                categories.forEachIndexed { index, cat ->
                    Tab(
                        selected = index == selectedIdx,
                        onClick  = { onCategorySelect(cat) },
                        text     = { Text(cat.label()) },
                    )
                }
            }
            val available = state.snapshot.availableFor(state.selectedCategory)
            val earned    = when (state.selectedCategory) {
                PerkCategory.ADVANTAGE -> state.snapshot.earnedAp
                PerkCategory.GATHERING -> state.snapshot.earnedGathering
                PerkCategory.CRAFTING  -> state.snapshot.earnedCrafting
                PerkCategory.COMBAT    -> state.snapshot.earnedCombat
            }
            Text(
                text       = stringResource(R.string.perks_points_available, available, earned),
                style      = tokens.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = tokens.colors.primary,
                modifier   = Modifier.padding(tokens.spacing.l),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = tokens.spacing.l),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.m),
            ) {
                PerkCatalog.byCategory(state.selectedCategory).forEach { perk ->
                    PerkCard(
                        perk       = perk,
                        snapshot   = state.snapshot,
                        onPurchase = onPurchase,
                    )
                }
                Spacer(Modifier.height(tokens.spacing.l))
                ChunkyButton(
                    text     = stringResource(R.string.minigame_hub_back),
                    onClick  = onBack,
                    variant  = ChunkyButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(tokens.spacing.l))
            }
        }
    }
}

@Composable
private fun PerkCard(
    perk: PerkDef,
    snapshot: PerkRepository.Snapshot,
    onPurchase: (String) -> Unit,
) {
    val tokens     = LocalFantasyTokens.current
    val owned      = snapshot.ownedTier(perk.id)
    val isMaxed    = owned >= perk.tiers.size
    val nextTier   = if (isMaxed) null else perk.tiers[owned]
    val available  = snapshot.availableFor(perk.category)
    val canAfford  = nextTier != null && available >= nextTier.costPoints

    ChunkyCard {
        Column {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text       = perk.displayName,
                    style      = tokens.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = tokens.colors.onSurface,
                    modifier   = Modifier.weight(1f),
                )
                Text(
                    text       = if (isMaxed) stringResource(R.string.perks_max_tier)
                                 else stringResource(R.string.perks_tier_label, owned),
                    style      = tokens.typography.labelSmall,
                    color      = tokens.colors.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(tokens.spacing.xs))
            Text(
                text  = perk.description,
                style = tokens.typography.bodyMedium,
                color = tokens.colors.onSurfaceMuted,
            )
            Spacer(Modifier.height(tokens.spacing.s))
            LinearProgressIndicator(
                progress = { owned.toFloat() / perk.tiers.size.toFloat() },
                modifier = Modifier.fillMaxWidth().height(tokens.spacing.s),
                color    = tokens.colors.primary,
            )
            Spacer(Modifier.height(tokens.spacing.m))
            if (nextTier != null) {
                ChunkyButton(
                    text     = stringResource(R.string.perks_buy_button, nextTier.costPoints),
                    onClick  = { onPurchase(perk.id) },
                    variant  = ChunkyButtonVariant.Primary,
                    enabled  = canAfford,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!canAfford) {
                    Spacer(Modifier.height(tokens.spacing.xs))
                    Text(
                        text  = stringResource(R.string.perks_cant_afford),
                        style = tokens.typography.labelSmall,
                        color = tokens.colors.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun PerkCategory.label(): String = when (this) {
    PerkCategory.ADVANTAGE -> stringResource(R.string.perks_tab_advantage)
    PerkCategory.GATHERING -> stringResource(R.string.perks_tab_gathering)
    PerkCategory.CRAFTING  -> stringResource(R.string.perks_tab_crafting)
    PerkCategory.COMBAT    -> stringResource(R.string.perks_tab_combat)
}

@PreviewLightDark
@Composable
private fun PreviewPerksScreen() {
    FantasyPreviewSurface {
        PerksScreenContent(
            state = PerksViewModel.UiState(
                isLoading = false,
                snapshot  = PerkRepository.Snapshot(
                    earnedAp        = 164,
                    earnedGathering = 90,
                    earnedCrafting  = 40,
                    earnedCombat    = 34,
                    state           = com.fantasyidler.data.model.PerkState(),
                ),
                selectedCategory = PerkCategory.GATHERING,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onCategorySelect  = {},
            onPurchase        = {},
            onBack            = {},
        )
    }
}
