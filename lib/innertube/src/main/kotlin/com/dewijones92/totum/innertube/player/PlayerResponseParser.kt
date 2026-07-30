package com.dewijones92.totum.innertube.player

import com.dewijones92.totum.common.HttpUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Reads a `/player` response into [StreamingData].
 *
 * Keeps formats that have NO url, unlike every URL extractor — that is the whole point.
 * They are what YouTube offers over SABR, and the app needs to know they exist even while
 * it cannot fetch them.
 *
 * Both format lists are read and merged: `formats` holds the legacy muxed streams (itag 18
 * and friends) and `adaptiveFormats` the separate video/audio ones. A video restricted to
 * SABR keeps exactly one entry in the first list and a full ladder, all URL-less, in the
 * second — which is how a 1080p video ends up playing at 360p.
 */
public object PlayerResponseParser {

    private val json = Json { ignoreUnknownKeys = true }

    public fun parse(body: String): PlayerResult {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: return PlayerResult.Failure("Unparseable player response")

        val status = (root["playabilityStatus"] as? JsonObject)
        when (val state = status?.stringAt("status")) {
            null, "OK" -> Unit
            else -> return PlayerResult.Unplayable(
                listOfNotNull(state, status.stringAt("reason")).joinToString(": "),
            )
        }

        val streaming = root["streamingData"] as? JsonObject
            ?: return PlayerResult.Failure("Player response carried no streamingData")
        val formats = listOf("formats", "adaptiveFormats")
            .flatMap { key -> (streaming[key] as? JsonArray).orEmpty() }
            .mapNotNull { (it as? JsonObject)?.toFormat() }

        return PlayerResult.Success(
            StreamingData(
                formats = formats,
                serverAbrStreamingUrl = streaming.stringAt("serverAbrStreamingUrl")?.let(HttpUrl::parse),
            ),
        )
    }

    private fun JsonObject.toFormat(): PlayableFormat? {
        val itag = this["itag"]?.jsonPrimitive?.longOrNull?.toInt() ?: return null
        return PlayableFormat(
            itag = itag,
            mimeType = stringAt("mimeType"),
            height = this["height"]?.jsonPrimitive?.longOrNull?.toInt(),
            bitrate = this["bitrate"]?.jsonPrimitive?.longOrNull,
            url = stringAt("url")?.let(HttpUrl::parse),
        )
    }

    private fun JsonObject.stringAt(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
}
