package com.dewijones92.uniapp.innertube.search

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.innertube.feeds.FeedVideo
import com.dewijones92.uniapp.innertube.feeds.parseClockToSeconds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses search results — YouTube's WEB search still answers with the classic
 * `videoRenderer` tile (runs / simpleText), not the newer `lockupViewModel` the
 * channel tabs use, so this is its own shape rather than a reuse of
 * `LockupParser`. Walks the whole tree so section reshuffles don't matter, and
 * dedupes by video id keeping first-seen (relevance) order.
 */
internal object SearchResultsParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun videos(body: String): List<SearchedVideo> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        val out = LinkedHashMap<String, SearchedVideo>()
        collect(root, "videoRenderer") { renderer ->
            renderer.toSearchedVideo()?.let { out.putIfAbsent(it.videoId, it) }
        }
        return out.values.toList()
    }

    private fun collect(node: JsonElement, key: String, onNode: (JsonObject) -> Unit) {
        when (node) {
            is JsonObject -> {
                (node[key] as? JsonObject)?.let(onNode)
                node.values.forEach { collect(it, key, onNode) }
            }
            is JsonArray -> node.forEach { collect(it, key, onNode) }
            else -> Unit
        }
    }

    private fun JsonObject.toSearchedVideo(): SearchedVideo? {
        val videoId = stringAt("videoId") ?: return null
        val watchUrl = FeedVideo.watchUrlFor(videoId) ?: return null
        val title = textAt("title") ?: return null
        return SearchedVideo(
            videoId = videoId,
            title = title,
            author = textAt("ownerText") ?: textAt("shortBylineText"),
            publishedText = textAt("publishedTimeText"),
            durationSeconds = textAt("lengthText")?.let(::parseClockToSeconds),
            thumbnailUrl = bestThumbnailUrl(),
            watchUrl = watchUrl,
        )
    }

    /** A classic text field: either `simpleText` or the concatenation of `runs`. */
    private fun JsonObject.textAt(key: String): String? {
        val field = this[key] as? JsonObject ?: return null
        field.stringAt("simpleText")?.let { return it }
        val runs = field["runs"] as? JsonArray ?: return null
        return runs.mapNotNull { (it as? JsonObject)?.stringAt("text") }
            .joinToString("")
            .ifBlank { null }
    }

    private fun JsonObject.bestThumbnailUrl(): HttpUrl? {
        val thumbnails = (this["thumbnail"] as? JsonObject)?.get("thumbnails") as? JsonArray ?: return null
        return (thumbnails.lastOrNull() as? JsonObject)?.stringAt("url")?.let(HttpUrl::parse)
    }

    private fun JsonObject.stringAt(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
}
