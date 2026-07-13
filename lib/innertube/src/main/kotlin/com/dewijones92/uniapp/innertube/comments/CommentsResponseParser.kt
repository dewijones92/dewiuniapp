package com.dewijones92.uniapp.innertube.comments

import com.dewijones92.uniapp.common.HttpUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses YouTube's WEB `next` responses for comments. Two jobs:
 *
 * - [findCommentsContinuation] pulls the token that loads the comment section
 *   out of the initial watch-page response (the `comment-item-section`).
 * - [parseComments] reads the comment page. Modern YouTube delivers comment
 *   data as `commentEntityPayload` entities in
 *   `frameworkUpdates.entityBatchUpdate.mutations`, not inline renderers, so we
 *   read those; only top-level comments (`replyLevel` 0) are returned.
 *
 * Shape verified against a real video (2026-07-13).
 */
internal object CommentsResponseParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun findCommentsContinuation(body: String): String? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null
        val section = findSection(root) ?: return null
        return firstToken(section)
    }

    fun parseComments(body: String): CommentsResult {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: return CommentsResult.Failure("Unparseable comments response")
        val mutations = root.path("frameworkUpdates", "entityBatchUpdate")?.arrayAt("mutations")
            ?: return CommentsResult.Success(emptyList())
        val comments = mutations.mapNotNull { mutation ->
            ((mutation as? JsonObject)?.get("payload") as? JsonObject)
                ?.get("commentEntityPayload")
                ?.let { it as? JsonObject }
                ?.toComment()
        }
        return CommentsResult.Success(comments)
    }

    private fun JsonObject.toComment(): Comment? {
        val properties = this["properties"] as? JsonObject ?: return null
        if ((properties.stringAt("replyLevel")?.toIntOrNull() ?: 0) != 0) return null
        val id = properties.stringAt("commentId") ?: return null
        val text = (properties["content"] as? JsonObject)?.stringAt("content") ?: ""
        val author = this["author"] as? JsonObject
        val toolbar = this["toolbar"] as? JsonObject
        return Comment(
            id = id,
            author = author?.stringAt("displayName") ?: "",
            authorAvatarUrl = author?.stringAt("avatarThumbnailUrl")?.let(HttpUrl::parse),
            text = text,
            publishedTime = properties.stringAt("publishedTime"),
            likeCount = toolbar?.stringAt("likeCountNotliked")?.ifBlank { null },
            replyCount = toolbar?.stringAt("replyCount")?.toIntOrNull() ?: 0,
            isCreator = author?.get("isCreator")?.jsonPrimitive?.booleanOrNull ?: false,
            isVerified = author?.get("isVerified")?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }

    /** The comment section renderer, identified by its stable section id. */
    private fun findSection(node: JsonElement): JsonObject? {
        when (node) {
            is JsonObject -> {
                if (node.stringAt("sectionIdentifier") == COMMENTS_SECTION ||
                    node.stringAt("targetId") == COMMENTS_TARGET
                ) {
                    return node
                }
                node.values.forEach { child -> findSection(child)?.let { return it } }
            }
            is JsonArray -> node.forEach { child -> findSection(child)?.let { return it } }
            else -> Unit
        }
        return null
    }

    /** First continuation token anywhere inside [node]. */
    private fun firstToken(node: JsonElement): String? {
        when (node) {
            is JsonObject -> {
                (node["continuationCommand"] as? JsonObject)?.stringAt("token")?.let { return it }
                node.values.forEach { child -> firstToken(child)?.let { return it } }
            }
            is JsonArray -> node.forEach { child -> firstToken(child)?.let { return it } }
            else -> Unit
        }
        return null
    }

    private fun JsonObject.path(vararg keys: String): JsonObject? {
        var current: JsonObject? = this
        for (key in keys) current = current?.get(key) as? JsonObject
        return current
    }

    private fun JsonObject.arrayAt(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.stringAt(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.ifBlank { null }

    private const val COMMENTS_SECTION = "comment-item-section"
    private const val COMMENTS_TARGET = "comments-section"
}
