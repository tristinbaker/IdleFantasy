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
internal fun ThievingSheet(
    npcs: Map<String, ThievingNpcData>,
    thievingLevel: Int,
    currentXp: Long,
    isStarting: Boolean,
    hasActiveSession: Boolean,
    isQueueFull: Boolean,
    sessionDurationMs: Long,
    context: android.content.Context,
    onSelect: (String) -> Unit,
) {
    var selectedKey by remember { mutableStateOf<String?>(null) }
    Column(Modifier.padding(bottom = 24.dp)) {
        Text(
            text     = stringResource(R.string.label_choose_activity),
            style    = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Text(
            text     = stringResource(R.string.skill_thieving_desc),
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
        )
        if (sessionDurationMs > 0) {
            Text(
                text     = stringResource(R.string.skills_session_duration, sessionDurationMs / 60_000),
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        HorizontalDivider()
        Column(Modifier.verticalScroll(rememberScrollState())) {
            npcs.values
                .sortedBy { it.levelRequired }
                .forEach { npc ->
                    val successChance = ((0.40 + (thievingLevel - npc.levelRequired) * 0.02)
                        .coerceIn(0.10, 0.95) * 100).toInt()
                    val xpGain = npc.baseXp.toLong()
                    ActivityRow(
                        name             = GameStrings.thievingNpcName(context, npc.key),
                        detail           = stringResource(
                            R.string.thieving_npc_detail,
                            npc.levelRequired,
                            npc.baseXp,
                            successChance,
                        ),
                        projectedLabel   = projectedXpLabel(currentXp, xpGain),
                        isStarting       = isStarting,
                        hasActiveSession = hasActiveSession,
                        isQueueFull      = isQueueFull,
                        onClick          = { selectedKey = npc.key },
                    )
                }
        }
    }
    selectedKey?.let { key ->
        val npc = npcs[key] ?: return@let
        val successChance = ((0.40 + (thievingLevel - npc.levelRequired) * 0.02)
            .coerceIn(0.10, 0.95) * 100).toInt()
        ActivityDetailDialog(
            name             = GameStrings.thievingNpcName(context, npc.key),
            detail           = stringResource(
                R.string.thieving_npc_detail,
                npc.levelRequired,
                npc.baseXp,
                successChance,
            ),
            description      = stringResource(
                R.string.thieving_npc_coins,
                npc.coinsMin,
                npc.coinsMax,
            ),
            hasActiveSession = hasActiveSession,
            isQueueFull      = isQueueFull,
            onConfirm        = { onSelect(key) },
            onDismiss        = { selectedKey = null },
        )
    }
}
