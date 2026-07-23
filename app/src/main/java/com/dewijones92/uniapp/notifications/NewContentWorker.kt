package com.dewijones92.uniapp.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
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
            NewContentNotifier(applicationContext).notify(container.contentRefresher.findNewContent())
            Result.success()
        }.getOrElse { Result.retry() }
    }

    public companion object {
        private const val UNIQUE_NAME = "new-content-refresh"
        private const val INTERVAL_HOURS = 6L

        /** Schedules the periodic refresh, keeping any already-scheduled instance. */
        public fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NewContentWorker>(INTERVAL_HOURS, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
