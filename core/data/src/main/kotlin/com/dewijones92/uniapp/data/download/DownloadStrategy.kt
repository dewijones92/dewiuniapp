package com.dewijones92.uniapp.data.download

import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItem
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * How one kind of media becomes a local file. The manager picks a strategy
 * per item so the two pillars' mechanics stay isolated behind one seam.
 * The returned flow is cold and terminates with [DownloadState.Downloaded]
 * or [DownloadState.Failed].
 */
public fun interface DownloadStrategy {
    public fun download(item: MediaItem, target: File): Flow<DownloadState>

    public companion object {
        /** Whether this strategy can handle [item]; the manager routes accordingly. */
        public fun handlesPodcast(item: MediaItem): Boolean = item.mediaUrl != null
    }
}
