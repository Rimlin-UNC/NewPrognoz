package com.meteoanalyst.app.data.model

import java.time.LocalDateTime

/**
 * Точечный прогноз одного провайдера (используется при сверке и снапшотах).
 * Соответствует интерфейсу WeatherProvider из ТЗ.
 */
data class Forecast(
    val temp: Float,
    val windSpeed: Float,
    val rainAmount: Float,
    val pressure: Float = 0f,
    val humidity: Float = 0f,
    val weatherCode: Int = 0
)

/**
 * Фактические (исторические) данные за сутки: Open-Meteo History/Archive.
 */
data class HistoricalData(
    val date: String,
    val timezone: String,
    val utcOffsetSeconds: Int,
    val hours: List<HourActual>
)

data class HourActual(
    val time: LocalDateTime,
    val temp: Float,
    val windSpeed: Float,
    val precipitation: Float,
    val pressure: Float,
    val humidity: Float,
    val weatherCode: Int
)

/**
 * Полный набор метеопараметров за один час (более 20 параметров, ТЗ п.6).
 * Все значения приведены к единицам: °C, м/с, мм, гПа, %, метры.
 */
data class WeatherPoint(
    val time: LocalDateTime,
    val temperature: Float,
    val apparentTemperature: Float,
    val humidity: Float,
    val dewPoint: Float,
    val pressure: Float,
    val cloudCover: Float,
    val visibility: Float?,
    val uvIndex: Float,
    val windSpeed: Float,
    val windDirection: Float,
    val windGusts: Float,
    val precipitation: Float,
    val rain: Float,
    val showers: Float,
    val snowfall: Float,
    val precipitationProbability: Float?,
    val weatherCode: Int,
    val isDay: Boolean,
    val cape: Float?,
    val freezingLevelHeight: Float?,
    val shortwaveRadiation: Float?,
    val soilTemperature: Float?,
    val soilMoisture: Float?
)

/**
 * Результат getHourlyForecast: точки + смещение таймзоны локации.
 */
data class HourlySeries(
    val points: List<WeatherPoint>,
    val utcOffsetSeconds: Int,
    val timezone: String
)

data class LocationInfo(
    val lat: Double,
    val lon: Double,
    val name: String
)

/** Рейтинг провайдера для UI. */
data class ProviderRating(
    val id: String,
    val name: String,
    val color: Long,
    val rating: Float
)

/** Ансамблевая точка + метаданные разброса источников. */
data class EnsemblePoint(
    val point: WeatherPoint,
    val tempMin: Float,
    val tempMax: Float,
    val confidence: Float,
    val providersAgree: Int,
    val providersCount: Int
)

/** Агрегированный прогноз на сутки. */
data class DayForecast(
    val date: LocalDateTime,
    val minTemp: Float,
    val maxTemp: Float,
    val middayWeatherCode: Int,
    val maxPrecipitation: Float,
    val confidence: Float
)

fun WeatherPoint.toForecast(): Forecast = Forecast(
    temp = temperature,
    windSpeed = windSpeed,
    rainAmount = precipitation,
    pressure = pressure,
    humidity = humidity,
    weatherCode = weatherCode
)
