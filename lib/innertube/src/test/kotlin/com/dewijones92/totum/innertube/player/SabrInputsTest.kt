package com.dewijones92.totum.innertube.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The four things a SABR request needs, read out of a player response.
 *
 * Each was found the hard way. The config is the one field the server refuses to work without;
 * `lastModified` and `xtags` together identify a format, because a real response carried 22
 * entries for a single audio itag, one per dubbed language.
 */
class SabrInputsTest {

    private val body = """
        {"playabilityStatus":{"status":"OK"},
         "playerConfig":{"mediaCommonConfig":{"mediaUstreamerRequestConfig":{
            "videoPlaybackUstreamerConfig":"CrlKCq8GCAAQgAU"}}},
         "streamingData":{
           "serverAbrStreamingUrl":"https://rr2.example.com/videoplayback?x=1",
           "adaptiveFormats":[
             {"itag":251,"mimeType":"audio/webm; codecs=\"opus\"","url":"https://r1.example.com/a",
              "lastModified":"1785351922567103","xtags":"ChEKBWFjb250"},
             {"itag":137,"mimeType":"video/mp4; codecs=\"avc1\"","height":1080,
              "lastModified":"1785388029379218"}]}}
    """.trimIndent()

    private val streaming get() = (PlayerResponseParser.parse(body) as PlayerResult.Success).streaming

    @Test
    fun `the ustreamer config is decoded from unpadded base64url`() {
        // "CrlKCq8GCAAQgAU" is 15 chars — not a multiple of 4, which the strict decoder rejects.
        val config = streaming.ustreamerConfig

        assertNotNull("an undecodable config is the same as sending none", config)
        assertEquals(11, config!!.size)
    }

    @Test
    fun `the SABR endpoint is read`() {
        assertEquals("https://rr2.example.com/videoplayback?x=1", streaming.serverAbrStreamingUrl?.value)
    }

    @Test
    fun `each format carries what identifies it to SABR`() {
        val audio = streaming.formats.single { it.itag == 251 }
        val video = streaming.formats.single { it.itag == 137 }

        assertEquals(1_785_351_922_567_103L, audio.lastModified)
        assertEquals("ChEKBWFjb250", audio.xtags)
        assertEquals(1_785_388_029_379_218L, video.lastModified)
        assertNull("video formats carry no xtags, and must not invent one", video.xtags)
    }

    @Test
    fun `a response with no SABR config yields null rather than empty bytes`() {
        val without = """
            {"playabilityStatus":{"status":"OK"},
             "streamingData":{"formats":[{"itag":18,"mimeType":"video/mp4","url":"https://x.test/a"}]}}
        """.trimIndent()

        val parsed = (PlayerResponseParser.parse(without) as PlayerResult.Success).streaming

        assertNull(parsed.ustreamerConfig)
        assertNull(parsed.serverAbrStreamingUrl)
    }
}
