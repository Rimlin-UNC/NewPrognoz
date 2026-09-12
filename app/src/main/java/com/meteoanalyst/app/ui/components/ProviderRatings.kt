package com.meteoanalyst.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.meteoanalyst.app.data.model.ProviderRating
import com.meteoanalyst.app.ui.theme.TextSecondary

/**
 * Список провайдеров с текущим рейтингом (анимированные полосы).
 */
@Composable
fun ProviderRatingList(
    providers: List<ProviderRating>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        providers.forEach { provider ->
            ProviderRatingRow(provider)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ProviderRatingRow(provider: ProviderRating) {
    val animated by animateFloatAsState(
        targetValue = provider.rating.coerceIn(0f, 100f) / 100f,
        animationSpec = tween(900),
        label = "ratingBar"
    )
    val color = Color(provider.color)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = provider.name,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${provider.rating.toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = ratingColor(provider.rating)
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Color.White.copy(alpha = 0.10f), CircleShape)
                .padding(vertical = 0.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(6.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(color.copy(alpha = 0.65f), color)
                        ),
                        CircleShape
                    )
            )
        }
    }
}
