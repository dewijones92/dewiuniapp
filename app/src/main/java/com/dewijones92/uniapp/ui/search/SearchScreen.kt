package com.dewijones92.uniapp.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.data.search.SearchHit
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.di.fake.FakeAppContainer
import com.dewijones92.uniapp.theme.UniAppTheme
import com.dewijones92.uniapp.ui.common.EmptyState
import com.dewijones92.uniapp.ui.common.MediaThumbnail
import com.dewijones92.uniapp.ui.search.SearchViewModel.Results
import com.dewijones92.uniapp.ui.search.SearchViewModel.UiState

@Composable
fun SearchScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SearchContent(
        state = state,
        onSearch = viewModel::search,
        onQueryChange = viewModel::onQueryChange,
        onSubscribe = viewModel::subscribe,
        onPlayVideo = viewModel::playVideo,
        onRemoveHistory = viewModel::removeHistory,
        onClearHistory = viewModel::clearHistory,
        modifier = modifier,
    )
}

@Composable
internal fun SearchContent(
    state: UiState,
    onSearch: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onSubscribe: (SearchHit.Podcast) -> Unit,
    onPlayVideo: (SearchHit.Video) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onQueryChange(it)
            },
            label = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
            trailingIcon = {
                IconButton(onClick = { onSearch(query) }) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_action))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        val runSearch: (String) -> Unit = { submitted ->
            query = submitted
            onSearch(submitted)
        }
        when (val results = state.results) {
            Results.Idle -> SearchIdle(state.history, runSearch, onRemoveHistory, onClearHistory)
            Results.Searching -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            is Results.Loaded -> ResultsList(results, state, onSubscribe, onPlayVideo)
        }
    }
}

/** Idle state: recent searches if any, otherwise the empty-state prompt. */
@Composable
private fun SearchIdle(
    history: List<String>,
    onSearch: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (history.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Search,
            headline = stringResource(R.string.search_empty_headline),
            supportingText = stringResource(R.string.search_empty_supporting),
        )
    } else {
        SearchHistory(history, onSearch, onRemove, onClear)
    }
}

/** Recent searches (idle state): tap to re-run, X to forget one, Clear all to wipe. */
@Composable
private fun SearchHistory(
    history: List<String>,
    onSearch: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.search_recent),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) { Text(stringResource(R.string.search_clear_all)) }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(history.size) { index ->
                val recent = history[index]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSearch(recent) }
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = recent,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemove(recent) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_remove_recent))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsList(
    results: Results.Loaded,
    state: UiState,
    onSubscribe: (SearchHit.Podcast) -> Unit,
    onPlayVideo: (SearchHit.Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (results.podcasts.isNotEmpty() || results.podcastsFailed) {
            item { SectionHeader(stringResource(R.string.destination_podcasts)) }
        }
        if (results.podcastsFailed) {
            item { SectionError() }
        }
        items(results.podcasts.size) { index ->
            val hit = results.podcasts[index]
            PodcastHitRow(
                hit = hit,
                subscribed = hit.feedUrl.value in state.subscribedFeeds,
                onSubscribe = { onSubscribe(hit) },
            )
        }

        if (results.videos.isNotEmpty() || results.videosFailed) {
            item { SectionHeader(stringResource(R.string.destination_videos)) }
        }
        if (results.videosFailed) {
            item { SectionError() }
        }
        items(results.videos.size) { index ->
            val hit = results.videos[index]
            VideoHitRow(
                hit = hit,
                resolving = state.resolving == hit.watchUrl.value,
                onPlay = { onPlayVideo(hit) },
            )
        }

        if (state.resolveFailed) {
            item {
                Text(
                    text = stringResource(R.string.error_extraction),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SectionError(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.search_section_failed),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun PodcastHitRow(
    hit: SearchHit.Podcast,
    subscribed: Boolean,
    onSubscribe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        MediaThumbnail(
            url = hit.artworkUrl,
            contentDescription = hit.title,
            modifier = Modifier.size(HIT_THUMBNAIL_SIZE),
        )
        Spacer(Modifier.width(12.dp))
        HitTitles(hit.title, hit.subtitle, Modifier.weight(1f))
        TextButton(onClick = onSubscribe, enabled = !subscribed) {
            Text(stringResource(if (subscribed) R.string.subscribed else R.string.subscribe))
        }
    }
}

@Composable
private fun VideoHitRow(
    hit: SearchHit.Video,
    resolving: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !resolving, onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        MediaThumbnail(
            url = hit.artworkUrl,
            contentDescription = hit.title,
            modifier = Modifier.size(width = VIDEO_HIT_THUMBNAIL_WIDTH, height = VIDEO_HIT_THUMBNAIL_HEIGHT),
        )
        Spacer(Modifier.width(12.dp))
        val subtitle = listOfNotNull(
            hit.subtitle,
            hit.durationSeconds?.let { stringResource(R.string.duration_minutes, it / SECONDS_PER_MINUTE) },
        ).joinToString(" · ").ifBlank { null }
        HitTitles(hit.title, subtitle, Modifier.weight(1f))
        if (resolving) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun HitTitles(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val SECONDS_PER_MINUTE = 60L

// Search hits carry their source's natural artwork shape: podcast art is
// square, a video still is 16:9.
private val HIT_THUMBNAIL_SIZE = 56.dp
private val VIDEO_HIT_THUMBNAIL_WIDTH = 96.dp
private val VIDEO_HIT_THUMBNAIL_HEIGHT = 54.dp

@Preview(showBackground = true)
@Composable
private fun SearchScreenPreview() {
    UniAppTheme { SearchScreen(FakeAppContainer()) }
}
