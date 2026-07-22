package com.dewijones92.uniapp.ui.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.ui.common.MediaItemRow
import com.dewijones92.uniapp.ui.common.mediaItemSubtitle

/**
 * New uploads from your subscriptions since you last looked — the stand-in for
 * YouTube's notification bell. Shows a snapshot of what was new on open, then
 * marks everything seen so the badge clears (the list itself stays put).
 */
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Snapshot the new uploads on open; marking them seen clears the badge but
    // must not empty the list the user is looking at.
    val uploads = remember { viewModel.snapshotUploads() }
    LaunchedEffect(Unit) { viewModel.markAllSeen() }

    Surface(modifier = modifier.fillMaxSize()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Text(
                    text = stringResource(R.string.notifications_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (uploads.isEmpty()) {
                Text(
                    text = stringResource(R.string.notifications_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uploads, key = { it.id.value }) { video ->
                        MediaItemRow(
                            item = video,
                            subtitle = mediaItemSubtitle(video),
                            downloadState = DownloadState.NotDownloaded,
                            onPlay = { viewModel.play(video) },
                            onDownload = {},
                            onDeleteDownload = {},
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}
