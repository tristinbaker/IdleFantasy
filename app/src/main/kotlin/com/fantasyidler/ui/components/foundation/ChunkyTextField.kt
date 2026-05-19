package com.fantasyidler.ui.components.foundation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/**
 * Chunky-styled text input — for name input, search, simple form fields. Built
 * on [BasicTextField] so the surface chrome stays pixel-clean instead of
 * inheriting Material's outlined-field metrics.
 */
@Composable
fun ChunkyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
) {
    val tokens = LocalFantasyTokens.current
    Surface(
        shape  = tokens.shapes.button,
        color  = tokens.colors.surface,
        border = BorderStroke(tokens.shapes.hairlineStroke, tokens.colors.primary.copy(alpha = 0.45f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m + tokens.spacing.xs)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                textStyle = LocalTextStyle.current.merge(
                    tokens.typography.bodyLarge.copy(color = tokens.colors.onSurface, fontWeight = FontWeight.Normal),
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(tokens.colors.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType   = keyboardType,
                    capitalization = capitalization,
                    imeAction      = imeAction,
                ),
                keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (value.isEmpty() && placeholder != null) {
                Text(
                    text  = placeholder,
                    style = tokens.typography.bodyLarge,
                    color = tokens.colors.onSurfaceMuted,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PreviewChunkyTextField() {
    FantasyPreviewSurface {
        ChunkyTextField(value = "", onValueChange = {}, placeholder = "Character name")
    }
}
