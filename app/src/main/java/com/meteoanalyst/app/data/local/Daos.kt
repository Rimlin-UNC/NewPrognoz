package com.meteoanalyst.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {

    @Query("SELECT * FROM providers ORDER BY rating DESC")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers")
    suspend fun getAll(): List<ProviderEntity>

    @Upsert
    suspend fun upsertAll(providers: List<ProviderEntity>)

    @Query("UPDATE providers SET rating = :rating, lastUpdated = :lastUpdated WHERE id = :id")
    suspend fun updateRating(id: String, rating: Float, lastUpdated: Long)
}

@Dao
interface DailyCheckDao {

    @Query("SELECT * FROM daily_checks WHERE date >= :sinceDate ORDER BY date ASC")
    fun observeSince(sinceDate: String): Flow<List<DailyCheckEntity>>

    @Query("SELECT * FROM daily_checks WHERE date >= :sinceDate ORDER BY date DESC")
    suspend fun since(sinceDate: String): List<DailyCheckEntity>

    @Query(
        "SELECT * FROM daily_checks WHERE providerId = :providerId " +
            "AND date >= :sinceDate ORDER BY date DESC LIMIT :limit"
    )
    suspend fun latestFor(providerId: String, sinceDate: String, limit: Int): List<DailyCheckEntity>

    @Query("SELECT * FROM daily_checks WHERE providerId = :providerId AND date = :date LIMIT 1")
    suspend fun find(providerId: String, date: String): DailyCheckEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(checks: List<DailyCheckEntity>)

    @Query("SELECT COUNT(*) FROM daily_checks")
    suspend fun count(): Int

    @Query("SELECT MAX(date) FROM daily_checks")
    suspend fun lastCheckDate(): String?
}

@Dao
interface SnapshotDao {

    @Query("SELECT * FROM forecast_snapshots WHERE providerId = :providerId AND targetDate = :targetDate LIMIT 1")
    suspend fun find(providerId: String, targetDate: String): ForecastSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(snapshot: ForecastSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(snapshots: List<ForecastSnapshotEntity>)

    @Query("DELETE FROM forecast_snapshots WHERE targetDate < :date")
    suspend fun deleteOlderThan(date: String)
}
