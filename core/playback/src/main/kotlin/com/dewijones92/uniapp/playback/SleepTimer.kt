package com.dewijones92.uniapp.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Pauses playback after a chosen delay — the classic "stop after N minutes"
 * sleep timer. Counts down once a second so the UI can show the time left, and
 * only pauses if something is still playing when it fires. Works for both
 * pillars since it drives the one [PlaybackController].
 */
public class SleepTimer(
    private val controller: PlaybackController,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    public val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var job: Job? = null

    /** Starts (or restarts) the timer for [duration]. */
    public fun start(duration: Duration) {
        job?.cancel()
        job = scope.launch {
            var remaining = duration
            _state.value = SleepTimerState.Running(remaining)
            while (isActive && remaining > Duration.ZERO) {
                delay(TICK)
                remaining -= TICK
                _state.value = SleepTimerState.Running(remaining.coerceAtLeast(Duration.ZERO))
            }
            if (controller.state.value?.isPlaying == true) controller.togglePlayPause()
            _state.value = SleepTimerState.Off
        }
    }

    /** Cancels a running timer, leaving playback alone. */
    public fun cancel() {
        job?.cancel()
        job = null
        _state.value = SleepTimerState.Off
    }

    private companion object {
        val TICK: Duration = 1.seconds
    }
}

/** Whether a sleep timer is running, and how long is left. */
public sealed interface SleepTimerState {
    public data object Off : SleepTimerState
    public data class Running(val remaining: Duration) : SleepTimerState
}
