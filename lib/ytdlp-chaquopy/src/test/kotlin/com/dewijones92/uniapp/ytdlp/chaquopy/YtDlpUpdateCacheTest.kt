package com.dewijones92.uniapp.ytdlp.chaquopy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class YtDlpUpdateCacheTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun cache() = YtDlpUpdateCache(temp.root)

    @Test
    fun `empty cache reports nothing`() {
        assertNull(cache().activeWheelPath())
        assertNull(cache().cachedVersion())
    }

    @Test
    fun `install stores the wheel and replaces older ones`() {
        val cache = cache()
        cache.install("2025.07.10", byteArrayOf(1, 2, 3))
        cache.install("2025.07.13", byteArrayOf(4, 5, 6))

        assertEquals("2025.07.13", cache.cachedVersion())
        assertTrue(cache.activeWheelPath()!!.endsWith("yt_dlp-2025.07.13.whl"))
        // Only the newest wheel remains.
        assertEquals(1, temp.root.listFiles { f -> f.name.endsWith(".whl") }!!.size)
    }

    @Test
    fun `newest version wins when several wheels are present`() {
        File(temp.root, "yt_dlp-2025.01.05.whl").writeBytes(byteArrayOf(0))
        File(temp.root, "yt_dlp-2025.12.31.whl").writeBytes(byteArrayOf(0))
        assertEquals("2025.12.31", cache().cachedVersion())
    }

    @Test
    fun `delete removes the wheel`() {
        val cache = cache()
        val path = cache.install("2025.07.13", byteArrayOf(1)).absolutePath
        cache.delete(path)
        assertNull(cache.cachedVersion())
    }
}
