package com.dewijones92.uniapp.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dewijones92.uniapp.UniAppApplication
import java.util.concurrent.TimeUnit

/**
 * Periodically refreshes every subscription (both pillars) and notifies the user
 * of genuinely-new content. All the pillar logic lives behind
 * [com.dewijones92.uniapp.di.AppContainer.contentRefresher]; this worker only
 * wires that seam to WorkManager and the notifier.
 */
public class NewContentWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as UniAppApplication).container
        return runCatching {
            val batch = container.contentRefresher.findNewContent()
            if (batch.newContent.isEmpty()) {
                // Nothing to deliver — still advance the seen-state (bootstrap / steady state).
                batch.markDelivered()
                return@runCatching Result.success()
            }
            if (NewContentNotifier(applicationContext).notify(batch.newContent)) {
                batch.markDelivered()
                Result.success()
            } else {
                // Couldn't deliver (permission not granted yet, transient failure) — leave
                // the items unseen so they're found again once we can notify.
                Result.retry()
            }
        }.getOrElse { Result.retry() }
    }

    public companion object {
        private const val UNIQUE_NAME = "new-content-refresh"
        private const val INTERVAL_HOURS = 6L

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
