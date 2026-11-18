package com.wavelength.player.playback

import kotlin.math.ceil
import kotlin.random.Random

/**
 * Builds shuffle orders that feel random but avoid the annoyances of pure random:
 *  - a song can't come back until [repeatWindow] of the queue has played since it last played
 *    (this matters when a looping playlist starts a new round),
 *  - songs by the same artist aren't played back to back when there's any alternative,
 *  - optional per-song weights let favourites come earlier (used by the learning shuffle styles later).
 */
object SmartShuffle {
    const val DEFAULT_REPEAT_WINDOW = 0.7

    /**
     * @param artists artist of each queue item (index = queue index).
     * @param first queue index that must come first (the song playing now), or null.
     * @param lastPlayedAgo for each queue index, how many songs ago it last played (0 = it is playing now),
     *   or null if it hasn't played recently.
     * @return queue indices in play order: a permutation of `artists.indices`.
     */
    fun buildOrder(
        artists: List<String>,
        first: Int?,
        lastPlayedAgo: IntArray? = null,
        repeatWindow: Double = DEFAULT_REPEAT_WINDOW,
        weight: (Int) -> Double = { 1.0 },
        random: Random = Random.Default,
    ): IntArray {
        val n = artists.size
        if (n == 0) return IntArray(0)
        val window = ceil(repeatWindow * n).toInt().coerceIn(1, n)
        val order = ArrayList<Int>(n)
        val remaining = (0 until n).toMutableList()

        if (first != null && first in 0 until n) {
            order += first
            remaining.remove(first)
        }

        while (remaining.isNotEmpty()) {
            val step = order.size // position this pick will take (0 = the song playing now)
            // Songs between this candidate's last play and this one, if it were picked now.
            fun gap(i: Int): Int = lastPlayedAgo?.getOrNull(i)?.takeIf { it >= 0 }?.let { it + step } ?: Int.MAX_VALUE
            val prevArtist = order.lastOrNull()?.let { artists[it] }

            val rested = remaining.filter { gap(it) >= window }
            val pool = rested.ifEmpty {
                // Nothing has rested long enough (tiny playlist): take the songs that rested longest.
                val best = remaining.maxOf { gap(it) }
                remaining.filter { gap(it) == best }
            }
            val varied = pool.filter { artists[it] != prevArtist }.ifEmpty { pool }

            val pick = weightedPick(varied, weight, random)
            order += pick
            remaining.remove(pick)
        }
        return order.toIntArray()
    }

    private fun weightedPick(candidates: List<Int>, weight: (Int) -> Double, random: Random): Int {
        val weights = candidates.map { weight(it).coerceAtLeast(0.0001) }
        var r = random.nextDouble() * weights.sum()
        candidates.forEachIndexed { k, c ->
            r -= weights[k]
            if (r <= 0) return c
        }
        return candidates.last()
    }
}
