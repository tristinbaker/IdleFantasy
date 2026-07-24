package com.fantasyidler.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.R
import com.fantasyidler.data.json.BoneData
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.Skills
import com.fantasyidler.repository.ChurchRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.GuildRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.resolveCapeMultiplier
import com.fantasyidler.repository.QuestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class BoneAltarUiState(
    val isLoading: Boolean = true,
    val availableBones: Map<String, BoneData> = emptyMap(),
    val inventory: Map<String, Int> = emptyMap(),
    val prayerLevel: Int = 1,
    val prayerXp: Long = 0L,
    val boostActive: Boolean = false,
    val prayerCapeMult: Float = 1f,
    val churchMult: Float = 1f,
    val prestigeMult: Float = 1f,
    val petBoostPct: Int = 0,
    val selectedBoneKey: String? = null,
    val combo: Int = 0,
    val lastTapMs: Long = 0L,
    val sessionXp: Long = 0L,
    val totalBuried: Int = 0,
    val snackbarMessage: String? = null,
)

@HiltViewModel
class BoneAltarViewModel @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val questRepo: QuestRepository,
    private val guildRepo: GuildRepository,
    private val gameData: GameDataRepository,
    private val json: Json,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val mutex = Mutex()
    private val _extra = MutableStateFlow(BoneAltarUiState())

    val uiState: StateFlow<BoneAltarUiState> = combine(
        playerRepo.playerFlow,
        _extra,
    ) { player, extra ->
        if (player == null) return@combine extra.copy(isLoading = true)
        val flags: PlayerFlags             = json.decodeFromString(player.flags)
        val levels: Map<String, Int>       = json.decodeFromString(player.skillLevels)
        val xpMap:  Map<String, Long>      = json.decodeFromString(player.skillXp)
        val inventory: Map<String, Int>    = json.decodeFromString(player.inventory)
        val equipped: Map<String, String?> = json.decodeFromString(player.equipped)

        val availableBones = gameData.bones
            .filter { (key, _) -> (inventory[key] ?: 0) > 0 }
            .entries.sortedByDescending { it.value.xpPerBone }
            .associate { it.key to it.value }

        val boostActive    = flags.xpBoostExpiresAt > System.currentTimeMillis()
        val equippedCape   = equipped[EquipSlot.CAPE]?.let { gameData.equipment[it] }
        // skillPrestige is intentionally omitted here (not flags.skillPrestige): prestige is
        // already applied as its own separate factor below (prestigeMult), multiplied together
        // with prayerCapeMult at collection time. Passing the real prestige map here would fold
        // (prestige + 1) into the cape multiplier too, double-counting prestige for any player
        // who has prestiged Prayer and owns/equips a prayer cape.
        val prayerCapeMult = resolveCapeMultiplier(
            skillName = Skills.PRAYER,
            equippedCape = equippedCape,
            inventoryKeys = inventory.keys,
            townBuildingTiers = flags.townBuildingTiers,
            skillPrestige = emptyMap(),
            allEquipment = gameData.equipment
        )
        val churchMult     = ChurchRepository.xpMultiplier(flags)
        val prestige       = flags.skillPrestige[Skills.PRAYER] ?: 0
        val prestigeMult   = if (prestige > 0) (1.0 + prestige * 0.10).toFloat() else 1f
        val petBoostPct    = petBoostFor(player.pets, Skills.PRAYER)

        val selectedKey = extra.selectedBoneKey?.takeIf { (inventory[it] ?: 0) > 0 }

        extra.copy(
            isLoading       = false,
            availableBones  = availableBones,
            inventory       = inventory,
            prayerLevel     = levels[Skills.PRAYER] ?: 1,
            prayerXp        = xpMap[Skills.PRAYER] ?: 0L,
            boostActive     = boostActive,
            prayerCapeMult  = prayerCapeMult,
            churchMult      = churchMult,
            prestigeMult    = prestigeMult,
            petBoostPct     = petBoostPct,
            selectedBoneKey = selectedKey,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoneAltarUiState())

    fun selectBone(boneKey: String?) {
        _extra.update { it.copy(selectedBoneKey = boneKey, combo = 0, lastTapMs = 0L) }
    }

    fun resetCombo() {
        _extra.update { it.copy(combo = 0, lastTapMs = 0L) }
    }

    fun tapBone() {
        val state   = uiState.value
        val boneKey = state.selectedBoneKey ?: return
        val bone    = gameData.bones[boneKey] ?: return

        val now      = System.currentTimeMillis()
        val newCombo = if (now - state.lastTapMs > COMBO_RESET_MS) 1
                       else (state.combo + 1).coerceAtMost(99)
        val comboMult = if (newCombo >= COMBO_THRESHOLD) COMBO_XP_MULT else 1.0f
        _extra.update { it.copy(combo = newCombo, lastTapMs = now) }

        val boostMult   = if (state.boostActive) 2.0f else 1.0f
        val petMult     = 1.0f + state.petBoostPct / 100.0f
        val effectiveXp = (bone.xpPerBone * comboMult * boostMult *
            state.churchMult * state.prayerCapeMult * state.prestigeMult * petMult)
            .toLong().coerceAtLeast(1L)

        viewModelScope.launch {
            mutex.withLock {
                val result = playerRepo.buryBoneAtomic(boneKey, effectiveXp)
                if (result.xpGained > 0L) {
                    if (!bone.isAsh) {
                        questRepo.recordBuried(1)
                        guildRepo.recordGuildPrayer(1)
                    }
                    playerRepo.recordDailyPrayer(1)
                    _extra.update { it.copy(
                        sessionXp       = it.sessionXp + result.xpGained,
                        totalBuried     = it.totalBuried + 1,
                        snackbarMessage = if (result.awardedCape != null)
                            context.getString(R.string.bone_altar_cape_awarded) else null,
                    )}
                }
            }
        }
    }

    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }

    private fun petBoostFor(petsJson: String, skillKey: String): Int {
        val pets = try {
            json.decodeFromString<List<com.fantasyidler.data.model.OwnedPet>>(petsJson)
        } catch (_: Exception) {
            return 0
        }
        return pets.sumOf { pet ->
            val pd = gameData.pets[pet.id]
            if (pd != null && (pd.boostedSkill == skillKey || pd.boostedSkill == "all")) pd.boostPercent else 0
        }
    }

    companion object {
        const val COMBO_RESET_MS  = 3_000L
        const val COMBO_THRESHOLD = 10
        const val COMBO_XP_MULT   = 1.5f
    }
}
