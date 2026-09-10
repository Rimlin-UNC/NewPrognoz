package com.meteoanalyst.app.domain

import kotlin.math.abs

/**
 * Суточный скор провайдера (ТЗ п.3):
 *
 *   Score = 100 - ( |T_prog - T_fact| * 8
 *                  + |Wind_prog - Wind_fact| * 4
 *                  + (Rain_mismatch ? 25 : 0) )
 *
 * Rain_mismatch — прогноз обещал осадки (>0.2 мм), а факта не было,
 * или наоборот. Результат зажимается в диапазон 0..100.
 */
object ScoreCalculator {

    const val TEMP_WEIGHT = 8f
    const val WIND_WEIGHT = 4f
    const val RAIN_MISMATCH_PENALTY = 25f
    const val RAIN_THRESHOLD = 0.2f

    fun score(
        tempForecast: Float,
        tempActual: Float,
        windForecast: Float,
        windActual: Float,
        rainForecast: Float,
        rainActual: Float
    ): Float {
        val tempPenalty = abs(tempForecast - tempActual) * TEMP_WEIGHT
        val windPenalty = abs(windForecast - windActual) * WIND_WEIGHT
        val rainPenalty = if (isRainMismatch(rainForecast, rainActual)) RAIN_MISMATCH_PENALTY else 0f
        return (100f - tempPenalty - windPenalty - rainPenalty).coerceIn(0f, 100f)
    }

    fun isRainMismatch(rainForecast: Float, rainActual: Float): Boolean {
        val forecastRainy = rainForecast > RAIN_THRESHOLD
        val actualRainy = rainActual > RAIN_THRESHOLD
        return forecastRainy != actualRainy
    }
}
