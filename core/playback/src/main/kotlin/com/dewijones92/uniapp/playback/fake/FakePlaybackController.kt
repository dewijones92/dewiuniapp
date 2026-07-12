package com.dewijones92.uniapp.playback.fake

import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.SkipSegment
import com.dewijones92.uniapp.playback.PlaybackController
import com.dewijones92.uniapp.playback.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** In-memory [PlaybackController] for tests and Compose previews. */
public class FakePlaybackController : PlaybackController {

    private val _state = MutableStateFlow<PlaybackState?>(null)
    override val state: StateFlow<PlaybackState?> = _state

    /** Segments handed to the most recent [play] call, for assertions. */
    public var lastSkipSegments: List<SkipSegment> = emptyList()
        private set

    override fun play(item: MediaItem, skipSegments: List<SkipSegment>) {
        lastSkipSegments = skipSegments
        _state.value = PlaybackState(
            itemId = item.id,
            title = item.title,
            isPlaying = true,
            positionMs = 0,
            durationMs = item.duration?.inWholeMilliseconds,
        )
    }

    override fun togglePlayPause() {
        _state.update { it?.copy(isPlaying = !it.isPlaying) }
    }
}
