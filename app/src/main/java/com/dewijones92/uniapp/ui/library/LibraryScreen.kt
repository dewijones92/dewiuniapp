package com.dewijones92.uniapp.ui.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.dewijones92.uniapp.theme.UniAppTheme
import com.dewijones92.uniapp.ui.common.EmptyState
import com.dewijones92.uniapp.ui.common.MediaItemRow
import com.dewijones92.uniapp.ui.common.mediaItemSubtitle
import com.dewijones92.uniapp.ui.library.LibraryViewModel.DownloadedItem

@Composable
fun LibraryScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
    val downloaded by viewModel.downloaded.collectAsStateWithLifecycle()

    LibraryContent(
        downloaded = downloaded,
        onPlay = viewModel::play,
        onDelete = viewModel::delete,
        modifier = modifier,
    )
}

@Composable
internal fun LibraryContent(
    downloaded: List<DownloadedItem>,
    onPlay: (DownloadedItem) -> Unit,
    onDelete: (DownloadedItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (downloaded.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.CollectionsBookmark,
            headline = stringResource(R.string.library_empty_headline),
            supportingText = stringResource(R.string.library_empty_supporting),
            modifier = modifier,
        )
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Text(
                text = stringResource(R.string.library_downloads),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    UniAppTheme { LibraryScreen(FakeAppContainer()) }
}
