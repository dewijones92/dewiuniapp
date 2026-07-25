package com.dewijones92.uniapp.playback

import android.app.PendingIntent
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

    /**
     * Detects silence so it can be handled by **rate**, not by dropping samples.
     * Dropping samples shortens the audio but not the video clock, which is why
     * skip-silence used to be audio-only; speeding up retimes both together, so this
     * works on video too and cannot desync.
     */
    @UnstableApi
    private val silenceDetector = SilenceDetectingAudioProcessor { silent ->
        mainHandler.post { onSilenceChanged(silent) }
    }

    /** The user's skip-silences intent. Applies to both pillars now. */
    private var skipSilenceEnabled = false

    /** The speed the user chose, restored when a silent stretch ends. */
    private var userSpeed = 1f

    /** True while we're racing through a silent stretch. */
    private var inSilence = false

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Platform gain for quiet audio; null when off or unavailable on this device. */
    private var loudness: android.media.audiofx.LoudnessEnhancer? = null

    /** Notices the user's own speed changes so a silent stretch restores the right rate. */
    private val speedWatcher = object : Player.Listener {
        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            if (!inSilence) userSpeed = playbackParameters.speed
        }
    }
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
                        // The detector only observes; Sonic does the actual retiming.
                        DefaultAudioSink.DefaultAudioProcessorChain(silenceDetector, SonicAudioProcessor()),
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
        player.addListener(speedWatcher)
        currentPlayer = player
        // When the tracks change (a new item, video vs audio), re-apply the effective
        // skip-silence: off whenever a video track is present, so A/V stays in sync.
        player.addListener(
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) = applyEffectiveSkipSilence()
            },
        )
        setUpCast(player)
        mediaSession = MediaSession.Builder(this, currentPlayer ?: player)
            .setCallback(SkipSilenceCallback())
            .apply { openAppIntent()?.let { setSessionActivity(it) } }
            .build()
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
                        .add(SessionCommand(ACTION_VOLUME_BOOST, Bundle.EMPTY))
                        .build(),
                )
                .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_VOLUME_BOOST) {
                applyVolumeBoost(args.getInt(EXTRA_VOLUME_BOOST_MILLIBELS, 0))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
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
     * Races through a silent stretch by raising the playback rate, and drops back when
     * sound returns. [Player.setPlaybackSpeed] retimes audio *and* video, so this holds
     * for both pillars and cannot pull them apart — the reason this replaced the
     * sample-dropping processor.
     */
    private fun onSilenceChanged(silent: Boolean) {
        val target = player ?: return
        if (!skipSilenceEnabled) {
            if (inSilence) {
                inSilence = false
                target.setPlaybackSpeed(userSpeed)
            }
            return
        }
        if (silent == inSilence) return
        inSilence = silent
        val speed = if (silent) (userSpeed * SILENCE_SPEED_MULTIPLIER).coerceAtMost(MAX_SILENCE_SPEED) else userSpeed
        Log.i("dewidebug", "skip-silence silent=$silent speed=$speed (user=$userSpeed)")
        target.setPlaybackSpeed(speed)
    }

    /**
     * Applies gain to the player's audio session with the platform's own enhancer.
     * A session effect can't reach a Cast receiver, so this is local playback only —
     * accepted rather than half-built.
     */
    @UnstableApi
    private fun applyVolumeBoost(millibels: Int) {
        val sessionId = player?.audioSessionId ?: return
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        // Recreated on change: the enhancer is bound to a session, and a stale one
        // after a session change would silently do nothing.
        runCatching { loudness?.release() }
        loudness = null
        if (millibels <= 0) return
        loudness = runCatching {
            android.media.audiofx.LoudnessEnhancer(sessionId).apply {
                setTargetGain(millibels)
                enabled = true
            }
        }.onFailure { Log.i("dewidebug", "volume boost unavailable: ${it.message}") }.getOrNull()
    }

    /** Turns the user's intent off cleanly, restoring their speed if we were racing. */
    private fun applyEffectiveSkipSilence() {
        if (!skipSilenceEnabled && inSilence) onSilenceChanged(false)
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

    /** Tapping the media notification reopens the app (its launcher activity). */
    private fun openAppIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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

        /** How much faster a silent stretch runs. High enough to feel skipped, low enough to stay smooth. */
        const val SILENCE_SPEED_MULTIPLIER = 4f

        /** Media3 clamps extreme rates and video decoders struggle past this. */
        const val MAX_SILENCE_SPEED = 8f
    }
}

/** Custom session command to toggle silence-skipping; the bool rides in [EXTRA_SKIP_SILENCE_ENABLED]. */
internal const val ACTION_SKIP_SILENCE: String = "com.dewijones92.uniapp.SKIP_SILENCE"
internal const val ACTION_VOLUME_BOOST: String = "com.dewijones92.uniapp.VOLUME_BOOST"
internal const val EXTRA_VOLUME_BOOST_MILLIBELS: String = "gain_millibels"
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
