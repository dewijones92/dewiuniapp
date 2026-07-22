package com.dewijones92.uniapp.ui.common

import com.dewijones92.uniapp.domain.MediaContentKind
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.innertube.feeds.FeedVideo
import kotlin.time.Duration.Companion.seconds

/**
 * Maps an InnerTube [FeedVideo] to the domain [MediaItem] every list and the
 * player use. One mapper for every YouTube list (feeds, playlists), so tags,
 * dates and the watch handle carry through identically. [watchUrl] is the stable
 * handle resolved to a stream on play.
 */
fun FeedVideo.toMediaItem(sourceId: SourceId): MediaItem = MediaItem(
    id = MediaItemId(videoId),
    sourceId = sourceId,
    title = title,
    publishedAt = null,
    publishedText = publishedText,
    duration = durationSeconds?.seconds,
    author = author,
    thumbnailUrl = thumbnailUrl,
    mediaUrl = watchUrl,
    contentKind = when (kind) {
        FeedVideo.Kind.VIDEO -> MediaContentKind.STANDARD
        FeedVideo.Kind.LIVE -> MediaContentKind.LIVE
        FeedVideo.Kind.SHORT -> MediaContentKind.SHORT
    },
)
