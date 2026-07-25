package com.dewijones92.totum.data.download

import com.dewijones92.totum.domain.DownloadState
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import kotlinx.coroutines.flow.Flow

/**
 * The app's single offline-downloads seam. Both pillars go through this:
 * how the bytes are fetched (podcast enclosure over HTTP vs. video via the
 * extraction engine) is chosen inside, behind [DownloadStrategy].
 */
public interface DownloadManager {

    /** Live state of every known download, keyed by item. */
    public fun observeDownloads(): Flow<Map<MediaItemId, DownloadState>>

    /** State of a single item (defaults to NotDownloaded). */
    public fun observe(id: MediaItemId): Flow<DownloadState>

    /**
     * Every state change as it happens, with the item attached. A hot stream of
     * transitions rather than current state, for consumers that react to a download
     * *becoming* something — notifications — instead of rendering what it is.
     */
    public fun events(): Flow<DownloadEvent>

    /**
     * Starts downloading [item]; progress is observable via [observe]. Idempotent —
     * except that an existing **audio-only** download does not satisfy a request for
     * the full media, so asking for video after the queue auto-downloaded the audio
     * re-fetches rather than silently reporting "already downloaded".
     */
    public suspend fun download(item: MediaItem, audioOnly: Boolean = false)

    /** Removes the local file and forgets the download. */
    public suspend fun delete(id: MediaItemId)
}
