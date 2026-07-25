package com.dewijones92.totum.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DownloadStateTest {

    @Test
    fun `fraction is known ratio, else null`() {
        assertEquals(0.25f, DownloadState.Downloading(250, 1000).fraction)
        assertNull(DownloadState.Downloading(250, null).fraction)
        // Zero total is only valid at zero progress, and yields an indeterminate fraction.
        assertNull(DownloadState.Downloading(0, 0).fraction)
    }

    @Test
    fun `downloading invariants hold`() {
        assertThrows(IllegalArgumentException::class.java) { DownloadState.Downloading(-1, 10) }
        assertThrows(IllegalArgumentException::class.java) { DownloadState.Downloading(10, 5) }
    }

    @Test
    fun `downloaded requires a path`() {
        assertThrows(IllegalArgumentException::class.java) { DownloadState.Downloaded(" ") }
    }

    @Test
    fun `an audio-only download is not a video file`() {
        // Playing the queue's automatic audio fetch for a video request gives sound
        // and a blank picture — this is the guard against that.
        assertNull(DownloadState.Downloaded("/tmp/a.m4a", audioOnly = true).videoFileOrNull())
    }

    @Test
    fun `a full download is the video file`() {
        assertEquals("/tmp/v.mkv", DownloadState.Downloaded("/tmp/v.mkv").videoFileOrNull())
    }

    @Test
    fun `an unfinished download is not a video file`() {
        assertNull(DownloadState.NotDownloaded.videoFileOrNull())
        assertNull(DownloadState.Downloading(1, 2).videoFileOrNull())
        assertNull(DownloadState.Failed("boom").videoFileOrNull())
    }
}
