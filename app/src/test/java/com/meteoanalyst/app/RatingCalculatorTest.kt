package com.meteoanalyst.app

import com.meteoanalyst.app.domain.RatingCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Тесты рейтинга: экспоненциальное сглаживание за 7 дней
 * с весами 0.30 / 0.20 / 0.15 / 0.12 / 0.10 / 0.08 / 0.05 (ТЗ п.3).
 */
class RatingCalculatorTest {

    @Test
    fun `нет проверок - рейтинг по умолчанию 50`() {
        assertEquals(50f, RatingCalculator.compute(emptyList()), 0.001f)
    }

    @Test
    fun `одна проверка равна самой себе`() {
        assertEquals(80f, RatingCalculator.compute(listOf(80f)), 0.001f)
    }

    @Test
    fun `свежий день весит больше вчерашнего`() {
        // (100 * 0.30 + 0 * 0.20) / 0.50 = 60
        val rating = RatingCalculator.compute(listOf(100f, 0f))
        assertEquals(60f, rating, 0.001f)
    }

    @Test
    fun `два дня перено нормировка весов`() {
        // (90 * 0.30 + 70 * 0.20) / 0.50 = 82
        val rating = RatingCalculator.compute(listOf(90f, 70f))
        assertEquals(82f, rating, 0.001f)
    }

    @Test
    fun `семь одинаковых дней дают то же значение`() {
        val rating = RatingCalculator.compute(List(7) { 70f })
        assertEquals(70f, rating, 0.001f)
    }

    @Test
    fun `ровно семь дней используют полные веса`() {
        // Последний (свежий) = 100, остальные 0:
        // 100 * 0.30 / 1.00 = 30
        val scores = listOf(100f, 0f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(30f, RatingCalculator.compute(scores), 0.001f)
    }

    @Test
    fun `восемь дней - берутся только 7 свежих`() {
        // Восьмой (самый старый) 100 не должен влиять
        val scores = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 100f)
        assertEquals(0f, RatingCalculator.compute(scores), 0.001f)
    }

    @Test
    fun `рейтинг всегда в диапазоне 0-100`() {
        assertEquals(0f, RatingCalculator.compute(List(7) { 0f }), 0.001f)
        assertEquals(100f, RatingCalculator.compute(List(7) { 100f }), 0.001f)
    }

    @Test
    fun `сглаживание гасит аномальный день`() {
        // Стабильные 90 и один провальный день 0:
        // (0 * 0.3 + 90 * (0.2 + 0.15 + 0.12 + 0.10 + 0.08 + 0.05)) / 1 =
        // 90 * 0.70 = 63
        val scores = listOf(0f, 90f, 90f, 90f, 90f, 90f, 90f)
        assertEquals(63f, RatingCalculator.compute(scores), 0.001f)
    }
}
