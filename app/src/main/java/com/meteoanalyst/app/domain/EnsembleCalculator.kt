package com.meteoanalyst.app.domain

import com.meteoanalyst.app.data.model.WeatherPoint

/**
 * Ансамблевый прогноз (ТЗ п.4): итоговое значение каждого параметра —
 * взвешенное среднее по всем источникам, где вес = рейтинг / 100.
 *
 *   T_ensemble = Σ(T_i * weight_i) / Σ(weight_i)
 *
 * Если все рейтинги нулевые, используется равномерное усреднение.
 */
object EnsembleCalculator {

    /** Ансамбль полного набора параметров за один час. */
    fun combine(points: List<WeatherPoint>, ratings: List<Float>): WeatherPoint {
        require(points.isNotEmpty()) { "points must not be empty" }
        if (points.size == 1) return points[0]

        val weights = normalizedWeights(ratings, points.size)

        fun avg(selector: (WeatherPoint) -> Float): Float {
            var acc = 0f
            for (i in points.indices) acc += selector(points[i]) * weights[i]
            return acc / weights.sum()
        }

        fun avgNullable(selector: (WeatherPoint) -> Float?): Float? {
            val present = points.withIndex().filter { selector(it.value) != null }
            if (present.isEmpty()) return null
            if (present.size == points.size) return avg(selector)
            // Значение есть не у всех — усредняем по имеющимся с их весами
            var acc = 0f
            var w = 0f
            for ((i, p) in present) {
                acc += selector(p)!! * weights[i]
                w += weights[i]
            }
            return if (w > 0f) acc / w else null
        }

        // Код погоды и день/ночь берём у самого «доверенного» источника
        val topIndex = weights.indices.maxByOrNull { weights[it] } ?: 0

        return points[0].copy(
            temperature = avg { it.temperature },
            apparentTemperature = avg { it.apparentTemperature },
            humidity = avg { it.humidity },
            dewPoint = avg { it.dewPoint },
            pressure = avg { it.pressure },
            cloudCover = avg { it.cloudCover },
            visibility = avgNullable { it.visibility },
            uvIndex = avg { it.uvIndex },
            windSpeed = avg { it.windSpeed },
            windDirection = avg { it.windDirection },
            windGusts = avg { it.windGusts },
            precipitation = avg { it.precipitation },
            rain = avg { it.rain },
            showers = avg { it.showers },
            snowfall = avg { it.snowfall },
            precipitationProbability = avgNullable { it.precipitationProbability },
            weatherCode = points[topIndex].weatherCode,
            isDay = points[topIndex].isDay,
            cape = avgNullable { it.cape },
            freezingLevelHeight = avgNullable { it.freezingLevelHeight },
            shortwaveRadiation = avgNullable { it.shortwaveRadiation },
            soilTemperature = avgNullable { it.soilTemperature },
            soilMoisture = avgNullable { it.soilMoisture }
        )
    }

    /** Вилка температур: min/max среди источников. */
    fun tempRange(points: List<WeatherPoint>): Pair<Float, Float> {
        if (points.isEmpty()) return 0f to 0f
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (p in points) {
            if (p.temperature < min) min = p.temperature
            if (p.temperature > max) max = p.temperature
        }
        return min to max
    }

    /** Число источников, попадающих в ±1 °C от ансамбля. */
    fun agreement(points: List<WeatherPoint>, ensembleTemp: Float): Int =
        points.count { kotlin.math.abs(it.temperature - ensembleTemp) <= 1f }

    /** Нормировка весов; при нулевой сумме — равномерные веса. */
    internal fun normalizedWeights(ratings: List<Float>, count: Int): List<Float> {
        val raw = List(count) { i ->
            (ratings.getOrNull(i) ?: RatingCalculator.DEFAULT_RATING) / 100f
        }
        val sum = raw.sum()
        return if (sum > 0f) raw else List(count) { 1f / count }
    }
}
