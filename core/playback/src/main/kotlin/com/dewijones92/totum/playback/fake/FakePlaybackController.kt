package com.dewijones92.totum.playback.fake

import androidx.media3.common.Player
import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.common.SubtitleTrack
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaKind
import com.dewijones92.totum.domain.SkipSegment
import com.dewijones92.totum.playback.PlaybackController
import com.dewijones92.totum.playback.PlaybackState
import com.dewijones92.totum.playback.VolumeBoost
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
        subtitles: List<SubtitleTrack>,
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
            skipSegments = skipSegments,
            chapters = item.chapters,
            subtitles = subtitles,
            subtitleLanguage = subtitleLanguage,
        )
    }

    override fun setSubtitleLanguage(languageCode: String?) {
        subtitleLanguage = languageCode
        _state.value = _state.value?.copy(subtitleLanguage = languageCode)
    }

    private var subtitleLanguage: String? = null

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

    override fun setVolumeBoost(boost: VolumeBoost) {
        _state.update { it?.copy(volumeBoost = boost) }
    }

    override fun setSkipSilence(enabled: Boolean) {
        _state.update { it?.copy(skipSilence = enabled) }
    }

    private companion object {
        const val SEEK_BACK_MS = 10_000L
        const val SEEK_FORWARD_MS = 30_000L
    }
}
