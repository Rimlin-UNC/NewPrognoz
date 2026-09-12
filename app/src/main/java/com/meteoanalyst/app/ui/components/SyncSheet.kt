package com.meteoanalyst.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.meteoanalyst.app.ui.theme.AccentCyan
import com.meteoanalyst.app.ui.theme.SheetBackground
import com.meteoanalyst.app.ui.theme.TextSecondary
import com.meteoanalyst.app.ui.theme.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Состояние синхронизации для UI. */
data class SyncUiState(
    val configured: Boolean = false,
    val serverUrl: String = "",
    val isBusy: Boolean = false,
    val lastMessage: String? = null,
    val pendingCount: Int = 0,
    val lastSyncAt: Long = 0L,
    val biasTemp: Float = 0f,
    val biasWind: Float = 0f,
    val biasSamples: Int = 0
)

/**
 * «Синхронизация» — подключение к серверу (ymaster.ru), ручной запуск,
 * статус очереди и bias-коррекции.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsSheet(
    state: SyncUiState,
    onRegister: (url: String, email: String, password: String) -> Unit,
    onSyncNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var url by remember { mutableStateOf(state.serverUrl.ifBlank { "https://" }) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBackground,
        contentColor = Color.White
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudSync, contentDescription = null, tint = AccentCyan)
                    Spacer(Modifier.width(10.dp))
                    Text("Синхронизация с сервером", style = MaterialTheme.typography.headlineMedium)
                }
            }
            item {
                Text(
                    if (state.configured)
                        "Подключено: ${state.serverUrl}"
                    else
                        "Приложение работает локально. Подключите сервер, чтобы отправлять " +
                            "наблюдения и получать bias-коррекцию от сообщества.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            if (!state.configured) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("Адрес сервера (https://…)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Пароль (от 8 символов)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { onRegister(url.trim(), email.trim(), password) },
                            enabled = !state.isBusy && url.isNotBlank() && email.contains("@") && password.length >= 8,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Зарегистрироваться и подключить") }
                    }
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatRow("В очереди на отправку", "${state.pendingCount}")
                        StatRow(
                            "Последняя синхронизация",
                            if (state.lastSyncAt == 0L) "ещё не было"
                            else SimpleDateFormat("d MMM HH:mm", Locale.forLanguageTag("ru"))
                                .format(Date(state.lastSyncAt))
                        )
                        if (state.biasSamples > 0) {
                            StatRow("Bias температуры (сообщество)",
                                "%+.2f °C (%d пар)".format(state.biasTemp, state.biasSamples))
                        } else {
                            Text(
                                "Bias-коррекция появится, когда сервер накопит сверки",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextTertiary
                            )
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = onSyncNow,
                            enabled = !state.isBusy
                        ) { Text("Синхронизировать сейчас") }
                        if (state.isBusy) {
                            Spacer(Modifier.width(12.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp).width(20.dp),
                                strokeWidth = 2.dp,
                                color = AccentCyan
                            )
                        }
                    }
                }
            }

            state.lastMessage?.let { message ->
                item {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.startsWith("Ошибка") || message.startsWith("Не"))
                            com.meteoanalyst.app.ui.theme.WarnRed
                        else AccentCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
