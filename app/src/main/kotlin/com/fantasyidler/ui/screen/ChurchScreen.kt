package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.data.json.BlessingData
import com.fantasyidler.data.json.BlessingType
import com.fantasyidler.repository.ChurchRepository
import com.fantasyidler.ui.viewmodel.ChurchViewModel
import com.fantasyidler.util.formatDurationMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChurchScreen(
    onBack: () -> Unit = {},
    viewModel: ChurchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    AppBannerEffect(state.snackbarMessage, viewModel::snackbarConsumed)

    // Confirmation dialog
    state.pendingBlessingKey?.let { key ->
        val blessing = ChurchRepository.ALL_BLESSINGS.find { it.key == key }
        if (blessing != null) {
            val context   = LocalContext.current
            val nameResId = context.resources.getIdentifier(
                "blessing_${blessing.key}_name", "string", context.packageName,
            )
            val name      = if (nameResId != 0) stringResource(nameResId) else blessing.key
            val cost      = ChurchRepository.boneCostFor(blessing)
            val hasEnough = state.totalBoneEquivalent >= cost
            AlertDialog(
                onDismissRequest = viewModel::dismissConfirm,
                title = { Text(stringResource(R.string.church_confirm_title), fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = blessingEffectText(blessing, state.blessingDuration, state.prayerCapeMult),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text  = stringResource(R.string.church_cost_bones, cost),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text  = stringResource(R.string.church_bones_available, state.totalBoneCount, state.totalBoneEquivalent),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasEnough) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.error,
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = viewModel::confirmActivate, enabled = hasEnough) {
                        Text(stringResource(R.string.church_activate))
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = viewModel::dismissConfirm) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                },
            )
        }
    }

    if (state.showDeactivateConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeactivate,
            title = { Text(stringResource(R.string.church_confirm_deactivate_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.church_confirm_deactivate_body)) },
            confirmButton = {
                Button(onClick = viewModel::confirmDeactivate) {
                    Text(stringResource(R.string.btn_deactivate))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::dismissDeactivate) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.church_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val anyBlessingActive = state.activeBlessing != null && state.activeBlessingRemainingMs > 0

        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            contentPadding      = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            // ── Active blessing banner ──────────────────────────────────
            item {
                if (anyBlessingActive) {
                    ActiveBlessingBanner(
                        blessing     = state.activeBlessing!!,
                        prayerCapeMult = state.prayerCapeMult,
                        remainingMs  = state.activeBlessingRemainingMs,
                        totalMs      = state.blessingDuration,
                        onDeactivate = viewModel::deactivateBlessing,
                    )
                } else {
                    Text(
                        text     = stringResource(R.string.church_no_active_blessing),
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                HorizontalDivider()
            }

            // ── Blessing rows grouped by type ───────────────────────────
            // Ironman characters can only use defensive blessings; the XP and coin
            // groups are hidden entirely and a short notice explains why.
            val groups = if (state.ironman) {
                listOf(R.string.church_section_defense to BlessingType.DEFENSE)
            } else {
                listOf(
                    R.string.church_section_xp      to BlessingType.XP,
                    R.string.church_section_defense to BlessingType.DEFENSE,
                    R.string.church_section_coins   to BlessingType.COINS,
                )
            }
            if (state.ironman) {
                item {
                    Text(
                        text     = stringResource(R.string.ironman_blessing_blocked),
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    )
                }
            }
            groups.forEach { (headerRes, type) ->
                val blessings = state.allBlessings.filter { it.type == type }
                item {
                    Text(
                        text     = stringResource(headerRes),
                        style    = MaterialTheme.typography.labelLarge,
                        color    = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(blessings, key = { it.key }) { blessing ->
                    BlessingRow(
                        blessing          = blessing,
                        isUnlocked        = blessing.key in state.unlockedBlessingKeys,
                        isActive          = blessing.key == state.activeBlessing?.key && state.activeBlessingRemainingMs > 0,
                        anyBlessingActive = anyBlessingActive,
                        boneCost          = ChurchRepository.boneCostFor(blessing),
                        blessingTimeMs    = state.blessingDuration,
                        prayerCapeMult    = state.prayerCapeMult,
                        onActivate        = { viewModel.activateBlessing(blessing.key) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

        }
    }
}

@Composable
private fun ActiveBlessingBanner(
    blessing: BlessingData,
    prayerCapeMult: Float,
    remainingMs: Long,
    totalMs: Long,
    onDeactivate: () -> Unit,
) {
    val context   = LocalContext.current
    val nameResId = context.resources.getIdentifier(
        "blessing_${blessing.key}_name", "string", context.packageName,
    )
    val name = if (nameResId != 0) stringResource(nameResId) else blessing.key
    val effectText = blessingEffectText(blessing, totalMs, prayerCapeMult)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = stringResource(R.string.church_active_label),
                style      = MaterialTheme.typography.labelSmall,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text       = name,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary,
            )
            Text(
                text  = effectText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = stringResource(R.string.church_expires_in, remainingMs.formatDurationMs(context)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        OutlinedButton(onClick = onDeactivate) {
            Text(stringResource(R.string.btn_deactivate))
        }
    }
}

@Composable
private fun BlessingRow(
    blessing: BlessingData,
    isUnlocked: Boolean,
    isActive: Boolean,
    anyBlessingActive: Boolean,
    boneCost: Int,
    blessingTimeMs: Long,
    prayerCapeMult: Float,
    onActivate: () -> Unit,
) {
    val context   = LocalContext.current
    val nameResId = context.resources.getIdentifier(
        "blessing_${blessing.key}_name", "string", context.packageName,
    )
    val name = if (nameResId != 0) stringResource(nameResId) else blessing.key

    val dimColor  = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val nameColor = if (isUnlocked) MaterialTheme.colorScheme.onSurface else dimColor
    val descColor = if (isUnlocked) MaterialTheme.colorScheme.onSurfaceVariant else dimColor

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text       = name,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = nameColor,
            )
            Text(
                text  = blessingEffectText(blessing, blessingTimeMs, prayerCapeMult),
                style = MaterialTheme.typography.bodySmall,
                color = descColor,
            )
            Text(
                text  = stringResource(R.string.church_cost_bones, boneCost),
                style = MaterialTheme.typography.labelSmall,
                color = descColor,
            )
            if (!isUnlocked) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = stringResource(R.string.church_locked_level, blessing.prayerLevelRequired),
                    style = MaterialTheme.typography.labelSmall,
                    color = dimColor,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        when {
            isActive && isUnlocked        -> Button(onClick = onActivate) {
                Text(stringResource(R.string.btn_extend))
            }
            isUnlocked && !anyBlessingActive -> Button(onClick = onActivate) {
                Text(stringResource(R.string.church_activate))
            }
            else                          -> {}
        }
    }
}

@Composable
private fun blessingEffectText(blessing: BlessingData, blessingTimeMs: Long, prayerCapeMult: Float): String {
    val hours = blessingTimeMs / 3_600_000
    val effectiveBlessing = ChurchRepository.effectiveMagnitude(blessing, prayerCapeMult)
    return when (blessing.type) {
        BlessingType.XP      -> stringResource(R.string.church_effect_xp,   effectiveBlessing, hours)
        BlessingType.DEFENSE -> stringResource(R.string.church_effect_def,   effectiveBlessing.toInt(), hours)
        BlessingType.COINS   -> stringResource(R.string.church_effect_coins, (effectiveBlessing * 100).toInt(), hours)
    }
}
