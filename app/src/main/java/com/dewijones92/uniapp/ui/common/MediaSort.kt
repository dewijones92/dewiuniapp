package com.dewijones92.uniapp.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.domain.MediaItem

/**
 * One sort order applied to any list of [MediaItem]s — the same options and the
 * same [SortControl] serve every list (feeds, podcast episodes, channel
 * uploads, downloads). Date sorts fall back to source order for items with no
 * known publish time (e.g. YouTube feed videos), so they degrade gracefully
 * rather than shuffling randomly.
 */
enum class MediaSort(@StringRes val labelRes: Int) {
    NEWEST(R.string.sort_newest),
    OLDEST(R.string.sort_oldest),
    TITLE(R.string.sort_title),
    LONGEST(R.string.sort_longest),
    SHORTEST(R.string.sort_shortest),
    ;

    fun apply(items: List<MediaItem>): List<MediaItem> = when (this) {
        // Sorting is stable, so items with no known date/duration (e.g. YouTube
        // feed videos) keep their source order rather than shuffling.
        NEWEST -> items.sortedByDescending { it.publishedAt }
        OLDEST -> items.sortedBy { it.publishedAt }
        TITLE -> items.sortedBy { it.title.lowercase() }
        LONGEST -> items.sortedByDescending { it.duration }
        SHORTEST -> items.sortedBy { it.duration }
    }

    companion object {
        val DEFAULT: MediaSort = NEWEST
    }
}

/**
 * A section header (title on the left) with the shared [SortControl] on the
 * right — used above any sortable list so the affordance looks the same
 * everywhere.
 */
@Composable
fun SectionHeaderWithSort(
    title: String,
    sort: MediaSort,
    onSetSort: (MediaSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        SortControl(current = sort, onSelect = onSetSort)
    }
}

/** A compact sort menu: a sort icon that opens the [MediaSort] options. */
@Composable
fun SortControl(current: MediaSort, onSelect: (MediaSort) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }, modifier = modifier) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Sort,
            contentDescription = stringResource(R.string.sort_label),
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        MediaSort.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(stringResource(option.labelRes)) },
                leadingIcon = {
                    if (option == current) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                    }
                },
                onClick = {
                    onSelect(option)
                    expanded = false
                },
            )
        }
    }
}
