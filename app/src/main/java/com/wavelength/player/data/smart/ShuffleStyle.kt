package com.wavelength.player.data.smart

import com.wavelength.player.data.db.SongStatsEntity
import com.wavelength.player.data.stats.Affinity
import kotlin.math.exp
import kotlin.math.pow

/**
 * How shuffle picks the next song. All styles keep the no-repeat window and artist spacing;
 * they differ in which songs tend to come up sooner.
 */
enum class ShuffleStyle(val label: String, val description: String) {
    Random("Random", "Every song has the same chance"),
    Smart("Smart", "Songs you love come up more, songs you skip come up less"),
    Discovery("Discovery", "Songs you rarely play come up more");

    /** Relative chance of a song being picked next (1.0 = normal). */
    fun weight(stats: SongStatsEntity?, now: Long): Double = when (this) {
        Random -> 1.0
        Smart -> {
            val score = stats?.takeIf { it.listens > 0 || it.favorite }
                ?.let { Affinity.score(it.affinityRaw, it.affinityUpdatedAt, it.favorite, now) } ?: 50
            exp((score - 50) / 20.0) // 80 → ×4.5, 50 → ×1, 20 → ×0.2
        }
        Discovery -> {
            val listens = stats?.listens ?: 0
            val skippy = stats != null && listens >= 3 && stats.skips.toFloat() / listens > 0.6f
            3.0 / (1.0 + listens).pow(0.8) * if (skippy) 0.3 else 1.0
        }
    }

    companion object {
        fun parse(name: String?) = entries.firstOrNull { it.name == name } ?: Smart
    }
}
