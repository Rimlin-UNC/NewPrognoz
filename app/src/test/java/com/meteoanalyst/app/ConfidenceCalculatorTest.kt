package com.meteoanalyst.app

import com.meteoanalyst.app.domain.ConfidenceCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Тесты достоверности прогноза (ТЗ п.5):
 * Confidence = avgRating * 0.92^(hoursAhead / 24).
 */
class ConfidenceCalculatorTest {

    @Test
    fun `сейчас достоверность равна среднему рейтингу`() {
        assertEquals(95f, ConfidenceCalculator.compute(95f, 0f), 0.01f)
    }

    @Test
    fun `24 часа вперед минус 8 процентов`() {
        // 95 * 0.92 = 87.4
        assertEquals(87.4f, ConfidenceCalculator.compute(95f, 24f), 0.01f)
    }

    @Test
    fun `48 часов - геометрическое затухание`() {
        // 100 * 0.92^2 = 84.64
        assertEquals(84.64f, ConfidenceCalculator.compute(100f, 48f), 0.01f)
    }

    @Test
    fun `72 часа - как в ТЗ примерно 74 процента`() {
        // 95 * 0.92^3 = 73.9
        assertEquals(73.9f, ConfidenceCalculator.compute(95f, 72f), 0.05f)
    }

    @Test
    fun `12 часов - половина суточного затухания`() {
        // 100 * 0.92^0.5 = 95.9
        assertEquals(95.9f, ConfidenceCalculator.compute(100f, 12f), 0.05f)
    }

    @Test
    fun `средний рейтинг пустого списка равен 50`() {
        assertEquals(50f, ConfidenceCalculator.averageRating(emptyList()), 0.001f)
    }

    @Test
    fun `средний рейтинг списка`() {
        assertEquals(75f, ConfidenceCalculator.averageRating(listOf(50f, 100f)), 0.001f)
    }
}
