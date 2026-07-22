package com.dewijones92.uniapp.playback.fake

import androidx.media3.common.Player
import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaKind
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

    /** No real player in the fake, so previews/tests show the audio layout. */
    override val player: Player? = null

    /** Segments handed to the most recent [play] call, for assertions. */
    public var lastSkipSegments: List<SkipSegment> = emptyList()
        private set

    /** localPath handed to the most recent [play] call, for assertions. */
    public var lastLocalPath: String? = null
        private set

    /** audioUrl handed to the most recent [play] call, for assertions. */
    public var lastAudioUrl: HttpUrl? = null
        private set

    override fun play(
        item: MediaItem,
        kind: MediaKind,
        skipSegments: List<SkipSegment>,
        localPath: String?,
        audioUrl: HttpUrl?,
    ) {
        lastSkipSegments = skipSegments
        lastLocalPath = localPath
        lastAudioUrl = audioUrl
        _state.value = PlaybackState(
            itemId = item.id,
            title = item.title,
            artist = item.author,
            artworkUrl = item.thumbnailUrl?.value,
            description = item.description,
            kind = kind,
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
