package com.dewijones92.totum.playback

import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.domain.MediaItemId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Re-resolves a stream whose URL has expired and carries on from where it stopped.
 *
 * A streaming URL is a lease. YouTube signs one for a few hours, and after that every
 * request is a 403 — so pausing overnight and pressing play in the morning cannot work, no
 * matter how many times the player retries. It retried seventeen times in a real report
 * (0.1.170: paused 23:50 at 35 minutes in, resumed 06:07) and would have retried forever,
 * because nothing in the app knew the difference between "the network hiccuped" and "this
 * address is dead". The queue holds the stable watch URL, so a fresh one is always one
 * re-resolve away.
 *
 * Pillar-agnostic: it reacts to the failure signal and asks the queue to replay whatever is
 * current, which routes by pillar exactly as an ordinary play does.
 *
 * @param replay plays the current item from a position, returning whether it started.
 */
internal class ExpiredStreamRecovery(
    private val failures: Flow<StreamFailure>,
    private val replay: suspend (Long) -> Boolean,
    private val scope: CoroutineScope,
    private val maxAttempts: Int = MAX_ATTEMPTS,
) {
    private var lastItem: MediaItemId? = null
    private var lastPositionMs = 0L
    private var attempts = 0

    fun start() {
        scope.launch {
            failures.collect(::recover)
        }
    }

    private suspend fun recover(failure: StreamFailure) {
        if (failure.shouldResetBudget()) {
            attempts = 0
        }
        lastItem = failure.itemId
        lastPositionMs = failure.positionMs

        if (attempts >= maxAttempts) {
            // Giving up is the point. Re-resolving repeatedly against something that is
            // genuinely gone — a private or deleted video — would be the same forever-loop
            // wearing a different hat, and would hide the real reason behind retry noise.
            Diag.warn("playback", "stream still failing after $attempts re-resolves; leaving it")
            return
        }
        attempts++
        Diag.log("playback", "re-resolving expired stream (attempt $attempts) from ${failure.positionMs}ms")
        if (!replay(failure.positionMs)) {
            Diag.warn("playback", "could not replay after expiry — nothing current, or it would not resolve")
        }
    }

    /**
     * A retry budget is per stuck point, not per item. Playing on and expiring again later
     * is a different, legitimate failure — a long listen crosses more than one lease — so
     * real progress since the last one earns a fresh budget. Without this, three expiries in
     * one sitting would permanently disable recovery for that item.
     */
    private fun StreamFailure.shouldResetBudget(): Boolean =
        itemId != lastItem || positionMs > lastPositionMs + PROGRESS_MS

    private companion object {
        const val MAX_ATTEMPTS = 3

        /** Playback this much further on means the previous re-resolve worked. */
        const val PROGRESS_MS = 30_000L
    }
}
