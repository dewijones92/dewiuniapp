package com.dewijones92.uniapp.data.rss

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Result of parsing feed XML. */
public sealed interface RssParseResult {
    public data class Success(val feed: ParsedFeed) : RssParseResult
    public data class Failure(val detail: String) : RssParseResult
}

/** An RSS 2.0 podcast feed, as found in the wild. */
public data class ParsedFeed(
    val title: String,
    val description: String?,
    val websiteUrl: String?,
    val episodes: List<ParsedEpisode>,
)

public data class ParsedEpisode(
    val guid: String?,
    val title: String,
    val author: String?,
    val enclosureUrl: String?,
    val publishedAt: Instant?,
    val duration: Duration?,
    val description: String?,
    val imageUrl: String?,
)

/**
 * Parses RSS 2.0 podcast feeds (with the usual iTunes extensions) via the
 * platform DOM parser — available identically on the JVM and Android.
 * Tolerant by design: a feed needs a channel title to be usable; anything
 * else missing degrades to null rather than failing the feed.
 */
public class RssParser {

    public fun parse(xml: String): RssParseResult {
        val document = runCatching {
            newHardenedDocumentBuilderFactory()
                .newDocumentBuilder()
                .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        }.getOrElse { return RssParseResult.Failure("Not parseable XML: ${it.message}") }

        val channel = document.documentElement
            ?.takeIf { it.tagName == "rss" }
            ?.firstChildElement("channel")
            ?: return RssParseResult.Failure("Not an RSS document")

        val title = channel.firstChildText("title")
            ?: return RssParseResult.Failure("Feed has no channel title")

        val episodes = channel.childElements("item").map { it.toEpisode() }

        return RssParseResult.Success(
            ParsedFeed(
                title = title,
                description = channel.firstChildText("description"),
                websiteUrl = channel.firstChildText("link"),
                episodes = episodes,
            ),
        )
    }

    private fun Element.toEpisode(): ParsedEpisode = ParsedEpisode(
        guid = firstChildText("guid"),
        title = firstChildText("title") ?: UNTITLED_EPISODE,
        author = firstChildText("itunes:author") ?: firstChildText("author"),
        enclosureUrl = firstChildElement("enclosure")?.getAttribute("url")?.ifBlank { null },
        publishedAt = firstChildText("pubDate")?.let(::parseRfc1123OrNull),
        duration = firstChildText("itunes:duration")?.let(::parseItunesDurationOrNull),
        description = firstChildText("description"),
        imageUrl = firstChildElement("itunes:image")?.getAttribute("href")?.ifBlank { null },
    )

    /**
     * External entities and doctypes are attack surface, not podcast data.
     * Note: only [setFeature]-based hardening is used. The bean-property
     * toggles (isExpandEntityReferences / isXIncludeAware) make Android's
     * Expat-backed parser throw "does not support specification Unknown
     * version 0.0" — and disallowing DOCTYPE outright covers XXE regardless.
     */
    private fun newHardenedDocumentBuilderFactory(): DocumentBuilderFactory =
        DocumentBuilderFactory.newInstance().apply {
            HARDENING_FEATURES.forEach { (feature, value) ->
                runCatching { setFeature(feature, value) }
            }
        }

    private fun Element.childElements(name: String): List<Element> {
        val result = mutableListOf<Element>()
        var child = firstChild
        while (child != null) {
            if (child is Element && child.tagName == name) result += child
            child = child.nextSibling
        }
        return result
    }

    private fun Element.firstChildElement(name: String): Element? = childElements(name).firstOrNull()

    private fun Element.firstChildText(name: String): String? =
        firstChildElement(name)?.textContent?.trim()?.ifBlank { null }

    private fun parseRfc1123OrNull(text: String): Instant? = runCatching {
        Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(text.trim()))
    }.getOrNull()

    /** iTunes duration comes as "HH:MM:SS", "MM:SS", or plain seconds. */
    private fun parseItunesDurationOrNull(text: String): Duration? {
        val parts = text.trim().split(":").map { it.toLongOrNull() ?: return null }
        val totalSeconds = when (parts.size) {
            SECONDS_ONLY_PARTS -> parts[0]
            MINUTES_SECONDS_PARTS -> parts[0] * SECONDS_PER_MINUTE + parts[1]
            HOURS_MINUTES_SECONDS_PARTS -> (parts[0] * MINUTES_PER_HOUR + parts[1]) * SECONDS_PER_MINUTE + parts[2]
            else -> return null
        }
        return totalSeconds.takeIf { it > 0 }?.seconds
    }

    private companion object {
        const val UNTITLED_EPISODE = "Untitled episode"
        const val SECONDS_PER_MINUTE = 60L
        const val MINUTES_PER_HOUR = 60L
        const val SECONDS_ONLY_PARTS = 1
        const val MINUTES_SECONDS_PARTS = 2
        const val HOURS_MINUTES_SECONDS_PARTS = 3
        val HARDENING_FEATURES = mapOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
        )
    }
}
