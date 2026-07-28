package com.dewijones92.totum.ui.queue

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.ui.common.CollapsingTitle
import com.dewijones92.totum.ui.common.EmptyState
import com.dewijones92.totum.ui.common.EqualiserSize
import com.dewijones92.totum.ui.common.MediaItemRow
import com.dewijones92.totum.ui.common.PlayingEqualiser
import com.dewijones92.totum.ui.common.ReorderState
import com.dewijones92.totum.ui.common.mediaItemSubtitle
import com.dewijones92.totum.ui.common.rememberReorderState
import com.dewijones92.totum.ui.common.reorderable
import kotlinx.coroutines.launch

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
    val playing by container.playbackController.state.collectAsStateWithLifecycle()
    val entries = snapshot.entries
    val scope = rememberCoroutineScope()

    // Hoisted so the header can collapse against it — the header sits outside the list, so
    // it cannot read a state the list owns privately.
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize()) {
        QueueHeader(canClear = entries.isNotEmpty(), onClear = queue::clear, listState = listState)
        if (entries.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                headline = stringResource(R.string.queue_title),
                supportingText = stringResource(R.string.queue_empty),
            )
        } else {
            val reorder = rememberReorderState(onMove = queue::move)
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsWithGroupHeaders(
                    entries = entries,
                    nowPlaying = NowPlaying(snapshot.currentIndex, playing?.progress, playing?.isPlaying == true),
                    downloads = downloads,
                    reorder = reorder,
                    actions = QueueActions(
                        onPlay = queue::jumpTo,
                        onRemove = queue::removeAt,
                        onRemoveGroup = queue::removeGroup,
                        onMove = queue::move,
                        // A manual retry: the queue fetches audio by itself, but a failed
                        // or skipped fetch otherwise leaves no way to ask again.
                        onDownload = { item ->
                            scope.launch { container.downloadManager.download(item, audioOnly = true) }
                        },
                        onDownloadVideo = { item ->
                            scope.launch { container.downloadManager.download(item, audioOnly = false) }
                        },
                        onDeleteDownload = { id -> scope.launch { container.downloadManager.delete(id) } },
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
    val onDownload: (MediaItem) -> Unit,
    val onDownloadVideo: (MediaItem) -> Unit,
    val onDeleteDownload: (MediaItemId) -> Unit,
)

/** Where the cursor is and how far through that item playback has got. */
private data class NowPlaying(val index: Int, val progress: Float?, val isPlaying: Boolean)

/**
 * Emits the queue rows, inserting a header wherever the group tag changes — so a
 * run of entries from one "Play all" reads as a block and can be dropped together.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsWithGroupHeaders(
    entries: List<QueueEntry>,
    nowPlaying: NowPlaying,
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
            if (index == nowPlaying.index) NowPlayingLabel(nowPlaying.progress, nowPlaying.isPlaying)
            MediaItemRow(
                item = media,
                subtitle = mediaItemSubtitle(media),
                downloadState = downloads[media.id] ?: DownloadState.NotDownloaded,
                pillar = entry.item.handle.pillar,
                onPlay = { actions.onPlay(index) },
                onDownload = { actions.onDownload(media) },
                onDeleteDownload = { actions.onDeleteDownload(media.id) },
                onDownloadVideo = { actions.onDownloadVideo(media) },
                onMoveToTop = { actions.onMove(index, 0) }.takeIf { index > 0 },
                onMoveToBottom = { actions.onMove(index, entries.lastIndex) }
                    .takeIf { index < entries.lastIndex },
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
private fun QueueHeader(canClear: Boolean, onClear: () -> Unit, listState: LazyListState) {
    CollapsingTitle(title = stringResource(R.string.queue_title), listState = listState) {
        if (canClear) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.queue_clear_all)) }
        }
    }
}

/**
 * Marks the entry the cursor is on — the playing item is a queue member, not a separate box,
 * which is why this stays a label in place rather than a now-playing card above the list.
 *
 * The label alone was easy to miss in a long queue, so it now carries a brand-tinted bar and
 * the item's progress. The progress line is what makes the tab feel like a player surface
 * rather than a list that happens to have one row highlighted.
 */
@Composable
private fun NowPlayingLabel(progress: Float?, isPlaying: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
    ) {
        // Dancing bars rather than a static bar: in a long queue you want to spot the item
        // making sound *now*, and motion says that in a way no glyph does. It also
        // distinguishes playing from paused without needing a second symbol.
        PlayingEqualiser(
            playing = isPlaying,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(EqualiserSize),
        )
        Text(
            text = stringResource(R.string.queue_now_playing),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
        progress?.let {
            LinearProgressIndicator(
                progress = { it },
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
                    .height(PROGRESS_HEIGHT),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
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

private val PROGRESS_HEIGHT = 2.dp
