package com.meteoanalyst.app

import android.app.Application
import com.meteoanalyst.app.di.ServiceLocator
import com.meteoanalyst.app.work.SyncWorker
import com.meteoanalyst.app.work.WorkScheduler

/**
 * Точка входа приложения: инициализация DI и планирование
 * ежедневной сверки в 15:00 МСК (ТЗ п.2) и фоновой синхронизации.
 */
class MeteoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        WorkScheduler.scheduleDailyVerification(this)
        SyncWorker.schedule(this)
    }
}
