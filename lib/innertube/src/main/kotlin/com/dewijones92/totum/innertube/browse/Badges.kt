package com.dewijones92.totum.innertube.browse

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The badge labels on a tile — "4K", "New", "Members only", "Verified".
 *
 * One reader for every shape, because the three tile parsers each guessed a different one
 * and two of them guessed wrong. Checked against live responses on 2026-07-30:
 *
 * - TV tiles put badges at `lineItemRenderer.badge.metadataBadgeRenderer.label` — the
 *   subscriptions feed carries `"label": "4K"` there. [VideoTileParser] was reading
 *   `lineItemRenderer.text` instead, so it could never see one.
 * - WEB search uses the same `metadataBadgeRenderer.label`, nested differently.
 * - Channel-tab lockups carry no `badgeViewModel` at all — the key [LockupParser] matched
 *   on appears in exactly zero real responses, and in none of the captured fixtures.
 *
 * So: walk the subtree and take every `metadataBadgeRenderer.label`, which is where YouTube
 * puts badge TEXT in every shape seen. Position-independent for the usual reason — YouTube
 * reshuffles, and a path-based read fails silently rather than loudly.
 */
public object Badges {

    public fun labelsIn(node: JsonElement): List<String> {
        val labels = mutableListOf<String>()
        walk(node, labels)
        return labels
    }

    private fun walk(node: JsonElement, into: MutableList<String>) {
        when (node) {
            is JsonObject -> {
                (node["metadataBadgeRenderer"] as? JsonObject)
                    ?.get("label")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.ifBlank { null }
                    ?.let(into::add)
                node.values.forEach { walk(it, into) }
            }
            is JsonArray -> node.forEach { walk(it, into) }
            else -> Unit
        }
    }
}
