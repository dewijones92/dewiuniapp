package com.dewijones92.uniapp.ytdlp.chaquopy

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.ytdlp.ChannelResult
import com.dewijones92.uniapp.ytdlp.DownloadEvent
import com.dewijones92.uniapp.ytdlp.DownloadRequest
import com.dewijones92.uniapp.ytdlp.EngineVersions
import com.dewijones92.uniapp.ytdlp.ExtractionResult
import com.dewijones92.uniapp.ytdlp.VideoSearchResult
import com.dewijones92.uniapp.ytdlp.YtDlpEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The real [YtDlpEngine]: yt-dlp running on an embedded CPython runtime
 * (Chaquopy). All Python calls happen off the main thread on [dispatcher].
 */
public class ChaquopyYtDlpEngine(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    updateCacheDir: File? = null,
) : YtDlpEngine {

    private val appContext = context.applicationContext
    private val updateCache = updateCacheDir?.let { YtDlpUpdateCache(it) }

    private val python: Python by lazy {
        if (!Python.isStarted()) Python.start(AndroidPlatform(appContext))
        Python.getInstance()
    }

    private val bridge by lazy {
        // Shadow the bundled yt-dlp with a runtime-downloaded wheel if one is
        // cached, BEFORE uniapp_ytdlp does `import yt_dlp`. A wheel that fails to
        // import is dropped so the bundled copy is used and not retried.
        val wheel = updateCache?.activeWheelPath()
        val used = python.getModule("uniapp_bootstrap").callAttr("activate", wheel).toString()
        if (wheel != null && used != "true") updateCache?.delete(wheel)
        python.getModule("uniapp_ytdlp")
    }

    /** Directory yt-dlp is pointed at for ffmpeg; null if not bundled for this ABI. */
    private val ffmpegLocation: String? by lazy { FfmpegBinary.locationDir(appContext) }

    override suspend fun versions(): EngineVersions = withContext(dispatcher) {
        parseVersions(bridge.callAttr("versions").toString())
    }

    override suspend fun extract(url: HttpUrl): ExtractionResult = withContext(dispatcher) {
        parseExtraction(url, bridge.callAttr("extract", url.value).toString())
    }

    override suspend fun searchVideos(query: String, maxResults: Int): VideoSearchResult =
        withContext(dispatcher) {
            parseSearch(bridge.callAttr("search", query, maxResults).toString())
        }

    override suspend fun fetchChannel(url: HttpUrl, maxVideos: Int): ChannelResult =
        withContext(dispatcher) {
            parseChannel(url, bridge.callAttr("channel", url.value, maxVideos).toString())
        }

    override fun download(request: DownloadRequest): Flow<DownloadEvent> = channelFlow {
        trySend(DownloadEvent.Started(request.url))
        // yt-dlp calls the hook synchronously on the download thread. A plain
        // flow{} would reject emissions from there ("flow invariant violated"),
        // so the channel — safe to send to from any thread — carries them out.
        val listener = object : ProgressListener {
            override fun onProgress(downloadedBytes: Long, totalBytes: Long, etaSeconds: Long) {
                trySend(
                    DownloadEvent.Progress(
                        bytesDownloaded = downloadedBytes,
                        totalBytes = totalBytes.takeIf { it > 0 },
                        etaSeconds = etaSeconds,
                    ),
                )
            }
        }
        val resultJson = bridge.callAttr(
            "download",
            request.url.value,
            request.targetDirectory.absolutePath,
            request.formatId,
            listener,
            ffmpegLocation,
            request.sponsorBlockCategories.joinToString(","),
        ).toString()
        trySend(parseDownloadCompletion(request.url, resultJson) { File(it) })
    }.buffer(Channel.UNLIMITED).flowOn(dispatcher)
}

/** Called from Python (yt-dlp progress hook) via Chaquopy's Java proxying. */
public interface ProgressListener {
    public fun onProgress(downloadedBytes: Long, totalBytes: Long, etaSeconds: Long)
}
