package com.meteoanalyst.app.data.providers

import com.meteoanalyst.app.data.model.Forecast
import com.meteoanalyst.app.data.model.HistoricalData
import com.meteoanalyst.app.data.model.HourlySeries

/**
 * Интерфейс погодного провайдера (ТЗ п.1).
 *
 * Все шесть источников приложения реализуют его: реальный Open-Meteo
 * и пять симулируемых. Для перехода на боевые API достаточно
 * заменить реализацию — архитектура останется прежней.
 */
interface WeatherProvider {

    /** Стабильный идентификатор ("openmeteo", "weatherapi", ...). */
    val id: String

    /** Отображаемое имя. */
    val name: String

    /** Цвет линии на графике рейтингов (ARGB). */
    val color: Long

    /** Почасовой прогноз на 7 дней (время — локальное для точки). */
    suspend fun getHourlyForecast(lat: Double, lon: Double): HourlySeries

    /** Прогноз на конкретный момент времени. */
    suspend fun getForecastFor(lat: Double, lon: Double, targetTime: java.time.LocalDateTime): Forecast

    /** Прогноз на 15:00 указанной даты (формат "yyyy-MM-dd") — ТЗ-сигнатура. */
    suspend fun getForecast(lat: Double, lon: Double, date: String): Forecast

    /** Исторические (фактические) данные за дату — Open-Meteo History/Archive. */
    suspend fun getHistorical(lat: Double, lon: Double, date: String): HistoricalData

    /**
     * Реконструкция прогноза, «сделанного 24 часа назад».
     *
     * Используется только пока нет реального снапшота (первые сутки
     * после установки): факт искажается детерминированной погрешностью
     * провайдера. Со второго дня сравниваются реальные снапшоты.
     */
    fun reconstructForecast(actual: Forecast, targetTime: java.time.LocalDateTime): Forecast
}
