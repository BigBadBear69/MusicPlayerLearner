package com.wavelength.player.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    /** Free Last.fm API key, entered by the user in Settings. Stays on the phone. */
    val lastFmApiKey: String = "",
    /** When off, Discover never goes online. */
    val discoverOnline: Boolean = true,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read() = AppSettings(
        lastFmApiKey = prefs.getString("lastfm_key", "").orEmpty(),
        discoverOnline = prefs.getBoolean("discover_online", true),
    )

    fun update(block: (AppSettings) -> AppSettings) {
        val new = block(_settings.value)
        prefs.edit()
            .putString("lastfm_key", new.lastFmApiKey.trim())
            .putBoolean("discover_online", new.discoverOnline)
            .apply()
        _settings.value = new.copy(lastFmApiKey = new.lastFmApiKey.trim())
    }
}
