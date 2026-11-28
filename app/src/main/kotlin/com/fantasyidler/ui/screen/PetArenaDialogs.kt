package com.fantasyidler.ui.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fantasyidler.R
import com.fantasyidler.data.json.PetData
import com.fantasyidler.repository.PetArenaCollectResult
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.simulator.PetBattle
import com.fantasyidler.simulator.XpTable
import com.fantasyidler.ui.viewmodel.petArenaResultTitle
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.formatXp
import kotlinx.coroutines.delay

/** "+12% XP (Mining)": a pet's skill boost at [percent]. */
internal fun petBoostText(context: android.content.Context, data: PetData, percent: Int): String {
    val boost = context.getString(if (data.effectType == "coin_boost") R.string.format_coin_boost_percent else R.string.format_xp_boost_percent, percent)
    val scope = when (data.boostedSkill) {
        "all"   -> context.getString(R.string.bonus_all_skills)
        "coins" -> ""
        else    -> GameStrings.skillName(context, data.boostedSkill)
    }
    return if (scope.isEmpty()) boost else "$boost ($scope)"
}

/** 0..1 progress through [xp]'s level. */
private fun levelProgress(xp: Long): Float {
    val level = PetBattle.level(xp)
    if (level >= PetBattle.MAX_LEVEL) return 1f
    val floor = XpTable.xpForLevel(level)
    val next = XpTable.xpForLevel(level + 1)
    return ((xp - floor).toFloat() / (next - floor)).coerceIn(0f, 1f)
}

/**
 * The collect screen for an arena session: result, loot, and every pet's XP bar filling up,
 * then an evolution scene for each pet that evolved.
 */
@Composable
fun PetArenaResultDialog(
    result: PetArenaCollectResult,
    petEmoji: Map<String, String>,
    petData: Map<String, PetData> = emptyMap(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val evolutions = remember(result) {
        result.levelChanges.filter { PetBattle.stage(it.newLevel) > PetBattle.stage(it.oldLevel) }
    }
    var step by remember(result) { mutableIntStateOf(-1) }  // -1 = summary, then one per evolution
    if (step >= 0) {
        val evo = evolutions.getOrNull(step)
        if (evo == null) { onDismiss(); return }
        PetEvolutionDialog(
            petId     = evo.petId,
            fromStage = PetBattle.stage(evo.oldLevel),
            toStage   = PetBattle.stage(evo.newLevel),
            emoji     = petEmoji[evo.petId] ?: "🐾",
            onDone    = { step++ },
        )
        return
    }
    AlertDialog(
        onDismissRequest = {},
        title            = { Text(petArenaResultTitle(context, result), style = MaterialTheme.typography.titleMedium) },
        text             = {
            Column(
                modifier            = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                result.petFound?.let { id ->
                    Text(stringResource(R.string.pet_arena_results_pet_found, "${petEmoji[id] ?: ""} ${GameStrings.petName(context, id)}"),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                }
                if (result.lootHalved) Text(stringResource(R.string.pet_arena_results_halved), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
                if (result.items.isNotEmpty() || result.coins > 0) {
                    Text(stringResource(R.string.pet_arena_results_loot), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    if (result.coins > 0) Text(stringResource(R.string.pet_arena_results_coins, result.coins.formatCoins()), style = MaterialTheme.typography.bodyMedium)
                    for ((key, qty) in result.items.entries.sortedByDescending { it.value }) {
                        Text("${"%,d".format(qty)} ${GameStrings.itemName(context, key)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (result.levelChanges.isNotEmpty()) {
                    Text(stringResource(R.string.pet_arena_results_team), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    for (c in result.levelChanges) PetXpRow(c, petEmoji[c.petId] ?: "🐾", petData[c.petId])
                }
                // Wounds carry into the next fight: flag anyone who should eat or rest first.
                for ((petId, fraction) in result.hpEnd.entries.sortedBy { it.value }) {
                    if (fraction >= 0.5) continue
                    val level = result.levelChanges.firstOrNull { it.petId == petId }?.newLevel
                    val name = if (level != null) GameStrings.petStageName(context, petId, PetBattle.stage(level)) else GameStrings.petName(context, petId)
                    Text(stringResource(R.string.pet_arena_results_hurt, name, (fraction * 100).toInt()), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton    = {
            TextButton(onClick = { if (evolutions.isEmpty()) onDismiss() else step = 0 }) {
                Text(stringResource(R.string.pet_arena_continue))
            }
        },
    )
}

@Composable
private fun PetXpRow(c: PlayerRepository.PetLevelChange, emoji: String, data: PetData?) {
    val context = LocalContext.current
    // Fill from where the pet started; a level-up fills the bar from empty for the new level.
    var shown by remember(c) { mutableStateOf(if (c.newLevel > c.oldLevel) 0f else levelProgress(c.oldXp)) }
    LaunchedEffect(c) { delay(250); shown = levelProgress(c.newXp) }
    val progress by animateFloatAsState(shown, tween(900), label = "petXp")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 28.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
        Column(Modifier.weight(1f)) {
            val name = GameStrings.petStageName(context, c.petId, PetBattle.stage(c.newLevel))
            Text(
                text       = if (c.newLevel > c.oldLevel) stringResource(R.string.pet_arena_results_level_up, name, c.oldLevel, c.newLevel)
                             else stringResource(R.string.pet_arena_name_level, name, c.newLevel),
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = if (c.newLevel > c.oldLevel) FontWeight.Bold else FontWeight.Normal,
            )
            LinearProgressIndicator(
                gapSize           = 0.dp,
                drawStopIndicator = {},
                progress          = { progress },
                modifier          = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
            Text(stringResource(R.string.pet_arena_results_xp, (c.newXp - c.oldXp).formatXp()), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            // A level-up that raised the pet's skill boost says so.
            if (data != null && data.boostPercent > 0) {
                val before = PetBattle.boostPercent(data.boostPercent, c.oldXp)
                val after = PetBattle.boostPercent(data.boostPercent, c.newXp)
                if (after > before) Text(
                    text  = stringResource(R.string.pet_arena_results_boost, petBoostText(context, data, after)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** "What? X is evolving!", a flashing pulse, then the new name. */
@Composable
fun PetEvolutionDialog(petId: String, fromStage: Int, toStage: Int, emoji: String, onDone: () -> Unit) {
    val context = LocalContext.current
    var evolved by remember(petId, toStage) { mutableStateOf(false) }
    LaunchedEffect(petId, toStage) { delay(2_600); evolved = true }
    val pulse = rememberInfiniteTransition(label = "evolve")
    val scale by pulse.animateFloat(0.85f, 1.2f, infiniteRepeatable(tween(300), RepeatMode.Reverse), label = "scale")
    val glow by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(150), RepeatMode.Reverse), label = "glow")
    val before = GameStrings.petStageName(context, petId, fromStage)
    val after = GameStrings.petStageName(context, petId, toStage)
    AlertDialog(
        onDismissRequest = {},
        text             = {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text     = emoji,
                    fontSize = 72.sp,
                    modifier = if (evolved) Modifier else Modifier.scale(scale).alpha(glow),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text      = if (evolved) stringResource(R.string.pet_arena_evolved_congrats, before, after)
                                else stringResource(R.string.pet_arena_evolving, before),
                    style     = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                if (evolved) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.pet_arena_special_learned), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton    = {
            if (evolved) TextButton(onClick = onDone) { Text(stringResource(R.string.pet_arena_continue)) }
        },
    )
}
