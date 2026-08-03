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
 * A stall EARLIER in an item is rescued differently: by replaying it from where it stopped with
 * a freshly resolved URL, never by advancing. Skipping a video because it hiccuped in the middle
 * would be a worse bug than the hiccup.
 *
 * That case used to be logged and left alone, on the grounds that re-resolving mid-item was a
 * behaviour change with *"not one observation of it yet to design against"*. Report 0.1.332 is
 * that observation. A Dwarkesh video froze at 652353ms with 48ms buffered on a connection
 * measuring 125Mbps; this watchdog saw it at 20 seconds, said *"not at the end, so leaving it to
 * the player"*, and the player never recovered. Four consecutive 30-second snapshots show the
 * position unchanged. It ended after **2 minutes 16 seconds** because Dewi dismissed the player
 * and pressed play again — and the log shows what that did: a fresh `extract` and a new
 * googlevideo URL, after which it played on normally.
 *
 * So the recovery that works was already in the codebase ([PlaybackQueue.replayCurrent], which
 * [ExpiredStreamRecovery] uses for expired streams). Nothing triggered it, because a request that
 * hangs raises no error — and an error is the only thing that recovery listens for. A hang is
 * silent, so the only watcher that can see it is this one, sampling the clock.
 */
internal class StallWatchdog(
    private val states: StateFlow<PlaybackState?>,
    private val advance: suspend () -> Boolean,
    /**
     * Re-resolves the current item and plays it again from the given position — the rescue for a
     * stall that is NOT at the end, and the thing Dewi had to do by hand for 2m16s.
     */
    private val replay: suspend (Long) -> Boolean,
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
        // Against a floor rather than zero. The threshold used to be `> 0`, which called 48ms
        // "STUCK" — implying the player holds data and needs a nudge — when 48ms of a 1080p
        // stream is starvation in every practical sense, and the fix for it is a fresh URL.
        val diagnosis = if (bufferedAheadMs > STARVED_UNDER_MS) {
            "STUCK (${bufferedAheadMs}ms buffered)"
        } else {
            "STARVED (only ${bufferedAheadMs}ms buffered)"
        }

        val remainingMs = state.durationMs?.minus(state.positionMs)
        if (remainingMs == null || remainingMs > END_MS) {
            // Mid-item: replay from where it stopped rather than advancing. Advancing would skip
            // a video the person is watching, which is a worse outcome than the stall.
            Diag.warn(
                "advance",
                "${state.itemId.value} stalled ${stalledForMs}ms at ${state.positionMs}ms — $diagnosis, " +
                    "${remainingMs}ms left — replaying it from there with a fresh stream",
            )
            Diag.log("advance", "${state.itemId.value} stall replay=${replay(state.positionMs)}")
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
         * Below this much buffered, call it starvation rather than a stuck player.
         *
         * Report 0.1.332 froze with 48ms and then 55ms in hand — reported as "STUCK", which reads
         * as "it has data and is not draining it" and points at the wrong fix entirely. A fifth of
         * a second is nothing on any stream this app plays.
         */
        const val STARVED_UNDER_MS = 200L

        /**
         * How close to the duration counts as "this is the end". The observed stall was 7
         * seconds short; a whole item's tail is what a player fails to load, not a minute of
         * it.
         */
        const val END_MS = 15_000L
    }
}
