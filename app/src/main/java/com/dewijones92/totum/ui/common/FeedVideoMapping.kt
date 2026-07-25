package com.dewijones92.totum.ui.common

import com.dewijones92.totum.data.search.SearchHit
import com.dewijones92.totum.domain.MediaContentKind
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.innertube.feeds.FeedVideo
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

/**
 * Maps a video search hit to a [MediaItem].
 *
 * Search results used to play through the launcher directly, which quietly bypassed the
 * queue — so a tapped search result never joined the spine. Giving a hit the same domain
 * shape as everything else is what lets it go through `PlaybackQueue` like the rest.
 *
 * The id comes from the watch URL rather than a video id, because a hit carries no id of
 * its own; it stays stable for the same video, which is all dedupe and play-state need.
 */
fun SearchHit.Video.toMediaItem(sourceId: SourceId): MediaItem = MediaItem(
    id = MediaItemId(watchUrl.value),
    sourceId = sourceId,
    title = title,
    publishedAt = null,
    publishedText = publishedText,
    duration = durationSeconds?.seconds,
    author = subtitle,
    thumbnailUrl = artworkUrl,
    mediaUrl = watchUrl,
)
