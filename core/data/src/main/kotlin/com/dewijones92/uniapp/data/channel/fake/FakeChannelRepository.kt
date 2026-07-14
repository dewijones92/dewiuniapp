package com.dewijones92.uniapp.data.channel.fake

import com.dewijones92.uniapp.common.HttpUrl
import com.dewijones92.uniapp.data.channel.ChannelRepository
import com.dewijones92.uniapp.data.channel.ChannelVideosResult
import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.SourceId

/** In-memory [ChannelRepository] for tests and previews. */
public class FakeChannelRepository : ChannelRepository {

    override suspend fun fetchChannelVideos(channelUrl: HttpUrl): ChannelVideosResult =
        ChannelVideosResult.Success(
            channelId = "UC${channelUrl.value.hashCode()}",
            title = channelUrl.value,
            videos = listOf(
                MediaItem(
                    id = MediaItemId("${channelUrl.value}/browse"),
                    sourceId = SourceId(channelUrl.value),
                    title = "Sample channel video",
                    publishedAt = null,
                    duration = null,
                    author = channelUrl.value,
                    mediaUrl = channelUrl,
                ),
            ),
        )
}
