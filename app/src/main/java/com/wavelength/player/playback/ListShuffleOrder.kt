package com.wavelength.player.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder

/**
 * A shuffle order whose sequence Wavelength decides (see [SmartShuffle]) rather than ExoPlayer.
 *
 * [shuffled] lists queue indices in play order. Items inserted later are placed predictably:
 * appended items go to the end ("Add to queue"), and items inserted in the middle of the list go right
 * after the item before them ("Play next" inserts after the current song, so they play next).
 */
@UnstableApi
class ListShuffleOrder(private val shuffled: IntArray) : ShuffleOrder {
    private val positionOf = IntArray(shuffled.size).also { pos ->
        shuffled.forEachIndexed { p, index -> pos[index] = p }
    }

    val order: IntArray get() = shuffled.copyOf()

    override fun getLength() = shuffled.size

    override fun getNextIndex(index: Int): Int {
        val p = positionOf[index] + 1
        return if (p < shuffled.size) shuffled[p] else C.INDEX_UNSET
    }

    override fun getPreviousIndex(index: Int): Int {
        val p = positionOf[index] - 1
        return if (p >= 0) shuffled[p] else C.INDEX_UNSET
    }

    override fun getLastIndex() = if (shuffled.isEmpty()) C.INDEX_UNSET else shuffled.last()
    override fun getFirstIndex() = if (shuffled.isEmpty()) C.INDEX_UNSET else shuffled.first()

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        val shifted = shuffled.map { if (it >= insertionIndex) it + insertionCount else it }.toMutableList()
        val inserted = List(insertionCount) { insertionIndex + it }
        val at = if (insertionIndex == 0 || insertionIndex >= shuffled.size) {
            if (insertionIndex == 0 && shuffled.isNotEmpty()) 0 else shifted.size
        } else {
            positionOf[insertionIndex - 1] + 1
        }
        shifted.addAll(at, inserted)
        return ListShuffleOrder(shifted.toIntArray())
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val count = indexToExclusive - indexFrom
        return ListShuffleOrder(
            shuffled.filter { it < indexFrom || it >= indexToExclusive }
                .map { if (it >= indexToExclusive) it - count else it }
                .toIntArray(),
        )
    }

    override fun cloneAndMove(indexFrom: Int, indexToExclusive: Int, newIndexFrom: Int): ShuffleOrder {
        // Rebuild the list-order permutation, then translate every index while keeping the play order.
        val list = MutableList(shuffled.size) { it }
        val moved = list.subList(indexFrom, indexToExclusive).toList()
        repeat(moved.size) { list.removeAt(indexFrom) }
        list.addAll(newIndexFrom, moved)
        val newIndexOfOld = IntArray(list.size).also { m -> list.forEachIndexed { newIdx, old -> m[old] = newIdx } }
        return ListShuffleOrder(IntArray(shuffled.size) { newIndexOfOld[shuffled[it]] })
    }

    override fun cloneAndClear(): ShuffleOrder = ListShuffleOrder(IntArray(0))
}
