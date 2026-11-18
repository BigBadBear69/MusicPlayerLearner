package com.wavelength.player.data.discover

import kotlin.math.sqrt

/** A song you like, used as a starting point. [weight] 0..1 says how much you like it. */
data class Seed(val artist: String, val title: String, val weight: Double)

data class Recommendation(
    val key: String,
    val artist: String,
    val title: String,
    val score: Double,
    /** The seed artist that contributed most, for "Because you like …". */
    val becauseOf: String,
    /** How many of your songs point to this one. */
    val seedCount: Int,
    /** Artist isn't in your library at all. */
    val newArtist: Boolean,
)

data class DiscoverResult(
    val topPicks: List<Recommendation>,
    val becauseYouLike: List<Pair<String, List<Recommendation>>>,
    val newArtists: List<Recommendation>,
    val seedsUsed: Int,
    val source: String,
    val createdAt: Long,
) {
    val all: List<Recommendation> get() = (topPicks + becauseYouLike.flatMap { it.second } + newArtists).distinctBy { it.key }
    val isEmpty get() = all.isEmpty()
}

/**
 * Turns "songs similar to each of your favourites" into one ranked list, shaped by your own data:
 *  - a song recommended because of several of your favourites ranks higher,
 *  - the more you like a seed song, the more its suggestions count,
 *  - songs you already have, or gave a thumbs-down, are removed,
 *  - artists you tend to skip are pushed down, artists you love are nudged up,
 *  - no more than 3 songs per artist, so the list stays varied.
 */
object DiscoverEngine {

    class Input(
        val seeds: List<Seed>,
        /** Similar tracks for each seed (same order as [seeds]). */
        val similarTracks: List<List<SimilarTrack>>,
        /** Similar artists for seed artists, with a few top tracks each: (seedArtist, similarArtist, match, topTracks). */
        val artistRoutes: List<ArtistRoute>,
        val libraryTrackKeys: Set<String>,
        /** Normalised artist name → how much you like that artist, -1 (always skipped) .. +1 (loved). */
        val libraryArtistTaste: Map<String, Double>,
        val dislikedKeys: Set<String>,
        /** Normalised artist → number of thumbs-down given to their songs in Discover. */
        val dislikedArtists: Map<String, Int>,
        /** Recommendation key → small multiplier from how you reacted to its preview. */
        val previewReaction: Map<String, Double> = emptyMap(),
    )

    data class ArtistRoute(val seedArtist: String, val seedWeight: Double, val artist: String, val match: Double, val topTracks: List<String>)

    private class Acc(val artist: String, val title: String) {
        var score = 0.0
        val bySeedArtist = HashMap<String, Double>()
        val seeds = HashSet<String>()
        var viaArtistRoute = false
    }

    fun rank(input: Input, source: String, now: Long = System.currentTimeMillis()): DiscoverResult {
        val seedKeys = input.seeds.mapTo(HashSet()) { Normalize.key(it.artist, it.title) }
        val acc = LinkedHashMap<String, Acc>()

        fun add(artist: String, title: String, amount: Double, seedArtist: String, seedKey: String, viaArtist: Boolean) {
            if (amount <= 0) return
            val key = Normalize.key(artist, title)
            if (key in seedKeys || key in input.libraryTrackKeys || key in input.dislikedKeys) return
            val a = acc.getOrPut(key) { Acc(artist, title) }
            a.score += amount
            a.bySeedArtist.merge(seedArtist, amount, Double::plus)
            a.seeds += seedKey
            a.viaArtistRoute = a.viaArtistRoute || viaArtist
        }

        input.seeds.forEachIndexed { i, seed ->
            val seedKey = Normalize.key(seed.artist, seed.title)
            input.similarTracks.getOrNull(i).orEmpty().forEach { t ->
                add(t.artist, t.title, seed.weight * t.match.coerceIn(0.0, 1.0), seed.artist, seedKey, viaArtist = false)
            }
        }
        input.artistRoutes.forEach { r ->
            r.topTracks.forEachIndexed { rank, title ->
                // Top tracks of a similar artist: the artist's best songs count most.
                val amount = r.seedWeight * r.match.coerceIn(0.0, 1.0) * 0.6 / sqrt(rank + 1.0)
                add(r.artist, title, amount, r.seedArtist, "artist:" + Normalize.artist(r.seedArtist), viaArtist = true)
            }
        }

        val scored = acc.map { (key, a) ->
            val artistNorm = Normalize.artist(a.artist)
            var s = a.score * (1 + 0.2 * (a.seeds.size - 1)) // several of your songs agree
            input.libraryArtistTaste[artistNorm]?.let { taste -> s *= 1 + 0.5 * taste.coerceIn(-1.0, 1.0) }
            input.dislikedArtists[artistNorm]?.let { n -> if (n >= 2) s *= 0.3 else if (n == 1) s *= 0.7 }
            input.previewReaction[key]?.let { s *= it }
            Recommendation(
                key = key, artist = a.artist, title = a.title, score = s,
                becauseOf = a.bySeedArtist.maxBy { it.value }.key,
                seedCount = a.seeds.size,
                newArtist = artistNorm !in input.libraryArtistTaste,
            )
        }.filter { it.score > 0.01 }.sortedByDescending { it.score }

        // Variety: at most 3 songs per artist overall.
        val perArtist = HashMap<String, Int>()
        val varied = scored.filter { r -> perArtist.merge(Normalize.artist(r.artist), 1, Int::plus)!! <= 3 }

        // Sections. Each song appears once; "new artists" shows each artist's best song only.
        val used = HashSet<String>()
        val newArtists = varied.filter { it.newArtist }.distinctBy { Normalize.artist(it.artist) }.take(12)
        used += newArtists.map { it.key }

        val topSeedArtists = input.seeds.groupBy { it.artist }.mapValues { (_, s) -> s.sumOf { it.weight } }
            .entries.sortedByDescending { it.value }.map { it.key }.take(4)
        val because = topSeedArtists.mapNotNull { seedArtist ->
            val list = varied.filter { it.key !in used && it.becauseOf == seedArtist }.take(8)
            used += list.map { it.key }
            if (list.size >= 3) seedArtist to list else { used -= list.map { it.key }.toSet(); null }
        }
        val top = varied.filter { it.key !in used }.take(25)

        return DiscoverResult(top, because, newArtists, input.seeds.size, source, now)
    }
}
