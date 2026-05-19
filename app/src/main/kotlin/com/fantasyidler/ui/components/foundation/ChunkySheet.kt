package com.fantasyidler.ui.components.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.material3.Text
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/**
 * Chunky bottom-sheet wrapper — uses [com.fantasyidler.ui.theme.fantasy.FantasyShapes.sheet]
 * with a gold-bordered top edge and tokens.spacing.l content padding. The drag
 * handle is rendered manually so it picks up the same gold tone as the border.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChunkySheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState       = sheetState,
        shape            = tokens.shapes.sheet,
        containerColor   = tokens.colors.surface,
        contentColor     = tokens.colors.onSurface,
        dragHandle       = { ChunkyDragHandle() },
        modifier         = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.l),
        ) {
            content()
        }
    }
}

@Composable
private fun ChunkyDragHandle() {
    val tokens = LocalFantasyTokens.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = tokens.spacing.m),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(tokens.spacing.xxl + tokens.spacing.s)
                .height(tokens.spacing.s)
                .background(
                    color = tokens.colors.primary.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(tokens.spacing.xs),
                ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun PreviewChunkySheetHandle() {
    FantasyPreviewSurface {
        // Previews can't host a ModalBottomSheet directly; render the handle
        // + a stand-in surface so the visual language is verifiable.
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
        ) {
            ChunkyDragHandle()
            Text(
                text = "Sheet content area",
                modifier = Modifier.padding(top = LocalFantasyTokens.current.spacing.xl),
            )
        }
    }
}
