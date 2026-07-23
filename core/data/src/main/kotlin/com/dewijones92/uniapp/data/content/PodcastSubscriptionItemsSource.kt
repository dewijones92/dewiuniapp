package com.dewijones92.uniapp.data.content

import com.dewijones92.uniapp.data.podcast.PodcastRepository
import kotlinx.coroutines.flow.first

/**
 * The podcast pillar's contribution to the refresh: re-fetch every subscribed
 * feed (reusing [PodcastRepository.refresh], the same pull-to-refresh path the
 * UI uses) then group the stored episodes by their feed. New-episode detection
 * is left to [ContentRefresher]; this only reports what each feed currently
 * holds.
 */
public class PodcastSubscriptionItemsSource(
    private val repository: PodcastRepository,
) : SubscriptionItemsSource {

    override suspend fun currentItems(): List<SourceUpdate> {
        repository.refresh()
        val subscriptions = repository.observeSubscriptions().first()
        val episodesBySource = repository.observeEpisodes().first().groupBy { it.sourceId }
        return subscriptions.map { subscription ->
            SourceUpdate(subscription.source, episodesBySource[subscription.source.id].orEmpty())
        }
    }
}
