package com.dewijones92.uniapp.data.download

import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
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

    /** Starts downloading [item]; progress is observable via [observe]. Idempotent. */
    public suspend fun download(item: MediaItem)

    /** Removes the local file and forgets the download. */
    public suspend fun delete(id: MediaItemId)
}
