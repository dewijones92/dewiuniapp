package com.dewijones92.uniapp.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItem

// A 16:9 leading thumbnail — the shape video stills want; square podcast art
// centre-crops into it cleanly.
private val THUMBNAIL_WIDTH = 96.dp
private val THUMBNAIL_HEIGHT = 54.dp

/**
 * One media item in a list — used identically for podcast episodes and any
 * other [MediaItem]. Tapping the row plays it; the leading [MediaThumbnail]
 * shows its artwork; the trailing control reflects and drives its offline
 * [DownloadState].
 */
@Composable
fun MediaItemRow(
    item: MediaItem,
    subtitle: String?,
    downloadState: DownloadState,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = item.mediaUrl != null, onClick = onPlay)
            .padding(16.dp),
    ) {
        MediaThumbnail(
            url = item.thumbnailUrl,
            contentDescription = item.title,
            modifier = Modifier.size(width = THUMBNAIL_WIDTH, height = THUMBNAIL_HEIGHT),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
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
        DownloadControl(downloadState, onDownload, onDeleteDownload)
    }
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
