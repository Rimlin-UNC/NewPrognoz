package com.meteoanalyst.app.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.meteoanalyst.app.data.model.EnsemblePoint
import com.meteoanalyst.app.domain.WeatherCodes
import com.meteoanalyst.app.ui.components.Formatters
import com.meteoanalyst.app.ui.components.GlassCard
import com.meteoanalyst.app.ui.components.WeatherIcon
import com.meteoanalyst.app.ui.theme.AccentCyan
import com.meteoanalyst.app.ui.theme.SheetBackground
import com.meteoanalyst.app.ui.theme.TextSecondary
import com.meteoanalyst.app.ui.theme.TextTertiary

private data class ParamRow(
    val icon: ImageVector,
    val title: String,
    val value: String
)

private data class Section(
    val title: String,
    val rows: List<ParamRow>
)

/**
 * Экран «Подробнее» (ТЗ п.6): Bottom Sheet с полным списком
 * более 20 метеопараметров.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsSheet(
    current: EnsemblePoint?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBackground,
        contentColor = Color.White
    ) {
        if (current == null) {
            Text(
                "Нет данных",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            DetailsContent(current)
        }
    }
}

@Composable
private fun DetailsContent(current: EnsemblePoint) {
    val p = current.point
    val sections = buildSections(current)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Заголовок с текущей температурой и состоянием
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            ) {
                WeatherIcon(
                    code = p.weatherCode,
                    isDay = p.isDay,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        Formatters.temp1(p.temperature),
                        style = MaterialTheme.typography.displayMedium
                    )
                    Text(
                        WeatherCodes.description(p.weatherCode),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            }
        }

        // Ансамбль: вилка источников и согласие
        item {
            GlassCard(cornerRadius = 18.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Вилка источников", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                        Text(
                            "${Formatters.temp1(current.tempMin)} … ${Formatters.temp1(current.tempMax)}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Согласие источников", style = MaterialTheme.typography.labelMedium, color = TextTertiary)
                        Text(
                            "${current.providersAgree} из ${current.providersCount}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (current.providersAgree >= current.providersCount - 1) AccentCyan else Color.White
                        )
                    }
                }
            }
        }

        sections.forEach { section ->
            item(key = "header_${section.title}") {
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(section.rows.size) { index ->
                val row = section.rows[index]
                ParamRowView(row)
            }
        }
    }
}

@Composable
private fun ParamRowView(row: ParamRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            row.icon,
            contentDescription = null,
            tint = AccentCyan,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            row.title,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            row.value,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

private fun buildSections(current: EnsemblePoint): List<Section> {
    val p = current.point
    return listOf(
        Section(
            "Основное",
            listOf(
                ParamRow(Icons.Filled.Thermostat, "Температура", Formatters.temp1(p.temperature)),
                ParamRow(Icons.Filled.DeviceThermostat, "Ощущается как", Formatters.temp1(p.apparentTemperature)),
                ParamRow(Icons.Filled.InvertColors, "Влажность", Formatters.percent(p.humidity)),
                ParamRow(Icons.Filled.WaterDrop, "Точка росы", Formatters.temp1(p.dewPoint)),
                ParamRow(Icons.Filled.Compress, "Давление", Formatters.hPa(p.pressure)),
                ParamRow(Icons.Filled.FilterDrama, "Облачность", Formatters.percent(p.cloudCover)),
                ParamRow(Icons.Filled.Visibility, "Видимость", Formatters.visibility(p.visibility)),
                ParamRow(Icons.Filled.WbSunny, "УФ-индекс", "${Formatters.uv(p.uvIndex)} (${Formatters.uvQuality(p.uvIndex)})"),
                ParamRow(if (p.isDay) Icons.Filled.LightMode else Icons.Filled.DarkMode, "Статус", if (p.isDay) "День" else "Ночь")
            )
        ),
        Section(
            "Ветер",
            listOf(
                ParamRow(Icons.Filled.Air, "Скорость ветра", Formatters.speed(p.windSpeed)),
                ParamRow(Icons.Filled.Speed, "Порывы ветра", Formatters.speed(p.windGusts)),
                ParamRow(Icons.Filled.Explore, "Направление", Formatters.direction(p.windDirection))
            )
        ),
        Section(
            "Осадки",
            listOf(
                ParamRow(Icons.Filled.WaterDrop, "Осадки за час", Formatters.mm(p.precipitation)),
                ParamRow(
                    Icons.Filled.Umbrella,
                    "Вероятность осадков",
                    p.precipitationProbability?.let { Formatters.percent(it) } ?: "—"
                ),
                ParamRow(Icons.Filled.Grain, "Дождь", Formatters.mm(p.rain)),
                ParamRow(Icons.Filled.Grain, "Ливень", Formatters.mm(p.showers)),
                ParamRow(Icons.Filled.AcUnit, "Снег", String.format(java.util.Locale.forLanguageTag("ru"), "%.1f см", p.snowfall))
            )
        ),
        Section(
            "Атмосфера",
            listOf(
                ParamRow(Icons.Filled.Thunderstorm, "Конвективная энергия (CAPE)", Formatters.cape(p.cape)),
                ParamRow(Icons.Filled.Height, "Высота нулевой изотермы", Formatters.height(p.freezingLevelHeight)),
                ParamRow(Icons.Filled.WbTwilight, "Солнечная радиация", Formatters.radiation(p.shortwaveRadiation))
            )
        ),
        Section(
            "Почва",
            listOf(
                ParamRow(Icons.Filled.Grass, "Температура почвы (0–7 см)", p.soilTemperature?.let { Formatters.temp1(it) } ?: "—"),
                ParamRow(Icons.Filled.Waves, "Влажность почвы (0–7 см)", Formatters.soilMoisture(p.soilMoisture))
            )
        )
    )
}
