package com.dewijones92.totum.queue

import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.data.download.DownloadManager
import com.dewijones92.totum.data.queue.QueueEntry
import com.dewijones92.totum.data.queue.QueueSnapshot
import com.dewijones92.totum.domain.DownloadState
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.PlayHandle
import com.dewijones92.totum.domain.isPermanent
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
    private val queue: StateFlow<QueueSnapshot>,
    private val downloads: DownloadManager,
    private val scope: CoroutineScope,
    private val isEnabled: () -> Boolean,
    private val isAllowedOnThisNetwork: () -> Boolean,
    private val maxAttempts: Int = MAX_ATTEMPTS,
) {
    /**
     * Transient attempts per item this session, so a flaky connection gets a few more goes
     * and a broken item does not get infinite ones. Not persisted: a fresh launch is a fair
     * reason to try again, and a permanent failure is refused on its reason regardless.
     */
    private val attempts = mutableMapOf<MediaItemId, Int>()

    fun start() {
        scope.launch {
            queue.collect { snapshot ->
                if (!isEnabled() || !isAllowedOnThisNetwork()) return@collect
                val states = downloads.observeDownloads().first()
                snapshot.entries.forEach { entry -> download(entry, states) }
            }
        }
    }

    private suspend fun download(entry: QueueEntry, states: Map<*, DownloadState>) {
        val item = entry.item.item
        val skip = skipReason(entry, states[item.id])
        if (skip != null) {
            // Said ONCE per item, not on every queue change. Three permanently-failed
            // members-only videos repeated their reason on every pass and took 14% of a
            // 387-event report (0.1.229) saying nothing new — and the report buffer is
            // bounded, so noise like that is evidence thrown away.
            if (skip.isNotEmpty() && explained.add(entry.item.item.id)) {
                Diag.log("download", "not fetching \"${item.title}\": $skip")
            }
            return
        }
        // The whole entry, handle included, so the video route gets its watch URL.
        downloads.download(entry.item, audioOnly = true)
    }

    /** Items whose skip reason has already been logged; it does not change between passes. */
    private val explained = mutableSetOf<MediaItemId>()

    /**
     * Null to fetch it; a reason to skip. An empty reason means "ordinary, not worth a line" —
     * already downloaded, nothing to fetch yet — so the trail keeps only the skips that would
     * otherwise be mysterious.
     */
    private fun skipReason(entry: QueueEntry, state: DownloadState?): String? = when {
        // A local file is already the point of this; nothing to fetch.
        entry.item.handle is PlayHandle.LocalVideo -> ""
        state is DownloadState.Downloaded || state is DownloadState.Downloading -> ""
        // Nothing to fetch yet — a feed item whose enclosure hasn't been read. Asking
        // anyway would only record a failure against it.
        entry.item.fetchUrl == null -> ""
        state is DownloadState.Failed -> failureSkip(entry.item.item.id, state)
        else -> null
    }

    /**
     * Whether a previous failure should stop us asking again.
     *
     * This used to fall through and retry on EVERY queue change, so two members-only videos
     * in a 59-item queue were re-attempted on every launch for days — in every diagnostics
     * report sent on 2026-07-28.
     */
    private fun failureSkip(id: MediaItemId, state: DownloadState.Failed): String? = when {
        state.isPermanent -> "asking again cannot help — ${state.reason.take(REASON_CHARS)}"
        else -> {
            val used = attempts.getOrDefault(id, 0)
            if (used >= maxAttempts) {
                "gave up after $used attempts"
            } else {
                attempts[id] = used + 1
                null
            }
        }
    }

    private companion object {
        /** Transient retries per item per session — enough for a blip, not a loop. */
        const val MAX_ATTEMPTS = 3

        /** Extractor errors are long; this is enough to recognise one in the trail. */
        const val REASON_CHARS = 80
    }
}
