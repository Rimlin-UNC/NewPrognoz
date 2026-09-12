package com.meteoanalyst.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.meteoanalyst.app.data.local.AppSettings
import com.meteoanalyst.app.data.model.LocationInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Определение местоположения без Google Play Services — через системный
 * LocationManager. Без разрешения (или без данных) используется
 * местоположение по умолчанию — Москва (ТЗ: сверка в 15:00 МСК).
 */
class LocationController(
    private val context: Context,
    private val settings: AppSettings
) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Лучшее известное местоположение; вызывать с IO-диспетчера.
     * Возвращает сохранённое, если разрешение не выдано.
     */
    suspend fun resolveLocation(): LocationInfo = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext settings.location
        val location = lastKnownLocation()
        if (location != null) {
            val info = LocationInfo(
                lat = location.latitude,
                lon = location.longitude,
                name = resolveCityName(location)
            )
            settings.location = info
            info
        } else {
            settings.location
        }
    }

    private fun lastKnownLocation(): Location? = runCatching {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = manager.getProviders(true)
        var best: Location? = null
        for (provider in providers) {
            val location = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            if (location != null && (best == null || location.time > best!!.time)) {
                best = location
            }
        }
        best
    }.getOrNull()

    /** Обратное геокодирование названия города (деградирует до заглушки). */
    @Suppress("DEPRECATION") // синхронный Geocoder работает на всех версиях API
    private fun resolveCityName(location: Location): String = runCatching {
        val geocoder = Geocoder(context, Locale.forLanguageTag("ru"))
        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
        val address = addresses?.firstOrNull()
        val city = address?.locality ?: address?.subAdminArea ?: address?.adminArea
        city?.takeIf { it.isNotBlank() } ?: "Ваше местоположение"
    }.getOrNull() ?: "Ваше местоположение"
}
