package com.meteoanalyst.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MeteoColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = NightBlue,
    secondary = AccentViolet,
    onSecondary = NightBlue,
    tertiary = AccentAmber,
    background = NightBlue,
    onBackground = TextPrimary,
    surface = NightBlueMid,
    onSurface = TextPrimary,
    surfaceVariant = DeepViolet,
    onSurfaceVariant = TextSecondary
)

@Composable
fun MeteoAnalystTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MeteoColorScheme,
        typography = MeteoTypography,
        content = content
    )
}
