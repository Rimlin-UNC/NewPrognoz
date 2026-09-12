package com.meteoanalyst.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * База данных Room: рейтинги провайдеров, ежедневные сверки,
 * снапшоты прогнозов на завтра (ТЗ п.7).
 */
@Database(
    entities = [
        ProviderEntity::class,
        DailyCheckEntity::class,
        ForecastSnapshotEntity::class,
        ObservationEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun providerDao(): ProviderDao
    abstract fun dailyCheckDao(): DailyCheckDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun observationDao(): ObservationDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "meteo_analyst.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
