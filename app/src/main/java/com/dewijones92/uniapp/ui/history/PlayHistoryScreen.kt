package com.dewijones92.uniapp.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.ui.common.MediaItemRow
import com.dewijones92.uniapp.ui.common.mediaItemSubtitle
import com.dewijones92.uniapp.ui.playlist.rememberPlaylistAdder

/** Recently-played history across both pillars: tap to replay, or clear all. */
@Composable
fun PlayHistoryScreen(container: AppContainer, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: PlayHistoryViewModel = viewModel(factory = PlayHistoryViewModel.factory(container))
    val items by viewModel.items.collectAsStateWithLifecycle()
    val downloadStates by viewModel.downloadStates.collectAsStateWithLifecycle()
    val addToPlaylist = rememberPlaylistAdder(container)

    Surface(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HistoryHeader(onBack = onBack, onClear = viewModel::clear, canClear = items.isNotEmpty())
            if (items.isEmpty()) {
                HistoryEmpty()
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(items, key = { it.item.id.value }) { entry ->
                        MediaItemRow(
                            item = entry.item,
                            subtitle = mediaItemSubtitle(entry.item),
                            downloadState = downloadStates[entry.item.id] ?: DownloadState.NotDownloaded,
                            onPlay = { viewModel.play(entry) },
                            onDownload = { viewModel.download(entry.item) },
                            onDeleteDownload = { viewModel.deleteDownload(entry.item.id) },
                            onAddToPlaylist = { addToPlaylist(entry.item) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryHeader(onBack: () -> Unit, onClear: () -> Unit, canClear: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        if (canClear) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.history_clear)) }
        }
    }
}

@Composable
private fun HistoryEmpty() {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.history_empty),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
