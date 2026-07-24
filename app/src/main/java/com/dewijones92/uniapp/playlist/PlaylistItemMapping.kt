package com.dewijones92.uniapp.playlist

import com.dewijones92.uniapp.data.playlist.PlaylistItem
import com.dewijones92.uniapp.data.playlist.PlaylistPlayback
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.queue.QueuedItem

/**
 * A list [MediaItem] as a saveable playlist item. The pillar is inferred from the
 * media URL: a YouTube watch URL is a video (kept as its stable watch handle,
 * re-resolved on play); anything else is a podcast enclosure. Null when the item
 * has no playable URL yet.
 */
public fun MediaItem.toPlaylistItemOrNull(): PlaylistItem? {
    val url = mediaUrl ?: return null
    val isYouTubeWatch = "youtube.com/watch" in url.value || "youtu.be/" in url.value
    val playback = if (isYouTubeWatch) PlaylistPlayback.Video(url) else PlaylistPlayback.Podcast()
    return PlaylistItem(this, playback)
}

/** A saved playlist item as a queue item, so a playlist plays through the queue. */
public fun PlaylistItem.toQueuedItem(): QueuedItem = when (val p = playback) {
    is PlaylistPlayback.Video -> QueuedItem.Video(item, p.watchUrl)
    is PlaylistPlayback.LocalVideo -> QueuedItem.LocalVideo(item, p.localPath)
    is PlaylistPlayback.Podcast -> QueuedItem.Podcast(item, p.localPath)
}
