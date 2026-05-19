package com.fantasyidler.ui.screen.profile.inventory

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.R
import com.fantasyidler.ui.screen.profile.ProfileEmptyState
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens
import com.fantasyidler.util.GameStrings

/**
 * Profile → Inventory tab. Lists every item key in the player's bag with its
 * quantity. Falls back to a chunky [ProfileEmptyState] when the bag is empty.
 */
@Composable
fun InventorySection(
    inventory: Map<String, Int>,
    context: Context,
    modifier: Modifier = Modifier,
) {
    if (inventory.isEmpty()) {
        ProfileEmptyState(
            title       = stringResource(R.string.label_empty),
            description = stringResource(R.string.profile_inventory_empty),
            modifier    = modifier,
        )
        return
    }
    val tokens = LocalFantasyTokens.current
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(inventory.entries.toList(), key = { it.key }) { (key, qty) ->
            InventoryRow(
                name = GameStrings.itemName(context, key),
                qty  = qty,
            )
        }
        item { Spacer(Modifier.height(tokens.spacing.l)) }
    }
}

@Composable
private fun InventoryRow(name: String, qty: Int) {
    val tokens = LocalFantasyTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.l)
            .padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m + tokens.spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(
            text  = name,
            style = tokens.typography.bodyLarge,
            color = tokens.colors.onSurface,
        )
        Text(
            text       = "×$qty",
            style      = tokens.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color      = tokens.colors.onSurfaceMuted,
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = tokens.spacing.l),
        color    = tokens.colors.border.copy(alpha = 0.12f),
    )
}

@PreviewLightDark
@Composable
private fun PreviewInventorySectionEmpty() {
    FantasyPreviewSurface {
        InventorySection(inventory = emptyMap(), context = LocalContext.current)
    }
}

@PreviewLightDark
@Composable
private fun PreviewInventorySectionPopulated() {
    FantasyPreviewSurface {
        InventorySection(
            inventory = linkedMapOf(
                "iron_ore"     to 42,
                "copper_ore"   to 17,
                "raw_shrimp"   to 8,
                "mithril_bar"  to 3,
            ),
            context = LocalContext.current,
        )
    }
}
