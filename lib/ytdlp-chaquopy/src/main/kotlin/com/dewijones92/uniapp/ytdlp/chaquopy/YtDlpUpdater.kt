package com.dewijones92.uniapp.ytdlp.chaquopy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * Keeps yt-dlp fresh without an app update. yt-dlp is pure Python, breaks
 * whenever YouTube changes, and ships fixes almost daily; this fetches the
 * latest wheel from PyPI into the cache, and [ChaquopyYtDlpEngine] shadows the
 * bundled copy with it on the next start.
 *
 * Fail-safe by construction: every failure path leaves the current (bundled or
 * previously-downloaded) yt-dlp untouched, and the wheel is only installed
 * after its PyPI-published SHA-256 verifies.
 */
public class YtDlpUpdater internal constructor(
    private val client: OkHttpClient,
    private val cache: YtDlpUpdateCache,
) {
    public constructor(client: OkHttpClient, cacheDir: File) :
        this(client, YtDlpUpdateCache(cacheDir))

    /**
     * Downloads the latest yt-dlp if newer than what's cached. Returns true if a
     * new wheel was installed (takes effect next start), false otherwise. Never
     * throws — a failed check simply keeps the current yt-dlp.
     */
    public suspend fun ensureLatest(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val release = PyPiYtDlp.parse(get(PyPiYtDlp.JSON_URL) ?: return@runCatching false)
                ?: return@runCatching false
            if (release.version == cache.cachedVersion()) return@runCatching false
            val bytes = getBytes(release.wheelUrl) ?: return@runCatching false
            if (sha256(bytes) != release.sha256.lowercase()) return@runCatching false
            cache.install(release.version, bytes)
            true
        }.getOrDefault(false)
    }

    private fun get(url: String): String? =
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.body.string() else null
        }

    private fun getBytes(url: String): ByteArray? =
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.body.bytes() else null
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
