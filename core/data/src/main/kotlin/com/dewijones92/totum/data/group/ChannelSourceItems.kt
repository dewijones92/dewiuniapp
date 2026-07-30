package com.dewijones92.totum.data.group

import com.dewijones92.totum.data.channel.ChannelRepository
import com.dewijones92.totum.data.channel.ChannelVideosResult
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaSource

/**
 * The video pillar's members, through the same [ChannelRepository] a channel page uses —
 * so a group shows exactly what opening each channel would show, including shorts and
 * live, and there is one fetch path to keep working rather than two.
 */
public class ChannelSourceItems(private val channels: ChannelRepository) : SourceItems {

    override suspend fun itemsFor(source: MediaSource): List<MediaItem> {
        val channel = source as? MediaSource.VideoChannel ?: return emptyList()
        return when (val result = channels.fetchChannelVideos(channel.channelUrl)) {
            is ChannelVideosResult.Success -> result.videos
            // Reported by GroupFeed, which knows whose failure it is; a failure value here
            // is an empty contribution, never an exception that takes the group down.
            is ChannelVideosResult.Failure -> throw GroupMemberUnreadable(result.toString())
        }
    }
}

/** A member that could not be read. Carries the reason so the group can log whose it was. */
public class GroupMemberUnreadable(detail: String) : Exception(detail)
