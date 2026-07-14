package com.dewijones92.uniapp.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.HorizontalDivider
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
import com.dewijones92.uniapp.ui.common.BuildInfoFooter
import com.dewijones92.uniapp.ui.common.EmptyState
import com.dewijones92.uniapp.ui.common.MediaItemRow
import com.dewijones92.uniapp.ui.common.MediaSort
import com.dewijones92.uniapp.ui.common.SectionHeaderWithSort
import com.dewijones92.uniapp.ui.common.mediaItemSubtitle
import com.dewijones92.uniapp.ui.library.LibraryViewModel.DownloadedItem

@Composable
fun LibraryScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
    val downloaded by viewModel.downloaded.collectAsStateWithLifecycle()
    val sort by viewModel.sortOrder.collectAsStateWithLifecycle()

    LibraryContent(
        downloaded = downloaded,
        sort = sort,
        onPlay = viewModel::play,
        onDelete = viewModel::delete,
        onSetSort = viewModel::setSort,
        modifier = modifier,
    )
}

@Composable
internal fun LibraryContent(
    downloaded: List<DownloadedItem>,
    sort: MediaSort,
    onPlay: (DownloadedItem) -> Unit,
    onDelete: (DownloadedItem) -> Unit,
    onSetSort: (MediaSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (downloaded.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.CollectionsBookmark,
                headline = stringResource(R.string.library_empty_headline),
                supportingText = stringResource(R.string.library_empty_supporting),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
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
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        BuildInfoFooter()
    }
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    UniAppTheme { LibraryScreen(FakeAppContainer()) }
}
