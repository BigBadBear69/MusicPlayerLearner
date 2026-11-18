package com.wavelength.player.data.lyrics

import java.io.DataInputStream
import java.io.InputStream
import java.nio.charset.Charset

/**
 * Reads lyrics stored inside the audio file itself:
 *  - MP3 (ID3v2 "USLT" frame), FLAC (Vorbis comment LYRICS / UNSYNCEDLYRICS), M4A ("©lyr" atom).
 */
object EmbeddedLyrics {
    private const val MAX_SCAN = 24 * 1024 * 1024

    fun read(open: () -> InputStream?, mime: String?, fileName: String): String? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return runCatching {
            when {
                ext == "flac" || mime == "audio/flac" -> open()?.use { flac(it) }
                ext in setOf("m4a", "mp4", "aac", "alac") || mime == "audio/mp4" -> open()?.use { m4a(it) }
                else -> open()?.use { id3(it) }
            }
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    // ---------------------------------------------------------------- ID3v2

    private fun id3(input: InputStream): String? {
        val header = ByteArray(10)
        if (input.readNBytesCompat(header) < 10 || header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null
        val version = header[3].toInt()
        val size = syncsafe(header, 6)
        if (size <= 0 || size > MAX_SCAN) return null
        val tag = ByteArray(size)
        input.readNBytesCompat(tag)
        var p = 0
        if (header[5].toInt() and 0x40 != 0 && version >= 3) p += 4 + (if (version == 4) syncsafe(tag, 0) - 4 else be32(tag, 0)) // skip extended header
        val idLen = if (version == 2) 3 else 4
        while (p + idLen + 6 <= tag.size) {
            val id = String(tag, p, idLen, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            val frameSize = when (version) {
                2 -> ((tag[p + 3].toInt() and 0xff) shl 16) or ((tag[p + 4].toInt() and 0xff) shl 8) or (tag[p + 5].toInt() and 0xff)
                4 -> syncsafe(tag, p + 4)
                else -> be32(tag, p + 4)
            }
            val headerLen = if (version == 2) 6 else 10
            val start = p + headerLen
            if (frameSize <= 0 || start + frameSize > tag.size) break
            if (id == "USLT" || id == "ULT") return uslt(tag, start, frameSize)
            p = start + frameSize
        }
        return null
    }

    private fun uslt(b: ByteArray, start: Int, len: Int): String? {
        val enc = b[start].toInt()
        val charset: Charset = when (enc) { 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; 3 -> Charsets.UTF_8; else -> Charsets.ISO_8859_1 }
        var p = start + 4 // encoding + 3-byte language
        val end = start + len
        // Skip the content descriptor (null-terminated; 2 zero bytes for UTF-16).
        val wide = enc == 1 || enc == 2
        while (p < end) {
            if (!wide && b[p].toInt() == 0) { p += 1; break }
            if (wide && p + 1 < end && b[p].toInt() == 0 && b[p + 1].toInt() == 0) { p += 2; break }
            p += if (wide) 2 else 1
        }
        if (p >= end) return null
        return String(b, p, end - p, charset).trimEnd('\u0000')
    }

    private fun syncsafe(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0x7f) shl 21) or ((b[o + 1].toInt() and 0x7f) shl 14) or ((b[o + 2].toInt() and 0x7f) shl 7) or (b[o + 3].toInt() and 0x7f)

    private fun be32(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xff) shl 24) or ((b[o + 1].toInt() and 0xff) shl 16) or ((b[o + 2].toInt() and 0xff) shl 8) or (b[o + 3].toInt() and 0xff)

    // ---------------------------------------------------------------- FLAC

    private fun flac(input: InputStream): String? {
        val d = DataInputStream(input)
        val magic = ByteArray(4)
        d.readFully(magic)
        if (String(magic, Charsets.ISO_8859_1) != "fLaC") return null
        while (true) {
            val h = d.readInt()
            val last = h ushr 31 == 1
            val type = (h ushr 24) and 0x7f
            val len = h and 0xffffff
            if (type == 4) {
                val block = ByteArray(len)
                d.readFully(block)
                return vorbisLyrics(block)
            }
            d.skipBytes(len)
            if (last) return null
        }
    }

    private fun vorbisLyrics(b: ByteArray): String? {
        fun le32(o: Int) = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)
        var p = 0
        val vendor = le32(p); p += 4 + vendor
        val count = le32(p); p += 4
        repeat(count) {
            if (p + 4 > b.size) return null
            val len = le32(p); p += 4
            if (len < 0 || p + len > b.size) return null
            val c = String(b, p, len, Charsets.UTF_8); p += len
            val key = c.substringBefore('=').uppercase()
            if (key == "LYRICS" || key == "UNSYNCEDLYRICS" || key == "SYNCEDLYRICS") return c.substringAfter('=')
        }
        return null
    }

    // ---------------------------------------------------------------- MP4 / M4A

    private fun m4a(input: InputStream): String? {
        val buf = ByteArray(MAX_SCAN)
        val n = input.readNBytesCompat(buf)
        val marker = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        var i = 4
        while (i <= n - 4) {
            if (buf[i] == marker[0] && buf[i + 1] == marker[1] && buf[i + 2] == marker[2] && buf[i + 3] == marker[3]) {
                // ©lyr atom → inner "data" atom: size(4) "data"(4) type(4) locale(4) payload
                val d = i + 4
                if (d + 16 <= n && String(buf, d + 4, 4, Charsets.ISO_8859_1) == "data") {
                    val size = be32(buf, d)
                    if (size in 17..(n - d)) return String(buf, d + 16, size - 16, Charsets.UTF_8)
                }
            }
            i++
        }
        return null
    }

    private fun InputStream.readNBytesCompat(b: ByteArray): Int {
        var off = 0
        while (off < b.size) {
            val r = read(b, off, b.size - off)
            if (r < 0) break
            off += r
        }
        return off
    }
}
