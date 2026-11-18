package com.wavelength.player.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The sleep timer's setting. The playback service watches it and pauses playback when it's due. */
data class SleepTimerState(
    /** Wall-clock time to pause at, or null. */
    val endsAt: Long? = null,
    /** Pause when the current song ends instead. */
    val endOfSong: Boolean = false,
) {
    val active get() = endsAt != null || endOfSong
}

class SleepTimer {
    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    fun startMinutes(minutes: Int) { _state.value = SleepTimerState(endsAt = System.currentTimeMillis() + minutes * 60_000L) }
    fun endOfSong() { _state.value = SleepTimerState(endOfSong = true) }
    fun extend(minutes: Int) {
        val base = _state.value.endsAt?.coerceAtLeast(System.currentTimeMillis()) ?: System.currentTimeMillis()
        _state.value = SleepTimerState(endsAt = base + minutes * 60_000L)
    }
    fun cancel() { _state.value = SleepTimerState() }
}
