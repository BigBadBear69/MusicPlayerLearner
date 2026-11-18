package com.wavelength.player.data.discover

import java.text.Normalizer

/**
 * Makes "Song Title (feat. Someone) - 2011 Remaster" by "The Artist" and "song title" by "artist"
 * compare equal, so recommendations can be matched against songs already on the phone.
 */
object Normalize {
    private val bracketed = Regex("""\s*[(\[][^)\]]*[)\]]""")
    private val suffix = Regex("""\s+-\s+.*(remaster|remix|version|edit|live|mono|stereo|mix|acoustic|demo).*$""", RegexOption.IGNORE_CASE)
    private val feat = Regex("""\s+(feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)
    private val nonWord = Regex("""[^\p{L}\p{N}]+""")

    private fun base(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("""\p{M}+"""), "")
        .lowercase()
        .replace("&", " and ")

    fun title(s: String): String =
        base(s).replace(bracketed, "").replace(suffix, "").replace(feat, "").replace(nonWord, " ").trim()

    fun artist(s: String): String {
        // "Artist A, Artist B" / "A & B" / "A feat. B": the first named artist is the main one.
        val main = base(s).replace(feat, "").split(Regex("""\s*(,|;|/| x | and | with )\s*""")).first()
        return main.removePrefix("the ").replace(nonWord, " ").trim()
    }

    fun key(artist: String, title: String) = artist(artist) + "|" + title(title)
}
