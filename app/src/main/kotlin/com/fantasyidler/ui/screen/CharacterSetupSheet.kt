package com.fantasyidler.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.R
import com.fantasyidler.ui.components.foundation.ChunkyButton
import com.fantasyidler.ui.components.foundation.ChunkyButtonVariant
import com.fantasyidler.ui.components.foundation.ChunkySheet
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

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
    ChunkySheet(onDismissRequest = onDismiss) {
        CharacterSetupBody(
            isFirstTime   = isFirstTime,
            initialName   = initialName,
            initialGender = initialGender,
            initialRace   = initialRace,
            onSave        = onSave,
            onDismiss     = onDismiss,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharacterSetupBody(
    isFirstTime: Boolean,
    initialName: String,
    initialGender: String,
    initialRace: String,
    onSave: (name: String, gender: String, race: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalFantasyTokens.current
    var draftName   by remember { mutableStateOf(initialName) }
    var draftGender by remember { mutableStateOf(initialGender) }
    var draftRace   by remember { mutableStateOf(initialRace) }

    val genderKeys = stringArrayResource(R.array.character_genders)
    val raceKeys   = stringArrayResource(R.array.character_races)
    val genderLabels = mapOf(
        "Male"   to stringResource(R.string.character_gender_male),
        "Female" to stringResource(R.string.character_gender_female),
        "Other"  to stringResource(R.string.character_gender_other),
    )
    val raceLabels = mapOf(
        "Human"    to stringResource(R.string.character_race_human),
        "Elf"      to stringResource(R.string.character_race_elf),
        "Dwarf"    to stringResource(R.string.character_race_dwarf),
        "Orc"      to stringResource(R.string.character_race_orc),
        "Halfling" to stringResource(R.string.character_race_halfling),
        "Gnome"    to stringResource(R.string.character_race_gnome),
    )

    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.l),
    ) {
        Text(
            text  = if (isFirstTime) stringResource(R.string.character_create_title)
                    else stringResource(R.string.character_edit_title),
            style = tokens.typography.headlineLarge,
            color = tokens.colors.onSurface,
        )

        OutlinedTextField(
            value         = draftName,
            onValueChange = { draftName = it },
            label         = { Text(stringResource(R.string.character_name_label)) },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.s)) {
            Text(
                text  = stringResource(R.string.character_gender),
                style = tokens.typography.labelSmall,
                color = tokens.colors.onSurfaceMuted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.m)) {
                genderKeys.forEach { gender ->
                    FilterChip(
                        selected = draftGender == gender,
                        onClick  = { draftGender = if (draftGender == gender) "" else gender },
                        label    = { Text(genderLabels[gender] ?: gender) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.s)) {
            Text(
                text  = stringResource(R.string.character_race),
                style = tokens.typography.labelSmall,
                color = tokens.colors.onSurfaceMuted,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.m)) {
                raceKeys.forEach { race ->
                    FilterChip(
                        selected = draftRace == race,
                        onClick  = { draftRace = if (draftRace == race) "" else race },
                        label    = { Text(raceLabels[race] ?: race) },
                    )
                }
            }
        }

        HorizontalDivider(color = tokens.colors.primary.copy(alpha = 0.25f))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            ChunkyButton(
                text    = if (isFirstTime) stringResource(R.string.onboarding_skip)
                          else stringResource(R.string.btn_cancel),
                onClick = onDismiss,
                variant = ChunkyButtonVariant.Ghost,
            )
            Spacer(Modifier.width(tokens.spacing.m))
            ChunkyButton(
                text    = stringResource(R.string.btn_confirm),
                onClick = { onSave(draftName.trim(), draftGender, draftRace) },
                enabled = draftName.isNotBlank(),
                variant = ChunkyButtonVariant.Primary,
            )
        }
        Spacer(Modifier.height(tokens.spacing.xl))
    }
}

@PreviewLightDark
@Composable
private fun PreviewCharacterSetupBody() {
    FantasyPreviewSurface {
        CharacterSetupBody(
            isFirstTime   = true,
            initialName   = "Arwen",
            initialGender = "Female",
            initialRace   = "Elf",
            onSave        = { _, _, _ -> },
            onDismiss     = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PreviewCharacterSetupBodyEmpty() {
    FantasyPreviewSurface {
        CharacterSetupBody(
            isFirstTime   = true,
            initialName   = "",
            initialGender = "",
            initialRace   = "",
            onSave        = { _, _, _ -> },
            onDismiss     = {},
        )
    }
}
