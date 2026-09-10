package com.meteoanalyst.app.ui.about

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
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
import com.meteoanalyst.app.ui.components.GlassCard
import com.meteoanalyst.app.ui.theme.AccentCyan
import com.meteoanalyst.app.ui.theme.AccentViolet
import com.meteoanalyst.app.ui.theme.SheetBackground
import com.meteoanalyst.app.ui.theme.TextSecondary
import com.meteoanalyst.app.ui.theme.TextTertiary

private val CHANGELOG = listOf(
    "Добавлена система рейтинга 6 погодных провайдеров",
    "Ежедневная сверка с архивом в 15:00 МСК",
    "Прогноз строится на основе рейтинга (взвешенное среднее)",
    "Индикатор достоверности прогноза во времени",
    "Дизайн Apple Glassmorphism",
    "Более 20 метеопараметров на экране «Подробнее»",
    "График точности провайдеров за неделю"
)

/**
 * «О приложении» + Changelog (ТЗ п.9): показывается при первом запуске
 * и по кнопке на главном экране.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBackground,
        contentColor = Color.White
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 36.dp)
        ) {
            item { Header() }
            item { ChangelogCard() }
            item { HowItWorksCard() }
            item { Attribution() }
        }
    }
}

@Composable
private fun Header() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        Icon(
            Icons.Filled.WbSunny,
            contentDescription = null,
            tint = AccentCyan,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text("Метео-Аналитик", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Версия 2.0 «Метео-Аналитик»",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ChangelogCard() {
    GlassCard(cornerRadius = 20.dp) {
        Text("Что нового", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
        Spacer(Modifier.height(12.dp))
        CHANGELOG.forEach { line ->
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(vertical = 5.dp)
            ) {
                Text("✅", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(10.dp))
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HowItWorksCard() {
    GlassCard(cornerRadius = 20.dp) {
        Text("Как работает самообучение", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
        Spacer(Modifier.height(12.dp))
        HowRow(
            Icons.Filled.Analytics,
            AccentViolet,
            "6 источников",
            "Реальный Open-Meteo и 5 симулируемых провайдеров с собственными погрешностями"
        )
        HowRow(
            Icons.Filled.Schedule,
            AccentCyan,
            "Сверка в 15:00 МСК",
            "Каждый день прогнозы, сделанные 24 часа назад, сравниваются с фактом архива"
        )
        HowRow(
            Icons.Filled.Tune,
            AccentViolet,
            "Взвешенный ансамбль",
            "Итоговый прогноз = среднее по источникам с весами, равными их рейтингу"
        )
    }
}

@Composable
private fun HowRow(icon: ImageVector, tint: Color, title: String, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun Attribution() {
    Text(
        "Данные: Open-Meteo.com (CC BY 4.0) · Прогнозная модель best-match, архив ERA5",
        style = MaterialTheme.typography.labelMedium,
        color = TextTertiary,
        modifier = Modifier.padding(top = 16.dp)
    )
}
