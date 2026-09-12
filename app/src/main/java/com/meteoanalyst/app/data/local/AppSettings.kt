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

    // ------------------------------------------------- Weather Pro 2.0

    /** Адрес сервера синхронизации (например, https://ymaster.ru). */
    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    /** API-ключ устройства (выдаётся при регистрации на сервере). */
    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    /** Курсор инкрементального pull (null — с начала). */
    var syncCursor: String?
        get() = prefs.getString(KEY_SYNC_CURSOR, null)
        set(value) = prefs.edit().putString(KEY_SYNC_CURSOR, value).apply()

    /** Код профессионального профиля (см. ProProfile). */
    var profileCode: String
        get() = prefs.getString(KEY_PROFILE_CODE, ProProfile.UNIVERSAL.code)
            ?: ProProfile.UNIVERSAL.code
        set(value) = prefs.edit().putString(KEY_PROFILE_CODE, value).apply()

    /** Bias-коррекция температуры от сообщества, °C. */
    var serverBiasTemp: Float
        get() = prefs.getFloat(KEY_BIAS_TEMP, 0f)
        set(value) = prefs.edit().putFloat(KEY_BIAS_TEMP, value).apply()

    /** Bias-коррекция ветра от сообщества, м/с. */
    var serverBiasWind: Float
        get() = prefs.getFloat(KEY_BIAS_WIND, 0f)
        set(value) = prefs.edit().putFloat(KEY_BIAS_WIND, value).apply()

    /** Число пар сверка-факт, на которых построен bias (0 — коррекции нет). */
    var serverBiasSamples: Int
        get() = prefs.getInt(KEY_BIAS_SAMPLES, 0)
        set(value) = prefs.edit().putInt(KEY_BIAS_SAMPLES, value).apply()

    /** Время последней успешной синхронизации (epoch millis). */
    var lastSyncAt: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

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

        // Weather Pro 2.0
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_SYNC_CURSOR = "sync_cursor"
        private const val KEY_PROFILE_CODE = "profile_code"
        private const val KEY_BIAS_TEMP = "server_bias_temp"
        private const val KEY_BIAS_WIND = "server_bias_wind"
        private const val KEY_BIAS_SAMPLES = "server_bias_samples"
        private const val KEY_LAST_SYNC = "last_sync_at"
    }
}
