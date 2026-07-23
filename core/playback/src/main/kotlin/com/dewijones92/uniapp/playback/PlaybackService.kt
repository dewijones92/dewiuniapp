package com.dewijones92.uniapp.playback

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Foreground media service so playback continues when the app is backgrounded.
 * Media3 renders and updates the media notification itself; the seek
 * increments below surface as skip buttons in the notification and on the
 * lock screen.
 */
public class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    // Held so the skip-silences toggle (a custom session command) can flip it live.
    @UnstableApi
    private val silenceSkipping = SilenceSkippingAudioProcessor()

    // The user's skip-silences intent. The processor is only actually enabled for
    // audio-only playback: on a video, silence-skipping shortens the audio but not
    // the video, so the audio runs ahead of the picture (measured desync). Video
    // present ⇒ forced off, so audio and video always stay in sync.
    private var skipSilenceEnabled = false
    private var player: ExoPlayer? = null

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    override fun onCreate() {
        super.onCreate()
        // A custom audio sink whose processor chain carries the silence skipper
        // (Sonic stays for speed/pitch); skipping is off until the user turns it on.
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                Log.i("dewidebug", "av-sync buildAudioSink -> custom chain with silence skipper installed")
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessorChain(
                        DefaultAudioSink.DefaultAudioProcessorChain(silenceSkipping, SonicAudioProcessor()),
                    )
                    .build()
            }
        }
        val player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
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
        this.player = player
        // dewidebug: A/V-sync instrumentation — dropped frames + frame-processing
        // offset (video-timing health) and audio underruns, stamped with skip state.
        player.addAnalyticsListener(AvSyncLogger())
        // When the tracks change (a new item, video vs audio), re-apply the effective
        // skip-silence: off whenever a video track is present, so A/V stays in sync.
        player.addListener(
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) = applyEffectiveSkipSilence()
            },
        )
        mediaSession = MediaSession.Builder(this, player).setCallback(SkipSilenceCallback()).build()
    }

    /** Adds the skip-silences custom command and applies it to the audio processor. */
    @UnstableApi
    private inner class SkipSilenceCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(ACTION_SKIP_SILENCE, Bundle.EMPTY))
                        .build(),
                )
                .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_SKIP_SILENCE) {
                val enabled = args.getBoolean(EXTRA_SKIP_SILENCE_ENABLED)
                Log.i("dewidebug", "skip-silence -> $enabled")
                skipSilenceEnabled = enabled
                applyEffectiveSkipSilence()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    /**
     * dewidebug: logs the signals that reveal whether video stays locked to the
     * (silence-adjusted) audio clock. The video renderer slaves frame release to
     * the audio-driven media clock, so when a silence is skipped the clock jumps
     * and the renderer drops the silent-gap frames to catch up — expect a
     * dropped-frame burst at each skip and a frame-processing offset that stays
     * bounded (not growing), which together mean audio and video stay in sync.
     */
    @UnstableApi
    private inner class AvSyncLogger : AnalyticsListener {
        override fun onVideoFrameProcessingOffset(
            eventTime: AnalyticsListener.EventTime,
            totalProcessingOffsetUs: Long,
            frameCount: Int,
        ) {
            if (frameCount == 0) return
            val avgMs = totalProcessingOffsetUs / frameCount / US_PER_MS
            Log.i(
                "dewidebug",
                "av-sync pos=${eventTime.currentPlaybackPositionMs}ms frameOffsetAvg=${avgMs}ms " +
                    "frames=$frameCount skipSilence=$skipSilenceEnabled " +
                    "silenceActive=${silenceSkipping.isActive} skippedFrames=${silenceSkipping.skippedFrames}",
            )
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long,
        ) {
            Log.i(
                "dewidebug",
                "av-sync pos=${eventTime.currentPlaybackPositionMs}ms droppedFrames=$droppedFrames " +
                    "over=${elapsedMs}ms skipSilence=$skipSilenceEnabled",
            )
        }

        override fun onAudioUnderrun(
            eventTime: AnalyticsListener.EventTime,
            bufferSize: Int,
            bufferSizeMs: Long,
            elapsedSinceLastFeedMs: Long,
        ) {
            Log.i(
                "dewidebug",
                "av-sync audioUnderrun bufferMs=$bufferSizeMs sinceLastFeedMs=$elapsedSinceLastFeedMs " +
                    "skipSilence=$skipSilenceEnabled",
            )
        }
    }

    /**
     * Enables the silence skipper only when the user asked for it AND there is no
     * video track: silence-skipping a video desyncs audio ahead of the picture, so
     * on video it's forced off (the toggle is also hidden in the UI for video).
     */
    @UnstableApi
    private fun applyEffectiveSkipSilence() {
        val hasVideo = player?.currentTracks?.groups?.any { it.type == C.TRACK_TYPE_VIDEO } == true
        val effective = skipSilenceEnabled && !hasVideo
        Log.i(
            "dewidebug",
            "av-sync applyEffectiveSkipSilence intent=$skipSilenceEnabled hasVideo=$hasVideo -> $effective"
        )
        silenceSkipping.setEnabled(effective)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    private companion object {
        // Podcast-style transport: small hop back to re-hear, bigger hop forward.
        const val SEEK_BACK_MS = 10_000L
        const val SEEK_FORWARD_MS = 30_000L

        /** dewidebug: microseconds per millisecond, for the A/V-sync frame-offset log. */
        const val US_PER_MS = 1_000
    }
}

/** Custom session command to toggle silence-skipping; the bool rides in [EXTRA_SKIP_SILENCE_ENABLED]. */
internal const val ACTION_SKIP_SILENCE: String = "com.dewijones92.uniapp.SKIP_SILENCE"
internal const val EXTRA_SKIP_SILENCE_ENABLED: String = "enabled"

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
