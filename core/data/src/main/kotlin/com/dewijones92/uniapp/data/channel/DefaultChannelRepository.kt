package com.dewijones92.uniapp.data.channel

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.subscription.SubscriptionStore
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import com.dewijones92.uniapp.ytdlp.ChannelResult
import com.dewijones92.uniapp.ytdlp.VideoSearchEntry
import com.dewijones92.uniapp.ytdlp.YtDlpEngine
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import kotlin.time.Duration.Companion.seconds

public class DefaultChannelRepository(
    private val engine: YtDlpEngine,
    private val store: SubscriptionStore,
    private val clock: Clock = Clock.systemUTC(),
    private val maxVideos: Int = DEFAULT_MAX_VIDEOS,
) : ChannelRepository {

    override fun observeSubscriptions(): Flow<List<Subscription>> = store.observeSubscriptions()

    override fun observeVideos(): Flow<List<MediaItem>> = store.observeItems()

    override suspend fun subscribe(channelUrl: HttpUrl): SubscribeChannelResult {
        val id = SourceId(channelUrl.value)
        if (store.contains(id)) return SubscribeChannelResult.AlreadySubscribed(id)

        val channel = when (val result = engine.fetchChannel(channelUrl, maxVideos)) {
            is ChannelResult.Success -> result
            is ChannelResult.Failure.Network -> return SubscribeChannelResult.Failure.Network(result.detail)
            is ChannelResult.Failure.NotAChannel ->
                return SubscribeChannelResult.Failure.NotAChannel(result.url.value)
        }

        val source = MediaSource.VideoChannel(id = id, title = channel.title, channelUrl = channelUrl)
        store.saveSource(
            subscription = Subscription(source = source, subscribedAt = clock.instant()),
            items = channel.videos.map { it.toMediaItem(id) },
        )
        return SubscribeChannelResult.Subscribed(source)
    }

    override suspend fun unsubscribe(id: SourceId) {
        store.removeSource(id)
    }

    override suspend fun importChannels(sources: List<MediaSource.VideoChannel>): Int {
        var added = 0
        for (source in sources) {
            if (store.contains(source.id)) continue
            store.saveSource(
                subscription = Subscription(source = source, subscribedAt = clock.instant()),
                items = emptyList(),
            )
            added++
        }
        return added
    }

    override suspend fun fetchChannelVideos(channelUrl: HttpUrl): ChannelVideosResult {
        val id = SourceId(channelUrl.value)
        return when (val result = engine.fetchChannel(channelUrl, maxVideos)) {
            is ChannelResult.Success ->
                ChannelVideosResult.Success(result.title, result.videos.map { it.toMediaItem(id) })
            is ChannelResult.Failure.Network -> ChannelVideosResult.Failure.Network(result.detail)
            is ChannelResult.Failure.NotAChannel -> ChannelVideosResult.Failure.NotAChannel(result.url.value)
        }
    }

    private fun VideoSearchEntry.toMediaItem(sourceId: SourceId) = MediaItem(
        id = MediaItemId(id),
        sourceId = sourceId,
        title = title,
        publishedAt = null,
        duration = durationSeconds?.seconds,
        author = uploader,
        // Resolved to a stream URL on play (video URLs expire); watchUrl is the stable handle.
        thumbnailUrl = thumbnailUrl?.let(HttpUrl::parse),
        mediaUrl = watchUrl,
    )

    private companion object {
        const val DEFAULT_MAX_VIDEOS = 20
    }
}
