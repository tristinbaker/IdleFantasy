package com.fantasyidler.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.R
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant
import com.fantasyidler.ui.components.foundation.ChunkyDialog
import com.fantasyidler.ui.screen.CharacterSetupSheet
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.ui.viewmodel.SessionSummary
import com.fantasyidler.util.formatCoins

/**
 * The four global flows that used to live inside HomeScreen.kt and only fired
 * when the player was on the Home tab. Hoisted to the root so they fire from
 * any destination:
 *
 *  - Session-summary dialog (after a Claim).
 *  - "What's new" dialog (first launch after an upgrade).
 *  - Character-setup sheet (first launch — blocking).
 *  - (Reserved) pending-collect surfacing — currently routed through the
 *    HUD's session pill which calls collectSession() when there are
 *    pending sessions.
 *
 * Stateless: the caller (AppNavigation) owns the state + callbacks.
 */
@Composable
fun GlobalGameOverlay(
    sessionSummary: SessionSummary?,
    showWhatsNew: Boolean,
    characterSetupDone: Boolean,
    characterName: String,
    onSummaryConsumed: () -> Unit,
    onDismissWhatsNew: () -> Unit,
    onSaveCharacter: (name: String, gender: String, race: String) -> Unit,
    onDismissCharacterSetup: () -> Unit,
) {
    sessionSummary?.let { summary ->
        SessionSummaryDialog(summary, onSummaryConsumed)
    }

    if (showWhatsNew) {
        WhatsNewDialog(onDismissWhatsNew)
    }

    if (!characterSetupDone) {
        CharacterSetupSheet(
            isFirstTime = true,
            initialName = characterName,
            onSave      = onSaveCharacter,
            onDismiss   = onDismissCharacterSetup,
        )
    }
}

@Composable
private fun SessionSummaryDialog(
    summary: SessionSummary,
    onDismiss: () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    ChunkyDialog(
        title            = summary.title,
        onDismissRequest = onDismiss,
        body = {
            Column(
                modifier            = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.s),
            ) {
                if (summary.died) {
                    Text(
                        text  = stringResource(R.string.home_died_message),
                        style = tokens.typography.bodyMedium,
                        color = tokens.colors.error,
                    )
                    Spacer(Modifier.height(tokens.spacing.s))
                }
                if (summary.boostWasActive) {
                    Text(
                        text       = stringResource(R.string.home_xp_boost_was_active),
                        style      = tokens.typography.labelSmall,
                        color      = tokens.colors.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(tokens.spacing.s))
                }
                if (summary.xpLines.isNotEmpty()) {
                    SummarySection(stringResource(R.string.label_xp_gained))
                    summary.xpLines.forEach { (skill, label) -> SummaryRow(skill, label) }
                } else if (summary.totalXpLabel.isNotEmpty()) {
                    SummaryRow(stringResource(R.string.label_xp_gained), summary.totalXpLabel)
                }
                if (summary.killLines.isNotEmpty()) {
                    Spacer(Modifier.height(tokens.spacing.s))
                    SummarySection(stringResource(R.string.label_kills))
                    summary.killLines.forEach { (enemy, kills) -> SummaryRow(enemy, kills) }
                }
                if (summary.itemLines.isNotEmpty()) {
                    Spacer(Modifier.height(tokens.spacing.s))
                    SummarySection(stringResource(R.string.home_loot))
                    summary.itemLines.forEach { (item, qty) -> SummaryRow(item, qty) }
                }
                if (summary.coinsGained > 0) {
                    SummaryRow(stringResource(R.string.label_coins), "+${summary.coinsGained.formatCoins()}")
                }
                if (summary.foodConsumedLines.isNotEmpty()) {
                    Spacer(Modifier.height(tokens.spacing.s))
                    SummarySection(stringResource(R.string.home_food_consumed))
                    summary.foodConsumedLines.forEach { (food, qty) -> SummaryRow(food, qty) }
                }
                if (summary.boneBuriedLabel.isNotEmpty()) {
                    SummaryRow(stringResource(R.string.home_bones_buried), summary.boneBuriedLabel)
                }
            }
        },
        actions = {
            ChunkyButton(
                text    = stringResource(R.string.btn_close),
                onClick = onDismiss,
                variant = ChunkyButtonVariant.Primary,
            )
        },
    )
}

@Composable
private fun WhatsNewDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val tokens = LocalFantasyTokens.current
    val changelogText = remember {
        runCatching { context.assets.open("changelog.txt").bufferedReader().readText().trim() }
            .getOrElse { "" }
    }
    if (changelogText.isEmpty()) return
    ChunkyDialog(
        title            = stringResource(R.string.home_whats_new),
        onDismissRequest = onDismiss,
        body = {
            Text(
                text  = changelogText,
                style = tokens.typography.bodyMedium,
                color = tokens.colors.onSurface,
            )
        },
        actions = {
            ChunkyButton(
                text    = stringResource(R.string.home_got_it),
                onClick = onDismiss,
                variant = ChunkyButtonVariant.Primary,
            )
        },
    )
}

@Composable
private fun SummarySection(title: String) {
    val tokens = LocalFantasyTokens.current
    Text(
        text  = title,
        style = tokens.typography.labelSmall,
        color = tokens.colors.onSurfaceMuted,
    )
}

@Composable
private fun SummaryRow(label: String, value: String) {
    val tokens = LocalFantasyTokens.current
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text     = label,
            style    = tokens.typography.bodyMedium,
            color    = tokens.colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text       = value,
            style      = tokens.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color      = tokens.colors.onSurface,
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewSummaryRow() {
    FantasyPreviewSurface {
        Column {
            SummarySection("XP Gained")
            SummaryRow("Mining", "+12,400")
            SummaryRow("Smithing", "+3,250")
        }
    }
}
