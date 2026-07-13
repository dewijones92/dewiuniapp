package com.dewijones92.uniapp.innertube.subscriptions

import com.dewijones92.uniapp.common.HttpUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Turns an InnerTube TV `browse` (FEchannels) response into subscribed
 * channels. The channel entries sit deep in a TV renderer tree
 * (`tvBrowseRenderer → … → tileRenderer`), so rather than hard-code that
 * fragile path we walk the whole tree and collect every `tileRenderer` whose
 * `contentType` marks it a channel — resilient to YouTube reshuffling the
 * wrapping shelves. Shape verified against a real 967-channel account
 * (2026-07-13). Deduplicates by channel id, preserving first-seen order.
 */
internal object SubscriptionsResponseParser {

    private const val CHANNEL_CONTENT_TYPE = "TILE_CONTENT_TYPE_CHANNEL"
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): SubscriptionsResult {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull()
            ?: return SubscriptionsResult.Failure("Unparseable subscriptions response")
        val channels = LinkedHashMap<String, SubscribedChannel>()
        collectChannelTiles(root) { tile ->
            tile.toSubscribedChannel()?.let { channels.putIfAbsent(it.channelId, it) }
        }
        return SubscriptionsResult.Success(channels.values.toList())
    }

    private fun collectChannelTiles(node: JsonElement, onTile: (JsonObject) -> Unit) {
        when (node) {
            is JsonObject -> {
                val tile = node["tileRenderer"] as? JsonObject
                if (tile != null && tile.stringAt("contentType") == CHANNEL_CONTENT_TYPE) onTile(tile)
                node.values.forEach { collectChannelTiles(it, onTile) }
            }
            is JsonArray -> node.forEach { collectChannelTiles(it, onTile) }
            else -> Unit
        }
    }

    private fun JsonObject.toSubscribedChannel(): SubscribedChannel? {
        val channelId = (this["onSelectCommand"] as? JsonObject)
            ?.let { it["browseEndpoint"] as? JsonObject }
            ?.stringAt("browseId")
            ?.takeIf { it.startsWith("UC") }
            ?: return null
        val channelUrl = SubscribedChannel.channelUrlFor(channelId) ?: return null
        val title = (this["metadata"] as? JsonObject)
            ?.let { it["tileMetadataRenderer"] as? JsonObject }
            ?.let { it["title"] as? JsonObject }
            ?.stringAt("simpleText")
            ?: channelId
        return SubscribedChannel(
            channelId = channelId,
            title = title,
            channelUrl = channelUrl,
            avatarUrl = bestAvatarUrl(),
        )
    }

    private fun JsonObject.bestAvatarUrl(): HttpUrl? {
        val thumbnails = (this["header"] as? JsonObject)
            ?.let { it["tileHeaderRenderer"] as? JsonObject }
            ?.let { it["thumbnail"] as? JsonObject }
            ?.let { it["thumbnails"] as? JsonArray }
            ?: return null
        // Last entry is the highest resolution.
        return thumbnails.lastOrNull()?.let { (it as? JsonObject)?.stringAt("url") }?.let(HttpUrl::parse)
    }

    private fun JsonObject.stringAt(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
}
