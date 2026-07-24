package com.dewijones92.uniapp.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaContentKind
import com.dewijones92.uniapp.domain.MediaItem

// A 16:9 leading thumbnail — the shape video stills want; square podcast art
// centre-crops into it cleanly.
private val THUMBNAIL_WIDTH = 96.dp
private val THUMBNAIL_HEIGHT = 54.dp

/**
 * One media item in a list — used identically for podcast episodes and any
 * other [MediaItem]. Tapping the row plays it; the leading [MediaThumbnail]
 * shows its artwork; the trailing control reflects and drives its offline
 * [DownloadState]. Long-press (or the ⋮) opens a bottom sheet of its actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaItemRow(
    item: MediaItem,
    subtitle: String?,
    downloadState: DownloadState,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
) {
    var showSheet by remember { mutableStateOf(false) }
    val hasMenu = listOf(onPlayNext, onAddToQueue, onAddToPlaylist, onRemoveFromPlaylist).any { it != null }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = item.mediaUrl != null || hasMenu,
                onClick = { if (item.mediaUrl != null) onPlay() },
                onLongClick = if (hasMenu) ({ showSheet = true }) else null,
            )
            .padding(16.dp),
    ) {
        MediaThumbnail(
            url = item.thumbnailUrl,
            contentDescription = item.title,
            modifier = Modifier.size(width = THUMBNAIL_WIDTH, height = THUMBNAIL_HEIGHT),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (item.contentKind != MediaContentKind.STANDARD) {
                ContentKindBadge(item.contentKind)
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (hasMenu) {
            IconButton(onClick = { showSheet = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.queue_menu))
            }
        }
        DownloadControl(downloadState, onDownload, onDeleteDownload)
    }
    if (showSheet) {
        ActionSheet(
            title = item.title,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            onRemoveFromPlaylist = onRemoveFromPlaylist,
            onDismiss = { showSheet = false },
        )
    }
}

/**
 * Long-press / overflow action sheet — a Material 3 bottom sheet of the actions
 * available for the row (what apps like YouTube use). Only non-null actions show.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionSheet(
    title: String,
    onPlayNext: (() -> Unit)?,
    onAddToQueue: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?,
    onRemoveFromPlaylist: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        SheetAction(onPlayNext, Icons.AutoMirrored.Filled.PlaylistPlay, R.string.queue_play_next, onDismiss)
        SheetAction(onAddToQueue, Icons.AutoMirrored.Filled.QueueMusic, R.string.queue_add, onDismiss)
        SheetAction(onAddToPlaylist, Icons.AutoMirrored.Filled.PlaylistAdd, R.string.playlist_add_to, onDismiss)
        SheetAction(onRemoveFromPlaylist, Icons.Filled.Delete, R.string.playlist_remove_from, onDismiss)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SheetAction(action: (() -> Unit)?, icon: ImageVector, labelRes: Int, onDismiss: () -> Unit) {
    action?.let {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onDismiss()
                    it()
                }
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 24.dp))
            Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** A small pill tagging a live stream or a Short in the unified feed. */
@Composable
private fun ContentKindBadge(kind: MediaContentKind) {
    val (label, color) = when (kind) {
        MediaContentKind.LIVE -> stringResource(R.string.tag_live) to MaterialTheme.colorScheme.error
        MediaContentKind.SHORT -> stringResource(R.string.tag_short) to MaterialTheme.colorScheme.tertiary
        MediaContentKind.STANDARD -> return
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun DownloadControl(
    state: DownloadState,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        DownloadState.NotDownloaded, is DownloadState.Failed ->
            IconButton(onClick = onDownload, modifier = modifier) {
                Icon(Icons.Outlined.Download, contentDescription = stringResource(R.string.download))
            }
        is DownloadState.Downloading ->
            CircularProgressIndicator(
                progress = { state.fraction ?: 0f },
                modifier = modifier
                    .padding(12.dp)
                    .size(20.dp),
            )
        is DownloadState.Downloaded ->
            IconButton(onClick = onDeleteDownload, modifier = modifier) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.downloaded_delete),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
    }
}
