package com.dewijones92.totum.playlist

import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.PlayHandle
import com.dewijones92.totum.domain.PlayableItem

/**
 * A list [MediaItem] as something playable/saveable. The pillar is inferred from the
 * media URL: a YouTube watch URL is a video (kept as its stable watch handle,
 * re-resolved on play); anything else is a podcast enclosure. Null when the item
 * has no playable URL yet.
 */
public fun MediaItem.toPlayableOrNull(): PlayableItem? {
    val url = mediaUrl ?: return null
    val isYouTubeWatch = "youtube.com/watch" in url.value || "youtu.be/" in url.value
    val handle = if (isYouTubeWatch) PlayHandle.Video(url) else PlayHandle.Podcast()
    return PlayableItem(this, handle)
}
