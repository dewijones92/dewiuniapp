package com.dewijones92.uniapp.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.domain.MediaItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The one-line "author · date · duration" summary shown under a media title. */
@Composable
fun mediaItemSubtitle(item: MediaItem): String? {
    val date = item.publishedAt?.let {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(it)
    }
    val duration = item.duration?.let { stringResource(R.string.duration_minutes, it.inWholeMinutes) }
    return listOfNotNull(item.author, date, duration).joinToString(" · ").ifBlank { null }
}
