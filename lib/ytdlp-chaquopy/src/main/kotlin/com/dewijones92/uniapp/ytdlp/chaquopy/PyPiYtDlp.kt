package com.dewijones92.uniapp.ytdlp.chaquopy

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The latest yt-dlp release on PyPI: its version and the universal wheel to fetch. */
internal data class YtDlpRelease(val version: String, val wheelUrl: String, val sha256: String)

/** Parses PyPI's project JSON (`https://pypi.org/pypi/yt-dlp/json`). Pure. */
internal object PyPiYtDlp {

    const val JSON_URL: String = "https://pypi.org/pypi/yt-dlp/json"

    /** The `py3-none-any` wheel for the latest version, or null if the JSON lacks one. */
    fun parse(json: String): YtDlpRelease? {
        val root = Json.parseToJsonElement(json).jsonObject
        val version = root["info"]?.jsonObject?.get("version")?.jsonPrimitive?.contentOrNull
            ?: return null
        val wheel = root["urls"]?.jsonArray?.firstOrNull {
            val o = it.jsonObject
            o["packagetype"]?.jsonPrimitive?.contentOrNull == "bdist_wheel" &&
                o["filename"]?.jsonPrimitive?.contentOrNull?.endsWith("py3-none-any.whl") == true
        }?.jsonObject ?: return null
        val url = wheel["url"]?.jsonPrimitive?.contentOrNull
        val sha256 = wheel["digests"]?.jsonObject?.get("sha256")?.jsonPrimitive?.contentOrNull
        return if (url != null && sha256 != null) YtDlpRelease(version, url, sha256) else null
    }
}
