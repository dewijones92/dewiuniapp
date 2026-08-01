package com.dewijones92.totum.playback

import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.domain.MediaItemId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Moves on when an item stops at its own end and never reports finishing.
 *
 * [AutoAdvancer] waits for the player to say `hasEnded`, and [ExpiredStreamRecovery] waits
 * for it to raise an error. A stall is neither: the player sits in BUFFERING with the
 * position frozen, forever, and both watchers are blind to it. With the screen off that is
 * indistinguishable from the queue simply stopping.
 *
 * A real report (0.1.230, 2026-07-31): a 41-minute video reached 2506062ms — inside the last
 * ten seconds, with watch-history already reporting it finished — went to BUFFERING at
 * 07:55:48 and was still at exactly that position 46 seconds later, across two 30-second
 * snapshots, until Dewi gave up and picked the next item by hand. Sixty-five items were
 * queued behind it.
 *
 * So: a position that has not moved for [STALL_MS] while buffering, within [END_MS] of the
 * duration, means this item is over whatever the player believes. Advance.
 *
 * **It SAMPLES the state on a clock rather than collecting it, and that is the whole trick.**
 * `PlaybackController.state` is a `StateFlow`, which drops a value equal to the one before
 * it — and a stall is by definition a run of identical states: same item, same position, same
 * buffering flag. A collector therefore gets exactly one emission when the stall begins and
 * then silence, so a timer driven by emissions would only ever be read at zero elapsed and
 * would never fire. Nothing about that failure is visible in a log; it just quietly does
 * nothing. The unit tests caught it before it shipped, which is why they tick a frozen state.
 *
 * A stall anywhere EARLIER is only logged, deliberately. It is the same class of fault and
 * just as fatal in a pocket, but re-resolving mid-item is a behaviour change to shipped
 * playback — a slow train tunnel would restart the video rather than wait it out — and there
 * is not one observation of it yet to design against. The log says how long and where, so
 * the next report can settle it.
 */
internal class StallWatchdog(
    private val states: StateFlow<PlaybackState?>,
    private val advance: suspend () -> Boolean,
    private val isEnabled: () -> Boolean,
    private val scope: CoroutineScope,
    private val checkEveryMs: Long = CHECK_MS,
) {
    private var stuckItem: MediaItemId? = null
    private var stuckPositionMs = -1L
    private var stalledForMs = 0L

    /**
     * The stall already rescued, so a frozen player cannot advance the queue over and over.
     *
     * Cleared the moment that item makes progress again. It used to be set once per item and
     * kept forever, which is the same defect that broke [AutoAdvancer]: an item rescued once
     * could never be rescued again, so replaying it and stalling again left the queue stopped
     * with nothing in the log to say why. Fixed here before it was ever reported, because the
     * two were the same three lines written twice.
     */
    private var handled: MediaItemId? = null

    fun start() {
        Diag.log("advance", "watching for stalls (a frozen buffer at the end of an item)")
        scope.launch {
            while (true) {
                delay(checkEveryMs)
                check(states.value)
            }
        }
    }

    private suspend fun check(state: PlaybackState?) {
        // A paused player has a frozen position too, and is not stuck — only a player that
        // is trying to load something can be.
        if (state == null || !state.isBuffering) {
            stuckItem = null
            return
        }
        if (state.itemId != stuckItem || state.positionMs != stuckPositionMs) {
            // A stall that recovers is the only evidence there will ever be for how long a
            // NORMAL re-buffer lasts, which is what the STALL_MS threshold is guessing at
            // and what the deferred mid-item decision needs. It costs one line per stall.
            if (stalledForMs >= NOTEWORTHY_MS && stuckItem != null) {
                Diag.log(
                    "advance",
                    "${stuckItem?.value} recovered after ${stalledForMs}ms stuck at ${stuckPositionMs}ms",
                )
            }
            stuckItem = state.itemId
            stuckPositionMs = state.positionMs
            stalledForMs = 0
            // Progress means any earlier rescue of this item is spent, not a reason to refuse
            // the next one.
            if (handled == state.itemId) {
                Diag.log("advance", "${state.itemId.value} is moving again; its earlier stall no longer counts")
                handled = null
            }
            return
        }
        stalledForMs += checkEveryMs
        if (stalledForMs < STALL_MS || handled == state.itemId) return
        handled = state.itemId

        // "starved" vs "stuck" is the question a stall report has never been able to answer,
        // and it decides whether the fix is a fresh URL or a nudge to the player.
        val bufferedAheadMs = state.bufferedPositionMs - state.positionMs
        val diagnosis =
            if (bufferedAheadMs > 0) "STUCK (${bufferedAheadMs}ms buffered)" else "STARVED (nothing buffered)"

        val remainingMs = state.durationMs?.minus(state.positionMs)
        if (remainingMs == null || remainingMs > END_MS) {
            Diag.warn(
                "advance",
                "${state.itemId.value} stalled ${stalledForMs}ms at ${state.positionMs}ms — $diagnosis, " +
                    "${remainingMs}ms left — not at the end, so leaving it to the player",
            )
            return
        }
        if (!isEnabled()) {
            Diag.log(
                "advance",
                "${state.itemId.value} stalled ${stalledForMs}ms at its end, but auto-play next is off",
            )
            return
        }
        Diag.log(
            "advance",
            "${state.itemId.value} stalled ${stalledForMs}ms with only ${remainingMs}ms left " +
                "($diagnosis); treating it as ended",
        )
        Diag.log("advance", "${state.itemId.value} stall advance=${advance()}")
    }

    private companion object {
        /** Often enough to be responsive, rare enough to cost nothing when all is well. */
        const val CHECK_MS = 5_000L

        /**
         * A recovered pause worth one line. Below this it is an ordinary re-buffer and
         * saying so would cost more report buffer than it is worth.
         */
        const val NOTEWORTHY_MS = 10_000L

        /**
         * Long enough that an ordinary re-buffer is never mistaken for a stall, short enough
         * that the gap is not what the user notices. The observed stall was still frozen at
         * 46 seconds.
         */
        const val STALL_MS = 20_000L

        /**
         * How close to the duration counts as "this is the end". The observed stall was 7
         * seconds short; a whole item's tail is what a player fails to load, not a minute of
         * it.
         */
        const val END_MS = 15_000L
    }
}
