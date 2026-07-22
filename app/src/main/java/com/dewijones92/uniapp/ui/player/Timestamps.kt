package com.dewijones92.uniapp.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink

/**
 * Finds chapter-style timestamps (e.g. `0:00`, `1:23`, `1:02:03`) in a
 * description and where each seeks to. Works for a video's chapters and a
 * podcast's show notes alike, so it backs the one description surface both
 * pillars share.
 */
internal object Timestamps {

    /** A timestamp in the text: where it sits, and where it seeks to. */
    data class Match(val range: IntRange, val positionMs: Long)

    private const val MILLIS_PER_SECOND = 1000L
    private const val SECONDS_PER_MINUTE = 60
    private const val MINUTES_PER_HOUR = 60

    // m:ss / mm:ss / h:mm:ss — minutes and seconds bounded so scores and ratios don't match.
    private val REGEX = Regex("""(?<![\d:])(?:(\d{1,2}):)?([0-5]?\d):([0-5]\d)(?![\d:])""")

    fun find(text: String): List<Match> =
        REGEX.findAll(text).map { match ->
            val (hours, minutes, seconds) = match.destructured
            val totalSeconds = (hours.toIntOrNull() ?: 0) * MINUTES_PER_HOUR * SECONDS_PER_MINUTE +
                minutes.toInt() * SECONDS_PER_MINUTE +
                seconds.toInt()
            Match(match.range, totalSeconds * MILLIS_PER_SECOND)
        }.toList()
}

/**
 * The text as an [AnnotatedString] with each chapter timestamp turned into a
 * link that seeks playback to that position.
 */
internal fun String.withTimestampLinks(linkColor: Color, onSeekTo: (Long) -> Unit): AnnotatedString {
    val matches = Timestamps.find(this)
    if (matches.isEmpty()) return AnnotatedString(this)
    return buildAnnotatedString {
        var cursor = 0
        matches.forEach { match ->
            append(substring(cursor, match.range.first))
            val link = LinkAnnotation.Clickable(
                tag = match.positionMs.toString(),
                styles = TextLinkStyles(SpanStyle(color = linkColor)),
            ) { onSeekTo((it as LinkAnnotation.Clickable).tag.toLong()) }
            withLink(link) { append(substring(match.range)) }
            cursor = match.range.last + 1
        }
        append(substring(cursor))
    }
}
