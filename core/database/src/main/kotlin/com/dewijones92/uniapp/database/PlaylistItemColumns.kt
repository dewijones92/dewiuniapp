package com.dewijones92.uniapp.database

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.playlist.PlaylistItem
import com.dewijones92.uniapp.data.playlist.PlaylistPlayback
import com.dewijones92.uniapp.domain.MediaContentKind
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.SourceId

/**
 * The denormalized columns a [PlaylistItem] persists as — shared by the local-playlist
 * and play-history entities so [playlistItemFrom] maps them one way for both (DRY).
 * A video keeps its watch URL as the handle; a podcast its enclosure in mediaUrl.
 */
internal interface PlaylistItemColumns {
    val itemId: String
    val sourceId: String
    val title: String
    val author: String?
    val thumbnailUrl: String?
    val contentKind: String
    val playbackType: String
    val handle: String?
    val mediaUrl: String?
}

/** The one place the denormalized columns rebuild a [PlaylistItem]; null if the handle is unusable. */
internal fun playlistItemFrom(columns: PlaylistItemColumns): PlaylistItem? {
    val playback = when (columns.playbackType) {
        "VIDEO" -> PlaylistPlayback.Video(HttpUrl.parse(columns.handle ?: return null) ?: return null)
        "LOCAL_VIDEO" -> PlaylistPlayback.LocalVideo(columns.handle ?: return null)
        else -> PlaylistPlayback.Podcast(localPath = columns.handle)
    }
    val item = MediaItem(
        id = MediaItemId(columns.itemId),
        sourceId = SourceId(columns.sourceId),
        title = columns.title,
        publishedAt = null,
        duration = null,
        author = columns.author,
        thumbnailUrl = columns.thumbnailUrl?.let(HttpUrl::parse),
        mediaUrl = columns.mediaUrl?.let(HttpUrl::parse),
        contentKind = runCatching { MediaContentKind.valueOf(columns.contentKind) }
            .getOrDefault(MediaContentKind.STANDARD),
    )
    return PlaylistItem(item, playback)
}

/** The persisted `playbackType` + `handle` for a [PlaylistPlayback]. */
internal fun PlaylistPlayback.typeAndHandle(): Pair<String, String?> = when (this) {
    is PlaylistPlayback.Video -> "VIDEO" to watchUrl.value
    is PlaylistPlayback.LocalVideo -> "LOCAL_VIDEO" to localPath
    is PlaylistPlayback.Podcast -> "PODCAST" to localPath
}
