package com.fantasyidler.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.fantasyidler.ui.theme.GoldPrimary
import com.fantasyidler.ui.components.foundation.ClaimBadge as FoundationClaimBadge
import com.fantasyidler.ui.components.foundation.ClaimStamp as FoundationClaimStamp

/** Thin proxy — see [com.fantasyidler.ui.components.foundation.ClaimBadge]. */
@Composable
fun ClaimBadge(
    text: String,
    modifier: Modifier = Modifier,
    pulse: Boolean = true,
    color: Color = GoldPrimary,
    icon: ImageVector? = null,
) = FoundationClaimBadge(text = text, modifier = modifier, pulse = pulse, color = color, icon = icon)

/** Thin proxy — see [com.fantasyidler.ui.components.foundation.ClaimStamp]. */
@Composable
fun ClaimStamp(
    text: String,
    modifier: Modifier = Modifier,
) = FoundationClaimStamp(text = text, modifier = modifier)
