package com.dewijones92.uniapp.video

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.innertube.history.YouTubeWatchHistory
import com.dewijones92.uniapp.playback.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * The one place a video goes from "a watch URL" to "playing", and the owner of
 * the current video's selectable qualities. Streaming URLs expire, so a video
 * is resolved through the shared [VideoResolver] at play time; a downloaded
 * copy skips resolution entirely. Every entry point that plays a video — the
 * Videos feed, Search, a channel page — uses this, so the resolve-then-play
 * decision and quality switching live once.
 */
class VideoPlaybackLauncher(
    private val resolver: VideoResolver,
    private val playback: PlaybackController,
    private val watchHistory: YouTubeWatchHistory,
    /** Max video height to auto-pick for the current network (a cap); default: no cap. */
    private val preferredMaxHeight: () -> Int = { Int.MAX_VALUE },
) {
    /** The current video's quality options and which one is playing. */
    data class QualityState(
        val options: List<VideoQuality> = emptyList(),
        val selectedId: String? = null,
        /** True when an audio-only stream exists, so "Listen" mode is offered. */
        val canListen: Boolean = false,
        /** True while playing audio-only ("Listen"); the toggle then offers "Watch". */
        val listening: Boolean = false,
    )

    private val _quality = MutableStateFlow(QualityState())
    val quality: StateFlow<QualityState> = _quality

    /** The last resolved video, kept so a quality switch replays without re-extracting. */
    private var current: VideoResolver.Resolved? = null

    /** Plays an already-downloaded file — no re-resolution, and no quality choice (it's one merged file). */
    fun playLocal(item: MediaItem, localPath: String) {
        current = null
        _quality.value = QualityState()
        playback.play(item, localPath = localPath)
    }

    /**
     * Resolves [watchUrl] to a playable stream (with its skip segments and
     * quality ladder) and plays the default quality. Returns false when the
     * video can't be resolved (private, removed, geo-blocked, …).
     */
    suspend fun play(watchUrl: HttpUrl, sourceId: SourceId): Boolean {
        val resolved = resolver.resolve(watchUrl, sourceId) ?: return false
        current = resolved
        // Register this video's tracking URLs so its progress can sync to YouTube.
        watchHistory.beginSession(
            resolved.item.id.value,
            resolved.playbackTrackingUrl,
            resolved.watchtimeTrackingUrl,
        )
        playVideoQuality(resolved)
        return true
    }

    /** Plays [resolved] as video at the best allowed quality — the shared play/"Watch" path. */
    private fun playVideoQuality(resolved: VideoResolver.Resolved) {
        // Auto-pick the best quality within the network's cap; fall back to the
        // reliable muxed default when nothing qualifies (or there are no ladders).
        val chosen = resolved.qualities.filter { it.height <= preferredMaxHeight() }.maxByOrNull { it.height }
        val selected = chosen?.id ?: resolved.qualities.firstOrNull { it.videoUrl == resolved.item.mediaUrl }?.id
        _quality.value = QualityState(
            options = resolved.qualities,
            selectedId = selected,
            canListen = resolved.audioOnlyUrl != null,
            listening = false,
        )
        if (chosen != null) {
            playback.play(
                resolved.item.copy(mediaUrl = chosen.videoUrl),
                skipSegments = resolved.skipSegments,
                audioUrl = chosen.audioUrl,
            )
        } else {
            playback.play(resolved.item, skipSegments = resolved.skipSegments)
        }
    }

    /**
     * Switches the current video to audio-only ("Listen") — replays the same
     * item with just the audio stream, so there's no video track (the player
     * shows artwork) and far less data is used. Replays from the saved position.
     * No-op if nothing is resolved or there's no audio-only stream.
     */
    fun listen() {
        val resolved = current ?: return
        val audio = resolved.audioOnlyUrl ?: return
        _quality.update { it.copy(selectedId = null, listening = true) }
        playback.play(resolved.item.copy(mediaUrl = audio), skipSegments = resolved.skipSegments)
    }

    /** Leaves "Listen" (audio-only) and returns to watching the video, at the saved position. */
    fun watch() {
        val resolved = current ?: return
        playVideoQuality(resolved)
    }

    /**
     * Switches the current video to another quality, replaying from where it
     * is now (the playback controller restores the saved position). No-op if
     * nothing is playing or the id is unknown.
     */
    fun selectQuality(id: String) {
        val resolved = current ?: return
        val quality = resolved.qualities.firstOrNull { it.id == id } ?: return
        _quality.update { it.copy(selectedId = id, listening = false) }
        playback.play(
            resolved.item.copy(mediaUrl = quality.videoUrl),
            skipSegments = resolved.skipSegments,
            audioUrl = quality.audioUrl,
        )
    }
}
