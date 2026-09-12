package com.meteoanalyst.app.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.meteoanalyst.app.R
import com.meteoanalyst.app.data.WeatherRepository
import com.meteoanalyst.app.data.local.AppSettings
import com.meteoanalyst.app.data.local.ObservationEntity
import com.meteoanalyst.app.data.model.EnsemblePoint
import com.meteoanalyst.app.data.model.HourlySeries
import com.meteoanalyst.app.data.model.LocationInfo
import com.meteoanalyst.app.data.model.ProviderRating
import com.meteoanalyst.app.data.model.WeatherPoint
import com.meteoanalyst.app.domain.ConfidenceCalculator
import com.meteoanalyst.app.domain.EnsembleCalculator
import com.meteoanalyst.app.domain.RatingCalculator
import com.meteoanalyst.app.location.LocationController
import com.meteoanalyst.app.ui.components.ChartSeriesUi
import com.meteoanalyst.app.ui.components.ChartUi
import com.meteoanalyst.app.ui.components.DayUi
import com.meteoanalyst.app.ui.components.HourUi
import com.meteoanalyst.app.work.WorkScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * ViewModel главного экрана (ТЗ п.4–6):
 *  • собирает прогнозы всех провайдеров;
 *  • строит ансамбль (взвешенное среднее, вес = рейтинг/100);
 *  • считает вилку температур и достоверность;
 *  • реактивно следит за рейтингами и историей сверок в Room.
 */
class WeatherViewModel(
    application: Application,
    private val repository: WeatherRepository,
    private val settings: AppSettings,
    private val locationController: LocationController,
    private val providersMeta: List<Triple<String, String, Long>>
) : AndroidViewModel(application) {

    data class UiState(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val locationName: String = "…",
        val current: EnsemblePoint? = null,
        val hourly: List<HourUi> = emptyList(),
        val daily: List<DayUi> = emptyList(),
        val weekMin: Float = 0f,
        val weekMax: Float = 0f,
        val avgRating: Float = RatingCalculator.DEFAULT_RATING,
        val providers: List<ProviderRating> = emptyList(),
        val chart: ChartUi = ChartUi(emptyList(), emptyList()),
        val lastCheckDate: String? = null,
        val showChangelogOnStart: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val colorById: Map<String, Long> = providersMeta.associate { it.first to it.third }
    private var refreshJob: Job? = null

    init {
        _state.value = _state.value.copy(
            showChangelogOnStart = !settings.changelogShown,
            profile = ProProfile.byCode(settings.profileCode),
            sync = syncUiState()
        )

        viewModelScope.launch {
            repository.seedProvidersIfEmpty()
            observeDatabase()
            observeObservationQueue()
        }
        refresh(initial = true)

        // Догоняющая сверка/bootstrap — идемпотентна (один раз в сутки).
        WorkScheduler.runVerificationNow(application)
    }

    fun refresh() = refresh(initial = false)

    fun onLocationPermissionGranted() = refresh()

    fun markChangelogShown() {
        settings.changelogShown = true
        _state.update { it.copy(showChangelogOnStart = false) }
    }

    fun markPermissionAsked() {
        settings.locationPermissionAsked = true
    }

    val isPermissionAsked: Boolean get() = settings.locationPermissionAsked

    // ------------------------------------------------ Weather Pro 2.0: профиль

    fun selectProfile(profile: ProProfile) {
        settings.profileCode = profile.code
        _state.update { it.copy(profile = profile, criticalFlags = computeFlags(it, profile)) }
    }

    private fun computeFlags(state: UiState, profile: ProProfile): List<CriticalFlag> {
        val points = state.hourly.map { it.point }
        if (points.isEmpty()) return emptyList()
        return ProfileRules.evaluateWindow(profile, points.take(12))
    }

    // ------------------------------------------- Weather Pro 2.0: наблюдения

    /** Сохраняет наблюдение в локальную очередь (offline-first). */
    fun reportObservation(
        temp: Float?, wind: Float?, gust: Float?,
        precip: Float?, pressure: Float?
    ) {
        val location = settings.location
        viewModelScope.launch {
            repository.insertObservation(
                ObservationEntity(
                    uuid = SyncEngine.newUuid(),
                    lat = location.lat,
                    lon = location.lon,
                    observedAt = System.currentTimeMillis(),
                    tempC = temp,
                    windMs = wind,
                    windGustMs = gust,
                    precipMm = precip,
                    pressureHpa = pressure
                )
            )
            _state.update { it.copy(sync = syncUiState()) }
        }
    }

    // ------------------------------------------- Weather Pro 2.0: синхронизация

    fun registerOnServer(url: String, email: String, password: String) {
        viewModelScope.launch {
            _state.update { it.copy(sync = syncUiState().copy(isBusy = true)) }
            val result = syncEngine.register(url, email, password)
            _state.update {
                it.copy(
                    sync = syncUiState().copy(
                        isBusy = false,
                        lastMessage = result.fold(
                            onSuccess = { "Подключено к $url" },
                            onFailure = { e -> "Ошибка: ${e.message}" }
                        )
                    )
                )
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _state.update { it.copy(sync = syncUiState().copy(isBusy = true)) }
            val result = syncEngine.syncNow()
            val message = when {
                !result.configured -> "Сервер не настроен"
                result.error != null -> result.error
                else -> "Синхронизировано: отправлено ${result.pushed}, " +
                    "bias ${if (result.biasSamples > 0) "%+.2f°".format(result.biasTemp) else "нет"}"
            }
            _state.update { it.copy(sync = syncUiState().copy(isBusy = false, lastMessage = message)) }
        }
    }

    private fun syncUiState(): SyncUiState = SyncUiState(
        configured = syncEngine.isConfigured,
        serverUrl = settings.serverUrl,
        pendingCount = 0,
        lastSyncAt = settings.lastSyncAt,
        biasTemp = settings.serverBiasTemp,
        biasWind = settings.serverBiasWind,
        biasSamples = settings.serverBiasSamples
    )

    private suspend fun observeObservationQueue() {
        repository.observePendingObservations().collect { count ->
            _state.update { it.copy(sync = it.sync.copy(pendingCount = count)) }
        }
    }

    private fun refresh(initial: Boolean) {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.update {
                if (initial) it.copy(isLoading = true, error = null)
                else it.copy(isRefreshing = true, error = null)
            }
            try {
                val location = locationController.resolveLocation()
                val series = repository.fetchProviderSeries(location.lat, location.lon)
                if (series.isEmpty()) {
                    throw IOException("Все провайдеры недоступны")
                }
                val ratings = repository.ratingsOnce()
                buildForecastState(location, series, ratings)

                // Снапшот прогнозов на завтра (для завтрашней сверки).
                // Не критично, если не удалось — воркер доделает.
                runCatching { repository.ensureSnapshotsForTomorrow(location) }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        error = getApplication<Application>()
                            .getString(R.string.error_no_network)
                    )
                }
            } finally {
                _state.update { it.copy(isLoading = false, isRefreshing = false) }
            }
        }
    }

    /** Реактивные рейтинги + история сверок -> график. */
    private suspend fun observeDatabase() {
        val since = LocalDate.now(WorkScheduler.MOSCOW).minusDays(8).toString()
        combine(
            repository.observeProviders(),
            repository.observeChecks(since)
        ) { providers, checks -> providers to checks }
            .collect { (providerEntities, checks) ->
                val ratings = providerEntities
                    .map {
                        ProviderRating(
                            id = it.id,
                            name = it.name,
                            color = colorById[it.id] ?: 0xFF64B5F6,
                            rating = it.rating
                        )
                    }
                    .sortedByDescending { it.rating }

                val dates = checks.map { it.date }.distinct().sorted()
                val series = providersMeta.map { meta ->
                    ChartSeriesUi(
                        providerId = meta.first,
                        name = meta.second,
                        color = meta.third,
                        scores = dates.map { date ->
                            checks
                                .firstOrNull { it.providerId == meta.first && it.date == date }
                                ?.score
                        }
                    )
                }

                val avg = ConfidenceCalculator.averageRating(ratings.map { it.rating })

                _state.update {
                    it.copy(
                        providers = ratings,
                        chart = ChartUi(dates, series),
                        lastCheckDate = checks.maxOfOrNull { c -> c.date },
                        avgRating = avg
                    )
                }
            }
    }

    private fun buildForecastState(
        location: LocationInfo,
        series: Map<String, HourlySeries>,
        ratings: Map<String, Float>
    ) {
        // Провайдеры, давшие данные; порядок стабильный из providersMeta
        val presentIds = providersMeta.map { it.first }.filter { series.containsKey(it) }
        val byTime: Map<String, Map<LocalDateTime, WeatherPoint>> = series.mapValues { (_, s) ->
            s.points.associateBy { it.time }
        }
        val anySeries = series.values.first()
        val times = anySeries.points.map { it.time }

        // «Сейчас» в локальном времени точки (API timezone=auto)
        val locationNow = Instant.now()
            .atOffset(ZoneOffset.ofTotalSeconds(anySeries.utcOffsetSeconds))
            .toLocalDateTime()
        val nowIndex = times.indexOfLast { !it.isAfter(locationNow) }
            .coerceAtLeast(0)

        val presentRatings = presentIds.map { ratings[it] ?: RatingCalculator.DEFAULT_RATING }

        // Ансамбль по часам
        val rawEnsemble: List<WeatherPoint> = times.map { t ->
            val points = presentIds.mapNotNull { id -> byTime[id]?.get(t) }
            EnsembleCalculator.combine(points, presentRatings)
        }

        // Bias-коррекция от сервера (агрегаты наблюдений сообщества)
        val biasTemp = settings.serverBiasTemp
        val biasWind = settings.serverBiasWind
        val biasActive = settings.serverBiasSamples > 0 && (biasTemp != 0f || biasWind != 0f)
        val ensemblePoints: List<WeatherPoint> = if (biasActive) {
            rawEnsemble.map {
                it.copy(
                    temperature = it.temperature - biasTemp,
                    apparentTemperature = it.apparentTemperature - biasTemp,
                    windSpeed = (it.windSpeed - biasWind).coerceAtLeast(0f)
                )
            }
        } else {
            rawEnsemble
        }

        val nowTime = times.getOrNull(nowIndex) ?: return
        val currentProviderPoints = presentIds.mapNotNull { byTime[it]?.get(nowTime) }
        val currentEnsemble = ensemblePoints.getOrNull(nowIndex) ?: return        val (tempMin, tempMax) = EnsembleCalculator.tempRange(currentProviderPoints)
        val avg = ConfidenceCalculator.averageRating(presentRatings)

        val current = EnsemblePoint(
            point = currentEnsemble,
            tempMin = tempMin,
            tempMax = tempMax,
            confidence = ConfidenceCalculator.compute(avg, 0f),
            providersAgree = EnsembleCalculator.agreement(currentProviderPoints, currentEnsemble.temperature),
            providersCount = presentIds.size
        )

        // Почасовой ряд: 24 часа вперёд
        val hourly = (nowIndex until (nowIndex + 24).coerceAtMost(ensemblePoints.size)).map { i ->
            HourUi(
                time = times[i],
                point = ensemblePoints[i],
                isNow = i == nowIndex,
                hoursAhead = Duration.between(locationNow, times[i])
                    .toMinutes()
                    .toFloat() / 60f
            )
        }

        // Суточный агрегат: сегодня + 6 дней
        val daily = mutableListOf<DayUi>()
        val dates = times.drop(nowIndex).map { it.toLocalDate() }.distinct().take(7)
        for (date in dates) {
            val dayPoints = ensemblePoints
                .withIndex()
                .filter { it.value.time.toLocalDate() == date }
                .map { it.value }
            if (dayPoints.isEmpty()) continue
            val minTemp = dayPoints.minOf { it.temperature }
            val maxTemp = dayPoints.maxOf { it.temperature }
            val midday = dayPoints.minByOrNull {
                kotlin.math.abs(Duration.between(it.time, it.time.withHour(12)).toMinutes())
            } ?: dayPoints.first()
            val hoursAhead = Duration.between(locationNow, midday.time).toMinutes().toFloat() / 60f
            daily += DayUi(
                date = date,
                minTemp = minTemp,
                maxTemp = maxTemp,
                weatherCode = midday.weatherCode,
                hoursAheadAtMidday = hoursAhead
            )
        }

        val profile = ProProfile.byCode(settings.profileCode)
        _state.update {
            it.copy(
                locationName = location.name,
                current = current,
                hourly = hourly,
                daily = daily,
                weekMin = daily.minOfOrNull { d -> d.minTemp } ?: 0f,
                weekMax = daily.maxOfOrNull { d -> d.maxTemp } ?: 0f,
                avgRating = avg,
                error = null,
                criticalFlags = ProfileRules.evaluateWindow(profile, hourly.map { h -> h.point }.take(12))
            )
        }
    }
}
