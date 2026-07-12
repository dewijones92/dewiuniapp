package com.dewijones92.uniapp.ytdlp.fake

import com.dewijones92.uniapp.ytdlp.DownloadEvent
import com.dewijones92.uniapp.ytdlp.DownloadRequest
import com.dewijones92.uniapp.ytdlp.EngineVersions
import com.dewijones92.uniapp.ytdlp.ExtractionResult
import com.dewijones92.uniapp.ytdlp.MediaFormat
import com.dewijones92.uniapp.ytdlp.MediaMetadata
import com.dewijones92.uniapp.ytdlp.MediaUrl
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

    private val mediaByUrl = mutableMapOf<MediaUrl, MediaMetadata>()

    /** Makes [url] extractable, returning canned [metadata]. */
    public fun registerMedia(url: MediaUrl, metadata: MediaMetadata) {
        mediaByUrl[url] = metadata
    }

    override suspend fun versions(): EngineVersions =
        EngineVersions(ytDlp = "fake", python = "fake")

    override suspend fun extract(url: MediaUrl): ExtractionResult =
        mediaByUrl[url]
            ?.let { ExtractionResult.Success(it) }
            ?: ExtractionResult.Failure.UnsupportedUrl(url)

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
                    isAudioOnly = false,
                    fileSizeBytes = 1_000_000,
                ),
                MediaFormat(
                    formatId = "140",
                    container = "m4a",
                    width = null,
                    height = null,
                    isAudioOnly = true,
                    fileSizeBytes = 250_000,
                ),
            ),
        )
    }
}
