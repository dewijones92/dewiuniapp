package com.dewijones92.uniapp.domain

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
}
