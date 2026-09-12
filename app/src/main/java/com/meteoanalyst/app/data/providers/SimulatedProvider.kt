package com.meteoanalyst.app.data.providers

import com.meteoanalyst.app.data.model.Forecast
import com.meteoanalyst.app.data.model.HistoricalData
import com.meteoanalyst.app.data.model.HourlySeries
import com.meteoanalyst.app.data.model.WeatherPoint
import com.meteoanalyst.app.data.model.toForecast
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * Провайдер-заглушка (ТЗ п.1): берёт реальные данные Open-Meteo и вносит
 * собственную детерминированную погрешность, имитируя независимый источник:
 *
 *   temp  = realTemp + bias + (rnd * 2 - 1) * tempError      // напр. ±1.5 °C
 *   wind  = realWind + bias + (rnd * 2 - 1) * windError      // напр. ±2 м/с
 *   rain  = бинарный шум (пропуск/ложные осадки) или добавка
 *
 * Детерминированность (seed = hash(id + время)) критична: она позволяет
 * при сверке точно воспроизвести «прогноз, сделанный 24 часа назад»,
 * пока не накопились реальные снапшоты.
 *
 * При появлении коммерческого ключа класс заменяется реальной реализацией
 * WeatherProvider — остальная система не меняется.
 */
class SimulatedProvider(
    private val base: OpenMeteoProvider,
    override val id: String,
    override val name: String,
    override val color: Long,
    private val tempError: Float = 1.5f,
    private val windError: Float = 2f,
    private val tempBias: Float = 0f,
    private val windBias: Float = 0f,
    private val rainNoise: RainNoise = RainNoise.NONE,
    private val rainFlipProbability: Float = 0.2f,
    private val rainAddAmount: Float = 0.3f
) : WeatherProvider {

    enum class RainNoise { NONE, FLIP, ADD }

    override suspend fun getHourlyForecast(lat: Double, lon: Double): HourlySeries {
        val baseSeries = base.getHourlyForecast(lat, lon)
        return HourlySeries(
            points = baseSeries.points.map { perturb(it) },
            utcOffsetSeconds = baseSeries.utcOffsetSeconds,
            timezone = baseSeries.timezone
        )
    }

    override suspend fun getForecastFor(
        lat: Double,
        lon: Double,
        targetTime: LocalDateTime
    ): Forecast {
        val baseSeries = base.getHourlyForecast(lat, lon)
        val point = baseSeries.points.firstOrNull { !it.time.isBefore(targetTime) }
            ?: baseSeries.points.lastOrNull()
            ?: return Forecast(temp = 0f, windSpeed = 0f, rainAmount = 0f)
        return perturb(point).toForecast()
    }

    override suspend fun getForecast(lat: Double, lon: Double, date: String): Forecast =
        getForecastFor(
            lat, lon,
            LocalDate.parse(date).atTime(OpenMeteoProvider.VERIFICATION_HOUR, 0)
        )

    /** Факт — всегда из эталонного Open-Meteo History. */
    override suspend fun getHistorical(lat: Double, lon: Double, date: String): HistoricalData =
        base.getHistorical(lat, lon, date)

    override fun reconstructForecast(actual: Forecast, targetTime: LocalDateTime): Forecast {
        val rnd = Random(seedFor(id, targetTime))
        val dTemp = tempBias + (rnd.nextFloat() * 2f - 1f) * tempError
        val dWind = windBias + (rnd.nextFloat() * 2f - 1f) * windError
        val rain = perturbRain(actual.rainAmount, rnd)
        return actual.copy(
            temp = actual.temp + dTemp,
            windSpeed = (actual.windSpeed + dWind).coerceAtLeast(0f),
            rainAmount = rain
        )
    }

    private fun perturb(point: WeatherPoint): WeatherPoint {
        val rnd = Random(seedFor(id, point.time))
        val dTemp = tempBias + (rnd.nextFloat() * 2f - 1f) * tempError
        val dWind = windBias + (rnd.nextFloat() * 2f - 1f) * windError
        val precipitation = perturbRain(point.precipitation, rnd)
        // Дождь корректируем согласованно с суммарными осадками
        val rain = (point.rain + (precipitation - point.precipitation))
            .coerceIn(0f, precipitation)
        return point.copy(
            temperature = point.temperature + dTemp,
            apparentTemperature = point.apparentTemperature + dTemp,
            windSpeed = (point.windSpeed + dWind).coerceAtLeast(0f),
            precipitation = precipitation,
            rain = rain
        )
    }

    private fun perturbRain(baseRain: Float, rnd: Random): Float = when (rainNoise) {
        RainNoise.NONE -> baseRain
        RainNoise.FLIP ->
            if (rnd.nextFloat() < rainFlipProbability) {
                if (baseRain > RAIN_THRESHOLD) 0f else rainAddAmount
            } else {
                baseRain
            }
        RainNoise.ADD ->
            if (rnd.nextFloat() < rainFlipProbability) baseRain + rainAddAmount else baseRain
    }

    companion object {
        const val RAIN_THRESHOLD = 0.2f
    }
}
