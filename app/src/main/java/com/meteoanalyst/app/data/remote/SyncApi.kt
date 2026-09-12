package com.meteoanalyst.app.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * API синхронизации Weather Pro 2.0 (см. docs/API.md).
 * Base URL — адрес сервера пользователя (ymaster.ru).
 */
interface SyncApi {

    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @POST("api/v1/sync/push")
    suspend fun push(
        @Header("X-Api-Key") apiKey: String,
        @Body body: PushRequest
    ): PushResponse

    @GET("api/v1/sync/pull")
    suspend fun pull(
        @Header("X-Api-Key") apiKey: String,
        @Query("since") since: String?
    ): PullResponse

    @GET("api/v1/health")
    suspend fun health(): HealthResponse
}

// --------------------------------------------------------------------- DTO

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String,
    @Json(name = "device_name") val deviceName: String,
    @Json(name = "platform") val platform: String = "android",
    @Json(name = "app_version") val appVersion: String = ""
)

@JsonClass(generateAdapter = true)
data class RegisterResponse(
    @Json(name = "user_uuid") val userUuid: String? = null,
    @Json(name = "device_uuid") val deviceUuid: String? = null,
    @Json(name = "api_key") val apiKey: String? = null
)

@JsonClass(generateAdapter = true)
data class PushRequest(
    @Json(name = "observations") val observations: List<ObservationDto>
)

@JsonClass(generateAdapter = true)
data class ObservationDto(
    @Json(name = "uuid") val uuid: String,
    @Json(name = "lat") val lat: Double,
    @Json(name = "lon") val lon: Double,
    @Json(name = "observed_at") val observedAt: String,
    @Json(name = "temp_c") val tempC: Float? = null,
    @Json(name = "wind_ms") val windMs: Float? = null,
    @Json(name = "wind_gust_ms") val windGustMs: Float? = null,
    @Json(name = "precip_mm") val precipMm: Float? = null,
    @Json(name = "pressure_hpa") val pressureHpa: Float? = null,
    @Json(name = "humidity_pct") val humidityPct: Float? = null,
    @Json(name = "visibility_m") val visibilityM: Float? = null,
    @Json(name = "weather_code") val weatherCode: Int? = null
)

@JsonClass(generateAdapter = true)
data class PushResponse(
    @Json(name = "accepted") val accepted: Int = 0,
    @Json(name = "duplicate") val duplicate: Int = 0,
    @Json(name = "rejected") val rejected: Int = 0,
    @Json(name = "results") val results: List<PushResultDto>? = null
)

@JsonClass(generateAdapter = true)
data class PushResultDto(
    @Json(name = "uuid") val uuid: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "reason") val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class PullResponse(
    @Json(name = "cursor") val cursor: String? = null,
    @Json(name = "server_time") val serverTime: String? = null,
    @Json(name = "provider_bias") val providerBias: BiasDto? = null
)

@JsonClass(generateAdapter = true)
data class BiasDto(
    @Json(name = "temp_c") val tempC: Float? = null,
    @Json(name = "wind_ms") val windMs: Float? = null,
    @Json(name = "sample_size") val sampleSize: Int = 0
)

@JsonClass(generateAdapter = true)
data class HealthResponse(
    @Json(name = "status") val status: String? = null,
    @Json(name = "version") val version: String? = null
)
