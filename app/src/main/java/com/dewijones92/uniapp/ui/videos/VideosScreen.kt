package com.dewijones92.uniapp.ui.videos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.innertube.feeds.AccountFeed
import com.dewijones92.uniapp.theme.UniAppTheme
import com.dewijones92.uniapp.ui.channel.ChannelScreen
import com.dewijones92.uniapp.ui.common.EmptyState
import com.dewijones92.uniapp.ui.common.MediaItemRow
import com.dewijones92.uniapp.ui.common.MediaSort
import com.dewijones92.uniapp.ui.common.SectionHeaderWithSort
import com.dewijones92.uniapp.ui.common.mediaItemSubtitle

@Composable
fun VideosScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: VideosViewModel = viewModel(factory = VideosViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // A tapped channel chip opens that channel's page over the feed.
    var browsingChannel by remember { mutableStateOf<MediaSource.VideoChannel?>(null) }
    val channel = browsingChannel

    if (channel != null) {
        ChannelScreen(container, channel, onBack = { browsingChannel = null }, modifier = modifier)
    } else {
        VideosContent(
            state = state,
            onSubscribe = viewModel::subscribe,
            onDialogClosed = viewModel::resetSubscribing,
            onPlay = viewModel::play,
            onDownload = viewModel::download,
            onDeleteDownload = viewModel::deleteDownload,
            onSelectFeed = viewModel::selectFeed,
            onChannelClick = { browsingChannel = it },
            onRefresh = viewModel::refresh,
            onSetSort = viewModel::setSort,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideosContent(
    state: VideosViewModel.UiState,
    onSubscribe: (String) -> Unit,
    onDialogClosed: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onDeleteDownload: (MediaItem) -> Unit,
    onSelectFeed: (AccountFeed?) -> Unit,
    onChannelClick: (MediaSource.VideoChannel) -> Unit,
    onRefresh: () -> Unit,
    onSetSort: (MediaSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.subscriptions.isEmpty() && !state.signedIn) {
                EmptyState(
                    icon = Icons.Outlined.SmartDisplay,
                    headline = stringResource(R.string.videos_empty_headline),
                    supportingText = stringResource(R.string.videos_empty_supporting),
                )
            } else {
                ChannelsAndVideos(
                    state,
                    onPlay,
                    onDownload,
                    onDeleteDownload,
                    onSelectFeed,
                    onChannelClick = onChannelClick,
                    onSetSort = onSetSort,
                )
            }
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
    onSelectFeed: (AccountFeed?) -> Unit,
    onChannelClick: (MediaSource.VideoChannel) -> Unit,
    onSetSort: (MediaSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (state.subscriptions.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items(state.subscriptions) { channel ->
                        AssistChip(
                            onClick = { onChannelClick(channel) },
                            label = { Text(channel.title) },
                        )
                    }
                }
            }
        }
        if (state.signedIn) {
            item { FeedSelector(state.selectedFeed, onSelectFeed) }
        }
        when {
            state.feedLoading -> item { FeedLoading() }
            state.feedError -> item { FeedMessage(stringResource(R.string.feed_error)) }
            state.videos.isEmpty() -> item { FeedMessage(stringResource(R.string.feed_empty)) }
            else -> {
                item {
                    SectionHeaderWithSort(
                        title = stringResource(feedTitleRes(state.selectedFeed)),
                        sort = state.sort,
                        onSetSort = onSetSort,
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
    }
}

@Composable
private fun FeedSelector(selected: AccountFeed?, onSelectFeed: (AccountFeed?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        items(AccountFeed.entries) { feed ->
            FilterChip(
                selected = feed == selected,
                onClick = { onSelectFeed(feed) },
                label = { Text(stringResource(feedChipRes(feed))) },
            )
        }
    }
}

@Composable
private fun FeedLoading() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun FeedMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
    )
}

private fun feedChipRes(feed: AccountFeed): Int = when (feed) {
    AccountFeed.RECOMMENDED -> R.string.feed_home
    AccountFeed.SUBSCRIPTIONS -> R.string.feed_subscriptions
    AccountFeed.WATCH_LATER -> R.string.feed_watch_later
    AccountFeed.HISTORY -> R.string.feed_history
}

private fun feedTitleRes(feed: AccountFeed?): Int = when (feed) {
    null -> R.string.latest_videos
    else -> feedChipRes(feed)
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
            onSelectFeed = {},
            onChannelClick = {},
            onRefresh = {},
            onSetSort = {},
        )
    }
}
