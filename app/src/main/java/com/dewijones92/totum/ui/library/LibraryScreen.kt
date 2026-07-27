package com.dewijones92.totum.ui.library

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
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dewijones92.totum.R
import com.dewijones92.totum.di.AppContainer
import com.dewijones92.totum.di.fake.FakeAppContainer
import com.dewijones92.totum.domain.DownloadState
import com.dewijones92.totum.domain.PlaylistId
import com.dewijones92.totum.domain.StorageUsage
import com.dewijones92.totum.domain.formatBytes
import com.dewijones92.totum.theme.TotumTheme
import com.dewijones92.totum.ui.account.AccountScreen
import com.dewijones92.totum.ui.common.BuildInfoFooter
import com.dewijones92.totum.ui.common.LocalItemActions
import com.dewijones92.totum.ui.common.MediaItemRow
import com.dewijones92.totum.ui.common.MediaSort
import com.dewijones92.totum.ui.common.SectionHeaderWithSort
import com.dewijones92.totum.ui.common.mediaItemSubtitle
import com.dewijones92.totum.ui.history.PlayHistoryScreen
import com.dewijones92.totum.ui.playlist.LocalPlaylistDetailScreen
import com.dewijones92.totum.ui.playlist.LocalPlaylistsScreen
import com.dewijones92.totum.ui.playlist.rememberPlaylistAdder

@Composable
fun LibraryScreen(container: AppContainer, modifier: Modifier = Modifier) {
    var showPlaylists by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
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
        showHistory ->
            PlayHistoryScreen(container, onBack = { showHistory = false }, modifier = modifier)
        showAccount ->
            AccountScreen(container, modifier = modifier, onBack = { showAccount = false })
        else -> LibraryHome(
            container,
            onOpenPlaylists = { showPlaylists = true },
            onOpenHistory = { showHistory = true },
            onOpenAccount = { showAccount = true },
            modifier = modifier,
        )
    }
}

@Composable
private fun LibraryHome(
    container: AppContainer,
    onOpenPlaylists: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
    val downloaded by viewModel.downloaded.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val sort by viewModel.sortOrder.collectAsStateWithLifecycle()
    val addToPlaylist = rememberPlaylistAdder(container)

    LibraryContent(
        downloaded = downloaded,
        storage = storage,
        sort = sort,
        onOpenPlaylists = onOpenPlaylists,
        onOpenHistory = onOpenHistory,
        onOpenAccount = onOpenAccount,
        onPlay = viewModel::play,
        onDelete = viewModel::delete,
        onAddToPlaylist = { addToPlaylist(it.item) },
        onSetSort = viewModel::setSort,
        modifier = modifier,
    )
}

@Composable
internal fun LibraryContent(
    downloaded: List<LibraryViewModel.Entry>,
    storage: StorageUsage,
    sort: MediaSort,
    onOpenPlaylists: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAccount: () -> Unit,
    onPlay: (LibraryViewModel.Entry) -> Unit,
    onDelete: (LibraryViewModel.Entry) -> Unit,
    onAddToPlaylist: (LibraryViewModel.Entry) -> Unit,
    onSetSort: (MediaSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = LocalItemActions.current
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item { PlaylistsEntry(onOpenPlaylists) }
            item { HistoryEntry(onOpenHistory) }
            item { AccountEntry(onOpenAccount) }
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
                item { StorageSummary(storage) }
                items(downloaded, key = { it.item.id.value }) { entry ->
                    MediaItemRow(
                        item = entry.item,
                        // The size sits with the item it belongs to; a total alone cannot
                        // tell you which download is the one worth deleting.
                        subtitle = listOfNotNull(mediaItemSubtitle(entry.item), formatBytes(entry.sizeBytes))
                            .joinToString("  ·  "),
                        downloadState = DownloadState.Downloaded(entry.media.localPath, entry.media.audioOnly),
                        pillar = entry.media.pillar,
                        onPlay = { onPlay(entry) },
                        onDownload = { },
                        onDeleteDownload = { onDelete(entry) },
                        onAddToPlaylist = { onAddToPlaylist(entry) },
                        // An audio-only copy of a video is still missing the picture,
                        // and Library is exactly where you'd notice.
                        onDownloadVideo = actions?.let { { it.download(entry.item, audioOnly = false) } },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        BuildInfoFooter()
    }
}

/**
 * What the downloads are costing, under the section heading. The queue downloads
 * everything in it automatically, so the app can fill a disk without being asked —
 * a number that only shows up once the phone complains has arrived too late.
 */
@Composable
private fun StorageSummary(storage: StorageUsage) {
    val used = formatBytes(storage.usedBytes)
    val text = storage.freeBytes
        ?.let { stringResource(R.string.library_storage_with_free, used, formatBytes(it)) }
        ?: stringResource(R.string.library_storage, used)
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun PlaylistsEntry(onOpen: () -> Unit) {
    LibraryNavEntry(Icons.AutoMirrored.Filled.PlaylistPlay, R.string.playlists_title, onOpen)
}

@Composable
private fun HistoryEntry(onOpen: () -> Unit) {
    LibraryNavEntry(Icons.Outlined.History, R.string.history_title, onOpen)
}

// Account lives here rather than on the bottom bar: it's visited once to sign in,
// so it doesn't earn a permanent tab (the queue does).
@Composable
private fun AccountEntry(onOpen: () -> Unit) {
    LibraryNavEntry(Icons.Outlined.AccountCircle, R.string.destination_account, onOpen)
}

@Composable
private fun LibraryNavEntry(icon: ImageVector, titleRes: Int, onOpen: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
        Text(
            text = stringResource(titleRes),
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
    TotumTheme { LibraryScreen(FakeAppContainer()) }
}
