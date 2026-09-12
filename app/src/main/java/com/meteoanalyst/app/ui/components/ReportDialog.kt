package com.meteoanalyst.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.meteoanalyst.app.ui.theme.TextSecondary
import com.meteoanalyst.app.ui.theme.TextTertiary

/**
 * «Сообщить погоду» — ввод фактического наблюдения (offline-first):
 * сохраняется в локальную очередь и уйдёт на сервер при синхронизации.
 */
@Composable
fun ReportObservationDialog(
    onDismiss: () -> Unit,
    onSave: (temp: Float?, wind: Float?, gust: Float?, precip: Float?, pressure: Float?) -> Unit
) {
    var temp by remember { mutableStateOf("") }
    var wind by remember { mutableStateOf("") }
    var gust by remember { mutableStateOf("") }
    var precip by remember { mutableStateOf("") }
    var pressure by remember { mutableStateOf("") }

    fun parse(s: String): Float? =
        s.replace(',', '.').toFloatOrNull()?.takeIf { s.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.meteoanalyst.app.ui.theme.SheetBackground,
        title = { Text("Сообщить погоду") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Ваше наблюдение улучшит точность прогнозов для всех пользователей рядом. " +
                        "Заполните хотя бы одно поле.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                NumberField(temp, { temp = it }, "Температура, °C")
                NumberField(wind, { wind = it }, "Ветер, м/с")
                NumberField(gust, { gust = it }, "Порывы, м/с")
                NumberField(precip, { precip = it }, "Осадки за час, мм")
                NumberField(pressure, { pressure = it }, "Давление, гПа")
                Text(
                    "Отправка без интернета: сохранится в очередь",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(parse(temp), parse(wind), parse(gust), parse(precip), parse(pressure))
                },
                enabled = listOf(temp, wind, gust, precip, pressure).any { it.isNotBlank() }
            ) { Text("Сохранить", color = com.meteoanalyst.app.ui.theme.AccentCyan) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = TextSecondary) }
        }
    )
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(8)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}
