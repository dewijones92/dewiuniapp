package com.dewijones92.uniapp.domain

import java.net.URI

/**
 * A validated absolute http(s) URL.
 *
 * Construction only succeeds for well-formed URLs, so any [WebUrl] in the
 * system is known-good — callers never re-validate.
 */
@JvmInline
public value class WebUrl private constructor(public val value: String) {

    override fun toString(): String = value

    public companion object {
        /** Returns a [WebUrl] if [raw] is an absolute http(s) URL, else null. */
        public fun parse(raw: String): WebUrl? {
            val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
            val isHttp = uri.scheme == "http" || uri.scheme == "https"
            return if (isHttp && !uri.host.isNullOrBlank()) WebUrl(uri.toString()) else null
        }

        /** Like [parse] but throws [IllegalArgumentException]; for trusted inputs. */
        public fun of(raw: String): WebUrl =
            requireNotNull(parse(raw)) { "Not an absolute http(s) URL: $raw" }
    }
}
