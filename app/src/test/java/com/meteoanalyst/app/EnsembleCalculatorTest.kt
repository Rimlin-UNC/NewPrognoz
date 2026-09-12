package com.meteoanalyst.app

import com.meteoanalyst.app.data.model.WeatherPoint
import com.meteoanalyst.app.domain.EnsembleCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/**
 * Тесты ансамблевого прогноза (ТЗ п.4): итоговое значение —
 * взвешенное среднее источников с весом rating/100.
 */
class EnsembleCalculatorTest {

    private fun point(
        time: LocalDateTime = LocalDateTime.of(2026, 9, 9, 15, 0),
        temperature: Float = 20f,
        windSpeed: Float = 3f,
        precipitation: Float = 0f
    ) = WeatherPoint(
        time = time,
        temperature = temperature,
        apparentTemperature = temperature,
        humidity = 60f,
        dewPoint = 12f,
        pressure = 1013f,
        cloudCover = 40f,
        visibility = 10000f,
        uvIndex = 3f,
        windSpeed = windSpeed,
        windDirection = 180f,
        windGusts = 5f,
        precipitation = precipitation,
        rain = precipitation,
        showers = 0f,
        snowfall = 0f,
        precipitationProbability = 10f,
        weatherCode = 1,
        isDay = true,
        cape = 100f,
        freezingLevelHeight = 3000f,
        shortwaveRadiation = 400f,
        soilTemperature = 16f,
        soilMoisture = 0.25f
    )

    @Test
    fun `взвешенное среднее температуры`() {
        // (10 * 1.0 + 20 * 0.5) / 1.5 = 13.33
        val ensemble = EnsembleCalculator.combine(
            points = listOf(point(temperature = 10f), point(temperature = 20f)),
            ratings = listOf(100f, 50f)
        )
        assertEquals(13.333f, ensemble.temperature, 0.01f)
    }

    @Test
    fun `взвешенное среднее ветра и осадков`() {
        // ветер: (2 * 1.0 + 6 * 0.25) / 1.25 = 2.8
        // осадки: (0 * 1.0 + 1.0 * 0.25) / 1.25 = 0.2
        val ensemble = EnsembleCalculator.combine(
            points = listOf(point(windSpeed = 2f, precipitation = 0f), point(windSpeed = 6f, precipitation = 1f)),
            ratings = listOf(100f, 25f)
        )
        assertEquals(2.8f, ensemble.windSpeed, 0.01f)
        assertEquals(0.2f, ensemble.precipitation, 0.01f)
    }

    @Test
    fun `код погоды берётся у самого доверенного источника`() {
        val a = point().copy(weatherCode = 0)   // ясно, рейтинг 90
        val b = point().copy(weatherCode = 61)  // дождь, рейтинг 10
        val ensemble = EnsembleCalculator.combine(listOf(a, b), listOf(90f, 10f))
        assertEquals(0, ensemble.weatherCode)
    }

    @Test
    fun `нулевые рейтинги дают равномерное среднее`() {
        val ensemble = EnsembleCalculator.combine(
            listOf(point(temperature = 10f), point(temperature = 30f)),
            listOf(0f, 0f)
        )
        assertEquals(20f, ensemble.temperature, 0.01f)
    }

    @Test
    fun `отсутствующий рейтинг считается 50`() {
        // (10 + 20) / 2 = 15 при равных весах
        val ensemble = EnsembleCalculator.combine(
            listOf(point(temperature = 10f), point(temperature = 20f)),
            listOf()
        )
        assertEquals(15f, ensemble.temperature, 0.01f)
    }

    @Test
    fun `вилка температур min-max по всем источникам`() {
        val (min, max) = EnsembleCalculator.tempRange(
            listOf(point(temperature = 14.2f), point(temperature = 18.7f), point(temperature = 16f))
        )
        assertEquals(14.2f, min, 0.01f)
        assertEquals(18.7f, max, 0.01f)
    }

    @Test
    fun `согласие источников в пределах одного градуса`() {
        val points = listOf(
            point(temperature = 20f),
            point(temperature = 20.5f),
            point(temperature = 19.7f),
            point(temperature = 25f)   // выброс
        )
        assertEquals(3, EnsembleCalculator.agreement(points, 20f))
    }
}
