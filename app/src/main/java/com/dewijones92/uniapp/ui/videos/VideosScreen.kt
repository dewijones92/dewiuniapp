package com.dewijones92.uniapp.ui.videos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.theme.UniAppTheme
import com.dewijones92.uniapp.ui.common.EmptyState
import com.dewijones92.uniapp.ui.common.MediaItemRow
import com.dewijones92.uniapp.ui.common.mediaItemSubtitle

@Composable
fun VideosScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: VideosViewModel = viewModel(factory = VideosViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    VideosContent(
        state = state,
        onSubscribe = viewModel::subscribe,
        onDialogClosed = viewModel::resetSubscribing,
        onPlay = viewModel::play,
        onDownload = viewModel::download,
        onDeleteDownload = viewModel::deleteDownload,
        modifier = modifier,
    )
}

@Composable
internal fun VideosContent(
    state: VideosViewModel.UiState,
    onSubscribe: (String) -> Unit,
    onDialogClosed: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onDeleteDownload: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        if (state.subscriptions.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.SmartDisplay,
                headline = stringResource(R.string.videos_empty_headline),
                supportingText = stringResource(R.string.videos_empty_supporting),
            )
        } else {
            ChannelsAndVideos(state, onPlay, onDownload, onDeleteDownload)
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_channel))
        }

        if (showAddDialog) {
            AddChannelDialog(
                subscribing = state.subscribing,
                onSubscribe = onSubscribe,
                onDismiss = {
                    showAddDialog = false
                    onDialogClosed()
                },
            )
        }
    }
}

@Composable
private fun ChannelsAndVideos(
    state: VideosViewModel.UiState,
    onPlay: (MediaItem) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onDeleteDownload: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(state.subscriptions) { subscription ->
                    AssistChip(onClick = {}, label = { Text(subscription.source.title) })
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.latest_videos),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        items(state.videos, key = { it.id.value }) { video ->
            MediaItemRow(
                item = video,
                subtitle = mediaItemSubtitle(video),
                downloadState = state.downloadStates[video.id] ?: DownloadState.NotDownloaded,
                onPlay = { onPlay(video) },
                onDownload = { onDownload(video) },
                onDeleteDownload = { onDeleteDownload(video) },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VideosContentPreview() {
    UniAppTheme {
        VideosContent(
            state = VideosViewModel.UiState(),
            onSubscribe = {},
            onDialogClosed = {},
            onPlay = {},
            onDownload = {},
            onDeleteDownload = {},
        )
    }
}
