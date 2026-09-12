package com.meteoanalyst.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Текущий рейтинг провайдера (0..100). Обновляется ежедневно
 * экспоненциальным сглаживанием за 7 дней (ТЗ п.3).
 */
@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,          // "openmeteo", "weatherapi", ...
    val name: String,
    val rating: Float,                   // 0..100
    val lastUpdated: Long
)

/**
 * Ежедневная сверка прогноза с фактом (ТЗ п.2–3).
 * Уникальность (providerId, date) не даёт задвоить проверку.
 */
@Entity(
    tableName = "daily_checks",
    indices = [Index(value = ["providerId", "date"], unique = true)]
)
data class DailyCheckEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val providerId: String,
    val date: String,                    // "2026-09-10"
    val tempForecast: Float,
    val tempActual: Float,
    val windForecast: Float,
    val windActual: Float,
    val rainForecast: Float,
    val rainActual: Float,
    val score: Float                     // суточный скор 0..100
)

/**
 * Снапшот прогноза провайдера на завтрашние 15:00 — фиксируется
 * сразу после сверки, чтобы ровно через 24 часа сравнить с фактом.
 */
@Entity(
    tableName = "forecast_snapshots",
    indices = [Index(value = ["providerId", "targetDate"], unique = true)]
)
data class ForecastSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val providerId: String,
    val targetDate: String,              // "2026-09-10"
    val targetTime: String,              // "2026-09-10T15:00"
    val createdAt: Long,
    val temp: Float,
    val windSpeed: Float,
    val rainAmount: Float
)
