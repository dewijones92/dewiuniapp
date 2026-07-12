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

    /** localPath handed to the most recent [play] call, for assertions. */
    public var lastLocalPath: String? = null
        private set

    override fun play(item: MediaItem, skipSegments: List<SkipSegment>, localPath: String?) {
        lastSkipSegments = skipSegments
        lastLocalPath = localPath
        _state.value = PlaybackState(
            itemId = item.id,
            title = item.title,
            artist = item.author,
            artworkUrl = item.thumbnailUrl?.value,
            isPlaying = true,
            positionMs = 0,
            durationMs = item.duration?.inWholeMilliseconds,
            speed = 1.0f,
        )
    }

    override fun togglePlayPause() {
        _state.update { it?.copy(isPlaying = !it.isPlaying) }
    }

    override fun seekTo(positionMs: Long) {
        _state.update { it?.copy(positionMs = positionMs.coerceAtLeast(0)) }
    }

    override fun seekBackward() {
        _state.update { it?.copy(positionMs = (it.positionMs - SEEK_BACK_MS).coerceAtLeast(0)) }
    }

    override fun seekForward() {
        _state.update { it?.copy(positionMs = it.positionMs + SEEK_FORWARD_MS) }
    }

    override fun setSpeed(speed: Float) {
        _state.update { it?.copy(speed = speed) }
    }

    private companion object {
        const val SEEK_BACK_MS = 10_000L
        const val SEEK_FORWARD_MS = 30_000L
    }
}
