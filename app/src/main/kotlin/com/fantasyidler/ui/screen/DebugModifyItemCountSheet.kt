package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DebugModifyItemCountSheet(
    onAddItem: (name: String, amount: Int) -> Unit,
    onRemoveItem: (name: String, amount: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftID by remember { mutableStateOf("") }
    var draftAmountText by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        dragHandle       = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text  = "Debug: Modify Item Count",
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value         = draftID,
                onValueChange = { draftID = it },
                label         = { Text("Item ID") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value         = draftAmountText,
                onValueChange = { new ->
                    draftAmountText = new.filter { it.isDigit() }
                },
                label         = { Text("Item Amount") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
       

            HorizontalDivider()

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onRemoveItem(draftID.trim(), draftAmountText.toInt())
                    },
                    enabled = draftID.isNotBlank() && draftAmountText.isNotBlank() &&
                            draftAmountText.toInt() > 0,
                ) {
                    Text("Remove")
                }
                Button(
                    onClick  = {
                        onAddItem(draftID.trim(), draftAmountText.toInt())
                    },
                    enabled  = draftID.isNotBlank() && draftAmountText.isNotBlank() &&
                            draftAmountText.toInt() > 0,
                ) {
                    Text("Add")
                }
            }
        }
    }
}
