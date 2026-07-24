package com.dewijones92.uniapp.innertube.feeds

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.innertube.playlists.Playlist
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Shared parser for YouTube's newer `lockupViewModel` tile shapes, used by both
 * the related list and channel tabs (one copy of the shape knowledge — DRY).
 * Walks the whole response tree collecting tiles, so it survives YouTube
 * reshuffling sections; dedupes by id, first-seen order preserved.
 *
 * - [videos] — `lockupViewModel` of `LOCKUP_CONTENT_TYPE_VIDEO`/`SHORTS`.
 * - [shorts] — `shortsLockupViewModel` (the Shorts-shelf tile).
 * - [playlists] — `lockupViewModel` of `LOCKUP_CONTENT_TYPE_PLAYLIST`.
 */
// A parser is naturally many small field-extractors; splitting it would scatter
// the one place that knows YouTube's lockup shape (the point of the class).
@Suppress("TooManyFunctions")
internal object LockupParser {

    private const val VIDEO_CONTENT_TYPE = "LOCKUP_CONTENT_TYPE_VIDEO"
    private const val SHORTS_CONTENT_TYPE = "LOCKUP_CONTENT_TYPE_SHORTS"
    private const val PLAYLIST_CONTENT_TYPE = "LOCKUP_CONTENT_TYPE_PLAYLIST"
    private val VIDEO_CONTENT_TYPES = setOf(VIDEO_CONTENT_TYPE, SHORTS_CONTENT_TYPE)
    private const val LIVE_BADGE_STYLE = "THUMBNAIL_OVERLAY_BADGE_STYLE_LIVE"
    private val json = Json { ignoreUnknownKeys = true }

    fun videos(body: String): List<FeedVideo> {
        val root = parseOrNull(body) ?: return emptyList()
        val out = LinkedHashMap<String, FeedVideo>()
        collect(root, "lockupViewModel") { lockup ->
            if (lockup.stringAt("contentType") in VIDEO_CONTENT_TYPES) {
                lockup.toFeedVideo()?.let { out.putIfAbsent(it.videoId, it) }
            }
        }
        return out.values.toList()
    }

    fun shorts(body: String): List<FeedVideo> {
        val root = parseOrNull(body) ?: return emptyList()
        val out = LinkedHashMap<String, FeedVideo>()
        collect(root, "shortsLockupViewModel") { short ->
            short.toShort()?.let { out.putIfAbsent(it.videoId, it) }
        }
        return out.values.toList()
    }

    fun playlists(body: String): List<Playlist> {
        val root = parseOrNull(body) ?: return emptyList()
        val out = LinkedHashMap<String, Playlist>()
        collect(root, "lockupViewModel") { lockup ->
            if (lockup.stringAt("contentType") == PLAYLIST_CONTENT_TYPE) {
                lockup.toPlaylist()?.let { out.putIfAbsent(it.browseId, it) }
            }
        }
        return out.values.toList()
    }

    private fun parseOrNull(body: String): JsonElement? = runCatching { json.parseToJsonElement(body) }.getOrNull()

    /** Walks the tree, invoking [onNode] for every object found under [key]. */
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

    private fun JsonObject.toFeedVideo(): FeedVideo? {
        val videoId = stringAt("contentId") ?: return null
        val watchUrl = FeedVideo.watchUrlFor(videoId) ?: return null
        val metadata = lockupMetadata()
        val title = (metadata?.get("title") as? JsonObject)?.stringAt("content") ?: return null
        return FeedVideo(
            videoId = videoId,
            title = title,
            author = metadata.authorLine(),
            durationSeconds = durationSeconds(),
            thumbnailUrl = bestThumbnailUrl(),
            watchUrl = watchUrl,
            kind = when {
                isLive() -> FeedVideo.Kind.LIVE
                stringAt("contentType") == SHORTS_CONTENT_TYPE -> FeedVideo.Kind.SHORT
                else -> FeedVideo.Kind.VIDEO
            },
            publishedText = metadata.publishedText(),
        )
    }

    private fun JsonObject.toShort(): FeedVideo? {
        val videoId = reelVideoId() ?: return null
        val watchUrl = FeedVideo.watchUrlFor(videoId) ?: return null
        val overlay = this["overlayMetadata"] as? JsonObject
        val title = (overlay?.get("primaryText") as? JsonObject)?.stringAt("content") ?: return null
        return FeedVideo(
            videoId = videoId,
            title = title,
            author = null,
            durationSeconds = null,
            thumbnailUrl = (this["thumbnailViewModel"] as? JsonObject)?.bestThumbnailUrlFromImage(),
            watchUrl = watchUrl,
            kind = FeedVideo.Kind.SHORT,
            publishedText = null,
        )
    }

    private fun JsonObject.toPlaylist(): Playlist? {
        val playlistId = stringAt("contentId") ?: return null
        val metadata = lockupMetadata()
        val title = (metadata?.get("title") as? JsonObject)?.stringAt("content") ?: return null
        return Playlist(
            // "VL" + id is the browse id that fetches the playlist's videos.
            browseId = "VL$playlistId",
            title = title,
            videoCountText = metadata.firstMetadataText(),
            thumbnailUrl = bestThumbnailUrl(),
        )
    }

    private fun JsonObject.reelVideoId(): String? {
        var found: String? = null
        collect(this, "reelWatchEndpoint") { ep -> if (found == null) found = ep.stringAt("videoId") }
        return found
    }

    private fun JsonObject.lockupMetadata(): JsonObject? =
        (this["metadata"] as? JsonObject)?.get("lockupMetadataViewModel") as? JsonObject

    /** The metadata part YouTube uses for the published date (e.g. "2 days ago"). */
    private fun JsonObject.publishedText(): String? {
        forEachMetadataPart { text -> if (text.looksLikePublished()) return text }
        return null
    }

    /** First metadata row's first part is the channel/author line. */
    private fun JsonObject.authorLine(): String? {
        val rows = metadataRows() ?: return null
        for (row in rows) {
            val parts = (row as? JsonObject)?.get("metadataParts") as? JsonArray ?: continue
            val text = ((parts.firstOrNull() as? JsonObject)?.get("text") as? JsonObject)?.stringAt("content")
            if (text != null) return text
        }
        return null
    }

    /** The very first metadata text (e.g. a playlist's "184 videos"). */
    private fun JsonObject.firstMetadataText(): String? {
        forEachMetadataPart { text -> return text }
        return null
    }

    private inline fun JsonObject.forEachMetadataPart(onText: (String) -> Unit) {
        val rows = metadataRows() ?: return
        for (row in rows) {
            val parts = (row as? JsonObject)?.get("metadataParts") as? JsonArray ?: continue
            for (part in parts) {
                val text = ((part as? JsonObject)?.get("text") as? JsonObject)?.stringAt("content")
                if (text != null) onText(text)
            }
        }
    }

    private fun JsonObject.metadataRows(): JsonArray? =
        ((this["metadata"] as? JsonObject)?.get("contentMetadataViewModel") as? JsonObject)
            ?.get("metadataRows") as? JsonArray

    private fun JsonObject.isLive(): Boolean {
        val overlays = thumbnailViewModel()?.get("overlays") as? JsonArray ?: return false
        return collectBadgeValues(overlays, "badgeStyle").any { it == LIVE_BADGE_STYLE }
    }

    /** Duration is the thumbnail's bottom-overlay badge text ("m:ss"/"h:mm:ss"). */
    private fun JsonObject.durationSeconds(): Long? {
        val overlays = thumbnailViewModel()?.get("overlays") as? JsonArray ?: return null
        collectBadgeValues(overlays, "text").forEach { text -> parseClockToSeconds(text)?.let { return it } }
        return null
    }

    private fun collectBadgeValues(node: JsonElement, field: String): List<String> {
        val values = mutableListOf<String>()
        collect(node, "thumbnailBadgeViewModel") { badge -> badge.stringAt(field)?.let { values.add(it) } }
        return values
    }

    private fun JsonObject.bestThumbnailUrl(): HttpUrl? = thumbnailViewModel()?.bestThumbnailUrlFromImage()

    private fun JsonObject.bestThumbnailUrlFromImage(): HttpUrl? {
        val sources = (this["image"] as? JsonObject)?.get("sources") as? JsonArray ?: return null
        return (sources.lastOrNull() as? JsonObject)?.stringAt("url")?.let(HttpUrl::parse)
    }

    private fun JsonObject.thumbnailViewModel(): JsonObject? =
        (this["contentImage"] as? JsonObject)?.get("thumbnailViewModel") as? JsonObject

    private fun JsonObject.stringAt(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
}
