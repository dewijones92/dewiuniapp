package com.dewijones92.uniapp.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.SkipSegment
import com.dewijones92.uniapp.domain.skipTargetFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import androidx.media3.common.MediaItem as Media3MediaItem

/**
 * [PlaybackController] backed by a [MediaController] connected to
 * [PlaybackService]. Commands issued before the async connection completes
 * are queued and replayed on connect.
 */
public class Media3PlaybackController(
    context: Context,
    private val scope: CoroutineScope,
) : PlaybackController {

    private val _state = MutableStateFlow<PlaybackState?>(null)
    override val state: StateFlow<PlaybackState?> = _state

    private var controller: MediaController? = null
    private val pendingCommands = mutableListOf<(MediaController) -> Unit>()
    private var activeSkipSegments: List<SkipSegment> = emptyList()

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

    override fun play(item: MediaItem, skipSegments: List<SkipSegment>) {
        val url = requireNotNull(item.mediaUrl) { "MediaItem ${item.id.value} has no mediaUrl" }
        activeSkipSegments = skipSegments
        withController { controller ->
            controller.setMediaItem(
                Media3MediaItem.Builder()
                    .setMediaId(item.id.value)
                    .setUri(url.value)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(item.title)
                            .setArtist(item.author)
                            .setArtworkUri(item.thumbnailUrl?.value?.let(android.net.Uri::parse))
                            .build(),
                    )
                    .build(),
            )
            controller.prepare()
            controller.play()
        }
    }

    override fun togglePlayPause() {
        withController { if (it.isPlaying) it.pause() else it.play() }
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
                }
                delay(POSITION_TICK_MS)
            }
        }
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
            isPlaying = isPlaying,
            positionMs = currentPosition.coerceAtLeast(0),
            durationMs = duration.takeIf { it > 0 },
        )
    }

    private companion object {
        const val POSITION_TICK_MS = 500L
    }
}
