package com.meteoanalyst.app

import com.meteoanalyst.app.domain.ScoreCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Тесты формулы суточного скора (ТЗ п.3):
 * Score = 100 - (|dT| * 8 + |dW| * 4 + (Rain_mismatch ? 25 : 0))
 */
class ScoreCalculatorTest {

    @Test
    fun `идеальный прогноз даёт 100`() {
        val score = ScoreCalculator.score(
            tempForecast = 20f, tempActual = 20f,
            windForecast = 3.5f, windActual = 3.5f,
            rainForecast = 0.1f, rainActual = 0.0f
        )
        assertEquals(100f, score, 0.001f)
    }

    @Test
    fun `ошибка температуры на 1 градус минус 8 баллов`() {
        val score = ScoreCalculator.score(21f, 20f, 3f, 3f, 0f, 0f)
        assertEquals(92f, score, 0.001f)
    }

    @Test
    fun `ошибка температуры на 2 градуса минус 16 баллов`() {
        val score = ScoreCalculator.score(22f, 20f, 3f, 3f, 0f, 0f)
        assertEquals(84f, score, 0.001f)
    }

    @Test
    fun `ошибка ветра на 1 мс минус 4 балла`() {
        val score = ScoreCalculator.score(20f, 20f, 4f, 3f, 0f, 0f)
        assertEquals(96f, score, 0.001f)
    }

    @Test
    fun `ошибка ветра на 3 мс минус 12 баллов`() {
        val score = ScoreCalculator.score(20f, 20f, 6f, 3f, 0f, 0f)
        assertEquals(88f, score, 0.001f)
    }

    @Test
    fun `прогнозировался дождь а дождя не было минус 25`() {
        val score = ScoreCalculator.score(20f, 20f, 3f, 3f, 0.5f, 0f)
        assertEquals(75f, score, 0.001f)
    }

    @Test
    fun `дождь не прогнозировался а он прошёл минус 25`() {
        val score = ScoreCalculator.score(20f, 20f, 3f, 3f, 0f, 0.4f)
        assertEquals(75f, score, 0.001f)
    }

    @Test
    fun `дождь совпал - штрафа нет даже при разной интенсивности`() {
        val score = ScoreCalculator.score(20f, 20f, 3f, 3f, 1.5f, 0.3f)
        assertEquals(100f, score, 0.001f)
    }

    @Test
    fun `порог осадков 02 мм не считается дождём`() {
        // 0.2 мм ровно и меньше — «нет дождя» с обеих сторон
        val score = ScoreCalculator.score(20f, 20f, 3f, 3f, 0.2f, 0.0f)
        assertEquals(100f, score, 0.001f)
    }

    @Test
    fun `комбинированная ошибка`() {
        // |dT|=1.5 -> 12; |dW|=2 -> 8; дождь не совпал -> 25; итого 55
        val score = ScoreCalculator.score(21.5f, 20f, 5f, 3f, 0.5f, 0f)
        assertEquals(55f, score, 0.001f)
    }

    @Test
    fun `скор не уходит ниже нуля`() {
        val score = ScoreCalculator.score(30f, 20f, 10f, 3f, 5f, 0f)
        assertEquals(0f, score, 0.001f)
    }

    @Test
    fun `детектор несоответствия осадков`() {
        assertTrue(ScoreCalculator.isRainMismatch(0.3f, 0.1f))
        assertTrue(ScoreCalculator.isRainMismatch(0.1f, 0.3f))
        assertFalse(ScoreCalculator.isRainMismatch(0.3f, 0.3f))
        assertFalse(ScoreCalculator.isRainMismatch(0.1f, 0.1f))
        // ровно на пороге 0.2 — осадков «нет»
        assertFalse(ScoreCalculator.isRainMismatch(0.2f, 0.2f))
    }
}
