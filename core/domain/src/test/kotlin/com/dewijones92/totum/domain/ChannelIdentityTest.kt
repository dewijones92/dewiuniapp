package com.dewijones92.totum.domain

import com.dewijones92.totum.common.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelIdentityTest {

    private val canonical = channel("https://www.youtube.com/channel/UCabc123")
    private val byHandle = channel("https://www.youtube.com/@novaramedia")

    @Test
    fun `a canonical url yields the UC id`() {
        assertEquals("UCabc123", canonical.youTubeChannelId)
    }

    @Test
    fun `a handle url has no UC id`() {
        assertNull(byHandle.youTubeChannelId)
    }

    /** The bug: the same channel by two different URLs must compare equal. */
    @Test
    fun `the same UC id matches whatever the url path looks like`() {
        val withSuffix = channel("https://www.youtube.com/channel/UCabc123/videos")
        assertTrue(canonical.isSameChannelAs(withSuffix))
    }

    @Test
    fun `different UC ids never match`() {
        assertFalse(canonical.isSameChannelAs(channel("https://www.youtube.com/channel/UCzzz999")))
    }

    /**
     * With no id on either side the URL is all the identity there is, so it is used — but a
     * handle and a canonical URL cannot be compared this way, which is exactly why the view
     * model has to resolve the id rather than rely on this.
     */
    @Test
    fun `without ids it falls back to url equality`() {
        assertTrue(byHandle.isSameChannelAs(channel("https://www.youtube.com/@novaramedia")))
        assertFalse(byHandle.isSameChannelAs(canonical))
    }

    /**
     * The gap this closes: a channel opened by handle has no id in its URL, so it could never be
     * matched against subscriptions keyed by canonical URLs. Loading the channel resolves the real
     * id, and that is what the comparison must use.
     */
    @Test
    fun `a handle-only channel matches once its id has been resolved`() {
        val subscriptions = listOf(canonical)

        assertFalse("no id yet — this is the bug", subscriptions.containsChannel(byHandle))
        assertTrue("resolved", subscriptions.containsChannel(byHandle, resolvedId = "UCabc123"))
    }

    @Test
    fun `a resolved id that is not subscribed still reports false`() {
        assertFalse(listOf(canonical).containsChannel(byHandle, resolvedId = "UCsomethingelse"))
    }

    @Test
    fun `an empty subscription list contains nothing`() {
        assertFalse(emptyList<MediaSource.VideoChannel>().containsChannel(canonical, "UCabc123"))
    }

    /** A canonical URL needs no resolution — the id is already in it. */
    @Test
    fun `a canonical channel matches with no resolved id`() {
        assertTrue(listOf(canonical).containsChannel(channel("https://www.youtube.com/channel/UCabc123/videos")))
    }

    private fun channel(url: String) = MediaSource.VideoChannel(
        id = SourceId(url),
        title = "a channel",
        channelUrl = HttpUrl.of(url),
    )
}
