package com.fantasyidler.util

/** Format a raw XP long as a readable string (e.g. 1,234,567 → "1.2M"). */
fun Long.formatXp(): String = when {
    this >= 1_000_000L -> "%.1fM".format(this / 1_000_000.0)
    this >= 1_000L     -> "%,d".format(this)
    else               -> toString()
}

/** Format a coin amount with thousands separators. */
fun Long.formatCoins(): String = when {
    this >= 1_000_000L -> "%.1fM".format(this / 1_000_000.0)
    this >= 1_000L     -> "%,d".format(this)
    else               -> toString()
}

/**
 * Convert an epoch-ms "ends_at" timestamp to a human-readable countdown string.
 * e.g. "42m 10s" or "Complete"
 */
fun Long.toCountdown(): String {
    val remaining = this - System.currentTimeMillis()
    if (remaining <= 0) return "Complete"
    val totalSeconds = remaining / 1_000
    val hours   = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0   -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else        -> "${seconds}s"
    }
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

/** Clamp an Int to the valid skill level range [1, 99]. */
fun Int.clampLevel(): Int = coerceIn(1, 99)
