package com.meteoanalyst.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.meteoanalyst.app.ui.theme.TextTertiary
import java.time.LocalDate

/** Строка суточного прогноза для UI. */
data class DayUi(
    val date: LocalDate,
    val minTemp: Float,
    val maxTemp: Float,
    val weatherCode: Int,
    val hoursAheadAtMidday: Float
)

/**
 * Список на 7 дней: день недели, иконка, вилка температур
 * с позиционированной полосой min–max.
 */
@Composable
fun DailyForecastList(
    days: List<DayUi>,
    weekMin: Float,
    weekMax: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        days.forEachIndexed { index, day ->
            DailyRow(day, weekMin, weekMax, index == 0)
        }
    }
}

@Composable
private fun DailyRow(day: DayUi, weekMin: Float, weekMax: Float, isToday: Boolean) {
    val span = (weekMax - weekMin).takeIf { it > 0.5f } ?: 1f
    val fractionStart by animateFloatAsState(
        targetValue = ((day.minTemp - weekMin) / span).coerceIn(0f, 1f),
        animationSpec = tween(800),
        label = "dayStart"
    )
    val fractionEnd by animateFloatAsState(
        targetValue = ((day.maxTemp - weekMin) / span).coerceIn(0f, 1f),
        animationSpec = tween(800),
        label = "dayEnd"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isToday) "Сегодня" else Formatters.weekday(day.date),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(86.dp)
        )
        WeatherIcon(
            code = day.weatherCode,
            isDay = true,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = Formatters.temp0(day.minTemp),
            style = MaterialTheme.typography.bodyLarge,
            color = TextTertiary
        )
        Spacer(Modifier.width(10.dp))
        Canvas(
            Modifier
                .weight(1f)
                .height(5.dp)
        ) {
            drawRoundRect(
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f),
                cornerRadius = CornerRadius(size.height / 2),
                size = Size(size.width, size.height)
            )
            val left = size.width * fractionStart
            val right = size.width * fractionEnd
            drawRoundRect(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(
                        androidx.compose.ui.graphics.Color(0xFF6EE7FF),
                        androidx.compose.ui.graphics.Color(0xFFB388FF),
                        androidx.compose.ui.graphics.Color(0xFFFFD54F)
                    )
                ),
                topLeft = Offset(left, 0f),
                size = Size((right - left).coerceAtLeast(size.height), size.height),
                cornerRadius = CornerRadius(size.height / 2)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = Formatters.temp0(day.maxTemp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
    ) {}
}

@Composable
private fun DayDivider() {
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 0.dp)
    )
}

@Suppress("unused")
private val unusedSecondary = TextSecondary
