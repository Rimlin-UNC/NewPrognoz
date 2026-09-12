package com.meteoanalyst.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Локальное наблюдение пользователя (offline-first).
 * Уходит на сервер через SyncEngine; UUID обеспечивает идемпотентность.
 */
@Entity(tableName = "observations")
data class ObservationEntity(
    @PrimaryKey val uuid: String,
    val lat: Double,
    val lon: Double,
    val observedAt: Long,           // epoch millis (UTC)
    val tempC: Float?,
    val windMs: Float?,
    val windGustMs: Float?,
    val precipMm: Float?,
    val pressureHpa: Float?,
    val humidityPct: Float? = null,
    val visibilityM: Float? = null,
    val weatherCode: Int? = null,
    val syncStatus: Int = STATUS_PENDING,
    val rejectReason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_SYNCED = 1
        const val STATUS_REJECTED = 2
    }
}

@Dao
interface ObservationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(observation: ObservationEntity)

    @Query("SELECT * FROM observations WHERE syncStatus = 0 ORDER BY observedAt ASC LIMIT :limit")
    suspend fun pending(limit: Int): List<ObservationEntity>

    @Query("SELECT COUNT(*) FROM observations WHERE syncStatus = 0")
    fun observePendingCount(): Flow<Int>

    @Query("UPDATE observations SET syncStatus = :status, rejectReason = :reason WHERE uuid IN (:uuids)")
    suspend fun mark(uuids: List<String>, status: Int, reason: String?)

    @Query("SELECT COUNT(*) FROM observations")
    suspend fun count(): Int
}
