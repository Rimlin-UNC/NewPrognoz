package com.meteoanalyst.app.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meteoanalyst.app.data.model.EnsemblePoint
import com.meteoanalyst.app.di.ServiceLocator
import com.meteoanalyst.app.domain.WeatherCodes
import com.meteoanalyst.app.ui.about.AboutSheet
import com.meteoanalyst.app.ui.components.AnimatedBackground
import com.meteoanalyst.app.ui.components.ConfidenceCard
import com.meteoanalyst.app.ui.components.DailyForecastList
import com.meteoanalyst.app.ui.components.Formatters
import com.meteoanalyst.app.ui.components.GlassCard
import com.meteoanalyst.app.ui.components.HourlyForecastRow
import com.meteoanalyst.app.ui.components.ProviderRatingList
import com.meteoanalyst.app.ui.components.RatingChart
import com.meteoanalyst.app.ui.components.StaggeredAppear
import com.meteoanalyst.app.ui.components.WeatherIcon
import com.meteoanalyst.app.ui.details.DetailsSheet
import com.meteoanalyst.app.ui.theme.AccentCyan
import com.meteoanalyst.app.ui.theme.GlassStroke
import com.meteoanalyst.app.ui.theme.TextSecondary
import com.meteoanalyst.app.ui.theme.TextTertiary
import com.meteoanalyst.app.ui.theme.WarnRed
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Главный экран (ТЗ п.6): текущая температура с вилкой источников,
 * карточка достоверности, почасовой прогноз, 7 дней, «Подробнее»
 * и график рейтингов провайдеров.
 */
@Composable
fun MainScreen(
    viewModel: WeatherViewModel = viewModel(factory = ServiceLocator.weatherViewModelFactory())
) {
    val state by viewModel.state.collectAsState()
    var showDetails by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    // Разрешение на геолокацию при первом запуске (ТЗ п.10)
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) viewModel.onLocationPermissionGranted()
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted && !viewModel.isPermissionAsked) {
            viewModel.markPermissionAsked()
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedBackground()

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            HeaderRow(
                locationName = state.locationName,
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                onAbout = { showAbout = true }
            )

            when {
                state.isLoading -> LoadingBlock()
                else -> {
                    state.error?.let { error ->
                        ErrorBanner(message = error, onRetry = { viewModel.refresh() })
                    }

                    state.current?.let { current ->
                        StaggeredAppear(0) { CurrentWeatherBlock(current) }
                    }

                    StaggeredAppear(1) {
                        ConfidenceCard(avgRating = state.avgRating)
                    }

                    SectionTitle("Почасовой прогноз")
                    StaggeredAppear(2) {
                        HourlyForecastRow(hours = state.hourly, avgRating = state.avgRating)
                    }

                    if (state.daily.isNotEmpty()) {
                        SectionTitle("Прогноз на 7 дней")
                        StaggeredAppear(3) {
                            GlassCard {
                                DailyForecastList(
                                    days = state.daily,
                                    weekMin = state.weekMin,
                                    weekMax = state.weekMax
                                )
                            }
                        }
                    }

                    StaggeredAppear(4) {
                        DetailsButton { showDetails = true }
                    }

                    SectionTitle("Точность провайдеров за 7 дней")
                    StaggeredAppear(5) {
                        GlassCard {
                            RatingChart(chart = state.chart)
                        }
                    }

                    SectionTitle("Рейтинг источников")
                    StaggeredAppear(6) {
                        GlassCard {
                            ProviderRatingList(providers = state.providers)
                            LastVerificationRow(state.lastCheckDate)
                        }
                    }

                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }

    if (showDetails) {
        DetailsSheet(current = state.current, onDismiss = { showDetails = false })
    }
    if (state.showChangelogOnStart) {
        AboutSheet(onDismiss = { viewModel.markChangelogShown() })
    } else if (showAbout) {
        AboutSheet(onDismiss = { showAbout = false })
    }
}

@Composable
private fun HeaderRow(
    locationName: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onAbout: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = locationName,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.weight(1f)
        )
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = AccentCyan
            )
            Spacer(Modifier.width(12.dp))
        }
        IconButton(onClick = onRefresh) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "Обновить",
                tint = TextSecondary
            )
        }
        IconButton(onClick = onAbout) {
            Icon(
                Icons.Filled.Info,
                contentDescription = "О приложении",
                tint = TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun CurrentWeatherBlock(current: EnsemblePoint) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            Formatters.fullDate(LocalDateTime.now()),
            style = MaterialTheme.typography.labelLarge,
            color = TextTertiary
        )
        Spacer(Modifier.height(8.dp))

        AnimatedContent(
            targetState = current.point.temperature.toInt(),
            transitionSpec = {
                (fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 5 }) togetherWith
                    fadeOut(tween(200))
            },
            label = "currentTemp"
        ) { temp ->
            Text(
                text = "${temp}°",
                style = MaterialTheme.typography.displayLarge
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(
                code = current.point.weatherCode,
                isDay = current.point.isDay,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                WeatherCodes.description(current.point.weatherCode),
                style = MaterialTheme.typography.titleLarge,
                color = TextSecondary
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoChip(text = "Источники: ${Formatters.temp0(current.tempMin)} … ${Formatters.temp0(current.tempMax)}")
            InfoChip(text = "Согласие: ${current.providersAgree}/${current.providersCount}")
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    GlassCard(
        cornerRadius = 16.dp,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = TextSecondary,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp)
    )
}

/** Кнопка «Подробнее» — стеклянная, во всю ширину (ТЗ п.6). */
@Composable
private fun DetailsButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .border(0.5.dp, GlassStroke, shape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0x2E6EE7FF), Color(0x26B388FF))
                ),
                shape
            )
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Подробнее · 20+ параметров",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = AccentCyan
            )
        }
    }
}

@Composable
private fun LoadingBlock() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 120.dp)
    ) {
        CircularProgressIndicator(color = AccentCyan)
        Spacer(Modifier.height(16.dp))
        Text("Загрузка…", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    GlassCard(
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(
            start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = WarnRed,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Text("Повторить", color = AccentCyan)
            }
        }
    }
}

@Composable
private fun LastVerificationRow(lastCheckDate: String?) {
    val text = lastCheckDate?.let {
        runCatching {
            "Последняя сверка: ${Formatters.shortDate(LocalDate.parse(it))}, 15:00 МСК"
        }.getOrNull()
    } ?: "Последняя сверка: ещё не проводилась"
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = TextTertiary,
        modifier = Modifier.padding(top = 4.dp)
    )
}
