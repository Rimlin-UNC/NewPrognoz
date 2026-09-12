package com.meteoanalyst.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.meteoanalyst.app.ui.theme.TextTertiary
import java.time.LocalDate

/** Ряд одного провайдера на графике (score == null — данных нет). */
data class ChartSeriesUi(
    val providerId: String,
    val name: String,
    val color: Long,
    val scores: List<Float?>
)

data class ChartUi(
    val dates: List<String>,
    val series: List<ChartSeriesUi>
)

/**
 * Линейный график точности провайдеров за последние 7 дней —
 * собственный Canvas вместо MPAndroidChart (ТЗ п.6, п.8).
 */
@Composable
fun RatingChart(
    chart: ChartUi,
    modifier: Modifier = Modifier
) {
    if (chart.dates.isEmpty()) {
        Text(
            "Данные появятся после первой сверки с архивом",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary
        )
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val padLeft = 4.dp.toPx()
            val padRight = 4.dp.toPx()
            val padTop = 8.dp.toPx()
            val padBottom = 10.dp.toPx()
            val w = size.width - padLeft - padRight
            val h = size.height - padTop - padBottom
            val n = chart.dates.size

            // Горизонтальная сетка 0/25/50/75/100
            for (level in intArrayOf(0, 25, 50, 75, 100)) {
                val y = padTop + h * (1f - level / 100f)
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(padLeft, y),
                    end = Offset(size.width - padRight, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val xStep = if (n > 1) w / (n - 1) else 0f
            val pointRadius = 3.dp.toPx()

            for (series in chart.series) {
                val lineColor = Color(series.color)
                var path: Path? = null
                var started = false

                fun flush() {
                    path?.let {
                        drawPath(
                            it,
                            color = lineColor,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    path = null
                    started = false
                }

                for (i in series.scores.indices) {
                    val score = series.scores[i]
                    if (score == null) {
                        flush()
                        continue
                    }
                    val x = padLeft + xStep * i
                    val y = padTop + h * (1f - score.coerceIn(0f, 100f) / 100f)
                    if (!started) {
                        path = (path ?: Path()).apply { moveTo(x, y) }
                        started = true
                    } else {
                        path!!.lineTo(x, y)
                    }
                }
                flush()

                // Точки на графиках (ТЗ: «линейный график с точками»)
                for (i in series.scores.indices) {
                    val score = series.scores[i] ?: continue
                    val x = padLeft + xStep * i
                    val y = padTop + h * (1f - score.coerceIn(0f, 100f) / 100f)
                    drawCircle(
                        color = lineColor,
                        radius = pointRadius,
                        center = Offset(x, y)
                    )
                }
            }
        }

        // Подписи дат под графиком
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            chart.dates.forEach { date ->
                Text(
                    text = runCatching {
                        Formatters.shortDate(LocalDate.parse(date))
                    }.getOrDefault(date.dropLast(3)),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }
}
