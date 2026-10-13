package com.fantasyidler.repository

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.fantasyidler.data.model.EquipSlot
import com.fantasyidler.data.model.OwnedPet
import com.fantasyidler.data.model.PlayerExport
import com.fantasyidler.data.model.PlayerFlags
import com.fantasyidler.data.model.SkillSessionExport
import com.fantasyidler.data.model.Skills
import com.fantasyidler.data.model.toExport
import com.fantasyidler.data.model.toSkillSession
import com.fantasyidler.simulator.XpTable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Lightweight per-slot summary written alongside each snapshot so the picker
 *  never has to parse the (potentially multi-MB) full save file. */
@Serializable
data class SaveSlotMeta(
    /** Raw PlayerFlags JSON — gives character name, appearance, and ironman. */
    @SerialName("flags") val flags: String,
    /** Raw Map<String, Int> JSON of skill levels. */
    @SerialName("skill_levels") val skillLevels: String,
    @SerialName("coins") val coins: Long,
    @SerialName("last_played_at") val lastPlayedAt: Long,
)

/** Picker-facing view of one save slot. */
data class SlotInfo(
    val slot: Int,
    val isActive: Boolean,
    val exists: Boolean,
    val flags: PlayerFlags? = null,
    val skillLevels: Map<String, Int> = emptyMap(),
    val coins: Long = 0L,
    val lastPlayedAt: Long = 0L,
)

/**
 * Manages up to [MAX_SLOTS] character save slots.
 *
 * The live Room DB always holds only the ACTIVE character. Inactive slots are stored as
 * full save-export JSON files (the same [PlayerExport] format as manual export/import) under
 * filesDir/save_slots/. Switching slots snapshots the outgoing character to its file, then
 * restores the incoming one through the same import path used by Settings > Import save —
 * incomplete sessions keep their absolute end times (they finish in real time while the
 * character is inactive; only manual file imports freeze remaining time) and alarms are
 * re-registered for the incoming character.
 */
@Singleton
class SaveSlotRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playerRepo: PlayerRepository,
    private val sessionRepo: SessionRepository,
    private val questRepo: QuestRepository,
    private val farmingRepo: FarmingRepository,
    private val guildRepo: GuildRepository,
    private val globalStateRepo: GlobalStateRepository,
    private val queuedSessionStarter: QueuedSessionStarter,
    private val workerStarter: WorkerQueuedSessionStarter,
    private val backupScheduler: BackupScheduler,
    private val buffNotifScheduler: BuffNotificationScheduler,
    private val json: Json,
) {
    private val slotsDir: File get() = File(context.filesDir, "save_slots")

    private fun slotFile(slot: Int) = File(slotsDir, "slot_$slot.json")
    private fun metaFile(slot: Int) = File(slotsDir, "slot_$slot.meta.json")
    // Marker written before a switch begins and deleted on success; its presence at startup means
    // a switch crashed mid-way. Contents: a bare slot (pre-snapshot and legacy — disambiguated by
    // identity), "ready:<slot>" or "fresh:<ironman>:<slot>" (both written after the snapshot).
    private fun pendingSwitchFile() = File(slotsDir, "pending_switch.marker")

    // Serializes concurrent switches and lets other code (e.g. HomeViewModel.collectSession)
    // refuse to start work while a switch is running.
    private val switchMutex = Mutex()
    @Volatile var switchInProgress: Boolean = false
        private set

    // ------------------------------------------------------------------
    // Full-save export/import — shared with SettingsViewModel
    // ------------------------------------------------------------------

    /** All sessions worth persisting: the active one plus completed ones, player and both worker slots. */
    suspend fun collectSessionExports(): List<SkillSessionExport> = buildList {
        sessionRepo.getActiveSession()?.let { add(it.toExport()) }
        addAll(sessionRepo.getAllCompletedSessions().map { it.toExport() })
        for (slot in 1..2) {
            sessionRepo.getActiveWorkerSession(slot)?.let { add(it.toExport()) }
            addAll(sessionRepo.getAllCompletedWorkerSessions(slot).map { it.toExport() })
        }
    }.distinctBy { it.sessionId }

    /** Serializes the current character (player + quests + patches + sessions) to save-file JSON. */
    suspend fun exportFullSave(): String = playerRepo.exportSave(collectSessionExports())

    /**
     * Overwrites the current character with [jsonString] and restores its sessions.
     * With [freezeSessionTimers] (manual file imports) incomplete sessions resume with the
     * remaining time they had at export; without it (slot switches) they keep their absolute
     * end times so they finish in real time while the character is inactive, and the recovery
     * pass below completes them and catches up the queue exactly as if the app had been closed.
     * Session/buff/backup alarms are re-registered for the restored character.
     * Returns true if an edited ironman save was demoted to a regular character.
     */
    suspend fun importFullSave(jsonString: String, freezeSessionTimers: Boolean = true): Boolean {
        val (export, ironmanDemoted) = playerRepo.importSave(jsonString)
        // Imported flags may predate the guild-leveling rework (reputation -> daily-count),
        // and importing overwrites the current save's migration state wholesale, so this
        // must re-run here rather than relying on the one-time app-startup call.
        guildRepo.migrateLegacyGuildReputation()
        sessionRepo.deleteAllSessions()
        sessionRepo.deleteAllWorkerSessions()
        val now = System.currentTimeMillis()
        val exportedAt = export.exportedAt.takeIf { it > 0L } ?: now
        export.sessions.forEach { s ->
            val restored = if (s.completed || !freezeSessionTimers) {
                s.toSkillSession()
            } else {
                val remainingMs = (s.endsAt - exportedAt).coerceAtLeast(0L)
                s.toSkillSession().copy(endsAt = now + remainingMs)
            }
            val session = if (s.completed) restored else restored.copy(
                startElapsedMs = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - s.startedAt),
                startBootCount = sessionRepo.currentBootCount(),
            )
            try {
                sessionRepo.insertSession(session)
            } catch (_: Exception) {
                // A duplicate/bad session in an old export file shouldn't abort the whole restore.
            }
        }
        sessionRepo.recoverActiveSession(queuedSessionStarter)
        sessionRepo.recoverActiveWorkerSession(1, workerStarter)
        sessionRepo.recoverActiveWorkerSession(2, workerStarter)
        rescheduleAlarmsFromFlags()
        return ironmanDemoted
    }

    // ------------------------------------------------------------------
    // Slot management
    // ------------------------------------------------------------------

    suspend fun activeSlot(): Int = globalStateRepo.getActiveSaveSlot()

    /** True when more than one character exists: the active slot always exists, so any inactive slot file counts. */
    suspend fun hasMultipleCharacters(): Boolean = withContext(Dispatchers.IO) {
        val active = globalStateRepo.getActiveSaveSlot()
        (1..MAX_SLOTS).any { it != active && (metaFile(it).exists() || slotFile(it).exists()) }
    }

    suspend fun slotInfos(): List<SlotInfo> = withContext(Dispatchers.IO) {
        val active = globalStateRepo.getActiveSaveSlot()
        (1..MAX_SLOTS).map { slot ->
            if (slot == active) {
                val player = playerRepo.getOrCreatePlayer()
                SlotInfo(
                    slot         = slot,
                    isActive     = true,
                    exists       = true,
                    flags        = decodeFlags(player.flags),
                    skillLevels  = decodeLevels(player.skillLevels),
                    coins        = player.coins,
                    lastPlayedAt = System.currentTimeMillis(),
                )
            } else {
                readInactiveSlot(slot)
            }
        }
    }

    /**
     * Saves the current character into its slot file, then loads [targetSlot].
     * An empty target slot becomes a brand-new character ([createIronman] decides its mode);
     * the fresh flags have characterSetupDone=false, so the Home screen shows the setup sheet.
     * Returns true if the loaded slot held an edited ironman save that was demoted.
     */
    /**
     * Fires after every successful character switch. ViewModels caching per-character UI
     * selections (active spell, arrow, potion, weapon style) must reset on it, or the old
     * character's picks override the new character's saved loadout (issue: spell leaking
     * across save slots).
     */
    private val _switchEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val switchEvents: SharedFlow<Unit> = _switchEvents

    suspend fun switchTo(targetSlot: Int, createIronman: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        require(targetSlot in 1..MAX_SLOTS) { "Invalid slot $targetSlot" }
        switchMutex.withLock {
            val current = globalStateRepo.getActiveSaveSlot()
            if (targetSlot == current) return@withLock false

            // Back up the outgoing character to its own external file while it is still live,
            // so an inactive character always has a fresh backup to restore from (issue #1640:
            // a cleared app storage lost the character whose backup never fired while active).
            backupScheduler.performBackup(playerRepo)

            switchInProgress = true
            try {
                slotsDir.mkdirs()
                // Phase 1: a crash from here until a phase-2 marker below leaves the outgoing
                // character live in Room; recovery disambiguates by identity and must not import
                // the target blindly. Bare ints match the legacy pre-fix format.
                writeAtomic(pendingSwitchFile(), targetSlot.toString())

                snapshotCurrent(current)

                val target = slotFile(targetSlot)
                var ironmanDemoted = false
                if (target.exists()) {
                    // Phase 2 (existing target): the outgoing character is safely on disk, so a
                    // crash from here on may complete by importing the target file.
                    writeAtomic(pendingSwitchFile(), "$SNAPSHOT_READY_PREFIX$targetSlot")
                    ironmanDemoted = importFullSave(target.readText(), freezeSessionTimers = false)
                } else {
                    // Phase 2 (new target): record the pending reset (with the ironman choice)
                    // before running it; a crash mid-way must be replayed from the start.
                    writeAtomic(pendingSwitchFile(), "$FRESH_TARGET_PREFIX${if (createIronman) 1 else 0}:$targetSlot")
                    resetToFreshCharacter(createIronman)
                }
                globalStateRepo.setActiveSaveSlot(targetSlot)
                deletePendingSwitchMarker()
                _switchEvents.tryEmit(Unit)
                ironmanDemoted
            } finally {
                switchInProgress = false
            }
        }
    }

    /** Where an interrupted switch was when the process died, parsed from the marker. */
    private enum class SwitchPhase {
        /** Bare-slot marker (pre-snapshot, and the only format older versions wrote): the phase
         *  is unknown, so recovery disambiguates by comparing Room with the target file. */
        LEGACY,
        /** [SNAPSHOT_READY_PREFIX] marker: the snapshot completed and the target file exists. */
        READY_TO_IMPORT,
        /** [FRESH_TARGET_PREFIX] marker: the snapshot completed and the target slot was new. */
        FRESH_TARGET,
    }

    /** Parsed pending-switch marker. [ironman] matters only for [SwitchPhase.FRESH_TARGET]. */
    private data class PendingSwitch(
        val phase: SwitchPhase,
        val targetSlot: Int,
        val ironman: Boolean = false,
    )

    /**
     * The [SNAPSHOT_READY_PREFIX]/[FRESH_TARGET_PREFIX] markers are written only after the
     * snapshot; bare slot numbers (legacy) need identity disambiguation. Anything else gets cleared.
     */
    private fun parsePendingSwitch(raw: String): PendingSwitch? {
        val text = raw.trim()
        if (text.startsWith(SNAPSHOT_READY_PREFIX)) {
            val target = text.removePrefix(SNAPSHOT_READY_PREFIX).trim().toIntOrNull() ?: return null
            return if (target in 1..MAX_SLOTS) PendingSwitch(SwitchPhase.READY_TO_IMPORT, target) else null
        }
        if (text.startsWith(FRESH_TARGET_PREFIX)) {
            val parts = text.removePrefix(FRESH_TARGET_PREFIX).split(':')
            if (parts.size != 2) return null
            val ironman = when (parts[0].trim()) {
                "1" -> true
                "0" -> false
                else -> return null
            }
            val target = parts[1].trim().toIntOrNull() ?: return null
            return if (target in 1..MAX_SLOTS) PendingSwitch(SwitchPhase.FRESH_TARGET, target, ironman) else null
        }
        val target = text.toIntOrNull() ?: return null
        return if (target in 1..MAX_SLOTS) PendingSwitch(SwitchPhase.LEGACY, target) else null
    }

    /**
     * If a previous switch crashed mid-way the marker is still on disk. Recovery finishes or
     * rolls the switch back by phase: pointer already on the target → only the marker deletion
     * was left (re-importing would overwrite progress made since the switch);
     * [SwitchPhase.READY_TO_IMPORT] → import the file and move the pointer (issue #1839), or
     * reset fresh when the file is gone (ironman assumed false, the marker does not record it);
     * [SwitchPhase.FRESH_TARGET] → replay the idempotent fresh reset and move the pointer;
     * [SwitchPhase.LEGACY] → finish the half-done import from the file when live material
     * matches, snapshot-first (identity-guarded) then move the pointer when there is no file
     * and no live material progress, heal from the active snapshot when there is no file but
     * live material matches it with wiped sessions and empty live quests/patches (medio-wipeado), else roll back
     * to the still-live outgoing character. Returns true when a switch was completed.
     */
    suspend fun recoverInterruptedSwitch(): Boolean = withContext(Dispatchers.IO) {
        // P2 race: the marker read + parse must sit inside the mutex with the apply, so a
        // concurrent switchTo cannot slip between the read and the locked apply and get its
        // completed switch reverted by stale marker data. Lock is held across one small
        // marker-file read + string parse (bounded).
        switchMutex.withLock {
            val marker = pendingSwitchFile()
            if (!marker.exists()) return@withLock false
            val pending = try { parsePendingSwitch(marker.readText()) } catch (_: Exception) { null }
            if (pending == null) {
                deletePendingSwitchMarker()
                return@withLock false
            }
            val inFlight = pending
            switchInProgress = true
            try {
                if (globalStateRepo.getActiveSaveSlot() == inFlight.targetSlot) {
                    deletePendingSwitchMarker()
                    return@withLock false
                }
                val target = slotFile(inFlight.targetSlot)
                when {
                    inFlight.phase == SwitchPhase.FRESH_TARGET -> resetToFreshCharacter(inFlight.ironman)
                    inFlight.phase == SwitchPhase.READY_TO_IMPORT && target.exists() ->
                        importFullSave(target.readText(), freezeSessionTimers = false)
                    inFlight.phase == SwitchPhase.READY_TO_IMPORT ->
                        // The file is gone (slot deleted before recovery): end up fresh. ironman
                        // is assumed false because the ready marker does not record it.
                        resetToFreshCharacter(ironman = false)
                    // Legacy: matching live material means the non-atomic import had started
                    // (the player row commits before sessions/quests/patches), so the file is
                    // the source of truth for those sections and the import must finish.
                    roomAlreadyHoldsTargetSlot(inFlight.targetSlot) ->
                        importFullSave(target.readText(), freezeSessionTimers = false)
                    // No file and no live material: the reset already ran, so only the pointer —
                    // unless the live character carries non-default identity (setup done and/or
                    // a name) but was never snapshotted: preserve it at its home slot first, or
                    // the pointer move silently rehomes it with no file describing it. Fresh
                    // characters and existing origin files stay a pointer-only move.
                    !target.exists() && roomHoldsNoMaterialProgress() -> {
                        val origin = globalStateRepo.getActiveSaveSlot()
                        if (!slotFile(origin).exists() && roomHoldsNonDefaultIdentity()) snapshotCurrent(origin)
                        rescheduleAlarmsFromFlags()
                    }
                    // P4 medio-wipeado: LEGACY bare marker + crash mid-way through the OLD reset
                    // (sessions/quests/patches wiped, player row intact, no target file). The
                    // materiality rule above cannot see this state because the player row is
                    // intact, and the good snapshot already on disk would be overwritten by the
                    // next switchTo — heal from it instead, then complete normally. The heal
                    // additionally requires live quests/patches empty (an idle-but-intact character
                    // must never rewind) and snapshot content worth restoring. Anything
                    // unproven falls through to the rollback below; a corrupt snapshot clears
                    // the marker and rolls back (no loop: the marker is gone).
                    !target.exists() -> {
                        val origin = globalStateRepo.getActiveSaveSlot()
                        val snapshotText = medioWipeadoSnapshotText(origin)
                        if (snapshotText == null) {
                            deletePendingSwitchMarker()
                            return@withLock false
                        }
                        try {
                            importFullSave(snapshotText, freezeSessionTimers = false)
                        } catch (_: Exception) {
                            Log.w(TAG, "Medio-wipeado heal import failed; rolled back with the marker cleared")
                            deletePendingSwitchMarker()
                            return@withLock false
                        }
                    }
                    // Cannot prove the live material is the target's (it may not be, or the
                    // check may not have been evaluable): roll the switch back.
                    else -> {
                        deletePendingSwitchMarker()
                        return@withLock false
                    }
                }
                globalStateRepo.setActiveSaveSlot(inFlight.targetSlot)
                deletePendingSwitchMarker()
                _switchEvents.tryEmit(Unit)
                true
            } finally {
                switchInProgress = false
            }
        }
    }

    /** Deletes an INACTIVE slot's files permanently. The active slot cannot be deleted here. */
    suspend fun deleteSlot(slot: Int) = withContext(Dispatchers.IO) {
        switchMutex.withLock {
            if (slot == globalStateRepo.getActiveSaveSlot()) return@withLock
            slotFile(slot).delete()
            metaFile(slot).delete()
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private suspend fun snapshotCurrent(slot: Int) {
        writeAtomic(slotFile(slot), exportFullSave())
        val player = playerRepo.getOrCreatePlayer()
        val meta = SaveSlotMeta(
            flags        = player.flags,
            skillLevels  = player.skillLevels,
            coins        = player.coins,
            lastPlayedAt = System.currentTimeMillis(),
        )
        writeAtomic(metaFile(slot), json.encodeToString(json.serializersModule.serializer<SaveSlotMeta>(), meta))
    }

    /** Buff-expiry and periodic-backup alarms follow the character; re-arm them from live flags. */
    private suspend fun rescheduleAlarmsFromFlags() {
        val flags = playerRepo.getFlags()
        buffNotifScheduler.cancelXpBoostExpiry()
        buffNotifScheduler.cancelBlessingExpiry()
        if (flags.xpBoostExpiresAt > System.currentTimeMillis()) {
            buffNotifScheduler.scheduleXpBoostExpiry(flags.xpBoostExpiresAt)
        }
        if (flags.activeBlessingExpiresAt > System.currentTimeMillis()) {
            buffNotifScheduler.scheduleBlessingExpiry(flags.activeBlessingExpiresAt)
        }
        backupScheduler.schedule(flags.backupFrequency)
    }

    /** Wipes the live character to a fresh slot's state and re-arms alarms; functionally
     *  idempotent so recovery can replay it (it re-stamps the cosmetic characterCreatedAt). */
    private suspend fun resetToFreshCharacter(ironman: Boolean) {
        sessionRepo.deleteAllSessions()
        sessionRepo.deleteAllWorkerSessions()
        questRepo.resetAllProgress()
        farmingRepo.resetAllPatches()
        playerRepo.resetProgression(ironman = ironman)
        rescheduleAlarmsFromFlags()
    }

    /**
     * Whether the live character already is the one in [targetSlot]'s file, used to disambiguate
     * a legacy marker: older code imported the target before moving the pointer. Compares the six
     * material player fields, not flags: [importFullSave] runs the guild migration right after the
     * import (`:124`) and flags also mutate at startup (migrations, buffs, hp), so flags prove
     * nothing. skillXp/skillLevels compare as maps after folding the legacy "hp" key the way
     * getOrCreatePlayer migrates it; the other four compare verbatim. On a match the file is the
     * source of truth for sessions/quests/patches; identical characters match too and the file
     * wins, same as the base. Failures return false.
     */
    private suspend fun roomAlreadyHoldsTargetSlot(targetSlot: Int): Boolean {
        return try {
            val target = slotFile(targetSlot)
            if (!target.exists()) {
                false
            } else {
                val export: PlayerExport = json.decodeFromString(playerRepo.stripJsonGarbage(target.readText()))
                livePlayerMatchesExport(export)
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * The six material player fields of the live row versus a snapshot export, shared by the
     * legacy identity guard above and the P4 medio-wipeado probe below. All six compare
     * decoded (skillXp/skillLevels as maps after the legacy hp fold, inventory/equipped as
     * maps, pets as a decoded list, coins as a number) so key order/whitespace never
     * mismatches; flags stay excluded. Any undecodable field reads as no-match.
     */
    private suspend fun livePlayerMatchesExport(export: PlayerExport): Boolean {
        return try {
            val live = playerRepo.getOrCreatePlayer()
            if (live.coins != export.coins) return false
            val (fileXp, fileLevels) = legacyHpMerged(export.skillXp, export.skillLevels)
            if (json.decodeFromString<Map<String, Long>>(live.skillXp) != fileXp) return false
            if (json.decodeFromString<Map<String, Int>>(live.skillLevels) != fileLevels) return false
            if (json.decodeFromString<Map<String, Int>>(live.inventory) !=
                json.decodeFromString<Map<String, Int>>(export.inventory)
            ) return false
            if (json.decodeFromString<Map<String, String?>>(live.equipped) !=
                json.decodeFromString<Map<String, String?>>(export.equipped)
            ) return false
            if (json.decodeFromString<List<OwnedPet>>(live.pets) !=
                json.decodeFromString<List<OwnedPet>>(export.pets)
            ) return false
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * P4 medio-wipeado probe: a LEGACY bare marker plus a crash mid-way through the OLD reset
     * leaves sessions/quests/patches wiped with the player row intact and no target file — a
     * state the materiality rule cannot see. Returns the ACTIVE slot's snapshot text when
     * (cheapest-first, new checks last): the snapshot file exists; live player material matches
     * it (same six fields, legacy hp fold); live sessions read empty (player and both worker
     * slots); live quests/patches read empty (an idle-but-intact character must never rewind);
     * and the snapshot carries something to restore (sessions, quest progress, or patches with
     * non-null crops — clearAll-aware). Null otherwise; any failure reads as no-match.
     */
    private suspend fun medioWipeadoSnapshotText(origin: Int): String? {
        return try {
            val snapshot = slotFile(origin)
            if (!snapshot.exists()) return null
            val text = snapshot.readText()
            val export: PlayerExport = json.decodeFromString(playerRepo.stripJsonGarbage(text))
            if (!livePlayerMatchesExport(export)) return null
            if (sessionRepo.getActiveSession() != null) return null
            if (sessionRepo.getAllCompletedSessions().isNotEmpty()) return null
            for (slot in 1..2) {
                if (sessionRepo.getActiveWorkerSession(slot) != null) return null
                if (sessionRepo.getAllCompletedWorkerSessions(slot).isNotEmpty()) return null
            }
            if (playerRepo.hasQuestOrPatchContent()) return null
            if (export.sessions.isEmpty() && export.questProgress.isEmpty() &&
                export.farmingPatches.none { it.cropType != null }
            ) return null
            text
        } catch (_: Exception) {
            null
        }
    }

    /** The file's skill maps after the same legacy "hp" fold migrateHpKey applies live: "hp"
     *  merges into hitpoints and that level is recomputed, so raw-hp files still match. */
    private fun legacyHpMerged(skillXp: String, skillLevels: String): Pair<Map<String, Long>, Map<String, Int>> {
        val xp: MutableMap<String, Long> = json.decodeFromString(skillXp)
        val levels: MutableMap<String, Int> = json.decodeFromString(skillLevels)
        val hpXp = xp.remove("hp")
        if (hpXp != null) {
            levels.remove("hp")
            val merged = (xp[Skills.HITPOINTS] ?: 0L) + hpXp
            xp[Skills.HITPOINTS] = merged
            levels[Skills.HITPOINTS] = XpTable.levelForXp(merged)
        }
        return xp to levels
    }

    /**
     * True when the live character holds no material progress: what [resetToFreshCharacter]
     * leaves, or a brand-new character. Material is coins, any skill XP, any skill level off
     * the fresh default (1), non-blank non-default inventory/equipped, or any pet. Blank raw
     * inventory/equipped/pets and empty decoded maps (plus an empty pet list) read as empty,
     * so truly-fresh defaults stay pointer-only. Any undecodable field reads as material
     * (fail-closed to rollback). Used for a legacy marker with no target file.
     */
    private suspend fun roomHoldsNoMaterialProgress(): Boolean {
        return try {
        val live = playerRepo.getOrCreatePlayer()
        if (live.coins != 0L) return false
        if (!json.decodeFromString<Map<String, Long>>(live.skillXp).values.all { it == 0L }) return false
        if (json.decodeFromString<Map<String, Int>>(live.skillLevels).values.any { it != 1 }) return false
        if (live.inventory.isNotBlank()) {
            val inventory: Map<String, Int> = json.decodeFromString(live.inventory)
            val fresh = mapOf(
                "bronze_pickaxe" to 1,
                "bronze_axe" to 1,
                "bronze_fishing_rod" to 1,
                "bronze_boots" to 1,
            )
            if (inventory.isNotEmpty() && inventory != fresh) return false
        }
        if (live.equipped.isNotBlank()) {
            val equipped: Map<String, String?> = json.decodeFromString(live.equipped)
            val freshEquipped: Map<String, String?> = EquipSlot.ALL.associateWith { null } + mapOf(
                EquipSlot.PICKAXE to "bronze_pickaxe",
                EquipSlot.AXE to "bronze_axe",
                EquipSlot.FISHING_ROD to "bronze_fishing_rod",
                EquipSlot.BOOTS to "bronze_boots",
            )
            if (equipped.isNotEmpty() && equipped != freshEquipped) return false
        }
        if (live.pets.isNotBlank()) {
            if (json.decodeFromString<List<OwnedPet>>(live.pets).isNotEmpty()) return false
        }
        true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * True when the live flags carry non-default identity: the setup sheet ran or was dismissed
     * ([PlayerFlags.characterSetupDone]) or a name was entered ([PlayerFlags.characterName]).
     * Fresh characters keep the data-class defaults (setupDone=false, name=""), so they read
     * false here and stay on the pointer-only path with no file created. Undecodable flags also
     * read false: the snapshot fires only on positive identity evidence.
     */
    private suspend fun roomHoldsNonDefaultIdentity(): Boolean = try {
        val flags = decodeFlags(playerRepo.getOrCreatePlayer().flags)
        flags != null && (flags.characterSetupDone || flags.characterName.isNotBlank())
    } catch (_: Exception) {
        false
    }

    /** Clears the pending-switch marker, warning when a present marker cannot be removed. */
    private fun deletePendingSwitchMarker() {
        val marker = pendingSwitchFile()
        if (marker.exists() && !marker.delete()) {
            Log.w(TAG, "Could not delete pending switch marker; recovery will retry next launch")
        }
    }

    private fun readInactiveSlot(slot: Int): SlotInfo {
        val meta = metaFile(slot)
        if (meta.exists()) {
            try {
                val parsed: SaveSlotMeta = json.decodeFromString(meta.readText())
                return SlotInfo(
                    slot         = slot,
                    isActive     = false,
                    exists       = true,
                    flags        = decodeFlags(parsed.flags),
                    skillLevels  = decodeLevels(parsed.skillLevels),
                    coins        = parsed.coins,
                    lastPlayedAt = parsed.lastPlayedAt,
                )
            } catch (_: Exception) {
                // Fall through to the full save file below.
            }
        }
        val save = slotFile(slot)
        if (save.exists()) {
            try {
                val export: PlayerExport = json.decodeFromString(save.readText())
                return SlotInfo(
                    slot         = slot,
                    isActive     = false,
                    exists       = true,
                    flags        = decodeFlags(export.flags),
                    skillLevels  = decodeLevels(export.skillLevels),
                    coins        = export.coins,
                    lastPlayedAt = export.exportedAt,
                )
            } catch (_: Exception) {
                // Corrupt slot file: surface it as empty rather than crashing the picker.
            }
        }
        return SlotInfo(slot = slot, isActive = false, exists = false)
    }

    private fun decodeFlags(raw: String): PlayerFlags? =
        try { json.decodeFromString<PlayerFlags>(raw) } catch (_: Exception) { null }

    private fun decodeLevels(raw: String): Map<String, Int> =
        try { json.decodeFromString(raw) } catch (_: Exception) { emptyMap() }

    private fun writeAtomic(target: File, content: String) {
        slotsDir.mkdirs()
        val tmp = File(slotsDir, "${target.name}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            // renameTo can fail across some filesystems; fall back to a direct write.
            target.writeText(content)
            tmp.delete()
        }
    }

    companion object {
        const val MAX_SLOTS = 3

        /** Marker prefix for a crashed import of an existing target slot. */
        private const val SNAPSHOT_READY_PREFIX = "ready:"

        /** Marker prefix for a crashed reset of a new target slot, followed by "<ironman 0|1>:". */
        private const val FRESH_TARGET_PREFIX = "fresh:"

        private const val TAG = "SaveSlotRepository"
    }
}
