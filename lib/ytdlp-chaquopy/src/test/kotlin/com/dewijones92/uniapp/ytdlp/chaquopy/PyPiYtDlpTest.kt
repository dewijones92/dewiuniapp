package com.dewijones92.uniapp.ytdlp.chaquopy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PyPiYtDlpTest {

    @Test
    fun `parses version and the py3 wheel, ignoring the sdist`() {
        val release = PyPiYtDlp.parse(
            """
            {
              "info": {"version": "2025.07.13"},
              "urls": [
                {"packagetype": "sdist", "filename": "yt_dlp-2025.07.13.tar.gz",
                 "url": "https://x/sdist.tar.gz", "digests": {"sha256": "aaa"}},
                {"packagetype": "bdist_wheel", "filename": "yt_dlp-2025.07.13-py3-none-any.whl",
                 "url": "https://x/yt_dlp.whl", "digests": {"sha256": "BBB"}}
              ]
            }
            """.trimIndent(),
        )
        assertEquals(YtDlpRelease("2025.07.13", "https://x/yt_dlp.whl", "BBB"), release)
    }

    @Test
    fun `returns null when there is no py3 wheel`() {
        val json = """{"info":{"version":"1"},"urls":[
            {"packagetype":"sdist","filename":"x.tar.gz","url":"u","digests":{"sha256":"a"}}]}"""
        assertNull(PyPiYtDlp.parse(json))
    }

    @Test
    fun `returns null when version is missing`() {
        val json = """{"info":{},"urls":[
            {"packagetype":"bdist_wheel","filename":"x-py3-none-any.whl","url":"u","digests":{"sha256":"a"}}]}"""
        assertNull(PyPiYtDlp.parse(json))
    }
}
