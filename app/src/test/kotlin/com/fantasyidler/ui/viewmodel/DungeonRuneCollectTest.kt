package com.fantasyidler.ui.viewmodel

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fantasyidler.data.db.AppDatabase
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.repository.BackupScheduler
import com.fantasyidler.repository.BoostRepository
import com.fantasyidler.repository.BuffNotificationScheduler
import com.fantasyidler.repository.DailyQuestRepository
import com.fantasyidler.repository.FarmingRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.GlobalStateRepository
import com.fantasyidler.repository.GuildRepository
import com.fantasyidler.repository.MercenaryRepository
import com.fantasyidler.repository.MonumentRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.QuestRepository
import com.fantasyidler.repository.QueuedSessionStarter
import com.fantasyidler.repository.SaveSlotRepository
import com.fantasyidler.repository.SeasonalEventRepository
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.repository.SlayerRepository
import com.fantasyidler.repository.TitleRepository
import com.fantasyidler.repository.TownRepository
import com.fantasyidler.repository.WeeklyQuestRepository
import com.fantasyidler.repository.WorkerQueuedSessionStarter
import com.fantasyidler.repository.StoryRepository
import javax.inject.Provider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Dungeon sessions recorded runes cast in their frames but collect only ever added the
 * reclaimed share back, never deducting the gross, so magic dungeons gained runes.
 * Collect now consumes gross runes and adds the reclaim, like arrows and boss fights (#2045).
 */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [34])
class DungeonRuneCollectTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var playerRepo: PlayerRepository
    private lateinit var sessionRepo: SessionRepository
    private lateinit var gameData: GameDataRepository
    private lateinit var queuedStarter: QueuedSessionStarter
    private lateinit var workerStarter: WorkerQueuedSessionStarter
    private lateinit var viewModel: HomeViewModel
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        gameData = GameDataRepository(context, json)
        val boostRepo = BoostRepository(gameData)
        val dailyRepo = DailyQuestRepository(gameData)
        val weeklyRepo = WeeklyQuestRepository(gameData)
        val buffScheduler = BuffNotificationScheduler(context)
        playerRepo = PlayerRepository(
            db.playerDao(),
            db.questProgressDao(),
            db.farmingPatchDao(),
            json,
            dailyRepo,
            weeklyRepo,
            buffScheduler,
            gameData,
            boostRepo,
            db,
        )
        sessionRepo = SessionRepository(
            db.skillSessionDao(),
            context,
            json,
            gameData,
            db.playerDao(),
            playerRepo,
            StoryRepository(playerRepo),
        )
        val questRepo = QuestRepository(db.questProgressDao(), gameData)
        val townRepo = TownRepository(gameData, playerRepo, questRepo, boostRepo)
        val mercRepo = MercenaryRepository(playerRepo, gameData)
        val queuedStarter = QueuedSessionStarter(
            boostRepo, context, playerRepo, sessionRepo, townRepo, gameData, mercRepo, json,
        ).also { this.queuedStarter = it }
        val workerStarter = WorkerQueuedSessionStarter(
            boostRepo, playerRepo, sessionRepo, gameData, json,
        ).also { this.workerStarter = it }
        val guildRepo = GuildRepository(
            playerRepo, db.questProgressDao(), gameData, Provider { townRepo },
        )
        val slayerRepo = SlayerRepository(boostRepo, playerRepo, questRepo, gameData, guildRepo)
        val monumentRepo = MonumentRepository(playerRepo, buffScheduler)
        val seasonalRepo = SeasonalEventRepository(playerRepo, gameData, dailyRepo, context)
        val titleRepo = TitleRepository(playerRepo, gameData, db.questProgressDao(), guildRepo)
        val globalStateRepo = GlobalStateRepository(db.globalStateDao())
        val farmingRepo = FarmingRepository(
            context, db, db.farmingPatchDao(), playerRepo, gameData,
            seasonalRepo, globalStateRepo, json, boostRepo,
        )
        val backupScheduler = BackupScheduler(context, sessionRepo, globalStateRepo)
        val saveSlotRepo = SaveSlotRepository(
            context, playerRepo, sessionRepo, questRepo, farmingRepo, guildRepo,
            globalStateRepo, queuedStarter, workerStarter, backupScheduler, buffScheduler, json,
        )
        viewModel = HomeViewModel(
            boostRepo, context, playerRepo, sessionRepo, gameData, questRepo, guildRepo,
            townRepo, queuedStarter, workerStarter, slayerRepo, monumentRepo, seasonalRepo,
            titleRepo, saveSlotRepo, StoryRepository(playerRepo), json,
        )
        runBlocking { playerRepo.getOrCreatePlayer() }
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * viewModelScope runs on Robolectric's Main, and @Test methods also run on Main:
     * blocking it with runBlocking wedges the dispatcher (issue #2013 repro lesson).
     * So the test body runs on a background thread while THIS (main) thread pumps
     * the main looper until the body finishes. future.get rethrows assertion errors.
     */
    private fun driveFromBackground(timeoutSec: Long = 120, block: suspend () -> Unit) {
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit { runBlocking { block() } }
            val main = Shadows.shadowOf(Looper.getMainLooper())
            val deadline = System.currentTimeMillis() + timeoutSec * 1000L
            while (!future.isDone) {
                main.idle()
                if (System.currentTimeMillis() > deadline) {
                    future.cancel(true)
                    throw AssertionError("timed out driving the main looper")
                }
                Thread.sleep(25)
            }
            future.get()
        } finally {
            executor.shutdownNow()
        }
    }

    /** viewModelScope runs on Main (pumped by [driveFromBackground]): idle it until [condition] holds. */
    private suspend fun awaitMainCondition(what: String, condition: suspend () -> Boolean) {
        withTimeout(30_000L) {
            while (!condition()) delay(50)
        }
    }

    private fun frames(runesPerFrame: Int): String = json.encodeToString(
        ListSerializer(SessionFrame.serializer()),
        List(60) { m ->
            SessionFrame(
                minute = m + 1, xpGain = 0, xpBefore = 0L, xpAfter = 0L, levelBefore = 1, levelAfter = 1,
                runesConsumed = mapOf("air_rune" to runesPerFrame),
            )
        },
    )

    private suspend fun collectDungeon(isElder: Boolean) {
        val activity = if (isElder) "abyssal_depths" else "goblin_cave"
        sessionRepo.startSession(
            skillName         = "combat",
            activityKey       = activity,
            frames            = frames(runesPerFrame = 1),
            skillDisplayName  = activity,
            insertAsCompleted = true,
            isElderSession    = isElder,
        )
        viewModel.collectSession()
        // collectSession deletes the sessions only after every reward and consumable is applied.
        awaitMainCondition("session collected") { sessionRepo.getAllCompletedSessions().isEmpty() }
    }

    // 60 runes cast at Magic 1 (25% reclaim): 60 consumed, 15 reclaimed, net -45.
    @Test
    fun `mainland dungeon collect deducts runes net of reclaim`() = driveFromBackground {
        playerRepo.addItems(mapOf("air_rune" to 100))
        collectDungeon(isElder = false)
        assertEquals(55, playerRepo.getInventory()["air_rune"])
    }

    @Test
    fun `isle dungeon collect deducts runes net of reclaim`() = driveFromBackground {
        playerRepo.addItems(mapOf("air_rune" to 100))
        collectDungeon(isElder = true)
        assertEquals(55, playerRepo.getInventory()["air_rune"])
    }
}
