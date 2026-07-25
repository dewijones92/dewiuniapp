package com.dewijones92.totum.data.download

import com.dewijones92.totum.domain.DownloadState
import com.dewijones92.totum.domain.MediaItem
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * How one kind of media becomes a local file. The manager picks a strategy
 * per item so the two pillars' mechanics stay isolated behind one seam.
 * The returned flow is cold and terminates with [DownloadState.Downloaded]
 * or [DownloadState.Failed].
 */
public fun interface DownloadStrategy {

    /**
     * [audioOnly] asks for just the audio — the queue's automatic downloads use it,
     * since they exist to make listening work offline without paying for video
     * bytes. Strategies whose media is inherently audio (a podcast enclosure) ignore
     * it.
     */
    public fun download(item: MediaItem, target: File, audioOnly: Boolean): Flow<DownloadState>
}
