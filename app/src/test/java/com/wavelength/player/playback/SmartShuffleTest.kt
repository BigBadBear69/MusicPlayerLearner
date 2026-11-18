package com.wavelength.player.playback

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SmartShuffleTest {

    private fun artists(n: Int, distinct: Int) = List(n) { "artist${it % distinct}" }

    @Test
    fun orderIsAPermutationStartingWithCurrentSong() {
        repeat(50) { seed ->
            val order = SmartShuffle.buildOrder(artists(40, 7), first = 13, random = Random(seed))
            assertEquals(13, order.first())
            assertEquals((0 until 40).toSet(), order.toSet())
            assertEquals(40, order.size)
        }
    }

    @Test
    fun sameArtistIsNeverBackToBackWhenAvoidable() {
        repeat(50) { seed ->
            val a = artists(30, 6)
            val order = SmartShuffle.buildOrder(a, first = 0, random = Random(seed))
            // With 6 evenly spread artists the greedy pass may only get stuck at the very end.
            val clashes = order.toList().zipWithNext().count { (x, y) -> a[x] == a[y] }
            assertTrue("seed $seed had $clashes clashes", clashes <= 1)
        }
    }

    @Test
    fun loopingRoundsRespectTheRepeatWindow() {
        val n = 20
        val a = artists(n, 20)
        val random = Random(7)
        val history = ArrayList<Int>()
        var order = SmartShuffle.buildOrder(a, first = 0, random = random)
        repeat(30) {
            history += order.toList().dropLast(1) // last song stays "current" and starts the next order
            val ago = IntArray(n) { -1 }
            order.forEachIndexed { pos, idx -> ago[idx] = order.size - 1 - pos }
            order = SmartShuffle.buildOrder(a, first = order.last(), lastPlayedAgo = ago, random = random)
        }
        // Minimum distance between two plays of the same song must be at least 70% of the playlist.
        val lastSeen = HashMap<Int, Int>()
        var minGap = Int.MAX_VALUE
        history.forEachIndexed { pos, song ->
            lastSeen[song]?.let { minGap = minOf(minGap, pos - it) }
            lastSeen[song] = pos
        }
        assertTrue("min gap $minGap", minGap >= 14)
    }

    @Test
    fun weightsFavourHeavierSongsEarly() {
        val a = artists(10, 10)
        var heavyFirstHalf = 0
        repeat(500) { seed ->
            val order = SmartShuffle.buildOrder(a, first = null, weight = { if (it == 3) 20.0 else 1.0 }, random = Random(seed))
            if (order.indexOf(3) < 5) heavyFirstHalf++
        }
        assertTrue("heavy song early $heavyFirstHalf/500", heavyFirstHalf > 400)
    }

    @Test
    fun shuffleOrderPlacesPlayNextAndAddToQueueCorrectly() {
        // Queue of 5, play order 2,0,4,1,3; the current song is 0.
        val order = ListShuffleOrder(intArrayOf(2, 0, 4, 1, 3))
        // "Play next": insert 1 item at list index 1 (right after current index 0).
        val playNext = order.cloneAndInsert(1, 1) as ListShuffleOrder
        assertEquals(listOf(3, 0, 1, 5, 2, 4), playNext.order.toList())
        assertEquals(1, playNext.getNextIndex(0))
        // "Add to queue": append 2 items at the end of the list.
        val appended = order.cloneAndInsert(5, 2) as ListShuffleOrder
        assertEquals(listOf(2, 0, 4, 1, 3, 5, 6), appended.order.toList())
        assertEquals(6, appended.lastIndex)
        // Remove list index 4 (song played third).
        val removed = order.cloneAndRemove(4, 5) as ListShuffleOrder
        assertEquals(listOf(2, 0, 1, 3), removed.order.toList())
        assertEquals(C.INDEX_UNSET, ListShuffleOrder(IntArray(0)).firstIndex)
    }

    @Test
    fun moveKeepsPlayOrder() {
        val order = ListShuffleOrder(intArrayOf(2, 0, 4, 1, 3))
        // Move list item 0 to list position 3: list becomes [1,2,3,0,4] (old indices).
        val moved = order.cloneAndMove(0, 1, 3) as ListShuffleOrder
        // Old play order 2,0,4,1,3 → new indices: old2→1, old0→3, old4→4, old1→0, old3→2.
        assertEquals(listOf(1, 3, 4, 0, 2), moved.order.toList())
    }
}
