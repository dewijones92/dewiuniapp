package com.dewijones92.uniapp.data.rss

import com.dewijones92.uniapp.domain.Chapter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds

/**
 * Parses a Podcasting 2.0 remote chapters document (linked by
 * `<podcast:chapters url=…>`): `{"chapters":[{"startTime":secs,"title":…}]}`.
 * Tolerant — a malformed document or element is skipped, never fatal.
 */
public object PodcastChaptersJson {

    public fun parse(json: String): List<Chapter> {
        val array = runCatching {
            (Json.parseToJsonElement(json) as? JsonObject)?.get("chapters") as? JsonArray
        }.getOrNull() ?: return emptyList()
        return array.mapNotNull { it.toChapterOrNull() }
    }

    private fun JsonElement.toChapterOrNull(): Chapter? {
        val obj = this as? JsonObject ?: return null
        val start = obj["startTime"]?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() && it >= 0 } ?: return null
        val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null } ?: return null
        return Chapter(start.seconds, title)
    }
}
