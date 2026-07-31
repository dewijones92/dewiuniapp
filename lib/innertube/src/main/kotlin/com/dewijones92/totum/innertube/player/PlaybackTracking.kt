package com.dewijones92.totum.innertube.player

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Where a video's watch-progress pings must be sent for YouTube to credit the account.
 *
 * These URLs are **identity-bearing**, which is the whole reason this type exists. The app
 * used to take them from yt-dlp's player response, and yt-dlp extracts unauthenticated — so
 * every ping went to an anonymous session's URL and credited nobody, while still answering
 * HTTP 204. Measured 2026-07-31: watching a video reported five successful pings and left the
 * account's history byte-identical.
 *
 * The same pings against a URL from an AUTHENTICATED `/player` call put the video at the top
 * of the account's history within twenty seconds — verified twice, on two videos, by reading
 * `FEhistory` back before and after. The authenticated response's URL differs by exactly one
 * parameter, `uga`, which is the signed-in signal.
 */
public data class PlaybackTracking(
    /** Opens the record. Absent on some responses, so pings still work without it. */
    public val playbackUrl: String?,
    /** Carries the position updates; without this there is nothing to report to. */
    public val watchtimeUrl: String,
)

/** Reads [PlaybackTracking] out of a `/player` response, or null when it carries none. */
public object PlaybackTrackingParser {

    private val json = Json { ignoreUnknownKeys = true }

    public fun parse(body: String): PlaybackTracking? {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        val tracking = root["playbackTracking"] as? JsonObject ?: return null
        val watchtime = tracking.baseUrlAt("videostatsWatchtimeUrl") ?: return null
        return PlaybackTracking(tracking.baseUrlAt("videostatsPlaybackUrl"), watchtime)
    }

    private fun JsonObject.baseUrlAt(key: String): String? =
        (this[key] as? JsonObject)?.get("baseUrl")?.jsonPrimitive?.contentOrNull?.ifBlank { null }
}
