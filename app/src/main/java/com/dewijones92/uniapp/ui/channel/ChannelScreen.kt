package com.dewijones92.uniapp.ui.channel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.dewijones92.uniapp.ui.common.MediaItemRow
import com.dewijones92.uniapp.ui.common.mediaItemSubtitle

/**
 * A channel's page: its recent uploads plus a subscribe/unsubscribe toggle.
 * Shown as a full-screen layer over the Videos tab; the rows are the same
 * shared [MediaItemRow] used everywhere, so playing and downloading behave
 * identically to the feed.
 */
@Composable
fun ChannelScreen(
    container: AppContainer,
    source: MediaSource.VideoChannel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ChannelViewModel =
        viewModel(key = source.id.value, factory = ChannelViewModel.factory(container, source))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ChannelContent(
        state = state,
        onBack = onBack,
        onToggleSubscribed = viewModel::toggleSubscribed,
        onPlay = viewModel::play,
        onDownload = viewModel::download,
        onDeleteDownload = viewModel::deleteDownload,
        modifier = modifier,
    )
}

@Composable
internal fun ChannelContent(
    state: ChannelViewModel.UiState,
    onBack: () -> Unit,
    onToggleSubscribed: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onDownload: (MediaItem) -> Unit,
    onDeleteDownload: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                ChannelHeader(
                    title = state.title,
                    subscribed = state.subscribed,
                    onBack = onBack,
                    onToggleSubscribed = onToggleSubscribed,
                )
            }
            when {
                state.loading -> item { CenteredProgress() }
                state.error -> item { Message(stringResource(R.string.feed_error)) }
                state.videos.isEmpty() -> item { Message(stringResource(R.string.feed_empty)) }
                else -> items(state.videos, key = { it.id.value }) { video ->
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
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
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
