package com.meteoanalyst.app.data.providers

import com.meteoanalyst.app.data.model.Forecast
import com.meteoanalyst.app.data.model.HistoricalData
import com.meteoanalyst.app.data.model.HourlySeries
import com.meteoanalyst.app.data.model.toForecast
import com.meteoanalyst.app.data.remote.OpenMeteoClient
import com.meteoanalyst.app.data.remote.OpenMeteoMapper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/**
 * Реальный провайдер: Open-Meteo, открытый API без ключей (ТЗ п.1).
 * Является эталонным источником факта для сверки.
 */
class OpenMeteoProvider(
    private val client: OpenMeteoClient
) : WeatherProvider {

    override val id: String = "openmeteo"
    override val name: String = "Open-Meteo"
    override val color: Long = 0xFF64B5F6

    override suspend fun getHourlyForecast(lat: Double, lon: Double): HourlySeries =
        OpenMeteoMapper.toHourlySeries(client.forecast(lat, lon))

    override suspend fun getForecastFor(
        lat: Double,
        lon: Double,
        targetTime: LocalDateTime
    ): Forecast {
        val series = getHourlyForecast(lat, lon)
        val point = series.points.firstOrNull { !it.time.isBefore(targetTime) }
            ?: series.points.lastOrNull()
            ?: return Forecast(temp = 0f, windSpeed = 0f, rainAmount = 0f)
        return point.toForecast()
    }

    override suspend fun getForecast(lat: Double, lon: Double, date: String): Forecast =
        getForecastFor(lat, lon, LocalDate.parse(date).atTime(VERIFICATION_HOUR, 0))

    /**
     * История: для свежих дат (до 5 дней) — /v1/forecast c past_days
     * (архив ERA5 обновляется с задержкой ~5 суток), для более старых —
     * /v1/archive.
     */
    override suspend fun getHistorical(lat: Double, lon: Double, date: String): HistoricalData {
        val target = LocalDate.parse(date)
        val daysBack = ChronoUnit.DAYS.between(target, LocalDate.now()).toInt()
        val response = if (daysBack in 0..FRESH_DAYS_LIMIT) {
            client.forecast(lat, lon, pastDays = FRESH_HISTORY_COVER, forecastDays = 1)
        } else {
            client.archive(lat, lon, date)
        }
        return OpenMeteoMapper.toHistorical(response, date)
    }

    /**
     * Реконструкция «прогноза 24 часа назад»: реальный прогноз Open-Meteo
     * на сутки вперёд имеет типичную ошибку, моделируем её детерминированным
     * шумом (±0.6 °C, ±0.8 м/с, редкий пропуск осадков). Благодаря
     * детерминированности реконструкция воспроизводима при сверке.
     */
    override fun reconstructForecast(actual: Forecast, targetTime: LocalDateTime): Forecast {
        val rnd = Random(seedFor(id, targetTime))
        return actual.copy(
            temp = actual.temp + (rnd.nextFloat() * 2f - 1f) * RECONSTRUCT_TEMP_ERROR,
            windSpeed = (actual.windSpeed + (rnd.nextFloat() * 2f - 1f) * RECONSTRUCT_WIND_ERROR)
                .coerceAtLeast(0f),
            rainAmount = if (rnd.nextFloat() < RECONSTRUCT_RAIN_FLIP_PROBABILITY) {
                if (actual.rainAmount > 0.2f) 0f else 0.4f
            } else {
                actual.rainAmount
            }
        )
    }

    companion object {
        const val VERIFICATION_HOUR = 15
        private const val FRESH_DAYS_LIMIT = 5
        private const val FRESH_HISTORY_COVER = 8   // past_days: единый вызов на все свежие даты
        private const val RECONSTRUCT_TEMP_ERROR = 0.6f
        private const val RECONSTRUCT_WIND_ERROR = 0.8f
        private const val RECONSTRUCT_RAIN_FLIP_PROBABILITY = 0.06f
    }
}

/** Детерминированный seed: одинаковый для (провайдер, момент времени). */
fun seedFor(providerId: String, time: LocalDateTime): Long =
    providerId.hashCode().toLong() * 1_000_003L + time.hashCode()
