package com.dewijones92.uniapp.data

import com.dewijones92.uniapp.domain.Chapter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.milliseconds

/**
 * Encodes/decodes a chapter list as the compact JSON stored in one episode
 * column (`[{"s":startMs,"t":title}]`). Pure logic, so it lives here (testable
 * on the JVM) rather than in the Room store. Decoding is tolerant: a malformed
 * document yields no chapters, and a single bad element is skipped, not fatal.
 */
public object ChapterJson {

    public fun encode(chapters: List<Chapter>): String? {
        if (chapters.isEmpty()) return null
        return buildJsonArray {
            chapters.forEach { chapter ->
                add(
                    buildJsonObject {
                        put("s", chapter.start.inWholeMilliseconds)
                        put("t", chapter.title)
                    },
                )
            }
        }.toString()
    }

    public fun decode(text: String?): List<Chapter> {
        if (text == null) return emptyList()
        val array = runCatching { Json.parseToJsonElement(text).jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { it.toChapterOrNull() }
    }

    private fun JsonElement.toChapterOrNull(): Chapter? {
        val obj = this as? JsonObject ?: return null
        val start = (obj["s"]?.jsonPrimitive?.longOrNull)?.takeIf { it >= 0 } ?: return null
        val title = obj["t"]?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: return null
        return Chapter(start.milliseconds, title)
    }
}
