package com.dewijones92.totum.data.group

import com.dewijones92.totum.common.HttpUrl
import com.dewijones92.totum.domain.MediaItem
import com.dewijones92.totum.domain.MediaItemId
import com.dewijones92.totum.domain.MediaSource
import com.dewijones92.totum.domain.SourceGroup
import com.dewijones92.totum.domain.SourceGroupId
import com.dewijones92.totum.domain.SourceId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class GroupFeedTest {

    private val channel = MediaSource.VideoChannel(
        SourceId("chan"),
        "A channel",
        HttpUrl.of("https://www.youtube.com/channel/UCaaa"),
    )
    private val feed = MediaSource.PodcastFeed(
        SourceId("pod"),
        "A podcast",
        HttpUrl.of("https://example.com/feed.xml"),
    )
    private val known = listOf(channel, feed).associateBy { it.id }

    private val group = SourceGroup(SourceGroupId("g"), "Politics", listOf(channel.id, feed.id))

    private fun groupFeed(itemsBySource: Map<SourceId, () -> List<MediaItem>>) = GroupFeed(
        items = { source -> itemsBySource[source.id]?.invoke() ?: emptyList() },
        locate = { id -> known[id] },
    )

    @Test
    fun `both pillars merge into one newest-first list`() = runTest {
        val result = groupFeed(
            mapOf(
                channel.id to { listOf(item("old video", channel.id, "2026-07-01T00:00:00Z")) },
                feed.id to { listOf(item("new episode", feed.id, "2026-07-29T00:00:00Z")) },
            ),
        ).itemsFor(group)

        assertEquals(listOf("new episode", "old video"), result.map { it.title })
    }

    @Test
    fun `a member that cannot be read does not take the group down`() = runTest {
        val result = groupFeed(
            mapOf(
                channel.id to { throw GroupMemberUnreadable("network") },
                feed.id to { listOf(item("survivor", feed.id, "2026-07-29T00:00:00Z")) },
            ),
        ).itemsFor(group)

        assertEquals(listOf("survivor"), result.map { it.title })
    }

    @Test
    fun `a membership whose source is gone is skipped`() = runTest {
        val orphaned = group.with(SourceId("unsubscribed-since"))

        val result = groupFeed(
            mapOf(channel.id to { listOf(item("still here", channel.id, "2026-07-29T00:00:00Z")) }),
        ).itemsFor(orphaned)

        assertEquals(listOf("still here"), result.map { it.title })
    }

    @Test
    fun `the same item from two members appears once`() = runTest {
        val shared = item("cross-posted", channel.id, "2026-07-29T00:00:00Z")

        val result = groupFeed(
            mapOf(channel.id to { listOf(shared) }, feed.id to { listOf(shared) }),
        ).itemsFor(group)

        assertEquals(1, result.size)
    }

    @Test
    fun `an undated item sorts last rather than being dropped`() = runTest {
        val result = groupFeed(
            mapOf(
                channel.id to { listOf(item("no date", channel.id, publishedAt = null)) },
                feed.id to { listOf(item("dated", feed.id, "2026-07-01T00:00:00Z")) },
            ),
        ).itemsFor(group)

        assertEquals(listOf("dated", "no date"), result.map { it.title })
    }

    @Test
    fun `an empty group is an empty feed and asks nothing`() = runTest {
        var asked = 0
        val result = GroupFeed(
            items = { _ ->
                asked++
                emptyList()
            },
            locate = { known[it] },
        ).itemsFor(SourceGroup(SourceGroupId("g"), "Empty"))

        assertEquals(emptyList<MediaItem>(), result)
        assertEquals(0, asked)
    }

    private fun item(title: String, sourceId: SourceId, publishedAt: String?) = MediaItem(
        id = MediaItemId(title),
        sourceId = sourceId,
        title = title,
        publishedAt = publishedAt?.let(Instant::parse),
        duration = null,
    )
}
