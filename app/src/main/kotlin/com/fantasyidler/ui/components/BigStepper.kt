package com.fantasyidler.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fantasyidler.ui.components.foundation.BigStepper as FoundationBigStepper

/** Thin proxy — see [com.fantasyidler.ui.components.foundation.BigStepper]. */
@Composable
fun BigStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minValue: Int = 1,
    maxValue: Int = Int.MAX_VALUE,
    step: Int = 1,
    onMax: (() -> Unit)? = null,
) = FoundationBigStepper(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    minValue = minValue,
    maxValue = maxValue,
    step = step,
    onMax = onMax,
)
