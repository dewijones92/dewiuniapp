package com.dewijones92.uniapp.data.channel.fake

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.channel.ChannelRepository
import com.dewijones92.uniapp.data.channel.SubscribeChannelResult
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

/** In-memory [ChannelRepository] for tests and previews. */
public class FakeChannelRepository : ChannelRepository {

    private val subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    private val videos = MutableStateFlow<List<MediaItem>>(emptyList())

    override fun observeSubscriptions(): Flow<List<Subscription>> = subscriptions

    override fun observeVideos(): Flow<List<MediaItem>> = videos

    override suspend fun subscribe(channelUrl: HttpUrl): SubscribeChannelResult {
        val id = SourceId(channelUrl.value)
        if (subscriptions.value.any { it.source.id == id }) return SubscribeChannelResult.AlreadySubscribed(id)
        val source = MediaSource.VideoChannel(id = id, title = channelUrl.value, channelUrl = channelUrl)
        subscriptions.update { it + Subscription(source, Instant.EPOCH) }
        videos.update {
            it + MediaItem(
                id = MediaItemId("${id.value}/vid"),
                sourceId = id,
                title = "Sample video",
                publishedAt = null,
                duration = null,
                author = channelUrl.value,
                mediaUrl = channelUrl,
            )
        }
        return SubscribeChannelResult.Subscribed(source)
    }

    override suspend fun unsubscribe(id: SourceId) {
        subscriptions.update { list -> list.filterNot { it.source.id == id } }
        videos.update { list -> list.filterNot { it.sourceId == id } }
    }
}
