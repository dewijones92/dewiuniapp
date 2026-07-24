package com.dewijones92.uniapp.ui.podcasts

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
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import com.dewijones92.uniapp.theme.UniAppTheme
import com.dewijones92.uniapp.ui.common.EmptyState
import com.dewijones92.uniapp.ui.common.MediaItemRow
import com.dewijones92.uniapp.ui.common.MediaSort
import com.dewijones92.uniapp.ui.common.SectionHeaderWithSort
import com.dewijones92.uniapp.ui.common.mediaItemSubtitle
import com.dewijones92.uniapp.ui.playlist.rememberPlaylistAdder

@Composable
fun PodcastsScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: PodcastsViewModel = viewModel(factory = PodcastsViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PodcastsContent(
        state = state,
        onSubscribe = viewModel::subscribe,
        onDialogClosed = viewModel::resetSubscribing,
        onPlayEpisode = viewModel::play,
        onDownload = viewModel::download,
        onDeleteDownload = viewModel::deleteDownload,
        onRefresh = viewModel::refresh,
        onSetSort = viewModel::setSort,
        onEnqueue = viewModel::enqueue,
        onPlayNext = viewModel::playNext,
        onAddToPlaylist = rememberPlaylistAdder(container),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PodcastsContent(
    state: PodcastsViewModel.UiState,
    onSubscribe: (String) -> Unit,
    onDialogClosed: () -> Unit,
    onPlayEpisode: (MediaItem) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onDeleteDownload: (MediaItem) -> Unit,
    onRefresh: () -> Unit,
    onSetSort: (MediaSort) -> Unit,
    onEnqueue: (MediaItem) -> Unit,
    onPlayNext: (MediaItem) -> Unit,
    onAddToPlaylist: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.subscriptions.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Podcasts,
                    headline = stringResource(R.string.podcasts_empty_headline),
                    supportingText = stringResource(R.string.podcasts_empty_supporting),
                )
            } else {
                SubscriptionsAndEpisodes(
                    state,
                    onPlayEpisode,
                    onDownload,
                    onDeleteDownload,
                    onSetSort,
                    onEnqueue,
                    onPlayNext,
                    onAddToPlaylist,
                )
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_podcast))
        }

        if (showAddDialog) {
            AddPodcastDialog(
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
private fun SubscriptionsAndEpisodes(
    state: PodcastsViewModel.UiState,
    onPlayEpisode: (MediaItem) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onDeleteDownload: (MediaItem) -> Unit,
    onSetSort: (MediaSort) -> Unit,
    onEnqueue: (MediaItem) -> Unit,
    onPlayNext: (MediaItem) -> Unit,
    onAddToPlaylist: (MediaItem) -> Unit,
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
            SectionHeaderWithSort(
                title = stringResource(R.string.latest_episodes),
                sort = state.sort,
                onSetSort = onSetSort,
            )
        }
        items(state.episodes, key = { it.id.value }) { episode ->
            MediaItemRow(
                item = episode,
                subtitle = mediaItemSubtitle(episode),
                downloadState = state.downloadStates[episode.id] ?: DownloadState.NotDownloaded,
                onPlay = { onPlayEpisode(episode) },
                onDownload = { onDownload(episode) },
                onDeleteDownload = { onDeleteDownload(episode) },
                onPlayNext = { onPlayNext(episode) },
                onAddToQueue = { onEnqueue(episode) },
                onAddToPlaylist = { onAddToPlaylist(episode) },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PodcastsContentPreview() {
    val sourceId = SourceId("preview-feed")
    val state = PodcastsViewModel.UiState(
        subscriptions = listOf(
            Subscription(
                source = MediaSource.PodcastFeed(
                    id = sourceId,
                    title = "Preview podcast",
                    feedUrl = HttpUrl.of("https://example.com/feed.xml"),
                ),
                subscribedAt = java.time.Instant.EPOCH,
            ),
        ),
        episodes = listOf(com.dewijones92.uniapp.data.podcast.fake.FakePodcastRepository.sampleEpisode(sourceId)),
    )
    UniAppTheme {
        PodcastsContent(
            state = state,
            onSubscribe = {},
            onDialogClosed = {},
            onPlayEpisode = {},
            onDownload = {},
            onDeleteDownload = {},
            onRefresh = {},
            onSetSort = {},
            onEnqueue = {},
            onPlayNext = {},
            onAddToPlaylist = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PodcastsEmptyPreview() {
    UniAppTheme {
        PodcastsContent(
            state = PodcastsViewModel.UiState(),
            onSubscribe = {},
            onDialogClosed = {},
            onPlayEpisode = {},
            onDownload = {},
            onDeleteDownload = {},
            onRefresh = {},
            onSetSort = {},
            onEnqueue = {},
            onPlayNext = {},
            onAddToPlaylist = {},
        )
    }
}
