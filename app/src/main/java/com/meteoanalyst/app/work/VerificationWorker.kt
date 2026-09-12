package com.meteoanalyst.app.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.meteoanalyst.app.di.ServiceLocator

/**
 * Фоновая сверка (ТЗ п.2): сравнивает прогнозы, сделанные 24 часа назад,
 * с фактом Open-Meteo History и обновляет рейтинги провайдеров.
 * Запускается ежедневно в 15:00 МСК (см. WorkScheduler).
 */
class VerificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        ServiceLocator.verificationEngine.runDailyVerification()
        Result.success()
    } catch (t: Throwable) {
        Log.w(TAG, "Сверка не выполнена (попытка $runAttemptCount)", t)
        if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
    }

    companion object {
        private const val TAG = "VerificationWorker"
        private const val MAX_RETRIES = 4
    }
}
