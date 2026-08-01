package com.dewijones92.totum.notifications

import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.common.Vitals
import com.dewijones92.totum.data.content.ContentRefresher
import com.dewijones92.totum.data.content.SourceUpdate

/**
 * One check for new content, shared by the background worker and the "check now" action.
 *
 * Extracted because the two must never drift: a button that runs *nearly* the same code as the
 * six-hourly job is worse than no button, since it would prove the wrong thing. This holds the
 * whole decision — find, deliver, and only then advance the seen-state — and both callers do
 * nothing but map its [Outcome] onto their own vocabulary.
 *
 * Delivery and detection stay separate for the reason [ContentRefresher] already documents: a
 * run that cannot notify (permission ungranted, transient failure) must NOT quietly consume new
 * items into the seen-set, or they are lost for good.
 */
internal class NewContentCheck(
    private val refresher: ContentRefresher,
    /** Returns true when the user was actually shown something. */
    private val notify: suspend (List<SourceUpdate>) -> Boolean,
) {
    /**
     * What a check did, in the caller's terms.
     *
     * Sealed and specific because the four cases mean genuinely different things to a person:
     * nothing new is success, undelivered is usually a permission they can grant, and a throw is
     * a bug. Collapsing them to a boolean is what made "I never get notified" and "there was
     * nothing new" indistinguishable in the first place.
     */
    sealed interface Outcome {
        data object NothingNew : Outcome
        data class Notified(val items: Int, val sources: Int) : Outcome

        /** Found things, could not tell anyone — most often `POST_NOTIFICATIONS` is ungranted. */
        data class Undelivered(val items: Int) : Outcome
        data class Failed(val error: Throwable) : Outcome
    }

    suspend fun run(): Outcome = runCatching { check() }.getOrElse { error ->
        // The throwable, kept. This used to be discarded into a bare retry, so a job failing
        // every six hours for weeks left nothing to diagnose it with.
        Vitals.add("content.checkFailures")
        Diag.warn("content", "content check threw", error)
        Outcome.Failed(error)
    }

    private suspend fun check(): Outcome {
        val batch = refresher.findNewContent()
        if (batch.newContent.isEmpty()) {
            // Still advance the seen-state: this is the bootstrap and steady-state path.
            batch.markDelivered()
            Diag.log("content", "nothing new; seen-state advanced")
            return Outcome.NothingNew
        }
        val items = batch.newContent.sumOf { it.items.size }
        if (!notify(batch.newContent)) {
            Vitals.add("content.undelivered")
            Diag.warn(
                "content",
                "found $items new item(s) but could NOT notify — most often POST_NOTIFICATIONS " +
                    "not granted; leaving them unseen to retry",
            )
            return Outcome.Undelivered(items)
        }
        batch.markDelivered()
        Diag.log("content", "notified about $items new item(s) from ${batch.newContent.size} source(s)")
        return Outcome.Notified(items, batch.newContent.size)
    }
}
