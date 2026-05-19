package com.fantasyidler.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.R
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant
import com.fantasyidler.ui.components.foundation.ChunkyCard
import com.fantasyidler.ui.components.foundation.IconDisk
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import kotlinx.coroutines.launch

private data class OnboardingPage(val emoji: String, val title: String, val body: String)

@Composable
private fun rememberPages() = listOf(
    OnboardingPage(
        emoji = "⚔️",
        title = stringResource(R.string.onboarding_welcome_title),
        body  = stringResource(R.string.onboarding_welcome_desc),
    ),
    OnboardingPage(
        emoji = "⏱️",
        title = stringResource(R.string.onboarding_sessions_title),
        body  = stringResource(R.string.onboarding_sessions_desc),
    ),
    OnboardingPage(
        emoji = "⚒️",
        title = stringResource(R.string.onboarding_progress_title),
        body  = stringResource(R.string.onboarding_progress_desc),
    ),
    OnboardingPage(
        emoji = "🏆",
        title = stringResource(R.string.onboarding_quests_title),
        body  = stringResource(R.string.onboarding_quests_desc),
    ),
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pages      = rememberPages()
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope      = rememberCoroutineScope()
    val tokens     = LocalFantasyTokens.current

    Surface(
        color    = tokens.colors.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                PageContent(pages[index])
            }

            Column(
                modifier            = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = tokens.spacing.xxl)
                    .padding(bottom = tokens.spacing.xxl + tokens.spacing.l),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.l),
            ) {
                PageDots(
                    count   = pages.size,
                    current = pagerState.currentPage,
                )

                if (pagerState.currentPage == pages.lastIndex) {
                    ChunkyButton(
                        text     = stringResource(R.string.onboarding_lets_go),
                        onClick  = onComplete,
                        modifier = Modifier.fillMaxWidth(),
                        variant  = ChunkyButtonVariant.Primary,
                    )
                } else {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        ChunkyButton(
                            text    = stringResource(R.string.onboarding_skip),
                            onClick = onComplete,
                            variant = ChunkyButtonVariant.Ghost,
                        )
                        ChunkyButton(
                            text    = stringResource(R.string.onboarding_next),
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            },
                            variant = ChunkyButtonVariant.Primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    val tokens = LocalFantasyTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.m)) {
        repeat(count) { i ->
            val selected = current == i
            Box(
                modifier = Modifier
                    .size(if (selected) tokens.spacing.m + tokens.spacing.xs else tokens.spacing.m - tokens.spacing.xs)
                    .background(
                        color = if (selected) tokens.colors.primary
                                else tokens.colors.onSurfaceMuted.copy(alpha = 0.45f),
                        shape = tokens.shapes.badge,
                    ),
            )
        }
    }
}

@Composable
private fun PageContent(page: OnboardingPage) {
    val tokens = LocalFantasyTokens.current
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = tokens.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconDisk(
            emoji = page.emoji,
            size  = tokens.spacing.xxl * 3,
        )
        Spacer(Modifier.height(tokens.spacing.xl))
        ChunkyCard {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text       = page.title,
                    style      = tokens.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color      = tokens.colors.onSurface,
                    textAlign  = TextAlign.Center,
                )
                Spacer(Modifier.height(tokens.spacing.m + tokens.spacing.s))
                Text(
                    text      = page.body,
                    style     = tokens.typography.bodyLarge,
                    color     = tokens.colors.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // Reserve room so the body never collides with the bottom controls.
        Spacer(Modifier.height(tokens.spacing.xxl * 5))
    }
}

@PreviewLightDark
@Composable
private fun PreviewOnboardingFirstPage() {
    FantasyPreviewSurface {
        PageContent(
            page = OnboardingPage(
                emoji = "⚔️",
                title = "Welcome to Idle Fantasy",
                body  = "Begin your adventure — sessions run in the background while you live your life.",
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewOnboardingDotsMiddle() {
    FantasyPreviewSurface {
        PageDots(count = 4, current = 2)
    }
}

@PreviewLightDark
@Composable
private fun PreviewOnboardingDotsLast() {
    FantasyPreviewSurface {
        PageDots(count = 4, current = 3)
    }
}
