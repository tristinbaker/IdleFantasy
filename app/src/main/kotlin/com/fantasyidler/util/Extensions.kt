package com.fantasyidler.util

import com.fantasyidler.data.model.SessionFrame
import com.fantasyidler.data.model.SkillSession
import kotlinx.serialization.json.Json

/** Format a raw XP long as a readable string (e.g. 1,234,567 → "1.2M"). */
fun Long.formatXp(): String = when {
    this >= 1_000_000L -> "%.1fM".format(this / 1_000_000.0)
    this >= 1_000L     -> "%,d".format(this)
    else               -> toString()
}

/** Parenthetical multiplier breakdown for a flat XP grant, e.g. "(50,000 × 2 × 1.28)", or null if no bonus applied. */
fun xpMultiplierBreakdown(baseXp: Long, boostActive: Boolean, blessingMult: Float, prestigeLevel: Int = 0): String? {
    if (!boostActive && blessingMult <= 1f && prestigeLevel <= 0) return null
    val factors = buildList {
        if (boostActive) add("2")
        if (blessingMult > 1f) add("%.2f".format(blessingMult).trimEnd('0').trimEnd('.'))
        if (prestigeLevel > 0) add("%.2f".format(1.0 + prestigeLevel * 0.10).trimEnd('0').trimEnd('.'))
    }
    return "(${baseXp.formatXp()} × ${factors.joinToString(" × ")})"
}

/** Format a coin amount with thousands separators. */
fun Long.formatCoins(): String = when {
    this >= 1_000_000L -> "%.1fM".format(this / 1_000_000.0)
    this >= 1_000L     -> "%,d".format(this)
    else               -> toString()
}

/** Abbreviated coin format for compact UI (e.g. 50000 → "50k"). */
fun Long.formatCoinsBrief(): String = when {
    this >= 1_000_000L -> "%.1fM".format(this / 1_000_000.0)
    this >= 1_000L     -> "${this / 1000}k"
    else               -> toString()
}

/** Formats an epoch-ms timestamp as a clock time, respecting the device's 12/24-hour preference. */
fun Long.toClockTime(context: android.content.Context): String =
    android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(this))

/** Formats the game's fixed 6 AM local daily reset time as a clock string, respecting the device's 12/24-hour preference. */
fun dailyResetClockTime(context: android.content.Context): String {
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 6)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
    }
    return cal.timeInMillis.toClockTime(context)
}

/**
 * Convert an epoch-ms "ends_at" timestamp to a human-readable countdown string, optionally
 * with the completion clock time, e.g. "42m 10s (1:45 PM)" or "42m 10s" or "Complete"
 */
fun Long.toCountdown(context: android.content.Context, showEndTime: Boolean = true): String {
    val remaining = this - System.currentTimeMillis()
    if (remaining <= 0) return "Complete"
    val totalSeconds = remaining / 1_000
    val hours   = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val duration = when {
        hours > 0   -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else        -> "${seconds}s"
    }
    return if (showEndTime) "$duration (${toClockTime(context)})" else duration
}

/**
 * Convert an epoch-ms "started_at" timestamp to a relative time string.
 * e.g. "5m ago", "1h 2m ago"
 */
fun Long.toRelativeTime(): String {
    val elapsedMs = System.currentTimeMillis() - this
    if (elapsedMs < 60_000) return "Just now"
    val minutes = elapsedMs / 60_000
    return if (minutes < 60) {
        "${minutes}m ago"
    } else {
        val h = minutes / 60
        val m = minutes % 60
        if (m == 0L) "${h}h ago" else "${h}h ${m}m ago"
    }
}

/** Format a raw millisecond duration (not an epoch) as a human-readable string, e.g. "2h 30m" or "45m". */
fun Long.formatDurationMs(): String {
    val totalSeconds = this / 1_000
    val hours   = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0                -> "${hours}h"
        minutes > 0              -> "${minutes}m"
        else                     -> "${totalSeconds}s"
    }
}

/**
 * Crafting-type sessions (mainly hired-worker batch jobs) are pre-simulated as a single frame
 * that already holds the whole job's assigned total, however long the job runs. Gathering/combat
 * sessions use many real per-minute frames instead, so this only returns something for that
 * single-frame batch case. Most of these batch types record the total in [SessionFrame.items]
 * (e.g. bars, potions, runes); Prayer's only records a bone-burial count in [SessionFrame.kills],
 * so that's used as a fallback, keyed by this session's own activity (the bone type).
 */
fun SkillSession.singleBatchItems(json: Json): Map<String, Int> = try {
    val decodedFrames: List<SessionFrame> = json.decodeFromString(frames)
    val frame = decodedFrames.singleOrNull()
    when {
        frame == null            -> emptyMap()
        frame.items.isNotEmpty() -> frame.items
        frame.kills > 0          -> mapOf(activityKey to frame.kills)
        else                      -> emptyMap()
    }
} catch (_: Exception) {
    emptyMap()
}

/** Clamp an Int to the valid skill level range [1, 99]. */
fun Int.clampLevel(): Int = coerceIn(1, 99)

/** Short display label for a skill key, used in compact bonus rows. */
fun String.toSkillAbbrev(): String = when (this) {
    "attack"       -> "Atk"
    "strength"     -> "Str"
    "defense"      -> "Def"
    "ranged"       -> "Rng"
    "magic"        -> "Mag"
    "hitpoints"    -> "HP"
    "prayer"       -> "Pry"
    "mining"       -> "Min"
    "woodcutting"  -> "WC"
    "fishing"      -> "Fish"
    "firemaking"   -> "FM"
    "cooking"      -> "Cook"
    "smithing"     -> "Smith"
    "crafting"     -> "Craft"
    "fletching"    -> "Fletch"
    "agility"      -> "Agil"
    "runecrafting" -> "RC"
    "farming"      -> "Farm"
    else           -> take(4).replaceFirstChar { it.uppercase() }
}
