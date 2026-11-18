package com.wavelength.player.playback

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** What was playing, so the queue survives the app being closed or the phone restarting. */
data class SavedQueue(
    val songIds: List<Long>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
    val shuffleOrder: IntArray?,
)

class QueueStore(context: Context) {
    private val file = File(context.filesDir, "queue.json")

    fun save(q: SavedQueue) {
        val json = JSONObject()
            .put("songIds", JSONArray(q.songIds))
            .put("index", q.index)
            .put("positionMs", q.positionMs)
            .put("shuffle", q.shuffle)
            .put("repeatMode", q.repeatMode)
            .put("shuffleOrder", q.shuffleOrder?.let { JSONArray(it.toList()) })
        val tmp = File(file.parentFile, "queue.json.tmp")
        tmp.writeText(json.toString())
        tmp.renameTo(file)
    }

    fun load(): SavedQueue? = runCatching {
        val o = JSONObject(file.readText())
        fun JSONArray.longs() = List(length()) { getLong(it) }
        SavedQueue(
            songIds = o.getJSONArray("songIds").longs(),
            index = o.getInt("index"),
            positionMs = o.getLong("positionMs"),
            shuffle = o.getBoolean("shuffle"),
            repeatMode = o.getInt("repeatMode"),
            shuffleOrder = o.optJSONArray("shuffleOrder")?.let { a -> IntArray(a.length()) { a.getInt(it) } },
        )
    }.getOrNull()
}
