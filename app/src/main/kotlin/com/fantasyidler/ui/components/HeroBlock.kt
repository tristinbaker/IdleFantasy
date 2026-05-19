package com.fantasyidler.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.components.foundation.HeroBlock as FoundationHeroBlock

/** Thin proxy — see [com.fantasyidler.ui.components.foundation.HeroBlock]. */
@Composable
fun HeroBlock(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
    accentColor: Color = GoldPrimary,
) = FoundationHeroBlock(
    title = title,
    modifier = modifier,
    subtitle = subtitle,
    leading = leading,
    trailing = trailing,
    content = content,
    accentColor = accentColor,
)
