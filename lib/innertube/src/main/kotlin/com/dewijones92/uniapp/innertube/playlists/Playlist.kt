package com.dewijones92.uniapp.innertube.playlists

import com.dewijones92.uniapp.common.HttpUrl

/**
 * A playlist on the signed-in account. [browseId] is already the "VL"-prefixed
 * browse id that fetches its videos, so it feeds straight back into a browse.
 */
public data class Playlist(
    val browseId: String,
    val title: String,
    /** How YouTube renders the size, e.g. "184 videos"; null if absent. */
    val videoCountText: String?,
    val thumbnailUrl: HttpUrl?,
)
