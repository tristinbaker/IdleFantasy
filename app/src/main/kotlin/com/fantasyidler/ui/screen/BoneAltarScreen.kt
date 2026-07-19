package com.fantasyidler.ui.screen

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.viewmodel.BoneAltarUiState
import com.fantasyidler.ui.viewmodel.BoneAltarViewModel
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatXp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoneAltarScreen(
    onBack: () -> Unit,
    viewModel: BoneAltarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    DisposableEffect(Unit) {
        onDispose { viewModel.resetCombo() }
    }

    ToastMessageEffect(state.snackbarMessage, viewModel::snackbarConsumed)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bone_altar_title)) },
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
        if (state.selectedBoneKey == null) {
            BoneSelectionContent(
                state    = state,
                onSelect = viewModel::selectBone,
                modifier = Modifier.padding(padding),
            )
        } else {
            BoneTapContent(
                state        = state,
                onTap        = viewModel::tapBone,
                onChangeBone = { viewModel.selectBone(null) },
                modifier     = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun BoneSelectionContent(
    state: BoneAltarUiState,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text     = stringResource(R.string.bone_altar_select_prompt),
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider()
        if (state.availableBones.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text  = stringResource(R.string.bone_altar_no_bones),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn {
                items(state.availableBones.entries.toList(), key = { it.key }) { (key, bone) ->
                    val qty = state.inventory[key] ?: 0
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(key) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(GameStrings.itemName(context, key), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text  = "${bone.xpPerBone.toInt()} XP  •  $qty in inventory",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text  = stringResource(R.string.btn_select),
                            style = MaterialTheme.typography.labelMedium,
                            color = GoldPrimary,
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun BoneTapContent(
    state: BoneAltarUiState,
    onTap: () -> Unit,
    onChangeBone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context     = LocalContext.current
    val boneKey     = state.selectedBoneKey ?: return
    val bone        = state.availableBones[boneKey] ?: return
    val boneName    = GameStrings.itemName(context, boneKey)
    val comboActive = state.combo >= BoneAltarViewModel.COMBO_THRESHOLD
    val comboColor  = if (comboActive) GoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val qty         = state.inventory[boneKey] ?: 0

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(boneName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text  = "$qty remaining  •  ${bone.xpPerBone.toInt()} XP base",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onChangeBone) {
                Text(stringResource(R.string.bone_altar_change_bone))
            }
        }
        HorizontalDivider()

        Row(
            modifier      = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text       = stringResource(R.string.bone_altar_session_xp, state.sessionXp.formatXp()),
                style      = MaterialTheme.typography.bodyMedium,
                color      = GoldPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.weight(1f),
            )
            Text(
                text  = stringResource(R.string.bone_altar_buried_count, state.totalBuried),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.combo > 0) {
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text       = stringResource(R.string.bone_altar_combo_count, state.combo),
                    style      = MaterialTheme.typography.labelLarge,
                    color      = comboColor,
                    fontWeight = if (comboActive) FontWeight.Bold else FontWeight.Normal,
                )
                if (comboActive) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text  = stringResource(R.string.bone_altar_bonus_active),
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldPrimary,
                    )
                }
            }
        }

        HorizontalDivider()

        Surface(
            modifier       = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp)
                .clickable(enabled = qty > 0, onClick = onTap),
            shape          = MaterialTheme.shapes.large,
            color          = if (qty > 0) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 4.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = stringResource(R.string.bone_altar_tap),
                        style      = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color      = if (qty > 0) MaterialTheme.colorScheme.onPrimaryContainer
                                     else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text  = boneName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = (if (qty > 0) MaterialTheme.colorScheme.onPrimaryContainer
                                 else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.7f),
                    )
                    if (qty == 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text  = stringResource(R.string.bone_altar_no_bones),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
