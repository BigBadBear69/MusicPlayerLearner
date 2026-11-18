package com.wavelength.player.data.discover

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Talks to the real Deezer / Last.fm APIs. Only runs with -Plive (needs internet). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveApiTest {
    private fun http() = JsonHttp({ null }, { _, _ -> }, 150)

    @Test
    fun deezerFallbackProducesRecommendations() = runBlocking {
        assumeTrue(System.getProperty("live") != null)
        val dz = DeezerApi(http())
        val preview = dz.findTrack("Daft Punk", "One More Time")
        assertNotNull(preview?.previewUrl)
        println("preview: ${preview?.title} ${preview?.coverUrl}")

        val routes = ArrayList<DiscoverEngine.ArtistRoute>()
        for ((artist, w) in listOf("Daft Punk" to 1.0, "Tame Impala" to 0.8)) {
            val id = dz.artistId(artist)!!
            dz.relatedArtists(id, 8).take(4).forEach { a ->
                val aid = dz.artistId(a.name) ?: return@forEach
                routes += DiscoverEngine.ArtistRoute(artist, w, a.name, a.match, dz.topTracks(aid, 3))
            }
        }
        val result = DiscoverEngine.rank(
            DiscoverEngine.Input(
                seeds = listOf(Seed("Daft Punk", "One More Time", 1.0), Seed("Tame Impala", "The Less I Know The Better", 0.8), Seed("Daft Punk", "Digital Love", 0.9)),
                similarTracks = listOf(emptyList(), emptyList(), emptyList()),
                artistRoutes = routes,
                libraryTrackKeys = emptySet(),
                libraryArtistTaste = mapOf("daft punk" to 0.8, "tame impala" to 0.6),
                dislikedKeys = emptySet(),
                dislikedArtists = emptyMap(),
            ),
            "Deezer",
        )
        result.newArtists.take(8).forEach { println("NEW  ${it.artist} - ${it.title}  (${"%.2f".format(it.score)}, because ${it.becauseOf})") }
        result.becauseYouLike.forEach { (a, l) -> println("BECAUSE $a: " + l.joinToString { "${it.artist} - ${it.title}" }) }
        assertTrue(result.all.size >= 5)
    }

    @Test
    fun lastFmRejectsBadKeyClearly() = runBlocking {
        assumeTrue(System.getProperty("live") != null)
        val e = runCatching { LastFmApi(http(), "0123456789abcdef0123456789abcdef").check() }.exceptionOrNull()
        println("lastfm bad key -> $e")
        assertTrue(e is InvalidApiKeyException)
    }
}
