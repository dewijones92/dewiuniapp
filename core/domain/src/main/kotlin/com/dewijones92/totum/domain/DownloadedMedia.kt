package com.dewijones92.totum.domain

/**
 * A finished download: what was fetched, where the bytes are, and which variant.
 *
 * The download record carries the item itself. Before it did, the downloads table held
 * ids alone, so listing what was offline meant joining against one pillar's own
 * catalogue — and the Library could only ever show podcast episodes. A downloaded video
 * was on disk and invisible.
 */
public data class DownloadedMedia(
    public val playable: PlayableItem,
    public val localPath: String,
    public val audioOnly: Boolean,
) {
    public val item: MediaItem get() = playable.item

    /** Which pillar it came from — for labelling. The file may still be audio-only. */
    public val pillar: MediaKind get() = playable.handle.pillar

    /**
     * The same item played from disk rather than the network. A video fetched
     * audio-only is an audio file, so it plays as one.
     */
    public val offline: PlayableItem
        get() = playable.copy(
            handle = if (audioOnly || pillar == MediaKind.PODCAST) {
                PlayHandle.Podcast(localPath)
            } else {
                PlayHandle.LocalVideo(localPath)
            },
        )
}
