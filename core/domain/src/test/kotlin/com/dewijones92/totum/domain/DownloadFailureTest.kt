package com.dewijones92.totum.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFailureTest {

    /** The exact text from the reports that prompted this. */
    @Test
    fun `members-only is permanent`() {
        assertTrue(
            failed("ERROR: [youtube] 77NdbZYoatg: Join this channel to get access to members-only content").isPermanent
        )
    }

    @Test
    fun `unavailable and private videos are permanent`() {
        assertTrue(failed("ERROR: [youtube] abc: Video unavailable").isPermanent)
        assertTrue(failed("ERROR: [youtube] abc: Private video. Sign in if granted access").isPermanent)
    }

    @Test
    fun `age-gated is permanent — an unattended retry cannot sign in`() {
        assertTrue(failed("ERROR: Sign in to confirm your age").isPermanent)
    }

    @Test
    fun `matching ignores case, since wording is not ours to rely on`() {
        assertTrue(failed("JOIN THIS CHANNEL to get access").isPermanent)
    }

    @Test
    fun `a network failure stays retryable`() {
        assertFalse(failed("Unable to connect: timeout").isPermanent)
        assertFalse(failed("HTTP Error 503: Service Unavailable").isPermanent)
    }

    /** Unrecognised means retry: giving up wrongly is worse than one wasted request. */
    @Test
    fun `anything unrecognised stays retryable`() {
        assertFalse(failed("something nobody has seen before").isPermanent)
        assertFalse(failed("").isPermanent)
    }

    private fun failed(reason: String) = DownloadState.Failed(reason)
}
