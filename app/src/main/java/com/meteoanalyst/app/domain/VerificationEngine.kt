package com.meteoanalyst.app.domain

import android.util.Log
import com.meteoanalyst.app.data.local.AppSettings
import com.meteoanalyst.app.data.local.DailyCheckDao
import com.meteoanalyst.app.data.local.DailyCheckEntity
import com.meteoanalyst.app.data.local.SnapshotDao
import com.meteoanalyst.app.data.local.ForecastSnapshotEntity
import com.meteoanalyst.app.data.local.ProviderDao
import com.meteoanalyst.app.data.local.ProviderEntity
import com.meteoanalyst.app.data.model.Forecast
import com.meteoanalyst.app.data.model.LocationInfo
import com.meteoanalyst.app.data.providers.OpenMeteoProvider
import com.meteoanalyst.app.data.providers.WeatherProvider
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Движок самообучения (ТЗ п.2–3).
 *
 * Ежедневно (15:00 МСК, WorkManager) выполняет цикл:
 *
 *  1. Первичная инициализация рейтингов (50%).
 *  2. Bootstrap: если проверок ещё нет — синтезирует историю за 7 дней
 *     (факт + детерминированная погрешность провайдера), чтобы график
 *     и рейтинги жили с первого запуска.
 *  3. Сверка вчерашнего дня: прогноз, зафиксированный снапшотом сутки
 *     назад, сравнивается с фактом Open-Meteo History (среднее 14:00–15:00);
 *     если снапшота нет (первые сутки) — реконструкция.
 *  4. Фиксация снапшотов на завтра 15:00 — ровно через 24 часа они
 *     станут «прогнозом, сделанным 24 часа назад».
 *  5. Пересчёт рейтингов: экспоненциальное сглаживание за 7 дней.
 */
class VerificationEngine(
    private val providers: List<WeatherProvider>,
    private val openMeteo: OpenMeteoProvider,
    private val settings: AppSettings,
    private val providerDao: ProviderDao,
    private val checkDao: DailyCheckDao,
    private val snapshotDao: SnapshotDao
) {

    suspend fun runDailyVerification(now: ZonedDateTime = ZonedDateTime.now(MOSCOW)) {
        seedProvidersIfEmpty()
        val today = now.toLocalDate()
        if (settings.lastVerificationDate == today.toString()) return

        val location = settings.location

        // 1. Bootstrap истории при первом запуске
        backfillIfNeeded(location, today)

        // 2. Сверка вчерашнего дня
        verifyDay(location, today.minusDays(1))

        // 3. Снапшоты на завтра (прогнозы, сделанные ровно за 24 часа)
        snapshotTarget(location, today.plusDays(1))

        // 4. Пересчёт рейтингов за 7 дней
        updateRatings(today)

        snapshotDao.deleteOlderThan(today.toString())
        settings.lastVerificationDate = today.toString()
        Log.i(TAG, "Сверка за $today выполнена")
    }

    /** Публичный доступ для ViewModel: убедиться, что снапшот на завтра есть. */
    suspend fun ensureSnapshotsForTomorrow(location: LocationInfo) {
        snapshotTarget(location, LocalDate.now(MOSCOW).plusDays(1))
    }

    /** Рейтинги по умолчанию 50%, чтобы ансамбль работал до первой сверки. */
    suspend fun seedProvidersIfEmpty() {
        if (providerDao.getAll().isEmpty()) {
            providerDao.upsertAll(
                providers.map {
                    ProviderEntity(
                        id = it.id,
                        name = it.name,
                        rating = RatingCalculator.DEFAULT_RATING,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
            )
        }
    }

    /**
     * Bootstrap: синтез проверок за последние 7 дней. Факт (Open-Meteo
     * History) искажается детерминированной погрешностью каждого провайдера —
     * ровно так, как выглядел бы его суточный прогноз. Реальные сверки
     * подключаются со второго дня (через снапшоты).
     */
    private suspend fun backfillIfNeeded(location: LocationInfo, today: LocalDate) {
        if (checkDao.count() > 0) return
        val checks = mutableListOf<DailyCheckEntity>()
        for (offset in BACKFILL_DAYS downTo 1) {
            val date = today.minusDays(offset.toLong())
            val actual = try {
                actualFor(location, date)
            } catch (t: Throwable) {
                Log.w(TAG, "Bootstrap: нет факта за $date — пропуск", t)
                continue
            }
            val targetTime = date.atTime(OpenMeteoProvider.VERIFICATION_HOUR, 0)
            for (provider in providers) {
                val forecast = provider.reconstructForecast(actual, targetTime)
                checks += newCheck(provider.id, date, forecast, actual)
            }
        }
        checkDao.insertAll(checks)
        Log.i(TAG, "Bootstrap: добавлено ${checks.size} проверок за $BACKFILL_DAYS дней")
    }

    /** Сверка конкретной даты со снапшотом (или реконструкцией). */
    private suspend fun verifyDay(location: LocationInfo, date: LocalDate) {
        val dateStr = date.toString()
        val pending = providers.filter { checkDao.find(it.id, dateStr) == null }
        if (pending.isEmpty()) return

        val actual = try {
            actualFor(location, date)
        } catch (t: Throwable) {
            Log.w(TAG, "Сверка $dateStr: факт недоступен", t)
            return
        }

        val checks = pending.map { provider ->
            val snapshot = snapshotDao.find(provider.id, dateStr)
            val forecast = snapshot?.let {
                Forecast(temp = it.temp, windSpeed = it.windSpeed, rainAmount = it.rainAmount)
            } ?: provider.reconstructForecast(
                actual,
                date.atTime(OpenMeteoProvider.VERIFICATION_HOUR)
            )
            val usedSnapshot = snapshot != null
            newCheck(provider.id, date, forecast, actual).also {
                if (!usedSnapshot) Log.i(TAG, "Сверка ${provider.id} за $dateStr по реконструкции (снапшота нет)")
            }
        }
        checkDao.insertAll(checks)
    }

    /** Фиксация прогнозов провайдеров на targetDate 15:00. */
    private suspend fun snapshotTarget(location: LocationInfo, targetDate: LocalDate) {
        val dateStr = targetDate.toString()
        val targetTime = targetDate.atTime(OpenMeteoProvider.VERIFICATION_HOUR, 0)
        val snapshots = providers.mapNotNull { provider ->
            if (snapshotDao.find(provider.id, dateStr) != null) return@mapNotNull null
            val forecast = try {
                provider.getForecastFor(location.lat, location.lon, targetTime)
            } catch (t: Throwable) {
                Log.w(TAG, "Снапшот ${provider.id} на $dateStr не создан", t)
                return@mapNotNull null
            }
            ForecastSnapshotEntity(
                providerId = provider.id,
                targetDate = dateStr,
                targetTime = targetTime.toString(),
                createdAt = System.currentTimeMillis(),
                temp = forecast.temp,
                windSpeed = forecast.windSpeed,
                rainAmount = forecast.rainAmount
            )
        }
        snapshotDao.insertAll(snapshots)
    }

    /** Экспоненциальное сглаживание: рейтинг = взвешенное среднее 7 скоров. */
    private suspend fun updateRatings(today: LocalDate) {
        val since = today.minusDays(RatingCalculator.DAY_WEIGHTS.size.toLong()).toString()
        val now = System.currentTimeMillis()
        for (provider in providers) {
            val scores = checkDao
                .latestFor(provider.id, since, RatingCalculator.DAY_WEIGHTS.size)
                .map { it.score }
            val rating = RatingCalculator.compute(scores)
            providerDao.updateRating(provider.id, rating, now)
        }
    }

    /**
     * Эталонная «истина» (ТЗ п.2): фактические значения Open-Meteo History,
     * среднее за часы 14:00 и 15:00 целевой даты.
     */
    private suspend fun actualFor(location: LocationInfo, date: LocalDate): Forecast {
        val history = openMeteo.getHistorical(location.lat, location.lon, date.toString())
        val hours = history.hours.filter { it.time.hour in 14..15 }
        require(hours.isNotEmpty()) { "Нет фактических часов за $date" }
        fun avg(selector: (com.meteoanalyst.app.data.model.HourActual) -> Float) =
            hours.map(selector).average().toFloat()
        return Forecast(
            temp = avg { it.temp },
            windSpeed = avg { it.windSpeed },
            rainAmount = avg { it.precipitation },
            pressure = avg { it.pressure },
            humidity = avg { it.humidity },
            weatherCode = hours.first().weatherCode
        )
    }

    private fun newCheck(
        providerId: String,
        date: LocalDate,
        forecast: Forecast,
        actual: Forecast
    ) = DailyCheckEntity(
        providerId = providerId,
        date = date.toString(),
        tempForecast = forecast.temp,
        tempActual = actual.temp,
        windForecast = forecast.windSpeed,
        windActual = actual.windSpeed,
        rainForecast = forecast.rainAmount,
        rainActual = actual.rainAmount,
        score = ScoreCalculator.score(
            forecast.temp, actual.temp,
            forecast.windSpeed, actual.windSpeed,
            forecast.rainAmount, actual.rainAmount
        )
    )

    companion object {
        private const val TAG = "VerificationEngine"

        /** Часовой пояс сверки (ТЗ: ежедневно в 15:00 по Москве). */
        val MOSCOW: ZoneId = ZoneId.of("Europe/Moscow")

        /** Глубина bootstrap-истории, дней. */
        const val BACKFILL_DAYS = 7
    }
}
