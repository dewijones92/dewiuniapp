package com.dewijones92.uniapp.ui.channel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.innertube.playlists.Playlist
import com.dewijones92.uniapp.ui.channel.ChannelViewModel.TabState
import com.dewijones92.uniapp.ui.common.MediaItemRow
import com.dewijones92.uniapp.ui.common.MediaThumbnail
import com.dewijones92.uniapp.ui.common.mediaItemSubtitle
import com.dewijones92.uniapp.ui.channel.ChannelViewModel.Tab as ChannelTab

/**
 * A channel's page: tabbed Videos / Shorts / Playlists (via InnerTube, so videos
 * show their upload dates) plus a subscribe toggle. Shown as a full-screen layer
 * over the Videos tab; video rows are the same shared [MediaItemRow] used
 * everywhere, so playing and downloading behave identically to the feed.
 */
@Composable
fun ChannelScreen(
    container: AppContainer,
    source: MediaSource.VideoChannel,
    onBack: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ChannelViewModel =
        viewModel(key = source.id.value, factory = ChannelViewModel.factory(container, source))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ChannelContent(
        state = state,
        onBack = onBack,
        onToggleSubscribed = viewModel::toggleSubscribed,
        onSelectTab = viewModel::selectTab,
        onPlay = viewModel::play,
        onDownload = viewModel::download,
        onDeleteDownload = viewModel::deleteDownload,
        onOpenPlaylist = onOpenPlaylist,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelContent(
    state: ChannelViewModel.UiState,
    onBack: () -> Unit,
    onToggleSubscribed: () -> Unit,
    onSelectTab: (ChannelTab) -> Unit,
    onPlay: (MediaItem) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onDeleteDownload: (MediaItem) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ChannelHeader(
                title = state.title,
                subscribed = state.subscribed,
                onBack = onBack,
                onToggleSubscribed = onToggleSubscribed,
            )
            SecondaryTabRow(selectedTabIndex = state.tab.ordinal) {
                ChannelTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == state.tab,
                        onClick = { onSelectTab(tab) },
                        text = { Text(stringResource(tab.labelRes())) },
                    )
                }
            }
            when (state.tab) {
                ChannelTab.VIDEOS -> MediaItemTab(
                    state.videos,
                    state.downloadStates,
                    onPlay,
                    onDownload,
                    onDeleteDownload
                )
                ChannelTab.SHORTS -> MediaItemTab(
                    state.shorts,
                    state.downloadStates,
                    onPlay,
                    onDownload,
                    onDeleteDownload
                )
                ChannelTab.PLAYLISTS -> PlaylistTab(state.playlists, onOpenPlaylist)
            }
        }
    }
}

private fun ChannelTab.labelRes(): Int = when (this) {
    ChannelTab.VIDEOS -> R.string.channel_tab_videos
    ChannelTab.SHORTS -> R.string.channel_tab_shorts
    ChannelTab.PLAYLISTS -> R.string.channel_tab_playlists
}

@Composable
private fun MediaItemTab(
    tab: TabState<MediaItem>,
    downloadStates: Map<com.dewijones92.uniapp.domain.MediaItemId, DownloadState>,
    onPlay: (MediaItem) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onDeleteDownload: (MediaItem) -> Unit,
) {
    when {
        tab.loading && tab.items.isEmpty() -> CenteredProgress()
        tab.error -> Message(stringResource(R.string.feed_error))
        tab.loaded && tab.items.isEmpty() -> Message(stringResource(R.string.feed_empty))
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(tab.items, key = { it.id.value }) { video ->
                MediaItemRow(
                    item = video,
                    subtitle = mediaItemSubtitle(video),
                    downloadState = downloadStates[video.id] ?: DownloadState.NotDownloaded,
                    onPlay = { onPlay(video) },
                    onDownload = { onDownload(video) },
                    onDeleteDownload = { onDeleteDownload(video) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun PlaylistTab(tab: TabState<Playlist>, onOpen: (Playlist) -> Unit) {
    when {
        tab.loading && tab.items.isEmpty() -> CenteredProgress()
        tab.error -> Message(stringResource(R.string.feed_error))
        tab.loaded && tab.items.isEmpty() -> Message(stringResource(R.string.feed_empty))
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(tab.items, key = { it.browseId }) { playlist ->
                PlaylistRow(playlist, onClick = { onOpen(playlist) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        MediaThumbnail(
            url = playlist.thumbnailUrl,
            contentDescription = playlist.title,
            modifier = Modifier.size(width = 96.dp, height = 54.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            playlist.videoCountText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChannelHeader(
    title: String,
    subscribed: Boolean,
    onBack: () -> Unit,
    onToggleSubscribed: () -> Unit,
) {
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
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        if (subscribed) {
            OutlinedButton(onClick = onToggleSubscribed) {
                Text(stringResource(R.string.channel_unsubscribe))
            }
        } else {
            FilledTonalButton(onClick = onToggleSubscribed) {
                Text(stringResource(R.string.channel_subscribe))
            }
        }
    }
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
    )
}
