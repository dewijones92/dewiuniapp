package com.dewijones92.totum.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.sabr.SabrSessions
import com.dewijones92.totum.sabr.SabrStream
import com.dewijones92.totum.sabr.SabrTrackKind
import com.dewijones92.totum.sabr.SabrTransport
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Serves `sabr://` URLs from a registered session, and hands everything else upstream.
 *
 * One factory rather than a separate player: Media3 picks a data source per URI, so a
 * SABR-backed track and an ordinary HTTP one can sit in the same playlist — which is what lets
 * this ship without touching the path that already works. A URL with no session behind it
 * falls through to [upstream], where it fails as any bad URL would rather than silently
 * playing nothing.
 */
@UnstableApi
public class SabrDataSourceFactory(
    private val upstream: DataSource.Factory,
) : DataSource.Factory {

    override fun createDataSource(): DataSource = Routing(upstream.createDataSource())

    /**
     * Decides per-URI, at open time, because that is the first moment the URI is known — a
     * factory is asked for a source before anyone says what it is for.
     */
    @UnstableApi
    private class Routing(private val fallback: DataSource) : DataSource by fallback {

        private var delegate: DataSource = fallback

        override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
            delegate = sabrFor(dataSpec.uri.toString()) ?: fallback
            return delegate.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            delegate.read(buffer, offset, length)

        override fun close() = delegate.close()

        override fun getUri() = delegate.uri

        private fun sabrFor(uri: String): DataSource? {
            val (videoId, itag) = SabrSessions.parse(uri) ?: return null
            val session = SabrSessions.of(videoId) ?: run {
                Diag.warn("sabr", "no session for $videoId — falling back, which will fail loudly")
                return null
            }
            val format = listOfNotNull(session.audio, session.video).firstOrNull { it.itag == itag }
                ?: run {
                    Diag.warn("sabr", "session for $videoId has no itag $itag")
                    return null
                }
            val kind = if (format == session.audio) SabrTrackKind.AUDIO else SabrTrackKind.VIDEO
            Diag.log("sabr", "serving $videoId itag $itag as $kind")
            return SabrDataSource(
                SabrStream(
                    url = session.streamingUrl,
                    ustreamerConfig = session.ustreamerConfig,
                    format = format,
                    kind = kind,
                    transport = UrlConnectionTransport,
                ),
            )
        }
    }

    /**
     * The SABR POST, on `HttpURLConnection`.
     *
     * Deliberately not OkHttp: `:core:playback` does not depend on it, and adding a client here
     * to make one POST would widen the module for nothing. The Android UA matters — it is the
     * client whose player response these URLs came from.
     */
    private object UrlConnectionTransport : SabrTransport {
        override suspend fun post(url: String, body: ByteArray): ByteArray {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Content-Type", "application/x-protobuf")
                setRequestProperty("User-Agent", ANDROID_UA)
            }
            return try {
                connection.outputStream.use { it.write(body) }
                connection.inputStream.use { it.readBytes() }
            } catch (e: IOException) {
                Diag.warn("sabr", "request failed", e)
                ByteArray(0)
            } finally {
                connection.disconnect()
            }
        }

        private const val TIMEOUT_MS = 30_000
        private const val ANDROID_UA = "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip"
    }
}
