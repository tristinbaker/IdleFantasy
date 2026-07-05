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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.CookingRecipe
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.json.EquipmentData
import com.fantasyidler.data.json.SpellData
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.Skills
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.theme.SuccessGreen
import com.fantasyidler.ui.viewmodel.CombatSessionResult
import com.fantasyidler.ui.viewmodel.CombatViewModel
import com.fantasyidler.ui.viewmodel.InventoryViewModel
import com.fantasyidler.ui.viewmodel.combatLevelFrom
import com.fantasyidler.ui.viewmodel.slotDisplayName
import com.fantasyidler.ui.viewmodel.xpProgressFraction
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.toCountdown
import com.fantasyidler.util.toTitleCase
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

// ---------------------------------------------------------------------------
// Combat result sheet
// ---------------------------------------------------------------------------

@Composable
internal fun CombatResultSheet(
    result: CombatSessionResult,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 40.dp),
    ) {
        if (!result.won) {
            Text(
                text       = stringResource(R.string.combat_you_died),
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(2.dp))
        }
        Text(
            text       = result.dungeonDisplayName,
            style      = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text  = if (result.won) stringResource(R.string.label_session_results)
                    else stringResource(R.string.combat_died_reward),
            style = MaterialTheme.typography.bodySmall,
            color = if (result.won) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))

        // XP per skill
        if (result.xpPerSkill.isNotEmpty()) {
            Text(
                text  = if (result.won) stringResource(R.string.label_xp_gained) else stringResource(R.string.combat_xp_consolation),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val orderedSkills = listOf(
                Skills.ATTACK, Skills.STRENGTH, Skills.DEFENSE,
                Skills.RANGED, Skills.MAGIC, Skills.HITPOINTS, Skills.PRAYER,
            )
            for (skill in orderedSkills) {
                val xp = result.xpPerSkill[skill] ?: continue
                if (xp <= 0L) continue
                val bonus = result.xpBlessingBonusBySkill[skill] ?: 0L
                val breakdown = combatXpBreakdownText(xp, bonus, result.boostWasActive)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text  = GameStrings.skillName(context, skill),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text       = "+${xp.formatXp()} ${stringResource(R.string.label_xp)}",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = GoldPrimary,
                        )
                        if (breakdown != null) {
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text  = breakdown,
                                style = MaterialTheme.typography.labelSmall,
                                color = GoldPrimary,
                            )
                        }
                    }
                }
            }
            if (result.boostWasActive) {
                Text(
                    text  = stringResource(R.string.home_xp_boost_was_active),
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldPrimary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // Coins gained
        if (result.coinsGained > 0L) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text  = stringResource(R.string.label_coins),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text       = "+${result.coinsGained.formatCoins()}",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = GoldPrimary,
                )
            }
            if (result.coinBlessingBonus > 0L) {
                Text(
                    text  = stringResource(R.string.church_blessing_bonus, result.coinBlessingBonus.formatCoins()),
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldPrimary,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // Items gained
        if (result.itemsGained.isNotEmpty()) {
            Text(
                text  = stringResource(R.string.label_items_collected),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for ((item, qty) in result.itemsGained) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text  = GameStrings.itemName(context, item),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text  = "×$qty",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Kills
        if (result.killsByEnemy.isNotEmpty()) {
            Text(
                text  = stringResource(R.string.label_kills),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for ((enemy, qty) in result.killsByEnemy.entries.sortedByDescending { it.value }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text  = GameStrings.enemyName(context, enemy),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text  = "×$qty",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Food consumed
        if (result.foodConsumed.isNotEmpty()) {
            Text(
                text  = stringResource(R.string.label_food),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for ((food, qty) in result.foodConsumed) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = GameStrings.itemName(context, food), style = MaterialTheme.typography.bodyMedium)
                    Text(text = "×$qty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Arrows consumed / reclaimed
        if (result.arrowsConsumed.isNotEmpty()) {
            Text(text = stringResource(R.string.label_arrows_consumed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for ((arrow, qty) in result.arrowsConsumed) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = GameStrings.itemName(context, arrow), style = MaterialTheme.typography.bodyMedium)
                    Text(text = "×$qty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (result.arrowsReclaimed.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(text = stringResource(R.string.label_arrows_reclaimed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for ((arrow, qty) in result.arrowsReclaimed) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = GameStrings.itemName(context, arrow), style = MaterialTheme.typography.bodyMedium)
                        Text(text = "+$qty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Runes consumed / reclaimed
        if (result.runesConsumed.isNotEmpty()) {
            Text(text = stringResource(R.string.label_runes_consumed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for ((rune, qty) in result.runesConsumed) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = GameStrings.itemName(context, rune), style = MaterialTheme.typography.bodyMedium)
                    Text(text = "×$qty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (result.runesReclaimed.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(text = stringResource(R.string.label_runes_reclaimed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for ((rune, qty) in result.runesReclaimed) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = GameStrings.itemName(context, rune), style = MaterialTheme.typography.bodyMedium)
                        Text(text = "+$qty", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.btn_close))
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Simplified OSRS combat level formula using melee stats only. */
internal fun combatLevel(levels: Map<String, Int>): Int {
    val attack  = levels[Skills.ATTACK]    ?: 1
    val strength = levels[Skills.STRENGTH] ?: 1
    val defence  = levels[Skills.DEFENSE]  ?: 1
    val hp       = levels[Skills.HITPOINTS] ?: 1
    return (((attack + strength) * 0.325) + (defence + hp) * 0.25).toInt().coerceAtLeast(1)
}
