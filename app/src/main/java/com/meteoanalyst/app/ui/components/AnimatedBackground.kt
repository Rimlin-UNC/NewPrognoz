package com.meteoanalyst.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.meteoanalyst.app.ui.theme.DeepViolet
import com.meteoanalyst.app.ui.theme.NightBlue
import com.meteoanalyst.app.ui.theme.NightBlueMid

/**
 * Живой градиентный фон: тёмно-синий -> фиолетовый и медленно дрейфующие
 * мягкие пятна-«авроры» (размытие имитирует стекло, ТЗ п.6).
 */
@Composable
fun AnimatedBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")

    val drift1x by transition.animateFloat(
        initialValue = -48f,
        targetValue = 48f,
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift1x"
    )
    val drift1y by transition.animateFloat(
        initialValue = -24f,
        targetValue = 32f,
        animationSpec = infiniteRepeatable(tween(17000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift1y"
    )
    val drift2x by transition.animateFloat(
        initialValue = 40f,
        targetValue = -56f,
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift2x"
    )
    val drift2y by transition.animateFloat(
        initialValue = 28f,
        targetValue = -36f,
        animationSpec = infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift2y"
    )

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(NightBlue, NightBlueMid, DeepViolet)
                )
            )
    ) {
        AuroraBlob(
            offsetX = (-40 + drift1x).dp,
            offsetY = (60 + drift1y).dp,
            size = 340.dp,
            color = Color(0xFF3D5A9E)
        )
        AuroraBlob(
            offsetX = (140 + drift2x).dp,
            offsetY = (520 + drift2y).dp,
            size = 380.dp,
            color = Color(0xFF5B3D8F)
        )
        AuroraBlob(
            offsetX = (40 + drift2y).dp,
            offsetY = (980 + drift1x).dp,
            size = 300.dp,
            color = Color(0xFF27476E)
        )
    }
}

@Composable
private fun AuroraBlob(offsetX: Dp, offsetY: Dp, size: Dp, color: Color) {
    // Радиальный градиент мягкий сам по себе; blur добавляет «стеклянности»
    // на Android 12+ (на старых версиях — просто деградация до градиента).
    Box(
        Modifier
            .offset(x = offsetX, y = offsetY)
            .size(size)
            .background(
                Brush.radialGradient(
                    listOf(color.copy(alpha = 0.5f), Color.Transparent)
                )
            )
    )
}
