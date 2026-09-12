package com.meteoanalyst.app.data.providers

import com.meteoanalyst.app.data.model.Forecast
import com.meteoanalyst.app.data.model.HistoricalData
import com.meteoanalyst.app.data.model.HourlySeries
import com.meteoanalyst.app.data.model.WeatherPoint
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * CustomProvider (ТЗ п.1): усреднённый из первых двух источников —
 * Open-Meteo и «WeatherAPI» — плюс небольшой собственный шум.
 */
class CustomProvider(
    private val first: OpenMeteoProvider,
    private val second: SimulatedProvider
) : WeatherProvider {

    override val id: String = "custom"
    override val name: String = "Custom (ансамбль 2-х)"
    override val color: Long = 0xFFFFF176

    override suspend fun getHourlyForecast(lat: Double, lon: Double): HourlySeries {
        val a = first.getHourlyForecast(lat, lon)
        val b = second.getHourlyForecast(lat, lon)
        val size = minOf(a.points.size, b.points.size)
        val points = (0 until size).map { i -> combine(a.points[i], b.points[i], a.points[i].time) }
        return HourlySeries(points, a.utcOffsetSeconds, a.timezone)
    }

    override suspend fun getForecastFor(
        lat: Double,
        lon: Double,
        targetTime: LocalDateTime
    ): Forecast {
        val a = first.getForecastFor(lat, lon, targetTime)
        val b = second.getForecastFor(lat, lon, targetTime)
        return combineForecasts(a, b, targetTime)
    }

    override suspend fun getForecast(lat: Double, lon: Double, date: String): Forecast =
        getForecastFor(
            lat, lon,
            java.time.LocalDate.parse(date).atTime(OpenMeteoProvider.VERIFICATION_HOUR, 0)
        )

    override suspend fun getHistorical(lat: Double, lon: Double, date: String): HistoricalData =
        first.getHistorical(lat, lon, date)

    override fun reconstructForecast(actual: Forecast, targetTime: LocalDateTime): Forecast {
        val a = first.reconstructForecast(actual, targetTime)
        val b = second.reconstructForecast(actual, targetTime)
        return combineForecasts(a, b, targetTime)
    }

    private fun combineForecasts(a: Forecast, b: Forecast, time: LocalDateTime): Forecast {
        val rnd = Random(seedFor(id, time))
        val noiseTemp = (rnd.nextFloat() * 2f - 1f) * CUSTOM_TEMP_ERROR
        val noiseWind = (rnd.nextFloat() * 2f - 1f) * CUSTOM_WIND_ERROR
        val noiseRain = if (rnd.nextFloat() < 0.05f) 0.15f else 0f
        return Forecast(
            temp = (a.temp + b.temp) / 2f + noiseTemp,
            windSpeed = ((a.windSpeed + b.windSpeed) / 2f + noiseWind).coerceAtLeast(0f),
            rainAmount = ((a.rainAmount + b.rainAmount) / 2f + noiseRain).coerceAtLeast(0f),
            pressure = (a.pressure + b.pressure) / 2f,
            humidity = (a.humidity + b.humidity) / 2f,
            weatherCode = if (a.rainAmount >= b.rainAmount) a.weatherCode else b.weatherCode
        )
    }

    /** Среднее двух точек; осадки-код берём у «мокрого» источника. */
    private fun combine(a: WeatherPoint, b: WeatherPoint, time: LocalDateTime): WeatherPoint {
        val rnd = Random(seedFor(id, time))
        val noiseTemp = (rnd.nextFloat() * 2f - 1f) * CUSTOM_TEMP_ERROR
        val noiseWind = (rnd.nextFloat() * 2f - 1f) * CUSTOM_WIND_ERROR
        fun avg(x: Float, y: Float) = (x + y) / 2f
        fun avgOrNull(x: Float?, y: Float?): Float? =
            if (x == null && y == null) null else avg(x ?: 0f, y ?: 0f)
        return a.copy(
            temperature = avg(a.temperature, b.temperature) + noiseTemp,
            apparentTemperature = avg(a.apparentTemperature, b.apparentTemperature) + noiseTemp,
            humidity = avg(a.humidity, b.humidity),
            dewPoint = avg(a.dewPoint, b.dewPoint),
            pressure = avg(a.pressure, b.pressure),
            cloudCover = avg(a.cloudCover, b.cloudCover),
            visibility = avgOrNull(a.visibility, b.visibility),
            uvIndex = avg(a.uvIndex, b.uvIndex),
            windSpeed = (avg(a.windSpeed, b.windSpeed) + noiseWind).coerceAtLeast(0f),
            windDirection = avg(a.windDirection, b.windDirection),
            windGusts = avg(a.windGusts, b.windGusts),
            precipitation = avg(a.precipitation, b.precipitation),
            rain = avg(a.rain, b.rain),
            showers = avg(a.showers, b.showers),
            snowfall = avg(a.snowfall, b.snowfall),
            precipitationProbability = avgOrNull(a.precipitationProbability, b.precipitationProbability),
            weatherCode = if (a.precipitation >= b.precipitation) a.weatherCode else b.weatherCode,
            cape = avgOrNull(a.cape, b.cape),
            freezingLevelHeight = avgOrNull(a.freezingLevelHeight, b.freezingLevelHeight),
            shortwaveRadiation = avgOrNull(a.shortwaveRadiation, b.shortwaveRadiation),
            soilTemperature = avgOrNull(a.soilTemperature, b.soilTemperature),
            soilMoisture = avgOrNull(a.soilMoisture, b.soilMoisture)
        )
    }

    companion object {
        private const val CUSTOM_TEMP_ERROR = 0.4f
        private const val CUSTOM_WIND_ERROR = 0.5f
    }
}
