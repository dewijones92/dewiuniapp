package com.dewijones92.uniapp.innertube.history

import com.dewijones92.uniapp.innertube.auth.AccessToken
import com.dewijones92.uniapp.innertube.auth.AccessTokenResult
import com.dewijones92.uniapp.innertube.auth.YouTubeAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.random.Random

/**
 * Pings YouTube's `stats/playback` + `stats/watchtime` (the mechanism SmartTube
 * uses) to sync watch-progress to the account. The tracking base URLs — which
 * already carry `docid`, `ei`, `of`, `vm`, `len` — come from the extractor's
 * player response via [beginSession]; here we add a per-video client playback
 * nonce (`cpn`) and the position, and authenticate the ping with the account's
 * TV-OAuth token so progress attributes to the account.
 */
public class HttpYouTubeWatchHistory(
    private val account: YouTubeAccount,
    private val client: OkHttpClient,
    private val newNonce: () -> String = ::randomClientPlaybackNonce,
) : YouTubeWatchHistory {

    private class Session(
        val playbackUrl: String?,
        val watchtimeUrl: String,
        val cpn: String,
        var recordCreated: Boolean = false,
    )

    private val sessions = mutableMapOf<String, Session>()

    override fun beginSession(videoId: String, playbackUrl: String?, watchtimeUrl: String?) {
        if (watchtimeUrl == null) return
        // Keep an existing session (and its cpn) if we already have one for this video.
        if (sessions[videoId] == null) {
            sessions[videoId] = Session(playbackUrl, watchtimeUrl, newNonce())
        }
    }

    override suspend fun reportProgress(
        videoId: String,
        positionSec: Float,
        lengthSec: Float,
        finished: Boolean,
    ): WatchHistoryResult {
        val session = sessions[videoId] ?: return WatchHistoryResult.NoSession
        return when (val token = account.accessToken()) {
            AccessTokenResult.SignedOut -> WatchHistoryResult.SignedOut
            is AccessTokenResult.Failure -> WatchHistoryResult.Failure(token.detail)
            is AccessTokenResult.Available -> report(session, positionSec, lengthSec, finished, token.token)
        }
    }

    private suspend fun report(
        session: Session,
        positionSec: Float,
        lengthSec: Float,
        finished: Boolean,
        token: AccessToken,
    ): WatchHistoryResult {
        val position = if (finished) lengthSec else positionSec
        val common = "&ver=2&cpn=${session.cpn}&cmt=$position" + if (finished) "&final=1" else ""

        // Open the record before watch-time updates land (SmartTube does the same).
        if (!session.recordCreated && session.playbackUrl != null) {
            val opened = ping(session.playbackUrl + common, token)
            if (opened != WatchHistoryResult.Success) return opened
            session.recordCreated = true
        }
        return ping(session.watchtimeUrl + common + "&st=$position&et=$position", token)
    }

    private suspend fun ping(url: String, token: AccessToken): WatchHistoryResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer ${token.value}")
                .get()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> WatchHistoryResult.Success
                        response.code == HTTP_UNAUTHORIZED || response.code == HTTP_FORBIDDEN ->
                            WatchHistoryResult.SignedOut
                        else -> WatchHistoryResult.Failure("HTTP ${response.code}")
                    }
                }
            } catch (e: IOException) {
                WatchHistoryResult.Failure(e.message ?: "network error")
            }
        }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
    }
}

private const val NONCE_LENGTH = 16
private const val NONCE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

/** A client playback nonce: 16 chars of YouTube's cpn alphabet, like the web player. */
public fun randomClientPlaybackNonce(): String =
    buildString(NONCE_LENGTH) { repeat(NONCE_LENGTH) { append(NONCE_ALPHABET[Random.nextInt(NONCE_ALPHABET.length)]) } }
