package com.fantasyidler.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.R
import com.fantasyidler.data.model.OwnedPet
import com.fantasyidler.data.model.Player
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.HiddenAchievements
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.util.withAppLocale
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * App-root watcher for the hidden achievements: on every player change it saves whatever the
 * current state has newly earned (event achievements are saved by their own hooks) and emits a
 * banner line for each unlock not yet announced on this character.
 */
@HiltViewModel
class HiddenAchievementsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerRepo: PlayerRepository,
    private val gameData: GameDataRepository,
    private val json: Json,
) : ViewModel() {

    private val banners = Channel<String>(Channel.BUFFERED)
    val unlockBanners: Flow<String> = banners.receiveAsFlow()

    private val titleIds: Set<String> = TitleCatalog.ALL.map { it.id }.toSet()
    private val nameResById: Map<String, Int> = HiddenAchievementCatalog.ALL.associate { it.id to it.nameRes }

    init {
        viewModelScope.launch {
            playerRepo.playerFlow.filterNotNull().conflate().collect {
                val announce = try {
                    withContext(Dispatchers.Default) { playerRepo.syncHiddenAchievements(::evaluate) }
                } catch (_: Exception) {
                    emptyList()
                }
                if (announce.isEmpty()) return@collect
                val ctx = context.withAppLocale()
                for (id in announce) {
                    val nameRes = nameResById[id] ?: continue
                    banners.trySend(ctx.getString(R.string.achievement_hidden_unlocked, ctx.getString(nameRes)))
                }
            }
        }
    }

    private fun evaluate(player: Player, flags: PlayerFlags): Set<String> {
        val inventory: Map<String, Int> = json.decodeFromString(player.inventory)
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)
        val pets: List<OwnedPet> = try { json.decodeFromString(player.pets) } catch (_: Exception) { emptyList() }
        return HiddenAchievements.earnedFromState(
            HiddenAchievements.StateInput(
                flags            = flags,
                inventory        = inventory,
                equippedItems    = equipped.values.filterNotNull().toSet(),
                coins            = player.coins,
                ownedPetIds      = pets.map { it.id }.toSet(),
                equipmentKeys    = gameData.equipment.keys,
                titleIds         = titleIds,
                seasonalSeries   = gameData.seasonalEvents.keys,
            )
        )
    }
}
