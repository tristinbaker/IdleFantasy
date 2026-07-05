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

// ---------------------------------------------------------------------------
// Active session banner
// ---------------------------------------------------------------------------

internal data class CombatLogEntry(
    val isPlayer: Boolean,
    val damage: Int,
    val enemyName: String,
    val isKill: Boolean = false,
)

@Composable
internal fun CombatSessionBanner(
    session: SkillSession,
    dungeons: List<DungeonData>,
    bosses: List<BossData>,
    enemies: Map<String, EnemyData>,
    skillLevels: Map<String, Int>,
    modifier: Modifier = Modifier,
    skillPrestige: Map<String, Int> = emptyMap(),
    attackBonus: Int,
    strengthBonus: Int,
    defenseBonus: Int,
    equippedFood: Map<String, Int>,
    foodHealValues: Map<String, Int>,
    onCollect: () -> Unit,
    onAbandon: () -> Unit,
    onDebugFinish: () -> Unit,
) {
    val context = LocalContext.current
    val dungeonName = dungeons.firstOrNull { it.name == session.activityKey }
        ?.let { GameStrings.dungeonName(context, it.name) }
        ?: bosses.firstOrNull { it.id == session.activityKey }?.let { "${it.emoji} ${GameStrings.bossName(context, it.id)}" }
        ?: run {
            if (session.skillName == "tower") {
                val floor = session.activityKey.removePrefix("tower_floor_").toIntOrNull()
                if (floor != null) context.getString(R.string.tower_floor_label, floor) else session.activityKey
            } else session.activityKey
        }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showAbandonConfirm by remember { mutableStateOf(false) }
    val endsAt = session.endsAt
    LaunchedEffect(endsAt) {
        while (System.currentTimeMillis() < endsAt) {
            now = System.currentTimeMillis()
            delay(500L)
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

    val currentEnemyKey: String? = remember(currentFrameIdx) {
        currentFrame?.enemyKey?.takeIf { it.isNotEmpty() }
            ?: frames.take(currentFrameIdx + 1)
                .lastOrNull { it.killsByEnemy.isNotEmpty() }
                ?.killsByEnemy?.keys?.firstOrNull()
    }
    val currentEnemy = currentEnemyKey?.let { enemies[it] }

    val isBoss = session.skillName == "boss"
    val attackSpeedMs = 2_400L
    val frameStartMs  = session.startedAt + currentFrameIdx.toLong() * perFrameMs
    val maxTick = (currentFrame?.playerHits?.size?.minus(1) ?: 0).coerceAtLeast(0)
    val tickInFrame = if (!isDone) ((now - frameStartMs) / attackSpeedMs).toInt().coerceIn(0, maxTick) else maxTick

    val killsSoFar: Map<String, Int> = remember(currentFrameIdx, tickInFrame) {
        val acc = frames.take(currentFrameIdx).fold(mutableMapOf<String, Int>()) { a, f ->
            f.killsByEnemy.forEach { (k, v) -> a[k] = (a[k] ?: 0) + v }
            a
        }
        val f = frames.getOrNull(currentFrameIdx)
        if (f != null && !isBoss) {
            val enemy = enemies[f.enemyKey]
            if (enemy != null && f.playerHits.isNotEmpty()) {
                var hp = enemy.hp
                var kills = 0
                for (dmg in f.playerHits.take(tickInFrame + 1)) {
                    hp -= dmg
                    if (hp <= 0) { kills++; hp = enemy.hp }
                }
                if (kills > 0) acc[f.enemyKey] = (acc[f.enemyKey] ?: 0) + kills
            }
        }
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
            color = if (isDone) GoldPrimary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = dungeonName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        if (!isDone) {
            Text(
                text       = remember(now) { endsAt.toCountdown() },
                style      = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary,
            )

            if (session.skillName == "combat" || session.skillName == "boss" || session.skillName == "tower") {
                val context = LocalContext.current
                val currentBoss = if (isBoss) bosses.firstOrNull { it.id == session.activityKey } else null
                val divColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)

                // Live player HP (per-tick if hit data exists, else per-frame fallback)
                val maxHp = ((skillLevels[Skills.HITPOINTS] ?: 1) + (skillPrestige[Skills.HITPOINTS] ?: 0) * 5) * 10
                val currentPlayerHp = if (currentFrame?.enemyHits?.isNotEmpty() == true) {
                    val base = frames.getOrNull(currentFrameIdx - 1)?.hpAfter ?: maxHp
                    (base - currentFrame.enemyHits.take(tickInFrame + 1).sum()).coerceAtLeast(0)
                } else {
                    frames.getOrNull(currentFrameIdx - 1)?.hpAfter ?: maxHp
                }

                // Live enemy HP (cumulative for boss, per-enemy reset for dungeon)
                val currentEnemyHp = when {
                    currentBoss != null -> {
                        val prevDmg = frames.take(currentFrameIdx).sumOf { it.playerHits.sum() }
                        val curDmg = currentFrame?.playerHits?.take(tickInFrame + 1)?.sum() ?: 0
                        (currentBoss.hp - prevDmg - curDmg).coerceAtLeast(0)
                    }
                    currentEnemy != null && currentFrame?.playerHits?.isNotEmpty() == true -> {
                        var hp = currentEnemy.hp
                        for (dmg in currentFrame.playerHits.take(tickInFrame + 1)) {
                            hp -= dmg
                            if (hp <= 0) hp = currentEnemy.hp
                        }
                        hp.coerceAtLeast(0)
                    }
                    else -> currentEnemy?.hp ?: 0
                }

                // Combat log: last 8 entries (interleaved per tick)
                val combatLog = remember(currentFrameIdx, tickInFrame) {
                    buildList<CombatLogEntry> {
                        for (i in 0 until currentFrameIdx) {
                            val f = frames.getOrNull(i) ?: break
                            val eName = bosses.firstOrNull { it.id == f.enemyKey }?.let { GameStrings.bossName(context, it.id) }
                                ?: enemies[f.enemyKey]?.let { GameStrings.enemyName(context, f.enemyKey) } ?: f.enemyKey
                            val enemyHp = if (!isBoss) enemies[f.enemyKey]?.hp ?: Int.MAX_VALUE else Int.MAX_VALUE
                            var hp = enemyHp
                            for (t in 0 until maxOf(f.playerHits.size, f.enemyHits.size)) {
                                f.playerHits.getOrNull(t)?.let { dmg ->
                                    add(CombatLogEntry(true, dmg, eName))
                                    hp -= dmg
                                    if (hp <= 0) { add(CombatLogEntry(false, 0, eName, isKill = true)); hp = enemyHp }
                                }
                                f.enemyHits.getOrNull(t)?.let { add(CombatLogEntry(false, it, eName)) }
                            }
                        }
                        val f = frames.getOrNull(currentFrameIdx) ?: return@buildList
                        val eName = bosses.firstOrNull { it.id == f.enemyKey }?.let { GameStrings.bossName(context, it.id) }
                            ?: enemies[f.enemyKey]?.let { GameStrings.enemyName(context, f.enemyKey) } ?: f.enemyKey
                        val enemyHp = if (!isBoss) enemies[f.enemyKey]?.hp ?: Int.MAX_VALUE else Int.MAX_VALUE
                        var hp = enemyHp
                        for (t in 0..tickInFrame) {
                            f.playerHits.getOrNull(t)?.let { dmg ->
                                add(CombatLogEntry(true, dmg, eName))
                                hp -= dmg
                                if (hp <= 0) { add(CombatLogEntry(false, 0, eName, isKill = true)); hp = enemyHp }
                            }
                            f.enemyHits.getOrNull(t)?.let { add(CombatLogEntry(false, it, eName)) }
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

                // Food remaining (equipped qty minus consumed so far in session)
                val foodRemaining = equippedFood.mapValues { (key, qty) ->
                    (qty - (foodConsumedSoFar[key] ?: 0)).coerceAtLeast(0)
                }.filter { (_, qty) -> qty > 0 }

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
                                text       = "${currentBoss.emoji} ${GameStrings.bossName(context, currentBoss.id)}",
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color      = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
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
                            hpPct >= 50 -> Color(0xFF4CAF50)
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
                            progress  = { if (maxHp > 0) currentPlayerHp / maxHp.toFloat() else 0f },
                            modifier  = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color     = hpColor,
                            trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f),
                        )
                        val atkLabel = stringResource(R.string.combat_atk)
                        val strLabel = stringResource(R.string.combat_str)
                        val defLabel = stringResource(R.string.combat_def)
                        val bonusParts = buildList {
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

                        // ── Equipped food ──────────────────────────────────
                        if (equippedFood.isNotEmpty()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = divColor)
                            Text(
                                text  = stringResource(R.string.label_food),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(2.dp))
                            for ((key, startQty) in equippedFood) {
                                val remaining = (startQty - (foodConsumedSoFar[key] ?: 0)).coerceAtLeast(0)
                                val heal      = foodHealValues[key] ?: 0
                                val name      = key.replace('_', ' ').replaceFirstChar { it.uppercase() }
                                Text(
                                    text  = "$name ×$remaining (${stringResource(R.string.combat_heals_hp, heal)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (remaining > 0)
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f),
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
                                    .joinToString(", ") { (k, v) ->
                                        "$v ${bosses.firstOrNull { it.id == k }?.let { GameStrings.bossName(context, it.id) } ?: enemies[k]?.let { GameStrings.enemyName(context, k) } ?: k}"
                                    }
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
                                    if (entry.isKill) {
                                        Text(
                                            text  = stringResource(R.string.combat_log_kill, entry.enemyName),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = GoldPrimary,
                                        )
                                    } else if (entry.isPlayer) {
                                        val color = if (entry.damage > 0) Color(0xFF4CAF50)
                                                    else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.45f)
                                        val text = if (entry.damage > 0)
                                            stringResource(R.string.combat_log_player_hit, entry.enemyName, entry.damage)
                                        else
                                            stringResource(R.string.combat_log_player_miss, entry.enemyName)
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
            Button(
                onClick  = onCollect,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.btn_collect_results))
            }
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
