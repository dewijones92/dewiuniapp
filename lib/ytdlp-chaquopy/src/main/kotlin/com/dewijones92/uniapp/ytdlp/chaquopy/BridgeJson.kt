package com.dewijones92.uniapp.ytdlp.chaquopy

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.ytdlp.DownloadEvent
import com.dewijones92.uniapp.ytdlp.EngineVersions
import com.dewijones92.uniapp.ytdlp.ExtractionResult
import com.dewijones92.uniapp.ytdlp.MediaFormat
import com.dewijones92.uniapp.ytdlp.MediaMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File

/** Parsing of the uniapp_ytdlp.py JSON contract. Pure logic; unit-tested on the JVM. */

private val json = Json { ignoreUnknownKeys = true }

internal fun parseVersions(text: String): EngineVersions {
    val obj = json.parseToJsonElement(text).jsonObject
    return EngineVersions(
        ytDlp = obj.stringOrNull("yt_dlp") ?: "unknown",
        python = obj.stringOrNull("python") ?: "unknown",
    )
}

internal fun parseExtraction(url: HttpUrl, text: String): ExtractionResult {
    val obj = json.parseToJsonElement(text).jsonObject
    return if (obj.isOk()) {
        ExtractionResult.Success(obj.getValue("info").jsonObject.toMediaMetadata(url))
    } else {
        obj.toFailure(url)
    }
}

internal fun parseDownloadCompletion(url: HttpUrl, text: String, fileOf: (String) -> File): DownloadEvent {
    val obj = json.parseToJsonElement(text).jsonObject
    if (!obj.isOk()) return DownloadEvent.Failed(obj.toFailure(url))
    val path = obj.stringOrNull("filepath")
        ?: return DownloadEvent.Failed(ExtractionResult.Failure.Extractor("yt-dlp reported no output file"))
    return DownloadEvent.Completed(fileOf(path))
}

private fun JsonObject.isOk(): Boolean = this["ok"]?.jsonPrimitive?.booleanOrNull == true

private fun JsonObject.toFailure(url: HttpUrl): ExtractionResult.Failure {
    val detail = stringOrNull("detail") ?: "unknown error"
    return when (stringOrNull("kind")) {
        "unsupported" -> ExtractionResult.Failure.UnsupportedUrl(url)
        "network" -> ExtractionResult.Failure.Network(detail)
        else -> ExtractionResult.Failure.Extractor(detail)
    }
}

private fun JsonObject.toMediaMetadata(url: HttpUrl): MediaMetadata = MediaMetadata(
    id = stringOrNull("id") ?: url.value,
    title = stringOrNull("title") ?: "Untitled",
    uploader = stringOrNull("uploader") ?: stringOrNull("channel"),
    durationSeconds = this["duration"]?.jsonPrimitive?.doubleOrNull?.toLong()?.takeIf { it > 0 },
    thumbnailUrl = stringOrNull("thumbnail"),
    formats = this["formats"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toMediaFormatOrNull() },
)

private fun JsonObject.toMediaFormatOrNull(): MediaFormat? {
    val formatId = stringOrNull("format_id") ?: return null
    val hasVideo = stringOrNull("vcodec").let { it != null && it != "none" }
    val hasAudio = stringOrNull("acodec").let { it != null && it != "none" }
    val isAudioOnly = hasAudio && !hasVideo
    return MediaFormat(
        formatId = formatId,
        container = stringOrNull("ext") ?: "unknown",
        width = if (isAudioOnly) null else this["width"]?.jsonPrimitive?.longOrNull?.toInt(),
        height = if (isAudioOnly) null else this["height"]?.jsonPrimitive?.longOrNull?.toInt(),
        isAudioOnly = isAudioOnly,
        fileSizeBytes = this["filesize"]?.jsonPrimitive?.longOrNull
            ?: this["filesize_approx"]?.jsonPrimitive?.longOrNull,
    )
}

private fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
