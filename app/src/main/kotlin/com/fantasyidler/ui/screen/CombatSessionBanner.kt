package com.fantasyidler.ui.screen

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.Skills
import com.fantasyidler.simulator.CombatSimulator
import com.fantasyidler.simulator.TowerScaling
import com.fantasyidler.ui.viewmodel.MercContract
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.toClockTime
import com.fantasyidler.util.toCountdown
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

internal fun combatXpBreakdownText(total: Long, bonus: Long, boostWasActive: Boolean): String? {
    if (bonus <= 0L) return null
    val afterBoost = total - bonus
    if (afterBoost <= 0L) return null
    val base = afterBoost / (if (boostWasActive) 2L else 1L)
    val blessMult = total.toDouble() / afterBoost
    val blessStr = "%.2f".format(blessMult).trimEnd('0').trimEnd('.')
    return if (boostWasActive) "(${base.formatXp()} × 2 × $blessStr)"
           else "(${base.formatXp()} × $blessStr)"
}

/**
 * Countdown text for an in-flight boss session. Agility/Chronospire compress the playback,
 * but the boss always gets its full durationMinutes of simulated fight: every screen shows
 * the full-length fight clock so a shorter playback doesn't read as less time to beat the
 * boss (issues #1590, #1788). The real completion time stays in the parentheses.
 */
internal fun bossFightCountdown(
    context: Context,
    startedAt: Long,
    endsAt: Long,
    durationMinutes: Int,
    now: Long,
    showEndTime: Boolean,
): String {
    val actualMs = (endsAt - startedAt).coerceAtLeast(1L)
    val fightRemainingMs = (endsAt - now).coerceAtLeast(0L) * (durationMinutes * 60_000L) / actualMs
    return (now + fightRemainingMs).toCountdown(context, showEndTime = false) +
        if (showEndTime) " (${endsAt.toClockTime(context)})" else ""
}

// ---------------------------------------------------------------------------
// Active session banner
// ---------------------------------------------------------------------------

internal data class CombatLogEntry(
    val isPlayer: Boolean,
    val damage: Int,
    val enemyName: String,
    val isKill: Boolean = false,
    /** HP restored by eating this tick; > 0 renders as an eat line (issue #1431). */
    val heal: Int = 0,
    /** True when this damage came from the raid mercenary party. */
    val ally: Boolean = false,
    /** True when a prestige Double Hit landed this tick (issue #1567). */
    val doubleHit: Boolean = false,
)

@Composable
internal fun CombatSessionBanner(
    session: SkillSession,
    dungeons: List<DungeonData>,
    bosses: List<BossData>,
    enemies: Map<String, EnemyData>,
    skillLevels: Map<String, Int>,
    modifier: Modifier = Modifier,
    hpPrestigeBonus: Int = 0,
    towerHpBonus: Int = 0,
    attackBonus: Int,
    strengthBonus: Int,
    defenseBonus: Int,
    equippedFood: Map<String, Int>,
    foodHealValues: Map<String, Int>,
    foodEatOrder: String,
    showEndTime: Boolean = true,
    repeatIndex: Int = 0,
    repeatTotal: Int = 0,
    hiredMercs: List<MercContract> = emptyList(),
    onAbandon: () -> Unit,
    onDebugFinish: () -> Unit,
) {
    val context = LocalContext.current
    // Tower floors above 100 scale enemy stats at simulation time; the banner must fight the
    // same scaled enemies or every HP-derived display (mid-minute kill estimate, enemy HP bar,
    // header stats) runs against base values and the kill count sawtooths (issue #1494).
    @Suppress("NAME_SHADOWING")
    val enemies = remember(session.sessionId, enemies) {
        val floor = if (session.skillName == "tower")
            session.activityKey.removePrefix("tower_floor_").toIntOrNull() else null
        if (floor != null) TowerScaling.scaledEnemies(floor, enemies) else enemies
    }
    val sessionBoss = bosses.firstOrNull { it.id == session.activityKey }
    val dungeonName = dungeons.firstOrNull { it.name == session.activityKey }
        ?.let { GameStrings.dungeonName(context, it.name) }
        ?: sessionBoss?.let { GameStrings.bossName(context, it.id) }
        ?: run {
            if (session.skillName == "tower") {
                val floor = session.activityKey.removePrefix("tower_floor_").toIntOrNull()
                if (floor != null) context.getString(R.string.tower_floor_label, floor) else session.activityKey
            } else session.activityKey
        }

    // Each GameStrings lookup creates a configuration context, and the log rebuild
    // resolved a name per kill line every half-tick, stalling kill-heavy sessions
    // (issue #1727). Resolve each enemy key once per session instead.
    val enemyNames = remember(session.sessionId) { mutableMapOf<String, String>() }
    fun enemyDisplayName(key: String): String = enemyNames.getOrPut(key) {
        bosses.firstOrNull { it.id == key }?.let { GameStrings.bossName(context, it.id) }
            ?: enemies[key]?.let { GameStrings.enemyName(context, key) } ?: key
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showAbandonConfirm by remember { mutableStateOf(false) }
    val endsAt = session.endsAt
    LaunchedEffect(endsAt) {
        while (System.currentTimeMillis() < endsAt) {
            now = System.currentTimeMillis()
            delay(500.milliseconds)
        }
        now = System.currentTimeMillis()
    }

    val isDone = session.completed || now >= endsAt

    // Decode frames once per session
    val frames = remember(session.sessionId) {
        runCatching { Json.decodeFromString<List<SessionFrame>>(session.frames) }.getOrElse { emptyList() }
    }
    val frameCount = if (session.skillName == "boss")
        (bosses.firstOrNull { it.id == session.activityKey }?.durationMinutes ?: 60).coerceAtLeast(1)
    else 60
    val perFrameMs = ((session.endsAt - session.startedAt) / frameCount.toLong()).coerceAtLeast(1L)
    val currentFrameIdx = remember(now) {
        ((now - session.startedAt) / perFrameMs).toInt()
            .coerceIn(0, (frames.size - 1).coerceAtLeast(0))
    }
    val currentFrame = frames.getOrNull(currentFrameIdx)

    val isBoss = session.skillName == "boss"
    // Pace by the session's true tick cadence, not the current frame's own hit count: a
    // partial final frame would otherwise stretch its few hits across the whole minute
    // (issue #935).
    val fullTicks     = remember(session.sessionId) { CombatSimulator.fullFrameTicks(frames) }
    val attackSpeedMs = (perFrameMs / fullTicks).coerceAtLeast(2L)
    val frameStartMs  = session.startedAt + currentFrameIdx.toLong() * perFrameMs
    val maxTick = (currentFrame?.playerHits?.size?.minus(1) ?: 0).coerceAtLeast(0)
    // Half-tick pacing: the player's hit shows on the tick, the enemy's reply half a tick
    // later, so log lines appear one at a time instead of clumping per tick (issue #935).
    val halfTickInFrame = if (!isDone) ((now - frameStartMs) * 2 / attackSpeedMs).toInt().coerceIn(0, maxTick * 2 + 1) else maxTick * 2 + 1
    val tickInFrame = halfTickInFrame / 2

    // Each kill respawns a random enemy type mid-minute, so the tick-exact enemy, its HP,
    // and the in-frame kill attribution all come from replaying the recorded spawn chain
    // instead of pinning the whole minute on the frame's first enemy (issue #1690).
    val replayState = remember(currentFrameIdx, tickInFrame) {
        if (isBoss) null else replayFrameAt(frames, currentFrameIdx, tickInFrame, enemies)
    }
    val currentEnemyKey: String? = replayState?.enemyKey
        ?: currentFrame?.enemyKey?.takeIf { it.isNotEmpty() }
        ?: frames.take(currentFrameIdx + 1)
            .lastOrNull { it.killsByEnemy.isNotEmpty() }
            ?.killsByEnemy?.keys?.firstOrNull()
    val currentEnemy = currentEnemyKey?.let { enemies[it] }

    val killsSoFar: Map<String, Int> = remember(currentFrameIdx, tickInFrame) {
        val acc = frames.take(currentFrameIdx).fold(mutableMapOf<String, Int>()) { a, f ->
            f.killsByEnemy.forEach { (k, v) -> a[k] = (a[k] ?: 0) + v }
            a
        }
        replayState?.killsInFrame?.forEach { (k, v) -> acc[k] = (acc[k] ?: 0) + v }
        acc
    }

    val foodConsumedSoFar: Map<String, Int> = remember(currentFrameIdx) {
        frames.take(currentFrameIdx).fold(mutableMapOf()) { acc, f ->
            f.foodConsumed.forEach { (k, v) -> acc[k] = (acc[k] ?: 0) + v }
            acc
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text  = if (isDone) stringResource(R.string.label_session_complete)
                    else stringResource(R.string.label_session_in_progress),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (sessionBoss != null) {
                BossIcon(
                    bossId        = sessionBoss.id,
                    modifier      = Modifier.size(28.dp),
                    fallbackEmoji = sessionBoss.emoji,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text  = dungeonName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if ((isBoss || session.skillName == "combat") && repeatTotal > 1) {
            Spacer(Modifier.height(4.dp))
            Text(
                text       = if (isBoss) stringResource(R.string.combat_fight_progress, repeatIndex.coerceAtLeast(1), repeatTotal)
                             else stringResource(R.string.combat_run_progress, repeatIndex.coerceAtLeast(1), repeatTotal),
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))

        if (!isDone) {
            Text(
                text       = remember(now, showEndTime) {
                    if (isBoss && sessionBoss != null) {
                        bossFightCountdown(context, session.startedAt, endsAt, sessionBoss.durationMinutes, now, showEndTime)
                    } else endsAt.toCountdown(context, showEndTime)
                },
                style      = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary,
            )

            if (session.skillName == "combat" || session.skillName == "boss" || session.skillName == "tower") {
                val context = LocalContext.current
                val currentBoss = if (isBoss) bosses.firstOrNull { it.id == session.activityKey } else null
                val divColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)

                // Player HP (per-tick if hit data exists, else per-frame fallback).
                // Enemy hits display half a tick after player hits, so only count the
                // newest tick's enemy damage once its log line is visible.
                // Max HP comes from the simulation snapshot so mid-run HP level-ups
                // don't shift an in-flight run's display (issue #1411); live stats are
                // the fallback for sessions recorded before the snapshot existed.
                val maxHp = frames.firstOrNull { it.maxHp > 0 }?.maxHp
                    ?: ((skillLevels[Skills.HITPOINTS] ?: 1) + hpPrestigeBonus + towerHpBonus) * 10
                val enemyTicksShown = tickInFrame + if (halfTickInFrame >= 2 * tickInFrame + 1) 1 else 0
                val currentPlayerHp = if (currentFrame?.enemyHits?.isNotEmpty() == true) {
                    val base = frames.getOrNull(currentFrameIdx - 1)?.hpAfter ?: maxHp
                    // Heals thread per tick alongside enemy damage so HP no longer sags all
                    // frame and snaps up at the boundary (issue #1431).
                    (base - currentFrame.enemyHits.take(enemyTicksShown).sum() +
                        currentFrame.playerHeals.take(enemyTicksShown).sum()).coerceAtLeast(0)
                } else {
                    frames.getOrNull(currentFrameIdx - 1)?.hpAfter ?: maxHp
                }

                // Live enemy HP (cumulative for boss, per-enemy reset for dungeon).
                // Raid mercenary damage counts toward the boss bar alongside the player's.
                val currentEnemyHp = when {
                    currentBoss != null -> {
                        val prevDmg = frames.take(currentFrameIdx).sumOf { it.playerHits.sum() + it.allyHits.sum() }
                        val curDmg = (currentFrame?.playerHits?.take(tickInFrame + 1)?.sum() ?: 0) +
                            (currentFrame?.allyHits?.take(tickInFrame + 1)?.sum() ?: 0)
                        (currentBoss.hp - prevDmg - curDmg).coerceAtLeast(0)
                    }
                    currentEnemy != null -> replayState?.enemyHp ?: currentEnemy.hp
                    else -> currentEnemy?.hp ?: 0
                }

                // Combat log: last 8 entries, interleaved per half-tick. Enemy HP threads
                // across frames like the simulator's carryover so kill lines land on the
                // tick they actually happened (issue #935).
                val combatLog = remember(currentFrameIdx, halfTickInFrame) {
                    buildList<CombatLogEntry> {
                        fun nameOf(key: String) = enemyDisplayName(key)
                        fun fullHpOf(key: String) = if (!isBoss) enemies[key]?.hp ?: Int.MAX_VALUE else Int.MAX_VALUE
                        var key = ""
                        var hp = 0
                        var carried = false
                        for (i in 0..currentFrameIdx) {
                            val f = frames.getOrNull(i) ?: break
                            // A mid-minute kill switches to the recorded next spawn, so names,
                            // kill lines, and HP follow the actual enemy chain (issue #1690).
                            if (!carried || f.enemyKey != key) { key = f.enemyKey; hp = fullHpOf(key) }
                            carried = true
                            var eName = nameOf(key)
                            var killIdx = 0
                            val lastTick = if (i < currentFrameIdx) maxOf(f.playerHits.size, f.enemyHits.size) - 1 else tickInFrame
                            for (t in 0..lastTick) {
                                f.playerHits.getOrNull(t)?.let { dmg ->
                                    add(CombatLogEntry(true, dmg, eName, doubleHit = t in f.doubleHitTicks))
                                    hp -= dmg
                                    if (hp <= 0) {
                                        add(CombatLogEntry(false, 0, eName, isKill = true))
                                        key = f.spawnsAfterKills.getOrNull(killIdx) ?: key
                                        killIdx++
                                        hp = fullHpOf(key)
                                        eName = nameOf(key)
                                    }
                                }
                                f.allyHits.getOrNull(t)?.takeIf { it > 0 }
                                    ?.let { add(CombatLogEntry(true, it, eName, ally = true)) }
                                if (i < currentFrameIdx || 2 * t + 1 <= halfTickInFrame) {
                                    f.enemyHits.getOrNull(t)?.let { add(CombatLogEntry(false, it, eName)) }
                                    f.playerHeals.getOrNull(t)?.takeIf { it > 0 }
                                        ?.let { add(CombatLogEntry(true, 0, eName, heal = it)) }
                                }
                            }
                        }
                    }.takeLast(8)
                }

                // Drops and XP from completed frames
                val dropsSoFar = remember(currentFrameIdx) {
                    frames.take(currentFrameIdx).fold(mutableMapOf<String, Int>()) { acc, f ->
                        f.items.forEach { (k, v) -> acc[k] = (acc[k] ?: 0) + v }
                        acc
                    }
                }
                val xpSoFar = remember(currentFrameIdx) {
                    frames.take(currentFrameIdx).fold(mutableMapOf<String, Long>()) { acc, f ->
                        f.xpBySkill.forEach { (k, v) -> acc[k] = (acc[k] ?: 0L) + v }
                        acc
                    }
                }

                Spacer(Modifier.height(16.dp))
                Surface(
                    shape    = RoundedCornerShape(12.dp),
                    color    = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {

                        // ── Enemy ──────────────────────────────────────────
                        if (currentBoss != null) {
                            Text(
                                text       = GameStrings.bossName(context, currentBoss.id),
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                                progress  = { if (currentBoss.hp > 0) currentEnemyHp / currentBoss.hp.toFloat() else 0f },
                                modifier  = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color     = MaterialTheme.colorScheme.error,
                                trackColor = MaterialTheme.colorScheme.errorContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text  = "${stringResource(R.string.label_hp)} $currentEnemyHp/${currentBoss.hp}  ${stringResource(R.string.combat_atk)} ${currentBoss.combatStats.attackLevel}  ${stringResource(R.string.combat_str)} ${currentBoss.combatStats.strengthLevel}  ${stringResource(R.string.combat_def)} ${currentBoss.combatStats.defenseLevel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        } else if (currentEnemy != null) {
                            Text(
                                text       = GameStrings.enemyName(context, currentEnemy.name),
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                                progress  = { if (currentEnemy.hp > 0) currentEnemyHp / currentEnemy.hp.toFloat() else 0f },
                                modifier  = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color     = MaterialTheme.colorScheme.error,
                                trackColor = MaterialTheme.colorScheme.errorContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text  = "${stringResource(R.string.label_hp)} $currentEnemyHp/${currentEnemy.hp}  ${stringResource(R.string.combat_atk)} ${currentEnemy.combatStats.attackLevel}  ${stringResource(R.string.combat_str)} ${currentEnemy.combatStats.strengthLevel}  ${stringResource(R.string.combat_def)} ${currentEnemy.combatStats.defenseLevel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        } else {
                            Text(
                                text  = stringResource(R.string.combat_fighting),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }

                        // ── Player HP + gear ───────────────────────────────
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                        val hpPct   = currentPlayerHp * 100 / maxHp
                        val hpColor = when {
                            hpPct >= 50 -> MaterialTheme.colorScheme.tertiary
                            hpPct >= 20 -> Color(0xFFFFC107)
                            else        -> MaterialTheme.colorScheme.error
                        }
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text(
                                text       = "${stringResource(R.string.label_hp)}: $currentPlayerHp / $maxHp",
                                style      = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color      = hpColor,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                            progress  = { if (maxHp > 0) currentPlayerHp / maxHp.toFloat() else 0f },
                            modifier  = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color     = hpColor,
                            trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                        )
                        val atkLabel = stringResource(R.string.combat_atk)
                        val strLabel = stringResource(R.string.combat_str)
                        val defLabel = stringResource(R.string.combat_def)
                        // Effective stats the simulation fought with (levels, gear, potions,
                        // blessings, prestige), stamped on frame 0 (issue #1569). Sessions
                        // simulated before the stamp existed fall back to gear bonuses.
                        val statsAtStart = frames.firstOrNull()?.statsAtStart ?: emptyMap()
                        val bonusParts = if (statsAtStart.isNotEmpty()) {
                            listOf("atk" to atkLabel, "str" to strLabel, "def" to defLabel)
                                .mapNotNull { (key, label) ->
                                    statsAtStart[key]?.let { value ->
                                        val potion = statsAtStart["${key}_potion"] ?: 0
                                        "$label ${value - potion}" + if (potion != 0) " (+$potion)" else ""
                                    }
                                }
                        } else buildList {
                            if (attackBonus   != 0) add("+$attackBonus $atkLabel")
                            if (strengthBonus != 0) add("+$strengthBonus $strLabel")
                            if (defenseBonus  != 0) add("+$defenseBonus $defLabel")
                        }
                        if (bonusParts.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text  = bonusParts.joinToString("  "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }

                        // ── Raid party ─────────────────────────────────────
                        if (currentBoss?.raid == true && hiredMercs.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                            Text(
                                text  = stringResource(R.string.raid_party_title),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                            // Frame order matches the contract list the session started with,
                            // so index i is merc i's HP. Pre-feature sessions lack the
                            // snapshot and fall back to full HP.
                            val allyHpNow = frames.getOrNull(currentFrameIdx - 1)?.allyHpAfter
                            hiredMercs.forEachIndexed { i, contract ->
                                val m         = contract.merc
                                val mercMaxHp = m.hp * 10
                                val mercHpNow = (allyHpNow?.getOrNull(i) ?: mercMaxHp).coerceAtLeast(0)
                                val downed    = mercHpNow <= 0
                                val mercHpPct   = if (mercMaxHp > 0) mercHpNow * 100 / mercMaxHp else 0
                                val mercHpColor = when {
                                    mercHpPct >= 50 -> MaterialTheme.colorScheme.tertiary
                                    mercHpPct >= 20 -> Color(0xFFFFC107)
                                    else            -> MaterialTheme.colorScheme.error
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text       = "${m.emoji} ${GameStrings.mercName(context, m.id)}",
                                    style      = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = if (downed) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f)
                                                 else MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text       = "${stringResource(R.string.label_hp)}: $mercHpNow / $mercMaxHp",
                                    style      = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color      = mercHpColor,
                                )
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    gapSize = 0.dp,
                                    drawStopIndicator = {},
                                    progress  = { if (mercMaxHp > 0) mercHpNow / mercMaxHp.toFloat() else 0f },
                                    modifier  = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color     = mercHpColor,
                                    trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text  = "${m.attackLevel + m.attackBonus} ${stringResource(R.string.combat_atk)}  " +
                                        "${m.strengthLevel + m.strengthBonus} ${stringResource(R.string.combat_str)}  " +
                                        "${m.defenseLevel} ${stringResource(R.string.combat_def)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            val alliesDownNow = frames.getOrNull(currentFrameIdx - 1)?.alliesDown ?: 0
                            if (alliesDownNow > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text  = stringResource(R.string.raid_allies_down, alliesDownNow),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        // ── Equipped food ──────────────────────────────────
                        // The frame-0 snapshot keeps the list stable if gear food is
                        // changed mid-run (issue #1411); live gear is the fallback for
                        // sessions recorded before the snapshot existed.
                        val foodAtStart = frames.firstOrNull()?.foodAtStart
                            ?.takeIf { it.isNotEmpty() } ?: equippedFood
                        if (foodAtStart.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                            Text(
                                text  = stringResource(R.string.label_food),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(2.dp))
                            for ((key, startQty) in foodAtStart.entries.sortedBy {
                                when (foodEatOrder) {
                                    "descending" -> -(foodHealValues[it.key] ?: 0)
                                    "ascending" -> foodHealValues[it.key] ?: 0
                                    "least_quantity" -> it.value
                                    else -> 0
                                }
                            }) {
                                val remaining = (startQty - (foodConsumedSoFar[key] ?: 0)).coerceAtLeast(0)
                                val heal      = foodHealValues[key] ?: 0
                                val name      = GameStrings.itemName(context, key)
                                Text(
                                    text  = "$name ×$remaining (${stringResource(R.string.combat_heals_hp, heal)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (remaining > 0)
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f),
                                )
                            }
                            if (foodConsumedSoFar.isNotEmpty()) {
                                val eatenSoFar = stringResource(R.string.combat_eaten_so_far)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text  = foodConsumedSoFar.entries
                                        .sortedBy {
                                            when (foodEatOrder) {
                                                "descending" -> -(foodHealValues[it.key] ?: 0)
                                                "ascending" -> foodHealValues[it.key] ?: 0
                                                "least_quantity" -> foodAtStart[it.key] ?: 0
                                                else -> 0
                                            }
                                        }
                                        .joinToString(", ") { (k, v) ->
                                            "$v ${GameStrings.itemName(context, k)}"
                                        }
                                        + " $eatenSoFar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }

                        // ── Kills ──────────────────────────────────────────
                        if (killsSoFar.isNotEmpty()) {
                            val defeatedSoFar = stringResource(R.string.combat_defeated_so_far)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                            Text(
                                text  = killsSoFar.entries
                                    .sortedByDescending { it.value }
                                    .joinToString(", ") { (k, v) -> "$v ${enemyDisplayName(k)}" }
                                    + " $defeatedSoFar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }

                        // ── Drops so far ───────────────────────────────────
                        if (dropsSoFar.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                            Text(
                                text  = stringResource(R.string.label_drops),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text  = dropsSoFar.entries
                                    .sortedByDescending { it.value }
                                    .joinToString("  ") { (k, v) ->
                                        "${GameStrings.itemName(context, k)} ×$v"
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }

                        // ── XP so far ──────────────────────────────────────
                        if (xpSoFar.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                            Text(
                                text  = stringResource(R.string.label_xp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(2.dp))
                            val xpSkillOrder = listOf(
                                Skills.ATTACK, Skills.STRENGTH, Skills.DEFENSE,
                                Skills.RANGED, Skills.MAGIC, Skills.HITPOINTS,
                            )
                            Text(
                                text  = xpSkillOrder
                                    .mapNotNull { skill -> xpSoFar[skill]?.let { skill to it } }
                                    .joinToString("  ") { (skill, xp) ->
                                        "${GameStrings.skillName(context, skill).take(3).uppercase()} +${xp.formatXp()}"
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }

                        // ── Combat log ─────────────────────────────────────
                        if (combatLog.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                            Text(
                                text  = stringResource(R.string.combat_log_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(2.dp))
                            Column {
                                for (entry in combatLog) {
                                    if (entry.heal > 0) {
                                        Text(
                                            text  = stringResource(R.string.combat_log_heal, entry.heal),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                        )
                                    } else if (entry.isKill) {
                                        Text(
                                            text  = stringResource(R.string.combat_log_kill, entry.enemyName),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    } else if (entry.ally) {
                                        Text(
                                            text  = stringResource(R.string.combat_log_ally_hit, entry.enemyName, entry.damage),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF64B5F6),
                                        )
                                    } else if (entry.isPlayer) {
                                        val color = if (entry.damage > 0) MaterialTheme.colorScheme.tertiary
                                                    else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.45f)
                                        val text = when {
                                            entry.doubleHit && entry.damage > 0 ->
                                                stringResource(R.string.combat_log_player_double_hit, entry.enemyName, entry.damage)
                                            entry.damage > 0 ->
                                                stringResource(R.string.combat_log_player_hit, entry.enemyName, entry.damage)
                                            else ->
                                                stringResource(R.string.combat_log_player_miss, entry.enemyName)
                                        }
                                        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
                                    } else {
                                        val color = if (entry.damage > 0) MaterialTheme.colorScheme.error
                                                    else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.45f)
                                        val text = if (entry.damage > 0)
                                            stringResource(R.string.combat_log_enemy_hit, entry.enemyName, entry.damage)
                                        else
                                            stringResource(R.string.combat_log_enemy_miss, entry.enemyName)
                                        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        if (isDone) {
            Text(
                text  = stringResource(R.string.worker_manage_from_home),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(8.dp))
        }

        if (!isDone) {
            OutlinedButton(
                onClick  = { showAbandonConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.btn_abandon_session))
            }
        }

        if (showAbandonConfirm) {
            AlertDialog(
                onDismissRequest = { showAbandonConfirm = false },
                title = { Text(stringResource(R.string.session_abandon_title)) },
                text  = { Text(stringResource(R.string.session_abandon_body)) },
                confirmButton = {
                    TextButton(onClick = { showAbandonConfirm = false; onAbandon() }) {
                        Text(stringResource(R.string.btn_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAbandonConfirm = false }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                },
            )
        }

        if (BuildConfig.DEBUG && !isDone) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDebugFinish) {
                Text("[Debug] Finish Now")
            }
        }
    }
}

/** Tick-exact dungeon replay result: the enemy under attack, its remaining HP, and this frame's per-type kills. */
private data class FrameReplayState(
    val enemyKey: String,
    val enemyHp: Int,
    val killsInFrame: Map<String, Int>,
)

/**
 * Replays player hits through [frameIdx] up to [tickInFrame], following each frame's
 * recorded spawn chain: the minute starts on [SessionFrame.enemyKey] and every kill
 * switches to the next entry of [SessionFrame.spawnsAfterKills] at that enemy's full HP
 * (issue #1690). Enemy HP carries across minute boundaries like the simulator's
 * carryover: it resets only on a kill or when the enemy type changes (issue #935).
 * Frames from before the spawn chain existed keep the old same-enemy behavior. Null for
 * a missing frame or one with no combat enemy.
 */
private fun replayFrameAt(
    frames: List<SessionFrame>,
    frameIdx: Int,
    tickInFrame: Int,
    enemies: Map<String, EnemyData>,
): FrameReplayState? {
    val frame = frames.getOrNull(frameIdx) ?: return null
    if (frame.enemyKey.isEmpty()) return null
    var key = ""
    var hp = 0
    var carried = false
    fun resetTo(newKey: String) { key = newKey; hp = enemies[newKey]?.hp ?: Int.MAX_VALUE }
    for (i in 0 until frameIdx) {
        val f = frames.getOrNull(i) ?: break
        if (f.enemyKey.isEmpty()) continue
        if (!carried || f.enemyKey != key) resetTo(f.enemyKey)
        carried = true
        var killIdx = 0
        for (dmg in f.playerHits) {
            hp -= dmg
            if (hp <= 0) { resetTo(f.spawnsAfterKills.getOrNull(killIdx) ?: key); killIdx++ }
        }
    }
    if (!carried || frame.enemyKey != key) resetTo(frame.enemyKey)
    val kills = mutableMapOf<String, Int>()
    var killIdx = 0
    for (dmg in frame.playerHits.take(tickInFrame + 1)) {
        hp -= dmg
        if (hp <= 0) {
            kills[key] = (kills[key] ?: 0) + 1
            resetTo(frame.spawnsAfterKills.getOrNull(killIdx) ?: key)
            killIdx++
        }
    }
    return FrameReplayState(key, hp.coerceAtLeast(0), kills)
}
