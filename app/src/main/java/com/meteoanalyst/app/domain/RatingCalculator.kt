package com.meteoanalyst.app.domain

/**
 * Итоговый рейтинг провайдера (ТЗ п.3) — взвешенное скользящее среднее
 * суточных скоров за 7 дней: вес вчерашнего дня 0.30, позавчерашнего 0.20,
 * далее 0.15 / 0.12 / 0.10 / 0.08 / 0.05 (сумма = 1.00).
 *
 * Если проверок меньше семи, доступные веса перенормируются —
 * это сглаживает аномалии и не даёт одному дню перекричать историю.
 */
object RatingCalculator {

    /** Веса по дням от самого свежего к самому старому. */
    val DAY_WEIGHTS = listOf(0.30f, 0.20f, 0.15f, 0.12f, 0.10f, 0.08f, 0.05f)

    const val DEFAULT_RATING = 50f

    /**
     * @param scoresNewestFirst суточные скоры, свежий — первым (не более 7).
     */
    fun compute(scoresNewestFirst: List<Float>): Float {
        if (scoresNewestFirst.isEmpty()) return DEFAULT_RATING
        val bounded = scoresNewestFirst.take(DAY_WEIGHTS.size)
        val weights = DAY_WEIGHTS.take(bounded.size)
        val weightSum = weights.sum()
        if (weightSum <= 0f) return DEFAULT_RATING
        var acc = 0f
        for (i in bounded.indices) {
            acc += bounded[i] * weights[i]
        }
        return (acc / weightSum).coerceIn(0f, 100f)
    }
}
