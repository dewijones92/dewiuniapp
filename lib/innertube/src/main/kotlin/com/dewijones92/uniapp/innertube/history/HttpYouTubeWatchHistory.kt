package com.dewijones92.uniapp.innertube.history

import com.dewijones92.uniapp.innertube.auth.AccessToken
import com.dewijones92.uniapp.innertube.auth.AccessTokenResult
import com.dewijones92.uniapp.innertube.auth.YouTubeAccount
import com.dewijones92.uniapp.innertube.browse.InnerTubeClient
import com.dewijones92.uniapp.innertube.browse.InnerTubeResponse
import kotlin.random.Random

/**
 * Reports watch progress via YouTube's `stats/playback` + `stats/watchtime`
 * pings (the mechanism SmartTube uses). For each video it fetches the authed
 * `player` response once to get the tracking URLs — which already carry
 * `docid`, `ei`, `of`, `vm`, `len` — then appends a per-video client playback
 * nonce (`cpn`) and the position. Authenticated with the same TV-OAuth token as
 * every other write, so progress attributes to the account.
 */
public class HttpYouTubeWatchHistory(
    private val account: YouTubeAccount,
    private val innerTube: InnerTubeClient,
    private val newNonce: () -> String = ::randomClientPlaybackNonce,
) : YouTubeWatchHistory {

    private class Session(val tracking: WatchTracking, val cpn: String, var recordCreated: Boolean = false)

    private val sessions = mutableMapOf<String, Session>()

    override suspend fun reportProgress(
        videoId: String,
        positionSec: Float,
        lengthSec: Float,
        finished: Boolean,
    ): WatchHistoryResult = when (val token = account.accessToken()) {
        AccessTokenResult.SignedOut -> WatchHistoryResult.SignedOut
        is AccessTokenResult.Failure -> WatchHistoryResult.Failure(token.detail)
        is AccessTokenResult.Available -> report(videoId, positionSec, lengthSec, finished, token.token)
    }

    private suspend fun report(
        videoId: String,
        positionSec: Float,
        lengthSec: Float,
        finished: Boolean,
        token: AccessToken,
    ): WatchHistoryResult {
        val session = sessions[videoId] ?: startSession(videoId, token)
            ?: return WatchHistoryResult.Failure("no tracking data")

        val position = if (finished) lengthSec else positionSec
        val common = "&ver=2&cpn=${session.cpn}&cmt=$position" + if (finished) "&final=1" else ""

        // The record must exist before watch-time updates land, so open it first.
        if (!session.recordCreated) {
            val opened = ping(session.tracking.playbackUrl + common, token)
            if (opened !is WatchHistoryResult.Success) return opened
            session.recordCreated = true
        }
        return ping(session.tracking.watchtimeUrl + common + "&st=$position&et=$position", token)
    }

    private suspend fun ping(url: String, token: AccessToken): WatchHistoryResult =
        when (val result = innerTube.get(url, token)) {
            is InnerTubeResponse.Success -> WatchHistoryResult.Success
            InnerTubeResponse.Unauthorized -> WatchHistoryResult.SignedOut
            is InnerTubeResponse.Failure -> WatchHistoryResult.Failure(result.detail)
        }

    private suspend fun startSession(videoId: String, token: AccessToken): Session? {
        val body = (innerTube.player(videoId, token) as? InnerTubeResponse.Success)?.body ?: return null
        val tracking = WatchTrackingParser.parse(body) ?: return null
        return Session(tracking, newNonce()).also { sessions[videoId] = it }
    }
}

private const val NONCE_LENGTH = 16
private const val NONCE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

/** A client playback nonce: 16 chars of YouTube's cpn alphabet, like the web player. */
public fun randomClientPlaybackNonce(): String =
    buildString(NONCE_LENGTH) { repeat(NONCE_LENGTH) { append(NONCE_ALPHABET[Random.nextInt(NONCE_ALPHABET.length)]) } }
