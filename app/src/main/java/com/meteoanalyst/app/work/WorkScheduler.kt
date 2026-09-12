package com.meteoanalyst.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Планировщик WorkManager (ТЗ п.2, п.7): периодическая сверка
 * ровно в 15:00 по Москве, при наличии сети.
 */
object WorkScheduler {

    const val PERIODIC_WORK_NAME = "Verification"
    private const val ONE_TIME_WORK_NAME = "VerificationNow"

    /** МСК без перехода на летнее время. */
    val MOSCOW: ZoneId = ZoneId.of("Europe/Moscow")

    /**
     * Задержка до ближайших 15:00 МСК: если сейчас раньше 15:00 — сегодня,
     * иначе — завтра.
     */
    fun computeDelayUntil15Moscow(now: ZonedDateTime = ZonedDateTime.now(MOSCOW)): Long {
        val today15 = now.toLocalDate().atTime(15, 0).atZone(MOSCOW)
        val target = if (now.isBefore(today15)) today15 else today15.plusDays(1)
        return Duration.between(now, target).toMillis().coerceAtLeast(0)
    }

    /** Ежедневная сверка в 15:00 МСК (ТЗ). */
    fun scheduleDailyVerification(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<VerificationWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setInitialDelay(computeDelayUntil15Moscow(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Разовая сверка «прямо сейчас»: догоняет пропущенные 15:00
     * (приложение не было запущено) и выполняет bootstrap при первом
     * запуске. Движок идемпотентен: в тот же день повторно не сработает.
     */
    fun runVerificationNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<VerificationWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
