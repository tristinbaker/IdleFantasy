package com.fantasyidler.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import com.fantasyidler.data.db.dao.PlayerDao
import com.fantasyidler.data.db.dao.SkillSessionDao
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import com.fantasyidler.receiver.SessionAlarmReceiver
import com.fantasyidler.simulator.CombatSimulator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SkillSessionDao,
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val gameData: GameDataRepository,
    private val playerDao: PlayerDao,
    private val playerRepo: PlayerRepository,
) {
    val activeSessionFlow: Flow<SkillSession?> = sessionDao.observeActiveSession()
    val completedCountFlow: Flow<Int> = sessionDao.observeCompletedCount()
    val workerCompletedCountFlow: Flow<Int> = sessionDao.observeWorkerCompletedCount()

    fun workerCompletedCountFlow(slot: Int): Flow<Int> =
        sessionDao.observeWorkerCompletedCount(slot)

    fun activeWorkerSessionFlow(slot: Int): Flow<SkillSession?> =
        sessionDao.observeActiveWorkerSession(slot)

    suspend fun getActiveSession(): SkillSession? = sessionDao.getActiveSession()

    suspend fun getActiveWorkerSession(slot: Int): SkillSession? =
        sessionDao.getActiveWorkerSession(slot)

    suspend fun getAllCompletedWorkerSessions(slot: Int): List<SkillSession> =
        sessionDao.getAllCompletedWorkerSessions(slot)

    suspend fun deleteAllWorkerSessions(slot: Int) = sessionDao.deleteAllWorkerSessions(slot)
    suspend fun deleteAllWorkerSessions() = sessionDao.deleteAllWorkerSessions()

    /**
     * Persist a new session and schedule an AlarmManager alarm for completion.
     *
     * @param skillName        canonical skill key, e.g. "mining"
     * @param activityKey      sub-activity key, e.g. "iron_ore" or "dark_cave"
     * @param frames           pre-serialised JSON of List<SessionFrame>
     * @param durationMs       wall-clock duration (already reduced by agility bonus)
     * @param skillDisplayName localised skill name forwarded to the notification
     */
    suspend fun startSession(
        skillName: String,
        activityKey: String,
        frames: String,
        durationMs: Long = SESSION_DURATION_MS,
        skillDisplayName: String,
        alarmOffsetMs: Long? = null,
        insertAsCompleted: Boolean = false,
        backdateMs: Long = 0L,
        catalystKey: String? = null,
        catalystQty: Int = 0,
        levelAtStart: Int = 0,
        weaponSlot: String? = null,
        playerMutexHeld: Boolean = false,
        isElderSession: Boolean = false,
        consumedMaterials: String? = null,
    ): SkillSession {
        val now = System.currentTimeMillis()
        val startedAt = now - backdateMs
        val session = SkillSession(
            sessionId    = UUID.randomUUID().toString(),
            skillName    = skillName,
            startedAt    = startedAt,
            endsAt       = startedAt + durationMs,
            frames       = frames,
            activityKey  = activityKey,
            completed    = insertAsCompleted,
            catalystKey  = catalystKey,
            catalystQty  = catalystQty,
            levelAtStart = levelAtStart,
            startElapsedMs = if (insertAsCompleted) null else SystemClock.elapsedRealtime() - backdateMs,
            startBootCount = if (insertAsCompleted) null else currentBootCount(),
            isElderSession = isElderSession,
            consumedMaterials = consumedMaterials,
        )
        sessionDao.insert(session)
        if (playerMutexHeld) playerRepo.stampHeirloomMirrorTargetsUnlocked(session.sessionId, weaponSlot)
        else playerRepo.stampHeirloomMirrorTargets(session.sessionId, weaponSlot)
        if (!insertAsCompleted) {
            val alarmAt = if (alarmOffsetMs != null) startedAt + alarmOffsetMs else session.endsAt
            scheduleAlarm(session.sessionId, alarmAt, skillDisplayName)
        }
        return session
    }

    suspend fun startWorkerSession(
        workerSlot: Int,
        skillName: String,
        activityKey: String,
        frames: String,
        durationMs: Long,
        skillDisplayName: String,
        efficiencyMultiplier: Float,
        levelAtStart: Int = 0,
        weaponSlot: String? = null,
        playerMutexHeld: Boolean = false,
    ): SkillSession {
        val now = System.currentTimeMillis()
        val session = SkillSession(
            sessionId            = UUID.randomUUID().toString(),
            skillName            = skillName,
            startedAt            = now,
            endsAt               = now + durationMs,
            frames               = frames,
            activityKey          = activityKey,
            isWorkerSession      = true,
            efficiencyMultiplier = efficiencyMultiplier,
            workerSlot           = workerSlot,
            levelAtStart         = levelAtStart,
            startElapsedMs       = SystemClock.elapsedRealtime(),
            startBootCount       = currentBootCount(),
        )
        sessionDao.insert(session)
        if (playerMutexHeld) playerRepo.stampHeirloomMirrorTargetsUnlocked(session.sessionId, weaponSlot)
        else playerRepo.stampHeirloomMirrorTargets(session.sessionId, weaponSlot)
        scheduleAlarm(session.sessionId, session.endsAt, skillDisplayName)
        return session
    }

    suspend fun markCompleted(sessionId: String) {
        cancelAlarm(sessionId)
        sessionDao.markCompleted(sessionId)
    }

    /**
     * Wall-clock moment a boss fight is actually over (boss or player dead), derived
     * from the pre-simulated frames. endsAt is only the cosmetic full-duration end.
     */
    fun bossFightEndMs(session: SkillSession): Long = try {
        val frames: List<SessionFrame> = json.decodeFromString(session.frames)
        val durMin     = (gameData.bosses[session.activityKey]?.durationMinutes ?: 60).coerceAtLeast(1)
        val perFrameMs = ((session.endsAt - session.startedAt) / durMin).coerceAtLeast(1L)
        val offset     = CombatSimulator.bossEndAlarmOffsetMs(frames, durMin, perFrameMs)
        if (offset != null) minOf(session.endsAt, session.startedAt + offset) else session.endsAt
    } catch (_: Exception) { session.endsAt }

    /**
     * Heirloom item keys already rolled inside any stored session's frames (active or
     * completed-but-uncollected, player or worker). Boss simulations must block these
     * alongside owned heirlooms, otherwise two queued sessions can each roll the same
     * unique before the first is collected (issue #1618).
     */
    suspend fun pendingHeirloomKeys(): Set<String> {
        val heirloomKeys = gameData.equipment.filterValues { it.heirloomSkill != null }.keys
        if (heirloomKeys.isEmpty()) return emptySet()
        return sessionDao.getAllSessions().flatMapTo(mutableSetOf()) { session ->
            try {
                val frames: List<SessionFrame> = json.decodeFromString(session.frames)
                frames.flatMap { frame -> frame.items.keys.filter { it in heirloomKeys } }
            } catch (_: Exception) { emptyList() }
        }
    }

    /**
     * True when [session]'s completion time is consistent with its monotonic anchor.
     * Enforced for ironman characters only — normal characters always pass; anchors are
     * still stamped for everyone so enforcement decisions stay possible later. Fails open
     * when the anchor or boot count is missing, or when the device rebooted since the
     * session started (elapsedRealtime restarts at boot, making the anchor meaningless).
     */
    suspend fun hasTrustedClock(session: SkillSession): Boolean {
        val anchor = session.startElapsedMs ?: return true
        if (!isIronman()) return true
        val bootCount = currentBootCount()
        if (session.startBootCount == null || bootCount == null || bootCount != session.startBootCount) return true
        val elapsedSinceStart = SystemClock.elapsedRealtime() - anchor
        if (elapsedSinceStart < 0L) return true
        return System.currentTimeMillis() - session.startedAt <= elapsedSinceStart + CLOCK_SKEW_TOLERANCE_MS
    }

    private suspend fun isIronman(): Boolean = try {
        playerDao.getPlayer()?.let { json.decodeFromString<PlayerFlags>(it.flags).ironman } ?: false
    } catch (_: Exception) { false }

    internal fun currentBootCount(): Int? = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
    } catch (_: Exception) { null }

    private val watchdogMutex = Mutex()

    /**
     * In-app watchdog: completes any overdue session (main and workers) without
     * depending on AlarmManager delivery, which Doze can defer for hours. Boss
     * sessions end at their simulated death moment; everything else at endsAt.
     * Overdue time is fed to the queue as offline catch-up, same as recovery.
     * Safe to call repeatedly from any ViewModel ticker.
     */
    suspend fun completeOverdueSessions(
        starter: QueuedSessionStarter,
        workerStarter: WorkerQueuedSessionStarter? = null,
    ): Unit = watchdogMutex.withLock {
        val now = System.currentTimeMillis()
        val session = getActiveSession()
        if (session != null && !session.completed) {
            val endMs = if (session.skillName == "boss") bossFightEndMs(session) else session.endsAt
            if (now >= endMs && hasTrustedClock(session)) {
                markCompleted(session.sessionId)
                var catchUpMs = now - endMs
                while (catchUpMs > 0) {
                    val used = try { starter.insertNextQueuedAsOffline(catchUpMs) } catch (_: Exception) { 0L }
                    if (used == 0L) break
                    catchUpMs -= used
                }
                try { starter.startNextQueued(backdateMs = catchUpMs.coerceAtLeast(0L)) } catch (_: Exception) {}
            }
        } else if (session != null && session.completed) {
            // The session already finished but the next queued item never started (e.g. the
            // process died between the alarm's markCompleted and its startNextQueued, which
            // aggressive battery savers do). Keep retrying every tick, back-dating by the
            // time lost since the session ended — an unbackdated start here permanently
            // pushed the queue's schedule late (issue #1739).
            if (hasTrustedClock(session)) {
                val endMs = if (session.skillName == "boss") bossFightEndMs(session) else session.endsAt
                var catchUpMs = maxOf(0L, now - endMs)
                while (catchUpMs > 0) {
                    val used = try { starter.insertNextQueuedAsOffline(catchUpMs) } catch (_: Exception) { 0L }
                    if (used == 0L) break
                    catchUpMs -= used
                }
                try { starter.startNextQueued(backdateMs = catchUpMs) } catch (_: Exception) {}
            } else {
                try { starter.startNextQueued() } catch (_: Exception) {}
            }
        }
        if (workerStarter != null) {
            for (slot in 1..2) {
                val ws = getActiveWorkerSession(slot)
                if (ws != null && !ws.completed && now >= ws.endsAt && hasTrustedClock(ws)) {
                    markCompleted(ws.sessionId)
                    try { workerStarter.startNextQueued(slot) } catch (_: Exception) {}
                }
            }
        }
    }

    suspend fun markAllExpiredWorkerSessions() {
        sessionDao.markAllExpiredWorkerSessions(
            System.currentTimeMillis(),
            SystemClock.elapsedRealtime(),
            CLOCK_SKEW_TOLERANCE_MS,
            enforceClock = isIronman(),
            // -1 never matches a stored boot count, so an unreadable setting fails open.
            bootCount = currentBootCount() ?: -1,
        )
    }

    /**
     * Called on boot or app open to recover from a lost alarm.
     * - If the active session has already passed its end time, marks it complete and
     *   advances the queue via [starter].
     * - If it's still running, reschedules the alarm so it fires at the correct time.
     */
    suspend fun recoverActiveSession(starter: QueuedSessionStarter) {
        val session = try { getActiveSession() } catch (_: Exception) { null } ?: run {
            starter.startNextQueued()
            return
        }
        if (session.completed) {
            if (!hasTrustedClock(session)) return
            val endMs = if (session.skillName == "boss") bossFightEndMs(session) else session.endsAt
            var catchUpMs = maxOf(0L, System.currentTimeMillis() - endMs)
            while (catchUpMs > 0) {
                val used = try { starter.insertNextQueuedAsOffline(catchUpMs) } catch (_: Exception) { 0L }
                if (used == 0L) break
                catchUpMs -= used
            }
            try { starter.startNextQueued(backdateMs = catchUpMs) } catch (_: Exception) { markCompleted(session.sessionId) }
            return
        }
        // Boss sessions: endsAt is cosmetic (full duration). The session really ends
        // at bossFightEndMs — complete or re-arm the alarm based on that moment,
        // never on endsAt.
        if (session.skillName == "boss") {
            val fightEndMs = bossFightEndMs(session)
            if (System.currentTimeMillis() >= fightEndMs && hasTrustedClock(session)) {
                markCompleted(session.sessionId)
                // Fast-forward the offline window like the generic path below, or a repeat
                // chain (x100 boss runs) advances only one fight per app launch when the OS
                // suppresses alarms for a killed app (Discord report, Aug 2026).
                var catchUpMs = System.currentTimeMillis() - fightEndMs
                while (catchUpMs > 0) {
                    val used = try { starter.insertNextQueuedAsOffline(catchUpMs) } catch (_: Exception) { 0L }
                    if (used == 0L) break
                    catchUpMs -= used
                }
                try { starter.startNextQueued(backdateMs = catchUpMs) } catch (_: Exception) { }
            } else {
                scheduleAlarm(session.sessionId, fightEndMs, session.skillName)
            }
            return
        }
        val now = System.currentTimeMillis()
        try {
            if (now >= session.endsAt && hasTrustedClock(session)) {
                markCompleted(session.sessionId)
                var catchUpMs = now - session.endsAt
                while (catchUpMs > 0) {
                    val used = starter.insertNextQueuedAsOffline(catchUpMs)
                    if (used == 0L) break
                    catchUpMs -= used
                }
                starter.startNextQueued(backdateMs = catchUpMs)
            } else {
                scheduleAlarm(session.sessionId, session.endsAt, session.skillName)
            }
        } catch (_: Exception) {
            if (hasTrustedClock(session)) markCompleted(session.sessionId)
        }
    }

    suspend fun recoverActiveWorkerSession(slot: Int, workerStarter: WorkerQueuedSessionStarter) {
        val session = try { getActiveWorkerSession(slot) } catch (_: Exception) { null } ?: run {
            workerStarter.startNextQueued(slot)
            return
        }
        if (session.completed) {
            workerStarter.startNextQueued(slot)
            return
        }
        val now = System.currentTimeMillis()
        try {
            if (now >= session.endsAt && hasTrustedClock(session)) {
                markCompleted(session.sessionId)
                workerStarter.startNextQueued(slot)
            } else {
                scheduleAlarm(session.sessionId, session.endsAt, session.skillName)
            }
        } catch (_: Exception) {
            if (hasTrustedClock(session)) markCompleted(session.sessionId)
        }
    }

    suspend fun getSession(sessionId: String): SkillSession? = sessionDao.getSession(sessionId)

    suspend fun abandonSession(sessionId: String) {
        cancelAlarm(sessionId)
        sessionDao.delete(sessionId)
        pruneMirrorStamps()
    }

    /** Delete a completed session after rewards have been applied. */
    suspend fun deleteSession(sessionId: String) {
        cancelAlarm(sessionId)
        sessionDao.delete(sessionId)
        pruneMirrorStamps()
    }

    suspend fun deleteAllSessions() {
        sessionDao.deleteAll()
        pruneMirrorStamps()
    }

    private suspend fun pruneMirrorStamps() =
        playerRepo.pruneHeirloomMirrorTargets(sessionDao.getAllSessions().mapTo(mutableSetOf()) { it.sessionId })

    suspend fun insertSession(session: SkillSession) = sessionDao.insert(session)

    suspend fun getRecentCompleted(limit: Int = 20): List<SkillSession> =
        sessionDao.getRecentCompleted(limit)

    suspend fun getAllCompletedSessions(): List<SkillSession> =
        sessionDao.getAllCompletedSessions()

    suspend fun getOldestCompletedSession(): SkillSession? =
        sessionDao.getOldestCompletedSession()

    /**
     * Food already simulated but not yet deducted from inventory, summed over the
     * whole completed-but-uncollected backlog (collected sessions are deleted, so
     * the remainder is exactly what collection will still deduct). Every combat
     * simulation input must subtract this (see [FoodReservation]); reading only
     * the active session lets queued/offline sessions 2..N simulate against a
     * phantom full supply (issue #1960).
     */
    suspend fun pendingFoodConsumed(): Map<String, Int> {
        val sessions = try {
            getAllCompletedSessions()
        } catch (_: Exception) {
            return emptyMap()
        }
        return FoodReservation.aggregatePending(sessions) { raw ->
            try {
                json.decodeFromString<List<SessionFrame>>(raw)
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    // ------------------------------------------------------------------

    private fun alarmIntent(sessionId: String, skillDisplayName: String): PendingIntent {
        val intent = Intent(context, SessionAlarmReceiver::class.java).apply {
            putExtra(SessionAlarmReceiver.KEY_SESSION_ID, sessionId)
            putExtra(SessionAlarmReceiver.KEY_SKILL_DISPLAY_NAME, skillDisplayName)
        }
        return PendingIntent.getBroadcast(
            context,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelIntent(sessionId: String): PendingIntent {
        val intent = Intent(context, SessionAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun scheduleAlarm(sessionId: String, endsAt: Long, skillDisplayName: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = alarmIntent(sessionId, skillDisplayName)
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, pi)
        }
    }

    internal fun cancelAlarm(sessionId: String) {
        try {
            val am      = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = cancelIntent(sessionId)
            am.cancel(pending)
            pending.cancel()
        } catch (_: Exception) {}
    }

    companion object {
        const val SESSION_DURATION_MS = 60L * 60L * 1_000L  // 1 hour
        const val CLOCK_SKEW_TOLERANCE_MS = 120_000L
    }
}
