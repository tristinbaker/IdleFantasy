package com.fantasyidler.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fantasyidler.R

/** One attribution row: [url] is null for artists credited by name only. */
private data class ArtCredit(
    val title: String,
    val subtitle: String,
    val url: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtCreditsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val credits = listOf(
        ArtCredit(
            title    = stringResource(R.string.settings_credit_story_title),
            subtitle = stringResource(R.string.settings_credit_story_subtitle),
            url      = "https://letterboxd.com/bathtubskeleton/",
        ),
        ArtCredit(
            title    = stringResource(R.string.settings_credit_banners_title),
            subtitle = stringResource(R.string.settings_credit_banners_subtitle),
            url      = "https://wenrexa.itch.io/banners-kingdoms",
        ),
        ArtCredit(
            title    = stringResource(R.string.settings_credit_skill_icons_title),
            subtitle = stringResource(R.string.settings_credit_skill_icons_subtitle),
            url      = "https://shikashipx.itch.io/shikashis-fantasy-icons-pack",
        ),
        ArtCredit(
            title    = stringResource(R.string.settings_credit_characters_title),
            subtitle = stringResource(R.string.settings_credit_characters_subtitle),
            url      = "https://ko-fi.com/pixylac",
        ),
        ArtCredit(
            title    = stringResource(R.string.settings_credit_bosses_title),
            subtitle = stringResource(R.string.settings_credit_bosses_subtitle),
            url      = null,
        ),
        ArtCredit(
            title    = stringResource(R.string.settings_credit_house_title),
            subtitle = stringResource(R.string.settings_credit_house_subtitle),
            url      = "https://elvgames.itch.io/rogue-adventure-interior",
        ),
    )

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_art_credits)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                credits.forEach { credit ->
                    SettingsRow(
                        title    = credit.title,
                        subtitle = credit.subtitle,
                        trailing = credit.url?.let { url ->
                            {
                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                ) {
                                    Text(stringResource(R.string.settings_source_open))
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
