package com.wavelength.player.ui

fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun songCount(n: Int) = if (n == 1) "1 song" else "$n songs"

/** "3 h 12 min", "45 min", "40 s". */
fun formatListenTime(ms: Long): String {
    val totalMin = ms / 60_000
    return when {
        ms < 60_000 -> "${ms / 1000} s"
        totalMin < 60 -> "$totalMin min"
        totalMin < 60 * 100 -> "${totalMin / 60} h ${totalMin % 60} min"
        else -> "%,d h".format(totalMin / 60)
    }
}

fun percent(f: Float) = "${(f * 100).toInt()}%"

/** "Today 14:05", "Yesterday 09:12", "Mar 3, 22:40". */
fun formatWhen(epochMs: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val t = java.time.Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    val time = "%02d:%02d".format(t.hour, t.minute)
    return when (t.toLocalDate()) {
        today -> "Today $time"
        today.minusDays(1) -> "Yesterday $time"
        else -> t.format(java.time.format.DateTimeFormatter.ofPattern(if (t.year == today.year) "MMM d, HH:mm" else "MMM d yyyy, HH:mm"))
    }
}

fun formatDate(epochMs: Long): String =
    java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"))
