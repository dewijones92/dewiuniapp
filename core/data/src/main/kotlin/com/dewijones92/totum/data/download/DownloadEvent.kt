package com.dewijones92.totum.data.download

import com.dewijones92.totum.domain.DownloadState
import com.dewijones92.totum.domain.MediaItem

/**
 * A download changing state, carrying the item it belongs to.
 *
 * [DownloadManager.observeDownloads] keys by id only, which is all a row needs — it
 * already has the item. Anything *outside* a list, notifications especially, has an id
 * and no way to turn it into a title, so the event carries the item with it rather than
 * making every consumer find one.
 */
public data class DownloadEvent(
    public val item: MediaItem,
    public val state: DownloadState,
)
