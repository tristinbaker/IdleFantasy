package com.fantasyidler.ui.screen.minigame

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.fantasyidler.ui.components.EntityIcon
import com.fantasyidler.ui.viewmodel.MarketCenterUiState

/**
 * Renders the current adventurer sprite. Selects between four idle sprites
 * and eight walk sprites (4 directions × 2 frames) based on state. Pure
 * stateless — state lives in `MarketCenterViewModel`.
 */
@Composable
fun WalkingAvatar(
    state: MarketCenterUiState,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
) {
    val dir = state.facing.slug
    val entityId = if (state.isMoving) {
        "adventurer_walk_${dir}_${if (state.walkFrameA) "a" else "b"}"
    } else {
        "adventurer_idle_$dir"
    }
    EntityIcon(
        entityId = entityId,
        size     = sizeDp,
        label    = "adventurer",
        modifier = modifier,
    )
}
