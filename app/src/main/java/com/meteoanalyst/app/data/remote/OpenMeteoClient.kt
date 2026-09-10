package com.meteoanalyst.app.data.remote

import com.meteoanalyst.app.data.remote.dto.OpenMeteoResponse
import com.squareup.moshi.Moshi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Единственный реальный сетевой клиент приложения.
 *
 * Важно: все шесть провайдеров строятся поверх одних и тех же данных
 * Open-Meteo, поэтому ответы кэшируются в памяти (TTL 10 минут) —
 * шесть «источников» обходятся одним HTTP-запросом. Сетевой вызов
 * выполняется под мьютексом: параллельные запросы дедуплицируются.
 */
class OpenMeteoClient(
    private val cacheTtlMillis: Long = 10 * 60_000L,
    private val maxCacheEntries: Int = 4
) {
    private val moshi: Moshi = Moshi.Builder().build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(OpenMeteoApi.BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: OpenMeteoApi = retrofit.create(OpenMeteoApi::class.java)

    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, CachedForecast>()

    private data class CachedForecast(
        val savedAtMillis: Long,
        val response: OpenMeteoResponse
    )

    /** Прогноз (кэш по координатам и окну дней). */
    suspend fun forecast(
        lat: Double,
        lon: Double,
        pastDays: Int = 1,
        forecastDays: Int = 7,
        forceRefresh: Boolean = false
    ): OpenMeteoResponse = mutex.withLock {
        val key = "$lat:$lon:$pastDays:$forecastDays"
        val cached = cache[key]
        if (!forceRefresh && cached != null &&
            System.currentTimeMillis() - cached.savedAtMillis < cacheTtlMillis
        ) {
            return cached.response
        }
        val response = api.forecast(
            latitude = lat,
            longitude = lon,
            hourly = OpenMeteoApi.HOURLY_FORECAST_VARS,
            timezone = "auto",
            pastDays = pastDays,
            forecastDays = forecastDays
        )
        put(key, response)
        return response
    }

    /** Архив (без кэша — запросы редкие, только при сверке старых дат). */
    suspend fun archive(lat: Double, lon: Double, date: String): OpenMeteoResponse =
        api.archive(
            latitude = lat,
            longitude = lon,
            hourly = OpenMeteoApi.HOURLY_ARCHIVE_VARS,
            timezone = "auto",
            startDate = date,
            endDate = date
        )

    private fun put(key: String, response: OpenMeteoResponse) {
        cache[key] = CachedForecast(System.currentTimeMillis(), response)
        while (cache.size > maxCacheEntries) {
            val eldest = cache.keys.firstOrNull() ?: break
            cache.remove(eldest)
        }
    }
}
