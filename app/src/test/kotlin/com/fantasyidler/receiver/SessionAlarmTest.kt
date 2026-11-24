package com.fantasyidler.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fantasyidler.data.db.AppDatabase
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.repository.BoostRepository
import com.fantasyidler.repository.BuffNotificationScheduler
import com.fantasyidler.repository.DailyQuestRepository
import com.fantasyidler.repository.GameDataRepository
import com.fantasyidler.repository.PlayerRepository
import com.fantasyidler.repository.SessionRepository
import com.fantasyidler.repository.WeeklyQuestRepository
import com.fantasyidler.repository.StoryRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [34])
class SessionAlarmTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var sessionRepo: SessionRepository
    private lateinit var alarmManager: AlarmManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val json = Json { ignoreUnknownKeys = true }
        val gameData = GameDataRepository(context, json)
        val playerRepo = PlayerRepository(
            db.playerDao(), db.questProgressDao(), db.farmingPatchDao(), json,
            DailyQuestRepository(gameData), WeeklyQuestRepository(gameData),
            BuffNotificationScheduler(context), gameData, BoostRepository(gameData), db,
        )
        sessionRepo = SessionRepository(db.skillSessionDao(), context, json, gameData, db.playerDao(), playerRepo, StoryRepository(playerRepo))
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `markCompleted cancels the pending alarm`() = runBlocking {
        val shadowAm = Shadows.shadowOf(alarmManager)
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val session = SkillSession(
            sessionId = sessionId,
            playerId = 1L,
            skillName = "mining",
            startedAt = now,
            endsAt = now + 3600_000L,
            frames = "[]",
            completed = false,
            activityKey = "copper_rock",
        )
        sessionRepo.insertSession(session)

        val method = sessionRepo.javaClass.getDeclaredMethod(
            "alarmIntent",
            String::class.java,
            String::class.java,
        ).apply { isAccessible = true }
        val pi = method.invoke(sessionRepo, sessionId, "Mining") as PendingIntent
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, session.endsAt, pi)

        assertEquals(1, shadowAm.scheduledAlarms.size)

        sessionRepo.markCompleted(sessionId)

        assertEquals(0, shadowAm.scheduledAlarms.size)
    }

    @Test
    fun `deleteSession cancels the pending alarm`() = runBlocking {
        val shadowAm = Shadows.shadowOf(alarmManager)
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val session = SkillSession(
            sessionId = sessionId,
            playerId = 1L,
            skillName = "mining",
            startedAt = now,
            endsAt = now + 3600_000L,
            frames = "[]",
            completed = false,
            activityKey = "copper_rock",
        )
        sessionRepo.insertSession(session)

        val method = sessionRepo.javaClass.getDeclaredMethod(
            "alarmIntent",
            String::class.java,
            String::class.java,
        ).apply { isAccessible = true }
        val pi = method.invoke(sessionRepo, sessionId, "Mining") as PendingIntent
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, session.endsAt, pi)

        assertEquals(1, shadowAm.scheduledAlarms.size)

        sessionRepo.deleteSession(sessionId)

        assertEquals(0, shadowAm.scheduledAlarms.size)
    }

    @Test
    fun `receiver processSessionAlarm returns early when session is already completed`() = runBlocking {
        val sessionId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val session = SkillSession(
            sessionId = sessionId,
            playerId = 1L,
            skillName = "mining",
            startedAt = now - 3600_000L,
            endsAt = now,
            frames = "[]",
            completed = true,
            activityKey = "copper_rock",
        )
        sessionRepo.insertSession(session)

        val receiver = SessionAlarmReceiver().apply {
            this.sessionRepository = sessionRepo
        }

        // Calling processSessionAlarm on an already-completed session must return early
        // without throwing or accessing uninitialized queuedSessionStarter/notificationManager.
        receiver.processSessionAlarm(sessionId, "Mining")

        val storedSession = sessionRepo.getSession(sessionId)
        assertNotNull(storedSession)
        assertTrue(storedSession!!.completed)
    }

    @Test
    fun `receiver processSessionAlarm returns early when session does not exist`() = runBlocking {
        val nonExistentSessionId = UUID.randomUUID().toString()

        val receiver = SessionAlarmReceiver().apply {
            this.sessionRepository = sessionRepo
        }

        // Calling processSessionAlarm on a non-existent session must return early
        // without throwing or accessing uninitialized queuedSessionStarter/notificationManager.
        receiver.processSessionAlarm(nonExistentSessionId, "Mining")
    }
}
