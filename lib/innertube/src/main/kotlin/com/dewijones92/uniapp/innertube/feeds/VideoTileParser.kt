package com.dewijones92.uniapp.innertube.feeds

import com.dewijones92.uniapp.common.HttpUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracts videos from any InnerTube TV feed response (home, subscriptions,
 * watch later, history — they share the `tileRenderer` shape). Walks the tree
 * and collects every video tile, so it survives YouTube reshuffling shelves;
 * dedupes by video id, first-seen order preserved. Shape verified against
 * real feeds (2026-07-13).
 */
internal object VideoTileParser {

    private const val VIDEO_CONTENT_TYPE = "TILE_CONTENT_TYPE_VIDEO"
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): FeedResult {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull()
            ?: return FeedResult.Failure("Unparseable feed response")
        val videos = LinkedHashMap<String, FeedVideo>()
        collectVideoTiles(root) { tile ->
            tile.toFeedVideo()?.let { videos.putIfAbsent(it.videoId, it) }
        }
        return FeedResult.Success(videos.values.toList())
    }

    private fun collectVideoTiles(node: JsonElement, onTile: (JsonObject) -> Unit) {
        when (node) {
            is JsonObject -> {
                val tile = node["tileRenderer"] as? JsonObject
                if (tile != null && tile.stringAt("contentType") == VIDEO_CONTENT_TYPE) onTile(tile)
                node.values.forEach { collectVideoTiles(it, onTile) }
            }
            is JsonArray -> node.forEach { collectVideoTiles(it, onTile) }
            else -> Unit
        }
    }

    private fun JsonObject.toFeedVideo(): FeedVideo? {
        val videoId = watchVideoId() ?: stringAt("contentId") ?: return null
        val watchUrl = FeedVideo.watchUrlFor(videoId) ?: return null
        val metadata = (this["metadata"] as? JsonObject)?.get("tileMetadataRenderer") as? JsonObject
        val title = (metadata?.get("title") as? JsonObject)?.stringAt("simpleText") ?: return null
        return FeedVideo(
            videoId = videoId,
            title = title,
            author = metadata.authorLine(),
            durationSeconds = durationSeconds(),
            thumbnailUrl = bestThumbnailUrl(),
            watchUrl = watchUrl,
        )
    }

    private fun JsonObject.watchVideoId(): String? =
        ((this["onSelectCommand"] as? JsonObject)?.get("watchEndpoint") as? JsonObject)?.stringAt("videoId")

    /** First metadata line's text is the channel/author. */
    private fun JsonObject.authorLine(): String? {
        val firstLine = (this["lines"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val items = ((firstLine["lineRenderer"] as? JsonObject)?.get("items") as? JsonArray) ?: return null
        val text = ((items.firstOrNull() as? JsonObject)?.get("lineItemRenderer") as? JsonObject)
            ?.get("text") as? JsonObject ?: return null
        return text.readText()?.ifBlank { null }
    }

    /** Duration lives in the thumbnail's time-status overlay, as "m:ss"/"h:mm:ss". */
    private fun JsonObject.durationSeconds(): Long? {
        val overlays = ((this["header"] as? JsonObject)?.get("tileHeaderRenderer") as? JsonObject)
            ?.get("thumbnailOverlays") as? JsonArray ?: return null
        for (overlay in overlays) {
            val status = (overlay as? JsonObject)?.get("thumbnailOverlayTimeStatusRenderer") as? JsonObject
            val text = (status?.get("text") as? JsonObject)?.readText()
            if (text != null) return parseClock(text)
        }
        return null
    }

    private fun JsonObject.bestThumbnailUrl(): HttpUrl? {
        val thumbnails = ((this["header"] as? JsonObject)?.get("tileHeaderRenderer") as? JsonObject)
            ?.let { it["thumbnail"] as? JsonObject }
            ?.let { it["thumbnails"] as? JsonArray } ?: return null
        return (thumbnails.lastOrNull() as? JsonObject)?.stringAt("url")?.let(HttpUrl::parse)
    }

    /** "s", "m:ss" or "h:mm:ss" → total seconds; each field carries into the next at base 60. */
    private fun parseClock(clock: String): Long? {
        val parts = clock.trim().split(":")
        if (parts.isEmpty() || parts.size > MAX_CLOCK_FIELDS) return null
        return parts.fold(0L) { acc, part -> acc * SECONDS_PER_MINUTE + (part.toLongOrNull() ?: return null) }
    }

    private fun JsonObject.readText(): String? {
        stringAt("simpleText")?.let { return it }
        val runs = this["runs"] as? JsonArray ?: return null
        return runs.joinToString("") { (it as? JsonObject)?.stringAt("text").orEmpty() }
    }

    private fun JsonObject.stringAt(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.ifBlank { null }

    private const val SECONDS_PER_MINUTE = 60L
    private const val MAX_CLOCK_FIELDS = 3
}
