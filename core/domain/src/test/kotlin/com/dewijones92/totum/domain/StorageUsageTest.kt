package com.dewijones92.totum.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageUsageTest {

    @Test
    fun `bytes read as a person would say them`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1 KB", formatBytes(1024))
        assertEquals("412 KB", formatBytes(422_000))
        assertEquals("1.5 MB", formatBytes(1_572_864))
        assertEquals("1.2 GB", formatBytes(1_288_490_189))
    }

    /** "5 MB" rather than "5.0 MB" — the decimal is only there when it says something. */
    @Test
    fun `a whole number keeps no decimal point`() {
        assertEquals("5 MB", formatBytes(5L * 1024 * 1024))
        assertEquals("2 GB", formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `an empty library costs nothing`() {
        assertEquals(0, StorageUsage.Empty.usedBytes)
        assertEquals(0, StorageUsage.Empty.itemCount)
        assertEquals("0 B", formatBytes(StorageUsage.Empty.usedBytes))
    }
}
