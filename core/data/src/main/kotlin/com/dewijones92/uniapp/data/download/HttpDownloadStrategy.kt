package com.dewijones92.uniapp.data.download

import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Streams a direct media URL (podcast enclosure) to a file, emitting
 * progress. Used for anything with a ready-to-fetch [MediaItem.mediaUrl].
 */
public class HttpDownloadStrategy(private val client: OkHttpClient) : DownloadStrategy {

    override fun download(item: MediaItem, target: File): Flow<DownloadState> = flow {
        val url = item.mediaUrl
        if (url == null) {
            emit(DownloadState.Failed("Nothing to download"))
            return@flow
        }
        try {
            client.newCall(Request.Builder().url(url.value).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(DownloadState.Failed("HTTP ${response.code}"))
                    return@flow
                }
                val body = response.body
                val total = body.contentLength().takeIf { it > 0 }
                var downloaded = 0L
                var lastEmitted = 0L
                target.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                            downloaded += read
                            if (downloaded - lastEmitted >= EMIT_EVERY_BYTES) {
                                lastEmitted = downloaded
                                emit(DownloadState.Downloading(downloaded, total))
                            }
                        }
                    }
                }
                emit(DownloadState.Downloaded(target.absolutePath))
            }
        } catch (e: IOException) {
            target.delete()
            emit(DownloadState.Failed(e.message ?: "network error"))
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        const val EMIT_EVERY_BYTES = 256 * 1024L
    }
}
