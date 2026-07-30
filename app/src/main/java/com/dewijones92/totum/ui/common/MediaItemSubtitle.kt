package com.dewijones92.totum.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dewijones92.totum.R
import com.dewijones92.totum.domain.MediaItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Duration

/**
 * The one-line "author · views · date" summary shown under a media title.
 *
 * Duration is deliberately NOT here — it rides on the thumbnail, where an ellipsis cannot
 * reach it. This line is the part that may truncate, so the least essential facts go last.
 */
@Composable
fun mediaItemSubtitle(item: MediaItem): String? {
    // Prefer the source's own published text (e.g. YouTube's "2 days ago");
    // otherwise format an absolute date (podcasts).
    val date = item.publishedText ?: item.publishedAt?.let {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(it)
    }
    return mediaSubtitle(item.author, date, item.viewsText)
}

/**
 * The one place that formats "author · views · date". Takes the parts rather than a
 * [MediaItem] so search hits — which aren't media items yet — read identically to every
 * other list instead of composing their own subtitle.
 */
@Composable
fun mediaSubtitle(author: String?, dateText: String?, viewsText: String? = null): String? =
    listOfNotNull(author, viewsText, dateText).joinToString(" · ").ifBlank { null }

/**
 * "12:34" / "1:02:45" — how every player writes a length, and short enough to sit on a
 * thumbnail. Minutes-only ("34 min") was what the subtitle used to say, which is both
 * longer and vaguer, and rounded a 45-second Short to nothing at all.
 */
fun formatClock(duration: Duration): String {
    val total = duration.inWholeSeconds
    val hours = total / SECONDS_PER_HOUR
    val minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = total % SECONDS_PER_MINUTE
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** A row's duration chip: the clock, or nothing when the source never said. */
fun durationLabel(item: MediaItem): String? = item.duration?.let(::formatClock)

/** Kept for the "N min" phrasing where a full clock would be overkill (sleep timer, settings). */
@Composable
fun minutesLabel(minutes: Long): String = stringResource(R.string.duration_minutes, minutes)

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
