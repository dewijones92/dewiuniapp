package com.dewijones92.totum.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dewijones92.totum.R
import com.dewijones92.totum.domain.MediaItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The one-line "author · date · duration" summary shown under a media title. */
@Composable
fun mediaItemSubtitle(item: MediaItem): String? {
    // Prefer the source's own published text (e.g. YouTube's "2 days ago");
    // otherwise format an absolute date (podcasts).
    val date = item.publishedText ?: item.publishedAt?.let {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(it)
    }
    return mediaSubtitle(item.author, date, item.duration?.inWholeMinutes)
}

/**
 * The one place that formats "author · date · duration". Takes the parts rather
 * than a [MediaItem] so search hits — which aren't media items yet — read
 * identically to every other list instead of composing their own subtitle.
 */
@Composable
fun mediaSubtitle(author: String?, dateText: String?, durationMinutes: Long?): String? {
    val duration = durationMinutes?.let { stringResource(R.string.duration_minutes, it) }
    return listOfNotNull(author, dateText, duration).joinToString(" · ").ifBlank { null }
}
