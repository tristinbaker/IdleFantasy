package com.fantasyidler.ui.screen.profile.pets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.fantasyidler.R
import com.fantasyidler.data.json.PetData
import com.fantasyidler.ui.components.SectionHeader
import com.fantasyidler.ui.components.foundation.IconDisk
import com.fantasyidler.ui.screen.profile.ProfileEmptyState
import com.fantasyidler.ui.theme.fantasy.FantasyPreviewSurface
import com.fantasyidler.ui.theme.fantasy.LocalFantasyTokens

/**
 * Profile → Pets tab. Groups every pet into "Collected" and "Not Yet Found"
 * lanes, each row showing the pet emoji on a gold disk, name + lore, and the
 * XP-boost percentage. Locked pets render at reduced opacity.
 */
@Composable
fun PetsTab(
    allPets: Map<String, PetData>,
    ownedPetIds: Set<String>,
    modifier: Modifier = Modifier,
) {
    if (allPets.isEmpty()) {
        ProfileEmptyState(
            title    = stringResource(R.string.profile_no_pets),
            modifier = modifier,
        )
        return
    }

    val tokens = LocalFantasyTokens.current
    val owned  = allPets.values.filter { it.id in ownedPetIds }
    val locked = allPets.values.filter { it.id !in ownedPetIds }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (owned.isNotEmpty()) {
            item("hdr_owned") { SectionHeader(stringResource(R.string.profile_pet_collected)) }
            items(owned, key = { "owned_${it.id}" }) { pet -> PetRow(pet = pet, owned = true) }
        }
        if (locked.isNotEmpty()) {
            item("hdr_locked") { SectionHeader(stringResource(R.string.profile_pet_not_found)) }
            items(locked, key = { "locked_${it.id}" }) { pet -> PetRow(pet = pet, owned = false) }
        }
        item { Spacer(Modifier.height(tokens.spacing.l)) }
    }
}

@Composable
private fun PetRow(pet: PetData, owned: Boolean) {
    val tokens = LocalFantasyTokens.current
    val alpha  = if (owned) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = tokens.spacing.xxl + tokens.spacing.l)
            .padding(horizontal = tokens.spacing.l, vertical = tokens.spacing.m + tokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconDisk(
            emoji      = pet.emoji,
            size       = tokens.spacing.xxl + tokens.spacing.m,
            background = tokens.colors.primary.copy(alpha = if (owned) 0.18f else 0.08f),
        )
        Spacer(Modifier.width(tokens.spacing.m + tokens.spacing.s))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = pet.displayName,
                style      = tokens.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = tokens.colors.onSurface.copy(alpha = alpha),
            )
            Text(
                text  = pet.description,
                style = tokens.typography.bodyMedium,
                color = tokens.colors.onSurfaceMuted.copy(alpha = alpha),
            )
        }
        Spacer(Modifier.width(tokens.spacing.m))
        Text(
            text  = stringResource(R.string.format_xp_boost_percent, pet.boostPercent),
            style = tokens.typography.labelSmall,
            color = (if (owned) tokens.colors.primary else tokens.colors.onSurfaceMuted)
                .copy(alpha = alpha),
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = tokens.spacing.l),
        color    = tokens.colors.border.copy(alpha = 0.12f),
    )
}

@PreviewLightDark
@Composable
private fun PreviewPetsTabEmpty() {
    FantasyPreviewSurface { PetsTab(allPets = emptyMap(), ownedPetIds = emptySet()) }
}

@PreviewLightDark
@Composable
private fun PreviewPetsTabMixed() {
    FantasyPreviewSurface {
        PetsTab(
            allPets = linkedMapOf(
                "rock_pet" to PetData(
                    id = "rock_pet",
                    displayName = "Rock Golem",
                    emoji = "🪨",
                    description = "+5% Mining XP",
                    source = "mining",
                    effectType = "xp_boost",
                    boostedSkill = "mining",
                    boostPercent = 5,
                ),
                "fish_pet" to PetData(
                    id = "fish_pet",
                    displayName = "Lucky Carp",
                    emoji = "🐟",
                    description = "+5% Fishing XP",
                    source = "fishing",
                    effectType = "xp_boost",
                    boostedSkill = "fishing",
                    boostPercent = 5,
                ),
            ),
            ownedPetIds = setOf("rock_pet"),
        )
    }
}
