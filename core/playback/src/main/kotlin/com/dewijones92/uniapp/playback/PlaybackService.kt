package com.dewijones92.uniapp.playback

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
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
import com.google.android.gms.cast.framework.CastContext
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

    // Cast: present only when Google Play Services + a receiver are available.
    // The session swaps between the local player and this one as a Cast session
    // comes and goes; null (or no devices) means everything plays locally as before.
    @UnstableApi
    private var castPlayer: CastPlayer? = null
    private var currentPlayer: Player? = null

    // Last item/position handed across a local↔cast switch, so a cast disconnect
    // (which nulls the CastPlayer's queue) can still resume locally.
    private var lastHandoffItem: MediaItem? = null
    private var lastHandoffPositionMs: Long = 0

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
        currentPlayer = player
        // When the tracks change (a new item, video vs audio), re-apply the effective
        // skip-silence: off whenever a video track is present, so A/V stays in sync.
        player.addListener(
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) = applyEffectiveSkipSilence()
            },
        )
        setUpCast(player)
        mediaSession = MediaSession.Builder(this, currentPlayer ?: player).setCallback(SkipSilenceCallback()).build()
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

    /** Wires a Cast session in if Play Services + a receiver are reachable; otherwise stays fully local. */
    @UnstableApi
    private fun setUpCast(localPlayer: Player) {
        val castContext = runCatching { CastContext.getSharedInstance(this) }.getOrNull() ?: return
        val cast = CastPlayer(castContext)
        cast.setSessionAvailabilityListener(
            object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() = switchTo(cast)
                override fun onCastSessionUnavailable() = switchTo(localPlayer)
            },
        )
        castPlayer = cast
    }

    /** Hands the current item + position to [target] and points the session at it (local ↔ cast). */
    @UnstableApi
    private fun switchTo(target: Player) {
        val previous = currentPlayer ?: return
        if (previous === target) return
        // On disconnect the CastPlayer has already torn its queue down, so its
        // currentMediaItem is null — fall back to the item/position cached when the
        // cast session started, so ending a cast resumes locally instead of dying.
        val item = previous.currentMediaItem ?: lastHandoffItem
        val position = previous.currentMediaItem?.let { previous.currentPosition } ?: lastHandoffPositionMs
        item?.let {
            target.setMediaItem(it, position)
            target.playWhenReady = previous.playWhenReady
            target.playbackParameters = previous.playbackParameters // carry playback speed across the handoff
            target.prepare()
            lastHandoffItem = it
            lastHandoffPositionMs = position
        }
        previous.stop()
        currentPlayer = target
        mediaSession?.player = target
        Log.i("dewidebug", "cast: player -> ${if (target === castPlayer) "cast" else "local"}")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    @UnstableApi
    override fun onDestroy() {
        mediaSession?.release()
        player?.release()
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        mediaSession = null
        player = null
        castPlayer = null
        currentPlayer = null
        super.onDestroy()
    }

    private companion object {
        // Podcast-style transport: small hop back to re-hear, bigger hop forward.
        const val SEEK_BACK_MS = 10_000L
        const val SEEK_FORWARD_MS = 30_000L
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
