package com.fantasyidler.ui.screen

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.ui.theme.GoldPrimary
import kotlinx.coroutines.launch

private data class OnboardingPage(val emoji: String, val title: String, val body: String)

private val PAGES = listOf(
    OnboardingPage(
        emoji = "⚔️",
        title = "Welcome to Idle Fantasy",
        body  = "An idle RPG inspired by classic MMORPGs. Train skills, fight dungeons, and craft gear. All offline, no accounts, no ads.",
    ),
    OnboardingPage(
        emoji = "⏱️",
        title = "Sessions",
        body  = "Tap any skill and start a training session. Sessions run in the background, even with the app closed. Come back to collect your loot and XP.",
    ),
    OnboardingPage(
        emoji = "🏆",
        title = "Quests & Pets",
        body  = "Complete quests to earn XP rewards. Rare pets can drop while training skills and provide permanent XP boosts.",
    ),
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PAGES.size })
    val scope      = rememberCoroutineScope()

    Surface(
        color    = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state    = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                PageContent(PAGES[index])
            }

            // Bottom controls
            Column(
                modifier            = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Page dots
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(PAGES.size) { i ->
                        val selected = pagerState.currentPage == i
                        Surface(
                            shape    = CircleShape,
                            color    = if (selected) GoldPrimary
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            modifier = Modifier.size(if (selected) 10.dp else 7.dp),
                        ) {}
                    }
                }

                if (pagerState.currentPage == PAGES.lastIndex) {
                    Button(
                        onClick  = onComplete,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.onboarding_lets_go), fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onComplete) { Text(stringResource(R.string.onboarding_skip)) }
                        Button(
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            },
                        ) { Text(stringResource(R.string.onboarding_next)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageContent(page: OnboardingPage) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text  = page.emoji,
            style = MaterialTheme.typography.displayMedium,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text       = page.title,
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text      = page.body,
            style     = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Extra space so content sits above the bottom controls
        Spacer(Modifier.height(160.dp))
    }
}
