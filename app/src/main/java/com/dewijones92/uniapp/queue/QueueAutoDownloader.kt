package com.dewijones92.uniapp.queue

import com.dewijones92.uniapp.data.download.DownloadManager
import com.dewijones92.uniapp.data.queue.QueueEntry
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.PlayHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fetches the **audio** of everything in the queue, so the queue is listenable
 * offline without being asked. Videos take their audio-only stream (small and
 * quick); podcasts take their enclosure, which is already audio.
 *
 * Sequential on purpose — the whole queue is downloaded, and a long queue firing
 * every download at once would saturate the connection and starve playback of
 * bandwidth. Nothing is ever deleted automatically: leaving the queue keeps the
 * file, and it is removed from Library like any other download.
 */
class QueueAutoDownloader(
    private val queue: StateFlow<List<QueueEntry>>,
    private val downloads: DownloadManager,
    private val scope: CoroutineScope,
    private val isEnabled: () -> Boolean,
    private val isAllowedOnThisNetwork: () -> Boolean,
) {
    fun start() {
        scope.launch {
            queue.collect { entries ->
                if (!isEnabled() || !isAllowedOnThisNetwork()) return@collect
                val states = downloads.observeDownloads().first()
                entries.forEach { entry -> download(entry, states) }
            }
        }
    }

    private suspend fun download(entry: QueueEntry, states: Map<*, DownloadState>) {
        val item = entry.item.item
        // A local file is already the point of this; nothing to fetch.
        if (entry.item.handle is PlayHandle.LocalVideo) return
        when (states[item.id]) {
            is DownloadState.Downloaded, is DownloadState.Downloading -> return
            else -> Unit
        }
        // Streaming URLs expire, so an item with nothing fetchable yet is skipped;
        // it will be picked up on a later queue change once it has a media URL.
        if (item.mediaUrl == null) return
        downloads.download(item, audioOnly = true)
    }
}
