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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The real [YtDlpEngine]: yt-dlp running on an embedded CPython runtime
 * (Chaquopy). All Python calls happen off the main thread on [dispatcher].
 */
public class ChaquopyYtDlpEngine(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : YtDlpEngine {

    private val python: Python by lazy {
        if (!Python.isStarted()) Python.start(AndroidPlatform(context.applicationContext))
        Python.getInstance()
    }

    private val bridge by lazy { python.getModule("uniapp_ytdlp") }

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

    override fun download(request: DownloadRequest): Flow<DownloadEvent> = flow {
        emit(DownloadEvent.Started(request.url))
        // yt-dlp's hook fires on the Python thread; hand events to the flow
        // through the collector's channel via runBlocking on this emitter.
        val collector = this
        val listener = object : ProgressListener {
            override fun onProgress(downloadedBytes: Long, totalBytes: Long, etaSeconds: Long) {
                runBlocking {
                    collector.emit(
                        DownloadEvent.Progress(
                            bytesDownloaded = downloadedBytes,
                            totalBytes = totalBytes.takeIf { it > 0 },
                            etaSeconds = etaSeconds,
                        ),
                    )
                }
            }
        }
        val resultJson = bridge.callAttr(
            "download",
            request.url.value,
            request.targetDirectory.absolutePath,
            request.formatId,
            listener,
        ).toString()
        emit(parseDownloadCompletion(request.url, resultJson) { File(it) })
    }.flowOn(dispatcher)
}

/** Called from Python (yt-dlp progress hook) via Chaquopy's Java proxying. */
public interface ProgressListener {
    public fun onProgress(downloadedBytes: Long, totalBytes: Long, etaSeconds: Long)
}
