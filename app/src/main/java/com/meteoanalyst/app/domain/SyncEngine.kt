package com.meteoanalyst.app.domain

import com.meteoanalyst.app.data.local.AppSettings
import com.meteoanalyst.app.data.local.AppDatabase
import com.meteoanalyst.app.data.local.ObservationEntity
import com.meteoanalyst.app.data.remote.BiasDto
import com.meteoanalyst.app.data.remote.ObservationDto
import com.meteoanalyst.app.data.remote.PullResponse
import com.meteoanalyst.app.data.remote.PushRequest
import com.meteoanalyst.app.data.remote.PushResponse
import com.meteoanalyst.app.data.remote.RegisterRequest
import com.meteoanalyst.app.data.remote.SyncApi
import com.squareup.moshi.Moshi
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.time.Instant
import java.util.UUID

/**
 * Движок синхронизации offline-first (docs/API.md — «Алгоритм синхронизации»):
 *
 *  PUSH: очередь pending-наблюдений → POST /sync/push (батч ≤200);
 *        accepted/duplicate → synced, rejected → помечается с причиной.
 *  PULL: GET /sync/pull?since=курсор → bias-коррекция ансамбля + курсор.
 *
 * Идемпотентность по UUID; сетевые ошибки оставляют записи в очереди,
 * WorkManager повторяет с нарастающим интервалом.
 */
class SyncEngine(
    private val settings: AppSettings,
    private val database: AppDatabase
) {

    data class SyncResult(
        val configured: Boolean,
        val pushed: Int = 0,
        val duplicates: Int = 0,
        val rejected: Int = 0,
        val pulled: Boolean = false,
        val biasTemp: Float = 0f,
        val biasWind: Float = 0f,
        val biasSamples: Int = 0,
        val error: String? = null
    )

    private fun api(): SyncApi? {
        val url = settings.serverUrl.trim()
        if (url.isBlank()) return null
        val base = if (url.endsWith("/")) url else "$url/"
        return Retrofit.Builder()
            .baseUrl(base)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
            .create(SyncApi::class.java)
    }

    val isConfigured: Boolean
        get() = settings.serverUrl.isNotBlank() && settings.apiKey.isNotBlank()

    /** Регистрация пользователя и устройства; api_key сохраняется в настройках. */
    suspend fun register(serverUrl: String, email: String, password: String): Result<Unit> = runCatching {
        require(serverUrl.isNotBlank()) { "Укажите адрес сервера" }
        require(email.contains("@")) { "Некорректный email" }
        require(password.length >= 8) { "Пароль должен быть не короче 8 символов" }
        val base = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val retrofit = Retrofit.Builder()
            .baseUrl(base)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .build()
        val response = retrofit.create(SyncApi::class.java)
            .register(RegisterRequest(email, password, deviceName()))
        val key = response.apiKey
            ?: throw IllegalStateException("Сервер не вернул api_key")
        settings.serverUrl = serverUrl.trim()
        settings.apiKey = key
        settings.syncCursor = null
    }

    private fun deviceName(): String =
        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".take(100)

    /** Полный цикл: push → pull. Безопасен при отсутствии конфигурации. */
    suspend fun syncNow(): SyncResult {
        val api = api() ?: return SyncResult(configured = false)
        val key = settings.apiKey
        if (key.isBlank()) return SyncResult(configured = false)

        var pushed = 0
        var duplicates = 0
        var rejected = 0
        try {
            val pending = database.observationDao().pending(BATCH_LIMIT)
            if (pending.isNotEmpty()) {
                val response = api.push(key, PushRequest(pending.map { it.toDto() }))
                val byUuid = (response.results ?: emptyList())
                    .filterNotNull()
                    .associate { (it.uuid ?: "") to it }
                val done = mutableListOf<String>()
                val failed = mutableListOf<String>()
                for (obs in pending) {
                    when (byUuid[obs.uuid]?.status) {
                        "accepted", "duplicate" -> done += obs.uuid
                        "rejected" -> {
                            failed += obs.uuid
                            rejected++
                        }
                        else -> done += obs.uuid // сервер подтвердил батч целиком
                    }
                }
                if (done.isNotEmpty()) {
                    database.observationDao().mark(done, ObservationEntity.STATUS_SYNCED, null)
                    pushed = done.size
                }
                if (failed.isNotEmpty()) {
                    val reason = byUuid.values.firstOrNull { it.status == "rejected" }?.reason
                    database.observationDao().mark(failed, ObservationEntity.STATUS_REJECTED, reason)
                }
                duplicates = response.duplicate
            }
        } catch (t: Throwable) {
            return SyncResult(configured = true, error = "Не удалось отправить: ${t.message}")
        }

        // PULL: bias + курсор
        return try {
            val pull = api.pull(key, settings.syncCursor)
            applyPull(pull)
            SyncResult(
                configured = true, pushed = pushed, duplicates = duplicates, rejected = rejected,
                pulled = true,
                biasTemp = settings.serverBiasTemp,
                biasWind = settings.serverBiasWind,
                biasSamples = settings.serverBiasSamples
            )
        } catch (t: Throwable) {
            SyncResult(
                configured = true, pushed = pushed, duplicates = duplicates, rejected = rejected,
                error = "Отправлено $pushed, но pull не удался: ${t.message}"
            )
        }
    }

    private fun applyPull(pull: PullResponse) {
        pull.cursor?.let { settings.syncCursor = it }
        val bias: BiasDto = pull.providerBias ?: return
        if (bias.sampleSize > 0) {
            settings.serverBiasTemp = bias.tempC ?: 0f
            settings.serverBiasWind = bias.windMs ?: 0f
            settings.serverBiasSamples = bias.sampleSize
        }
        settings.lastSyncAt = System.currentTimeMillis()
    }

    companion object {
        const val BATCH_LIMIT = 200

        fun newUuid(): String = UUID.randomUUID().toString()
    }
}

private fun ObservationEntity.toDto() = ObservationDto(
    uuid = uuid,
    lat = lat,
    lon = lon,
    observedAt = Instant.ofEpochMilli(observedAt).toString(),
    tempC = tempC,
    windMs = windMs,
    windGustMs = windGustMs,
    precipMm = precipMm,
    pressureHpa = pressureHpa,
    humidityPct = humidityPct,
    visibilityM = visibilityM,
    weatherCode = weatherCode
)
