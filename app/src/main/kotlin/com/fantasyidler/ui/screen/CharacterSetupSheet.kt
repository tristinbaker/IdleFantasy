package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp

internal val CHARACTER_GENDERS = listOf("Male", "Female", "Other")
internal val CHARACTER_RACES   = listOf("Human", "Elf", "Dwarf", "Orc", "Halfling", "Gnome")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CharacterSetupSheet(
    isFirstTime: Boolean,
    initialName: String = "",
    initialGender: String = "",
    initialRace: String = "",
    onSave: (name: String, gender: String, race: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftName   by remember { mutableStateOf(initialName) }
    var draftGender by remember { mutableStateOf(initialGender) }
    var draftRace   by remember { mutableStateOf(initialRace) }
    val sheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        dragHandle       = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text  = if (isFirstTime) "Create Your Character" else "Edit Character",
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value         = draftName,
                onValueChange = { draftName = it },
                label         = { Text("Character Name") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Gender", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CHARACTER_GENDERS.forEach { gender ->
                        FilterChip(
                            selected = draftGender == gender,
                            onClick  = { draftGender = if (draftGender == gender) "" else gender },
                            label    = { Text(gender) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Race", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CHARACTER_RACES.forEach { race ->
                        FilterChip(
                            selected = draftRace == race,
                            onClick  = { draftRace = if (draftRace == race) "" else race },
                            label    = { Text(race) },
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(if (isFirstTime) "Skip" else "Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick  = { onSave(draftName.trim(), draftGender, draftRace) },
                    enabled  = draftName.isNotBlank(),
                ) {
                    Text("Save")
                }
            }
        }
    }
}
