package com.fantasyidler.repository

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fantasyidler.data.db.AppDatabase
import com.fantasyidler.data.model.SkillSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [34])
class SessionClockTrustTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var sessionRepo: SessionRepository
    private lateinit var playerRepo: PlayerRepository
    private lateinit var starter: QueuedSessionStarter
    private lateinit var workerStarter: WorkerQueuedSessionStarter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, BOOT_COUNT)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val json = Json { ignoreUnknownKeys = true }
        val gameData = GameDataRepository(context, json)
        val boostRepo = BoostRepository(gameData)
        val dailyQuestRepo = DailyQuestRepository(gameData)
        val weeklyQuestRepo = WeeklyQuestRepository(gameData)
        playerRepo = PlayerRepository(
            db.playerDao(),
            db.questProgressDao(),
            db.farmingPatchDao(),
            json,
            dailyQuestRepo,
            weeklyQuestRepo,
            BuffNotificationScheduler(context),
            gameData,
            boostRepo,
            db,
        )
        sessionRepo = SessionRepository(db.skillSessionDao(), context, json, gameData, db.playerDao(), playerRepo, StoryRepository(playerRepo))
        starter = QueuedSessionStarter(
            boostRepo,
            context,
            playerRepo,
            sessionRepo,
            TownRepository(gameData, playerRepo, QuestRepository(db.questProgressDao(), gameData), boostRepo),
            gameData,
            MercenaryRepository(playerRepo, gameData),
            json,
        )
        workerStarter = WorkerQueuedSessionStarter(boostRepo, playerRepo, sessionRepo, gameData, json)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun makeIronman() = runBlocking {
        playerRepo.getOrCreatePlayer()
        playerRepo.updateFlagsAtomically { it.copy(ironman = true) }
    }

    private fun makeRegular() = runBlocking {
        playerRepo.getOrCreatePlayer()
    }

    private fun overdueMainSession(
        id: String,
        startElapsedMs: Long?,
        startBootCount: Int? = if (startElapsedMs != null) BOOT_COUNT else null,
    ): SkillSession {
        val now = System.currentTimeMillis()
        return SkillSession(
            sessionId = id,
            skillName = "mining",
            startedAt = now - 3_600_000,
            endsAt = now - 1000,
            activityKey = "iron_ore",
            startElapsedMs = startElapsedMs,
            startBootCount = startBootCount,
        )
    }

    private fun overdueWorkerSession(id: String, slot: Int, startElapsedMs: Long?): SkillSession {
        val now = System.currentTimeMillis()
        return SkillSession(
            sessionId = id,
            skillName = "mining",
            startedAt = now - 3_600_000,
            endsAt = now - 1000,
            activityKey = "iron_ore",
            isWorkerSession = true,
            workerSlot = slot,
            startElapsedMs = startElapsedMs,
            startBootCount = if (startElapsedMs != null) BOOT_COUNT else null,
        )
    }

    @Test
    fun `ironman trusted overdue session completes via watchdog`() = runBlocking {
        makeIronman()
        sessionRepo.insertSession(overdueMainSession("trusted", SystemClock.elapsedRealtime() - 3_600_000))

        sessionRepo.completeOverdueSessions(starter)

        assertTrue(sessionRepo.getSession("trusted")!!.completed)
    }

    @Test
    fun `ironman manipulated clock anchor keeps overdue session incomplete`() = runBlocking {
        makeIronman()
        sessionRepo.insertSession(overdueMainSession("faked", SystemClock.elapsedRealtime() - 1000))

        sessionRepo.completeOverdueSessions(starter)

        assertFalse(sessionRepo.getSession("faked")!!.completed)
    }

    @Test
    fun `normal character completes despite manipulated anchor`() = runBlocking {
        makeRegular()
        sessionRepo.insertSession(overdueMainSession("normal_faked", SystemClock.elapsedRealtime() - 1000))

        sessionRepo.completeOverdueSessions(starter)

        assertTrue(sessionRepo.getSession("normal_faked")!!.completed)
    }

    @Test
    fun `ironman anchor from a previous boot fails open`() = runBlocking {
        makeIronman()
        sessionRepo.insertSession(
            overdueMainSession("rebooted", SystemClock.elapsedRealtime() - 1000, startBootCount = BOOT_COUNT - 1))

        sessionRepo.completeOverdueSessions(starter)

        assertTrue(sessionRepo.getSession("rebooted")!!.completed)
    }

    @Test
    fun `ironman legacy session without anchor completes via watchdog`() = runBlocking {
        makeIronman()
        sessionRepo.insertSession(overdueMainSession("legacy", null))

        sessionRepo.completeOverdueSessions(starter)

        assertTrue(sessionRepo.getSession("legacy")!!.completed)
    }

    @Test
    fun `ironman trusted overdue worker session completes via watchdog`() = runBlocking {
        makeIronman()
        sessionRepo.insertSession(overdueWorkerSession("w_trusted", 1, SystemClock.elapsedRealtime() - 3_600_000))

        sessionRepo.completeOverdueSessions(starter, workerStarter)

        assertTrue(sessionRepo.getSession("w_trusted")!!.completed)
    }

    @Test
    fun `ironman manipulated worker anchor keeps overdue worker session incomplete`() = runBlocking {
        makeIronman()
        sessionRepo.insertSession(overdueWorkerSession("w_faked", 1, SystemClock.elapsedRealtime() - 1000))

        sessionRepo.completeOverdueSessions(starter, workerStarter)

        assertFalse(sessionRepo.getSession("w_faked")!!.completed)
    }

    @Test
    fun `normal character worker session completes despite manipulated anchor via bulk expiry`() = runBlocking {
        makeRegular()
        sessionRepo.insertSession(overdueWorkerSession("w_normal", 1, SystemClock.elapsedRealtime() - 1000))

        sessionRepo.markAllExpiredWorkerSessions()

        assertTrue(sessionRepo.getSession("w_normal")!!.completed)
    }

    @Test
    fun `ironman worker session stays incomplete via bulk expiry with manipulated anchor`() = runBlocking {
        makeIronman()
        sessionRepo.insertSession(overdueWorkerSession("w_iron_bulk", 1, SystemClock.elapsedRealtime() - 1000))

        sessionRepo.markAllExpiredWorkerSessions()

        assertFalse(sessionRepo.getSession("w_iron_bulk")!!.completed)
    }

    companion object {
        private const val BOOT_COUNT = 7
    }
}
