package com.dewijones92.uniapp.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.playlist.toPlaylistItemOrNull
import com.dewijones92.uniapp.playlist.toQueuedItem
import com.dewijones92.uniapp.queue.PlaybackQueue

/**
 * The per-row long-press actions shared by every feed, both pillars — queue and
 * playlist wiring lives here once so no screen re-implements it. A feed item's
 * pillar/handle is inferred from its media URL ([toPlaylistItemOrNull]); items
 * without a playable URL yet simply can't be queued (the action no-ops).
 */
class MediaItemActions internal constructor(
    private val queue: PlaybackQueue,
    private val openPlaylistPicker: (MediaItem) -> Unit,
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
}

/** Wires [MediaItemActions] from the container and hosts the add-to-playlist picker dialog. */
@Composable
fun rememberMediaItemActions(container: AppContainer): MediaItemActions {
    val adder = com.dewijones92.uniapp.ui.playlist.rememberPlaylistAdder(container)
    return remember(container, adder) { MediaItemActions(container.playbackQueue, adder) }
}
