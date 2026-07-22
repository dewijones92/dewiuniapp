package com.dewijones92.uniapp.data.importexport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionImportParserTest {

    private val parser = SubscriptionImportParser()

    private fun parse(content: String): List<ImportedSource> =
        (parser.parse(content) as ImportParseResult.Success).sources

    @Test
    fun `OPML podcast outlines become podcast sources`() {
        val opml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <body>
                <outline type="rss" text="Show One" xmlUrl="https://feeds.example.com/one.xml" htmlUrl="https://one.example.com"/>
                <outline type="rss" title="Show Two" xmlUrl="http://feeds.example.com/two.xml"/>
              </body>
            </opml>
        """.trimIndent()

        val sources = parse(opml)

        assertEquals(2, sources.size)
        val first = sources[0] as ImportedSource.Podcast
        assertEquals("Show One", first.title)
        assertEquals("https://feeds.example.com/one.xml", first.feedUrl.value)
        assertEquals("Show Two", (sources[1] as ImportedSource.Podcast).title)
    }

    @Test
    fun `nested OPML outlines are found at any depth`() {
        val opml = """
            <opml version="2.0"><body>
              <outline text="Folder">
                <outline type="rss" text="Nested" xmlUrl="https://feeds.example.com/nested.xml"/>
              </outline>
            </body></opml>
        """.trimIndent()

        val sources = parse(opml)

        assertEquals(1, sources.size)
        assertEquals("Nested", sources[0].title)
    }

    @Test
    fun `a YouTube feed inside OPML is a channel, not a podcast`() {
        val opml = """
            <opml version="2.0"><body>
              <outline type="rss" text="A Channel"
                xmlUrl="https://www.youtube.com/feeds/videos.xml?channel_id=UCaaaaaaaaaaaaaaaaaaaaaa"/>
            </body></opml>
        """.trimIndent()

        val channel = parse(opml).single() as ImportedSource.YouTubeChannel

        assertEquals("A Channel", channel.title)
        assertEquals("https://www.youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaaaa", channel.channelUrl.value)
    }

    @Test
    fun `NewPipe JSON becomes YouTube channels, skipping non-YouTube services`() {
        val json = """
            {"app_version":"0.0","subscriptions":[
              {"service_id":0,"url":"https://www.youtube.com/channel/UCbbbbbbbbbbbbbbbbbbbbbb","name":"Chan B"},
              {"service_id":1,"url":"https://soundcloud.com/someone","name":"Not YouTube"}
            ]}
        """.trimIndent()

        val sources = parse(json)

        assertEquals(1, sources.size)
        val channel = sources[0] as ImportedSource.YouTubeChannel
        assertEquals("Chan B", channel.title)
        assertEquals("https://www.youtube.com/channel/UCbbbbbbbbbbbbbbbbbbbbbb", channel.channelUrl.value)
    }

    @Test
    fun `Takeout CSV drops the header row and reads channels`() {
        val csv = """
            Channel Id,Channel URL,Channel Title
            UCcccccccccccccccccccccc,http://www.youtube.com/channel/UCcccccccccccccccccccccc,"Title, with comma"
        """.trimIndent()

        val channel = parse(csv).single() as ImportedSource.YouTubeChannel

        assertEquals("Title, with comma", channel.title)
        assertEquals("https://www.youtube.com/channel/UCcccccccccccccccccccccc", channel.channelUrl.value)
    }

    @Test
    fun `Takeout CSV honours doubled-quote escapes inside a quoted title`() {
        val csv = """
            Channel Id,Channel URL,Channel Title
            UCcccccccccccccccccccccc,http://www.youtube.com/channel/UCcccccccccccccccccccccc,"Say ""hi"" now"
        """.trimIndent()

        val channel = parse(csv).single() as ImportedSource.YouTubeChannel

        assertEquals("Say \"hi\" now", channel.title)
    }

    @Test
    fun `a byte-order mark before the OPML does not defeat format sniffing`() {
        val opml = "\uFEFF<opml version=\"2.0\"><body>" +
            "<outline type=\"rss\" text=\"Show\" xmlUrl=\"https://feeds.example.com/a.xml\"/></body></opml>"

        val sources = parse(opml)

        assertEquals(1, sources.size)
        assertEquals("Show", sources[0].title)
    }

    @Test
    fun `empty content is a failure, not an empty success`() {
        assertTrue(parser.parse("   ") is ImportParseResult.Failure)
    }

    @Test
    fun `unparseable XML is a failure`() {
        assertTrue(parser.parse("<opml><body><outline") is ImportParseResult.Failure)
    }

    @Test
    fun `an OPML with no feed URLs is a failure`() {
        val opml = "<opml version=\"2.0\"><body><outline text=\"Folder only\"/></body></opml>"
        assertTrue(parser.parse(opml) is ImportParseResult.Failure)
    }
}
