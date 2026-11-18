package com.wavelength.player.data.stats

import com.wavelength.player.data.db.Outcome
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.tanh

/** The rules that turn "how long did you listen" into a play, a skip or a partial listen. */
object ListeningRules {
    const val SKIP_BEFORE_MS = 30_000L
    const val PLAY_AFTER_MS = 4 * 60_000L
    const val PLAY_AFTER_FRACTION = 0.5
    /** Under a second isn't a listen at all (e.g. flicking past while the queue loads). */
    const val IGNORE_BELOW_MS = 1_000L

    /** Returns the outcome, or null if the listen was too short to count. */
    fun classify(listenedMs: Long, durationMs: Long, endedNaturally: Boolean): Int? {
        val playThreshold = if (durationMs > 0) min((durationMs * PLAY_AFTER_FRACTION).toLong(), PLAY_AFTER_MS) else PLAY_AFTER_MS
        return when {
            listenedMs >= playThreshold -> Outcome.PLAYED
            listenedMs < IGNORE_BELOW_MS -> null
            listenedMs < SKIP_BEFORE_MS && !endedNaturally -> Outcome.SKIPPED
            else -> Outcome.PARTIAL
        }
    }
}

/**
 * Affinity: how much you like a song right now, learned from what you do.
 *
 * Every listen nudges a song's raw score up or down; older nudges fade with a half-life of
 * [HALF_LIFE_DAYS] days, so the score follows your taste as it changes. Favourites get a steady boost.
 * [score] maps it to 0–100 where 50 is neutral.
 */
object Affinity {
    const val HALF_LIFE_DAYS = 45.0
    private const val DAY_MS = 86_400_000.0
    const val FAVORITE_BONUS = 2.5

    fun delta(outcome: Int, listenedMs: Long, durationMs: Long, replay: Boolean): Double {
        val completion = if (durationMs > 0) (listenedMs.toDouble() / durationMs).coerceIn(0.0, 1.0) else 0.0
        return when (outcome) {
            Outcome.PLAYED -> 1.0 + if (replay) 0.5 else 0.0
            Outcome.SKIPPED -> if (listenedMs < 10_000) -1.0 else -0.6
            else -> (completion - 0.35) * 1.5 // stopped early: mildly negative, mid-way: mildly positive
        }
    }

    fun decay(raw: Double, fromMs: Long, toMs: Long): Double {
        if (fromMs <= 0 || toMs <= fromMs) return raw
        val days = (toMs - fromMs) / DAY_MS
        return raw * exp(-ln(2.0) * days / HALF_LIFE_DAYS)
    }

    /** Raw score after adding one listen at [atMs]. */
    fun update(raw: Double, updatedAt: Long, atMs: Long, delta: Double) = decay(raw, updatedAt, atMs) + delta

    /** 0–100 score at [nowMs]. */
    fun score(raw: Double, updatedAt: Long, favorite: Boolean, nowMs: Long): Int {
        val value = decay(raw, updatedAt, nowMs) + if (favorite) FAVORITE_BONUS else 0.0
        return (50 + 50 * tanh(value / 5.0)).toInt().coerceIn(0, 100)
    }
}
