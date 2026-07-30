package com.dewijones92.totum.innertube.browse

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The badges on a tile — "4K", "New", "Members only" — across every shape YouTube uses.
 *
 * One reader, because the three tile parsers each guessed a different shape and each
 * guessed differently wrong. Verified against live responses on 2026-07-30:
 *
 * - **Channel lockups**: `badges[].badgeViewModel` with the text in `badgeText` and a style
 *   in `badgeStyle`. This is where "Members only" actually lives (The Rest Is Politics'
 *   Videos tab carries seven, signed out).
 * - **TV tiles**: `lineItemRenderer.badge.metadataBadgeRenderer` with the text in `label` —
 *   the subscriptions feed carries "4K" there. A different key AND a different field.
 * - **WEB search**: `metadataBadgeRenderer.label` again, nested differently.
 *
 * [membersOnly] keys on the STYLE, not the words. `BADGE_MEMBERS_ONLY` is a constant;
 * "Members only" is English, and YouTube localises badge text. The text match stays as a
 * fallback for any shape that omits the style.
 */
public object Badges {

    private const val MEMBERS_ONLY_STYLE = "BADGE_MEMBERS_ONLY"

    /** Every badge's visible text, whichever renderer carries it. */
    public fun labelsIn(node: JsonElement): List<String> = collect(node) { badge ->
        badge.stringAt("badgeText") ?: badge.stringAt("label")
    }

    /** Whether this tile is behind a channel membership. */
    public fun membersOnly(node: JsonElement): Boolean {
        val styles = collect(node) { it.stringAt("badgeStyle") ?: it.stringAt("style") }
        return MEMBERS_ONLY_STYLE in styles || labelsIn(node).any { it.contains("members", ignoreCase = true) }
    }

    private fun collect(node: JsonElement, from: (JsonObject) -> String?): List<String> {
        val found = mutableListOf<String>()
        walk(node, from, found)
        return found
    }

    private fun walk(node: JsonElement, from: (JsonObject) -> String?, into: MutableList<String>) {
        when (node) {
            is JsonObject -> {
                BADGE_KEYS.forEach { key -> (node[key] as? JsonObject)?.let(from)?.let(into::add) }
                node.values.forEach { walk(it, from, into) }
            }
            is JsonArray -> node.forEach { walk(it, from, into) }
            else -> Unit
        }
    }

    /**
     * `thumbnailBadgeViewModel` is deliberately absent: it carries the duration and the LIVE
     * marker, which are read elsewhere and would otherwise turn every video's length into a
     * "badge".
     */
    private val BADGE_KEYS = listOf("badgeViewModel", "metadataBadgeRenderer")

    private fun JsonObject.stringAt(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
}
