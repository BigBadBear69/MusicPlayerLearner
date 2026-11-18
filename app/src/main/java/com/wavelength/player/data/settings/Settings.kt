package com.wavelength.player.data.settings

import android.content.Context
import com.wavelength.player.data.smart.ShuffleStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    /** Free Last.fm API key, entered by the user in Settings. Stays on the phone. */
    val lastFmApiKey: String = "",
    /** When off, Discover never goes online. */
    val discoverOnline: Boolean = true,
    /** Which songs shuffle favours. */
    val shuffleStyle: ShuffleStyle = ShuffleStyle.Smart,
    /** Share of a looping shuffled playlist that must play before a song can repeat (0.3–0.9). */
    val repeatWindow: Double = 0.7,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read() = AppSettings(
        lastFmApiKey = prefs.getString("lastfm_key", "").orEmpty(),
        discoverOnline = prefs.getBoolean("discover_online", true),
        shuffleStyle = ShuffleStyle.parse(prefs.getString("shuffle_style", null)),
        repeatWindow = prefs.getFloat("repeat_window", 0.7f).toDouble().coerceIn(0.3, 0.9),
    )

    fun update(block: (AppSettings) -> AppSettings) {
        val new = block(_settings.value)
        prefs.edit()
            .putString("lastfm_key", new.lastFmApiKey.trim())
            .putBoolean("discover_online", new.discoverOnline)
            .putString("shuffle_style", new.shuffleStyle.name)
            .putFloat("repeat_window", new.repeatWindow.toFloat())
            .apply()
        _settings.value = new.copy(lastFmApiKey = new.lastFmApiKey.trim())
    }
}
