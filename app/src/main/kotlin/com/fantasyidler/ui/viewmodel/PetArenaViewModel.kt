package com.fantasyidler.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantasyidler.R
import com.fantasyidler.data.json.ArenaTrainer
import com.fantasyidler.data.json.ArenaZone
import com.fantasyidler.data.model.OwnedPet
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PetArenaCollectResult
import com.fantasyidler.repository.PetArenaRepository
import com.fantasyidler.repository.PetBattleStartResult
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.simulator.PetBattle
import com.fantasyidler.simulator.XpTable
import com.fantasyidler.util.GameStrings
import com.fantasyidler.util.withAppLocale
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class ArenaPetUi(
    val id: String,
    val emoji: String,
    val name: String,
    val level: Int,
    val stage: Int,
    val xp: Long,
    /** XP still needed for the next level; 0 at level 99. */
    val xpToNext: Long,
    /** 0..1 progress through the current level. */
    val levelProgress: Float,
    val type: String,
    val rarity: String,
    val attack: Int,
    val defense: Int,
    val hp: Int,
    val boostPercent: Int,
    val boostedSkill: String,
    val effectType: String,
    val onTeam: Boolean,
)

data class ArenaZoneUi(val zone: ArenaZone, val unlocked: Boolean)

/** The Home screen's view of the arena slot (see HomeViewModel.petArenaSession). */
data class PetArenaHomeUi(
    val sessionId: String,
    val zoneId: String?,
    val trainerId: String?,
    /** The zone's or trainer's emoji. */
    val emoji: String,
    val startedAt: Long,
    val endsAt: Long,
    val completed: Boolean,
    /** Wins banked by the end of each simulated minute. */
    val winsByMinute: List<Int>,
    val teamEmoji: List<String>,
)

/** One collected arena session as banner text, shared by the arena screen and Home. */
internal fun petArenaResultMessage(ctx: Context, r: PetArenaCollectResult): String {
    val lines = mutableListOf<String>()
    when {
        r.trainerId != null && r.wins > 0 -> lines += r.badgeEarned
            ?.let { ctx.getString(R.string.pet_arena_result_badge, GameStrings.arenaBadgeName(ctx, it)) }
            ?: ctx.getString(R.string.pet_arena_result_trainer_won, GameStrings.arenaTrainerName(ctx, r.trainerId))
        r.trainerId != null -> lines += ctx.getString(R.string.pet_arena_result_trainer_lost, GameStrings.arenaTrainerName(ctx, r.trainerId))
        r.wiped             -> lines += ctx.getString(R.string.pet_arena_result_wiped, r.wins)
        else                -> lines += ctx.getString(R.string.pet_arena_result_wins, r.wins)
    }
    for (c in r.levelChanges) {
        val before = PetBattle.stage(c.oldLevel)
        val after = PetBattle.stage(c.newLevel)
        lines += if (after > before) ctx.getString(
            R.string.pet_arena_evolved,
            GameStrings.petStageName(ctx, c.petId, before),
            GameStrings.petStageName(ctx, c.petId, after),
        ) else ctx.getString(R.string.pet_arena_level_up, GameStrings.petStageName(ctx, c.petId, after), c.newLevel)
    }
    return lines.joinToString("\n")
}

data class ArenaTrainerUi(val trainer: ArenaTrainer, val unlocked: Boolean, val beaten: Boolean)

data class ArenaActiveUi(
    val trainerId: String?,
    val zoneId: String?,
    val startedAt: Long,
    val endsAt: Long,
    val completed: Boolean,
    /** Wins banked by the end of each simulated minute, for the live counter. */
    val winsByMinute: List<Int>,
    val wiped: Boolean,
)

data class ArenaTreatUi(val key: String, val owned: Int, val xpEach: Long)

data class PetArenaUiState(
    val isLoading: Boolean = true,
    val needsStarter: Boolean = false,
    val pets: List<ArenaPetUi> = emptyList(),
    val team: List<String> = emptyList(),
    val zones: List<ArenaZoneUi> = emptyList(),
    val trainers: List<ArenaTrainerUi> = emptyList(),
    val badgeCount: Int = 0,
    val active: ArenaActiveUi? = null,
    val treats: List<ArenaTreatUi> = emptyList(),
    val snackbarMessage: String? = null,
)

@HiltViewModel
class PetArenaViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val arenaRepo: PetArenaRepository,
    private val gameData: GameDataRepository,
    private val json: Json,
) : ViewModel() {

    private val _extra = MutableStateFlow(PetArenaUiState())

    val uiState: StateFlow<PetArenaUiState> = combine(
        playerRepo.playerFlow,
        sessionRepo.activeWorkerSessionFlow(PetArenaRepository.PET_ARENA_SLOT),
        _extra,
    ) { player, session, extra ->
        if (player == null) return@combine extra
        val flags: PlayerFlags = try { json.decodeFromString(player.flags) } catch (_: Exception) { PlayerFlags() }
        val owned: List<OwnedPet> = try { json.decodeFromString(player.pets) } catch (_: Exception) { emptyList() }
        val inventory: Map<String, Int> = try { json.decodeFromString(player.inventory) } catch (_: Exception) { emptyMap() }
        val ctx = context.withAppLocale()
        val team = flags.petTeam.filter { id -> owned.any { it.id == id } }
        val pets = owned.mapNotNull { pet ->
            val data = gameData.pets[pet.id] ?: return@mapNotNull null
            val level = PetBattle.level(pet.xp)
            val f = PetBattle.petFighter(data, pet.xp)
            val floor = XpTable.xpForLevel(level)
            val next = if (level >= PetBattle.MAX_LEVEL) floor else XpTable.xpForLevel(level + 1)
            ArenaPetUi(
                id            = pet.id,
                emoji         = data.emoji,
                name          = GameStrings.petStageName(ctx, pet.id, PetBattle.stage(level)),
                level         = level,
                stage         = PetBattle.stage(level),
                xp            = pet.xp,
                xpToNext      = (next - pet.xp).coerceAtLeast(0L),
                levelProgress = if (next > floor) ((pet.xp - floor).toFloat() / (next - floor)).coerceIn(0f, 1f) else 1f,
                type          = data.battleType,
                rarity        = data.rarity,
                attack        = f.attack,
                defense       = f.defense,
                hp            = f.maxHp,
                boostPercent  = PetBattle.boostPercent(data.boostPercent, pet.xp),
                boostedSkill  = data.boostedSkill,
                effectType    = data.effectType,
                onTeam        = pet.id in team,
            )
        }.sortedWith(compareByDescending<ArenaPetUi> { it.onTeam }.thenByDescending { it.level }.thenBy { it.name })
        val arena = gameData.petArena
        val active = session?.let { s ->
            val frames: List<SessionFrame> = try { json.decodeFromString(s.frames) } catch (_: Exception) { emptyList() }
            var running = 0
            ArenaActiveUi(
                trainerId    = PetArenaRepository.trainerIdOf(s.activityKey),
                zoneId       = PetArenaRepository.zoneIdOf(s.activityKey),
                startedAt    = s.startedAt,
                endsAt       = s.endsAt,
                completed    = s.completed,
                winsByMinute = frames.map { running += it.kills; running },
                wiped        = frames.any { it.died },
            )
        }
        extra.copy(
            isLoading    = false,
            needsStarter = owned.isNotEmpty() && flags.petArenaStarter == null,
            pets         = pets,
            team         = team,
            zones        = arena.zones.map { ArenaZoneUi(it, PetArenaRepository.zoneUnlocked(it, flags.petBadges)) },
            trainers     = arena.trainers.mapIndexed { i, t ->
                ArenaTrainerUi(t, PetArenaRepository.trainerUnlocked(arena.trainers, i, flags.petBadges), t.id in flags.petBadges)
            },
            badgeCount   = arena.trainers.count { it.id in flags.petBadges },
            active       = active,
            treats       = arena.treats.map { (key, xp) -> ArenaTreatUi(key, inventory[key] ?: 0, xp) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PetArenaUiState())

    init {
        viewModelScope.launch { arenaRepo.completeIfExpired() }
    }

    private fun message(text: String) = _extra.update { it.copy(snackbarMessage = text) }
    fun snackbarConsumed() = _extra.update { it.copy(snackbarMessage = null) }
    private fun str(id: Int, vararg args: Any) = context.withAppLocale().getString(id, *args)

    fun chooseStarter(petId: String) {
        viewModelScope.launch { playerRepo.choosePetArenaStarter(petId) }
    }

    fun toggleTeam(petId: String) {
        val current = uiState.value.team
        val next = when {
            petId in current                    -> current - petId
            current.size >= PetBattle.MAX_TEAM -> { message(str(R.string.pet_arena_team_full, PetBattle.MAX_TEAM)); return }
            else                                -> current + petId
        }
        viewModelScope.launch { playerRepo.setPetTeam(next) }
    }

    /** Called when the countdown reaches zero, so the finished battle shows as collectable. */
    fun refreshCompletion() {
        viewModelScope.launch { arenaRepo.completeIfExpired() }
    }

    private fun startMessage(result: PetBattleStartResult) = when (result) {
        PetBattleStartResult.SUCCESS          -> null
        PetBattleStartResult.NO_TEAM          -> str(R.string.pet_arena_no_team)
        PetBattleStartResult.ALREADY_BATTLING -> str(R.string.pet_arena_already_battling)
        PetBattleStartResult.LOCKED,
        PetBattleStartResult.UNKNOWN          -> str(R.string.pet_arena_locked_action)
    }

    fun startZone(zoneId: String) {
        viewModelScope.launch { startMessage(arenaRepo.startZone(zoneId))?.let(::message) }
    }

    fun startTrainer(trainerId: String) {
        viewModelScope.launch { startMessage(arenaRepo.startTrainer(trainerId))?.let(::message) }
    }

    fun collect() {
        viewModelScope.launch {
            val result = arenaRepo.collect() ?: return@launch
            message(petArenaResultMessage(context.withAppLocale(), result))
        }
    }

    fun feed(petId: String, treatKey: String, qty: Int) {
        val xpEach = gameData.petArena.treats[treatKey] ?: return
        viewModelScope.launch {
            val change = playerRepo.feedPetTreat(petId, treatKey, qty, xpEach) ?: return@launch
            val ctx = context.withAppLocale()
            val before = PetBattle.stage(change.oldLevel)
            val after = PetBattle.stage(change.newLevel)
            message(
                if (after > before) ctx.getString(
                    R.string.pet_arena_evolved,
                    GameStrings.petStageName(ctx, petId, before),
                    GameStrings.petStageName(ctx, petId, after),
                ) else ctx.getString(R.string.pet_arena_fed, qty, GameStrings.itemName(ctx, treatKey), GameStrings.petStageName(ctx, petId, after))
            )
        }
    }
}
