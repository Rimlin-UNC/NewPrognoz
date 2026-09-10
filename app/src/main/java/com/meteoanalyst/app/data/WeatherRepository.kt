package com.meteoanalyst.app.data

import com.meteoanalyst.app.data.local.AppSettings
import com.meteoanalyst.app.data.local.AppDatabase
import com.meteoanalyst.app.data.local.DailyCheckEntity
import com.meteoanalyst.app.data.local.ProviderEntity
import com.meteoanalyst.app.data.model.HourlySeries
import com.meteoanalyst.app.data.model.LocationInfo
import com.meteoanalyst.app.data.providers.WeatherProvider
import com.meteoanalyst.app.domain.VerificationEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий: сбор прогнозов всех провайдеров и доступ к рейтингам.
 */
class WeatherRepository(
    private val providers: List<WeatherProvider>,
    private val database: AppDatabase,
    private val settings: AppSettings,
    private val verificationEngine: VerificationEngine
) {

    /**
     * Прогнозы всех провайдеров параллельно. Симулируемые источники
     * переиспользуют кэш Open-Meteo, поэтому фактически выполняется
     * один сетевой запрос. Неудавшийся источник пропускается.
     */
    suspend fun fetchProviderSeries(lat: Double, lon: Double): Map<String, HourlySeries> =
        coroutineScope {
            providers.map { provider ->
                async {
                    runCatching { provider.getHourlyForecast(lat, lon) }
                        .getOrNull()
                        ?.let { series -> provider.id to series }
                }
            }.awaitAll().filterNotNull().toMap()
        }

    fun observeProviders(): Flow<List<ProviderEntity>> =
        database.providerDao().observeAll()

    fun observeChecks(sinceDate: String): Flow<List<DailyCheckEntity>> =
        database.dailyCheckDao().observeSince(sinceDate)

    suspend fun ratingsOnce(): Map<String, Float> =
        database.providerDao().getAll().associate { it.id to it.rating }

    suspend fun lastCheckDate(): String? = database.dailyCheckDao().lastCheckDate()

    suspend fun seedProvidersIfEmpty() = verificationEngine.seedProvidersIfEmpty()

    suspend fun ensureSnapshotsForTomorrow(location: LocationInfo) =
        verificationEngine.ensureSnapshotsForTomorrow(location)

    val providerCount: Int get() = providers.size

    val providersMeta: List<Triple<String, String, Long>>
        get() = providers.map { Triple(it.id, it.name, it.color) }

    /** Настройки нужны движку сверки — точка входа для Worker'а. */
    val appSettings: AppSettings get() = settings
}
