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

enum class PetBattleStartResult { SUCCESS, NO_TEAM, NEEDS_REST, LOCKED, ALREADY_BATTLING, UNKNOWN }

/** What one collected arena session paid out, for the result banner. */
data class PetArenaCollectResult(
    val wins: Int,
    val wiped: Boolean,
    val trainerId: String?,
    val badgeEarned: String?,
    val items: Map<String, Int>,
    val coins: Long,
    val levelChanges: List<PlayerRepository.PetLevelChange>,
    /** The Ultimate Challenge was fought (win or loss). */
    val ultimate: Boolean = false,
    /** A pet that dropped and joined the collection (the Aether Hatchling). */
    val petFound: String? = null,
    /** The team was knocked out in the wild, so half the hour's loot and coins were lost. */
    val lootHalved: Boolean = false,
    /** Each pet's HP share when the battle ended. */
    val hpEnd: Map<String, Double> = emptyMap(),
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
        /** Title for beating the Ultimate Challenge. */
        const val ULTIMATE_TITLE = "first_beast_slayer"
        private const val ULTIMATE_PREFIX = "ultimate:"

        fun ultimateKey(id: String) = "$ULTIMATE_PREFIX$id"
        fun ultimateIdOf(activityKey: String): String? = activityKey.removePrefix(ULTIMATE_PREFIX).takeIf { activityKey.startsWith(ULTIMATE_PREFIX) }
        /** The Ultimate Challenge opens once every trainer's badge is held. */
        fun ultimateUnlocked(trainers: List<ArenaTrainer>, badges: Set<String>): Boolean = trainers.isNotEmpty() && trainers.all { it.id in badges }
        private const val ZONE_PREFIX = "zone:"
        private const val TRAINER_PREFIX = "trainer:"
        private const val MINUTE_MS = 60_000L

        /** Ladder rule: a trainer can be challenged once every trainer before it is beaten. */
        fun trainerUnlocked(trainers: List<ArenaTrainer>, index: Int, badges: Set<String>): Boolean =
            index in trainers.indices && trainers.take(index).all { it.id in badges }

        fun zoneUnlocked(zone: ArenaZone, badges: Set<String>): Boolean = badges.size >= zone.badgesRequired

        /** A wild wipe's penalty: half of every item stack (rounded down) and half the coins. */
        fun halveLoot(items: Map<String, Int>, coins: Long): Pair<Map<String, Int>, Long> =
            items.mapValues { it.value / 2 }.filterValues { it > 0 } to coins / 2

        fun zoneKey(id: String) = "$ZONE_PREFIX$id"
        fun trainerKey(id: String) = "$TRAINER_PREFIX$id"
        fun trainerIdOf(activityKey: String): String? = activityKey.removePrefix(TRAINER_PREFIX).takeIf { activityKey.startsWith(TRAINER_PREFIX) }
        fun zoneIdOf(activityKey: String): String? = activityKey.removePrefix(ZONE_PREFIX).takeIf { activityKey.startsWith(ZONE_PREFIX) }
    }

    val arena get() = gameData.petArena

    /** Serialises start and collect so a double tap can neither start two battles nor pay one twice. */
    private val mutex = Mutex()

    suspend fun activeSession(): SkillSession? = sessionRepo.getActiveWorkerSession(PET_ARENA_SLOT)

    /** A team member ready to fight: its stats (Well Fed folded in), HP going in, and HP share now. */
    private data class Entrant(val fighter: PetBattle.Fighter, val hp: Int, val fraction: Double)

    /** The team's pets that can fight right now: owned, and not knocked out (0 HP pets sit out until healed). */
    private fun entrants(teamIds: List<String>, pets: List<OwnedPet>, now: Long, wellFed: Boolean = true): List<Entrant>? {
        val owned = pets.associateBy { it.id }
        val team = teamIds.mapNotNull { id ->
            val pet = owned[id] ?: return@mapNotNull null
            val data = gameData.pets[id] ?: return@mapNotNull null
            val fraction = PetBattle.restedFraction(pet.hpFraction, pet.hpAt, now)
            val fighter = PetBattle.petFighter(data, pet.xp, if (wellFed) pet.wellFed else 0)
            Entrant(fighter, (fraction * fighter.maxHp).toInt(), fraction)
        }
        return if (team.isEmpty()) null else team.filter { it.hp > 0 }
    }

    private suspend fun start(
        activityKey: String,
        seed: Long,
        ultimate: Boolean = false,
        simulate: (List<PetBattle.Fighter>, Random, List<Int>) -> List<SessionFrame>,
    ): PetBattleStartResult = mutex.withLock {
        if (activeSession() != null) return@withLock PetBattleStartResult.ALREADY_BATTLING
        val now = System.currentTimeMillis()
        val player = playerRepo.getOrCreatePlayer()
        val flags: PlayerFlags = json.decodeFromString(player.flags)
        val pets: List<OwnedPet> = json.decodeFromString(player.pets)
        val ready: List<Entrant>
        if (ultimate) {
            // All 8 must be there and standing; Aetherion's aura cancels Well Fed, so it waits for the next battle.
            val size = arena.ultimate?.teamSize ?: PetBattle.MAX_TEAM
            val all = entrants(flags.petChallengeTeam, pets, now, wellFed = false) ?: return@withLock PetBattleStartResult.NO_TEAM
            if (flags.petChallengeTeam.size < size || flags.petChallengeTeam.any { id -> pets.none { it.id == id } }) return@withLock PetBattleStartResult.NO_TEAM
            if (all.size < size) return@withLock PetBattleStartResult.NEEDS_REST
            ready = all
        } else {
            ready = entrants(flags.petTeam, pets, now) ?: return@withLock PetBattleStartResult.NO_TEAM
            if (ready.isEmpty()) return@withLock PetBattleStartResult.NEEDS_REST
        }
        val team = ready.map { it.fighter }
        val startHp = ready.map { it.hp }
        // Frame 0 keeps the seed, starting team and starting HP so the battle screen can replay the fight.
        val frames = simulate(team, Random(seed), startHp)
            .let { f -> listOf(f[0].copy(petSeed = seed, petTeamStart = team, petStartHp = startHp)) + f.drop(1) }
        // The Well Fed bonus is spent (not in the Ultimate), and each pet's HP baseline is fixed for the battle.
        playerRepo.beginPetBattle(ready.associate { it.fighter.id to it.fraction }, now, spendWellFed = !ultimate)
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

    suspend fun startZone(zoneId: String, seed: Long = Random.nextLong()): PetBattleStartResult {
        val zone = arena.zones.firstOrNull { it.id == zoneId } ?: return PetBattleStartResult.UNKNOWN
        if (!zoneUnlocked(zone, playerRepo.getFlags().petBadges)) return PetBattleStartResult.LOCKED
        return start(zoneKey(zoneId), seed) { team, rnd, hp -> PetBattle.simulateZone(team, zone, arena.creatures, rnd, hp) }
    }

    suspend fun startTrainer(trainerId: String, seed: Long = Random.nextLong()): PetBattleStartResult {
        val index = arena.trainers.indexOfFirst { it.id == trainerId }
        if (index < 0) return PetBattleStartResult.UNKNOWN
        if (!trainerUnlocked(arena.trainers, index, playerRepo.getFlags().petBadges)) return PetBattleStartResult.LOCKED
        val trainer = arena.trainers[index]
        return start(trainerKey(trainerId), seed) { team, rnd, hp -> PetBattle.simulateTrainer(team, trainer, arena.creatures, rnd, hp) }
    }

    suspend fun startUltimate(seed: Long = Random.nextLong()): PetBattleStartResult {
        val u = arena.ultimate ?: return PetBattleStartResult.UNKNOWN
        if (!ultimateUnlocked(arena.trainers, playerRepo.getFlags().petBadges)) return PetBattleStartResult.LOCKED
        return start(ultimateKey(u.id), seed, ultimate = true) { team, rnd, hp -> PetBattle.simulateUltimate(team, u, rnd, hp) }
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
        // A wild wipe costs half of everything the hour dropped (XP is kept).
        val lootHalved = zoneIdOf(session.activityKey) != null && frames.any { it.died }
        if (lootHalved) {
            val (kept, keptCoins) = halveLoot(items, coins)
            items.clear()
            items.putAll(kept)
            coins = keptCoins
        }

        val trainerId = trainerIdOf(session.activityKey)
        val trainer = trainerId?.let { id -> arena.trainers.firstOrNull { it.id == id } }
        val badges = playerRepo.getFlags().petBadges
        val badge = trainer?.id?.takeIf { wins > 0 && it !in badges }
        if (badge != null) {
            coins += trainer.rewardCoins
            for ((k, q) in trainer.rewardItems) items[k] = (items[k] ?: 0) + q
        }
        // Wounds carry out of the arena: each pet's HP share at the end, resting from when the battle ended.
        val fighters = frames.firstOrNull()?.petTeamStart.orEmpty().associateBy { it.id }
        val hpEnd = frames.lastOrNull()?.petHpEnd.orEmpty().mapNotNull { (id, hp) ->
            fighters[id]?.let { f -> id to (hp.toDouble() / f.maxHp.coerceAtLeast(1)).coerceIn(0.0, 1.0) }
        }.toMap()
        // A pet drop joins the collection instead of the inventory.
        val ultimate = ultimateIdOf(session.activityKey) != null
        val petFound = items.keys.firstOrNull { it in gameData.pets }
        if (petFound != null) items.remove(petFound)
        val changes = playerRepo.applyPetArenaResults(
            petXp, items, coins, wins.toLong(), badge, hpEnd, minOf(session.endsAt, System.currentTimeMillis()),
            ultimateWin = ultimate && wins > 0,
            newPet = petFound?.let { id -> id to (gameData.pets[id]?.boostPercent ?: 0) },
        )
        sessionRepo.deleteSession(session.sessionId)
        PetArenaCollectResult(
            wins = wins, wiped = frames.any { it.died }, trainerId = trainerId, badgeEarned = badge,
            items = items, coins = coins, levelChanges = changes, lootHalved = lootHalved, hpEnd = hpEnd,
            ultimate = ultimate, petFound = petFound,
        )
    }
}
