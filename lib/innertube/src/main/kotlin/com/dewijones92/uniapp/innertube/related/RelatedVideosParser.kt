package com.dewijones92.uniapp.innertube.related

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
 * Extracts the related / "up next" videos from a WEB `next` (watch-page)
 * response. YouTube serves these as `lockupViewModel`s — its newer tile
 * component — under the secondary results. Walks the whole tree and collects
 * every video lockup, so it survives YouTube reshuffling the sections; dedupes
 * by video id, first-seen order preserved. Shape verified against a real
 * watch-page response (2026-07-14).
 */
internal object RelatedVideosParser {

    private const val VIDEO_CONTENT_TYPE = "LOCKUP_CONTENT_TYPE_VIDEO"
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): RelatedResult {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull()
            ?: return RelatedResult.Failure("Unparseable watch-page response")
        val videos = LinkedHashMap<String, FeedVideo>()
        collectVideoLockups(root) { lockup ->
            lockup.toFeedVideo()?.let { videos.putIfAbsent(it.videoId, it) }
        }
        return RelatedResult.Success(videos.values.toList())
    }

    private fun collectVideoLockups(node: JsonElement, onLockup: (JsonObject) -> Unit) {
        when (node) {
            is JsonObject -> {
                val lockup = node["lockupViewModel"] as? JsonObject
                if (lockup != null && lockup.stringAt("contentType") == VIDEO_CONTENT_TYPE) onLockup(lockup)
                node.values.forEach { collectVideoLockups(it, onLockup) }
            }
            is JsonArray -> node.forEach { collectVideoLockups(it, onLockup) }
            else -> Unit
        }
    }

    private fun JsonObject.toFeedVideo(): FeedVideo? {
        val videoId = stringAt("contentId") ?: return null
        val watchUrl = FeedVideo.watchUrlFor(videoId) ?: return null
        val metadata = (this["metadata"] as? JsonObject)?.get("lockupMetadataViewModel") as? JsonObject
        val title = (metadata?.get("title") as? JsonObject)?.stringAt("content") ?: return null
        return FeedVideo(
            videoId = videoId,
            title = title,
            author = metadata.authorLine(),
            durationSeconds = durationSeconds(),
            thumbnailUrl = bestThumbnailUrl(),
            watchUrl = watchUrl,
        )
    }

    /** First metadata row's first part is the channel/author line. */
    private fun JsonObject.authorLine(): String? {
        val rows = ((this["metadata"] as? JsonObject)?.get("contentMetadataViewModel") as? JsonObject)
            ?.get("metadataRows") as? JsonArray ?: return null
        for (row in rows) {
            val parts = (row as? JsonObject)?.get("metadataParts") as? JsonArray ?: continue
            val text = ((parts.firstOrNull() as? JsonObject)?.get("text") as? JsonObject)?.stringAt("content")
            if (text != null) return text
        }
        return null
    }

    /** Duration is the thumbnail's bottom-overlay badge text ("m:ss"/"h:mm:ss"). */
    private fun JsonObject.durationSeconds(): Long? {
        val overlays = thumbnailViewModel()?.get("overlays") as? JsonArray ?: return null
        collectBadgeTexts(overlays).forEach { text -> parseClockToSeconds(text)?.let { return it } }
        return null
    }

    private fun collectBadgeTexts(node: JsonElement): List<String> {
        val texts = mutableListOf<String>()
        fun walk(n: JsonElement) {
            when (n) {
                is JsonObject -> {
                    val badge = n["thumbnailBadgeViewModel"] as? JsonObject
                    badge?.stringAt("text")?.let { texts.add(it) }
                    n.values.forEach { walk(it) }
                }
                is JsonArray -> n.forEach { walk(it) }
                else -> Unit
            }
        }
        walk(node)
        return texts
    }

    private fun JsonObject.bestThumbnailUrl(): HttpUrl? {
        val sources = (thumbnailViewModel()?.get("image") as? JsonObject)?.get("sources") as? JsonArray
            ?: return null
        return (sources.lastOrNull() as? JsonObject)?.stringAt("url")?.let(HttpUrl::parse)
    }

    private fun JsonObject.thumbnailViewModel(): JsonObject? =
        (this["contentImage"] as? JsonObject)?.get("thumbnailViewModel") as? JsonObject

    private fun JsonObject.stringAt(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
}
