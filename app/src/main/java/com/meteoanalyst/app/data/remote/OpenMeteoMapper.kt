package com.meteoanalyst.app.data.remote

import com.meteoanalyst.app.data.model.HourActual
import com.meteoanalyst.app.data.model.HistoricalData
import com.meteoanalyst.app.data.model.HourlySeries
import com.meteoanalyst.app.data.model.WeatherPoint
import com.meteoanalyst.app.data.remote.dto.OpenMeteoResponse
import java.time.LocalDateTime
import java.time.format.DateTimeParseException

/**
 * Преобразование DTO Open-Meteo в доменные модели.
 * Null-значения API безопасно превращаются в нули/пропуски.
 */
object OpenMeteoMapper {

    fun toHourlySeries(response: OpenMeteoResponse): HourlySeries {
        val hourly = response.hourly
        val times = hourly?.time.orEmpty()
        val points = mutableListOf<WeatherPoint>()
        for (i in times.indices) {
            val stamp = times[i] ?: continue
            val time = parseTime(stamp) ?: continue
            points += WeatherPoint(
                time = time,
                temperature = hourly.temperature?.getOrNull(i) ?: 0f,
                apparentTemperature = hourly.apparentTemperature?.getOrNull(i) ?: 0f,
                humidity = hourly.humidity?.getOrNull(i) ?: 0f,
                dewPoint = hourly.dewPoint?.getOrNull(i) ?: 0f,
                pressure = hourly.pressureMsl?.getOrNull(i) ?: 0f,
                cloudCover = hourly.cloudCover?.getOrNull(i) ?: 0f,
                visibility = hourly.visibility?.getOrNull(i),
                uvIndex = hourly.uvIndex?.getOrNull(i) ?: 0f,
                windSpeed = hourly.windSpeed?.getOrNull(i) ?: 0f,
                windDirection = hourly.windDirection?.getOrNull(i) ?: 0f,
                windGusts = hourly.windGusts?.getOrNull(i) ?: 0f,
                precipitation = hourly.precipitation?.getOrNull(i) ?: 0f,
                rain = hourly.rain?.getOrNull(i) ?: 0f,
                showers = hourly.showers?.getOrNull(i) ?: 0f,
                snowfall = hourly.snowfall?.getOrNull(i) ?: 0f,
                precipitationProbability = hourly.precipitationProbability?.getOrNull(i),
                weatherCode = hourly.weatherCode?.getOrNull(i) ?: 0,
                isDay = (hourly.isDay?.getOrNull(i) ?: 1) == 1,
                cape = hourly.cape?.getOrNull(i),
                freezingLevelHeight = hourly.freezingLevelHeight?.getOrNull(i),
                shortwaveRadiation = hourly.shortwaveRadiation?.getOrNull(i),
                soilTemperature = hourly.soilTemperature?.getOrNull(i),
                soilMoisture = hourly.soilMoisture?.getOrNull(i)
            )
        }
        return HourlySeries(
            points = points,
            utcOffsetSeconds = response.utcOffsetSeconds ?: 0,
            timezone = response.timezone ?: "UTC"
        )
    }

    fun toHistorical(response: OpenMeteoResponse, date: String): HistoricalData {
        val hourly = response.hourly
        val times = hourly?.time.orEmpty()
        val hours = mutableListOf<HourActual>()
        for (i in times.indices) {
            val stamp = times[i] ?: continue
            val time = parseTime(stamp) ?: continue
            hours += HourActual(
                time = time,
                temp = hourly.temperature?.getOrNull(i) ?: 0f,
                windSpeed = hourly.windSpeed?.getOrNull(i) ?: 0f,
                precipitation = hourly.precipitation?.getOrNull(i) ?: 0f,
                pressure = hourly.pressureMsl?.getOrNull(i) ?: 0f,
                humidity = hourly.humidity?.getOrNull(i) ?: 0f,
                weatherCode = hourly.weatherCode?.getOrNull(i) ?: 0
            )
        }
        return HistoricalData(
            date = date,
            timezone = response.timezone ?: "UTC",
            utcOffsetSeconds = response.utcOffsetSeconds ?: 0,
            hours = hours
        )
    }

    private fun parseTime(stamp: String): LocalDateTime? = try {
        LocalDateTime.parse(stamp)
    } catch (e: DateTimeParseException) {
        null
    }
}
