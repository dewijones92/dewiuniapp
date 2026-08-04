package com.dewijones92.totum.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a queue row should admit when there is no network.
 *
 * The queue already skips these rather than spending a minute of stall budget proving each one is
 * unplayable. That is the right behaviour and completely invisible — an item silently passed over
 * looks like the app losing your place, which is why the row has to say it.
 */
class UnavailableOfflineNowTest {

    @Test
    fun `offline and not downloaded is unavailable`() {
        assertTrue(unavailableOfflineNow(DownloadState.NotDownloaded, offline = true))
    }

    /** The whole point of having downloaded it. */
    @Test
    fun `offline but downloaded is available`() {
        assertFalse(unavailableOfflineNow(DownloadState.Downloaded("/data/a.mp3"), offline = true))
    }

    /**
     * A download that is still running is unavailable offline too — and will stay that way, since
     * the bytes it is waiting for cannot arrive. Saying "downloading" would be a promise the
     * device cannot keep.
     */
    @Test
    fun `offline and still downloading is unavailable`() {
        assertTrue(
            unavailableOfflineNow(DownloadState.Downloading(downloadedBytes = 1L, totalBytes = 2L), offline = true)
        )
    }

    @Test
    fun `online, nothing is unavailable for want of a network`() {
        assertFalse(unavailableOfflineNow(DownloadState.NotDownloaded, offline = false))
        assertFalse(
            unavailableOfflineNow(DownloadState.Downloading(downloadedBytes = 1L, totalBytes = 2L), offline = false)
        )
        assertFalse(unavailableOfflineNow(DownloadState.Downloaded("/data/a.mp3"), offline = false))
    }
}
