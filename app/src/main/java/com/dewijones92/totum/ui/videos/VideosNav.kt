package com.dewijones92.totum.ui.videos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.domain.MediaSource
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.innertube.playlists.Playlist

/**
 * Which overlay the Videos tab has open over its feed.
 *
 * Saveable, and that is the whole point. This was held in a plain `remember`, so being three
 * screens deep — a channel, the playlists list, a playlist — lasted exactly as long as you
 * stayed on the tab. Glance at the queue and you were back at the feed. The same bug was
 * fixed in Podcasts, Search and Library and missed here, which is a good argument for the
 * shell's state holder not being the end of the job: it restores what is *saveable*, and a
 * holder full of `mutableStateOf` is not.
 *
 * Order matters where it is read: a tapped playlist wins over the list that opened it, so
 * backing out returns to the list rather than the feed.
 */
internal class VideosNav {
    var channel by mutableStateOf<MediaSource.VideoChannel?>(null)
    var playlist by mutableStateOf<Playlist?>(null)
    var showPlaylists by mutableStateOf(false)
    var showNotifications by mutableStateOf(false)

    val overlayShowing: Boolean
        get() = channel != null || playlist != null || showPlaylists || showNotifications

    /** For the place trail — which overlay was open, in one short string. */
    fun describe(): String = when {
        playlist != null -> "playlist=${playlist?.title}"
        channel != null -> "channel=${channel?.title}"
        showPlaylists -> "playlists"
        showNotifications -> "notifications"
        else -> "feed"
    }

    companion object {
        /**
         * Flat, fixed-width encoding with [NONE] for absent values, so a missing thumbnail
         * cannot shift the playlist title into the channel's slot.
         */
        val Saver: Saver<VideosNav, Any> = Saver(
            save = { nav ->
                listOf(
                    nav.channel?.id?.value ?: NONE,
                    nav.channel?.title ?: NONE,
                    nav.channel?.channelUrl?.value ?: NONE,
                    nav.playlist?.browseId ?: NONE,
                    nav.playlist?.title ?: NONE,
                    nav.playlist?.videoCountText ?: NONE,
                    nav.playlist?.thumbnailUrl?.value ?: NONE,
                    nav.showPlaylists.toString(),
                    nav.showNotifications.toString(),
                )
            },
            restore = { saved -> (saved as? List<*>)?.filterIsInstance<String>()?.let(::restore) },
        )

        private fun restore(saved: List<String>): VideosNav = VideosNav().apply {
            val channelUrl = saved.getOrNull(CHANNEL_URL)?.takeIf { it != NONE }?.let(HttpUrl::parse)
            if (channelUrl != null) {
                channel = MediaSource.VideoChannel(
                    id = SourceId(saved.getOrEmpty(CHANNEL_ID)),
                    title = saved.getOrEmpty(CHANNEL_TITLE),
                    channelUrl = channelUrl,
                )
            }
            val browseId = saved.getOrNull(PLAYLIST_ID)?.takeIf { it != NONE }
            if (browseId != null) {
                playlist = Playlist(
                    browseId = browseId,
                    title = saved.getOrEmpty(PLAYLIST_TITLE),
                    videoCountText = saved.getOrNull(PLAYLIST_COUNT)?.takeIf { it != NONE },
                    thumbnailUrl = saved.getOrNull(PLAYLIST_THUMB)?.takeIf { it != NONE }?.let(HttpUrl::parse),
                )
            }
            showPlaylists = saved.getOrNull(SHOW_PLAYLISTS).toBoolean()
            showNotifications = saved.getOrNull(SHOW_NOTIFICATIONS).toBoolean()
        }

        private fun List<String>.getOrEmpty(index: Int): String = getOrNull(index).orEmpty()

        private const val NONE = ""
        private const val CHANNEL_ID = 0
        private const val CHANNEL_TITLE = 1
        private const val CHANNEL_URL = 2
        private const val PLAYLIST_ID = 3
        private const val PLAYLIST_TITLE = 4
        private const val PLAYLIST_COUNT = 5
        private const val PLAYLIST_THUMB = 6
        private const val SHOW_PLAYLISTS = 7
        private const val SHOW_NOTIFICATIONS = 8
    }
}
