package com.wavelength.player.data.audio

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ln

/** What this phone's equalizer offers. Known once playback has started at least once. */
data class EqInfo(val bandCentersHz: List<Int>, val minDb: Float, val maxDb: Float)

data class EqSettings(
    val enabled: Boolean = false,
    val preset: String = EqPresets.FLAT,
    /** Gain per band in dB (same order as EqInfo.bandCentersHz). */
    val bandsDb: List<Float> = emptyList(),
    /** 0..1 */
    val bassBoost: Float = 0f,
)

/** Named sound curves, defined by frequency so they fit any phone's band layout. */
object EqPresets {
    const val FLAT = "Flat"
    const val CUSTOM = "Custom"
    private val curves: Map<String, List<Pair<Int, Float>>> = linkedMapOf(
        FLAT to listOf(60 to 0f, 16000 to 0f),
        "Bass boost" to listOf(60 to 6f, 230 to 4f, 910 to 0f, 3600 to 0f, 14000 to 0f),
        "Rock" to listOf(60 to 5f, 230 to 3f, 910 to -1f, 3600 to 3f, 14000 to 5f),
        "Pop" to listOf(60 to -1f, 230 to 2f, 910 to 4f, 3600 to 2f, 14000 to -1f),
        "Electronic" to listOf(60 to 5f, 230 to 2f, 910 to 0f, 3600 to 2f, 14000 to 4f),
        "Hip-hop" to listOf(60 to 6f, 230 to 3f, 910 to 0f, 3600 to 1f, 14000 to 2f),
        "Jazz" to listOf(60 to 3f, 230 to 1f, 910 to -1f, 3600 to 2f, 14000 to 3f),
        "Classical" to listOf(60 to 4f, 230 to 2f, 910 to -1f, 3600 to 2f, 14000 to 4f),
        "Vocal" to listOf(60 to -2f, 230 to 0f, 910 to 4f, 3600 to 3f, 14000 to 0f),
        "Acoustic" to listOf(60 to 3f, 230 to 1f, 910 to 1f, 3600 to 2f, 14000 to 2f),
        "Treble boost" to listOf(60 to 0f, 230 to 0f, 910 to 0f, 3600 to 4f, 14000 to 6f),
        "Late night" to listOf(60 to -3f, 230 to -1f, 910 to 1f, 3600 to 1f, 14000 to -2f),
    )
    val names: List<String> get() = curves.keys.toList()

    /** Gain for each band, interpolated on a log-frequency scale. */
    fun gains(name: String, bandCentersHz: List<Int>, minDb: Float, maxDb: Float): List<Float> {
        val curve = curves[name] ?: curves.getValue(FLAT)
        return bandCentersHz.map { hz ->
            val x = ln(hz.coerceAtLeast(1).toDouble())
            val db = when {
                hz <= curve.first().first -> curve.first().second
                hz >= curve.last().first -> curve.last().second
                else -> {
                    val i = curve.indexOfLast { it.first <= hz }
                    val (f0, g0) = curve[i]
                    val (f1, g1) = curve[i + 1]
                    val t = (x - ln(f0.toDouble())) / (ln(f1.toDouble()) - ln(f0.toDouble()))
                    (g0 + (g1 - g0) * t).toFloat()
                }
            }
            db.coerceIn(minDb, maxDb)
        }
    }
}

/**
 * Equalizer and bass boost, attached to the player's audio session by the playback service.
 * Settings are saved and re-applied whenever playback starts.
 */
class AudioEffects(context: Context) {
    private val prefs = context.getSharedPreferences("equalizer", Context.MODE_PRIVATE)
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null

    private val _info = MutableStateFlow(readInfo())
    val info: StateFlow<EqInfo?> = _info.asStateFlow()
    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<EqSettings> = _settings.asStateFlow()
    private val _supported = MutableStateFlow(true)
    val supported: StateFlow<Boolean> = _supported.asStateFlow()

    fun attach(audioSessionId: Int) {
        release()
        try {
            val e = Equalizer(0, audioSessionId)
            val (min, max) = e.bandLevelRange.let { it[0] / 100f to it[1] / 100f }
            val centers = List(e.numberOfBands.toInt()) { e.getCenterFreq(it.toShort()) / 1000 }
            eq = e
            val info = EqInfo(centers, min, max)
            if (_info.value != info) {
                _info.value = info
                prefs.edit().putString("info", "${centers.joinToString(",")};$min;$max").apply()
            }
            bass = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
            _supported.value = true
            apply()
        } catch (t: Throwable) {
            Log.w("AudioEffects", "Equalizer not available", t)
            _supported.value = false
        }
    }

    fun release() {
        runCatching { eq?.release() }
        runCatching { bass?.release() }
        eq = null
        bass = null
    }

    fun update(block: (EqSettings) -> EqSettings) {
        val s = block(_settings.value)
        _settings.value = s
        prefs.edit()
            .putBoolean("enabled", s.enabled)
            .putString("preset", s.preset)
            .putString("bands", s.bandsDb.joinToString(","))
            .putFloat("bass", s.bassBoost)
            .apply()
        apply()
    }

    fun choosePreset(name: String) {
        val info = _info.value ?: return update { it.copy(preset = name) }
        update { it.copy(preset = name, bandsDb = EqPresets.gains(name, info.bandCentersHz, info.minDb, info.maxDb), enabled = true) }
    }

    fun setBand(index: Int, db: Float) {
        val info = _info.value ?: return
        update { s ->
            val bands = s.bandsDb.takeIf { it.size == info.bandCentersHz.size }?.toMutableList()
                ?: MutableList(info.bandCentersHz.size) { 0f }
            bands[index] = db.coerceIn(info.minDb, info.maxDb)
            s.copy(bandsDb = bands, preset = EqPresets.CUSTOM, enabled = true)
        }
    }

    private fun apply() {
        val s = _settings.value
        eq?.let { e ->
            runCatching {
                e.enabled = s.enabled
                if (s.bandsDb.size == e.numberOfBands.toInt()) {
                    s.bandsDb.forEachIndexed { i, db -> e.setBandLevel(i.toShort(), (db * 100).toInt().toShort()) }
                }
            }
        }
        bass?.let { b ->
            runCatching {
                b.enabled = s.enabled && s.bassBoost > 0f
                if (b.strengthSupported) b.setStrength((s.bassBoost * 1000).toInt().coerceIn(0, 1000).toShort())
            }
        }
    }

    private fun readInfo(): EqInfo? = prefs.getString("info", null)?.let { raw ->
        runCatching {
            val (c, min, max) = raw.split(';')
            EqInfo(c.split(',').map { it.toInt() }, min.toFloat(), max.toFloat())
        }.getOrNull()
    }

    private fun readSettings() = EqSettings(
        enabled = prefs.getBoolean("enabled", false),
        preset = prefs.getString("preset", EqPresets.FLAT) ?: EqPresets.FLAT,
        bandsDb = prefs.getString("bands", "")?.split(',')?.mapNotNull { it.toFloatOrNull() }.orEmpty(),
        bassBoost = prefs.getFloat("bass", 0f),
    )
}
