package com.dewijones92.uniapp.ytdlp

import com.dewijones92.uniapp.common.HttpUrl
import kotlinx.coroutines.flow.Flow

/**
 * The library's single entry point: a yt-dlp media extraction and download
 * engine.
 *
 * Implementations: a real engine backed by an embedded CPython runtime
 * running yt-dlp (in progress), and [com.dewijones92.uniapp.ytdlp.fake.FakeYtDlpEngine]
 * for tests, previews, and development against the boundary.
 */
public interface YtDlpEngine {

    /** Versions of the moving parts, for diagnostics and update decisions. */
    public suspend fun versions(): EngineVersions

    /**
     * Extracts metadata (title, formats, …) for [url] without downloading.
     * Expected failures are values — see [ExtractionResult.Failure].
     */
    public suspend fun extract(url: HttpUrl): ExtractionResult

    /** Searches for videos (yt-dlp `ytsearch`), returning at most [maxResults] entries. */
    public suspend fun searchVideos(query: String, maxResults: Int): VideoSearchResult

    /**
     * Downloads media described by [request]. The returned flow is cold:
     * collecting starts the download, cancelling the collection cancels it.
     * Terminal events are [DownloadEvent.Completed] and [DownloadEvent.Failed].
     */
    public fun download(request: DownloadRequest): Flow<DownloadEvent>
}

/** Versions of the engine's moving parts. */
public data class EngineVersions(
    val ytDlp: String,
    val python: String,
)
