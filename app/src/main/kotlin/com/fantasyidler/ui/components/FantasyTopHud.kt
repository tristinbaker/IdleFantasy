package com.fantasyidler.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.ui.motion.pressScale
import com.fantasyidler.ui.theme.GoldPrimary
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
    // The Surface fills to the very top of the screen so its background colour
    // sits beneath the system status bar (clock/signal/battery) — visually a
    // single continuous bar. The inner Row's windowInsetsPadding(statusBars)
    // pushes the actual HUD content down past the system icons.
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LevelChip(
                level = combatLevel,
                label = "Lv",
                onClick = onProfile,
            )

            Spacer(Modifier.width(8.dp))

            CoinTap(coins = coins, onTap = onShop)

            Spacer(Modifier.weight(1f))

            HudSessionPill(session = activeSession, onTap = onSession)

            Spacer(Modifier.width(8.dp))

            HudGear(onTap = onSettings)
        }
    }
}

@Composable
private fun CoinTap(
    coins: Long,
    onTap: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = GoldPrimary.copy(alpha = 0.18f),
        modifier = Modifier
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onTap,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "🪙",
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.width(6.dp))
            AnimatedCounter(
                value = coins,
                format = { it.formatCoins() },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = GoldPrimary,
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
    val interactionSource = remember { MutableInteractionSource() }

    if (session == null) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .pressScale(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onTap,
                ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Start a session",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
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

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDone) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onTap,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$skillEmoji $skillLabel",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isDone) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(8.dp))
            if (isDone) {
                Text(
                    text = "Claim",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                AnimatedCounter(
                    value = secondsRemaining,
                    format = { endsAt.toCountdown() },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun HudGear(onTap: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onTap,
            ),
    ) {
        Box(
            modifier = Modifier.padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
