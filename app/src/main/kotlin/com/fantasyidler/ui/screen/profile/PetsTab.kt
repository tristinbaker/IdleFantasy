package com.fantasyidler.ui.screen.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fantasyidler.R
import com.fantasyidler.data.json.PetData
import com.fantasyidler.ui.components.CompletionProgressBar
import com.fantasyidler.ui.screen.AppBannerCenter
import com.fantasyidler.ui.screen.SlotSectionHeader
import com.fantasyidler.util.GameStrings

/** The 8 isle-only pets (7 elder dungeon drops + Last Elder's familiar). Hidden from the
 *  pet list until the isle is unlocked, so a pre-unlock save can't spoil what's coming. */
private val ELDER_ISLE_PET_IDS = setOf(
    "tidal_sprite", "grove_fawn", "magma_salamander", "abyss_wisp",
    "ancient_turtle", "cinder_hawk", "deepwater_serpent", "elder_familiar",
)

@Composable
internal fun PetsTab(
    allPets: Map<String, PetData>,
    ownedPetIds: Set<String>,
    elderIsleUnlocked: Boolean = false,
) {
    val visiblePets = if (elderIsleUnlocked) allPets
                      else allPets.filterKeys { it !in ELDER_ISLE_PET_IDS }
    if (visiblePets.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.profile_no_pets),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        val owned = visiblePets.values.filter { it.id in ownedPetIds }
        val locked = visiblePets.values.filter { it.id !in ownedPetIds }

        item {
            CompletionProgressBar(
                completed = owned.size,
                total = visiblePets.size,
                label = stringResource(R.string.achievements_progress_bar),
            )
        }
        if (owned.isNotEmpty()) {
            item { SlotSectionHeader(stringResource(R.string.profile_pet_collected)) }
            itemsIndexed(owned, key = { _, it -> it.id }) { index, pet ->
                PetRow(pet = pet, owned = true)
                if (index < owned.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        if (locked.isNotEmpty()) {
            item { SlotSectionHeader(stringResource(R.string.profile_pet_not_found)) }
            itemsIndexed(locked, key = { _, it -> it.id }) { index, pet ->
                PetRow(pet = pet, owned = false)
                if (index < locked.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PetRow(pet: PetData, owned: Boolean) {
    val alpha = if (owned) 1f else 0.38f
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (owned) Modifier.clickable {
                val messages = context.resources.getStringArray(R.array.profile_pet_happy_messages)
                AppBannerCenter.enqueue(
                    String.format(
                        messages.random(),
                        GameStrings.petName(context, pet.id)
                    )
                )
            } else Modifier.Companion)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = pet.emoji,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = GameStrings.petName(context, pet.id),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Text(
                text = GameStrings.petDesc(context, pet.id),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
        Text(
            text = stringResource(
                if (pet.effectType == "coin_boost") R.string.format_coin_boost_percent
                else R.string.format_xp_boost_percent,
                pet.boostPercent,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = (if (owned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                .copy(alpha = alpha),
        )
    }
}