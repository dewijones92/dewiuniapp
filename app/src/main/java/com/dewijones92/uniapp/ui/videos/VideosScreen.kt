package com.dewijones92.uniapp.ui.videos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.dewijones92.uniapp.domain.MediaContentKind
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.innertube.feeds.AccountFeed
import com.dewijones92.uniapp.innertube.playlists.Playlist
import com.dewijones92.uniapp.theme.UniAppTheme
import com.dewijones92.uniapp.ui.channel.ChannelScreen
import com.dewijones92.uniapp.ui.common.EmptyState
import com.dewijones92.uniapp.ui.common.MediaItemRow
import com.dewijones92.uniapp.ui.common.MediaSort
import com.dewijones92.uniapp.ui.common.SectionHeaderWithSort
import com.dewijones92.uniapp.ui.common.mediaItemSubtitle
import com.dewijones92.uniapp.ui.notifications.NotificationsScreen
import com.dewijones92.uniapp.ui.notifications.NotificationsViewModel
import com.dewijones92.uniapp.ui.playlist.PlaylistScreen
import com.dewijones92.uniapp.ui.playlist.PlaylistsListScreen

@Composable
fun VideosScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
    onOpenShorts: (List<MediaItem>) -> Unit = {},
) {
    val viewModel: VideosViewModel = viewModel(factory = VideosViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Overlays over the feed: a tapped channel chip, the playlists list (and a
    // tapped playlist), and the new-uploads notifications.
    val notificationsViewModel: NotificationsViewModel =
        viewModel(factory = NotificationsViewModel.factory(container))
    val newUploadsCount by notificationsViewModel.count.collectAsStateWithLifecycle()
    var browsingChannel by remember { mutableStateOf<MediaSource.VideoChannel?>(null) }
    var showPlaylists by remember { mutableStateOf(false) }
    var showNotifications by remember { mutableStateOf(false) }
    var browsingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    val channel = browsingChannel
    val playlist = browsingPlaylist

    when {
        playlist != null ->
            PlaylistScreen(container, playlist, onBack = { browsingPlaylist = null }, modifier = modifier)
        showPlaylists ->
            PlaylistsListScreen(
                container,
                onOpen = { browsingPlaylist = it },
                onBack = { showPlaylists = false },
                modifier = modifier,
            )
        showNotifications ->
            NotificationsScreen(notificationsViewModel, onBack = { showNotifications = false }, modifier = modifier)
        channel != null ->
            ChannelScreen(container, channel, onBack = { browsingChannel = null }, modifier = modifier)
        else -> VideosContent(
            state = state,
            newUploadsCount = newUploadsCount,
            onSubscribe = viewModel::subscribe,
            onDialogClosed = viewModel::resetSubscribing,
            onPlay = viewModel::play,
            onDownload = viewModel::download,
            onDeleteDownload = viewModel::deleteDownload,
            onSelectFeed = viewModel::selectFeed,
            onChannelClick = { browsingChannel = it },
            onOpenPlaylists = { showPlaylists = true },
            onOpenShorts = { onOpenShorts(state.videos.filter { it.contentKind == MediaContentKind.SHORT }) },
            onOpenNotifications = { showNotifications = true },
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
    newUploadsCount: Int,
    onSubscribe: (String) -> Unit,
    onDialogClosed: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onDeleteDownload: (MediaItem) -> Unit,
    onSelectFeed: (AccountFeed?) -> Unit,
    onChannelClick: (MediaSource.VideoChannel) -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenShorts: () -> Unit,
    onOpenNotifications: () -> Unit,
    onRefresh: () -> Unit,
    onSetSort: (MediaSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        Column {
            if (state.signedIn) {
                VideosTopBar(newUploadsCount, onOpenNotifications)
            }
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
                        onOpenPlaylists = onOpenPlaylists,
                        onOpenShorts = onOpenShorts,
                        onSetSort = onSetSort,
                    )
                }
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

/** The top row with the new-uploads bell, shown when signed in. */
@Composable
private fun VideosTopBar(newUploadsCount: Int, onOpenNotifications: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        NotificationsBell(newUploadsCount, onOpenNotifications)
    }
}

@Composable
private fun NotificationsBell(count: Int, onClick: () -> Unit) {
    BadgedBox(badge = { if (count > 0) Badge { Text(count.toString()) } }) {
        IconButton(onClick = onClick) {
            Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.notifications_title))
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
    onOpenPlaylists: () -> Unit,
    onOpenShorts: () -> Unit,
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
            item { FeedSelector(state.selectedFeed, onSelectFeed, onOpenPlaylists, onOpenShorts) }
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
private fun FeedSelector(
    selected: AccountFeed?,
    onSelectFeed: (AccountFeed?) -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenShorts: () -> Unit,
) {
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
        // Not feed filters — open the Shorts reel and the playlists list.
        item {
            AssistChip(
                onClick = onOpenShorts,
                label = { Text(stringResource(R.string.shorts_title)) },
            )
        }
        item {
            AssistChip(
                onClick = onOpenPlaylists,
                label = { Text(stringResource(R.string.playlists_title)) },
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
            newUploadsCount = 0,
            onChannelClick = {},
            onOpenPlaylists = {},
            onOpenShorts = {},
            onOpenNotifications = {},
            onRefresh = {},
            onSetSort = {},
        )
    }
}
