package com.dewijones92.uniapp.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

/**
 * One media item in a list — used identically for podcast episodes and any
 * other [MediaItem]. Tapping the row plays it; the trailing control reflects
 * and drives its offline [DownloadState].
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
