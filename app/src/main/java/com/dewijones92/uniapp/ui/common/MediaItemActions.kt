package com.dewijones92.uniapp.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.dewijones92.uniapp.data.source.SourceLocator
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.playlist.toPlaylistItemOrNull
import com.dewijones92.uniapp.playlist.toQueuedItem
import com.dewijones92.uniapp.queue.PlaybackQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The per-row long-press actions shared by every feed, both pillars — queue and
 * playlist wiring lives here once so no screen re-implements it. A feed item's
 * pillar/handle is inferred from its media URL ([toPlaylistItemOrNull]); items
 * without a playable URL yet simply can't be queued (the action no-ops).
 */
class MediaItemActions internal constructor(
    private val queue: PlaybackQueue,
    private val openPlaylistPicker: (MediaItem) -> Unit,
    private val locator: SourceLocator,
    private val scope: CoroutineScope,
) {
    fun playNext(item: MediaItem) {
        item.toPlaylistItemOrNull()?.toQueuedItem()?.let(queue::playNext)
    }

    fun addToQueue(item: MediaItem) {
        item.toPlaylistItemOrNull()?.toQueuedItem()?.let(queue::enqueue)
    }

    fun addToPlaylist(item: MediaItem) {
        openPlaylistPicker(item)
    }

    /**
     * Resolves the item's source and hands it to [onResolved] to navigate to.
     * A subscribed podcast feed is a local lookup; a video's channel is resolved
     * through the engine, so this may take a moment. Does nothing when the source
     * can't be determined.
     */
    fun goToSource(item: MediaItem, onResolved: (MediaSource) -> Unit) {
        scope.launch { locator.locate(item)?.let(onResolved) }
    }
}

/** Wires [MediaItemActions] from the container and hosts the add-to-playlist picker dialog. */
@Composable
fun rememberMediaItemActions(container: AppContainer): MediaItemActions {
    val adder = com.dewijones92.uniapp.ui.playlist.rememberPlaylistAdder(container)
    val scope = rememberCoroutineScope()
    return remember(container, adder, scope) {
        MediaItemActions(container.playbackQueue, adder, container.sourceLocator, scope)
    }
}
