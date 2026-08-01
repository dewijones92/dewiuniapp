package com.dewijones92.totum.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dewijones92.totum.TotumApplication
import com.dewijones92.totum.common.Diag
import com.dewijones92.totum.common.Vitals
import java.util.concurrent.TimeUnit

/**
 * Periodically refreshes every subscription (both pillars) and notifies the user
 * of genuinely-new content. All the pillar logic lives behind
 * [com.dewijones92.totum.di.AppContainer.contentRefresher]; this worker only
 * wires that seam to WorkManager and the notifier.
 */
public class NewContentWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as TotumApplication).container
        // Said before the work, so a run that never returns is distinguishable from one that
        // was never scheduled — WorkManager can defer this for hours and nothing else records
        // that it woke up at all.
        Diag.log("content", "background refresh starting (every $INTERVAL_HOURS hours)")
        return runCatching {
            val batch = container.contentRefresher.findNewContent()
            if (batch.newContent.isEmpty()) {
                // Nothing to deliver — still advance the seen-state (bootstrap / steady state).
                batch.markDelivered()
                Diag.log("content", "nothing new; seen-state advanced")
                return@runCatching Result.success()
            }
            val items = batch.newContent.sumOf { it.items.size }
            if (NewContentNotifier(applicationContext).notify(batch.newContent)) {
                batch.markDelivered()
                Diag.log("content", "notified about $items new item(s) from ${batch.newContent.size} source(s)")
                Result.success()
            } else {
                // Couldn't deliver (permission not granted yet, transient failure) — leave
                // the items unseen so they're found again once we can notify. Named, because
                // "I never get notified" and "there was nothing new" are indistinguishable
                // otherwise, and the usual cause is a permission the user can simply grant.
                Vitals.add("content.undelivered")
                Diag.warn(
                    "content",
                    "found $items new item(s) but could NOT notify — most often POST_NOTIFICATIONS " +
                        "not granted; leaving them unseen to retry",
                )
                Result.retry()
            }
        }.getOrElse { error ->
            // This used to swallow the throwable whole. A background job retrying every six
            // hours for weeks with no trace of why is the hardest possible thing to diagnose.
            Vitals.add("content.workerFailures")
            Diag.warn("content", "background refresh threw; retrying", error)
            Result.retry()
        }
    }

    public companion object {
        private const val UNIQUE_NAME = "new-content-refresh"
        internal const val INTERVAL_HOURS = 6L

        /** Schedules the periodic refresh, keeping any already-scheduled instance. */
        public fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NewContentWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
