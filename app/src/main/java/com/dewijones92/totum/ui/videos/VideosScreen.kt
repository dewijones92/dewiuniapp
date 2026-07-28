package com.dewijones92.totum.ui.videos

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.dewijones92.totum.R
import com.dewijones92.totum.di.AppContainer
import com.dewijones92.totum.domain.DownloadState
import com.dewijones92.totum.domain.MediaContentKind
import com.dewijones92.totum.domain.MediaFilter
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaKind
import com.dewijones92.totum.domain.MediaSource
import com.dewijones92.totum.domain.PlayState
import com.dewijones92.totum.domain.filteredBy
import com.dewijones92.totum.innertube.feeds.AccountFeed
import com.dewijones92.totum.theme.TotumTheme
import com.dewijones92.totum.ui.channel.ChannelScreen
import com.dewijones92.totum.ui.common.EmptyState
import com.dewijones92.totum.ui.common.LoadMoreOnScrollToEnd
import com.dewijones92.totum.ui.common.LoadingMoreFooter
import com.dewijones92.totum.ui.common.LocalPlayStates
import com.dewijones92.totum.ui.common.MediaFilterChips
import com.dewijones92.totum.ui.common.MediaItemActions
import com.dewijones92.totum.ui.common.MediaItemRow
import com.dewijones92.totum.ui.common.MediaListSkeleton
import com.dewijones92.totum.ui.common.MediaSort
import com.dewijones92.totum.ui.common.SectionHeaderWithSort
import com.dewijones92.totum.ui.common.TotumFab
import com.dewijones92.totum.ui.common.TrackPlace
import com.dewijones92.totum.ui.common.mediaItemSubtitle
import com.dewijones92.totum.ui.common.rememberMediaItemActions
import com.dewijones92.totum.ui.notifications.NotificationsScreen
import com.dewijones92.totum.ui.notifications.NotificationsViewModel
import com.dewijones92.totum.ui.playlist.PlaylistScreen
import com.dewijones92.totum.ui.playlist.PlaylistsListScreen

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
    val nav = rememberSaveable(saver = VideosNav.Saver) { VideosNav() }
    // At screen level, so it reports even when the feed is empty — the inner tracker sits
    // inside the non-empty-feed composable and stays silent in exactly the case most likely
    // to be the bug (a restored scroll index applied to a list that has not arrived yet).
    TrackPlace("videos-screen") { "${nav.describe()} videos=${state.videos.size} signedIn=${state.signedIn}" }
    val actions = rememberMediaItemActions(container)
    val switchMode = rememberModeSwitch(actions)
    val settings by container.appPreferences.settings.collectAsStateWithLifecycle()

    when {
        nav.overlayShowing -> VideosOverlay(container, nav, notificationsViewModel, modifier)
        else -> VideosContent(
            state = state,
            filter = settings.mediaFilter,
            onSetFilter = container.appPreferences::setMediaFilter,
            newUploadsCount = notificationsViewModel.count.collectAsStateWithLifecycle().value,
            actions = actions,
            onSubscribe = viewModel::subscribe,
            onDialogClosed = viewModel::resetSubscribing,
            onPlay = viewModel::play,
            onDownload = viewModel::download,
            onDeleteDownload = viewModel::deleteDownload,
            onSelectFeed = viewModel::selectFeed,
            onChannelClick = { nav.channel = it },
            onSwitchMode = switchMode,
            onGoToChannel = { item ->
                actions.goToSource(item) { source ->
                    (source as? MediaSource.VideoChannel)?.let { nav.channel = it }
                }
            },
            onOpenPlaylists = { nav.showPlaylists = true },
            onOpenShorts = { onOpenShorts(state.videos.filter { it.contentKind == MediaContentKind.SHORT }) },
            onOpenNotifications = { nav.showNotifications = true },
            onRefresh = viewModel::refresh,
            onSetSort = viewModel::setSort,
            onLoadMore = viewModel::loadMore,
            modifier = modifier,
        )
    }
}

/**
 * The row action that flips between listening and watching. It sets the **mode**, not
 * just this item, and says so — a row action quietly changing a global setting would
 * be baffling.
 */
@Composable
private fun rememberModeSwitch(actions: MediaItemActions): (MediaItem) -> Unit {
    val audioOn = stringResource(R.string.mode_audio_on)
    val videoOn = stringResource(R.string.mode_video_on)
    return { item ->
        actions.switchMode(
            item = item,
            toAudio = !actions.audioMode,
            audioOnMessage = audioOn,
            videoOnMessage = videoOn,
        )
    }
}

/**
 * Which full-screen overlay the Videos tab is showing, if any. A holder rather than four
 * loose booleans: the states are mutually exclusive in practice, and naming the concept
 * keeps the screen's `when` readable as "an overlay, or the feed".
 */
/**
 * The overlays that sit over the feed. Order matters: a tapped playlist wins over the
 * playlists list that opened it, so backing out returns to the list rather than the feed.
 */
@Composable
private fun VideosOverlay(
    container: AppContainer,
    nav: VideosNav,
    notificationsViewModel: NotificationsViewModel,
    modifier: Modifier = Modifier,
) {
    val playlist = nav.playlist
    val channel = nav.channel
    when {
        playlist != null ->
            PlaylistScreen(container, playlist, onBack = { nav.playlist = null }, modifier = modifier)
        nav.showPlaylists ->
            PlaylistsListScreen(
                container,
                onOpen = { nav.playlist = it },
                onBack = { nav.showPlaylists = false },
                modifier = modifier,
            )
        nav.showNotifications ->
            NotificationsScreen(notificationsViewModel, onBack = { nav.showNotifications = false }, modifier = modifier)
        channel != null ->
            ChannelScreen(
                container,
                channel,
                onBack = { nav.channel = null },
                onOpenPlaylist = { nav.playlist = it },
                modifier = modifier,
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideosContent(
    state: VideosViewModel.UiState,
    newUploadsCount: Int,
    actions: MediaItemActions,
    onSubscribe: (String) -> Unit,
    onDialogClosed: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onDeleteDownload: (MediaItem) -> Unit,
    onSelectFeed: (AccountFeed?) -> Unit,
    onChannelClick: (MediaSource.VideoChannel) -> Unit,
    onSwitchMode: (MediaItem) -> Unit,
    onGoToChannel: (MediaItem) -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenShorts: () -> Unit,
    onOpenNotifications: () -> Unit,
    onRefresh: () -> Unit,
    onSetSort: (MediaSort) -> Unit,
    onLoadMore: () -> Unit,
    filter: MediaFilter,
    onSetFilter: (MediaFilter) -> Unit,
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
                        actions,
                        onPlay,
                        onDownload,
                        onDeleteDownload,
                        onSelectFeed,
                        onChannelClick = onChannelClick,
                        onSwitchMode = onSwitchMode,
                        onGoToChannel = onGoToChannel,
                        onOpenPlaylists = onOpenPlaylists,
                        onOpenShorts = onOpenShorts,
                        onSetSort = onSetSort,
                        onLoadMore = onLoadMore,
                        filter = filter,
                        onSetFilter = onSetFilter,
                    )
                }
            }
        }

        TotumFab(
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
    actions: MediaItemActions,
    onPlay: (MediaItem) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onDeleteDownload: (MediaItem) -> Unit,
    onSelectFeed: (AccountFeed?) -> Unit,
    onChannelClick: (MediaSource.VideoChannel) -> Unit,
    onSwitchMode: (MediaItem) -> Unit,
    onGoToChannel: (MediaItem) -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenShorts: () -> Unit,
    onSetSort: (MediaSort) -> Unit,
    onLoadMore: () -> Unit,
    filter: MediaFilter,
    onSetFilter: (MediaFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playStates = LocalPlayStates.current
    val listState = rememberLazyListState()
    // The item count belongs in the trail as much as the offset: a restored scroll index
    // cannot survive being applied to an empty list, so "scroll=40 videos=0" and
    // "scroll=0 videos=40" are different bugs that look identical without it.
    TrackPlace("videos") {
        "feed=${state.selectedFeed} scroll=${listState.firstVisibleItemIndex}" +
            "+${listState.firstVisibleItemScrollOffset} videos=${state.videos.size}"
    }
    LoadMoreOnScrollToEnd(listState, enabled = state.canLoadMore && !state.loadingMore, loadMore = onLoadMore)
    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        if (state.subscriptions.isNotEmpty()) {
            item { SubscriptionChips(state.subscriptions, onChannelClick) }
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
                item { MediaFilterChips(selected = filter, onSelect = onSetFilter) }
                // Filtered here rather than in the view model so the chips react instantly
                // without a round trip, and so one domain function serves every feed.
                val shown = state.videos.filteredBy(filter) { playStates[it] ?: PlayState.Unplayed }
                if (shown.isEmpty()) {
                    item { FeedMessage(stringResource(R.string.filter_hides_everything)) }
                }
                items(shown, key = { it.id.value }) { video ->
                    MediaItemRow(
                        item = video,
                        subtitle = mediaItemSubtitle(video),
                        downloadState = state.downloadStates[video.id] ?: DownloadState.NotDownloaded,
                        pillar = MediaKind.VIDEO,
                        onPlay = { onPlay(video) },
                        onDownload = { onDownload(video) },
                        onDeleteDownload = { onDeleteDownload(video) },
                        onPlayNext = { actions.playNext(video) },
                        onAddToQueue = { actions.addToQueue(video) },
                        onAddToPlaylist = { actions.addToPlaylist(video) },
                        onPeek = { actions.peek(video) },
                        onDownloadVideo = { onDownload(video) },
                        onSwitchMode = { onSwitchMode(video) },
                        audioMode = actions.audioMode,
                        onGoToSource = { onGoToChannel(video) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
                if (state.loadingMore) item { LoadingMoreFooter() }
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
    // A skeleton rather than a spinner: it says what is coming and roughly how much, so the
    // screen reads as filling in rather than blocked, and nothing jumps when content lands.
    MediaListSkeleton()
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

/**
 * Where the Videos tab was, for the place trail.
 *
 * The item count is here for a reason: a restored scroll index cannot survive being applied
 * to an empty list, so "scroll=40 videos=0" and "scroll=0 videos=40" are different bugs
 * needing different fixes, and without the count they look identical in a report.
 */
/** The horizontal strip of subscribed channels above the feed. */
@Composable
private fun SubscriptionChips(
    subscriptions: List<MediaSource.VideoChannel>,
    onChannelClick: (MediaSource.VideoChannel) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(subscriptions) { channel ->
            AssistChip(onClick = { onChannelClick(channel) }, label = { Text(channel.title) })
        }
    }
}

private fun feedTitleRes(feed: AccountFeed?): Int = when (feed) {
    null -> R.string.latest_videos
    else -> feedChipRes(feed)
}

@Preview(showBackground = true)
@Composable
private fun VideosContentPreview() {
    TotumTheme {
        VideosContent(
            state = VideosViewModel.UiState(),
            actions = rememberMediaItemActions(com.dewijones92.totum.di.fake.FakeAppContainer()),
            onSubscribe = {},
            onDialogClosed = {},
            onPlay = {},
            onDownload = {},
            onDeleteDownload = {},
            onSelectFeed = {},
            newUploadsCount = 0,
            onChannelClick = {},
            onSwitchMode = {},
            onGoToChannel = {},
            onOpenPlaylists = {},
            onOpenShorts = {},
            onOpenNotifications = {},
            onRefresh = {},
            onSetSort = {},
            onLoadMore = {},
            filter = MediaFilter.ALL,
            onSetFilter = {},
        )
    }
}
