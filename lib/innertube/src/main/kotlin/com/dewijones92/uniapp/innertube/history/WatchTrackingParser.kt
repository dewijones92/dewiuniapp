package com.dewijones92.uniapp.innertube.history

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The bits of a `player` response needed to report watch progress: the two
 * stats base URLs. Each already carries `docid`, `ei`, `of`, `vm`, `len`, so
 * reporting only appends `cpn` + position. Shape mirrors what SmartTube uses.
 */
public data class WatchTracking(
    val playbackUrl: String,
    val watchtimeUrl: String,
)

/** Pulls the [WatchTracking] URLs out of a `playbackTracking` block. */
internal object WatchTrackingParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): WatchTracking? {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val tracking = root["playbackTracking"]?.jsonObject ?: return null
        val playback = tracking.baseUrl("videostatsPlaybackUrl") ?: return null
        val watchtime = tracking.baseUrl("videostatsWatchtimeUrl") ?: return null
        return WatchTracking(playback, watchtime)
    }

    private fun JsonObject.baseUrl(key: String): String? =
        this[key]?.jsonObject?.get("baseUrl")?.jsonPrimitive?.contentOrNull
}
