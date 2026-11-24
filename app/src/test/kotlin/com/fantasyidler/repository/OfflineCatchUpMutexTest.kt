package com.fantasyidler.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fantasyidler.data.db.AppDatabase
import com.fantasyidler.data.model.QueuedAction
import com.fantasyidler.data.model.Skills
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Offline queue fast-forward must hold playerMutex
 * around its internal mutex, matching startNextQueued()'s lock hierarchy
 * (playerMutex -> QueuedSessionStarter.mutex), and must not re-enter playerMutex.
 */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [34])
class OfflineCatchUpMutexTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var playerRepo: PlayerRepository
    private lateinit var starter: QueuedSessionStarter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val json = Json { ignoreUnknownKeys = true }
        val gameData = GameDataRepository(context, json)
        val dailyQuestRepo = DailyQuestRepository(gameData)
        val weeklyQuestRepo = WeeklyQuestRepository(gameData)
        val boostRepo = BoostRepository(gameData)
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
        val sessionRepo = SessionRepository(db.skillSessionDao(), context, json, gameData, db.playerDao(), playerRepo, StoryRepository(playerRepo))
        val questRepo = QuestRepository(db.questProgressDao(), gameData)
        val townRepo = TownRepository(gameData, playerRepo, questRepo, boostRepo)
        val mercRepo = MercenaryRepository(playerRepo, gameData)
        starter = QueuedSessionStarter(
            boostRepo, context, playerRepo, sessionRepo, townRepo, gameData, mercRepo, json,
        )
        runBlocking { playerRepo.getOrCreatePlayer() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insertNextQueuedAsOffline serializes on playerMutex without re-entering it`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val holder = launch { playerRepo.withLock { gate.await() } }
        var waited = 0
        while (!playerRepo.playerMutex.isLocked && waited < 2_000) { delay(5); waited += 5 }
        assertTrue("holder never acquired playerMutex", playerRepo.playerMutex.isLocked)

        val ranWhileLocked = withTimeoutOrNull(300) { starter.insertNextQueuedAsOffline(1_000L); true }
        assertNull("offline catch-up ran while playerMutex was held elsewhere", ranWhileLocked)

        gate.complete(Unit)
        val finishedAfterRelease = withTimeoutOrNull(3_000) { starter.insertNextQueuedAsOffline(1_000L); true }
        assertNotNull("offline catch-up did not finish after lock release (re-entry deadlock)", finishedAfterRelease)

        holder.cancel()
    }

    @Test
    fun `startNextQueued starts a queued action without re-entering playerMutex`() = runBlocking {
        // Regression for the 1.14.5 deadlock: startNextQueued holds playerMutex while
        // startSession stamps heirloom mirror targets, which must use the unlocked variant.
        playerRepo.enqueueAction(
            QueuedAction(
                skillName        = Skills.MINING,
                activityKey      = "copper_ore",
                skillDisplayName = "Mining",
            )
        )
        val started = withTimeoutOrNull(10_000) { starter.startNextQueued() }
        assertNotNull("startNextQueued deadlocked (playerMutex re-entry via heirloom stamp)", started)
        assertTrue("queued action failed to start", started == true)
    }
}
