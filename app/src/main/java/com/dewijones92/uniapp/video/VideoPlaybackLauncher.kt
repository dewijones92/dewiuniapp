package com.dewijones92.uniapp.video

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.playback.PlaybackController

/**
 * The one place a video goes from "a watch URL" to "playing". Streaming URLs
 * expire, so a video is resolved through the shared [VideoResolver] at play
 * time; a downloaded copy skips resolution entirely. Every entry point that
 * plays a video — the Videos feed, Search, a channel page — uses this, so the
 * resolve-then-play decision lives once.
 */
class VideoPlaybackLauncher(
    private val resolver: VideoResolver,
    private val playback: PlaybackController,
) {
    /** Plays an already-downloaded file — no re-resolution needed (it's stream-ready). */
    fun playLocal(item: MediaItem, localPath: String) {
        playback.play(item, localPath = localPath)
    }

    /**
     * Resolves [watchUrl] to a playable stream (with its skip segments) and
     * plays it. Returns false when the video can't be resolved (private,
     * removed, geo-blocked, …) so callers can surface that.
     */
    suspend fun play(watchUrl: HttpUrl, sourceId: SourceId): Boolean {
        val resolved = resolver.resolve(watchUrl, sourceId) ?: return false
        playback.play(resolved.item, skipSegments = resolved.skipSegments)
        return true
    }
}
