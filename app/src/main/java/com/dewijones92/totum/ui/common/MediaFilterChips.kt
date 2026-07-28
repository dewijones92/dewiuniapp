package com.dewijones92.totum.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dewijones92.totum.R
import com.dewijones92.totum.domain.MediaFilter

/**
 * The progress filter, as chips above a feed. One row shared by every list, both pillars —
 * "hide what I have finished" means the same thing for a podcast episode and a video, so it
 * would be a design failure for each feed to grow its own.
 *
 * Horizontally scrollable rather than wrapped: three chips fit on any phone today, and a Row
 * that reflows would shift the feed down by a line the moment a fourth is added.
 */
@Composable
fun MediaFilterChips(
    selected: MediaFilter,
    onSelect: (MediaFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        MediaFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.labelRes())) },
            )
        }
    }
}

private fun MediaFilter.labelRes(): Int = when (this) {
    MediaFilter.ALL -> R.string.filter_all
    MediaFilter.UNPLAYED -> R.string.filter_unplayed
    MediaFilter.IN_PROGRESS -> R.string.filter_in_progress
}
