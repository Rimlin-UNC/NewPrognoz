package com.meteoanalyst.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.meteoanalyst.app.di.ServiceLocator
import java.util.concurrent.TimeUnit

/**
 * Фоновая синхронизация (offline-first): каждые 6 часов при наличии сети —
 * push очереди наблюдений + pull bias-коррекции. Ручной запуск — из UI.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val result = ServiceLocator.syncEngine.syncNow()
        Log.i(TAG, "Sync: pushed=${result.pushed} pulled=${result.pulled} err=${result.error}")
        Result.success()
    } catch (t: Throwable) {
        Log.w(TAG, "Sync failed (attempt $runAttemptCount)", t)
        if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val WORK_NAME = "SyncPeriodic"
        private const val MAX_RETRIES = 5

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
