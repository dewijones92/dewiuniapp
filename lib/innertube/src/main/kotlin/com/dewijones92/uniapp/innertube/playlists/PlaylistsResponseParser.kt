package com.dewijones92.uniapp.innertube.playlists

import com.dewijones92.uniapp.common.HttpUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses the account's `FEplaylist_aggregation` browse response. Playlists come
 * back as `tileRenderer`s with `contentType == TILE_CONTENT_TYPE_PLAYLIST` —
 * the same TV tile shape as video feeds, but selecting one browses the playlist
 * (a "VL"-prefixed browse id) rather than watching a video. Walks the tree so it
 * survives reshuffling; dedupes by browse id, first-seen order preserved. Shape
 * verified against a real account (2026-07-22).
 */
internal object PlaylistsResponseParser {

    private const val PLAYLIST_CONTENT_TYPE = "TILE_CONTENT_TYPE_PLAYLIST"
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): PlaylistsResult {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull()
            ?: return PlaylistsResult.Failure("Unparseable playlists response")
        val playlists = LinkedHashMap<String, Playlist>()
        collectPlaylistTiles(root) { tile ->
            tile.toPlaylist()?.let { playlists.putIfAbsent(it.browseId, it) }
        }
        return PlaylistsResult.Success(playlists.values.toList())
    }

    private fun collectPlaylistTiles(node: JsonElement, onTile: (JsonObject) -> Unit) {
        when (node) {
            is JsonObject -> {
                val tile = node["tileRenderer"] as? JsonObject
                if (tile != null && tile.stringAt("contentType") == PLAYLIST_CONTENT_TYPE) onTile(tile)
                node.values.forEach { collectPlaylistTiles(it, onTile) }
            }
            is JsonArray -> node.forEach { collectPlaylistTiles(it, onTile) }
            else -> Unit
        }
    }

    private fun JsonObject.toPlaylist(): Playlist? {
        val browseId = ((this["onSelectCommand"] as? JsonObject)?.get("browseEndpoint") as? JsonObject)
            ?.stringAt("browseId") ?: return null
        val metadata = (this["metadata"] as? JsonObject)?.get("tileMetadataRenderer") as? JsonObject
        val title = (metadata?.get("title") as? JsonObject)?.stringAt("simpleText") ?: return null
        return Playlist(
            browseId = browseId,
            title = title,
            videoCountText = videoCountText(),
            thumbnailUrl = bestThumbnailUrl(),
        )
    }

    /** The "N videos" overlay text on the playlist thumbnail. */
    private fun JsonObject.videoCountText(): String? {
        val overlays = header()?.get("thumbnailOverlays") as? JsonArray ?: return null
        for (overlay in overlays) {
            val text = ((overlay as? JsonObject)?.get("thumbnailOverlayTimeStatusRenderer") as? JsonObject)
                ?.get("text") as? JsonObject
            text?.joinRuns()?.let { return it }
        }
        return null
    }

    private fun JsonObject.bestThumbnailUrl(): HttpUrl? {
        val thumbnails = (header()?.get("thumbnail") as? JsonObject)?.get("thumbnails") as? JsonArray ?: return null
        return (thumbnails.lastOrNull() as? JsonObject)?.stringAt("url")?.let(HttpUrl::parse)
    }

    private fun JsonObject.header(): JsonObject? =
        (this["header"] as? JsonObject)?.get("tileHeaderRenderer") as? JsonObject
}

private fun JsonObject.joinRuns(): String? {
    stringAt("simpleText")?.let { return it }
    val runs = this["runs"] as? JsonArray ?: return null
    return runs.joinToString("") { (it as? JsonObject)?.stringAt("text").orEmpty() }.ifBlank { null }
}

private fun JsonObject.stringAt(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
