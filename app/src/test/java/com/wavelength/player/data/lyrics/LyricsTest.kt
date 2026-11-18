package com.wavelength.player.data.lyrics

import com.wavelength.player.data.audio.EqPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class LyricsTest {
    @Test
    fun parsesLrcWithMultipleStampsAndOffset() {
        val lines = Lrc.parse(
            """
            [ti:Song]
            [offset:+500]
            [00:12.50]First line
            [00:20.00][01:00.00]Chorus
            [00:30.1]Third
            """.trimIndent(),
        )
        assertEquals(listOf(12_000L, 19_500L, 29_600L, 59_500L), lines.map { it.timeMs })
        assertEquals("Chorus", lines[1].text)
        assertEquals(-1, Lrc.currentIndex(lines, 5_000))
        assertEquals(1, Lrc.currentIndex(lines, 25_000))
        assertEquals(3, Lrc.currentIndex(lines, 600_000))
        assertTrue(Lrc.from("just words\nno timing", "x") is Lyrics.Plain)
    }

    private fun syncsafe(n: Int) = byteArrayOf((n shr 21 and 0x7f).toByte(), (n shr 14 and 0x7f).toByte(), (n shr 7 and 0x7f).toByte(), (n and 0x7f).toByte())

    @Test
    fun readsId3UsltFrame() {
        val text = "Hello darkness\nmy old friend"
        val body = ByteArrayOutputStream().apply {
            write(3) // UTF-8
            write("eng".toByteArray())
            write(0) // empty descriptor
            write(text.toByteArray(Charsets.UTF_8))
        }.toByteArray()
        val frame = ByteArrayOutputStream().apply {
            write("USLT".toByteArray()); write(syncsafe(body.size)); write(byteArrayOf(0, 0)); write(body)
        }.toByteArray()
        val file = ByteArrayOutputStream().apply {
            write("ID3".toByteArray()); write(byteArrayOf(4, 0, 0)); write(syncsafe(frame.size + 20)); write(frame); write(ByteArray(20)); write(ByteArray(100))
        }.toByteArray()
        assertEquals(text, EmbeddedLyrics.read({ file.inputStream() }, "audio/mpeg", "a.mp3"))
    }

    @Test
    fun readsFlacVorbisLyrics() {
        fun le32(n: Int) = byteArrayOf(n.toByte(), (n shr 8).toByte(), (n shr 16).toByte(), (n shr 24).toByte())
        val comments = listOf("TITLE=x", "LYRICS=[00:01.00]La la la")
        val block = ByteArrayOutputStream().apply {
            write(le32(3)); write("abc".toByteArray()); write(le32(comments.size))
            comments.forEach { c -> val b = c.toByteArray(); write(le32(b.size)); write(b) }
        }.toByteArray()
        val file = ByteArrayOutputStream().apply {
            write("fLaC".toByteArray())
            write(byteArrayOf(0, 0, 0, 34)); write(ByteArray(34)) // STREAMINFO
            write(byteArrayOf((0x80 or 4).toByte(), (block.size shr 16).toByte(), (block.size shr 8).toByte(), block.size.toByte())); write(block)
        }.toByteArray()
        assertEquals("[00:01.00]La la la", EmbeddedLyrics.read({ file.inputStream() }, "audio/flac", "a.flac"))
    }

    @Test
    fun presetsFitAnyBandLayout() {
        val five = listOf(60, 230, 910, 3600, 14000)
        assertEquals(listOf(6f, 4f, 0f, 0f, 0f), EqPresets.gains("Bass boost", five, -15f, 15f))
        val ten = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val g = EqPresets.gains("Bass boost", ten, -3f, 3f)
        assertEquals(10, g.size)
        assertTrue(g.all { it in -3f..3f }) // clamped to the phone's range
        assertTrue(g.first() >= g.last())
    }
}
