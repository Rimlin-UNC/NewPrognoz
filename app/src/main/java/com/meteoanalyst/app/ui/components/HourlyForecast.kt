package com.meteoanalyst.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.FontWeight
import androidx.compose.ui.unit.dp
import com.meteoanalyst.app.data.model.WeatherPoint
import com.meteoanalyst.app.domain.ConfidenceCalculator
import com.meteoanalyst.app.ui.theme.AccentCyan
import com.meteoanalyst.app.ui.theme.TextSecondary
import java.time.LocalDateTime

/** Элемент почасового ряда для UI. */
data class HourUi(
    val time: LocalDateTime,
    val point: WeatherPoint,
    val isNow: Boolean,
    val hoursAhead: Float
)

/**
 * Горизонтальный скролл почасового прогноза (ТЗ п.6): время, иконка,
 * температура, вероятность осадков, мини-индикатор достоверности.
 */
@Composable
fun HourlyForecastRow(
    hours: List<HourUi>,
    avgRating: Float,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(hours) { item ->
            HourCard(item, avgRating)
        }
    }
}

@Composable
private fun HourCard(item: HourUi, avgRating: Float) {
    val confidence = ConfidenceCalculator.compute(avgRating, item.hoursAhead)
    GlassCard(
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (item.isNow) "Сейчас" else Formatters.hour(item.time),
                style = MaterialTheme.typography.labelMedium,
                color = if (item.isNow) AccentCyan else TextSecondary,
                fontWeight = if (item.isNow) FontWeight.Bold else FontWeight.Medium
            )
            Spacer(Modifier.height(10.dp))
            WeatherIcon(
                code = item.point.weatherCode,
                isDay = item.point.isDay,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = Formatters.temp0(item.point.temperature),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(6.dp))
            val prob = item.point.precipitationProbability
            if (prob != null && prob > 5f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.WaterDrop,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "${prob.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCyan
                    )
                }
            } else {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(8.dp))
            GlassProgress(
                fraction = confidence / 100f,
                brush = Brush.horizontalGradient(
                    listOf(AccentCyan.copy(alpha = 0.9f), AccentCyan.copy(alpha = 0.25f))
                ),
                modifier = Modifier.width(36.dp)
            )
        }
    }
}
