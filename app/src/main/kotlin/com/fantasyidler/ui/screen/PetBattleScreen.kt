package com.fantasyidler.ui.screen

import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fantasyidler.R
import com.fantasyidler.simulator.PetBattle
import com.fantasyidler.ui.viewmodel.BattleLine
import com.fantasyidler.ui.viewmodel.FighterView
import com.fantasyidler.ui.viewmodel.PetBattleViewModel
import com.fantasyidler.ui.viewmodel.ReplayView
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatDurationMs
import com.fantasyidler.util.formatXp
import kotlinx.coroutines.delay

/** Real time of one battle tick (every fighter makes one move per tick). */
private const val TICK_MS = 60_000.0 / PetBattle.TICKS_PER_FRAME

private val HP_GREEN = Color(0xFF4CAF50)
private val HP_YELLOW = Color(0xFFFFC107)
private val HP_RED = Color(0xFFF44336)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetBattleScreen(
    onBack: () -> Unit = {},
    viewModel: PetBattleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    state.result?.let { result ->
        PetArenaResultDialog(result, viewModel.petEmoji, viewModel.petData) { viewModel.resultConsumed(); onBack() }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.sessionId, state.completed) {
        while (true) {
            now = System.currentTimeMillis()
            if (state.sessionId != null && !state.completed && now >= state.endsAt) viewModel.refreshCompletion()
            delay(100)
        }
    }
    val title = state.ultimateId?.let { GameStrings.arenaCreatureName(context, it) }
        ?: state.trainerId?.let { GameStrings.arenaTrainerName(context, it) }
        ?: state.zoneId?.let { GameStrings.arenaZoneName(context, it) }
        ?: stringResource(R.string.pet_arena_title)
    val pos = if (state.completed) Double.MAX_VALUE else (now - state.startedAt) / TICK_MS
    val view = state.replay?.at(pos)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title)
                        if (view != null) Text(
                            text  = buildString {
                                if (state.trainerId == null && state.ultimateId == null) {
                                    append(context.getString(R.string.pet_battle_number, view.battleNumber))
                                    append("   ")
                                    append(context.getString(R.string.pet_battle_wins, view.wins))
                                }
                                if (!state.completed) {
                                    if (isNotEmpty()) append("   ")
                                    append(context.getString(R.string.pet_battle_left, (state.endsAt - now).coerceAtLeast(0L).formatDurationMs(context)))
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isLoading         -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.sessionId == null -> BattleNote(stringResource(R.string.pet_battle_idle))
                view == null            -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                                               horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.pet_battle_no_replay), textAlign = TextAlign.Center)
                    if (state.completed) {
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::collect) { Text(stringResource(R.string.pet_arena_collect)) }
                    }
                }
                else -> BattleScene(view, state.completed, viewModel)
            }
        }
    }
}

@Composable
private fun BattleNote(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BattleScene(view: ReplayView, completed: Boolean, viewModel: PetBattleViewModel) {
    val context = LocalContext.current
    fun isPet(f: PetBattle.Fighter) = f.id in viewModel.petEmoji
    fun nameOf(f: PetBattle.Fighter) =
        if (isPet(f)) GameStrings.petStageName(context, f.id, PetBattle.stage(f.level)) else GameStrings.arenaCreatureName(context, f.id)
    fun emojiOf(f: PetBattle.Fighter) = if (isPet(f)) viewModel.petEmoji[f.id] ?: "🐾" else viewModel.creatureEmoji(f.id)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Column(
            modifier            = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            view.trainerId?.let { id ->
                Text(
                    text     = "${viewModel.trainerEmoji(id)} ${GameStrings.arenaTrainerName(context, id)}",
                    style    = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.End),
                )
            }
            for (fv in view.foes) FighterRow(fv, nameOf(fv.fighter), emojiOf(fv.fighter), isFoe = true, enraged = view.enraged)
            Spacer(Modifier.height(12.dp))
            for (fv in view.team) FighterRow(fv, nameOf(fv.fighter), emojiOf(fv.fighter), isFoe = false)
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            shape    = RoundedCornerShape(12.dp),
            border   = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
            color    = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                view.lines.forEachIndexed { i, line ->
                    Text(
                        text       = lineText(context, line, ::nameOf),
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (i == view.lines.lastIndex) FontWeight.Bold else FontWeight.Normal,
                        color      = if (i == view.lines.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (completed) {
            Spacer(Modifier.height(12.dp))
            Button(onClick = viewModel::collect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pet_arena_collect)) }
        }
    }
}

@Composable
private fun FighterRow(fv: FighterView, name: String, emoji: String, isFoe: Boolean, enraged: Boolean = false) {
    val lunge by animateDpAsState(if (fv.attacking) (if (isFoe) (-18).dp else 18.dp) else 0.dp, tween(120), label = "lunge")
    val flash by animateFloatAsState(if (fv.hurt) 0.35f else 1f, tween(90), label = "flash")
    val faded by animateFloatAsState(if (fv.fainted) 0.25f else 1f, tween(500), label = "faint")
    val emojiView: @Composable () -> Unit = {
        Text(
            text      = emoji,
            fontSize  = 48.sp,
            textAlign = TextAlign.Center,
            modifier  = Modifier.width(72.dp).offset(x = lunge).alpha(flash * faded),
        )
    }
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFoe) Arrangement.End else Arrangement.Start,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        if (!isFoe) emojiView()
        FighterCard(fv, name, showNumbers = !isFoe, enraged = enraged, modifier = Modifier.widthIn(max = 260.dp).weight(1f, fill = false).alpha(faded))
        if (isFoe) emojiView()
    }
}

@Composable
private fun FighterCard(fv: FighterView, name: String, showNumbers: Boolean, enraged: Boolean = false, modifier: Modifier = Modifier) {
    val fraction = fv.hp.toFloat() / fv.fighter.maxHp.coerceAtLeast(1)
    val shown by animateFloatAsState(fraction, tween(400), label = "hp")
    val barColor = when {
        shown > 0.5f -> HP_GREEN
        shown > 0.2f -> HP_YELLOW
        else         -> HP_RED
    }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.pet_battle_level, fv.fighter.level), style = MaterialTheme.typography.labelMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (fv.fighter.alpha) Chip(stringResource(R.string.pet_battle_status_alpha), HP_YELLOW)
                if (enraged && !fv.fainted) Chip(stringResource(R.string.pet_battle_status_enraged), HP_RED)
                Chip(typeLabel(fv.fighter.type), MaterialTheme.colorScheme.primary)
                if (fv.burned)  Chip(stringResource(R.string.pet_battle_status_burn), HP_RED)
                if (fv.guarded) Chip(stringResource(R.string.pet_battle_status_guard), MaterialTheme.colorScheme.tertiary)
                if (fv.fighter.wellFed > 0 && !fv.fainted) Chip(stringResource(R.string.pet_battle_status_well_fed), HP_GREEN)
                if (fv.fainted) Chip(stringResource(R.string.pet_battle_status_fainted), MaterialTheme.colorScheme.outline)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pet_battle_hp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                LinearProgressIndicator(
                    gapSize           = 0.dp,
                    drawStopIndicator = {},
                    progress          = { shown },
                    color             = barColor,
                    modifier          = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                )
            }
            if (showNumbers) Text(
                text     = "${fv.hp} / ${fv.fighter.maxHp}",
                style    = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.18f)) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
    }
}

@Composable
private fun typeLabel(type: String): String = stringResource(when (type) {
    "ranged" -> R.string.pet_arena_type_ranged
    "magic"  -> R.string.pet_arena_type_magic
    else     -> R.string.pet_arena_type_melee
}).uppercase()

private fun typeWord(ctx: Context, type: String): String = ctx.getString(when (type) {
    "ranged" -> R.string.pet_arena_type_ranged
    "magic"  -> R.string.pet_arena_type_magic
    else     -> R.string.pet_arena_type_melee
}).lowercase()

private fun lineText(ctx: Context, line: BattleLine, nameOf: (PetBattle.Fighter) -> String): String = when (line) {
    is BattleLine.WildAppeared     -> if (line.foes.size == 1) ctx.getString(R.string.pet_battle_msg_wild_one, nameOf(line.foes[0]))
                                      else ctx.getString(R.string.pet_battle_msg_wild_many, line.foes.joinToString(", ") { nameOf(it) })
    is BattleLine.AlphaAppeared    -> ctx.getString(R.string.pet_battle_msg_alpha, nameOf(line.foe))
    is BattleLine.BossAppeared     -> ctx.getString(R.string.pet_battle_msg_boss, nameOf(line.boss))
    is BattleLine.BossShifted      -> ctx.getString(R.string.pet_battle_msg_shift, nameOf(line.boss), typeWord(ctx, line.type))
    is BattleLine.BossEnraged      -> ctx.getString(R.string.pet_battle_msg_enraged, nameOf(line.boss))
    is BattleLine.BossArea         -> ctx.getString(R.string.pet_battle_msg_area, nameOf(line.boss), GameStrings.petMoveName(ctx, line.move))
    is BattleLine.TrainerChallenge -> ctx.getString(R.string.pet_battle_msg_challenge, GameStrings.arenaTrainerName(ctx, line.trainerId))
    is BattleLine.TrainerSentOut   -> ctx.getString(R.string.pet_battle_msg_sent_out, GameStrings.arenaTrainerName(ctx, line.trainerId),
                                          line.foes.joinToString(", ") { nameOf(it) })
    is BattleLine.Go               -> ctx.getString(R.string.pet_battle_msg_go, line.team.joinToString(", ") { nameOf(it) })
    is BattleLine.Used             -> ctx.getString(R.string.pet_battle_msg_used, nameOf(line.user), GameStrings.petMoveName(ctx, line.move))
    is BattleLine.Missed           -> ctx.getString(R.string.pet_battle_msg_missed, nameOf(line.user))
    BattleLine.SuperEffective      -> ctx.getString(R.string.pet_battle_msg_super)
    BattleLine.NotVeryEffective    -> ctx.getString(R.string.pet_battle_msg_not_very)
    BattleLine.Critical            -> ctx.getString(R.string.pet_battle_msg_crit)
    is BattleLine.HitTimes         -> ctx.getString(R.string.pet_battle_msg_hit_times, line.times)
    is BattleLine.Softened         -> ctx.getString(R.string.pet_battle_msg_softened, nameOf(line.target))
    is BattleLine.Braced           -> ctx.getString(R.string.pet_battle_msg_braced, nameOf(line.user))
    is BattleLine.Burned           -> ctx.getString(R.string.pet_battle_msg_burned, nameOf(line.target))
    is BattleLine.BurnHurt         -> ctx.getString(R.string.pet_battle_msg_burn_hurt, nameOf(line.target))
    is BattleLine.Fainted          -> ctx.getString(R.string.pet_battle_msg_fainted, nameOf(line.fighter))
    is BattleLine.Won              -> ctx.getString(R.string.pet_battle_msg_won, line.xpEach.formatXp())
    BattleLine.Lost                -> ctx.getString(R.string.pet_battle_msg_lost)
    BattleLine.TimeUp              -> ctx.getString(R.string.pet_battle_msg_time_up)
}
