package com.dewijones92.uniapp.data.download

import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.ytdlp.DownloadEvent
import com.dewijones92.uniapp.ytdlp.DownloadRequest
import com.dewijones92.uniapp.ytdlp.YtDlpEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * Downloads a video through the yt-dlp engine, which fetches the separate
 * best-quality video and audio streams and merges them with the bundled ffmpeg
 * (a plain HTTP GET of one stream can't do that). Optionally cuts SponsorBlock
 * segments out of the finished file so downloads match playback.
 *
 * yt-dlp picks its own filename and container, so the download lands in a temp
 * directory and the single result is moved onto the manager's [target].
 */
public class EngineDownloadStrategy(
    private val engine: YtDlpEngine,
    private val sponsorBlockCategories: Set<String> = emptySet(),
) : DownloadStrategy {

    override fun download(item: MediaItem, target: File): Flow<DownloadState> = flow {
        val url = item.mediaUrl
        if (url == null) {
            emit(DownloadState.Failed("Nothing to download"))
            return@flow
        }
        val work = File(target.parentFile, "${target.nameWithoutExtension}.part").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            val request = DownloadRequest(
                url = url,
                targetDirectory = work,
                formatId = BEST_MERGED,
                sponsorBlockCategories = sponsorBlockCategories,
            )
            engine.download(request).collect { event ->
                when (event) {
                    is DownloadEvent.Started -> emit(DownloadState.Downloading(0, null))
                    is DownloadEvent.Progress -> emit(progress(event))
                    is DownloadEvent.Completed -> {
                        target.delete()
                        // Same filesystem (temp dir is under target's parent), so a
                        // rename is instant; copy only if the platform refuses it.
                        if (!event.file.renameTo(target)) event.file.copyTo(target, overwrite = true)
                        emit(DownloadState.Downloaded(target.absolutePath))
                    }
                    is DownloadEvent.Failed -> emit(DownloadState.Failed(event.reason.toString()))
                }
            }
        } finally {
            work.deleteRecursively()
        }
    }.flowOn(Dispatchers.IO)

    private fun progress(event: DownloadEvent.Progress): DownloadState.Downloading {
        // yt-dlp's total can lag behind bytes for estimates; drop it rather than
        // trip DownloadState's total >= downloaded invariant.
        val total = event.totalBytes?.takeIf { it >= event.bytesDownloaded }
        return DownloadState.Downloading(event.bytesDownloaded, total)
    }

    public companion object {
        /** Best video + best audio, merged; falls back to the best single stream. */
        private const val BEST_MERGED = "bv*+ba/b"

        private val STREAMING_HOSTS = listOf("youtube.com", "youtu.be")

        /** Whether [item] is a streaming page the engine must resolve+merge (vs a direct file). */
        public fun handles(item: MediaItem): Boolean {
            val url = item.mediaUrl?.value ?: return false
            return STREAMING_HOSTS.any { it in url }
        }
    }
}
