package com.meteoanalyst.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.meteoanalyst.app.domain.WeatherCodes
import com.meteoanalyst.app.ui.theme.AccentCyan
import com.meteoanalyst.app.ui.theme.AccentViolet
import com.meteoanalyst.app.ui.theme.GoodGreen

/**
 * Векторная погодная иконка по WMO-коду (Material Icons Extended, ТЗ п.6).
 */
@Composable
fun WeatherIcon(
    code: Int,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val kind = WeatherCodes.kind(code)
    val vector: ImageVector = when (kind) {
        WeatherCodes.Kind.CLEAR -> if (isDay) Icons.Filled.WbSunny else Icons.Filled.Bedtime
        WeatherCodes.Kind.PARTLY -> if (isDay) Icons.Filled.FilterDrama else Icons.Filled.Bedtime
        WeatherCodes.Kind.CLOUDY -> Icons.Filled.Cloud
        WeatherCodes.Kind.FOG -> Icons.Filled.BlurOn
        WeatherCodes.Kind.DRIZZLE -> Icons.Filled.Grain
        WeatherCodes.Kind.RAIN -> Icons.Filled.WaterDrop
        WeatherCodes.Kind.SNOW -> Icons.Filled.AcUnit
        WeatherCodes.Kind.THUNDER -> Icons.Filled.Thunderstorm
    }
    val color: Color = if (tint != Color.Unspecified) tint else when (kind) {
        WeatherCodes.Kind.CLEAR -> if (isDay) Color(0xFFFFD54F) else AccentViolet
        WeatherCodes.Kind.PARTLY -> if (isDay) Color(0xFFFFD54F) else AccentViolet
        WeatherCodes.Kind.CLOUDY -> Color(0xFFB0BEC5)
        WeatherCodes.Kind.FOG -> Color(0xFF90A4AE)
        WeatherCodes.Kind.DRIZZLE -> AccentCyan
        WeatherCodes.Kind.RAIN -> AccentCyan
        WeatherCodes.Kind.SNOW -> Color(0xFFB3E5FC)
        WeatherCodes.Kind.THUNDER -> Color(0xFFFFF176)
    }
    Icon(
        imageVector = vector,
        contentDescription = WeatherCodes.description(code),
        modifier = modifier,
        tint = color
    )
}

/** Цвет качественной оценки рейтинга. */
fun ratingColor(rating: Float): Color = when {
    rating >= 80f -> GoodGreen
    rating >= 60f -> AccentCyan
    rating >= 40f -> Color(0xFFFFD54F)
    else -> com.meteoanalyst.app.ui.theme.WarnRed
}
