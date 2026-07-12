package com.dewijones92.uniapp.ui.podcasts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.podcast.PodcastRepository
import com.dewijones92.uniapp.data.podcast.fake.FakePodcastRepository
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import com.dewijones92.uniapp.theme.UniAppTheme
import com.dewijones92.uniapp.ui.common.EmptyState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun PodcastsScreen(
    repository: PodcastRepository,
    onPlayEpisode: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PodcastsViewModel = viewModel(factory = PodcastsViewModel.factory(repository))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PodcastsContent(
        state = state,
        onSubscribe = viewModel::subscribe,
        onDialogClosed = viewModel::resetSubscribing,
        onPlayEpisode = onPlayEpisode,
        modifier = modifier,
    )
}

@Composable
internal fun PodcastsContent(
    state: PodcastsViewModel.UiState,
    onSubscribe: (String) -> Unit,
    onDialogClosed: () -> Unit,
    onPlayEpisode: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        if (state.subscriptions.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Podcasts,
                headline = stringResource(R.string.podcasts_empty_headline),
                supportingText = stringResource(R.string.podcasts_empty_supporting),
            )
        } else {
            SubscriptionsAndEpisodes(state.subscriptions, state.episodes, onPlayEpisode)
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_podcast))
        }

        if (showAddDialog) {
            AddPodcastDialog(
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
private fun SubscriptionsAndEpisodes(
    subscriptions: List<Subscription>,
    episodes: List<MediaItem>,
    onPlayEpisode: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                items(subscriptions) { subscription ->
                    AssistChip(
                        onClick = {},
                        label = { Text(subscription.source.title) },
                    )
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.latest_episodes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        items(episodes, key = { it.id.value }) { episode ->
            EpisodeRow(episode = episode, onClick = { onPlayEpisode(episode) })
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Playable only when the feed provided an enclosure.
            .clickable(enabled = episode.mediaUrl != null, onClick = onClick)
            .padding(16.dp),
    ) {
        Text(
            text = episode.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val details = listOfNotNull(
                episode.author,
                episode.publishedAt?.let(::formatDate),
                episode.duration?.let { stringResource(R.string.duration_minutes, it.inWholeMinutes) },
            )
            Text(
                text = details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDate(instant: Instant): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withZone(ZoneId.systemDefault())
        .format(instant)

@Preview(showBackground = true)
@Composable
private fun PodcastsContentPreview() {
    val sourceId = SourceId("preview-feed")
    val state = PodcastsViewModel.UiState(
        subscriptions = listOf(
            Subscription(
                source = MediaSource.PodcastFeed(
                    id = sourceId,
                    title = "Preview podcast",
                    feedUrl = HttpUrl.of("https://example.com/feed.xml"),
                ),
                subscribedAt = Instant.EPOCH,
            ),
        ),
        episodes = listOf(FakePodcastRepository.sampleEpisode(sourceId)),
    )
    UniAppTheme {
        PodcastsContent(state = state, onSubscribe = {}, onDialogClosed = {}, onPlayEpisode = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun PodcastsEmptyPreview() {
    UniAppTheme {
        PodcastsContent(
            state = PodcastsViewModel.UiState(),
            onSubscribe = {},
            onDialogClosed = {},
            onPlayEpisode = {},
        )
    }
}
