package com.meteoanalyst.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.meteoanalyst.app.data.model.LocationInfo
import com.meteoanalyst.app.domain.ProProfile

/**
 * Локальные настройки (SharedPreferences): выбранная локация,
 * флаги первого запуска и даты последней сверки.
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("meteo_settings", Context.MODE_PRIVATE)

    var location: LocationInfo
        get() = LocationInfo(
            lat = prefs.getString(KEY_LAT, null)?.toDoubleOrNull() ?: DEFAULT_LAT,
            lon = prefs.getString(KEY_LON, null)?.toDoubleOrNull() ?: DEFAULT_LON,
            name = prefs.getString(KEY_LOCATION_NAME, null) ?: DEFAULT_NAME
        )
        set(value) {
            prefs.edit()
                .putString(KEY_LAT, value.lat.toString())
                .putString(KEY_LON, value.lon.toString())
                .putString(KEY_LOCATION_NAME, value.name)
                .apply()
        }

    /** Дата последней успешной сверки (yyyy-MM-dd) в 15:00 МСК. */
    var lastVerificationDate: String?
        get() = prefs.getString(KEY_LAST_VERIFICATION, null)
        set(value) = prefs.edit().putString(KEY_LAST_VERIFICATION, value).apply()

    /** Changelog показан при первом запуске (ТЗ п.9). */
    var changelogShown: Boolean
        get() = prefs.getBoolean(KEY_CHANGELOG_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_CHANGELOG_SHOWN, value).apply()

    /** Разрешение на геолокацию уже запрашивалось. */
    var locationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERMISSION_ASKED, value).apply()

    companion object {
        const val DEFAULT_LAT = 55.7558
        const val DEFAULT_LON = 37.6173
        const val DEFAULT_NAME = "Москва"

        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val KEY_LOCATION_NAME = "location_name"
        private const val KEY_LAST_VERIFICATION = "last_verification_date"
        private const val KEY_CHANGELOG_SHOWN = "changelog_shown"
        private const val KEY_PERMISSION_ASKED = "location_permission_asked"
    }
}
