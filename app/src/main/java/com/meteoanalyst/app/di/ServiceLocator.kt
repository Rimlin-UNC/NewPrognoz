package com.meteoanalyst.app.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meteoanalyst.app.data.WeatherRepository
import com.meteoanalyst.app.data.local.AppSettings
import com.meteoanalyst.app.data.local.AppDatabase
import com.meteoanalyst.app.data.providers.ProviderFactory
import com.meteoanalyst.app.data.providers.OpenMeteoProvider
import com.meteoanalyst.app.data.remote.OpenMeteoClient
import com.meteoanalyst.app.domain.SyncEngine
import com.meteoanalyst.app.domain.VerificationEngine
import com.meteoanalyst.app.location.LocationController
import com.meteoanalyst.app.ui.main.WeatherViewModel

/**
 * Компактный ручной DI (вместо Hilt — минимальный вес APK, ТЗ п.8).
 * Инициализируется один раз в Application.onCreate.
 */
object ServiceLocator {

    @Volatile
    private var initialized = false

    lateinit var settings: AppSettings
        private set
    lateinit var database: AppDatabase
        private set
    lateinit var client: OpenMeteoClient
        private set
    lateinit var providers: List<com.meteoanalyst.app.data.providers.WeatherProvider>
        private set
    lateinit var verificationEngine: VerificationEngine
        private set
    lateinit var repository: WeatherRepository
        private set
    lateinit var locationController: LocationController
        private set
    lateinit var syncEngine: SyncEngine
        private set

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val app = context.applicationContext
            settings = AppSettings(app)
            database = AppDatabase.build(app)
            client = OpenMeteoClient()
            providers = ProviderFactory.create(client)
            verificationEngine = VerificationEngine(
                providers = providers,
                openMeteo = providers.filterIsInstance<OpenMeteoProvider>().first(),
                settings = settings,
                providerDao = database.providerDao(),
                checkDao = database.dailyCheckDao(),
                snapshotDao = database.snapshotDao()
            )
            repository = WeatherRepository(
                providers = providers,
                database = database,
                settings = settings,
                verificationEngine = verificationEngine
            )
            locationController = LocationController(app, settings)
            syncEngine = SyncEngine(settings = settings, database = database)
            initialized = true
        }
    }

    /** Фабрика ViewModel для Compose (viewModel(factory = ...)). */
    fun weatherViewModelFactory(): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
            ServiceLocator.assertInitialized()
            WeatherViewModel(
                application = app,
                repository = ServiceLocator.repository,
                settings = ServiceLocator.settings,
                locationController = ServiceLocator.locationController,
                providersMeta = ServiceLocator.providers.map { Triple(it.id, it.name, it.color) },
                syncEngine = ServiceLocator.syncEngine
            )
        }
    }

    fun assertInitialized() {
        check(initialized) { "ServiceLocator не инициализирован (MeteoApp.onCreate)" }
    }
}
