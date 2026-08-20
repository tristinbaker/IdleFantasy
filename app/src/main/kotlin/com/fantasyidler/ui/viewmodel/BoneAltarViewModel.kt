package com.fantasyidler.ui.viewmodel

import com.fantasyidler.util.withAppLocale

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
import com.fantasyidler.repository.QuestRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class BoneAltarUiState(
    val isLoading: Boolean = true,
    val availableBones: Map<String, BoneData> = emptyMap(),
    val inventory: Map<String, Int> = emptyMap(),
    val prayerLevel: Int = 1,
    val prayerXp: Long = 0L,
    val boostActive: Boolean = false,
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

    private val _extra = MutableStateFlow(BoneAltarUiState())

    // Rapid taps are counted optimistically in the UI and written to the DB in adaptive
    // batches: while one batch is being written, new taps accumulate into the next one.
    // Everything below is only touched from the main thread (tap handlers and the drain
    // loop both run on Dispatchers.Main), so no locking is needed. The drain runs on its
    // own scope so a final flush can survive onCleared() — the old per-tap pipeline
    // silently dropped still-queued taps when the player left the screen quickly.
    private class PendingTaps(var count: Int = 0, var xp: Long = 0L)
    private val pending = LinkedHashMap<String, PendingTaps>()
    private var inFlightKey: String? = null
    private var inFlightCount = 0
    private var drainJob: Job? = null
    private val flushScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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

        val boostActive    = !flags.ironman && flags.xpBoostExpiresAt > System.currentTimeMillis()
        val equippedCape   = equipped[EquipSlot.CAPE]?.let { gameData.equipment[it] }
        // skillPrestige is intentionally counted here twice: prestige is
        // applied as its own separate factor below (prestigeMult), multiplied together
        // with churchMult at collection time (which can be affected by prestige).
        // This double-counting is intended as it comes from two separate sources:
        // the inherent prestige multiplier of the skill and the effect of the prestige on capes.
        // This is in line with the multipliers for other skills, the only difference being that
        // in this case both come from the prayer prestige
        val churchMult     = if (flags.ironman) 1.0f else ChurchRepository.xpMultiplier(flags, equippedCape, inventory.keys, gameData.equipment)
        val prestige       = if (flags.ironman) 0 else flags.skillPrestige[Skills.PRAYER] ?: 0
        val prestigeMult   = if (prestige > 0) (1.0 + prestige * 0.10).toFloat() else 1f
        val petBoostPct    = if (flags.ironman) 0 else petBoostFor(player.pets, Skills.PRAYER)

        val selectedKey = extra.selectedBoneKey?.takeIf { (inventory[it] ?: 0) > 0 }

        extra.copy(
            isLoading       = false,
            availableBones  = availableBones,
            inventory       = inventory,
            prayerLevel     = levels[Skills.PRAYER] ?: 1,
            prayerXp        = xpMap[Skills.PRAYER] ?: 0L,
            boostActive     = boostActive,
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

        // Refuse the tap once every bone in inventory is already spoken for by
        // unflushed or in-flight taps, so optimistic counting can't overshoot.
        val unflushed = (pending[boneKey]?.count ?: 0) +
            (if (inFlightKey == boneKey) inFlightCount else 0)
        if ((state.inventory[boneKey] ?: 0) - unflushed <= 0) return

        val now      = System.currentTimeMillis()
        val newCombo = if (now - state.lastTapMs > COMBO_RESET_MS) 1
                       else (state.combo + 1).coerceAtMost(99)
        val comboMult = if (newCombo >= COMBO_THRESHOLD) COMBO_XP_MULT else 1.0f

        val boostMult   = if (state.boostActive) 2.0f else 1.0f
        val petMult     = 1.0f + state.petBoostPct / 100.0f
        val effectiveXp = (bone.xpPerBone * comboMult * boostMult *
            state.churchMult * state.prestigeMult * petMult)
            .toLong().coerceAtLeast(1L)

        _extra.update { it.copy(
            combo       = newCombo,
            lastTapMs   = now,
            sessionXp   = it.sessionXp + effectiveXp,
            totalBuried = it.totalBuried + 1,
        )}
        val taps = pending.getOrPut(boneKey) { PendingTaps() }
        taps.count += 1
        taps.xp    += effectiveXp
        scheduleDrain()
    }

    private fun scheduleDrain() {
        if (drainJob?.isActive == true) return
        drainJob = flushScope.launch {
            while (pending.isNotEmpty()) {
                val (boneKey, taps) = pending.entries.first()
                pending.remove(boneKey)
                inFlightKey   = boneKey
                inFlightCount = taps.count
                val isAsh  = gameData.bones[boneKey]?.isAsh == true
                val result = playerRepo.buryBonesAtomic(boneKey, taps.count, taps.xp)
                if (result.buried > 0 && !isAsh) {
                    questRepo.recordBuried(result.buried)
                    guildRepo.recordGuildPrayer(result.buried)
                    playerRepo.recordDailyPrayer(result.buried)
                }
                inFlightKey   = null
                inFlightCount = 0
                if (result.buried < taps.count || result.awardedCape != null) {
                    _extra.update { it.copy(
                        totalBuried     = it.totalBuried - (taps.count - result.buried),
                        sessionXp       = it.sessionXp - (taps.xp - result.xpGained),
                        snackbarMessage = if (result.awardedCape != null)
                            context.withAppLocale().getString(R.string.bone_altar_cape_awarded) else it.snackbarMessage,
                    )}
                }
            }
        }
    }

    override fun onCleared() {
        scheduleDrain()
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
