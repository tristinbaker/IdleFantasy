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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import androidx.compose.ui.platform.LocalContext
import com.fantasyidler.util.GameStrings
import com.fantasyidler.ui.theme.ScaledSheetContent

internal val CHARACTER_GENDERS = listOf("Male", "Female", "Other")
internal val CHARACTER_RACES   = listOf("human", "elf", "dwarf", "orc", "halfling", "gnome")
internal const val CHARACTER_NAME_MAX_LENGTH = 256

/** A title option ready to render — display strings already resolved, whether static or seasonal. */
data class TitleOption(
    val id: String,
    val name: String,
    val requirement: String,
    val unlocked: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CharacterSetupSheet(
    raceProficiencies: Map<String, List<String>> = emptyMap(),
    isFirstTime: Boolean,
    initialName: String = "",
    initialGender: String = "",
    initialRace: String = "",
    titles: List<TitleOption> = emptyList(),
    equippedTitleId: String? = null,
    onEquipTitle: (String?) -> Unit = {},
    /** Show the permanent Ironman choice — creation flows only, never the edit sheet. */
    showIronmanOption: Boolean = false,
    onSave: (name: String, gender: String, race: String, ironman: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftName   by remember { mutableStateOf(initialName) }
    val isCustomGender = initialGender.isNotBlank() && initialGender !in CHARACTER_GENDERS
    var draftGender by remember { mutableStateOf(if (isCustomGender) "Other" else initialGender) }
    var customGenderText by remember { mutableStateOf(if (isCustomGender) initialGender else "") }
    var draftRace   by remember { mutableStateOf(initialRace) }
    var draftIronman by remember { mutableStateOf(false) }
    val sheetState  = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        dragHandle       = { BottomSheetDefaults.DragHandle() },
    ) {
        ScaledSheetContent {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text  = if (isFirstTime) stringResource(R.string.character_create_title)
                        else stringResource(R.string.character_edit_title),
                style = MaterialTheme.typography.titleLarge,
            )

            val nameTooLong = draftName.length > CHARACTER_NAME_MAX_LENGTH
            OutlinedTextField(
                value         = draftName,
                onValueChange = { draftName = it },
                label         = { Text(stringResource(R.string.character_name_label)) },
                singleLine    = true,
                isError       = nameTooLong,
                supportingText = if (nameTooLong) {
                    { Text(stringResource(R.string.character_name_too_long, CHARACTER_NAME_MAX_LENGTH)) }
                } else null,
                modifier      = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.character_gender),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CHARACTER_GENDERS.forEach { gender ->
                        FilterChip(
                            selected = draftGender == gender,
                            onClick  = { draftGender = if (draftGender == gender) "" else gender },
                            label    = { Text(GameStrings.genderName(context, gender)) },
                        )
                    }
                }
                if (draftGender == "Other") {
                    OutlinedTextField(
                        value         = customGenderText,
                        onValueChange = { customGenderText = it },
                        label         = { Text(stringResource(R.string.character_gender_custom_hint)) },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.character_race),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isFirstTime) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CHARACTER_RACES.forEach { race ->
                            FilterChip(
                                selected = draftRace == race,
                                onClick  = { draftRace = if (draftRace == race) "" else race },
                                label    = { Text(GameStrings.raceName(context, race)) },
                            )
                        }
                    }
                } else {
                    // Post-setup the race is reference-only here: changes cost a token or
                    // coins and go through the appearance editor's confirm flow.
                    Text(
                        text  = GameStrings.raceName(context, draftRace).ifBlank { stringResource(R.string.character_race_human) },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                val setupContext = LocalContext.current
                val draftKey = draftRace.lowercase()
                val proficiencyText = when {
                    draftKey == "human" -> stringResource(R.string.race_proficiency_human)
                    draftKey.isNotBlank() -> raceProficiencies[draftKey]?.takeIf { it.isNotEmpty() }?.let { skills ->
                        stringResource(
                            R.string.race_proficiency_label,
                            skills.joinToString(", ") { GameStrings.skillName(setupContext, it) },
                        )
                    }
                    else -> null
                }
                if (proficiencyText != null) {
                    Text(
                        text  = proficiencyText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (!isFirstTime) {
                    Text(
                        text  = stringResource(R.string.character_race_edit_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (draftIronman) {
                    Text(
                        text  = stringResource(R.string.ironman_race_permanent_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (showIronmanOption) {
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text  = stringResource(R.string.character_ironman_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text  = stringResource(R.string.character_ironman_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (draftIronman) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked         = draftIronman,
                        onCheckedChange = { draftIronman = it },
                    )
                }
            }

            if (!isFirstTime && titles.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.character_title_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    val currentLabel = if (equippedTitleId == null) stringResource(R.string.character_title_none)
                        else titles.firstOrNull { it.id == equippedTitleId }?.name ?: stringResource(R.string.character_title_none)
                    var titleExpanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded         = titleExpanded,
                        onExpandedChange = { titleExpanded = it },
                    ) {
                        OutlinedTextField(
                            value         = currentLabel,
                            onValueChange = {},
                            readOnly      = true,
                            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = titleExpanded) },
                            modifier      = Modifier.menuAnchor().fillMaxWidth(),
                            textStyle     = MaterialTheme.typography.bodyMedium,
                            singleLine    = true,
                        )
                        ExposedDropdownMenu(
                            expanded         = titleExpanded,
                            onDismissRequest = { titleExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text       = stringResource(R.string.character_title_none),
                                        fontWeight = if (equippedTitleId == null) FontWeight.SemiBold else FontWeight.Normal,
                                        color      = if (equippedTitleId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                },
                                onClick = {
                                    onEquipTitle(null)
                                    titleExpanded = false
                                },
                            )
                            titles.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text       = option.name,
                                                fontWeight = if (option.id == equippedTitleId) FontWeight.SemiBold else FontWeight.Normal,
                                                color      = when {
                                                    !option.unlocked            -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    option.id == equippedTitleId -> MaterialTheme.colorScheme.primary
                                                    else                         -> MaterialTheme.colorScheme.onSurface
                                                },
                                            )
                                            if (!option.unlocked) {
                                                Text(
                                                    text  = option.requirement,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    },
                                    enabled = option.unlocked,
                                    onClick = {
                                        onEquipTitle(option.id)
                                        titleExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(if (isFirstTime) stringResource(R.string.onboarding_skip)
                         else stringResource(R.string.btn_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick  = {
                        val gender = if (draftGender == "Other" && customGenderText.isNotBlank())
                            customGenderText.trim() else draftGender
                        onSave(draftName.trim().take(CHARACTER_NAME_MAX_LENGTH), gender, draftRace, draftIronman)
                    },
                    enabled  = draftName.isNotBlank(),
                ) {
                    Text(stringResource(R.string.btn_confirm))
                }
            }
        }
        }
    }
}
