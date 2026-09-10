package com.meteoanalyst.app.ui.components

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Форматирование значений для UI. */
object Formatters {

    private val ru: Locale = Locale.forLanguageTag("ru")

    private val dayOfWeekFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE, d MMMM", ru)
    private val shortTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", ru)
    private val shortDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM", ru)
    private val weekdayFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE", ru)

    fun fullDate(date: LocalDateTime): String =
        dayOfWeekFormatter.format(date).replaceFirstChar { it.uppercase(ru) }

    fun hour(time: LocalDateTime): String = shortTimeFormatter.format(time)

    fun shortDate(date: LocalDate): String = shortDateFormatter.format(date)

    fun weekday(date: LocalDate): String = weekdayFormatter.format(date)

    /** «16,2°» — одна десятичная, запятая по-русски. */
    fun temp1(value: Float): String = String.format(ru, "%.1f°", value)

    /** «16°» — без десятых. */
    fun temp0(value: Float): String = "${value.roundToInt()}°"

    fun percent(value: Float): String = "${value.roundToInt()}%"

    fun speed(value: Float): String = String.format(ru, "%.1f м/с", value)

    fun mm(value: Float): String = String.format(ru, "%.1f мм", value)

    fun hPa(value: Float): String = "${value.roundToInt()} гПа"

    fun uv(value: Float): String = String.format(ru, "%.1f", value)

    fun uvQuality(value: Float): String = when {
        value < 3f -> "низкий"
        value < 6f -> "умеренный"
        value < 8f -> "высокий"
        value < 11f -> "очень высокий"
        else -> "экстремальный"
    }

    fun visibility(meters: Float?): String =
        meters?.let { String.format(ru, "%.1f км", it / 1000f) } ?: "—"

    fun direction(degrees: Float): String {
        val names = listOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")
        val index = ((degrees % 360f + 360f + 22.5f) % 360f / 45f).toInt() % 8
        return "${names[index]} · ${degrees.roundToInt()}°"
    }

    fun soilMoisture(value: Float?): String =
        value?.let { String.format(ru, "%.2f м³/м³", it) } ?: "—"

    fun height(meters: Float?): String =
        meters?.let { String.format(ru, "%,d м", it.roundToInt()) } ?: "—"

    fun radiation(value: Float?): String =
        value?.let { String.format(ru, "%.0f Вт/м²", it) } ?: "—"

    fun cape(value: Float?): String =
        value?.let { String.format(ru, "%.0f Дж/кг", it) } ?: "—"

    private fun Float.roundToInt(): Int = kotlin.math.round(this).toInt()
}
