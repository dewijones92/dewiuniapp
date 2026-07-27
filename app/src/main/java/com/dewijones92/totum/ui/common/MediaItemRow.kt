package com.dewijones92.totum.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dewijones92.totum.R
import com.dewijones92.totum.domain.DownloadState
import com.dewijones92.totum.domain.MediaContentKind
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaKind
import com.dewijones92.totum.domain.PlayState

// A 16:9 leading thumbnail — the shape video stills want; square podcast art
// centre-crops into it cleanly.
private const val TITLE_MAX_LINES = 2

private val THUMBNAIL_WIDTH = 96.dp
private val THUMBNAIL_HEIGHT = 54.dp

/**
 * One media item in a list — used identically for podcast episodes and any
 * other [MediaItem]. Tapping the row plays it; the leading [MediaThumbnail]
 * shows its artwork; the trailing control reflects and drives its offline
 * [DownloadState]. Long-press (or the ⋮) opens a bottom sheet of its actions.
 *
 * Every row states what it is: its [pillar], whether it's held offline, and its
 * [playState]. [pillar] is required rather than inferred — mixed lists know it from
 * the item's `PlayHandle` and single-pillar screens know it outright, so guessing from
 * a URL would be both lossy and unnecessary.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaItemRow(
    item: MediaItem,
    subtitle: String?,
    pillar: MediaKind,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    modifier: Modifier = Modifier,
    /** Defaults to the app-wide offline state, so no screen has to plumb it. */
    downloadState: DownloadState = LocalDownloadStates.current[item.id] ?: DownloadState.NotDownloaded,
    // Everything an item can do defaults to the app-wide capability. A screen has to work
    // to REMOVE an action, never to remember one. Only genuinely contextual actions
    // (remove-from-playlist, move-within-queue) stay null, because they only exist somewhere.
    onPlayNext: (() -> Unit)? = LocalItemActions.current.bind { playNext(item) },
    onAddToQueue: (() -> Unit)? = LocalItemActions.current.bind { addToQueue(item) },
    onAddToPlaylist: (() -> Unit)? = LocalItemActions.current.bind { addToPlaylist(item) },
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onPeek: (() -> Unit)? = LocalItemActions.current.bind { peek(item) },
    /**
     * Offered when the row's local copy is audio only (what the queue fetches
     * automatically): the tick already means "offline", so without this there'd be no
     * way left to ask for the picture too.
     */
    onDownloadVideo: (() -> Unit)? = null,
    /** Switches between listening and watching (and sets the mode); videos only. */
    onSwitchMode: (() -> Unit)? =
        LocalItemActions.current.takeIf { pillar == MediaKind.VIDEO }.bind { switchMode(item) },
    /** True when the mode is audio, so the action reads "Watch with video" instead. */
    audioMode: Boolean = LocalItemActions.current?.audioMode == true,
    /** Defaults to the app-wide play state, so no screen has to plumb it. */
    playState: PlayState = LocalPlayStates.current[item.id] ?: PlayState.Unplayed,
    /** Marks the item played or unplayed by hand — AntennaPod's most-used action. */
    onSetPlayed: ((Boolean) -> Unit)? = LocalSetPlayed.current?.let { set -> { played -> set(item.id, played) } },
    /** Queue-only: jump this entry to the front / back of the up-next order. */
    onMoveToTop: (() -> Unit)? = null,
    onMoveToBottom: (() -> Unit)? = null,
    onGoToSource: (() -> Unit)? = LocalItemActions.current.bind { goToSource(item) },
    /** Label for [onGoToSource] — the host knows its pillar ("channel" vs "podcast"). */
    goToSourceLabelRes: Int = R.string.go_to_channel,
    /**
     * Replaces the download control for rows whose trailing affordances are about
     * something else — the queue's reorder/remove buttons, for instance.
     */
    trailing: (@Composable () -> Unit)? = null,
) {
    var showSheet by remember { mutableStateOf(false) }
    // "Download the video too" only makes sense once the local copy is audio-only.
    val downloadVideo = onDownloadVideo?.takeIf {
        (downloadState as? DownloadState.Downloaded)?.audioOnly == true
    }
    // Rows that replace the download control with something else (the queue's drag handle)
    // would otherwise have no way to (re)try a download at all — which matters precisely
    // when an automatic fetch failed.
    val sheetDownload = onDownload.takeIf {
        trailing != null && downloadState !is DownloadState.Downloaded && downloadState !is DownloadState.Downloading
    }
    val hasMenu = listOfNotNull(
        onPlayNext, onAddToQueue, onAddToPlaylist, onRemoveFromPlaylist, onPeek,
        downloadVideo, sheetDownload, onGoToSource, onSetPlayed, onMoveToTop, onMoveToBottom,
    ).isNotEmpty()
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
        ThumbnailWithProgress(item, playState)
        Spacer(Modifier.width(12.dp))
        TitleAndSubtitle(item, subtitle, pillar, playState, downloadState, Modifier.weight(1f))
        if (hasMenu) {
            IconButton(onClick = { showSheet = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.queue_menu))
            }
        }
        if (trailing != null) trailing() else DownloadControl(downloadState, onDownload, onDeleteDownload)
    }
    if (showSheet) {
        ActionSheet(
            title = item.title,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            onRemoveFromPlaylist = onRemoveFromPlaylist,
            onPeek = onPeek,
            onDownloadVideo = downloadVideo,
            onDownload = sheetDownload,
            onSwitchMode = onSwitchMode,
            audioMode = audioMode,
            onGoToSource = onGoToSource,
            goToSourceLabelRes = goToSourceLabelRes,
            onMoveToTop = onMoveToTop,
            onMoveToBottom = onMoveToBottom,
            onSetPlayed = onSetPlayed,
            played = playState.isPlayed,
            onDismiss = { showSheet = false },
        )
    }
}

/** The artwork with a progress sliver beneath it, so "you are here" needs no words. */
@Composable
private fun ThumbnailWithProgress(item: MediaItem, playState: PlayState) {
    Column {
        MediaThumbnail(
            url = item.thumbnailUrl,
            contentDescription = item.title,
            modifier = Modifier.size(width = THUMBNAIL_WIDTH, height = THUMBNAIL_HEIGHT),
        )
        PlayProgressSliver(playState, Modifier.width(THUMBNAIL_WIDTH))
    }
}

@Composable
private fun TitleAndSubtitle(
    item: MediaItem,
    subtitle: String?,
    pillar: MediaKind,
    playState: PlayState,
    downloadState: DownloadState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (item.contentKind != MediaContentKind.STANDARD) {
            ContentKindBadge(item.contentKind)
            Spacer(Modifier.height(2.dp))
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            // Two lines keeps a list scannable; long podcast titles were running to
            // five, which made every row a paragraph.
            maxLines = TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(playedTitleAlpha(playState)),
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
        MediaItemStatus(pillar, playState, downloadState, StatusRowSpacing)
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
