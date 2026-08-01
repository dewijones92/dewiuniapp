package com.dewijones92.totum.data.torrent

import com.dewijones92.totum.common.HttpUrl

/**
 * The home server's torrent facilities: search one thing, stream another.
 *
 * A port rather than a concrete client because the two halves live at different hosts (Prowlarr
 * and TorrServer), both behind the same Google gate, and because a fake makes the whole feature
 * testable without a Pi, a swarm or a network.
 *
 * The shape deliberately hides that torrents are involved at all beyond this boundary. [stream]
 * returns a plain HTTP URL with range support, which is exactly what the app already plays — so
 * `MediaItem.mediaUrl` takes it unchanged and there is no new playback path, no new download
 * strategy, and nothing about pieces or peers anywhere in the app.
 */
public interface HomeTorrentServer {

    /** Results ordered by the caller's preference, or a failure that says why. */
    public suspend fun search(query: String): TorrentSearchResult

    /**
     * Registers [magnet] with the server and returns what it can play.
     *
     * Separate from [stream] because adding is the slow part — the server has to reach the
     * swarm and read the metadata — and because doing it EARLY is what makes playback feel
     * quick. Calling this when a result is opened, rather than when play is pressed, means
     * pieces are already arriving by the time there is something to play.
     */
    public suspend fun prepare(magnet: String): PreparedTorrent?

    /**
     * A directly-playable URL for one file inside a prepared torrent.
     *
     * Plain HTTP with range requests: seeking anywhere works because the server fetches the
     * pieces at whatever offset is asked for. Measured 2026-08-01, a seek to the middle of a
     * 1.74GB film was served in 3.3s against 3.6s for the start of the same file.
     */
    public fun stream(torrent: PreparedTorrent, file: TorrentFile): HttpUrl
}

public sealed interface TorrentSearchResult {
    public data class Success(public val results: List<TorrentResult>) : TorrentSearchResult

    /** Named rather than an empty list: "nothing found" and "the server is down" differ. */
    public data class Failure(public val detail: String) : TorrentSearchResult
}

public data class TorrentResult(
    public val title: String,
    public val magnet: String,
    public val seeders: Int,
    public val sizeBytes: Long,
    public val indexer: String?,
)

/**
 * A torrent the server has read the metadata for, and the files inside it.
 *
 * [files] matters because a season pack is a folder of episodes rather than one playable thing;
 * without it, "play" would have to guess which of twenty-four files was meant.
 */
public data class PreparedTorrent(
    public val hash: String,
    public val name: String,
    public val files: List<TorrentFile>,
) {
    /**
     * The file most likely to be the thing wanted: the largest playable one.
     *
     * Size is a blunt rule that happens to be reliable — a release folder pairs one large video
     * with samples, subtitles and a NFO, and the feature is the video every time. Only used
     * when there is no reason to ask; a multi-episode pack should offer a choice instead.
     */
    public val mainFile: TorrentFile?
        get() = files.filter { it.isPlayable }.maxByOrNull { it.sizeBytes }
}

public data class TorrentFile(
    public val index: Int,
    public val path: String,
    public val sizeBytes: Long,
) {
    /** The display name, without the release folder that prefixes every path. */
    public val name: String get() = path.substringAfterLast('/')

    /**
     * Whether this is worth offering as something to play.
     *
     * Extension-based, because the server reports paths and lengths and nothing else. Samples
     * are excluded by name: a release folder routinely carries a 30-second "sample.mkv" that is
     * a perfectly valid video and never the thing anyone wants.
     */
    public val isPlayable: Boolean
        get() = PLAYABLE_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) } &&
            !name.contains("sample", ignoreCase = true)

    private companion object {
        val PLAYABLE_EXTENSIONS = listOf(".mkv", ".mp4", ".avi", ".m4v", ".mov", ".webm", ".mp3", ".m4a", ".flac")
    }
}
