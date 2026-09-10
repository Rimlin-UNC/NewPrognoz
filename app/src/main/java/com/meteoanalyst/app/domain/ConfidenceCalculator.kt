package com.meteoanalyst.app.domain

import kotlin.math.pow

/**
 * Достоверность прогноза во времени (ТЗ п.5) — «энтропия времени»:
 *
 *   Confidence = avgRating * 0.92 ^ (hoursAhead / 24)
 *
 * Прогноз на сегодня — достоверность = среднему рейтингу провайдеров,
 * на завтра — ~92% от него, через 3 суток — ~74%.
 */
object ConfidenceCalculator {

    const val DECAY_BASE = 0.92

    fun compute(avgRating: Float, hoursAhead: Float): Float =
        (avgRating * DECAY_BASE.pow(hoursAhead / 24.0)).toFloat().coerceIn(0f, 100f)

    fun averageRating(ratings: Collection<Float>): Float =
        if (ratings.isEmpty()) RatingCalculator.DEFAULT_RATING
        else (ratings.sum() / ratings.size).coerceIn(0f, 100f)
}
