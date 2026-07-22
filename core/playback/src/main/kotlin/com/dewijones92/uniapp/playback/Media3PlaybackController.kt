package com.dewijones92.uniapp.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.domain.Chapter
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.MediaKind
import com.dewijones92.uniapp.domain.SkipSegment
import com.dewijones92.uniapp.domain.skipTargetFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import androidx.media3.common.MediaItem as Media3MediaItem

/**
 * [PlaybackController] backed by a [MediaController] connected to
 * [PlaybackService]. Commands issued before the async connection completes
 * are queued and replayed on connect.
 */
// The one adapter binding the app's PlaybackController seam to Media3; its
// method count is the interface surface plus a few small private helpers, and
// collapsing them to satisfy the counter would only duplicate logic.
@Suppress("TooManyFunctions")
public class Media3PlaybackController(
    context: Context,
    private val scope: CoroutineScope,
    private val progressStore: PlaybackProgressStore = NoOpPlaybackProgressStore,
) : PlaybackController {

    private val _state = MutableStateFlow<PlaybackState?>(null)
    override val state: StateFlow<PlaybackState?> = _state

    private var controller: MediaController? = null
    override val player: Player? get() = controller
    private val pendingCommands = mutableListOf<(MediaController) -> Unit>()
    private var activeSkipSegments: List<SkipSegment> = emptyList()
    private var activeChapters: List<Chapter> = emptyList()
    private var skipSilence = false
    private var ticksSinceSave = 0

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val connected = future.get()
                controller = connected
                connected.addListener(
                    object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            if (events.containsAny(
                                    Player.EVENT_VIDEO_SIZE_CHANGED,
                                    Player.EVENT_TRACKS_CHANGED,
                                )
                            ) {
                                Log.i(
                                    "dewidebug",
                                    "video size=${player.videoSize.width}x${player.videoSize.height} " +
                                        "hasVideo=${player.currentTracks.groups.any {
                                            it.type == C.TRACK_TYPE_VIDEO
                                        }}",
                                )
                            }
                            _state.value = connected.currentPlaybackState()
                        }
                    },
                )
                pendingCommands.forEach { it(connected) }
                pendingCommands.clear()
                startPositionTicker(connected)
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    override fun play(
        item: MediaItem,
        kind: MediaKind,
        skipSegments: List<SkipSegment>,
        localPath: String?,
        audioUrl: HttpUrl?,
    ) {
        val uri = localPath?.let { File(it).toURI().toString() }
            ?: requireNotNull(item.mediaUrl) { "MediaItem ${item.id.value} has no mediaUrl" }.value
        activeSkipSegments = skipSegments
        activeChapters = item.chapters
        // A separate audio track (higher-than-muxed qualities) rides along in
        // the request metadata; the service merges it with the video-only URI.
        val requestMetadata = Media3MediaItem.RequestMetadata.Builder()
            .setExtras(audioUrl?.let { bundleOf(EXTRA_AUDIO_URL to it.value) })
            .build()
        val media3Item = Media3MediaItem.Builder()
            .setMediaId(item.id.value)
            .setUri(uri)
            .setRequestMetadata(requestMetadata)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.author)
                    .setDescription(item.description)
                    .setArtworkUri(item.thumbnailUrl?.value?.let(android.net.Uri::parse))
                    // Round-trips the pillar through the session so the UI can label it.
                    .setMediaType(
                        when (kind) {
                            MediaKind.VIDEO -> MediaMetadata.MEDIA_TYPE_VIDEO
                            MediaKind.PODCAST -> MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE
                        },
                    )
                    .build(),
            )
            .build()
        // Resume where this item was left (both pillars). Fetched first so we
        // can hand the start position straight to the player — no jump from 0.
        scope.launch {
            val resumeMs = progressStore.resumePositionMs(item.id) ?: 0L
            ticksSinceSave = 0
            withController { controller ->
                controller.setMediaItem(media3Item, resumeMs)
                controller.prepare()
                controller.play()
            }
        }
    }

    override fun togglePlayPause() {
        withController {
            if (it.isPlaying) {
                it.pause()
                saveProgress(it) // capture where we paused straight away
            } else {
                it.play()
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        withController { controller ->
            val max = controller.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
            controller.seekTo(positionMs.coerceIn(0, max))
            _state.value = controller.currentPlaybackState()
        }
    }

    override fun seekBackward() {
        withController {
            it.seekBack()
            _state.value = it.currentPlaybackState()
        }
    }

    override fun seekForward() {
        withController {
            it.seekForward()
            _state.value = it.currentPlaybackState()
        }
    }

    override fun setSpeed(speed: Float) {
        withController {
            it.setPlaybackSpeed(speed.coerceIn(MIN_SPEED, MAX_SPEED))
            _state.value = it.currentPlaybackState()
        }
    }

    override fun setSkipSilence(enabled: Boolean) {
        skipSilence = enabled
        withController {
            it.sendCustomCommand(
                SessionCommand(ACTION_SKIP_SILENCE, Bundle.EMPTY),
                bundleOf(EXTRA_SKIP_SILENCE_ENABLED to enabled),
            )
            _state.value = it.currentPlaybackState()
        }
    }

    private fun withController(command: (MediaController) -> Unit) {
        controller?.let(command) ?: pendingCommands.add(command)
    }

    private fun startPositionTicker(controller: MediaController) {
        scope.launch {
            while (isActive) {
                if (controller.isPlaying) {
                    applySkipSegments(controller)
                    _state.value = controller.currentPlaybackState()
                    if (++ticksSinceSave >= TICKS_PER_SAVE) {
                        ticksSinceSave = 0
                        saveProgress(controller)
                    }
                }
                delay(POSITION_TICK_MS)
            }
        }
    }

    /** Persists the current item's position so it resumes there next time. */
    private fun saveProgress(controller: MediaController) {
        val id = controller.currentMediaItem?.mediaId ?: return
        val position = controller.currentPosition.coerceAtLeast(0)
        val duration = controller.duration.takeIf { it > 0 }
        scope.launch { progressStore.save(MediaItemId(id), position, duration) }
    }

    /** The one place segment-skipping happens, for every pillar. */
    private fun applySkipSegments(controller: MediaController) {
        val target = activeSkipSegments.skipTargetFor(controller.currentPosition.milliseconds) ?: return
        controller.seekTo(target.inWholeMilliseconds)
    }

    private fun MediaController.currentPlaybackState(): PlaybackState? {
        val current = currentMediaItem ?: return null
        return PlaybackState(
            itemId = MediaItemId(current.mediaId),
            title = current.mediaMetadata.title?.toString().orEmpty(),
            artist = current.mediaMetadata.artist?.toString(),
            artworkUrl = current.mediaMetadata.artworkUri?.toString(),
            description = current.mediaMetadata.description?.toString(),
            kind = if (current.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE) {
                MediaKind.PODCAST
            } else {
                MediaKind.VIDEO
            },
            isPlaying = isPlaying,
            positionMs = currentPosition.coerceAtLeast(0),
            durationMs = duration.takeIf { it > 0 },
            speed = playbackParameters.speed,
            hasVideo = currentTracks.groups.any { it.type == C.TRACK_TYPE_VIDEO },
            videoAspectRatio = videoSize.takeIf { it.width > 0 && it.height > 0 }
                ?.let { it.width * it.pixelWidthHeightRatio / it.height },
            hasEnded = playbackState == Player.STATE_ENDED,
            isBuffering = playbackState == Player.STATE_BUFFERING,
            skipSegments = activeSkipSegments,
            skipSilence = skipSilence,
            chapters = activeChapters,
        )
    }

    private companion object {
        const val POSITION_TICK_MS = 500L
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3.0f

        /** Persist progress every ~5s of playback (10 ticks of 500ms). */
        const val TICKS_PER_SAVE = 10
    }
}
