package com.dewijones92.uniapp.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.dewijones92.uniapp.di.fake.FakeAppContainer
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.PlaylistId
import com.dewijones92.uniapp.theme.UniAppTheme
import com.dewijones92.uniapp.ui.common.BuildInfoFooter
import com.dewijones92.uniapp.ui.common.MediaItemRow
import com.dewijones92.uniapp.ui.common.MediaSort
import com.dewijones92.uniapp.ui.common.SectionHeaderWithSort
import com.dewijones92.uniapp.ui.common.mediaItemSubtitle
import com.dewijones92.uniapp.ui.library.LibraryViewModel.DownloadedItem
import com.dewijones92.uniapp.ui.playlist.LocalPlaylistDetailScreen
import com.dewijones92.uniapp.ui.playlist.LocalPlaylistsScreen
import com.dewijones92.uniapp.ui.playlist.rememberPlaylistAdder

@Composable
fun LibraryScreen(container: AppContainer, modifier: Modifier = Modifier) {
    var showPlaylists by remember { mutableStateOf(false) }
    var openPlaylist by remember { mutableStateOf<PlaylistId?>(null) }
    val playlist = openPlaylist

    when {
        playlist != null ->
            LocalPlaylistDetailScreen(container, playlist, onBack = { openPlaylist = null }, modifier = modifier)
        showPlaylists ->
            LocalPlaylistsScreen(
                container,
                onBack = { showPlaylists = false },
                onOpen = { openPlaylist = it },
                modifier = modifier,
            )
        else -> LibraryHome(container, onOpenPlaylists = { showPlaylists = true }, modifier = modifier)
    }
}

@Composable
private fun LibraryHome(container: AppContainer, onOpenPlaylists: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
    val downloaded by viewModel.downloaded.collectAsStateWithLifecycle()
    val sort by viewModel.sortOrder.collectAsStateWithLifecycle()
    val addToPlaylist = rememberPlaylistAdder(container)

    LibraryContent(
        downloaded = downloaded,
        sort = sort,
        onOpenPlaylists = onOpenPlaylists,
        onPlay = viewModel::play,
        onDelete = viewModel::delete,
        onAddToPlaylist = { addToPlaylist(it.item) },
        onSetSort = viewModel::setSort,
        modifier = modifier,
    )
}

@Composable
internal fun LibraryContent(
    downloaded: List<DownloadedItem>,
    sort: MediaSort,
    onOpenPlaylists: () -> Unit,
    onPlay: (DownloadedItem) -> Unit,
    onDelete: (DownloadedItem) -> Unit,
    onAddToPlaylist: (DownloadedItem) -> Unit,
    onSetSort: (MediaSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item { PlaylistsEntry(onOpenPlaylists) }
            if (downloaded.isEmpty()) {
                item { DownloadsEmpty() }
            } else {
                item {
                    SectionHeaderWithSort(
                        title = stringResource(R.string.library_downloads),
                        sort = sort,
                        onSetSort = onSetSort,
                    )
                }
                items(downloaded, key = { it.item.id.value }) { entry ->
                    MediaItemRow(
                        item = entry.item,
                        subtitle = mediaItemSubtitle(entry.item),
                        downloadState = DownloadState.Downloaded(entry.localPath),
                        onPlay = { onPlay(entry) },
                        onDownload = { },
                        onDeleteDownload = { onDelete(entry) },
                        onAddToPlaylist = { onAddToPlaylist(entry) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        BuildInfoFooter()
    }
}

@Composable
private fun PlaylistsEntry(onOpen: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.PlaylistPlay,
            contentDescription = null,
            modifier = Modifier.padding(end = 16.dp)
        )
        Text(
            text = stringResource(R.string.playlists_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun DownloadsEmpty() {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Outlined.CollectionsBookmark,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.library_empty_headline),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.library_empty_supporting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    UniAppTheme { LibraryScreen(FakeAppContainer()) }
}
