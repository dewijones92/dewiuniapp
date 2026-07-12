package com.dewijones92.uniapp.ytdlp

import java.net.URI

/**
 * A validated absolute http(s) URL that the engine can be asked to extract.
 *
 * Deliberately independent of any app-side URL type: this library stands
 * alone. Construction only succeeds for well-formed URLs, so every
 * [MediaUrl] an engine receives is known-good.
 */
@JvmInline
public value class MediaUrl private constructor(public val value: String) {

    override fun toString(): String = value

    public companion object {
        /** Returns a [MediaUrl] if [raw] is an absolute http(s) URL, else null. */
        public fun parse(raw: String): MediaUrl? {
            val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
            val isHttp = uri.scheme == "http" || uri.scheme == "https"
            return if (isHttp && !uri.host.isNullOrBlank()) MediaUrl(uri.toString()) else null
        }

        /** Like [parse] but throws [IllegalArgumentException]; for trusted inputs. */
        public fun of(raw: String): MediaUrl =
            requireNotNull(parse(raw)) { "Not an absolute http(s) URL: $raw" }
    }
}
