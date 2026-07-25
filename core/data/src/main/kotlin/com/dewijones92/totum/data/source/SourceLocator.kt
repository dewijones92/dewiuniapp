package com.dewijones92.totum.data.source

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.data.podcast.PodcastRepository
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaSource
import com.dewijones92.totum.domain.SourceId
import com.dewijones92.totum.ytdlp.ExtractionResult
import com.dewijones92.totum.ytdlp.YtDlpEngine
import kotlinx.coroutines.flow.first

/**
 * Finds the [MediaSource] a [MediaItem] came from, so a row can navigate to its
 * source page ("go to channel" / "go to podcast"). One port for both pillars.
 *
 * Rows don't carry their source: a podcast episode's `sourceId` is its feed, but a
 * video from an account feed carries `ytfeed:<FEED>` — the feed, not the channel —
 * and `author` is only a display name.
 */
public interface SourceLocator {

    /** Null when the source can't be determined (unknown feed, unresolvable video). */
    public suspend fun locate(item: MediaItem): MediaSource?
}

/**
 * Resolves against data rather than sniffing URLs: a subscribed podcast feed is a
 * local lookup, and anything else is resolved through the engine, which reports the
 * uploader's own page. So the pillar is decided by what the item *is*, not by
 * pattern-matching its media URL in a second place.
 */
public class DefaultSourceLocator(
    private val podcasts: PodcastRepository,
    private val engine: YtDlpEngine,
) : SourceLocator {

    override suspend fun locate(item: MediaItem): MediaSource? =
        subscribedFeed(item) ?: uploaderChannel(item)

    private suspend fun subscribedFeed(item: MediaItem): MediaSource? =
        podcasts.observeSubscriptions().first()
            .map { it.source }
            .firstOrNull { it.id == item.sourceId }

    private suspend fun uploaderChannel(item: MediaItem): MediaSource? {
        val url = item.mediaUrl ?: return null
        val metadata = (engine.extract(url) as? ExtractionResult.Success)?.metadata ?: return null
        val channelUrl = metadata.uploaderUrl?.let(HttpUrl::parse) ?: return null
        return MediaSource.VideoChannel(
            // The channel page addresses itself by URL; its id only needs to be
            // stable and distinct, and the canonical URL is both.
            id = SourceId(channelUrl.value),
            title = metadata.uploader ?: item.author.orEmpty().ifBlank { channelUrl.value },
            channelUrl = channelUrl,
        )
    }
}
