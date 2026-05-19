package com.fantasyidler.ui.screen.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.R
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/**
 * Token-driven loading state used by every Profile tab — a centered spinner
 * tinted with the gold primary plus an accessible label for screen readers.
 */
@Composable
internal fun ProfileLoadingState(
    label: String = stringResource(R.string.profile_loading),
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFantasyTokens.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(color = tokens.colors.primary)
            Spacer(Modifier.height(tokens.spacing.l))
            Text(
                text  = label,
                style = tokens.typography.bodyMedium,
                color = tokens.colors.onSurfaceMuted,
            )
        }
    }
}

/**
 * Token-driven empty state. Centered, parchment-muted, with a short title +
 * optional description. Wrapped in a min-tap-target Box so VoiceOver users can
 * still reliably swipe to it.
 */
@Composable
internal fun ProfileEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val tokens = LocalFantasyTokens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.xl, vertical = tokens.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text      = title,
            style     = tokens.typography.titleLarge,
            color     = tokens.colors.onSurface,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Spacer(Modifier.height(tokens.spacing.m))
            Text(
                text      = description,
                style     = tokens.typography.bodyMedium,
                color     = tokens.colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Token-driven error state with retry CTA. Profile VMs don't surface errors
 * today, but this primitive previews the chunky design language so the screen
 * is ready the moment an error field is wired.
 */
@Composable
internal fun ProfileErrorState(
    message: String = stringResource(R.string.state_error_generic),
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFantasyTokens.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.xl, vertical = tokens.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text      = message,
            style     = tokens.typography.bodyLarge,
            color     = tokens.colors.error,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(tokens.spacing.l))
            ChunkyButton(
                text    = stringResource(R.string.state_retry),
                onClick = onRetry,
                variant = ChunkyButtonVariant.Secondary,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewProfileLoadingState() {
    FantasyPreviewSurface { ProfileLoadingState() }
}

@PreviewLightDark
@Composable
private fun PreviewProfileEmptyState() {
    FantasyPreviewSurface {
        ProfileEmptyState(
            title = "Your bag is empty",
            description = "Train a skill to gather items.",
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewProfileErrorState() {
    FantasyPreviewSurface {
        ProfileErrorState(message = "Couldn't load profile.", onRetry = {})
    }
}
