package com.dewijones92.uniapp.innertube.feeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTileParserTest {

    private fun fixture(): String =
        checkNotNull(javaClass.getResourceAsStream("/feed_tv_sample.json")) { "fixture missing" }
            .bufferedReader().readText()

    private fun parsed(): List<FeedVideo> =
        (VideoTileParser.parse(fixture()) as FeedResult.Success).videos

    @Test
    fun `collects videos in order, deduped, ignoring channel tiles`() {
        assertEquals(
            listOf("vid00000001", "vid00000002", "vid00000003", "vid00000004"),
            parsed().map { it.videoId },
        )
    }

    @Test
    fun `maps title, author, thumbnail and watch url`() {
        val first = parsed().first()
        assertEquals("First Video", first.title)
        assertEquals("Alpha Channel", first.author)
        assertEquals("https://www.youtube.com/watch?v=vid00000001", first.watchUrl.value)
        assertEquals("https://i.ytimg.com/vid00000001/hq.jpg", first.thumbnailUrl?.value)
    }

    @Test
    fun `parses m ss and h mm ss durations`() {
        val byId = parsed().associateBy { it.videoId }
        assertEquals(12L * 60 + 34, byId.getValue("vid00000001").durationSeconds)
        assertEquals(1L * 3600 + 2 * 60 + 3, byId.getValue("vid00000002").durationSeconds)
        assertEquals(45L, byId.getValue("vid00000004").durationSeconds)
    }

    @Test
    fun `a live item with no duration overlay yields a null duration`() {
        assertNull(parsed().first { it.videoId == "vid00000003" }.durationSeconds)
    }

    @Test
    fun `author can come from runs as well as simpleText`() {
        assertEquals("Delta Channel", parsed().first { it.videoId == "vid00000004" }.author)
    }

    @Test
    fun `unparseable json is a failure value`() {
        assertTrue(VideoTileParser.parse("not json") is FeedResult.Failure)
    }

    @Test
    fun `normal feed tiles are tagged VIDEO`() {
        assertTrue(parsed().all { it.kind == FeedVideo.Kind.VIDEO })
    }

    @Test
    fun `extracts the published relative time from tile metadata`() {
        val body = """
            {"contents":[{"tileRenderer":{
              "contentType":"TILE_CONTENT_TYPE_VIDEO",
              "onSelectCommand":{"watchEndpoint":{"videoId":"dated000001"}},
              "metadata":{"tileMetadataRenderer":{
                "title":{"simpleText":"Dated video"},
                "lines":[
                  {"lineRenderer":{"items":[{"lineItemRenderer":{"text":{"simpleText":"Some Channel"}}}]}},
                  {"lineRenderer":{"items":[
                    {"lineItemRenderer":{"text":{"simpleText":"12K views"}}},
                    {"lineItemRenderer":{"text":{"simpleText":"2 days ago"}}}
                  ]}}
                ]}}}}]}
        """.trimIndent()
        val video = (VideoTileParser.parse(body) as FeedResult.Success).videos.single()
        assertEquals("2 days ago", video.publishedText)
        assertEquals("Some Channel", video.author)
    }

    @Test
    fun `a live tile is tagged LIVE`() {
        val body = """
            {"contents":[{"tileRenderer":{
              "contentType":"TILE_CONTENT_TYPE_VIDEO",
              "onSelectCommand":{"watchEndpoint":{"videoId":"live0000001"}},
              "metadata":{"tileMetadataRenderer":{"title":{"simpleText":"Live now"}}},
              "header":{"tileHeaderRenderer":{"thumbnailOverlays":[
                {"thumbnailOverlayTimeStatusRenderer":{"style":"LIVE","text":{"runs":[{"text":"LIVE"}]}}}
              ]}}}}]}
        """.trimIndent()
        val video = (VideoTileParser.parse(body) as FeedResult.Success).videos.single()
        assertEquals(FeedVideo.Kind.LIVE, video.kind)
    }
}
