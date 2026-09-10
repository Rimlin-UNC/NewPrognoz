package com.meteoanalyst.app.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTO ответа Open-Meteo (эндпоинты /v1/forecast и /v1/archive).
 * Все поля nullable: архив возвращает подмножество переменных,
 * а почасовые значения могут содержать null.
 */
@JsonClass(generateAdapter = true)
data class OpenMeteoResponse(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String? = null,
    @Json(name = "utc_offset_seconds") val utcOffsetSeconds: Int? = null,
    @Json(name = "elevation") val elevation: Double? = null,
    val hourly: HourlyDto? = null
)

@JsonClass(generateAdapter = true)
data class HourlyDto(
    val time: List<String?>? = null,

    @Json(name = "temperature_2m") val temperature: List<Float?>? = null,
    @Json(name = "relative_humidity_2m") val humidity: List<Float?>? = null,
    @Json(name = "dew_point_2m") val dewPoint: List<Float?>? = null,
    @Json(name = "apparent_temperature") val apparentTemperature: List<Float?>? = null,
    @Json(name = "precipitation_probability") val precipitationProbability: List<Float?>? = null,
    val precipitation: List<Float?>? = null,
    val rain: List<Float?>? = null,
    val showers: List<Float?>? = null,
    val snowfall: List<Float?>? = null,
    @Json(name = "weather_code") val weatherCode: List<Int?>? = null,
    @Json(name = "pressure_msl") val pressureMsl: List<Float?>? = null,
    @Json(name = "cloud_cover") val cloudCover: List<Float?>? = null,
    val visibility: List<Float?>? = null,
    @Json(name = "wind_speed_10m") val windSpeed: List<Float?>? = null,
    @Json(name = "wind_direction_10m") val windDirection: List<Float?>? = null,
    @Json(name = "wind_gusts_10m") val windGusts: List<Float?>? = null,
    @Json(name = "uv_index") val uvIndex: List<Float?>? = null,
    @Json(name = "is_day") val isDay: List<Int?>? = null,
    val cape: List<Float?>? = null,
    @Json(name = "freezing_level_height") val freezingLevelHeight: List<Float?>? = null,
    @Json(name = "shortwave_radiation") val shortwaveRadiation: List<Float?>? = null,
    @Json(name = "soil_temperature_0_to_7cm") val soilTemperature: List<Float?>? = null,
    @Json(name = "soil_moisture_0_to_7cm") val soilMoisture: List<Float?>? = null
)
