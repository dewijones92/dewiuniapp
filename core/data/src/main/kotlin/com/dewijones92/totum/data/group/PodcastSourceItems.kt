package com.dewijones92.totum.data.group

import com.dewijones92.totum.data.podcast.PodcastRepository
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaSource
import kotlinx.coroutines.flow.first

/**
 * The podcast pillar's members, read from what is already stored rather than re-fetched.
 *
 * A podcast feed's episodes are local — that is the difference between the pillars, and
 * the reason this is not symmetrical with the channel adapter. Refreshing every feed in a
 * group on entry would re-download RSS the app already has; the existing refresh path
 * (pull-to-refresh, the background refresher) keeps it current.
 */
public class PodcastSourceItems(private val podcasts: PodcastRepository) : SourceItems {

    override suspend fun itemsFor(source: MediaSource): List<MediaItem> {
        val feed = source as? MediaSource.PodcastFeed ?: return emptyList()
        return podcasts.observeEpisodes().first().filter { it.sourceId == feed.id }
    }
}
