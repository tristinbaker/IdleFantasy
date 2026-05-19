package com.fantasyidler.ui.screen.combat

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.BuildConfig
import com.fantasyidler.R
import com.fantasyidler.data.json.BossData
import com.fantasyidler.data.json.DungeonData
import com.fantasyidler.data.json.EnemyData
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.data.model.Skills
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant
import com.fantasyidler.ui.screen.combat.sheets.FoodPickerSheet
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatXp
import com.fantasyidler.util.toCountdown
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** One row of the per-tick combat log. */
internal data class CombatLogEntry(
    val isPlayer: Boolean,
    val damage: Int,
    val enemyName: String,
)

/**
 * Active-session banner. Renders the timer, a live HUD card (enemy, player
 * HP, food, kills, drops, XP, combat log), and the abandon / collect actions
 * that drive the session lifecycle.
 *
 * The banner keeps `var now by mutableLongStateOf(...)` so the countdown +
 * per-tick enemy/HP/log displays advance every 500ms — there are no other
 * recompositions, the rest of the screen is static for a given session frame.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CombatSessionBanner(
    session: SkillSession,
    dungeons: List<DungeonData>,
    bosses: List<BossData>,
    enemies: Map<String, EnemyData>,
    skillLevels: Map<String, Int>,
    attackBonus: Int,
    strengthBonus: Int,
    defenseBonus: Int,
    equippedFood: Map<String, Int>,
    foodHealValues: Map<String, Int>,
    onCollect: () -> Unit,
    onAbandon: () -> Unit,
    onDebugFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens   = LocalFantasyTokens.current
    val activity = remember(session.activityKey, dungeons, bosses) {
        dungeons.firstOrNull { it.name == session.activityKey }?.displayName
            ?: bosses.firstOrNull { it.id == session.activityKey }?.let { "${it.emoji} ${it.displayName}" }
            ?: session.activityKey
    }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.endsAt) {
        while (System.currentTimeMillis() < session.endsAt) {
            now = System.currentTimeMillis()
            delay(500L)
        }
        now = System.currentTimeMillis()
    }

    val frames = remember(session.sessionId) {
        runCatching { Json.decodeFromString<List<SessionFrame>>(session.frames) }
            .getOrElse { emptyList() }
    }
    val perFrameMs = ((session.endsAt - session.startedAt) / 60L).coerceAtLeast(1L)
    val currentFrameIdx = ((now - session.startedAt) / perFrameMs).toInt()
        .coerceIn(0, (frames.size - 1).coerceAtLeast(0))
    val currentFrame = frames.getOrNull(currentFrameIdx)
    val isDone       = session.completed || now >= session.endsAt

    var showFoodPicker by remember { mutableStateOf(false) }
    val foodConsumedSoFar = remember(currentFrameIdx) {
        frames.take(currentFrameIdx).fold(mutableMapOf<String, Int>()) { acc, f ->
            f.foodConsumed.forEach { (k, v) -> acc[k] = (acc[k] ?: 0) + v }
            acc
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(tokens.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(tokens.spacing.l))
        Text(
            text  = if (isDone) stringResource(R.string.label_session_complete)
                    else stringResource(R.string.label_session_in_progress),
            style = tokens.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = if (isDone) tokens.colors.primary else tokens.colors.onSurface,
        )
        Spacer(Modifier.height(tokens.spacing.m))
        Text(
            text  = activity,
            style = tokens.typography.titleLarge,
            color = tokens.colors.onSurfaceMuted,
        )
        Spacer(Modifier.height(tokens.spacing.l))

        if (!isDone) {
            Text(
                text       = remember(now) { session.endsAt.toCountdown() },
                style      = tokens.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color      = tokens.colors.primary,
                modifier   = Modifier.semantics {
                    contentDescription = session.endsAt.toCountdown()
                },
            )

            if (session.skillName == "combat") {
                CombatHudCard(
                    session            = session,
                    frames             = frames,
                    currentFrameIdx    = currentFrameIdx,
                    currentFrame       = currentFrame,
                    perFrameMs         = perFrameMs,
                    now                = now,
                    enemies            = enemies,
                    skillLevels        = skillLevels,
                    attackBonus        = attackBonus,
                    strengthBonus      = strengthBonus,
                    defenseBonus       = defenseBonus,
                    equippedFood       = equippedFood,
                    foodHealValues     = foodHealValues,
                    foodConsumedSoFar  = foodConsumedSoFar,
                    onViewFood         = { showFoodPicker = true },
                )
            }

            Spacer(Modifier.height(tokens.spacing.xl))
        }

        if (isDone) {
            ChunkyButton(
                text     = stringResource(R.string.btn_collect_results),
                onClick  = onCollect,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(tokens.spacing.m))
        }

        ChunkyButton(
            text     = stringResource(R.string.btn_abandon_session),
            onClick  = onAbandon,
            variant  = ChunkyButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )

        if (BuildConfig.DEBUG && !isDone) {
            Spacer(Modifier.height(tokens.spacing.m))
            ChunkyButton(
                text    = stringResource(R.string.combat_debug_finish_now),
                onClick = onDebugFinish,
                variant = ChunkyButtonVariant.Ghost,
            )
        }
    }

    if (showFoodPicker) {
        FoodPickerSheet(
            equippedFood   = equippedFood,
            foodHealValues = foodHealValues,
            foodConsumed   = foodConsumedSoFar,
            onDismiss      = { showFoodPicker = false },
        )
    }
}

@Composable
private fun CombatHudCard(
    session: SkillSession,
    frames: List<SessionFrame>,
    currentFrameIdx: Int,
    currentFrame: SessionFrame?,
    perFrameMs: Long,
    now: Long,
    enemies: Map<String, EnemyData>,
    skillLevels: Map<String, Int>,
    attackBonus: Int,
    strengthBonus: Int,
    defenseBonus: Int,
    equippedFood: Map<String, Int>,
    foodHealValues: Map<String, Int>,
    foodConsumedSoFar: Map<String, Int>,
    onViewFood: () -> Unit,
) {
    val tokens   = LocalFantasyTokens.current
    val context  = LocalContext.current
    val divider  = tokens.colors.onSurfaceMuted.copy(alpha = 0.2f)

    // Resolve current enemy key (falls back to last known-from-kill enemy)
    val currentEnemyKey: String? = currentFrame?.enemyKey?.takeIf { it.isNotEmpty() }
        ?: frames.take(currentFrameIdx + 1).lastOrNull { it.killsByEnemy.isNotEmpty() }
            ?.killsByEnemy?.keys?.firstOrNull()
    val currentEnemy = currentEnemyKey?.let { enemies[it] }

    // Tick state (player attack speed is fixed at 2.4s per OSRS combat tick)
    val attackSpeedMs = 2_400L
    val frameStartMs  = session.startedAt + currentFrameIdx.toLong() * perFrameMs
    val maxTick       = (currentFrame?.playerHits?.size?.minus(1) ?: 0).coerceAtLeast(0)
    val tickInFrame   = ((now - frameStartMs) / attackSpeedMs).toInt().coerceIn(0, maxTick)

    // Player HP (per-tick if hit data exists, else per-frame fallback)
    val maxHp = (skillLevels[Skills.HITPOINTS] ?: 1) * 10
    val currentPlayerHp = if (currentFrame?.enemyHits?.isNotEmpty() == true) {
        val base = frames.getOrNull(currentFrameIdx - 1)?.hpAfter ?: maxHp
        (base - currentFrame.enemyHits.take(tickInFrame + 1).sum()).coerceAtLeast(0)
    } else {
        frames.getOrNull(currentFrameIdx - 1)?.hpAfter ?: maxHp
    }

    // Enemy HP (replay player hits in-frame to surface mid-frame kills)
    val currentEnemyHp = if (currentEnemy != null && currentFrame?.playerHits?.isNotEmpty() == true) {
        var hp = currentEnemy.hp
        for (dmg in currentFrame.playerHits.take(tickInFrame + 1)) {
            hp -= dmg
            if (hp <= 0) hp = currentEnemy.hp
        }
        hp.coerceAtLeast(0)
    } else currentEnemy?.hp ?: 0

    val killsSoFar: Map<String, Int> = remember(currentFrameIdx) {
        frames.take(currentFrameIdx).fold(mutableMapOf()) { acc, f ->
            f.killsByEnemy.forEach { (k, v) -> acc[k] = (acc[k] ?: 0) + v }
            acc
        }
    }
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

    // Combat log: last 8 entries (interleaved per tick)
    val combatLog = remember(currentFrameIdx, tickInFrame) {
        buildList {
            for (i in 0 until currentFrameIdx) {
                val f = frames.getOrNull(i) ?: break
                val eName = enemies[f.enemyKey]?.displayName ?: f.enemyKey
                for (t in 0 until maxOf(f.playerHits.size, f.enemyHits.size)) {
                    f.playerHits.getOrNull(t)?.let { add(CombatLogEntry(true, it, eName)) }
                    f.enemyHits.getOrNull(t)?.let { add(CombatLogEntry(false, it, eName)) }
                }
            }
            val f = frames.getOrNull(currentFrameIdx) ?: return@buildList
            val eName = enemies[f.enemyKey]?.displayName ?: f.enemyKey
            for (t in 0..tickInFrame) {
                f.playerHits.getOrNull(t)?.let { add(CombatLogEntry(true, it, eName)) }
                f.enemyHits.getOrNull(t)?.let { add(CombatLogEntry(false, it, eName)) }
            }
        }.takeLast(8)
    }

    Spacer(Modifier.height(tokens.spacing.l))
    Surface(
        shape    = tokens.shapes.card,
        color    = tokens.colors.secondaryContainer,
        border   = BorderStroke(tokens.shapes.hairlineStroke, tokens.colors.primary.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(tokens.spacing.m)) {
            EnemyStatCard(enemy = currentEnemy, currentHp = currentEnemyHp)

            HorizontalDivider(modifier = Modifier.padding(vertical = tokens.spacing.m), color = divider)
            PlayerHpBlock(
                currentPlayerHp = currentPlayerHp,
                maxHp           = maxHp,
                attackBonus     = attackBonus,
                strengthBonus   = strengthBonus,
                defenseBonus    = defenseBonus,
            )

            if (equippedFood.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = tokens.spacing.m), color = divider)
                FoodBlock(
                    equippedFood       = equippedFood,
                    foodHealValues     = foodHealValues,
                    foodConsumedSoFar  = foodConsumedSoFar,
                    onViewFood         = onViewFood,
                )
            }

            if (killsSoFar.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = tokens.spacing.m), color = divider)
                Text(
                    text  = killsSoFar.entries.sortedByDescending { it.value }
                        .joinToString(", ") { (k, v) -> "$v ${enemies[k]?.displayName ?: k}" } +
                        " " + stringResource(R.string.combat_defeated_so_far),
                    style = tokens.typography.bodyMedium,
                    color = tokens.colors.onSurface,
                )
            }

            if (dropsSoFar.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = tokens.spacing.m), color = divider)
                Text(
                    text  = stringResource(R.string.label_drops),
                    style = tokens.typography.labelSmall,
                    color = tokens.colors.onSurfaceMuted,
                )
                Spacer(Modifier.height(tokens.spacing.xs))
                Text(
                    text  = dropsSoFar.entries.sortedByDescending { it.value }
                        .joinToString("  ") { (k, v) -> "${GameStrings.itemName(context, k)} ×$v" },
                    style = tokens.typography.bodyMedium,
                    color = tokens.colors.onSurface,
                )
            }

            if (xpSoFar.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = tokens.spacing.m), color = divider)
                Text(
                    text  = stringResource(R.string.label_xp),
                    style = tokens.typography.labelSmall,
                    color = tokens.colors.onSurfaceMuted,
                )
                Spacer(Modifier.height(tokens.spacing.xs))
                Text(
                    text  = COMBAT_HUD_SKILLS.mapNotNull { skill ->
                        xpSoFar[skill]?.let { skill to it }
                    }.joinToString("  ") { (skill, xp) ->
                        "${GameStrings.skillName(context, skill).take(3).uppercase()} +${xp.formatXp()}"
                    },
                    style = tokens.typography.bodyMedium,
                    color = tokens.colors.onSurface,
                )
            }

            if (combatLog.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = tokens.spacing.m), color = divider)
                CombatLogBlock(entries = combatLog)
            }
        }
    }
}

@Composable
private fun PlayerHpBlock(
    currentPlayerHp: Int,
    maxHp: Int,
    attackBonus: Int,
    strengthBonus: Int,
    defenseBonus: Int,
) {
    val tokens  = LocalFantasyTokens.current
    val hpPct   = if (maxHp > 0) currentPlayerHp * 100 / maxHp else 0
    val hpColor = when {
        hpPct >= 50 -> tokens.colors.success
        hpPct >= 20 -> tokens.colors.warning
        else        -> tokens.colors.error
    }
    val hpLabel = stringResource(R.string.label_hp)

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(
            text       = "$hpLabel: $currentPlayerHp / $maxHp",
            style      = tokens.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color      = hpColor,
        )
    }
    Spacer(Modifier.height(tokens.spacing.xs))
    LinearProgressIndicator(
        progress  = { if (maxHp > 0) currentPlayerHp / maxHp.toFloat() else 0f },
        modifier  = Modifier
            .fillMaxWidth()
            .height(tokens.spacing.m - tokens.spacing.xs)
            .clip(tokens.shapes.chip),
        color     = hpColor,
        trackColor = tokens.colors.onSurfaceMuted.copy(alpha = 0.15f),
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
        Spacer(Modifier.height(tokens.spacing.xs))
        Text(
            text  = bonusParts.joinToString("  "),
            style = tokens.typography.bodyMedium,
            color = tokens.colors.onSurface,
        )
    }
}

@Composable
private fun FoodBlock(
    equippedFood: Map<String, Int>,
    foodHealValues: Map<String, Int>,
    foodConsumedSoFar: Map<String, Int>,
    onViewFood: () -> Unit,
) {
    val tokens  = LocalFantasyTokens.current
    val context = LocalContext.current

    Text(
        text  = stringResource(R.string.label_food),
        style = tokens.typography.labelSmall,
        color = tokens.colors.onSurfaceMuted,
    )
    Spacer(Modifier.height(tokens.spacing.xs))
    for ((key, startQty) in equippedFood) {
        val remaining = (startQty - (foodConsumedSoFar[key] ?: 0)).coerceAtLeast(0)
        val heal      = foodHealValues[key] ?: 0
        val name      = GameStrings.itemName(context, key)
        Text(
            text  = "$name ×$remaining (${stringResource(R.string.combat_heals_hp, heal)})",
            style = tokens.typography.bodyMedium,
            color = if (remaining > 0) tokens.colors.onSurface
                    else tokens.colors.onSurfaceMuted.copy(alpha = 0.4f),
        )
    }
    Spacer(Modifier.height(tokens.spacing.s))
    ChunkyButton(
        text    = stringResource(R.string.combat_view_food),
        onClick = onViewFood,
        variant = ChunkyButtonVariant.Ghost,
    )
}

@Composable
private fun CombatLogBlock(entries: List<CombatLogEntry>) {
    val tokens    = LocalFantasyTokens.current
    val youHit    = stringResource(R.string.combat_log_you_hit)
    val dmgLabel  = stringResource(R.string.combat_log_dmg)
    val youMissed = stringResource(R.string.combat_log_you_missed)
    val hitYou    = stringResource(R.string.combat_log_hit_you)
    val missed    = stringResource(R.string.combat_log_missed)

    Text(
        text  = stringResource(R.string.combat_log_label),
        style = tokens.typography.labelSmall,
        color = tokens.colors.onSurfaceMuted,
    )
    Spacer(Modifier.height(tokens.spacing.xs))
    Column {
        for (entry in entries) {
            val (arrow, dmgText, color) = if (entry.isPlayer) {
                val c: Color = if (entry.damage > 0) tokens.colors.success
                else tokens.colors.onSurfaceMuted.copy(alpha = 0.45f)
                Triple(
                    "→",
                    if (entry.damage > 0) "$youHit ${entry.enemyName}: ${entry.damage} $dmgLabel"
                    else "$youMissed ${entry.enemyName}",
                    c,
                )
            } else {
                val c: Color = if (entry.damage > 0) tokens.colors.error
                else tokens.colors.onSurfaceMuted.copy(alpha = 0.45f)
                Triple(
                    "←",
                    if (entry.damage > 0) "${entry.enemyName} $hitYou: ${entry.damage} $dmgLabel"
                    else "${entry.enemyName} $missed",
                    c,
                )
            }
            Text(
                text  = "$arrow $dmgText",
                style = tokens.typography.bodyMedium,
                color = color,
            )
        }
    }
}

private val sampleSession = SkillSession(
    sessionId    = "preview-session",
    playerId     = 1L,
    skillName    = "combat",
    startedAt    = 0L,
    endsAt       = 60L * 60L * 1000L,
    frames       = "[]",
    completed    = false,
    activityKey  = "dark_cave",
)

private val sampleDungeon = DungeonData(
    name              = "dark_cave",
    displayName       = "Dark Cave",
    description       = "Goblins and rats.",
    recommendedLevel  = 12,
    encounterRate     = 0.6,
    enemySpawns       = emptyList(),
)

@PreviewLightDark
@Composable
private fun PreviewCombatSessionBannerInProgress() {
    FantasyPreviewSurface {
        CombatSessionBanner(
            session        = sampleSession,
            dungeons       = listOf(sampleDungeon),
            bosses         = emptyList(),
            enemies        = emptyMap(),
            skillLevels    = mapOf("hitpoints" to 30),
            attackBonus    = 12,
            strengthBonus  = 10,
            defenseBonus   = 8,
            equippedFood   = mapOf("shark" to 5),
            foodHealValues = mapOf("shark" to 20),
            onCollect      = {},
            onAbandon      = {},
            onDebugFinish  = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewCombatSessionBannerComplete() {
    FantasyPreviewSurface {
        CombatSessionBanner(
            session        = sampleSession.copy(
                endsAt    = 0L,
                completed = true,
            ),
            dungeons       = listOf(sampleDungeon),
            bosses         = emptyList(),
            enemies        = emptyMap(),
            skillLevels    = mapOf("hitpoints" to 30),
            attackBonus    = 0,
            strengthBonus  = 0,
            defenseBonus   = 0,
            equippedFood   = emptyMap(),
            foodHealValues = emptyMap(),
            onCollect      = {},
            onAbandon      = {},
            onDebugFinish  = {},
        )
    }
}
