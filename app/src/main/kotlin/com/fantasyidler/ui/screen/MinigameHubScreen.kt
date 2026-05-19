package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.R
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant
import com.fantasyidler.ui.components.foundation.ChunkyCard
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/**
 * Placeholder "city hall" hub. The real feature (bartending and other side
 * minigames that mint Time Points + Time Vouchers with a relevance bonus) will
 * replace this body in a follow-up — see plan §3.
 *
 * TODO(minigame-hub): wire actual minigames. Data model sketch —
 *   - TimePoint ledger on PlayerFlags
 *   - TimeVoucher purchase that subtracts ms from the active SkillSession.endsAt
 *   - Minigame catalog (bartender, blacksmith, kitchen, courier) with per-game
 *     point yields and relevance hooks ("current skill matches" → 1.5×).
 */
@Composable
fun MinigameHubScreen(onBack: () -> Unit = {}) {
    val tokens = LocalFantasyTokens.current
    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier            = Modifier
                    .fillMaxSize()
                    .padding(tokens.spacing.l),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.l),
            ) {
                Spacer(Modifier.height(tokens.spacing.xl))
                Surface(
                    shape    = CircleShape,
                    color    = tokens.colors.primary.copy(alpha = 0.18f),
                    modifier = Modifier.size(tokens.spacing.xxl + tokens.spacing.xxl),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector        = Icons.Filled.LocationCity,
                            contentDescription = null,
                            tint               = tokens.colors.primary,
                            modifier           = Modifier.size(tokens.spacing.xxl),
                        )
                    }
                }
                Text(
                    text       = stringResource(R.string.minigame_hub_title),
                    style      = tokens.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color      = tokens.colors.onSurface,
                )
                Text(
                    text       = stringResource(R.string.minigame_hub_subtitle),
                    style      = tokens.typography.bodyMedium,
                    color      = tokens.colors.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                ChunkyCard {
                    Text(
                        text  = stringResource(R.string.minigame_hub_body),
                        style = tokens.typography.bodyMedium,
                        color = tokens.colors.onSurface,
                    )
                }
                ChunkyButton(
                    text     = stringResource(R.string.minigame_hub_back),
                    onClick  = onBack,
                    variant  = ChunkyButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewMinigameHubScreen() {
    FantasyPreviewSurface {
        MinigameHubScreen()
    }
}
