package com.dewijones92.uniapp.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Foreground media service so playback continues when the app is backgrounded.
 * Media3 renders and updates the media notification itself; the seek
 * increments below surface as skip buttons in the notification and on the
 * lock screen.
 */
public class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(MergingAudioVideoFactory(DefaultMediaSourceFactory(this)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                // handleAudioFocus:
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private companion object {
        // Podcast-style transport: small hop back to re-hear, bigger hop forward.
        const val SEEK_BACK_MS = 10_000L
        const val SEEK_FORWARD_MS = 30_000L
    }
}

/**
 * Wraps the default source factory: when a [MediaItem] carries a separate audio
 * URL (higher-than-muxed video qualities stream video-only + audio-only), the
 * two are merged into one [MergingMediaSource] so they play in sync. Everything
 * else — podcasts, muxed streams, local files — passes straight through.
 */
@UnstableApi
private class MergingAudioVideoFactory(
    private val default: DefaultMediaSourceFactory,
) : MediaSource.Factory {

    override fun getSupportedTypes(): IntArray = default.supportedTypes

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory =
        apply { default.setDrmSessionManagerProvider(provider) }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory =
        apply { default.setLoadErrorHandlingPolicy(policy) }

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val audioUrl = mediaItem.requestMetadata.extras?.getString(EXTRA_AUDIO_URL)
        val video = default.createMediaSource(mediaItem)
        if (audioUrl.isNullOrEmpty()) return video
        val audio = default.createMediaSource(MediaItem.fromUri(audioUrl))
        return MergingMediaSource(video, audio)
    }
}

/** Extras key on a [MediaItem]'s request metadata carrying the separate audio-track URL. */
internal const val EXTRA_AUDIO_URL: String = "com.dewijones92.uniapp.AUDIO_URL"
