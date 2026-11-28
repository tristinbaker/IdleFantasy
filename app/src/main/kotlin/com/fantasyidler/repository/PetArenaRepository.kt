package com.fantasyidler.repository

import com.fantasyidler.data.json.ArenaTrainer
import com.fantasyidler.data.json.ArenaZone
import com.fantasyidler.data.model.OwnedPet
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.simulator.PetBattle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

enum class PetBattleStartResult { SUCCESS, NO_TEAM, LOCKED, ALREADY_BATTLING, UNKNOWN }

/** What one collected arena session paid out, for the result banner. */
data class PetArenaCollectResult(
    val wins: Int,
    val wiped: Boolean,
    val trainerId: String?,
    val badgeEarned: String?,
    val items: Map<String, Int>,
    val coins: Long,
    val levelChanges: List<PlayerRepository.PetLevelChange>,
)

/**
 * Pet Arena (2.1.0). Arena battles run in their own session slot ([PET_ARENA_SLOT]) beside the
 * player's own session, reusing the worker-slot plumbing for timers, offline completion and save
 * exports. Wild zones fill an hour with back-to-back battles; a trainer is a single fight whose
 * first win awards that trainer's badge. Everything paid out is earned by the player's own pets,
 * so it counts for ironmen.
 */
@Singleton
class PetArenaRepository @Inject constructor(
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val gameData: GameDataRepository,
    private val json: Json,
) {
    companion object {
        /** Session slot for arena battles; town workers use 1 and 2, the player 0. */
        const val PET_ARENA_SLOT = 3
        const val SKILL_NAME = "pet_arena"
        /** Title for holding every badge (see TitleRepository). */
        const val CHAMPION_TITLE = "arena_champion"
        private const val ZONE_PREFIX = "zone:"
        private const val TRAINER_PREFIX = "trainer:"
        private const val MINUTE_MS = 60_000L

        /** Ladder rule: a trainer can be challenged once every trainer before it is beaten. */
        fun trainerUnlocked(trainers: List<ArenaTrainer>, index: Int, badges: Set<String>): Boolean =
            index in trainers.indices && trainers.take(index).all { it.id in badges }

        fun zoneUnlocked(zone: ArenaZone, badges: Set<String>): Boolean = badges.size >= zone.badgesRequired

        fun zoneKey(id: String) = "$ZONE_PREFIX$id"
        fun trainerKey(id: String) = "$TRAINER_PREFIX$id"
        fun trainerIdOf(activityKey: String): String? = activityKey.removePrefix(TRAINER_PREFIX).takeIf { activityKey.startsWith(TRAINER_PREFIX) }
        fun zoneIdOf(activityKey: String): String? = activityKey.removePrefix(ZONE_PREFIX).takeIf { activityKey.startsWith(ZONE_PREFIX) }
    }

    val arena get() = gameData.petArena

    /** Serialises start and collect so a double tap can neither start two battles nor pay one twice. */
    private val mutex = Mutex()

    suspend fun activeSession(): SkillSession? = sessionRepo.getActiveWorkerSession(PET_ARENA_SLOT)

    /** The team as fighters, skipping ids the player no longer owns. */
    private fun teamFighters(flags: PlayerFlags, pets: List<OwnedPet>): List<PetBattle.Fighter> {
        val owned = pets.associateBy { it.id }
        return flags.petTeam.mapNotNull { id ->
            val pet = owned[id] ?: return@mapNotNull null
            val data = gameData.pets[id] ?: return@mapNotNull null
            PetBattle.petFighter(data, pet.xp)
        }
    }

    private suspend fun start(activityKey: String, simulate: (List<PetBattle.Fighter>) -> List<SessionFrame>): PetBattleStartResult = mutex.withLock {
        if (activeSession() != null) return@withLock PetBattleStartResult.ALREADY_BATTLING
        val player = playerRepo.getOrCreatePlayer()
        val flags: PlayerFlags = json.decodeFromString(player.flags)
        val pets: List<OwnedPet> = json.decodeFromString(player.pets)
        val team = teamFighters(flags, pets)
        if (team.isEmpty()) return@withLock PetBattleStartResult.NO_TEAM
        val frames = simulate(team)
        sessionRepo.startWorkerSession(
            workerSlot           = PET_ARENA_SLOT,
            skillName            = SKILL_NAME,
            activityKey          = activityKey,
            frames               = json.encodeToString(ListSerializer(SessionFrame.serializer()), frames),
            durationMs           = frames.size.coerceAtLeast(1) * MINUTE_MS,
            skillDisplayName     = "Pet Arena",
            efficiencyMultiplier = 1.0f,
        )
        PetBattleStartResult.SUCCESS
    }

    suspend fun startZone(zoneId: String, random: Random = Random.Default): PetBattleStartResult {
        val zone = arena.zones.firstOrNull { it.id == zoneId } ?: return PetBattleStartResult.UNKNOWN
        if (!zoneUnlocked(zone, playerRepo.getFlags().petBadges)) return PetBattleStartResult.LOCKED
        return start(zoneKey(zoneId)) { team -> PetBattle.simulateZone(team, zone, arena.creatures, random) }
    }

    suspend fun startTrainer(trainerId: String, random: Random = Random.Default): PetBattleStartResult {
        val index = arena.trainers.indexOfFirst { it.id == trainerId }
        if (index < 0) return PetBattleStartResult.UNKNOWN
        if (!trainerUnlocked(arena.trainers, index, playerRepo.getFlags().petBadges)) return PetBattleStartResult.LOCKED
        val trainer = arena.trainers[index]
        return start(trainerKey(trainerId)) { team -> PetBattle.simulateTrainer(team, trainer, arena.creatures, random) }
    }

    /** Marks a finished arena session complete if its alarm was missed (reboot, battery saver). */
    suspend fun completeIfExpired() {
        val session = activeSession() ?: return
        if (!session.completed && System.currentTimeMillis() >= session.endsAt && sessionRepo.hasTrustedClock(session)) {
            sessionRepo.markCompleted(session.sessionId)
        }
    }

    /** Pays out a finished arena session and clears the slot; null when nothing is ready. */
    suspend fun collect(): PetArenaCollectResult? = mutex.withLock {
        completeIfExpired()
        val session = activeSession()?.takeIf { it.completed } ?: return@withLock null
        val frames: List<SessionFrame> = try { json.decodeFromString(session.frames) } catch (_: Exception) { emptyList() }
        val petXp = mutableMapOf<String, Long>()
        val items = mutableMapOf<String, Int>()
        for (f in frames) {
            for ((id, xp) in f.petXp) petXp[id] = (petXp[id] ?: 0L) + xp
            for ((k, q) in f.items) items[k] = (items[k] ?: 0) + q
        }
        val wins = frames.sumOf { it.kills }
        var coins = (items.remove("coins") ?: 0).toLong()

        val trainerId = trainerIdOf(session.activityKey)
        val trainer = trainerId?.let { id -> arena.trainers.firstOrNull { it.id == id } }
        val badges = playerRepo.getFlags().petBadges
        val badge = trainer?.id?.takeIf { wins > 0 && it !in badges }
        if (badge != null) {
            coins += trainer.rewardCoins
            for ((k, q) in trainer.rewardItems) items[k] = (items[k] ?: 0) + q
        }
        val changes = playerRepo.applyPetArenaResults(petXp, items, coins, wins.toLong(), badge)
        sessionRepo.deleteSession(session.sessionId)
        PetArenaCollectResult(
            wins = wins, wiped = frames.any { it.died }, trainerId = trainerId, badgeEarned = badge,
            items = items, coins = coins, levelChanges = changes,
        )
    }
}
