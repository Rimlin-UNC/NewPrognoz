package com.meteoanalyst.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meteoanalyst.app.domain.ConfidenceCalculator
import com.meteoanalyst.app.ui.theme.AccentCyan
import com.meteoanalyst.app.ui.theme.AccentViolet
import com.meteoanalyst.app.ui.theme.TextSecondary

/**
 * Карточка «Достоверность на сегодня: 94%» (ТЗ п.6).
 * Confidence = avgRating * 0.92^(hoursAhead/24).
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun ConfidenceCard(
    avgRating: Float,
    modifier: Modifier = Modifier
) {
    val now = ConfidenceCalculator.compute(avgRating, 0f)
    val next24 = ConfidenceCalculator.compute(avgRating, 24f)
    val next72 = ConfidenceCalculator.compute(avgRating, 72f)

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Verified,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Достоверность прогноза",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )
        }
        Spacer(Modifier.height(10.dp))
        AnimatedBigPercent(now)
        Spacer(Modifier.height(12.dp))
        GlassProgress(now / 100f, Brush.horizontalGradient(listOf(AccentCyan, AccentViolet)))
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ConfidencePill("Сейчас", now)
            ConfidencePill("+24 ч", next24)
            ConfidencePill("+72 ч", next72)
        }
    }
}

/** Крупный процент с анимацией обновления данных (ТЗ: плавность). */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AnimatedBigPercent(value: Float) {
    AnimatedContent(
        targetState = value.toInt(),
        transitionSpec = {
            (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 3 }) togetherWith
                fadeOut(tween(200))
        },
        label = "confidence"
    ) { percent ->
        Text(
            "$percent%",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun GlassProgress(
    fraction: Float,
    brush: Brush,
    modifier: Modifier = Modifier
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(900),
        label = "progress"
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(8.dp)
                .background(brush, RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun ConfidencePill(label: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        Text(
            "${value.toInt()}%",
            style = MaterialTheme.typography.titleMedium
        )
    }
}
