package com.dewijones92.uniapp.innertube.playlists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistsResponseParserTest {

    private val sample = """
        {"contents":[
          {"tileRenderer":{
            "contentType":"TILE_CONTENT_TYPE_PLAYLIST",
            "onSelectCommand":{"browseEndpoint":{"browseId":"VLWL"}},
            "metadata":{"tileMetadataRenderer":{"title":{"simpleText":"Watch later"}}},
            "header":{"tileHeaderRenderer":{
              "thumbnail":{"thumbnails":[{"url":"https://t/1.jpg"},{"url":"https://t/2.jpg"}]},
              "thumbnailOverlays":[{"thumbnailOverlayTimeStatusRenderer":{
                "text":{"runs":[{"text":"184"},{"text":" videos"}]},"style":"DEFAULT"}}]
            }}}},
          {"tileRenderer":{
            "contentType":"TILE_CONTENT_TYPE_VIDEO",
            "onSelectCommand":{"watchEndpoint":{"videoId":"vid1"}},
            "metadata":{"tileMetadataRenderer":{"title":{"simpleText":"a video"}}}}}
        ]}
    """.trimIndent()

    private fun parsed() = (PlaylistsResponseParser.parse(sample) as PlaylistsResult.Success).playlists

    @Test
    fun `collects playlist tiles and ignores video tiles`() {
        assertEquals(listOf("VLWL"), parsed().map { it.browseId })
    }

    @Test
    fun `maps title, video count and thumbnail`() {
        val playlist = parsed().single()
        assertEquals("Watch later", playlist.title)
        assertEquals("184 videos", playlist.videoCountText)
        assertEquals("https://t/2.jpg", playlist.thumbnailUrl?.value)
    }

    @Test
    fun `unparseable json is a failure`() {
        assertTrue(PlaylistsResponseParser.parse("not json") is PlaylistsResult.Failure)
    }
}
