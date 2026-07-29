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

    private fun channel(url: String) = MediaSource.VideoChannel(
        id = SourceId(url),
        title = "a channel",
        channelUrl = HttpUrl.of(url),
    )
}
