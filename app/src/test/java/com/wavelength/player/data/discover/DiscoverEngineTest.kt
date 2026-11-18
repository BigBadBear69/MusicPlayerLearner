package com.wavelength.player.data.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverEngineTest {

    @Test
    fun normalizeMatchesCommonVariants() {
        assertEquals(Normalize.key("The Killers", "Mr. Brightside"), Normalize.key("Killers", "Mr Brightside - 2004 Remaster"))
        assertEquals(Normalize.key("Daft Punk feat. Pharrell", "Get Lucky (Radio Edit)"), Normalize.key("Daft Punk", "Get Lucky"))
        assertEquals(Normalize.title("Café del Mar"), Normalize.title("Cafe Del Mar"))
    }

    private fun input(
        similar: List<List<SimilarTrack>>,
        library: Set<String> = emptySet(),
        taste: Map<String, Double> = emptyMap(),
        disliked: Set<String> = emptySet(),
        dislikedArtists: Map<String, Int> = emptyMap(),
    ) = DiscoverEngine.Input(
        seeds = listOf(Seed("Seed A", "One", 1.0), Seed("Seed B", "Two", 0.5)),
        similarTracks = similar,
        artistRoutes = emptyList(),
        libraryTrackKeys = library,
        libraryArtistTaste = taste,
        dislikedKeys = disliked,
        dislikedArtists = dislikedArtists,
    )

    @Test
    fun songsSuggestedBySeveralSeedsRankHigher() {
        val r = DiscoverEngine.rank(
            input(
                listOf(
                    listOf(SimilarTrack("X", "Shared", 0.5), SimilarTrack("Y", "Only A", 0.6)),
                    listOf(SimilarTrack("X", "Shared", 0.5)),
                ),
            ),
            "test",
        )
        assertEquals("Shared", r.all.maxBy { it.score }.title)
        assertEquals(2, r.all.first { it.title == "Shared" }.seedCount)
    }

    @Test
    fun ownedAndDislikedSongsAreRemovedAndSkippedArtistsDemoted() {
        val owned = Normalize.key("X", "Owned")
        val nope = Normalize.key("Y", "Nope")
        val r = DiscoverEngine.rank(
            input(
                listOf(
                    listOf(
                        SimilarTrack("X", "Owned", 0.9), SimilarTrack("Y", "Nope", 0.9),
                        SimilarTrack("Skippy", "Song", 0.8), SimilarTrack("Fresh", "Song", 0.5),
                    ),
                    emptyList(),
                ),
                library = setOf(owned),
                taste = mapOf("x" to 0.5, "skippy" to -1.0),
                disliked = setOf(nope),
            ),
            "test",
        )
        assertFalse(owned in r.all.map { it.key })
        assertFalse(nope in r.all.map { it.key })
        // Skippy had the higher raw match but you always skip that artist.
        assertTrue(r.all.first { it.artist == "Fresh" }.score > r.all.first { it.artist == "Skippy" }.score)
        assertTrue(r.all.first { it.artist == "Fresh" }.newArtist)
        assertFalse(r.all.first { it.artist == "Skippy" }.newArtist)
    }

    @Test
    fun atMostThreeSongsPerArtist() {
        val many = List(10) { SimilarTrack("Same", "Song $it", 0.9 - it * 0.01) }
        val r = DiscoverEngine.rank(input(listOf(many, emptyList())), "test")
        assertEquals(3, r.all.count { it.artist == "Same" })
    }
}
