package com.dewijones92.totum.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which failures earn a re-resolve. The cause-chain walk that feeds this is not covered
 * here — building the Media3 exception needs an `android.net.Uri`, which a JVM test cannot
 * make — but the codes are the part with a judgement in them.
 */
class ExpiredStatusTest {

    @Test
    fun `403 is an expired signature`() = assertTrue(isExpiredStatus(403))

    @Test
    fun `410 is a retired URL`() = assertTrue(isExpiredStatus(410))

    /** The content is gone; a fresh address would find nothing there either. */
    @Test
    fun `404 is not re-resolved`() = assertFalse(isExpiredStatus(404))

    @Test
    fun `a server error is not re-resolved`() {
        assertFalse(isExpiredStatus(500))
        assertFalse(isExpiredStatus(503))
    }

    @Test
    fun `success is obviously not an expiry`() = assertFalse(isExpiredStatus(200))
}
