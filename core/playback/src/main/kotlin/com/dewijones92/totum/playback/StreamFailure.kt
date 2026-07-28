package com.dewijones92.totum.playback

import com.dewijones92.totum.domain.MediaItemId

/**
 * A stream that failed for a reason re-resolving would fix, with the position to resume at.
 *
 * Deliberately not the [androidx.media3.common.PlaybackException]: the listener's job is to
 * fetch a fresh URL and carry on, and everything it needs is here. Keeping Media3 out of the
 * signal is also what lets the fake controller raise one in a unit test.
 */
public data class StreamFailure(
    public val itemId: MediaItemId,
    public val positionMs: Long,
)
