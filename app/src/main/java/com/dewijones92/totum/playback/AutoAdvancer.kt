package com.dewijones92.totum.playback

import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.domain.MediaItemId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Starts the next item when one finishes — off the UI's lifecycle, so it works with the
 * screen off.
 *
 * This used to live in a composable's `LaunchedEffect`, reading playback state through
 * `collectAsStateWithLifecycle()`. That collection **stops when the activity is stopped**, so
 * with the phone in a pocket the composition never saw `hasEnded` flip and the decision was
 * simply never made. A real report caught it exactly: a video ended at 14:39:09 with sixty
 * items queued, and the advance decision was not reached until 14:46:13 — seven minutes
 * later, when the app came back to the foreground. The 30-second activity snapshots kept
 * arriving throughout, because they run on a plain coroutine; only the advance was frozen.
 *
 * The same class of bug as playback work on `rememberCoroutineScope()`: logic that must
 * outlive the UI, hosted by the UI. Anything the user expects to keep happening in their
 * pocket belongs on an application-lifetime scope, and this is the second instance found in
 * one day — worth treating as a pattern rather than two accidents.
 *
 * @param whenQueueEmpty tried when the queue has nothing playable left — the related-video
 *   fallback. Suspending and app-scoped, so it is not tied to a screen either.
 */
internal class AutoAdvancer(
    private val states: Flow<PlaybackState?>,
    private val advance: suspend () -> Boolean,
    private val whenQueueEmpty: suspend () -> Unit,
    private val isEnabled: () -> Boolean,
    private val scope: CoroutineScope,
) {
    /** Ends are per item, so finishing the same item twice is not a reason to skip. */
    private var handled: MediaItemId? = null

    fun start() {
        scope.launch {
            var seenAnyState = false
            states
                .filterNotNull()
                // Only the transition matters; the player re-emits state on every position
                // tick, and acting on each would fire the advance dozens of times.
                .distinctUntilChanged { old, new -> old.itemId == new.itemId && old.hasEnded == new.hasEnded }
                .collect { state ->
                    // The FIRST state is a baseline, never a transition. Connecting to the
                    // playback session reports whatever it currently holds, which after a
                    // process restart can be an item that ended long ago — acting on that
                    // would skip an item the moment the app launched. Only an end that
                    // happens while we are watching counts.
                    if (!seenAnyState) {
                        seenAnyState = true
                        if (state.hasEnded) {
                            handled = state.itemId
                            Diag.log("advance", "${state.itemId.value} was already ended on connect; ignoring")
                        }
                        return@collect
                    }
                    if (state.hasEnded) advancePast(state.itemId)
                }
        }
    }

    private suspend fun advancePast(itemId: MediaItemId) {
        // Every branch says why. The failure mode is silence — an item ends, nothing happens,
        // and there is no way to tell which reason applied.
        val refusal = when {
            !isEnabled() -> "auto-play next is off"
            handled == itemId -> "already handled this item's end"
            else -> null
        }
        if (refusal != null) {
            Diag.log("advance", "not advancing past ${itemId.value}: $refusal")
            return
        }
        handled = itemId
        val advanced = advance()
        Diag.log("advance", "${itemId.value} ended -> queue advance=$advanced")
        if (!advanced) {
            Diag.log("advance", "queue had nothing playable; trying a related video")
            whenQueueEmpty()
        }
    }
}
