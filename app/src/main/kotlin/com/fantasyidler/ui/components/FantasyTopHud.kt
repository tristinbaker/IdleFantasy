package com.fantasyidler.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.R
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.ui.motion.pressScale
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.formatCoins
import com.fantasyidler.util.toCountdown
import kotlinx.coroutines.delay

/**
 * The persistent top heads-up display. Survives every tab switch (only the body
 * fades between destinations). Three regions:
 *
 *  - Left:   Level chip (combat level) → tap navigates to Profile.
 *  - Middle: Coins, with [AnimatedCounter] so gains/losses tick rather than jump.
 *  - Right:  Active-session pill — skill emoji + smoothly-animated countdown.
 *            Tap navigates to Skills so the player can interact with the session.
 *            Collapses to a faint "Start a session" prompt when nothing's running.
 *
 * @param onShop temp navigation target for the coin tap. PR #10 replaces this
 *               with a bottom-sheet that opens in place.
 * @param onSettings temp settings entry point. PR #11 removes this in favour
 *                   of a gear icon on the Profile screen.
 */
@Composable
fun FantasyTopHud(
    coins: Long,
    combatLevel: Int,
    activeSession: SkillSession?,
    onProfile: () -> Unit,
    onShop: () -> Unit,
    onSession: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFantasyTokens.current
    // The Surface fills to the very top of the screen so its background colour
    // sits beneath the system status bar (clock/signal/battery) — visually a
    // single continuous bar. The inner Row's windowInsetsPadding(statusBars)
    // pushes the actual HUD content down past the system icons.
    Surface(
        color          = tokens.colors.surface,
        tonalElevation = tokens.elevation.card,
        modifier       = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(tokens.spacing.xxl + tokens.spacing.xl)
                .padding(horizontal = tokens.spacing.m + tokens.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LevelChip(
                level = combatLevel,
                label = "Lv",
                onClick = onProfile,
            )

            Spacer(Modifier.width(tokens.spacing.m))

            CoinTap(coins = coins, onTap = onShop)

            Spacer(Modifier.weight(1f))

            HudSessionPill(session = activeSession, onTap = onSession)

            Spacer(Modifier.width(tokens.spacing.m))

            HudGear(onTap = onSettings)
        }
    }
}

@Composable
private fun CoinTap(
    coins: Long,
    onTap: () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val cd = stringResource(R.string.hud_coins_cd)
    Surface(
        shape = tokens.shapes.button,
        color = tokens.colors.primary.copy(alpha = 0.18f),
        modifier = Modifier
            .pressScale(interactionSource)
            .defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.l)
            .semantics { contentDescription = cd }
            .clickable(
                interactionSource = interactionSource,
                indication        = LocalIndication.current,
                onClick           = onTap,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = tokens.spacing.m + tokens.spacing.xs, vertical = tokens.spacing.s + tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text  = "🪙",
                style = tokens.typography.titleLarge,
            )
            Spacer(Modifier.width(tokens.spacing.s + tokens.spacing.xs))
            AnimatedCounter(
                value      = coins,
                format     = { it.formatCoins() },
                style      = tokens.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color      = tokens.colors.primary,
            )
        }
    }
}

@Composable
private fun HudSessionPill(
    session: SkillSession?,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val tokens = LocalFantasyTokens.current
    val interactionSource = remember { MutableInteractionSource() }

    if (session == null) {
        val idleCd = stringResource(R.string.hud_session_idle_cd)
        Surface(
            shape = tokens.shapes.button,
            color = tokens.colors.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .pressScale(interactionSource)
                .defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.l)
                .semantics { contentDescription = idleCd }
                .clickable(
                    interactionSource = interactionSource,
                    indication        = LocalIndication.current,
                    onClick           = onTap,
                ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = tokens.spacing.m + tokens.spacing.xs, vertical = tokens.spacing.s + tokens.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text  = stringResource(R.string.hud_session_idle),
                    style = tokens.typography.labelSmall,
                    color = tokens.colors.onSurfaceMuted,
                )
                Spacer(Modifier.width(tokens.spacing.xs))
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint               = tokens.colors.onSurfaceMuted,
                    modifier           = Modifier.size(tokens.spacing.l),
                )
            }
        }
        return
    }

    val endsAt = session.endsAt
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (System.currentTimeMillis() < endsAt) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
        now = System.currentTimeMillis()
    }
    val isDone = session.completed || now >= endsAt
    val skillEmoji = GameStrings.skillEmoji(session.skillName)
    val skillLabel = if (session.skillName == "combat") "Combat"
                     else GameStrings.skillName(context, session.skillName)
    val secondsRemaining = ((endsAt - now) / 1_000L).coerceAtLeast(0L)
    val activeCd = if (isDone) stringResource(R.string.hud_session_claim_cd, skillLabel)
                   else stringResource(R.string.hud_session_active_cd, skillLabel)

    Surface(
        shape = tokens.shapes.button,
        color = if (isDone) tokens.colors.primary.copy(alpha = 0.22f)
                else tokens.colors.secondaryContainer,
        modifier = Modifier
            .pressScale(interactionSource)
            .defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.l)
            .semantics { contentDescription = activeCd }
            .clickable(
                interactionSource = interactionSource,
                indication        = LocalIndication.current,
                onClick           = onTap,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = tokens.spacing.m + tokens.spacing.xs, vertical = tokens.spacing.s + tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text       = "$skillEmoji $skillLabel",
                style      = tokens.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color      = tokens.colors.onSurface,
            )
            Spacer(Modifier.width(tokens.spacing.m))
            if (isDone) {
                Text(
                    text       = stringResource(R.string.hud_session_claim),
                    style      = tokens.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color      = tokens.colors.primary,
                )
                Spacer(Modifier.width(tokens.spacing.xs))
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint               = tokens.colors.primary,
                    modifier           = Modifier.size(tokens.spacing.l),
                )
            } else {
                AnimatedCounter(
                    value      = secondsRemaining,
                    format     = { endsAt.toCountdown() },
                    style      = tokens.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color      = tokens.colors.onSurface,
                )
            }
        }
    }
}

@Composable
private fun HudGear(onTap: () -> Unit) {
    val tokens = LocalFantasyTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val cd = stringResource(R.string.hud_settings_cd)
    Surface(
        shape = tokens.shapes.button,
        color = tokens.colors.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .pressScale(interactionSource)
            .defaultMinSize(minWidth = tokens.spacing.xxl + tokens.spacing.l, minHeight = tokens.spacing.xxl + tokens.spacing.l)
            .clickable(
                interactionSource = interactionSource,
                indication        = LocalIndication.current,
                onClick           = onTap,
            ),
    ) {
        Box(
            modifier         = Modifier.padding(tokens.spacing.m),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Filled.Settings,
                contentDescription = cd,
                tint               = tokens.colors.onSurfaceMuted,
                modifier           = Modifier.size(tokens.spacing.xl - tokens.spacing.xs),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewFantasyTopHudIdle() {
    FantasyPreviewSurface {
        FantasyTopHud(
            coins         = 12_345L,
            combatLevel   = 27,
            activeSession = null,
            onProfile     = {},
            onShop        = {},
            onSession     = {},
            onSettings    = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewFantasyTopHudActiveSession() {
    FantasyPreviewSurface {
        FantasyTopHud(
            coins         = 1_204_567L,
            combatLevel   = 80,
            activeSession = SkillSession(
                sessionId = "preview",
                skillName = "mining",
                startedAt = 0L,
                endsAt    = 600_000L,
                completed = false,
            ),
            onProfile     = {},
            onShop        = {},
            onSession     = {},
            onSettings    = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewFantasyTopHudClaimable() {
    FantasyPreviewSurface {
        FantasyTopHud(
            coins         = 999_999_999L,
            combatLevel   = 126,
            activeSession = SkillSession(
                sessionId = "preview",
                skillName = "fishing",
                startedAt = 0L,
                endsAt    = 1L,
                completed = true,
            ),
            onProfile     = {},
            onShop        = {},
            onSession     = {},
            onSettings    = {},
        )
    }
}
