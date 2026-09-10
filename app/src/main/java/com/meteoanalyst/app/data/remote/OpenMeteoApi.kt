package com.meteoanalyst.app.data.remote

import com.meteoanalyst.app.data.remote.dto.OpenMeteoResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Открытое API Open-Meteo — без ключей (ТЗ п.1).
 *
 *  • /v1/forecast — прогноз до 7 дней (почасовой) + прошедшие часы (past_days);
 *  • /v1/archive  — исторические данные за прошедшие годы (ERA5).
 */
interface OpenMeteoApi {

    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String,
        @Query("timezone") timezone: String = "auto",
        @Query("past_days") pastDays: Int? = null,
        @Query("forecast_days") forecastDays: Int? = null
    ): OpenMeteoResponse

    /** Абсолютный URL: домен архива отличается от домена прогноза. */
    @GET("https://archive-api.open-meteo.com/v1/archive")
    suspend fun archive(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String,
        @Query("timezone") timezone: String = "auto",
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String
    ): OpenMeteoResponse

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"

        /** Переменные прогноза: более 20 метеопараметров (ТЗ п.6). */
        const val HOURLY_FORECAST_VARS: String =
            "temperature_2m,relative_humidity_2m,dew_point_2m,apparent_temperature," +
                "precipitation_probability,precipitation,rain,showers,snowfall,weather_code," +
                "pressure_msl,cloud_cover,visibility,wind_speed_10m,wind_direction_10m," +
                "wind_gusts_10m,uv_index,is_day,cape,freezing_level_height,shortwave_radiation," +
                "soil_temperature_0_to_7cm,soil_moisture_0_to_7cm"

        /** Переменные, необходимые для сверки факта (ТЗ п.2). */
        const val HOURLY_ARCHIVE_VARS: String =
            "temperature_2m,relative_humidity_2m,wind_speed_10m,precipitation,pressure_msl,weather_code"
    }
}
