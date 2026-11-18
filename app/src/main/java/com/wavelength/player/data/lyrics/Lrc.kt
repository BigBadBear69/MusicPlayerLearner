package com.wavelength.player.data.lyrics

/** One lyrics line with its start time (ms), for synced lyrics. */
data class LyricLine(val timeMs: Long, val text: String)

sealed interface Lyrics {
    val source: String
    data class Synced(val lines: List<LyricLine>, override val source: String) : Lyrics
    data class Plain(val text: String, override val source: String) : Lyrics
}

/** Parses LRC: "[01:23.45] text", several timestamps per line, and an optional [offset:+/-ms] tag. */
object Lrc {
    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    fun looksSynced(text: String) = stamp.containsMatchIn(text)

    fun parse(text: String): List<LyricLine> {
        val offset = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = ArrayList<LyricLine>()
        text.lineSequence().forEach { raw ->
            val stamps = stamp.findAll(raw).toList()
            if (stamps.isEmpty()) return@forEach
            val words = raw.substring(stamps.last().range.last + 1).trim()
            stamps.forEach { m ->
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                lines += LyricLine((min * 60_000 + sec * 1000 + ms - offset).coerceAtLeast(0), words)
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /** Index of the line being sung at [positionMs], or -1 before the first line. */
    fun currentIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.lastIndex
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs <= positionMs) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }

    fun from(text: String?, source: String): Lyrics? {
        val t = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (looksSynced(t)) {
            val lines = parse(t)
            if (lines.size >= 3) return Lyrics.Synced(lines, source)
        }
        return Lyrics.Plain(t, source)
    }
}
