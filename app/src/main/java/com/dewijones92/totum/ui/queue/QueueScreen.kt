package com.dewijones92.totum.ui.queue

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dewijones92.totum.R
import com.dewijones92.totum.data.queue.QueueEntry
import com.dewijones92.totum.di.AppContainer
import com.dewijones92.totum.domain.DownloadState
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.ui.common.EmptyState
import com.dewijones92.totum.ui.common.MediaItemRow
import com.dewijones92.totum.ui.common.ReorderState
import com.dewijones92.totum.ui.common.mediaItemSubtitle
import com.dewijones92.totum.ui.common.rememberReorderState
import com.dewijones92.totum.ui.common.reorderable

/**
 * The queue: what is playing now and what follows, for both pillars at once.
 *
 * Entries that arrived together (a "Play all") share a [com.dewijones92.totum.data.queue.QueueGroup]
 * tag, and a header is drawn over each **contiguous run** of the same tag with a
 * one-tap "remove these". Because grouping is drawn from runs rather than stored as
 * structure, dragging an entry out simply splits the run — nothing to repair.
 */
@Composable
fun QueueScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val queue = container.playbackQueue
    val snapshot by queue.state.collectAsStateWithLifecycle()
    val downloads by container.downloadManager.observeDownloads().collectAsStateWithLifecycle(emptyMap())
    val entries = snapshot.entries

    Column(modifier = modifier.fillMaxSize()) {
        QueueHeader(canClear = entries.isNotEmpty(), onClear = queue::clear)
        if (entries.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                headline = stringResource(R.string.queue_title),
                supportingText = stringResource(R.string.queue_empty),
            )
        } else {
            val reorder = rememberReorderState(onMove = queue::move)
            LazyColumn(Modifier.fillMaxSize()) {
                itemsWithGroupHeaders(
                    entries = entries,
                    currentIndex = snapshot.currentIndex,
                    downloads = downloads,
                    reorder = reorder,
                    actions = QueueActions(
                        onPlay = queue::jumpTo,
                        onRemove = queue::removeAt,
                        onRemoveGroup = queue::removeGroup,
                        onMove = queue::move,
                    ),
                )
            }
        }
    }
}

/** What a queue row can do — bundled so the row builder isn't a wall of lambdas. */
private data class QueueActions(
    val onPlay: (Int) -> Unit,
    val onRemove: (Int) -> Unit,
    val onRemoveGroup: (String) -> Unit,
    val onMove: (Int, Int) -> Unit,
)

/**
 * Emits the queue rows, inserting a header wherever the group tag changes — so a
 * run of entries from one "Play all" reads as a block and can be dropped together.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsWithGroupHeaders(
    entries: List<QueueEntry>,
    currentIndex: Int,
    downloads: Map<MediaItemId, DownloadState>,
    reorder: ReorderState,
    actions: QueueActions,
) {
    entries.forEachIndexed { index, entry ->
        val group = entry.group
        val startsRun = group != null && entries.getOrNull(index - 1)?.group?.id != group.id
        if (startsRun) {
            item(key = "group-$index-${group.id}") {
                GroupHeader(title = group.title, onRemoveGroup = { actions.onRemoveGroup(group.id) })
            }
        }
        item(key = "entry-$index-${entry.item.item.id.value}") {
            val media = entry.item.item
            if (index == currentIndex) NowPlayingLabel()
            MediaItemRow(
                item = media,
                subtitle = mediaItemSubtitle(media),
                downloadState = downloads[media.id] ?: DownloadState.NotDownloaded,
                pillar = entry.item.handle.pillar,
                onPlay = { actions.onPlay(index) },
                onDownload = { },
                onDeleteDownload = { },
                modifier = Modifier.reorderable(reorder, index),
                trailing = {
                    with(reorder) {
                        DragHandle(
                            modifier = Modifier.dragHandle(index, entries.size),
                            onRemove = { actions.onRemove(index) },
                        )
                    }
                },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun QueueHeader(canClear: Boolean, onClear: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.queue_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        if (canClear) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.queue_clear_all)) }
        }
    }
}

/** Marks the entry the cursor is on — the playing item is a queue member, not a separate box. */
@Composable
private fun NowPlayingLabel() {
    Text(
        text = stringResource(R.string.queue_now_playing),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
    )
}

/** The header over a run of entries that arrived together. */
@Composable
private fun GroupHeader(title: String, onRemoveGroup: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRemoveGroup) { Text(stringResource(R.string.queue_remove_group)) }
    }
}

/** The grip to long-press and drag, plus remove. Two controls instead of the old three. */
@Composable
private fun DragHandle(modifier: Modifier, onRemove: () -> Unit) {
    Icon(
        Icons.Filled.DragHandle,
        contentDescription = stringResource(R.string.queue_reorder),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 8.dp),
    )
    IconButton(onClick = onRemove) {
        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.queue_remove))
    }
}
