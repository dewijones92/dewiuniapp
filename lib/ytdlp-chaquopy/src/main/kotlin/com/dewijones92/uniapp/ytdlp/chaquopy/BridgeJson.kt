package com.dewijones92.uniapp.ytdlp.chaquopy

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.ytdlp.ChannelResult
import com.dewijones92.uniapp.ytdlp.ChapterInfo
import com.dewijones92.uniapp.ytdlp.DownloadEvent
import com.dewijones92.uniapp.ytdlp.EngineVersions
import com.dewijones92.uniapp.ytdlp.ExtractionResult
import com.dewijones92.uniapp.ytdlp.MediaFormat
import com.dewijones92.uniapp.ytdlp.MediaMetadata
import com.dewijones92.uniapp.ytdlp.VideoSearchEntry
import com.dewijones92.uniapp.ytdlp.VideoSearchResult
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
        val tracking = obj["tracking"]?.jsonObject
        ExtractionResult.Success(
            obj.getValue("info").jsonObject.toMediaMetadata(
                url = url,
                playbackTrackingUrl = tracking?.stringOrNull("playback"),
                watchtimeTrackingUrl = tracking?.stringOrNull("watchtime"),
            ),
        )
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

private fun JsonObject.toMediaMetadata(
    url: HttpUrl,
    playbackTrackingUrl: String?,
    watchtimeTrackingUrl: String?,
): MediaMetadata = MediaMetadata(
    id = stringOrNull("id") ?: url.value,
    title = stringOrNull("title") ?: "Untitled",
    uploader = stringOrNull("uploader") ?: stringOrNull("channel"),
    durationSeconds = this["duration"]?.jsonPrimitive?.doubleOrNull?.toLong()?.takeIf { it > 0 },
    thumbnailUrl = stringOrNull("thumbnail"),
    formats = this["formats"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toMediaFormatOrNull() },
    description = stringOrNull("description"),
    playbackTrackingUrl = playbackTrackingUrl,
    watchtimeTrackingUrl = watchtimeTrackingUrl,
    chapters = this["chapters"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toChapterOrNull() },
)

private fun JsonObject.toChapterOrNull(): ChapterInfo? {
    val start = this["start_time"]?.jsonPrimitive?.doubleOrNull ?: return null
    val title = stringOrNull("title") ?: return null
    return ChapterInfo(startSeconds = start, title = title)
}

private fun JsonObject.toMediaFormatOrNull(): MediaFormat? {
    val formatId = stringOrNull("format_id") ?: return null
    val hasVideo = stringOrNull("vcodec").let { it != null && it != "none" }
    val hasAudio = stringOrNull("acodec").let { it != null && it != "none" }
    // Storyboards and other codec-less pseudo-formats are not media.
    if (!hasVideo && !hasAudio) return null
    return MediaFormat(
        formatId = formatId,
        container = stringOrNull("ext") ?: "unknown",
        width = if (hasVideo) this["width"]?.jsonPrimitive?.longOrNull?.toInt() else null,
        height = if (hasVideo) this["height"]?.jsonPrimitive?.longOrNull?.toInt() else null,
        hasVideo = hasVideo,
        hasAudio = hasAudio,
        fileSizeBytes = this["filesize"]?.jsonPrimitive?.longOrNull
            ?: this["filesize_approx"]?.jsonPrimitive?.longOrNull,
        url = stringOrNull("url"),
    )
}

internal fun parseChannel(url: HttpUrl, text: String): ChannelResult {
    val obj = json.parseToJsonElement(text).jsonObject
    if (!obj.isOk()) {
        val detail = obj.stringOrNull("detail") ?: "unknown error"
        return when (obj.stringOrNull("kind")) {
            "network" -> ChannelResult.Failure.Network(detail)
            else -> ChannelResult.Failure.NotAChannel(url)
        }
    }
    return ChannelResult.Success(
        channelId = obj.stringOrNull("channel_id") ?: url.value,
        title = obj.stringOrNull("title") ?: "Channel",
        videos = obj["videos"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toSearchEntryOrNull() },
    )
}

internal fun parseSearch(text: String): VideoSearchResult {
    val obj = json.parseToJsonElement(text).jsonObject
    if (!obj.isOk()) {
        return VideoSearchResult.Failure(obj.stringOrNull("detail") ?: "unknown error")
    }
    val entries = obj["entries"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toSearchEntryOrNull() }
    return VideoSearchResult.Success(entries)
}

/** Shared flat-entry → [VideoSearchEntry] mapping for search and channel results. */
private fun JsonObject.toSearchEntryOrNull(): VideoSearchEntry? {
    val id = stringOrNull("id") ?: return null
    val watchUrl = stringOrNull("url")?.let(HttpUrl::parse) ?: return null
    return VideoSearchEntry(
        id = id,
        title = stringOrNull("title") ?: "Untitled",
        uploader = stringOrNull("uploader"),
        durationSeconds = this["duration"]?.jsonPrimitive?.doubleOrNull?.toLong()?.takeIf { it > 0 },
        watchUrl = watchUrl,
        thumbnailUrl = stringOrNull("thumbnail"),
    )
}

private fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
