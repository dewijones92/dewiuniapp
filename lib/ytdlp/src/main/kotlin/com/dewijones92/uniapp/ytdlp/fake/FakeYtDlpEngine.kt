package com.dewijones92.uniapp.ytdlp.fake

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.ytdlp.DownloadEvent
import com.dewijones92.uniapp.ytdlp.DownloadRequest
import com.dewijones92.uniapp.ytdlp.EngineVersions
import com.dewijones92.uniapp.ytdlp.ExtractionResult
import com.dewijones92.uniapp.ytdlp.MediaFormat
import com.dewijones92.uniapp.ytdlp.MediaMetadata
import com.dewijones92.uniapp.ytdlp.VideoSearchEntry
import com.dewijones92.uniapp.ytdlp.VideoSearchResult
import com.dewijones92.uniapp.ytdlp.YtDlpEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * An in-memory [YtDlpEngine] for tests, Compose previews, and development
 * until the embedded-CPython engine lands. Behaviour is deterministic:
 * URLs registered via [registerMedia] extract successfully; everything else
 * is [ExtractionResult.Failure.UnsupportedUrl].
 */
public class FakeYtDlpEngine : YtDlpEngine {

    private val mediaByUrl = mutableMapOf<HttpUrl, MediaMetadata>()
    private val searchResults = mutableMapOf<String, List<VideoSearchEntry>>()

    /** Makes [url] extractable, returning canned [metadata]. */
    public fun registerMedia(url: HttpUrl, metadata: MediaMetadata) {
        mediaByUrl[url] = metadata
    }

    /** Makes [query] return canned [entries]; unregistered queries return no hits. */
    public fun registerSearch(query: String, entries: List<VideoSearchEntry>) {
        searchResults[query] = entries
    }

    override suspend fun versions(): EngineVersions =
        EngineVersions(ytDlp = "fake", python = "fake")

    override suspend fun extract(url: HttpUrl): ExtractionResult =
        mediaByUrl[url]
            ?.let { ExtractionResult.Success(it) }
            ?: ExtractionResult.Failure.UnsupportedUrl(url)

    override suspend fun searchVideos(query: String, maxResults: Int): VideoSearchResult =
        VideoSearchResult.Success(searchResults[query].orEmpty().take(maxResults))

    override fun download(request: DownloadRequest): Flow<DownloadEvent> = flow {
        emit(DownloadEvent.Started(request.url))
        val metadata = mediaByUrl[request.url]
        if (metadata == null) {
            emit(DownloadEvent.Failed(ExtractionResult.Failure.UnsupportedUrl(request.url)))
            return@flow
        }
        val totalBytes = TOTAL_FAKE_BYTES
        var downloaded = 0L
        while (downloaded < totalBytes) {
            downloaded = (downloaded + totalBytes / PROGRESS_STEPS).coerceAtMost(totalBytes)
            emit(DownloadEvent.Progress(downloaded, totalBytes, etaSeconds = 0))
        }
        emit(DownloadEvent.Completed(request.targetDirectory.resolve("${metadata.id}.mp4")))
    }

    public companion object {
        private const val TOTAL_FAKE_BYTES = 1_000_000L
        private const val PROGRESS_STEPS = 4

        /** A ready-made metadata sample for previews and tests. */
        public fun sampleMetadata(id: String = "sample-1"): MediaMetadata = MediaMetadata(
            id = id,
            title = "Sample video",
            uploader = "Sample channel",
            durationSeconds = 90,
            thumbnailUrl = null,
            formats = listOf(
                MediaFormat(
                    formatId = "22",
                    container = "mp4",
                    width = 1280,
                    height = 720,
                    hasVideo = true,
                    hasAudio = true,
                    fileSizeBytes = 1_000_000,
                    url = "https://cdn.example.com/$id.mp4",
                ),
                MediaFormat(
                    formatId = "140",
                    container = "m4a",
                    width = null,
                    height = null,
                    hasVideo = false,
                    hasAudio = true,
                    fileSizeBytes = 250_000,
                    url = "https://cdn.example.com/$id.m4a",
                ),
            ),
        )

        /** A ready-made search entry for previews and tests. */
        public fun sampleSearchEntry(id: String = "vid-1", title: String = "Sample result"): VideoSearchEntry =
            VideoSearchEntry(
                id = id,
                title = title,
                uploader = "Sample channel",
                durationSeconds = 90,
                watchUrl = HttpUrl.of("https://example.com/watch?v=$id"),
                thumbnailUrl = null,
            )
    }
}
