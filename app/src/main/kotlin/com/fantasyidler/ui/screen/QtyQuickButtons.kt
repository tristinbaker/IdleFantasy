package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fantasyidler.R

/**
 * Two rows of quick-select buttons for quantity pickers.
 * Row 1 — absolute: 1, 60, 180, Max
 * Row 2 — relative: -100, -10, +10, +100
 * All values are clamped to [1, max] before calling [onSet].
 */
@Composable
fun QtyQuickButtons(qty: Int, max: Int, onSet: (Int) -> Unit) {
    val safeMax = max.coerceAtLeast(1)
    val set = { v: Int -> onSet(v.coerceIn(1, safeMax)) }

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, androidx.compose.ui.Alignment.CenterHorizontally),
    ) {
        listOf(1, 60, 180).forEach { preset ->
            SuggestionChip(
                onClick = { set(preset) },
                label   = { Text("$preset") },
                enabled = safeMax >= preset && qty != preset,
            )
        }
        SuggestionChip(
            onClick = { set(safeMax) },
            label   = { Text(stringResource(R.string.qty_max)) },
            enabled = qty != safeMax,
        )
    }

    Spacer(Modifier.height(4.dp))

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, androidx.compose.ui.Alignment.CenterHorizontally),
    ) {
        listOf(-100, -10, +10, +100).forEach { delta ->
            val label = if (delta > 0) "+$delta" else "$delta"
            SuggestionChip(
                onClick = { set(qty + delta) },
                label   = { Text(label) },
                enabled = if (delta > 0) qty < safeMax else qty > 1,
            )
        }
    }
}
