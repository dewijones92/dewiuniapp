package com.dewijones92.totum.database

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.domain.MediaContentKind
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.PlayHandle
import com.dewijones92.totum.domain.PlayableItem
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.domain.persisted
import com.dewijones92.totum.domain.playHandleFrom

/**
 * The denormalized columns a [PlayableItem] persists as — shared by the local-playlist
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

/** The one place the denormalized columns rebuild a [PlayableItem]; null if the handle is unusable. */
internal fun playlistItemFrom(columns: PlaylistItemColumns): PlayableItem? {
    val playback = playHandleFrom(columns.playbackType, columns.handle) ?: return null
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
    return PlayableItem(item, playback)
}

/** The persisted `playbackType` + `handle`; the vocabulary itself lives in the domain. */
internal fun PlayHandle.typeAndHandle(): Pair<String, String?> = persisted()
